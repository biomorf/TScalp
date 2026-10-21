package com.gitlab.biomorf.tscalp.data.api

import com.gitlab.biomorf.tscalp.transport.ktor.dto.MarketDataResponseDto

/**
 * WS DTO → пара (tscalpInstrumentId, цена).
 *
 * Используется PriceFlowManager через оркестратор.
 * subscribeLastPriceResponse и ping игнорируются — это не данные.
 *
 * Возвращает null, если сообщение не содержит lastPrice или price null.
 */
object TInvestPricesWsMapper {
    fun mapLastPrice(response: MarketDataResponseDto): Pair<String, Double>? {
        val lp = response.lastPrice ?: return null
        val price = lp.price ?: return null
        val uid = lp.instrumentUid
        if (uid.isBlank()) return null
        val value = (price.units.toLongOrNull() ?: 0L).toDouble() + price.nano / 1_000_000_000.0
        return uid to value
    }
}
