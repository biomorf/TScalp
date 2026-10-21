package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PositionFlowManagerTest {
    private fun manager(): PositionFlowManager = PositionFlowManager(BrokerManager(emptyMap()))

    @Test
    fun `ws currency without polling cache returns null`() {
        val m = manager()
        val wsItem =
            PositionStreamItem(
                tscalpInstrumentId = "",
                brokerName = BrokerName.TINVEST,
                ticker = "",
                quantity = 0L,
                instrumentType = "currency",
                currency = "rub",
                money = 2415.31,
                blocked = null,
            )

        assertNull(m.enrichAndCache(wsItem))
    }

    @Test
    fun `ws currency merges into cached polling currency item`() {
        val m = manager()
        val pollingItem =
            PositionStreamItem(
                tscalpInstrumentId = "uid-rub",
                brokerName = BrokerName.TINVEST,
                ticker = "RUB000UTSTOM",
                quantity = 2415L,
                currentPrice = 1.0,
                instrumentType = "currency",
                currency = "rub",
            )
        m.enrichAndCache(pollingItem)

        val wsItem =
            PositionStreamItem(
                tscalpInstrumentId = "",
                brokerName = BrokerName.TINVEST,
                ticker = "",
                quantity = 0L,
                instrumentType = "currency",
                currency = "rub",
                money = 2415.31,
                blocked = 100.0,
            )
        val result = m.enrichAndCache(wsItem)

        assertNotNull(result)
        assertEquals("uid-rub", result!!.tscalpInstrumentId)
        assertEquals("RUB000UTSTOM", result.ticker)
        assertEquals(2415.31, result.money!!, 0.0001)
        assertEquals(100.0, result.blocked!!, 0.0001)
    }

    @Test
    fun `ws currency with no matching currency in cache returns null`() {
        val m = manager()
        m.enrichAndCache(
            PositionStreamItem(
                tscalpInstrumentId = "uid-usd",
                brokerName = BrokerName.TINVEST,
                ticker = "USD000UTSTOM",
                quantity = 100L,
                currentPrice = 1.0,
                instrumentType = "currency",
                currency = "usd",
            ),
        )
        val wsRub =
            PositionStreamItem(
                tscalpInstrumentId = "",
                brokerName = BrokerName.TINVEST,
                ticker = "",
                quantity = 0L,
                instrumentType = "currency",
                currency = "rub",
                money = 100.0,
            )

        assertNull(m.enrichAndCache(wsRub))
    }

    @Test
    fun `full share item is returned unchanged`() {
        val m = manager()
        val share =
            PositionStreamItem(
                tscalpInstrumentId = "uid-sber",
                brokerName = BrokerName.TINVEST,
                ticker = "SBER",
                quantity = 2L,
                currentPrice = 292.6,
                instrumentType = "share",
                expectedYield = 14.73,
            )

        val result = m.enrichAndCache(share)

        assertSame(share, result)
    }
}
