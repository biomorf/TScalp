package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderRequest
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType
import com.gitlab.biomorf.tscalp.domain.models.StopOrderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepareOrderRequestUseCaseTest {
    private val useCase = PrepareOrderRequestUseCase()

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    private val tmon =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-tmon",
            ticker = "TMON",
            name = "ТМОН",
            isin = "RU0000000000",
        )

    @Test
    fun `market returns Prepared with Broker Market, no pair`() {
        val result =
            useCase.prepare(
                instrument = sber,
                quantity = 2,
                direction = OrderDirection.BUY,
                accountId = "acc-1",
                orderType = OrderTypeSelection.Market,
                limitPrice = null,
                stopPrice = null,
                expirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
                pairedInstrument = null,
                pairedAccountId = null,
                pairedMultiplier = null,
            )
        assertTrue(result is PrepareOrderRequestUseCase.PrepareResult.Prepared)
        val r = result as PrepareOrderRequestUseCase.PrepareResult.Prepared
        assertTrue(r.primary is OrderRequest.Broker)
        assertEquals("MARKET", (r.primary as OrderRequest.Broker).request.type.name)
        assertNull(r.paired)
    }

    @Test
    fun `limit returns Prepared with Broker Limit, no pair`() {
        val result =
            useCase.prepare(
                instrument = sber,
                quantity = 2,
                direction = OrderDirection.BUY,
                accountId = "acc-1",
                orderType = OrderTypeSelection.Limit,
                limitPrice = "150.5",
                stopPrice = null,
                expirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
                pairedInstrument = null,
                pairedAccountId = null,
                pairedMultiplier = null,
            )
        assertTrue(result is PrepareOrderRequestUseCase.PrepareResult.Prepared)
        val r = result as PrepareOrderRequestUseCase.PrepareResult.Prepared
        assertTrue(r.primary is OrderRequest.Broker)
        val broker = (r.primary as OrderRequest.Broker).request
        assertEquals("LIMIT", broker.type.name)
        assertEquals(150.5, broker.price!!, 0.0001)
    }

    @Test
    fun `stopLoss with stopPrice returns Prepared with Stop STOP_LOSS`() {
        val result =
            useCase.prepare(
                instrument = sber,
                quantity = 2,
                direction = OrderDirection.SELL,
                accountId = "acc-1",
                orderType = OrderTypeSelection.StopLoss,
                limitPrice = null,
                stopPrice = "145.0",
                expirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
                pairedInstrument = null,
                pairedAccountId = null,
                pairedMultiplier = null,
            )
        assertTrue(result is PrepareOrderRequestUseCase.PrepareResult.Prepared)
        val r = result as PrepareOrderRequestUseCase.PrepareResult.Prepared
        assertTrue(r.primary is OrderRequest.Stop)
        val stop = (r.primary as OrderRequest.Stop).request
        assertEquals(StopOrderType.STOP_LOSS, stop.stopOrderType)
        assertEquals(145.0, stop.stopPrice, 0.0001)
        assertNull(r.paired)
    }

    @Test
    fun `stopLimit with stopPrice and limitPrice returns Stop STOP_LIMIT`() {
        val result =
            useCase.prepare(
                instrument = sber,
                quantity = 2,
                direction = OrderDirection.BUY,
                accountId = "acc-1",
                orderType = OrderTypeSelection.StopLimit,
                limitPrice = "150.0",
                stopPrice = "151.0",
                expirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
                pairedInstrument = null,
                pairedAccountId = null,
                pairedMultiplier = null,
            )
        assertTrue(result is PrepareOrderRequestUseCase.PrepareResult.Prepared)
        val r = result as PrepareOrderRequestUseCase.PrepareResult.Prepared
        assertTrue(r.primary is OrderRequest.Stop)
        val stop = (r.primary as OrderRequest.Stop).request
        assertEquals(StopOrderType.STOP_LIMIT, stop.stopOrderType)
        assertEquals(151.0, stop.stopPrice, 0.0001)
        assertEquals(150.0, stop.price!!, 0.0001)
    }

    @Test
    fun `stopLoss without stopPrice returns Invalid`() {
        val result =
            useCase.prepare(
                instrument = sber,
                quantity = 2,
                direction = OrderDirection.SELL,
                accountId = "acc-1",
                orderType = OrderTypeSelection.StopLoss,
                limitPrice = null,
                stopPrice = null,
                expirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
                pairedInstrument = null,
                pairedAccountId = null,
                pairedMultiplier = null,
            )
        assertTrue(result is PrepareOrderRequestUseCase.PrepareResult.Invalid)
        assertEquals(
            "stopPrice required for StopLoss",
            (result as PrepareOrderRequestUseCase.PrepareResult.Invalid).reason,
        )
    }

    @Test
    fun `market with pair returns Prepared Broker + Broker (Market pair)`() {
        val result =
            useCase.prepare(
                instrument = sber,
                quantity = 2,
                direction = OrderDirection.BUY,
                accountId = "acc-1",
                orderType = OrderTypeSelection.Market,
                limitPrice = null,
                stopPrice = null,
                expirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
                pairedInstrument = tmon,
                pairedAccountId = "acc-1",
                pairedMultiplier = 2L,
            )
        assertTrue(result is PrepareOrderRequestUseCase.PrepareResult.Prepared)
        val r = result as PrepareOrderRequestUseCase.PrepareResult.Prepared
        assertTrue(r.primary is OrderRequest.Broker)
        assertTrue(r.paired is OrderRequest.Broker)
        val pair = (r.paired as OrderRequest.Broker).request
        assertEquals("MARKET", pair.type.name)
        assertEquals(OrderDirection.SELL, pair.direction)
        assertEquals(4, pair.quantity)
    }

    @Test
    fun `limit BUY with pair returns Stop Loss pair`() {
        val result =
            useCase.prepare(
                instrument = sber,
                quantity = 2,
                direction = OrderDirection.BUY,
                accountId = "acc-1",
                orderType = OrderTypeSelection.Limit,
                limitPrice = "150.0",
                stopPrice = null,
                expirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
                pairedInstrument = tmon,
                pairedAccountId = "acc-1",
                pairedMultiplier = 1L,
            )
        assertTrue(result is PrepareOrderRequestUseCase.PrepareResult.Prepared)
        val r = result as PrepareOrderRequestUseCase.PrepareResult.Prepared
        assertTrue(r.primary is OrderRequest.Broker)
        assertTrue(r.paired is OrderRequest.Stop)
        val pair = (r.paired as OrderRequest.Stop).request
        assertEquals(StopOrderType.STOP_LOSS, pair.stopOrderType)
        assertEquals(150.0, pair.stopPrice, 0.0001)
    }

    @Test
    fun `limit SELL with pair returns Take Profit pair`() {
        val result =
            useCase.prepare(
                instrument = sber,
                quantity = 2,
                direction = OrderDirection.SELL,
                accountId = "acc-1",
                orderType = OrderTypeSelection.Limit,
                limitPrice = "150.0",
                stopPrice = null,
                expirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
                pairedInstrument = tmon,
                pairedAccountId = "acc-1",
                pairedMultiplier = 1L,
            )
        assertTrue(result is PrepareOrderRequestUseCase.PrepareResult.Prepared)
        val r = result as PrepareOrderRequestUseCase.PrepareResult.Prepared
        assertTrue(r.paired is OrderRequest.Stop)
        val pair = (r.paired as OrderRequest.Stop).request
        assertEquals(StopOrderType.TAKE_PROFIT, pair.stopOrderType)
        assertEquals(150.0, pair.stopPrice, 0.0001)
    }
}
