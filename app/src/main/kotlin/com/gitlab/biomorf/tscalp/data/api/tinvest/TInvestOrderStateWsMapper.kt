package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.models.OrderState
import com.gitlab.biomorf.tscalp.transport.ktor.dto.MoneyValueDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.OrderStateDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.OrderStateStreamResponseDto
import java.time.Instant

/**
 * WS DTO → доменная OrderState.
 *
 * Проще, чем gRPC-версия: поля типизированы, без reflection.
 * subscription и ping игнорируются — это не данные заявки.
 */
object TInvestOrderStateWsMapper {
    fun mapOrderState(response: OrderStateStreamResponseDto): OrderState? {
        val dto = response.orderState ?: return null
        return fromDto(dto)
    }

    private fun fromDto(dto: OrderStateDto): OrderState =
        OrderState(
            orderId = dto.orderId,
            orderRequestId = dto.orderRequestId.ifBlank { null },
            ticker = dto.ticker,
            direction = dto.direction.removePrefix("ORDER_DIRECTION_"),
            limitPrice = dto.initialOrderPrice?.toDoubleOrNull(),
            executedPrice = dto.executedOrderPrice?.toDoubleOrNull(),
            quantity = dto.lotsRequested.toLongOrNull() ?: 0L,
            executedQuantity = dto.lotsExecuted.toLongOrNull() ?: 0L,
            status = dto.executionReportStatus.removePrefix("EXECUTION_REPORT_STATUS_"),
            updateTime = dto.createdAt?.let { parseEpochSeconds(it) },
            orderType = dto.orderType.removePrefix("ORDER_TYPE_").ifBlank { null },
            tscalpInstrumentId = dto.instrumentUid.ifBlank { null },
        )

    private fun MoneyValueDto.toDoubleOrNull(): Double = (units.toLongOrNull() ?: 0L).toDouble() + nano / 1_000_000_000.0

    private fun parseEpochSeconds(rfc3339: String): Long? =
        try {
            Instant.parse(rfc3339).epochSecond
        } catch (e: Exception) {
            null
        }
}
