package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.data.api.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.data.orchestrator.DebugConfig
import com.gitlab.biomorf.tscalp.data.repository.InvestRepository
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PriceUpdate
import com.gitlab.biomorf.tscalp.util.AppLogger
import com.gitlab.biomorf.tscalp.util.toAppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единый источник рыночных цен для всего приложения.
 *
 * ViewModel декларируют интерес через [setInterest] / [clearInterest].
 * Менеджер держит объединение всех активных наборов
 * `tscalpInstrumentId` и работает по нему — gRPC-стрим плюс
 * REST-fallback.
 *
 * Жизненный цикл стрима:
 * - пустой union → ничего не запущено;
 * - непустой union → запущен gRPC-стрим;
 * - фактическое изменение union → пересоздание стрима;
 * - совпадающий union → перезапуска нет.
 *
 * [prices] не имеет replay-кэша: актуальное состояние подписчик
 * подтягивает сам через `broker.pricesSnapshot` (оркестратор).
 */
@Singleton
class PriceFlowManager
    @Inject
    constructor(
        private val brokerManager: BrokerManager,
        private val repository: InvestRepository,
    ) {
        companion object {
            private const val TAG = "PriceFlowManager"

            /**
             * Задержки между попытками переподключения gRPC-стрима
             * при ошибке. Экспоненциальный backoff с cap 60 секунд —
             * см. ROADMAP → «Reconnect-loop для gRPC-стрима цен».
             */
            private val RETRY_DELAYS_MS = longArrayOf(1_000, 5_000, 15_000, 30_000, 60_000)
        }

        /** Текущие интересы: ключ потребителя → набор tscalpInstrumentId. */
        private val interests = MutableStateFlow<Map<PriceConsumer, Set<String>>>(emptyMap())

        /** Единый поток цен для всех потребителей. */
        private val _prices = MutableSharedFlow<PriceUpdate>(replay = 0)
        val prices: SharedFlow<PriceUpdate> = _prices.asSharedFlow()

        /** Job gRPC-стрима. Пересоздаётся при изменении union. */
        private var streamJob: Job? = null

        /** Job REST-polling. Пересоздаётся при изменении union. */
        private var pollingJob: Job? = null

        private val scope =
            CoroutineScope(
                Dispatchers.IO + SupervisorJob() +
                    CoroutineExceptionHandler { _, e ->
                        AppLogger.e(TAG, "Uncaught error in price stream", e)
                    },
            )

        /**
         * Регистрирует или обновляет интерес потребителя.
         * tscalpIds — набор `tscalpInstrumentId` (универсальные ключи
         * инструментов, не брокерские идентификаторы).
         * Если фактический union не изменился — стрим не трогаем.
         */
        fun setInterest(
            consumer: PriceConsumer,
            tscalpIds: Set<String>,
        ) {
            val oldUnion = currentUnion()
            interests.update { it + (consumer to tscalpIds) }
            val newUnion = currentUnion()
            if (newUnion != oldUnion) {
                AppLogger.d(TAG, "setInterest($consumer, size=${tscalpIds.size}), union changed: ${oldUnion.size} → ${newUnion.size}")
                restart(newUnion)
            }
        }

        /**
         * Снимает интерес потребителя.
         * Если union опустел — останавливает стрим.
         * tscalpIds — набор `tscalpInstrumentId`, а не брокерских идентификаторов.
         */
        fun clearInterest(consumer: PriceConsumer) {
            val oldUnion = currentUnion()
            interests.update { it - consumer }
            val newUnion = currentUnion()
            if (newUnion != oldUnion) {
                AppLogger.d(TAG, "clearInterest($consumer), union changed: ${oldUnion.size} → ${newUnion.size}")
                restart(newUnion)
            }
        }

        /** Объединение всех активных наборов tscalpInstrumentId. */
        private fun currentUnion(): Set<String> =
            interests.value.values
                .flatten()
                .toSet()

        /**
         * Принудительно перезапускает стрим и polling с текущими
         * флагами DebugConfig. Вызывается из UI при переключении
         * тумблеров (gRPC, WS, REST), чтобы применить их на лету.
         */
        fun reapplyDebugFlags() {
            restart(currentUnion())
        }

        /**
         * Пересоздаёт gRPC-стрим под новый набор tscalpInstrumentId.
         * Пустой union → стрим остановлен, ничего не запускаем.
         */
        private fun restart(union: Set<String>) {
            streamJob?.cancel()
            streamJob = null
            pollingJob?.cancel()
            pollingJob = null
            if (union.isEmpty()) {
                AppLogger.d(TAG, "Union empty, gRPC stream stopped")
                return
            }
            if (DebugConfig.forceRest) {
                AppLogger.d(TAG, "forceRest enabled: skipping gRPC stream, polling only")
            } else if (DebugConfig.disableGrpc && DebugConfig.disableWs) {
                AppLogger.d(TAG, "gRPC and WS disabled: skipping stream job, polling only")
            } else {
                AppLogger.d(TAG, "Starting gRPC stream for ${union.size} tscalpInstrumentId(s)")
                streamJob = scope.launch { runGrcpStream(union) }
            }
            pollingJob = scope.launch { runPolling(union) }
        }

        /**
         * Подписывается на gRPC-стрим цен и эмитит каждое обновление
         * в общий поток [prices].
         *
         * Переподключается при ошибке с экспоненциальным backoff
         * (1s / 5s / 15s / 30s / 60s, cap 60s). Polling работает
         * параллельно и прикрывает паузы между попытками.
         */
        private suspend fun runGrcpStream(union: Set<String>) {
            if (DebugConfig.forceRest) {
                AppLogger.d(TAG, "forceRest enabled: runGrcpStream bypassed")
                return
            }
            val broker = BrokerName.TINVEST
            var attempt = 0
            while (currentCoroutineContext().isActive) {
                try {
                    repository
                        .subscribeLastPrices(broker, union.toList())
                        .collect { (tscalpId, price) ->
                            AppLogger.d(TAG, "[manager/stream] $tscalpId = $price")
                            _prices.emit(PriceUpdate(broker, tscalpId, price))
                        }
                    // Сервер закрыл стрим штатно. Сбрасываем attempt
                    // и пробуем подключиться заново без задержки.
                    attempt = 0
                    AppLogger.d(TAG, "[prices] gRPC stream completed by server, reconnecting in 1s")
                    delay(1000) // Защита от busy-loop, если сервер закрывает стрим сразу
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempt++
                    val delayMs = retryDelayMs(attempt)
                    AppLogger.w(
                        TAG,
                        "[prices] gRPC error (attempt=$attempt), retry in ${delayMs}ms: ${e.message}",
                        e,
                    )
                    delay(delayMs)
                }
            }
        }

        /**
         * Задержка перед следующей попыткой переподключения.
         * attempt — номер попытки начиная с 1.
         */
        private fun retryDelayMs(attempt: Int): Long {
            val idx = (attempt - 1).coerceIn(0, RETRY_DELAYS_MS.lastIndex)
            return RETRY_DELAYS_MS[idx]
        }

        /**
         * REST-опрос цен раз в 5 секунд.
         *
         * Работает параллельно с gRPC-стримом и служит fallback'ом,
         * когда тиков от биржи нет (ночь, выходные, пауза торгов):
         * стрим молчит, а polling продолжает доставлять последнюю
         * известную биржевую цену.
         *
         * Null-значения отфильтровываем — если по какому-то tscalpInstrumentId цены
         * нет, не эмитим. Ошибки логируем и продолжаем цикл: одна
         * неудачная итерация не должна останавливать polling.
         */
        private suspend fun runPolling(union: Set<String>) {
            val brokerName = BrokerName.TINVEST
            val broker = brokerManager.getBroker(brokerName) as? TInvestBrokerAPI
            if (broker == null) {
                AppLogger.e(TAG, "TInvestBrokerAPI не инициализирован, polling не запускаем")
                return
            }
            while (currentCoroutineContext().isActive) {
                delay(5_000)
                try {
                    val prices = broker.pricesSnapshot(union.toList())
                    prices.forEach { (tscalpId, price) ->
                        if (price != null) {
                            AppLogger.d(TAG, "[manager/polling/${brokerName.key}] $tscalpId = $price")
                            _prices.emit(PriceUpdate(brokerName, tscalpId, price))
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "REST poll failed: ${e.toAppError(BrokerName.TINVEST).message}", e)
                }
            }
        }
    }
