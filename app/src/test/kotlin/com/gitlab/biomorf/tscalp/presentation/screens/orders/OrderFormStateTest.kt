package com.gitlab.biomorf.tscalp.presentation.screens.orders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderFormStateTest {
    @Test
    fun `pairedQuantity is quantity times pairedMultiplier`() {
        val s = OrderFormState(quantity = "10", pairedMultiplier = "2")
        assertEquals(20L, s.pairedQuantity)
    }

    @Test
    fun `pairedQuantity is null when quantity is empty`() {
        val s = OrderFormState(quantity = "", pairedMultiplier = "2")
        assertNull(s.pairedQuantity)
    }

    @Test
    fun `pairedQuantity is null when quantity is not a number`() {
        val s = OrderFormState(quantity = "abc", pairedMultiplier = "2")
        assertNull(s.pairedQuantity)
    }

    @Test
    fun `pairedQuantity is null when pairedMultiplier is not a number`() {
        val s = OrderFormState(quantity = "10", pairedMultiplier = "abc")
        assertNull(s.pairedQuantity)
    }

    @Test
    fun `pairedQuantity with zero multiplier is zero`() {
        val s = OrderFormState(quantity = "10", pairedMultiplier = "0")
        assertEquals(0L, s.pairedQuantity)
    }

    @Test
    fun `defaults are quantity 1 and pairedMultiplier 10`() {
        val s = OrderFormState()
        assertEquals("1", s.quantity)
        assertEquals("10", s.pairedMultiplier)
        assertEquals(10L, s.pairedQuantity)
    }
}
