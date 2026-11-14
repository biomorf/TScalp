package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderInputState
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderInstrumentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderPlaceRequestMapperTest {
    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    @Test
    fun `success maps all fields`() {
        val instrument =
            OrderInstrumentState(
                selectedInstrument = sber,
                selectedAccountId = "acc-1",
                pairedInstrument = sber,
                pairedAccountId = "acc-2",
            )
        val input =
            OrderInputState(
                quantity = "3",
                orderType = OrderTypeSelection.Limit,
                limitPrice = "150.5",
                stopPrice = "0",
                expirationType = StopOrderExpirationType.GOOD_TILL_DATE,
                pairedMultiplier = "2",
            )

        val r = toPlaceRequest(instrument, input)!!

        assertEquals("uid-sber", r.instrument.tscalpInstrumentId)
        assertEquals("acc-1", r.selectedAccountId)
        assertEquals("acc-2", r.pairedAccountId)
        assertEquals(3L, r.quantity)
        assertEquals(OrderTypeSelection.Limit, r.orderType)
        assertEquals("150.5", r.limitPrice)
        assertEquals("0", r.stopPrice)
        assertEquals(StopOrderExpirationType.GOOD_TILL_DATE, r.expirationType)
        assertEquals("uid-sber", r.pairedInstrument?.tscalpInstrumentId)
        assertEquals(2L, r.pairedMultiplier)
    }

    @Test
    fun `null selectedInstrument returns null`() {
        val instrument = OrderInstrumentState(selectedInstrument = null)
        val input = OrderInputState(quantity = "1")

        assertNull(toPlaceRequest(instrument, input))
    }

    @Test
    fun `null quantity returns null`() {
        val instrument = OrderInstrumentState(selectedInstrument = sber)
        val input = OrderInputState(quantity = "")

        assertNull(toPlaceRequest(instrument, input))
    }

    @Test
    fun `null pairedMultiplier returns null`() {
        val instrument = OrderInstrumentState(selectedInstrument = sber)
        val input = OrderInputState(quantity = "1", pairedMultiplier = "")

        assertNull(toPlaceRequest(instrument, input))
    }
}
