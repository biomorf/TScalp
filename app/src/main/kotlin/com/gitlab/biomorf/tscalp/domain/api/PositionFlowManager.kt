package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.data.api.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Агрегирует обновления позиций счёта из двух источников:
 *
 * 1. **Stream** (`subscribePositions`, gRPC + WS fallback).
 *    Мгновенные события изменения количества. Отдаёт неполные
 *    [PositionStreamItem]: без `currentPrice` / `averagePositionPrice` /
 *    `expectedYield` (стрим их не присылает).
 * 2. **Snapshot polling** (`positionsSnapshot`, gRPC unary + REST
 *    fallback, раз в [POLLING_INTERVAL_MS]). Полные снимки
 *    с P&L. Обогащают неполные события stream-уровня из кэша.
 *
 * Merge-семантика — через [enrichAndCache]: полное событие (от
 * polling, `currentPrice != null`) обновляет кэш, неполное (от
 * stream) дополняется полями P&L из кэша. Если кэша нет —
 * неполное событие отдаётся как есть (UI покажет заглушку).
 *
 * Scope — single-threaded (`Dispatchers.IO.limitedParallelism(1)`),
 * поэтому доступ к [snapshotCache] не требует синхронизации:
 * stream и polling выполняются последовательно на одном worker'е.
 *
 * TInvest-specific: [brokerManager.getBroker] приводится к
 * [TInvestBrokerAPI] ради вызова `positionsSnapshot`, который не
 * входит в `BrokerApi` (polling с fallback gRPC→REST живёт в
 * оркестраторе). Для BCS / Finam — отдельная задача
 * (см. ROADMAP → «TInvest-specific методы в *FlowManager»).
 */
