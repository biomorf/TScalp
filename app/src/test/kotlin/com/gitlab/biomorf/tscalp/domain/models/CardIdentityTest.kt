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
    ) = CardIdentity(tscalpInstrumentId = uid, brokerName = broker, accountId = accountId)

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
        val target = card(uid = "uid-gazp")
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
        val b = card(uid = "uid-gazp", accountId = "acc-2")
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
    // ---- Full composite key (with accountId) ----

    @Test
    fun `matches by full composite key with accountId`() {
        assertTrue(card(accountId = "A").matches("uid-sber", BrokerName.TINVEST, "A"))
    }

    @Test
    fun `matches false on different accountId`() {
        assertEquals(false, card(accountId = "A").matches("uid-sber", BrokerName.TINVEST, "B"))
    }

    @Test
    fun `matches false on null vs non-null accountId`() {
        assertEquals(false, card(accountId = null).matches("uid-sber", BrokerName.TINVEST, "A"))
        assertEquals(false, card(accountId = "A").matches("uid-sber", BrokerName.TINVEST, null))
    }

    @Test
    fun `matches true on both null accountId`() {
        assertTrue(card(accountId = null).matches("uid-sber", BrokerName.TINVEST, null))
    }

    @Test
    fun `findCard by full composite distinguishes accounts`() {
        val a = card(uid = "uid-sber", accountId = "A")
        val b = card(uid = "uid-sber", accountId = "B")
        val list = listOf(a, b)
        assertEquals(a, list.findCard("uid-sber", BrokerName.TINVEST, "A"))
        assertEquals(b, list.findCard("uid-sber", BrokerName.TINVEST, "B"))
        assertNull(list.findCard("uid-sber", BrokerName.TINVEST, "C"))
        assertNull(list.findCard("uid-sber", BrokerName.TINVEST, null))
    }

    // ---- withRecreatedSettings ----

    @Test
    fun `withRecreatedSettings replaces old account with new`() {
        val a = card(uid = "uid-sber", accountId = "A")
        val b = card(uid = "uid-other", accountId = "X")
        val result = listOf(a, b).withRecreatedSettings("uid-sber", BrokerName.TINVEST, "A", "B")
        assertEquals(2, result.size)
        assertTrue(result.any { it.tscalpInstrumentId == "uid-sber" && it.accountId == "B" })
        assertTrue(result.none { it.tscalpInstrumentId == "uid-sber" && it.accountId == "A" })
        assertTrue(result.any { it.tscalpInstrumentId == "uid-other" && it.accountId == "X" })
    }

    @Test
    fun `withRecreatedSettings adds new when old is null`() {
        val list = emptyList<CardIdentity>()
        val result = list.withRecreatedSettings("uid-sber", BrokerName.TINVEST, null, "A")
        assertEquals(1, result.size)
        assertEquals("uid-sber", result[0].tscalpInstrumentId)
        assertEquals("A", result[0].accountId)
    }

    @Test
    fun `withRecreatedSettings keeps existing new and drops old`() {
        val a = card(uid = "uid-sber", accountId = "A")
        val b = card(uid = "uid-sber", accountId = "B")
        val result = listOf(a, b).withRecreatedSettings("uid-sber", BrokerName.TINVEST, "A", "B")
        assertEquals(1, result.size)
        assertEquals("B", result[0].accountId)
    }
}
