package com.gitlab.biomorf.tscalp.domain.api.managers

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.roles.PortfolioApi
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AccountBalance
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio
import com.gitlab.biomorf.tscalp.domain.models.BondPosition
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CurrencyPosition
import com.gitlab.biomorf.tscalp.domain.models.EtfPosition
import com.gitlab.biomorf.tscalp.domain.models.FuturePosition
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.OptionPosition
import com.gitlab.biomorf.tscalp.domain.models.OtherPosition
import com.gitlab.biomorf.tscalp.domain.models.PortfolioSheet
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.SharePosition
import com.gitlab.biomorf.tscalp.domain.models.StreamState
import com.gitlab.biomorf.tscalp.domain.models.toPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Агрегирует обновления позиций всех запрошенных счетов в единый
 * [PortfolioSheet]. Два источника:
 *
 * 1. **Stream** (`broker.streams?.subscribePositions(accountId)`):
 *    мгновенные partial-события (только quantity/идентификаторы).
 *    Мержатся поверх existing [Position] из `_sheet` (или создаются
 *    через `toPosition(instrument)` из [instrumentCache], если позиции
 *    ещё нет).
 * 2. **Polling** (`broker.portfolio?.fetchPortfolioPositions(accountId,
 *    sandboxMode)` раз в [POLLING_INTERVAL_MS]): полные [AccountPortfolio]
 *    со всех счетов. Заменяет `_sheet.accounts[accountKey]` целиком
 *    (existing [AccountBalance] от WS сохраняются, если polling балансы
 *    не даёт — GetPortfolio money/blocked не отдаёт).
 *
 * `_sheet` — единственный публичный эмитируемый тип (`sheet: StateFlow<PortfolioSheet>`).
 * [connectionState] — агрегат по всем счетам (один tracker).
 *
 * Polling идёт через `BrokerApi.portfolio` ([PortfolioApi], опциональный
 * контракт). `portfolio == null` (Finam / BCS-заглушка) → polling не
 * запускается; stream работает; watchdog через 30 сек → STALE.
 */
