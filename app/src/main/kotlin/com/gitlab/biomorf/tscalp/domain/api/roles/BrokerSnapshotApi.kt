package com.gitlab.biomorf.tscalp.domain.api.roles

import com.gitlab.biomorf.tscalp.domain.models.Order
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem

/**
 * Опциональный контракт: unary-снимки для polling в менеджерах.
 *
 * Реализуется брокером, если polling с fallback gRPC → REST
 * живёт на уровне брокера / оркестратора и даёт снимки,
 * совместимые со stream-событиями.
 *
 * Доступ через `BrokerApi.snapshot` (`null` = не поддерживает).
 * Fallback-логика — внутри реализации (у T-Invest — в
 * `TInvestOrchestrator`). Менеджер не должен кастовать к
 * конкретной реализации — только `BrokerApi.snapshot`.
 *
 * См. `docs/ROADMAP.md` → `### TInvest-specific методы в *FlowManager`.
 */
interface BrokerSnapshotApi {
    /**
     * Последние цены по списку `tscalpInstrumentId`
     * (универсальные ключи инструментов).
     *
     * `null` в значении карты — цена недоступна для инструмента.
     * Fallback gRPC unary → REST на транспортные ошибки —
     * внутри реализации.
     */
    suspend fun pricesSnapshot(ids: List<String>): Map<String, Double?>

    /**
     * Снимок активных заявок счёта (обычные + stop).
     *
     * Используется `OrderFlowManager` в polling раз в 15 сек;
     * мержится со stream-событиями по `orderId`.
     */
    suspend fun ordersSnapshot(accountId: String): List<Order>

    /**
     * Снимок позиций счёта — `PositionStreamItem`, в котором
     * полные события (от polling) содержат P&L, а неполные
     * (от stream) обогащаются в
     * `PositionFlowManager.enrichAndCache`.
     *
     * **Не путать** с `BrokerApi.fetchPortfolioPositions` — тот
     * возвращает полный `PortfolioPosition` для UI
     * (`totalValue`, `profitPercent`, `priceChangePercent`).
     */
    suspend fun positionsSnapshot(accountId: String): List<PositionStreamItem>
}
