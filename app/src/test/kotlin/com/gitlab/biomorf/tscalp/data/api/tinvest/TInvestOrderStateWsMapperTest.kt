package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.transport.ktor.dto.MoneyValueDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.OrderStateDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.OrderStateStreamResponseDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PingDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TInvestOrderStateWsMapperTest {
    @Test
    fun `full order state maps to domain`() {
        val response =
            OrderStateStreamResponseDto(
                orderState =
                    OrderStateDto(
                        orderId = "order-1",
                        orderRequestId = "req-1",
                        ticker = "SBER",
                        direction = "ORDER_DIRECTION_BUY",
                        executionReportStatus = "EXECUTION_REPORT_STATUS_NEW",
                        initialOrderPrice = MoneyValueDto(currency = "rub", units = "290", nano = 500_000_000),
                        executedOrderPrice = null,
                        lotsRequested = "10",
                        lotsExecuted = "0",
                        createdAt = "2026-10-21T10:00:00Z",
                    ),
            )

        val result = TInvestOrderStateWsMapper.mapOrderState(response)

        assertNotNull(result)
        assertEquals("order-1", result!!.orderId)
        assertEquals("req-1", result.orderRequestId)
        assertEquals("SBER", result.ticker)
        assertEquals("BUY", result.direction)
        assertEquals(290.5, result.limitPrice!!, 0.0001)
        assertNull(result.executedPrice)
        assertEquals(10L, result.quantity)
        assertEquals(0L, result.executedQuantity)
        assertEquals("NEW", result.status)
        assertEquals(1_792_576_800L, result.updateTime)
    }

    @Test
    fun `blank orderRequestId maps to null`() {
        val response =
            OrderStateStreamResponseDto(
                orderState =
                    OrderStateDto(
                        orderId = "order-1",
                        orderRequestId = "",
                        lotsRequested = "1",
                        lotsExecuted = "0",
                    ),
            )

        val result = TInvestOrderStateWsMapper.mapOrderState(response)

        assertNotNull(result)
        assertNull(result!!.orderRequestId)
    }

    @Test
    fun `missing createdAt leaves updateTime null`() {
        val response =
            OrderStateStreamResponseDto(
                orderState =
                    OrderStateDto(
                        orderId = "order-1",
                        createdAt = null,
                    ),
            )

        val result = TInvestOrderStateWsMapper.mapOrderState(response)

        assertNotNull(result)
        assertNull(result!!.updateTime)
    }

    @Test
    fun `invalid createdAt parses to null`() {
        val response =
            OrderStateStreamResponseDto(
                orderState =
                    OrderStateDto(
                        orderId = "order-1",
                        createdAt = "not-a-date",
                    ),
            )

        val result = TInvestOrderStateWsMapper.mapOrderState(response)

        assertNotNull(result)
        assertNull(result!!.updateTime)
    }

    @Test
    fun `ping without order state returns null`() {
        val response =
            OrderStateStreamResponseDto(
                ping = PingDto(time = "2026-10-21T10:00:00Z"),
            )

        assertNull(TInvestOrderStateWsMapper.mapOrderState(response))
    }
}
