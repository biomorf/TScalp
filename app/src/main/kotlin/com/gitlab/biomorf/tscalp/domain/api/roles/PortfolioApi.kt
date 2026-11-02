package com.gitlab.biomorf.tscalp.domain.api.roles

import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio

/**
 * Опциональный контракт: полный снимок одного счёта (позиции + балансы).
 *
 * Доступ через `BrokerApi.portfolio` (`null` = не поддерживает).
 * Supertype = «реализую все методы» (AGENTS «Nullable-val роли»):
 * TInvest, BCS — реализующие (BCS — заглушка с пустыми positions,
 * см. `BcsBrokerApi` KDoc); Finam — заглушка, `null`.
 *
 * **Не путать** с `BrokerSnapshotApi.positionsSnapshot` — тот partial
 * `PositionStreamItem` для merge со stream в `PositionFlowManager`.
 */
interface PortfolioApi {
    suspend fun fetchPortfolioPositions(
        accountId: String,
        sandboxMode: Boolean,
    ): AccountPortfolio
}
