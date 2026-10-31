package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.transport.ktor.dto.MoneyValueDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsFuturesDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsSecuritiesDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsStreamResponseDto

/**
 * WS DTO → PositionStreamItem.
 *
 * Даёт только неполные события (количество + идентификаторы),
 * без currentPrice / P&L — те же ограничения, что у gRPC-стрима.
 * Обогащение — задача PositionFlowManager.
 *
 * ping / subscriptions / пустые сообщения игнорируются.
 */
object TInvestPositionsWsMapper {
    /**
     * Одно WS-сообщение → список событий позиций.
     *
     * initialPositions и position — оба могут содержать несколько
     * инструментов; UI ждёт по одному элементу на инструмент.
     */
    fun mapAll(response: PositionsStreamResponseDto): List<PositionStreamItem> {
        val result = mutableListOf<PositionStreamItem>()
        response.initialPositions?.let { snapshot ->
            snapshot.securities.forEach { result.add(fromSecurity(it)) }
            snapshot.futures.forEach { result.add(fromFuture(it)) }
            result.addAll(fromMoneyAndBlocked(snapshot.money, snapshot.blocked))
        }
        response.position?.let { update ->
            update.securities.forEach { result.add(fromSecurity(it)) }
            update.futures.forEach { result.add(fromFuture(it)) }
            result.addAll(fromMoneyAndBlocked(update.money, update.blocked))
        }
        return result
    }

    private fun fromSecurity(dto: PositionsSecuritiesDto): PositionStreamItem =
        PositionStreamItem(
            tscalpInstrumentId = dto.instrumentUid,
            brokerName = BrokerName.TINVEST,
            ticker = dto.ticker,
            quantity = dto.balance?.toLongOrNull() ?: 0L,
            classCode = dto.classCode,
            instrumentType = dto.instrumentType.ifBlank { "share" },
        )

    private fun fromFuture(dto: PositionsFuturesDto): PositionStreamItem =
        PositionStreamItem(
            tscalpInstrumentId = dto.instrumentUid,
            brokerName = BrokerName.TINVEST,
            ticker = dto.ticker,
            quantity = dto.balance?.toLongOrNull() ?: 0L,
            classCode = dto.classCode,
            instrumentType = "futures",
        )

    /**
     * Валютные позиции: money (свободные) + blocked (заблокированные).
     *
     * Приходят как account-level массивы MoneyValue с одним полем
     * currency (без uid и ticker). Склеиваем по currency в один
     * PositionStreamItem на валюту — money и blocked могут быть
     * в разных массивах, но относятся к одной валютной позиции.
     *
     * tscalpInstrumentId оставлен пустым: WS не знает uid валютной
     * позиции. Слияние с item'ом из polling выполняется в
     * PositionFlowManager по полю currency.
     */
    private fun fromMoneyAndBlocked(
        money: List<MoneyValueDto>,
        blocked: List<MoneyValueDto>,
    ): List<PositionStreamItem> {
        if (money.isEmpty() && blocked.isEmpty()) return emptyList()
        val byCurrency = mutableMapOf<String, PositionStreamItem>()
        money.forEach { dto ->
            if (dto.currency.isBlank()) return@forEach
            val current = byCurrency[dto.currency] ?: emptyCurrencyItem(dto.currency)
            byCurrency[dto.currency] = current.copy(money = dto.toAmountOrZero())
        }
        blocked.forEach { dto ->
            if (dto.currency.isBlank()) return@forEach
            val current = byCurrency[dto.currency] ?: emptyCurrencyItem(dto.currency)
            byCurrency[dto.currency] = current.copy(blocked = dto.toAmountOrZero())
        }
        return byCurrency.values.toList()
    }

    private fun emptyCurrencyItem(currency: String): PositionStreamItem =
        PositionStreamItem(
            tscalpInstrumentId = "",
            brokerName = BrokerName.TINVEST,
            ticker = "",
            quantity = 0L,
            instrumentType = "currency",
            currency = currency,
        )

    private fun MoneyValueDto.toAmountOrZero(): Double = (units.toLongOrNull() ?: 0L).toDouble() + nano / 1_000_000_000.0
}
