package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Order
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.transport.ktor.RestOrder
import com.gitlab.biomorf.tscalp.transport.ktor.RestPosition
import com.gitlab.biomorf.tscalp.transport.ktor.RestQuotation
import com.gitlab.biomorf.tscalp.transport.ktor.RestStopOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TInvestRestMappersTest {
    @Test
    fun `RestOrder maps to Order correctly`() {
        val restOrder =
            RestOrder(
                orderId = "order-123",
                instrumentUid = "uid-sber",
                ticker = "SBER",
                classCode = "TQBR",
                direction = "ORDER_DIRECTION_BUY",
                lotsRequested = 10,
                lotsExecuted = 5,
                initialOrderPrice = RestQuotation(units = "150", nano = 500_000_000),
                executedOrderPrice = RestQuotation(units = "151", nano = 0),
                orderType = "ORDER_TYPE_LIMIT",
                executionReportStatus = "EXECUTION_REPORT_STATUS_NEW",
                orderDate = "2023-01-01T00:00:00Z",
            )

        val order: Order = restOrder.toOrder()

        assertEquals("order-123", order.orderId)
        assertEquals("SBER", order.ticker)
        assertEquals("uid-sber", order.tscalpInstrumentId)
        assertEquals("BUY", order.direction)
        assertEquals(150.5, order.price, 0.0001)
        assertEquals(10L, order.quantity)
        assertEquals(OrderTypeSelection.Limit, order.orderType)
        assertEquals("NEW", order.status)
        assertNull(order.orderDate)
        assertEquals(151.0, (order as Order.Regular).executedPrice!!, 0.0001)
        assertEquals(5L, order.executedQuantity)
    }

    @Test
    fun `RestStopOrder maps to Order correctly`() {
        val restStopOrder =
            RestStopOrder(
                stopOrderId = "stop-123",
                instrumentUid = "uid-gazp",
                ticker = "GAZP",
                classCode = "TQBR",
                direction = "ORDER_DIRECTION_SELL",
                lotsRequested = 2,
                stopPrice = RestQuotation(units = "200", nano = 0),
                limitPrice = RestQuotation(units = "199", nano = 0),
                orderType = "ORDER_TYPE_STOP_LIMIT",
                executionReportStatus = "EXECUTION_REPORT_STATUS_PARTIALLYFILL",
                createDate = "2023-01-01T00:00:00Z",
            )

        val stop: Order.Stop = restStopOrder.toOrder()

        assertEquals("stop-123", stop.orderId)
        assertEquals("GAZP", stop.ticker)
        assertEquals("uid-gazp", stop.tscalpInstrumentId)
        assertEquals("SELL", stop.direction)
        assertEquals(199.0, stop.price, 0.0001)
        assertEquals(200.0, stop.stopPrice, 0.0001)
        assertEquals(2L, stop.quantity)
        assertEquals(OrderTypeSelection.StopLimit, stop.orderType)
        assertEquals("PARTIALLYFILL", stop.status)
        assertNull(stop.orderDate)
    }

    @Test
    fun `RestPosition maps to PositionStreamItem with currentPrice zero fallback`() {
        val restPosition =
            RestPosition(
                instrumentUid = "uid-aapl",
                quantity = RestQuotation(units = "10", nano = 0),
                currentPrice = RestQuotation(units = "120", nano = 0),
                averagePositionPrice = RestQuotation(units = "100", nano = 0),
                expectedYield = RestQuotation(units = "200", nano = 0),
                instrumentType = "share",
            )

        val item: PositionStreamItem = restPosition.toStreamItem()

        assertEquals("uid-aapl", item.tscalpInstrumentId)
        assertEquals(BrokerName.TINVEST, item.brokerName)
        assertEquals(10L, item.quantity)
        assertEquals(120.0, item.currentPrice!!, 0.0001)
        assertEquals(100.0, item.averagePositionPrice!!, 0.0001)
        assertEquals(200.0, item.expectedYield!!, 0.0001)
        assertEquals("share", item.instrumentType)
        assertEquals("", item.ticker)
        assertEquals("", item.classCode)
        assertEquals("", item.isin)
    }

    @Test
    fun `RestPosition with null currentPrice yields zero in PositionStreamItem`() {
        val restPosition =
            RestPosition(
                instrumentUid = "uid-bond",
                quantity = RestQuotation(units = "5", nano = 0),
                currentPrice = null,
                averagePositionPrice = null,
                expectedYield = null,
                instrumentType = "bond",
            )

        val item: PositionStreamItem = restPosition.toStreamItem()

        assertEquals(0.0, item.currentPrice!!, 0.0001)
        assertNull(item.averagePositionPrice)
        assertNull(item.expectedYield)
    }
}
