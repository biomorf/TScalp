package com.gitlab.biomorf.tscalp.domain.api.roles

import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.OrderListItem
import com.gitlab.biomorf.tscalp.domain.models.OrderResult
import com.gitlab.biomorf.tscalp.domain.models.StopOrderRequest

/**
 * Опциональный контракт: операции с заявками брокера.
 *
 * Реализуется брокером, если он поддерживает **все** шесть
 * методов. Доступ через `BrokerApi.orders` (`null` = не
 * поддерживает).
 *
 * **Nullable-val и supertype.** По правилу AGENTS.md
 * «Nullable-val роли»: supertype = «реализую все методы».
 * Если брокер реализует не все 6 — `orders = null` **без**
 * supertype. По grep 2026-10-25:
 * - TInvest — 6/6 (через `TInvestOrdersService`), supertype
 *   + `orders = this`;
 * - BCS — 1/6 (только `postOrder`), `orders = null`, без
 *   supertype;
 * - Finam — 0/6 (все заглушки), `orders = null`, без
 *   supertype.
 *
 * См. `docs/ROADMAP.md` → `### OrdersApi` (шаг #4) и
 * `### Рефакторинг ролей BrokerApi (nullable-val, Scenario2)`.
 */
interface OrdersApi {
    /**
     * Выставляет обычную заявку (лимитную или рыночную).
     */
    suspend fun postOrder(request: BrokerOrderRequest): OrderResult

    /**
     * Выставляет стоп-заявку (TAKE_PROFIT / STOP_LOSS / STOP_LIMIT).
     *
     * @return `stopOrderId` созданной заявки.
     */
    suspend fun postStopOrder(request: StopOrderRequest): String

    /**
     * Снимок активных обычных заявок счёта.
     */
    suspend fun getOrders(accountId: String): List<OrderListItem>

    /**
     * Снимок активных стоп-заявок счёта.
     */
    suspend fun getStopOrders(accountId: String): List<OrderListItem>

    /**
     * Отменяет обычную заявку по `orderId`.
     */
    suspend fun cancelOrder(
        accountId: String,
        orderId: String,
    )

    /**
     * Отменяет стоп-заявку по `stopOrderId`.
     */
    suspend fun cancelStopOrder(
        accountId: String,
        stopOrderId: String,
    )
}
