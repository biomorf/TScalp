package com.gitlab.biomorf.tscalp.domain.api

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.data.repository.InvestRepository
import com.gitlab.biomorf.tscalp.util.AppLogger

/**
 * Единый источник рыночных цен для всего приложения.
 *
 * ViewModel декларируют интерес через [setInterest] / [clearInterest].
 * Менеджер держит объединение всех активных наборов uid и работает
 * по нему — gRPC-стрим плюс REST-fallback раз в 5 секунд.
 *
 * Жизненный цикл стрима:
 * - пустой union → ничего не запущено;
 * - непустой union → запущены gRPC-стрим и REST-polling;
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

    /** Текущие интересы: ключ потребителя → набор uid. */
    private val interests = mutableMapOf<PriceConsumer, Set<String>>()

    /** Единый поток цен для всех потребителей. */
    private val _prices = MutableSharedFlow<Pair<String, Double>>(replay = 0)
    val prices: SharedFlow<Pair<String, Double>> = _prices.asSharedFlow()

    /** Job'ы gRPC-стрима и REST-polling. Пока не используются, но объявлены. */
    @Suppress("unused")
    private var streamJob: Job? = null

    @Suppress("unused")
    private var pollingJob: Job? = null

    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e ->
            AppLogger.e(TAG, "Uncaught error in price stream", e)
        }
    )

    /**
     * Регистрирует или обновляет интерес потребителя.
     * Если фактический union не изменился — ничего не делает.
     * Подкоммит 1: только сохраняем интерес, стрим не запускаем.
     */
    fun setInterest(consumer: PriceConsumer, uids: Set<String>) {
        interests[consumer] = uids
        AppLogger.d(TAG, "setInterest($consumer, size=${uids.size}), union=${currentUnion().size}")
        // TODO (подкоммит 2): перезапуск стрима при изменении union
    }

    /**
     * Снимает интерес потребителя.
     * Подкоммит 1: только удаляем запись, стрим не трогаем.
     */
    fun clearInterest(consumer: PriceConsumer) {
        interests.remove(consumer)
        AppLogger.d(TAG, "clearInterest($consumer), union=${currentUnion().size}")
        // TODO (подкоммит 2): перезапуск стрима при изменении union
    }

    /** Объединение всех активных наборов uid. */
    private fun currentUnion(): Set<String> =
        interests.values.flatten().toSet()
}