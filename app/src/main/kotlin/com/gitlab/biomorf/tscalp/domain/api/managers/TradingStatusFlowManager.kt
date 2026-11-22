package com.gitlab.biomorf.tscalp.domain.api.managers

import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.usecases.UpdateTradingStatusesUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единый источник periodic-обновления trading availability для
 * всего приложения.
 *
 * ViewModel декларируют интерес через [setInterest] / [clearInterest].
 * Интерес задаётся **сразу по всем брокерам** (`Map<BrokerName,
 * Set<String>>`): один вызов — один рестарт polling и один раунд
 * fetch. Покомпонентный вызов по брокерам давал бы N рестартов и
 * N лишних раундов на одну загрузку (rate-limit, T-9).
 *
 * Менеджер держит объединение всех активных наборов
 * `tscalpInstrumentId` и раз в [intervalMs] обновляет статусы через
 * [UpdateTradingStatusesUseCase]. SSoT — `TradingStatusRepository`
 * (обновляется внутри usecase), VM читают его напрямую (passthrough).
 *
 * Жизненный цикл polling:
 * - пустой union -> ничего не запущено;
 * - непустой union -> immediate fetch + periodic polling раз в [intervalMs];
 * - фактическое изменение union -> перезапуск polling;
 * - совпадающий union -> перезапуска нет.
 *
 * **Не путать** с `PositionFlowManager`/`PriceFlowManager`:
 * - `PositionFlowManager`: stream + polling позиций per-account;
 * - `PriceFlowManager`: gRPC-стрим цен + REST-fallback по `tscalpInstrumentId`;
 * - здесь: periodic fetch trading-statuses (биржи открываются/закрываются
 *   несколько раз в день, стрима нет).
 */
@Singleton
class TradingStatusFlowManager
    @Inject
    constructor(
        private val updateTradingStatusesUseCase: UpdateTradingStatusesUseCase,
        private val logger: Logger,
        private val managerDispatcher: CoroutineDispatcher,
    ) {
        /**
         * Тестовый шов (2d): интервал polling. В проде — [INTERVAL_MS];
         * переопределяется только internal-конструктором, чтобы тест
         * периодичности не ждал 60 с. `Long` в DI-граф не попадает:
         * Hilt использует трехаргументный `@Inject`-конструктор.
         */
        internal var intervalMs: Long = INTERVAL_MS
            private set

        internal constructor(
            updateTradingStatusesUseCase: UpdateTradingStatusesUseCase,
            logger: Logger,
            managerDispatcher: CoroutineDispatcher,
            intervalMs: Long,
        ) : this(updateTradingStatusesUseCase, logger, managerDispatcher) {
            this.intervalMs = intervalMs
        }

        companion object {
            private const val TAG = "TradingStatusFlowManager"
            private const val INTERVAL_MS = 60_000L
        }

        /**
         * Текущие интересы: consumer -> brokerName -> набор
         * tscalpInstrumentId. Брокер — часть ключа, потому что
         * `UpdateTradingStatusesUseCase.execute` принимает `brokerName`.
         */
        private val interests =
            MutableStateFlow<Map<TradingStatusConsumer, Map<BrokerName, Set<String>>>>(emptyMap())

        /** Единый job: polling под одним родителем. */
        private var job: Job? = null

        /**
         * Защищает check-then-set в [setInterest] / [clearInterest]
         * от race: два параллельных вызова иначе проходят проверку
         * union и создают по job'у (первый — orphan).
         */
        private val startLock = Any()

        private val scope =
            CoroutineScope(
                managerDispatcher.limitedParallelism(1) + SupervisorJob() +
                    CoroutineExceptionHandler { _, e ->
                        logger.e(TAG, "Uncaught error in trading-status polling", e)
                    },
            )

        /**
         * Регистрирует или обновляет интерес потребителя целиком
         * (все брокеры за один вызов). При первом непустом
         * объединении — immediate fetch и старт periodic polling.
         * Если фактический union не изменился — polling не трогаем.
         */
        fun setInterest(
            consumer: TradingStatusConsumer,
            byBroker: Map<BrokerName, Set<String>>,
        ) = synchronized(startLock) {
            val oldUnion = currentUnion()
            interests.update { current ->
                current + (consumer to byBroker.filterValues { it.isNotEmpty() })
            }
            val newUnion = currentUnion()
            if (newUnion != oldUnion) {
                logger.d(TAG, "setInterest($consumer, brokers=${byBroker.size}), union changed: ${oldUnion.size} -> ${newUnion.size}")
                restart(newUnion)
            }
        }

        /**
         * Снимает интерес потребителя (по всем брокерам).
         * Если union опустел — останавливает polling.
         */
        fun clearInterest(consumer: TradingStatusConsumer) =
            synchronized(startLock) {
                val oldUnion = currentUnion()
                interests.update { it - consumer }
                val newUnion = currentUnion()
                if (newUnion != oldUnion) {
                    logger.d(TAG, "clearInterest($consumer), union changed: ${oldUnion.size} -> ${newUnion.size}")
                    restart(newUnion)
                }
            }

        /** Объединение всех активных наборов tscalpInstrumentId. */
        private fun currentUnion(): Set<String> =
            interests.value.values
                .flatMap { it.values }
                .flatten()
                .toSet()

        /**
         * Пересоздаёт polling под новый union.
         * Пустой union -> polling остановлен, ничего не запускаем.
         *
         * **Caller must hold [startLock].**
         */
        private fun restart(union: Set<String>) {
            job?.cancel()
            job = null
            if (union.isEmpty()) {
                logger.d(TAG, "Union empty, polling stopped")
                return
            }
            logger.d(TAG, "Starting polling for ${union.size} instruments")
            job =
                scope.launch {
                    fetchAndUpdate()
                    while (isActive) {
                        delay(intervalMs)
                        fetchAndUpdate()
                    }
                }
        }

        /**
         * Обновляет статусы по всем активным интересам. Группирует
         * ids по брокеру и вызывает
         * [UpdateTradingStatusesUseCase.execute] на каждого брокера.
         */
        private suspend fun fetchAndUpdate() {
            val byBroker =
                interests.value.values
                    .flatMap { it.entries }
                    .groupBy({ it.key }, { it.value })
                    .mapValues { (_, sets) -> sets.flatten().toSet() }
            for ((brokerName, ids) in byBroker) {
                if (ids.isEmpty()) continue
                when (val result = updateTradingStatusesUseCase.execute(brokerName, ids.toList())) {
                    is AppResult.Success ->
                        logger.d(TAG, "Updated statuses for ${brokerName.displayName}: ${result.data.size}")
                    is AppResult.Failure ->
                        logger.e(TAG, "Failed to update statuses for ${brokerName.displayName}: ${result.error.message}", result.error.cause)
                }
            }
        }
    }
