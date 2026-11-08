package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-контракт in-memory кэша trading availability по
 * `tscalpInstrumentId` — разбиение `OrdersViewModel` на 4 VM,
 * шаг 4.
 *
 * Обновляется [com.gitlab.biomorf.tscalp.domain.usecases.UpdateTradingStatusesUseCase]
 * (merge: `statuses + incoming`). Читается UI (через VM) и
 * Coordinator (фоновое обновление).
 *
 * Не персистится: статусы перезагружаются при старте
 * (`UpdateTradingStatusesUseCase` в init/Coord).
 */
interface TradingStatusRepository {
    val statuses: StateFlow<Map<String, TradingAvailability>>

    suspend fun update(statuses: Map<String, TradingAvailability>)

    suspend fun clear()
}
