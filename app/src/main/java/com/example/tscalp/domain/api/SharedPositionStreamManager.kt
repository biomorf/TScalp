package com.example.tscalp.data.api

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

import com.example.tscalp.di.BrokerManager
import com.example.tscalp.domain.models.PositionStreamItem
import com.example.tscalp.domain.models.BrokerName

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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Запускает единый источник обновлений позиций.
     * Вызывается один раз при инициализации приложения или после смены счёта.
     */
    fun start(accountId: String) {
        if (job?.isActive == true) {
            Log.d(TAG, "Поток позиций уже запущен")
            return
        }
        job?.cancel()
        job = scope.launch {
            val broker = brokerManager.getBroker(BrokerName.TINVEST) as? TInvestBrokerAPI
            if (broker == null || !broker.isInitialized) {
                Log.e(TAG, "TInvestBrokerAPI не инициализирован, поток не запускаем")
                return@launch
            }
            broker.subscribePositions(accountId).collect { item ->
                Log.d(TAG, "Элемент потока: ${item.instrumentUid}")
                _flow.emit(item)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}