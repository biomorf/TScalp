package com.gitlab.biomorf.tscalp.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardIdentityTest {
    private fun card(
        uid: String = "uid-sber",
        broker: BrokerName = BrokerName.TINVEST,
        ticker: String = "SBER",
        accountId: String? = "acc-1",
    ) = CardIdentity(tscalpInstrumentId = uid, ticker = ticker, brokerName = broker, accountId = accountId)

    @Test
    fun `matches true on same uid and broker`() {
        assertTrue(card().matches("uid-sber", BrokerName.TINVEST))
    }

    @Test
    fun `matches false on different uid`() {
        assertEquals(false, card().matches("uid-gazp", BrokerName.TINVEST))
    }

    @Test
    fun `matches false on different broker`() {
        assertEquals(false, card().matches("uid-sber", BrokerName.BCS))
    }

    @Test
    fun `matches by instrument`() {
        val instrument =
            Share(
                brokerName = BrokerName.TINVEST,
                tscalpInstrumentId = "uid-sber",
                ticker = "SBER",
                name = "Сбербанк",
                isin = "RU0009029540",
            )
        assertTrue(card().matches(instrument))
    }

    @Test
    fun `findCard returns matching card`() {
        val target = card(uid = "uid-gazp", ticker = "GAZP")
        val list = listOf(card(), target)
        assertEquals(target, list.findCard("uid-gazp", BrokerName.TINVEST))
    }

    @Test
    fun `findCard returns null when no match`() {
        assertNull(listOf(card()).findCard("uid-gazp", BrokerName.TINVEST))
    }

    @Test
    fun `withUpdatedSettings updates matching card only`() {
        val a = card(uid = "uid-sber", ticker = "SBER")
        val b = card(uid = "uid-gazp", ticker = "GAZP", accountId = "acc-2")
        val updated = listOf(a, b).withUpdatedSettings("uid-sber", BrokerName.TINVEST, "acc-new")
        assertEquals("acc-new", updated[0].accountId)
        assertEquals("acc-2", updated[1].accountId)
    }

    @Test
    fun `withUpdatedSettings leaves non-matching broker untouched`() {
        val a = card(broker = BrokerName.TINVEST, uid = "uid-sber")
        val updated = listOf(a).withUpdatedSettings("uid-sber", BrokerName.BCS, "acc-new")
        assertEquals(a, updated[0])
    }

    @Test
    fun `withUpdatedSettings null accountId clears matching account`() {
        val a = card(uid = "uid-sber", accountId = "acc-1")
        val updated = listOf(a).withUpdatedSettings("uid-sber", BrokerName.TINVEST, null)
        assertNull(updated[0].accountId)
    }

    @Test
    fun `withUpdatedSettings is idempotent`() {
        val list = listOf(card(uid = "uid-sber"))
        val once = list.withUpdatedSettings("uid-sber", BrokerName.TINVEST, "acc-new")
        val twice = once.withUpdatedSettings("uid-sber", BrokerName.TINVEST, "acc-new")
        assertEquals(once, twice)
    }
}
