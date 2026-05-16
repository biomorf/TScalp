package com.example.tscalp.data.api

import android.util.Log
import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.domain.models.PositionStreamItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SharedPositionStreamManager {
    private const val TAG = "SharedPositionStream"
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
            val broker = ServiceLocator.getBrokerManager().getBroker("TInvest") as? TInvestInvestService
            if (broker == null) {
                Log.e(TAG, "TInvestInvestService не доступен")
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