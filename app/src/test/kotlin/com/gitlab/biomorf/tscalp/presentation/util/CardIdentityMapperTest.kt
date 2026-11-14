package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderInstrumentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardIdentityMapperTest {
    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    @Test
    fun `primary with account maps all fields`() {
        val state =
            OrderInstrumentState(
                selectedInstrument = sber,
                selectedBroker = BrokerName.TINVEST,
                selectedAccountId = "acc-1",
            )

        val card = state.toPrimaryCardIdentity()!!

        assertEquals("uid-sber", card.tscalpInstrumentId)
        assertEquals(BrokerName.TINVEST, card.brokerName)
        assertEquals("acc-1", card.accountId)
    }

    @Test
    fun `primary without account keeps null accountId`() {
        val state =
            OrderInstrumentState(
                selectedInstrument = sber,
                selectedBroker = BrokerName.BCS,
                selectedAccountId = null,
            )

        val card = state.toPrimaryCardIdentity()!!

        assertEquals("uid-sber", card.tscalpInstrumentId)
        assertEquals(BrokerName.BCS, card.brokerName)
        assertNull(card.accountId)
    }

    @Test
    fun `paired with account maps all fields`() {
        val state =
            OrderInstrumentState(
                pairedInstrument = sber,
                pairedBroker = BrokerName.BCS,
                pairedAccountId = "acc-2",
            )

        val card = state.toPairedCardIdentity()!!

        assertEquals("uid-sber", card.tscalpInstrumentId)
        assertEquals(BrokerName.BCS, card.brokerName)
        assertEquals("acc-2", card.accountId)
    }

    @Test
    fun `paired without instrument returns null`() {
        val state = OrderInstrumentState(pairedInstrument = null)

        assertNull(state.toPairedCardIdentity())
    }
}