@Singleton
class PositionFlowManager
    @Inject
    constructor(
        private val brokerManager: BrokerManager,
    ) {
        companion object {
            private const val TAG = "PositionFlowManager"
            private const val POLLING_INTERVAL_MS = 10_000L
        }

        private val _flow = MutableSharedFlow<PositionStreamItem>(replay = 1)
        val flow: SharedFlow<PositionStreamItem> = _flow.asSharedFlow()

        /**
         * Кэш последних полных снимков по инструментам.
         *
         * Полный снимок — событие с непустым `currentPrice` (приходит
         * от polling). Неполные события от stream-уровня (только
         * количество и идентификаторы) обогащаются полями P&L из
         * этого кэша.
         *
         * Чистится при смене счёта в [doStart].
         */
        private val snapshotCache = mutableMapOf<String, PositionStreamItem>()

        private var job: Job? = null
        private var currentAccountId: String? = null

        /**
         * Защищает check-then-set в [start] / [stop] / [reapplyDebugFlags]
         * от race: два параллельных вызова иначе проходят проверку
         * идемпотентности и создают по job'у (первый — orphan).
         */
        private val startLock = Any()

        private val scope =
            CoroutineScope(
                Dispatchers.IO.limitedParallelism(1) + SupervisorJob() +
                    CoroutineExceptionHandler { _, e ->
                        AppLogger.e(TAG, "Uncaught error in position stream", e)
                    },
            )

        /**
         * Запускает единый источник обновлений позиций.
         * Идемпотентен для одного и того же accountId. При смене
         * счёта — перезапускает оба источника и очищает кэш +
         * replay-кэш [flow].
         */
        fun start(
            accountId: String,
            forceRestart: Boolean = false,
        ) = synchronized(startLock) {
            doStart(accountId, forceRestart)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun doStart(
            accountId: String,
            forceRestart: Boolean,
        ) {
            if (!forceRestart && job?.isActive == true && currentAccountId == accountId) {
                AppLogger.d(TAG, "Поток позиций уже запущен для $accountId")
                return
            }
            if (currentAccountId != accountId) {
                AppLogger.d(TAG, "Перезапуск потока позиций: $currentAccountId → $accountId")
                snapshotCache.clear()
                _flow.resetReplayCache()
            }
            job?.cancel()
            currentAccountId = accountId
            job =
                scope.launch {
                    val broker = brokerManager.getBroker(BrokerName.TINVEST) as? TInvestBrokerAPI
                    if (broker == null || !broker.isInitialized) {
                        AppLogger.e(TAG, "TInvestBrokerAPI не инициализирован, поток не запускаем")
                        return@launch
                    }
                    coroutineScope {
                        launch { collectStream(broker, accountId) }
                        launch { runPolling(broker, accountId) }
                    }
                }
        }

        private suspend fun collectStream(
            broker: TInvestBrokerAPI,
            accountId: String,
        ) {
            try {
                broker.subscribePositions(accountId).collect { item ->
                    emitEnriched(item, source = "stream")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "stream error: ${e.message}", e)
            }
        }

        private suspend fun runPolling(
            broker: TInvestBrokerAPI,
            accountId: String,
        ) {
            while (currentCoroutineContext().isActive) {
                try {
                    val snapshot = broker.positionsSnapshot(accountId)
                    AppLogger.d(TAG, "polling: ${snapshot.size} positions")
                    for (item in snapshot) {
                        emitEnriched(item, source = "polling")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e.message?.contains("NOT_FOUND") == true) {
                        AppLogger.w(TAG, "polling: account not found — stopping")
                        return
                    }
                    AppLogger.w(TAG, "polling error: ${e.message}")
                }
                delay(POLLING_INTERVAL_MS)
            }
        }

        private suspend fun emitEnriched(
            item: PositionStreamItem,
            source: String,
        ) {
            val enriched = enrichAndCache(item) ?: return
            AppLogger.d(
                TAG,
                "[$source] ${enriched.tscalpInstrumentId}, " +
                    "qty=${enriched.quantity}, " +
                    "price=${enriched.currentPrice}, " +
                    "yield=${enriched.expectedYield}",
            )
            _flow.emit(enriched)
        }

        /**
         * Обогащает событие полями P&L из кэша последнего полного снимка.
         *
         * Маркер полного события — непустой [PositionStreamItem.currentPrice].
         * Polling заполняет его (даже нулём), stream-уровень — оставляет `null`.
         *
         * Если событие полное — обновляем кэш и возвращаем как есть.
         * Если неполное и кэш есть — дополняем поля, которые stream
         * не даёт. Если неполное и кэша нет (первое событие после
         * старта) — возвращаем как есть; UI покажет заглушку для P&L.
         */
        private fun enrichAndCache(item: PositionStreamItem): PositionStreamItem? {
            // Валютная позиция из WS (money/blocked): приходит без
            // tscalpInstrumentId и ticker — только currency. Сливаем
            // в закэшированный item из polling по currency. Если
            // polling ещё не пришёл — null (ждём, чтобы не создавать
            // дубликат карточки).
            if (item.instrumentType == "currency" && item.tscalpInstrumentId.isBlank()) {
                val cached =
                    snapshotCache.values.firstOrNull {
                        it.instrumentType == "currency" && it.currency == item.currency
                    } ?: return null
                val merged =
                    cached.copy(
                        money = item.money ?: cached.money,
                        blocked = item.blocked ?: cached.blocked,
                    )
                snapshotCache[cached.tscalpInstrumentId] = merged
                return merged
            }
            val isFull = item.currentPrice != null
            if (isFull) {
                snapshotCache[item.tscalpInstrumentId] = item
                return item
            }
            val cached = snapshotCache[item.tscalpInstrumentId] ?: return item
            return item.copy(
                currentPrice = cached.currentPrice,
                averagePositionPrice = cached.averagePositionPrice,
                expectedYield = cached.expectedYield,
                isin = cached.isin,
                pointValue = cached.pointValue,
            )
        }

        fun reapplyDebugFlags() =
            synchronized(startLock) {
                val accountId = currentAccountId ?: return@synchronized
                AppLogger.d(TAG, "reapplyDebugFlags: restarting stream")
                doStart(accountId, forceRestart = true)
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        fun stop() =
            synchronized(startLock) {
                job?.cancel()
                job = null
                currentAccountId = null
                // Сбрасываем replay-кэш, чтобы новые подписчики
                // не получили устаревший элемент от прошлой сессии.
                _flow.resetReplayCache()
            }
    }
