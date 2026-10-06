package com.gitlab.biomorf.tscalp.domain.api

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext

import com.gitlab.biomorf.tscalp.data.repository.InvestRepository
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.util.AppLogger
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.PriceUpdate

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
 * подтягивает сам через InvestRepository.getLastPricesResult.
 */
@Singleton
class PriceStreamManager @Inject constructor(
    private val brokerManager: BrokerManager,
    private val repository: InvestRepository,
) {

    companion object {
        private const val TAG = "PriceStreamManager"
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

    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e ->
            AppLogger.e(TAG, "Uncaught error in price stream", e)
        }
    )

    /**
     * Регистрирует или обновляет интерес потребителя.
     * tscalpIds — набор `tscalpInstrumentId` (универсальные ключи
     * инструментов, не брокерские идентификаторы).
     * Если фактический union не изменился — стрим не трогаем.
     */
    fun setInterest(consumer: PriceConsumer, tscalpIds: Set<String>) {
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
        interests.value.values.flatten().toSet()

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
        AppLogger.d(TAG, "Starting gRPC stream for ${union.size} tscalpInstrumentId(s)")
        streamJob = scope.launch { runGrcpStream(union) }
        pollingJob = scope.launch { runPolling(union) }
    }

    /**
     * Подписывается на gRPC-стрим цен и эмитит каждое обновление
     * в общий поток [prices].
     *
     * При ошибке стрим логируется и завершается. Переподключение
     * не делаем — REST-fallback (подкоммит 3) закроет паузы.
     */
    private suspend fun runGrcpStream(union: Set<String>) {
        val broker = BrokerName.TINVEST
        repository.subscribeLastPrices(broker, union.toList())
            .catch { e -> AppLogger.e(TAG, "gRPC stream error", e) }
            .collect { (tscalpId, price) ->
                AppLogger.d(TAG, "[gRPC/${broker.key}] $tscalpId = $price")
                _prices.emit(PriceUpdate(broker, tscalpId, price))
            }
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
        val broker = BrokerName.TINVEST
        while (currentCoroutineContext().isActive) {
            delay(5_000)
            when (val result = repository.getLastPricesResult(union.toList())) {
                is AppResult.Success -> {
                    result.data.forEach { (tscalpId, price) ->
                        if (price != null) {
                            AppLogger.d(TAG, "[REST/${broker.key}] $tscalpId = $price")
                            _prices.emit(PriceUpdate(broker, tscalpId, price))
                        }
                    }
                }
                is AppResult.Failure -> {
                    AppLogger.w(TAG, "REST poll failed: ${result.error.message}", result.error.cause)
                }
            }
        }
    }
}