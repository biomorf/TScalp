package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.data.api.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.util.AppLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PositionStreamManager
    @Inject
    constructor(
        private val brokerManager: BrokerManager,
    ) {
        companion object {
            private const val TAG = "PositionStream"
        }

        private val _flow = MutableSharedFlow<PositionStreamItem>(replay = 1)
        val flow: SharedFlow<PositionStreamItem> = _flow.asSharedFlow()

        /**
         * Кэш последних полных снимков по инструментам.
         *
         * Полный снимок — событие с непустым `currentPrice` (приходит
         * от polling). Неполные события от gRPC-стрима (только количество
         * и идентификаторы) обогащаются полями P&L из этого кэша.
         *
         * Чистится при смене счёта в [start].
         */
        private val snapshotCache = mutableMapOf<String, PositionStreamItem>()

        private var job: Job? = null
        private var currentAccountId: String? = null
        private val scope =
            CoroutineScope(
                Dispatchers.IO + SupervisorJob() +
                    CoroutineExceptionHandler { _, e ->
                        AppLogger.e(TAG, "Uncaught error in position stream", e)
                    },
            )

        /**
         * Запускает единый источник обновлений позиций.
         * Идемпотентен для одного и того же accountId. При смене счёта —
         * перезапускает поток (иначе polling продолжит ходить по старому счёту).
         */
        fun start(
            accountId: String,
            forceRestart: Boolean = false,
        ) {
            if (!forceRestart && job?.isActive == true && currentAccountId == accountId) {
                AppLogger.d(TAG, "Поток позиций уже запущен для $accountId")
                return
            }
            if (currentAccountId != accountId) {
                AppLogger.d(TAG, "Перезапуск потока позиций: $currentAccountId → $accountId")
                snapshotCache.clear()
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
                    broker.subscribePositions(accountId).collect { item ->
                        val enriched = enrichAndCache(item) ?: return@collect
                        AppLogger.d(
                            TAG,
                            "Элемент потока: ${enriched.tscalpInstrumentId}, " +
                                "qty=${enriched.quantity}, " +
                                "price=${enriched.currentPrice}, " +
                                "yield=${enriched.expectedYield}",
                        )
                        _flow.emit(enriched)
                    }
                }
        }

        /**
         * Обогащает событие полями P&L из кэша последнего полного снимка.
         *
         * Маркер полного события — непустой [PositionStreamItem.currentPrice].
         * Polling заполняет его (даже нулём), gRPC-стрим — оставляет `null`.
         *
         * Если событие полное — обновляем кэш и возвращаем как есть.
         * Если неполное и кэш есть — дополняем поля, которые gRPC не даёт.
         * Если неполное и кэша нет (первое событие после старта) —
         * возвращаем как есть; UI покажет заглушку для P&L.
         */
        internal fun enrichAndCache(item: PositionStreamItem): PositionStreamItem? {
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

        @OptIn(ExperimentalCoroutinesApi::class)
        fun reapplyDebugFlags() {
            val accountId = currentAccountId ?: return
            AppLogger.d(TAG, "reapplyDebugFlags: restarting stream")
            job?.cancel()
            start(accountId, forceRestart = true)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        fun stop() {
            job?.cancel()
            job = null
            currentAccountId = null
            // Сбрасываем replay-кэш, чтобы новые подписчики
            // не получили устаревший элемент от прошлой сессии.
            _flow.resetReplayCache()
        }
    }
