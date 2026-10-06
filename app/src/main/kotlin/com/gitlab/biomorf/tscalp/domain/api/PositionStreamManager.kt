package com.gitlab.biomorf.tscalp.data.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject
import javax.inject.Singleton

import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.util.AppLogger

@Singleton
class PositionStreamManager @Inject constructor(
    private val brokerManager: BrokerManager
) {
    companion object {
        private const val TAG = "PositionStream"
    }

    private val _flow = MutableSharedFlow<PositionStreamItem>(replay = 1)
    val flow: SharedFlow<PositionStreamItem> = _flow.asSharedFlow()

    private var job: Job? = null
    private var currentAccountId: String? = null
    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e ->
            AppLogger.e(TAG, "Uncaught error in position stream", e)
        }
    )

    /**
     * Запускает единый источник обновлений позиций.
     * Идемпотентен для одного и того же accountId. При смене счёта —
     * перезапускает поток (иначе polling продолжит ходить по старому счёту).
     */
    fun start(accountId: String) {
        if (job?.isActive == true && currentAccountId == accountId) {
            AppLogger.d(TAG, "Поток позиций уже запущен для $accountId")
            return
        }
        if (currentAccountId != accountId) {
            AppLogger.d(TAG, "Перезапуск потока позиций: $currentAccountId → $accountId")
        }
        job?.cancel()
        currentAccountId = accountId
        job = scope.launch {
            val broker = brokerManager.getBroker(BrokerName.TINVEST) as? TInvestBrokerAPI
            if (broker == null || !broker.isInitialized) {
                AppLogger.e(TAG, "TInvestBrokerAPI не инициализирован, поток не запускаем")
                return@launch
            }
            broker.subscribePositions(accountId).collect { item ->
                AppLogger.d(TAG, "Элемент потока: ${item.tscalpInstrumentId}")
                _flow.emit(item)
            }
        }
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