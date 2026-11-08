package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderFormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderFormStateMapperTest {
    private val sber =
        Share(
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    @Test
    fun `success maps all fields`() {
        val state =
            OrderFormState(
                ticker = "SBER",
                selectedInstrument = sber,
                quantity = "3",
                orderType = OrderTypeSelection.Limit,
                limitPrice = "150.5",
                stopPrice = "0",
                expirationType = StopOrderExpirationType.GOOD_TILL_DATE,
                pairedInstrument = sber,
                pairedMultiplier = "2",
            )

        val r = state.toPlaceOrderRequest()!!

        assertEquals("SBER", r.ticker)
        assertEquals("uid-sber", r.tscalpInstrumentId)
        assertEquals(3L, r.quantity)
        assertEquals(OrderTypeSelection.Limit, r.orderType)
        assertEquals("150.5", r.limitPrice)
        assertEquals("0", r.stopPrice)
        assertEquals(StopOrderExpirationType.GOOD_TILL_DATE, r.expirationType)
        assertEquals("uid-sber", r.pairedTscalpInstrumentId)
        assertEquals("SBER", r.pairedTicker)
        assertEquals("2", r.pairedMultiplier)
    }

    @Test
    fun `blank ticker falls back to selectedInstrument ticker`() {
        val state =
            OrderFormState(
                ticker = "",
                selectedInstrument = sber,
                quantity = "1",
            )

        val r = state.toPlaceOrderRequest()!!

        assertEquals("SBER", r.ticker)
    }

    @Test
    fun `null quantity returns null`() {
        val state =
            OrderFormState(
                ticker = "SBER",
                selectedInstrument = sber,
                quantity = "",
            )

        assertNull(state.toPlaceOrderRequest())
    }

    @Test
    fun `null selectedInstrument returns null`() {
        val state =
            OrderFormState(
                ticker = "SBER",
                selectedInstrument = null,
                quantity = "1",
            )

        assertNull(state.toPlaceOrderRequest())
    }
}
