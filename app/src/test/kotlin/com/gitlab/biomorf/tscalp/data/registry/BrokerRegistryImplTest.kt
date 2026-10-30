package com.gitlab.biomorf.tscalp.data.registry

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BrokerRegistryImplTest {
    private fun broker(name: BrokerName): BrokerApi {
        val broker = mockk<BrokerApi>()
        every { broker.name } returns name
        every { broker.isInitialized } returns false
        return broker
    }

    @Test
    fun `available names follow BrokerName entries order, not set order`() {
        val tinvest = broker(BrokerName.TINVEST)
        val finam = broker(BrokerName.FINAM)
        val bcs = broker(BrokerName.BCS)

        // Порядок в set намеренно не совпадает с BrokerName.entries.
        val registry = BrokerRegistryImpl(setOf(finam, bcs, tinvest))

        assertEquals(
            listOf(BrokerName.TINVEST, BrokerName.BCS, BrokerName.FINAM),
            registry.getAvailableBrokerNames(),
        )
        assertEquals(3, registry.getAllBrokers().size)
    }

    @Test
    fun `default broker is TINVEST from BrokerRegistry companion`() {
        val tinvest = broker(BrokerName.TINVEST)
        val registry = BrokerRegistryImpl(setOf(tinvest))

        assertEquals(BrokerName.TINVEST, BrokerRegistry.defaultBroker)
        assertSame(tinvest, registry.getDefaultBroker())
    }

    @Test
    fun `getBroker returns null for absent broker`() {
        val registry = BrokerRegistryImpl(setOf(broker(BrokerName.BCS)))
        assertNull(registry.getBroker(BrokerName.TINVEST))
    }

    @Test(expected = IllegalStateException::class)
    fun `getDefaultBroker throws when default broker is absent`() {
        BrokerRegistryImpl(setOf(broker(BrokerName.BCS))).getDefaultBroker()
    }

    @Test
    fun `anyInitialized reflects broker isInitialized`() {
        val bcs = broker(BrokerName.BCS)
        every { bcs.isInitialized } returns true
        val registry = BrokerRegistryImpl(setOf(bcs))

        assertEquals(true, registry.anyInitialized.value)
        registry.refreshInitializationState()
        assertEquals(true, registry.anyInitialized.value)
    }
}
