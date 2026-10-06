package com.gitlab.biomorf.tscalp.domain.api

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
import javax.inject.Inject
import javax.inject.Singleton

import com.gitlab.biomorf.tscalp.data.repository.InvestRepository
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.util.AppLogger

/**
 * Единый источник рыночных цен для всего приложения.
 *
 * ViewModel декларируют интерес через [setInterest] / [clearInterest].
 * Менеджер держит объединение всех активных наборов uid и работает
 * по нему — gRPC-стрим плюс REST-fallback (см. подкоммит 3).
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

    /** Текущие интересы: ключ потребителя → набор uid. */
    private val interests = MutableStateFlow<Map<PriceConsumer, Set<String>>>(emptyMap())

    /** Единый поток цен для всех потребителей. */
    private val _prices = MutableSharedFlow<Pair<String, Double>>(replay = 0)
    val prices: SharedFlow<Pair<String, Double>> = _prices.asSharedFlow()

    /** Job gRPC-стрима. Пересоздаётся при изменении union. */
    private var streamJob: Job? = null

    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e ->
            AppLogger.e(TAG, "Uncaught error in price stream", e)
        }
    )

    /**
     * Регистрирует или обновляет интерес потребителя.
     * Если фактический union не изменился — стрим не трогаем.
     */
    fun setInterest(consumer: PriceConsumer, uids: Set<String>) {
        val oldUnion = currentUnion()
        interests.update { it + (consumer to uids) }
        val newUnion = currentUnion()
        if (newUnion != oldUnion) {
            AppLogger.d(TAG, "setInterest($consumer, size=${uids.size}), union changed: ${oldUnion.size} → ${newUnion.size}")
            restart(newUnion)
        }
    }

    /**
     * Снимает интерес потребителя.
     * Если union опустел — останавливает стрим.
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

    /** Объединение всех активных наборов uid. */
    private fun currentUnion(): Set<String> =
        interests.value.values.flatten().toSet()

    /**
     * Пересоздаёт gRPC-стрим под новый набор uid.
     * Пустой union → стрим остановлен, ничего не запускаем.
     */
    private fun restart(union: Set<String>) {
        streamJob?.cancel()
        streamJob = null
        if (union.isEmpty()) {
            AppLogger.d(TAG, "Union empty, gRPC stream stopped")
            return
        }
        AppLogger.d(TAG, "Starting gRPC stream for ${union.size} uid(s)")
        streamJob = scope.launch { runGrcpStream(union) }
    }

    /**
     * Подписывается на gRPC-стрим цен и эмитит каждое обновление
     * в общий поток [prices].
     *
     * При ошибке стрим логируется и завершается. Переподключение
     * не делаем — REST-fallback (подкоммит 3) закроет паузы.
     */
    private suspend fun runGrcpStream(union: Set<String>) {
        repository.subscribeLastPrices(BrokerName.TINVEST, union.toList())
            .catch { e -> AppLogger.e(TAG, "gRPC stream error", e) }
            .collect { pair -> _prices.emit(pair) }
    }
}