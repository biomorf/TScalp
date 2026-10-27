package com.gitlab.biomorf.tscalp.domain.api.roles

import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem

/**
 * Опциональный контракт: снимок позиций портфеля (full snapshot).
 *
 * Доступ через `BrokerApi.portfolio` (`null` = не поддерживает).
 * Supertype = «реализую все методы» (AGENTS «Nullable-val роли»):
 * TInvest, BCS — реальные; Finam — заглушка, `null`.
 *
 * **Не путать** с `BrokerSnapshotApi.positionsSnapshot` — тот partial
 * `PositionStreamItem` для merge со stream в `PositionFlowManager`.
 */
interface PortfolioApi {
    suspend fun fetchPortfolioPositions(
        accountId: String,
        sandboxMode: Boolean,
    ): List<PositionStreamItem>
}
