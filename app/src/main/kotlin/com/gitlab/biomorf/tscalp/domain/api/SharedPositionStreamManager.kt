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
import javax.inject.Inject
import javax.inject.Singleton

import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.util.AppLogger

@Singleton
class SharedPositionStreamManager @Inject constructor(
    private val brokerManager: BrokerManager
) {
    companion object {
        private const val TAG = "SharedPositionStream"
    }

    private val _flow = MutableSharedFlow<PositionStreamItem>(replay = 1)
    val flow: SharedFlow<PositionStreamItem> = _flow.asSharedFlow()

    private var job: Job? = null
    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e ->
            AppLogger.e(TAG, "Uncaught error in position stream", e)
        }
    )

    /**
     * Запускает единый источник обновлений позиций.
     * Вызывается один раз при инициализации приложения или после смены счёта.
     */
    fun start(accountId: String) {
        if (job?.isActive == true) {
            AppLogger.d(TAG, "Поток позиций уже запущен")
            return
        }
        job?.cancel()
        job = scope.launch {
            val broker = brokerManager.getBroker(BrokerName.TINVEST) as? TInvestBrokerAPI
            if (broker == null || !broker.isInitialized) {
                AppLogger.e(TAG, "TInvestBrokerAPI не инициализирован, поток не запускаем")
                return@launch
            }
            broker.subscribePositions(accountId).collect { item ->
                AppLogger.d(TAG, "Элемент потока: ${item.instrumentUid}")
                _flow.emit(item)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}