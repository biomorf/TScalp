package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PortfolioPosition

/**
 * Domain-контракт портфеля и баланса.
 *
 * Реализация — `data/repository/PortfolioRepositoryImpl.kt` (Phase 1 step 3).
 * Перенесён из `InvestRepository` без изменения поведения (Phase 1).
 *
 * @see com.gitlab.biomorf.tscalp.domain.api.roles.BrokerSnapshotApi
 * @see com.gitlab.biomorf.tscalp.domain.api.roles.BalanceApi
 */
interface PortfolioRepository {
    /** Позиции портфеля (full snapshot, конвертируется в `PortfolioPosition`). */
    suspend fun fetchPositionsResult(
        brokerName: BrokerName,
        accountId: String,
        sandboxMode: Boolean,
    ): AppResult<List<PortfolioPosition>>

    /** Свободный остаток по счёту. */
    suspend fun getBalanceResult(
        accountId: String,
    ): AppResult<Double>
}
