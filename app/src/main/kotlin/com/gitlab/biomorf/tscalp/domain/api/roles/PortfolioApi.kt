package com.gitlab.biomorf.tscalp.domain.api.roles

import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem

/**
 * Опциональный контракт: снимок позиций портфеля (full snapshot).
 *
 * Доступ через `BrokerApi.portfolio` (`null` = не поддерживает).
 *
 * **Nullable-val и supertype.** По правилу AGENTS.md «Nullable-val
 * роли»: supertype = «реализую все методы». По факту 2026-10-28:
 * - TInvest — реальный (`marketData.fetchPortfolioPositions`);
 * - BCS — реальный (REST `/portfolio` + парсинг JSON);
 * - Finam — заглушка (`emptyList()`), `portfolio = null`, без
 *   supertype.
 *
 * **Не путать** с `BrokerSnapshotApi.positionsSnapshot` — тот
 * partial `PositionStreamItem` для merge со stream в
 * `PositionFlowManager` (без `currentPrice`). Здесь — full
 * snapshot с `currentPrice`.
 */
interface PortfolioApi {
    /**
     * Снимок позиций портфеля. Возвращает domain-модель
     * `PositionStreamItem` (без presentation-полей).
     * UI-конвертация в `PortfolioPosition` — в
     * `PortfolioRepositoryImpl` через `toPortfolioPosition()`.
     */
    suspend fun fetchPortfolioPositions(
        accountId: String,
        sandboxMode: Boolean,
    ): List<PositionStreamItem>
}
