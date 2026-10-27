package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability

/**
 * Domain-контракт рыночных данных (unary-запросы).
 *
 * Реализация — `data/repository/MarketDataRepositoryImpl.kt` (Phase 1 step 3).
 * Перенесён из `InvestRepository` без изменения поведения (Phase 1).
 *
 * Стримы цен живут в `PriceFlowManager` (через `StreamsApi`), здесь — только unary.
 */
interface MarketDataRepository {
    /** Последние цены по списку `tscalpInstrumentId`. */
    suspend fun getLastPricesResult(
        ids: List<String>,
    ): AppResult<Map<String, Double?>>

    /** Статусы доступности торговли по списку `tscalpInstrumentId`. */
    suspend fun getTradingStatusesResult(
        brokerName: BrokerName,
        ids: List<String>,
    ): AppResult<Map<String, TradingAvailability>>
}
