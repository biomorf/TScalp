package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderType
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.StopOrderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairOrderMapperTest {

    // ---------- Limit + BUY → StopLoss ----------

    @Test
    fun `limit buy produces stop-loss paired order`() {
        val spec = PairOrderMapper.map(
            primaryType = OrderTypeSelection.Limit,
            primaryDirection = OrderDirection.BUY,
            primaryPrice = 250.5
        )

        assertEquals(OrderTypeSelection.StopLoss, spec.orderType)
        assertEquals(StopOrderType.STOP_LOSS, spec.stopOrderType)
        assertEquals(250.5, spec.stopPrice!!, 0.0001)
        assertNull(spec.price)
        assertTrue(spec.isStopOrder)
        assertNull(spec.brokerOrderType)
    }

    // ---------- Limit + SELL → TakeProfit ----------

    @Test
    fun `limit sell produces take-profit paired order`() {
        val spec = PairOrderMapper.map(
            primaryType = OrderTypeSelection.Limit,
            primaryDirection = OrderDirection.SELL,
            primaryPrice = 310.0
        )

        assertEquals(OrderTypeSelection.TakeProfit, spec.orderType)
        assertEquals(StopOrderType.TAKE_PROFIT, spec.stopOrderType)
        assertEquals(310.0, spec.stopPrice!!, 0.0001)
        assertNull(spec.price)
        assertTrue(spec.isStopOrder)
        assertNull(spec.brokerOrderType)
    }

    // ---------- Limit с null-ценой ----------

    @Test
    fun `limit buy with null price keeps null stop-price`() {
        val spec = PairOrderMapper.map(
            primaryType = OrderTypeSelection.Limit,
            primaryDirection = OrderDirection.BUY,
            primaryPrice = null
        )

        assertEquals(OrderTypeSelection.StopLoss, spec.orderType)
        assertNull(spec.stopPrice)
        assertTrue(spec.isStopOrder)
    }

    @Test
    fun `limit buy with zero price keeps zero stop-price`() {
        val spec = PairOrderMapper.map(
            primaryType = OrderTypeSelection.Limit,
            primaryDirection = OrderDirection.BUY,
            primaryPrice = 0.0
        )

        assertEquals(0.0, spec.stopPrice!!, 0.0)
        assertTrue(spec.isStopOrder)
    }

    // ---------- Market → Market ----------

    @Test
    fun `market buy produces market paired order`() {
        val spec = PairOrderMapper.map(
            primaryType = OrderTypeSelection.Market,
            primaryDirection = OrderDirection.BUY,
            primaryPrice = null
        )

        assertEquals(OrderTypeSelection.Market, spec.orderType)
        assertEquals(BrokerOrderType.MARKET, spec.brokerOrderType)
        assertNull(spec.stopOrderType)
        assertNull(spec.price)
        assertNull(spec.stopPrice)
        assertFalse(spec.isStopOrder)
    }

    @Test
    fun `market sell produces market paired order`() {
        val spec = PairOrderMapper.map(
            primaryType = OrderTypeSelection.Market,
            primaryDirection = OrderDirection.SELL,
            primaryPrice = null
        )

        assertEquals(OrderTypeSelection.Market, spec.orderType)
        assertEquals(BrokerOrderType.MARKET, spec.brokerOrderType)
        assertFalse(spec.isStopOrder)
    }

    // ---------- Временное отключение стоп-основ ----------
    // Пока логика закомментирована, все три типа отображаются в Market.
    // Когда раскомментируют — эти тесты надо будет переписать.

    @Test
    fun `stop-loss primary falls back to market paired (temporary)`() {
        val spec = PairOrderMapper.map(
            primaryType = OrderTypeSelection.StopLoss,
            primaryDirection = OrderDirection.SELL,
            primaryPrice = 100.0
        )

        assertEquals(OrderTypeSelection.Market, spec.orderType)
        assertEquals(BrokerOrderType.MARKET, spec.brokerOrderType)
        assertFalse(spec.isStopOrder)
    }

    @Test
    fun `take-profit primary falls back to market paired (temporary)`() {
        val spec = PairOrderMapper.map(
            primaryType = OrderTypeSelection.TakeProfit,
            primaryDirection = OrderDirection.BUY,
            primaryPrice = 100.0
        )

        assertEquals(OrderTypeSelection.Market, spec.orderType)
        assertFalse(spec.isStopOrder)
    }

    @Test
    fun `stop-limit primary falls back to market paired (temporary)`() {
        val spec = PairOrderMapper.map(
            primaryType = OrderTypeSelection.StopLimit,
            primaryDirection = OrderDirection.BUY,
            primaryPrice = 100.0
        )

        assertEquals(OrderTypeSelection.Market, spec.orderType)
        assertFalse(spec.isStopOrder)
    }
}
