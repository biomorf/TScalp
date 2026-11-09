package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.SelectedInstrumentInfo
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.SharePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PositionFormatTest {
    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    private fun basePosition(): SharePosition =
        SharePosition(
            instrument = sber,
            brokerName = BrokerName.TINVEST,
            accountId = "acc-1",
            quantity = 0.0,
            currentPrice = 150.0,
            averagePrice = null,
            profit = null,
        )

    private fun card(
        qty: Long = 5,
        avg: Double? = 149.5,
        profit: Double? = 2.5,
    ) = SelectedInstrumentInfo(
        instrument = sber,
        currentPrice = 150.0,
        priceChangePercent = 1.0,
        quantity = qty,
        averagePrice = avg,
        profit = profit,
        profitPercent = null,
    )

    @Test
    fun `withCard null card returns receiver unchanged`() {
        val p = basePosition()
        val r = p.withCard(null)
        assertSame(p, r)
    }

    @Test
    fun `withCard copies quantity averagePrice profit into SharePosition`() {
        val p = basePosition()
        val r = p.withCard(card()) as SharePosition
        assertEquals(5.0, r.quantity, 0.0)
        assertEquals(149.5, r.averagePrice!!, 0.0)
        assertEquals(2.5, r.profit!!, 0.0)
        // unchanged fields
        assertEquals(p.instrument, r.instrument)
        assertEquals(p.accountId, r.accountId)
        assertEquals(p.currentPrice, r.currentPrice, 0.0)
    }

    @Test
    fun `withCard copies null averagePrice and null profit`() {
        val p = basePosition()
        val r = p.withCard(card(qty = 0, avg = null, profit = null)) as SharePosition
        assertEquals(0.0, r.quantity, 0.0)
        assertNull(r.averagePrice)
        assertNull(r.profit)
    }

    @Test
    fun `withCard truncates quantity to long-then-double`() {
        val p = basePosition()
        val r = p.withCard(card(qty = 42)) as SharePosition
        assertEquals(42.0, r.quantity, 0.0)
    }
}
