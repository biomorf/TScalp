package com.gitlab.biomorf.tscalp.domain.api.roles

import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult

/**
 * Опциональный контракт: предварительная проверка возможности
 * сделки (свободный остаток + наличие цены).
 *
 * Доступ через `BrokerApi.tradeCheck` (`null` = не поддерживает):
 * TInvest — реальный, BCS/Finam — заглушки, `null`.
 */
interface TradeCheckApi {
    suspend fun checkTradeAvailability(
        accountId: String,
        tscalpInstrumentId: String,
        brokerUid: String?,
        direction: OrderDirection,
        quantity: Long,
    ): TradeCheckResult
}
