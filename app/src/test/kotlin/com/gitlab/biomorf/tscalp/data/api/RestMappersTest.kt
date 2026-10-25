package com.gitlab.biomorf.tscalp.data.api

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.OrderListItem
import com.gitlab.biomorf.tscalp.domain.models.PortfolioPosition
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.transport.ktor.RestOrder
import com.gitlab.biomorf.tscalp.transport.ktor.RestPosition
import com.gitlab.biomorf.tscalp.transport.ktor.RestQuotation
import com.gitlab.biomorf.tscalp.transport.ktor.RestStopOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestMappersTest {
    @Test
    fun `RestOrder maps to OrderListItem correctly`() {
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

        val item: OrderListItem = restOrder.toOrderListItem()

        assertEquals("order-123", item.orderId)
        assertEquals("SBER", item.ticker)
        assertEquals("uid-sber", item.tscalpInstrumentId)
        assertEquals("BUY", item.direction)
        assertEquals(150.5, item.price, 0.0001)
        assertNull(item.stopPrice)
        assertEquals(10L, item.quantity)
        assertEquals("LIMIT", item.type)
        assertEquals("NEW", item.status)
        assertNull(item.orderDate)
        assertEquals(false, item.isStopOrder)
    }

    @Test
    fun `RestStopOrder maps to OrderListItem correctly`() {
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

        val item: OrderListItem = restStopOrder.toOrderListItem()

        assertEquals("stop-123", item.orderId)
        assertEquals("GAZP", item.ticker)
        assertEquals("uid-gazp", item.tscalpInstrumentId)
        assertEquals("SELL", item.direction)
        assertEquals(200.0, item.price, 0.0001)
        assertEquals(200.0, item.stopPrice!!, 0.0001)
        assertEquals(2L, item.quantity)
        assertEquals("STOP_LIMIT", item.type)
        assertEquals("PARTIALLYFILL", item.status)
        assertNull(item.orderDate)
        assertEquals(true, item.isStopOrder)
    }

    @Test
    fun `RestPosition maps to PortfolioPosition with calculated percent`() {
        val restPosition =
            RestPosition(
                instrumentUid = "uid-aapl",
                quantity = RestQuotation(units = "10", nano = 0),
                currentPrice = RestQuotation(units = "120", nano = 0),
                averagePositionPrice = RestQuotation(units = "100", nano = 0),
                expectedYield = RestQuotation(units = "200", nano = 0),
                instrumentType = "share",
            )

        val pos: PortfolioPosition = restPosition.toPortfolioPosition()

        assertEquals("uid-aapl", pos.tscalpInstrumentId)
        assertEquals(10L, pos.quantity)
        assertEquals(120.0, pos.currentPrice, 0.0001)
        assertEquals(100.0, pos.averagePrice!!, 0.0001)
        assertEquals(200.0, pos.profit!!, 0.0001)
        assertEquals(20.0, pos.profitPercent!!, 0.0001)
        assertEquals(1200.0, pos.totalValue, 0.0001)
        assertEquals("share", pos.instrumentType)
    }

    @Test
    fun `RestPosition without expectedYield has null profit and percent`() {
        val restPosition =
            RestPosition(
                instrumentUid = "uid-bond",
                quantity = RestQuotation(units = "5", nano = 0),
                currentPrice = RestQuotation(units = "1000", nano = 0),
                averagePositionPrice = null,
                expectedYield = null,
                instrumentType = "bond",
            )

        val pos: PortfolioPosition = restPosition.toPortfolioPosition()

        assertNull(pos.averagePrice)
        assertNull(pos.profit)
        assertNull(pos.profitPercent)
        assertEquals(5000.0, pos.totalValue, 0.0001)
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
