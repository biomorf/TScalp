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

    // ---- withEnsuredCard (модель A, 1b-11) ----

    @Test
    fun `withEnsuredCard adds when missing`() {
        val a = card(uid = "uid-sber", accountId = "A")
        val result = listOf(a).withEnsuredCard("uid-sber", BrokerName.TINVEST, "B")
        assertEquals(2, result.size)
        assertTrue(result.any { it.tscalpInstrumentId == "uid-sber" && it.accountId == "A" })
        assertTrue(result.any { it.tscalpInstrumentId == "uid-sber" && it.accountId == "B" })
    }

    @Test
    fun `withEnsuredCard no-op when already exists`() {
        val a = card(uid = "uid-sber", accountId = "A")
        val b = card(uid = "uid-sber", accountId = "B")
        val result = listOf(a, b).withEnsuredCard("uid-sber", BrokerName.TINVEST, "B")
        assertEquals(2, result.size)
        assertEquals("A", result[0].accountId)
        assertEquals("B", result[1].accountId)
    }

    @Test
    fun `withEnsuredCard adds null-account card when missing`() {
        val a = card(uid = "uid-sber", accountId = "A")
        val result = listOf(a).withEnsuredCard("uid-sber", BrokerName.TINVEST, null)
        assertEquals(2, result.size)
        assertTrue(result.any { it.tscalpInstrumentId == "uid-sber" && it.accountId == "A" })
        assertTrue(result.any { it.tscalpInstrumentId == "uid-sber" && it.accountId == null })
    }
}
