package com.gitlab.biomorf.tscalp.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculateProfitPercentTest {

    // ---------- Share (pointValue = null / 1.0) ----------

    @Test
    fun `share profit percent uses yield over avg times quantity`() {
        // yield=1000, avg=100, qty=10 → 1000/1000*100 = 100%
        val result = calculateProfitPercent(
            yield = 1000.0,
            avgPrice = 100.0,
            quantity = 10L,
            pointValue = null
        )
        assertEquals(100.0, result!!, 0.0001)
    }

    @Test
    fun `share profit percent negative`() {
        // yield=-50, avg=100, qty=10 → -50/1000*100 = -5%
        val result = calculateProfitPercent(
            yield = -50.0,
            avgPrice = 100.0,
            quantity = 10L,
            pointValue = null
        )
        assertEquals(-5.0, result!!, 0.0001)
    }

    @Test
    fun `pointValue 1_0 behaves like null`() {
        val withNull = calculateProfitPercent(100.0, 100.0, 10L, null)
        val withOne = calculateProfitPercent(100.0, 100.0, 10L, 1.0)
        assertEquals(withNull!!, withOne!!, 0.0001)
    }

    // ---------- Futures (pointValue > 1) ----------

    @Test
    fun `futures profit percent uses pointValue in denominator`() {
        // yield=1000, avg=100 (пунктов), qty=10, pointValue=5
        // invested = 100 * 10 * 5 = 5000, percent = 1000/5000*100 = 20%
        val result = calculateProfitPercent(
            yield = 1000.0,
            avgPrice = 100.0,
            quantity = 10L,
            pointValue = 5.0
        )
        assertEquals(20.0, result!!, 0.0001)
    }

    // ---------- Degenerate inputs ----------

    @Test
    fun `null yield returns null`() {
        assertNull(calculateProfitPercent(null, 100.0, 10L, null))
    }

    @Test
    fun `null avgPrice returns null`() {
        assertNull(calculateProfitPercent(100.0, null, 10L, null))
    }

    @Test
    fun `zero avgPrice returns null`() {
        assertNull(calculateProfitPercent(100.0, 0.0, 10L, null))
    }

    @Test
    fun `negative avgPrice returns null`() {
        assertNull(calculateProfitPercent(100.0, -5.0, 10L, null))
    }

    @Test
    fun `zero quantity returns null`() {
        assertNull(calculateProfitPercent(100.0, 100.0, 0L, null))
    }

    @Test
    fun `zero pointValue falls back to 1_0`() {
        // pointValue=0.0 → невалидно, fallback на 1.0
        val withZero = calculateProfitPercent(1000.0, 100.0, 10L, 0.0)
        val withNull = calculateProfitPercent(1000.0, 100.0, 10L, null)
        assertEquals(withNull!!, withZero!!, 0.0001)
    }

    @Test
    fun `negative pointValue falls back to 1_0`() {
        val withNegative = calculateProfitPercent(1000.0, 100.0, 10L, -2.0)
        val withNull = calculateProfitPercent(1000.0, 100.0, 10L, null)
        assertEquals(withNull!!, withNegative!!, 0.0001)
    }
}