@Singleton
class PositionFlowManager
    @Inject
    constructor(
        private val brokerManager: BrokerRegistry,
        private val logger: Logger,
        private val managerDispatcher: CoroutineDispatcher,
    ) {
        companion object {
            private const val TAG = "PositionFlowManager"
            private const val POLLING_INTERVAL_MS = 10_000L
            private const val STATE_TICK_MS = 1_000L
        }

        private val _sheet = MutableStateFlow(PortfolioSheet(emptyMap()))

        /** Полный снимок всех запрошенных счетов. */
        val sheet: StateFlow<PortfolioSheet> = _sheet.asStateFlow()

        private val stateTracker = StreamStateTracker(logger = logger, tag = TAG)

        /** Состояние источника данных по всем счетам: LIVE / POLLING / STALE / STOPPED. */
        val connectionState: StateFlow<StreamState> = stateTracker.state

        /** Кэш `Instrument` per account (заполняется polling'ом). */
        private val instrumentCache = mutableMapOf<AccountKey, MutableMap<String, Instrument>>()

        private var job: Job? = null
        private var currentKeys: Set<AccountKey> = emptySet()
        private var currentSandboxMode: Boolean = false

        private val startLock = Any()

        private val scope =
            CoroutineScope(
                managerDispatcher.limitedParallelism(1) + SupervisorJob() +
                    CoroutineExceptionHandler { _, e ->
                        logger.e(TAG, "Uncaught error in position stream", e)
                    },
            )

        /**
         * Запускает сбор позиций со всех [accountKeys]. Идемпотентно
         * для того же набора счетов и режима. При изменении — перезапускает
         * оба источника и очищает [instrumentCache] + [PortfolioSheet] + replay [flow].
         *
         * @param seed начальный [PortfolioSheet] (из primary fetch в VM). Применяется
         *   к [_sheet] идемпотентно: если счёт уже есть — обновляются только
         *   `balances` (если seed.balances непуст), positions из manager'а
         *   сохраняются (polling мог их обновить свежее, чем seed).
         *   На первом старте (`_sheet` пуст) — seed кладётся целиком.
         */
        fun start(
            accountKeys: Set<AccountKey>,
            sandboxMode: Boolean,
            forceRestart: Boolean = false,
            seed: PortfolioSheet? = null,
        ) = synchronized(startLock) {
            doStart(accountKeys, sandboxMode, forceRestart, seed)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun doStart(
            accountKeys: Set<AccountKey>,
            sandboxMode: Boolean,
            forceRestart: Boolean,
            seed: PortfolioSheet?,
        ) {
            if (!forceRestart &&
                job?.isActive == true &&
                currentKeys == accountKeys &&
                currentSandboxMode == sandboxMode
            ) {
                seed?.let { applySeed(it) }
                logger.d(TAG, "Поток позиций уже запущен для $accountKeys")
                return
            }
            if (currentKeys != accountKeys || currentSandboxMode != sandboxMode) {
                logger.d(TAG, "Перезапуск потока позиций: $currentKeys → $accountKeys, sandbox=$sandboxMode")
                instrumentCache.clear()
                _sheet.value = PortfolioSheet(emptyMap())
            }
            seed?.let { applySeed(it) }
            job?.cancel()
            currentKeys = accountKeys
            currentSandboxMode = sandboxMode
            stateTracker.onStart()
            job =
                scope.launch {
                    val broker = brokerManager.getBroker(BrokerName.TINVEST)
                    if (broker == null || !broker.isInitialized) {
                        logger.e(TAG, "TInvestBrokerAPI не инициализирован, поток не запускаем")
                        stateTracker.onStop()
                        return@launch
                    }
                    val portfolioApi = broker.portfolio
                    if (portfolioApi == null) {
                        logger.e(TAG, "${broker.name.key}: *Portfolio не поддерживается, polling не запускаем")
                    }
                    coroutineScope {
                        for (key in accountKeys) {
                            launch { collectStream(broker, key) }
                            if (portfolioApi != null) {
                                launch { runPolling(portfolioApi, key, sandboxMode) }
                            }
                        }
                        launch { stateWatchdog() }
                    }
                }
        }

        private suspend fun collectStream(
            broker: BrokerApi,
            accountKey: AccountKey,
        ) {
            try {
                broker.streams?.subscribePositions(accountKey.accountId)?.collect { item ->
                    stateTracker.onStreamEmit()
                    handleStreamItem(item, accountKey)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "stream error for $accountKey: ${e.message}", e)
            }
        }

        private suspend fun runPolling(
            portfolioApi: PortfolioApi,
            accountKey: AccountKey,
            sandboxMode: Boolean,
        ) {
            while (currentCoroutineContext().isActive) {
                try {
                    val accountPortfolio = portfolioApi.fetchPortfolioPositions(accountKey.accountId, sandboxMode)
                    stateTracker.onPollingSuccess()
                    logger.d(TAG, "polling $accountKey: ${accountPortfolio.positions.size} positions")
                    handlePollingSnapshot(accountPortfolio, accountKey)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e.message?.contains("NOT_FOUND") == true) {
                        logger.w(TAG, "polling $accountKey: account not found — stopping")
                        return
                    }
                    logger.w(TAG, "polling $accountKey error: ${e.message}")
                }
                delay(POLLING_INTERVAL_MS)
            }
        }

        private suspend fun handlePollingSnapshot(
            accountPortfolio: AccountPortfolio,
            accountKey: AccountKey,
        ) {
            val cache = instrumentCache.getOrPut(accountKey) { mutableMapOf() }
            for (position in accountPortfolio.positions) {
                cache[position.instrument.tscalpInstrumentId] = position.instrument
            }
            val existingBalances = _sheet.value.accounts[accountKey]?.balances ?: emptyList()
            val mergedBalances =
                if (accountPortfolio.balances.isEmpty()) existingBalances else accountPortfolio.balances
            val merged = accountPortfolio.copy(balances = mergedBalances)
            _sheet.update { current ->
                current.copy(accounts = current.accounts + (accountKey to merged))
            }
        }

        private suspend fun handleStreamItem(
            item: PositionStreamItem,
            accountKey: AccountKey,
        ) {
            if (item.instrumentType == InstrumentType.CURRENCY && item.isDomesticCurrency) {
                mergeBalance(item, accountKey)
                return
            }
            val currentAccount = _sheet.value.accounts[accountKey] ?: return
            val existing =
                currentAccount.positions.firstOrNull {
                    it.instrument.tscalpInstrumentId == item.tscalpInstrumentId
                }
            val merged: Position? =
                if (existing != null) {
                    existing.mergeStreamItem(item)
                } else {
                    val instrument = instrumentCache[accountKey]?.get(item.tscalpInstrumentId) ?: return
                    item.toPosition(instrument)
                }
            if (merged == null) return
            val newPositions =
                if (existing != null) {
                    currentAccount.positions.map { if (it === existing) merged else it }
                } else {
                    currentAccount.positions + merged
                }
            _sheet.update { current ->
                current.copy(accounts = current.accounts + (accountKey to currentAccount.copy(positions = newPositions)))
            }
        }

        private fun mergeBalance(
            item: PositionStreamItem,
            accountKey: AccountKey,
        ) {
            val currentAccount = _sheet.value.accounts[accountKey] ?: return
            val currency = item.currency ?: item.brokerName.domesticCurrency
            val existing = currentAccount.balances.firstOrNull { it.currency == currency }
            val newBalance =
                if (existing != null) {
                    existing.copy(
                        money = item.money ?: existing.money,
                        blocked = item.blocked ?: existing.blocked,
                    )
                } else {
                    AccountBalance(currency = currency, money = item.money ?: 0.0, blocked = item.blocked ?: 0.0)
                }
            val newBalances =
                if (existing != null) {
                    currentAccount.balances.map { if (it === existing) newBalance else it }
                } else {
                    currentAccount.balances + newBalance
                }
            _sheet.update { current ->
                current.copy(accounts = current.accounts + (accountKey to currentAccount.copy(balances = newBalances)))
            }
        }

        fun reapplyDebugFlags() =
            synchronized(startLock) {
                if (currentKeys.isEmpty()) return@synchronized
                logger.d(TAG, "reapplyDebugFlags: restarting stream")
                doStart(currentKeys, currentSandboxMode, forceRestart = true, seed = null)
            }

        /**
         * Merge [seed] в [_sheet]. Идемпотентно:
         * - если счёта ещё нет — кладём seed-снимок целиком (первый старт);
         * - если есть — обновляем только `balances` (positions из manager'а
         *   могут быть свежее, чем seed). Если seed.balances пуст (ошибка
         *   `getBalanceResult`) — оставляем существующие.
         */
        private fun applySeed(seed: PortfolioSheet) {
            val current = _sheet.value
            val merged = current.accounts.toMutableMap()
            for ((key, seedAccount) in seed.accounts) {
                val existing = merged[key]
                merged[key] =
                    when {
                        existing == null -> seedAccount
                        seedAccount.balances.isNotEmpty() -> existing.copy(balances = seedAccount.balances)
                        else -> existing
                    }
            }
            _sheet.value = PortfolioSheet(merged)
        }

        private suspend fun stateWatchdog() {
            while (currentCoroutineContext().isActive) {
                delay(STATE_TICK_MS)
                stateTracker.tick()
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        fun stop() =
            synchronized(startLock) {
                job?.cancel()
                job = null
                currentKeys = emptySet()
                currentSandboxMode = false
                instrumentCache.clear()
                _sheet.value = PortfolioSheet(emptyMap())
                stateTracker.onStop()
            }
    }

/**
 * Merge stream-события [item] поверх существующей [Position].
 *
 * `quantity` — из события; `currentPrice` / `averagePrice` / `profit` —
 * non-null fallback (partial-событие не обнуляет). `Type-preserving`:
 * возвращает тот же subtype (Share/Future/...). Диспетчеризация по
 * `this is XxxPosition`.
 *
 * Top-level extension (не метод `PositionFlowManager`): держит
 * `TooManyFunctions` под порогом без `@Suppress`.
 */
private fun Position.mergeStreamItem(item: PositionStreamItem): Position {
    val qty = item.quantity.toDouble()
    val price = item.currentPrice ?: currentPrice
    val avg = item.averagePositionPrice ?: averagePrice
    val profit = item.expectedYield ?: this.profit
    return when (this) {
        is SharePosition -> copy(quantity = qty, currentPrice = price, averagePrice = avg, profit = profit)
        is FuturePosition -> copy(quantity = qty, currentPrice = price, averagePrice = avg, profit = profit)
        is BondPosition -> copy(quantity = qty, currentPrice = price, averagePrice = avg, profit = profit)
        is OptionPosition -> copy(quantity = qty, currentPrice = price, averagePrice = avg, profit = profit)
        is EtfPosition -> copy(quantity = qty, currentPrice = price, averagePrice = avg, profit = profit)
        is CurrencyPosition -> copy(quantity = qty, currentPrice = price, averagePrice = avg, profit = profit)
        is OtherPosition -> copy(quantity = qty, currentPrice = price, averagePrice = avg, profit = profit)
    }
}
