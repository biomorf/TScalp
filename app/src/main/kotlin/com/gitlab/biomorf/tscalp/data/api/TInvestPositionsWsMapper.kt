package com.gitlab.biomorf.tscalp.data.api

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsFuturesDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsSecuritiesDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsStreamResponseDto

/**
 * WS DTO → PositionStreamItem.
 *
 * Даёт только неполные события (количество + идентификаторы),
 * без currentPrice / P&L — те же ограничения, что у gRPC-стрима.
 * Обогащение — задача PositionStreamManager.
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
        }
        response.position?.let { update ->
            update.securities.forEach { result.add(fromSecurity(it)) }
            update.futures.forEach { result.add(fromFuture(it)) }
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
}
