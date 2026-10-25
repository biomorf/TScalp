package com.gitlab.biomorf.tscalp.domain.api.roles

import com.gitlab.biomorf.tscalp.domain.models.OrderState
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import kotlinx.coroutines.flow.Flow

/**
 * Опциональный контракт: стрим-методы брокера (gRPC / WS).
 *
 * Реализуется брокером, если он поддерживает хотя бы один
 * стрим-метод. Доступ через `BrokerApi.streams`
 * (`null` = не поддерживает).
 *
 * **Только стрим.** Polling сюда не входит — он живёт в
 * [BrokerSnapshotApi] (`pricesSnapshot` / `ordersSnapshot` /
 * `positionsSnapshot`). BCS: `streams = null`, потому что
 * рабочий стрим-транспорт у BCS отсутствует; polling-путь для
 * BCS — отдельная задача (`### Реализация BCS Broker API`).
 *
 * См. `docs/ROADMAP.md` → `### StreamsApi: выделение роли
 * из BrokerApi` (шаг #1 плана) и `### Рефакторинг ролей
 * BrokerApi (nullable-val, Scenario2)`.
 */
interface StreamsApi {
    /**
     * Единый поток обновлений позиций по счёту.
     *
     * Скрывает внутри себя каскад fallback (gRPC → WebSocket).
     * Polling в этот поток не входит — он в
     * [BrokerSnapshotApi.positionsSnapshot], merge делает
     * `PositionFlowManager`.
     *
     * При подписке брокер может эмитить текущий снапшот
     * (например, T-Invest с `withInitialPositions = true`).
     */
    fun subscribePositions(accountId: String): Flow<PositionStreamItem>

    /**
     * Единый поток событий изменения статуса заявок.
     *
     * Event-only: снапшот активных заявок не эмитится.
     * Полный снимок — через [BrokerSnapshotApi.ordersSnapshot]
     * в `OrderFlowManager` (polling раз в 15 сек).
     *
     * `suspend` — как в `BrokerApi.subscribeOrderState`
     * (контракт совпадает с fat-интерфейсом).
     */
    suspend fun subscribeOrderState(accountId: String): Flow<OrderState>

    /**
     * Стрим последних цен по списку `tscalpInstrumentId`
     * (универсальные ключи инструментов).
     *
     * Один поток на все интересы; incremental subscription
     * (SUBSCRIBE/UNSUBSCRIBE) — на стороне брокера (у T-Invest —
     * `TInvestPricesWebSocket` / gRPC).
     */
    fun subscribeLastPrices(uids: List<String>): Flow<Pair<String, Double>>
}
