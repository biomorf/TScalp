package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Position

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
    /** Позиции портфеля (full snapshot, domain-модель `Position`). */
    suspend fun fetchPositionsResult(
        brokerName: BrokerName,
        accountId: String,
        sandboxMode: Boolean,
    ): AppResult<List<Position>>

    /** Свободный остаток по счёту. */
    suspend fun getBalanceResult(
        accountId: String,
    ): AppResult<Double>
}
