package com.gitlab.biomorf.tscalp.domain.api.roles

import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult

/**
 * Опциональный контракт: предварительная проверка возможности
 * сделки (свободный остаток + наличие цены по инструменту).
 *
 * Доступ через `BrokerApi.tradeCheck` (`null` = не поддерживает).
 *
 * **Nullable-val и supertype.** По факту 2026-10-28:
 * - TInvest — реальный (`marketData.checkTradeAvailability`);
 * - BCS / Finam — заглушки (`TradeCheckResult.Success`),
 *   `tradeCheck = null`, без supertype.
 */
interface TradeCheckApi {
    /**
     * @param tscalpInstrumentId универсальный идентификатор.
     * @param brokerUid идентификатор в формате брокера (T-Invest =
     *   tscalpInstrumentId, BCS = figi). `null` — брокер сам
     *   разрешит (TInvest: `Error("Нет brokerUid")`).
     */
    suspend fun checkTradeAvailability(
        accountId: String,
        tscalpInstrumentId: String,
        brokerUid: String?,
        direction: OrderDirection,
        quantity: Long,
    ): TradeCheckResult
}
