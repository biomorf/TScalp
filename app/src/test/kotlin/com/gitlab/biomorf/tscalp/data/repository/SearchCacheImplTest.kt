package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.SearchItem
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCacheImplTest {
    private val brokerRegistry = mockk<BrokerRegistry>(relaxed = true)
    private val cache = SearchCacheImpl(brokerRegistry)

    private val sber =
        SearchItem(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Sberbank",
            instrumentType = InstrumentType.SHARE,
        )

    @Test
    fun `isCached false before any search`() {
        assertFalse(cache.isCached(BrokerName.TINVEST, "SBER"))
    }

    @Test
    fun `isCached true after successful search`() =
        runBlocking {
            coEvery { brokerRegistry.getBroker(BrokerName.TINVEST) } returns mockk(relaxed = true)
            cache.search(BrokerName.TINVEST, "SBER")
            assertTrue(cache.isCached(BrokerName.TINVEST, "SBER"))
        }

    @Test
    fun `isCached normalizes query (trim + lowercase)`() =
        runBlocking {
            coEvery { brokerRegistry.getBroker(BrokerName.TINVEST) } returns mockk(relaxed = true)
            cache.search(BrokerName.TINVEST, "  SBER  ")
            assertTrue(cache.isCached(BrokerName.TINVEST, "sber"))
            assertTrue(cache.isCached(BrokerName.TINVEST, "  SBER  "))
            assertFalse(cache.isCached(BrokerName.TINVEST, "GAZP"))
        }

    @Test
    fun `isCached false after invalidate`() =
        runBlocking {
            coEvery { brokerRegistry.getBroker(BrokerName.TINVEST) } returns mockk(relaxed = true)
            cache.search(BrokerName.TINVEST, "SBER")
            cache.invalidate(BrokerName.TINVEST, "SBER")
            assertFalse(cache.isCached(BrokerName.TINVEST, "SBER"))
        }
}
