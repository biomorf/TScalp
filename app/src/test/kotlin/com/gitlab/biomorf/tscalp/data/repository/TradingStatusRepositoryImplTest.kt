package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TradingStatusRepositoryImplTest {
    @Test
    fun `statuses is empty initially`() =
        runTest {
            val repo = TradingStatusRepositoryImpl()
            assertTrue(repo.statuses.value.isEmpty())
        }

    @Test
    fun `update merges with existing statuses`() =
        runTest {
            val repo = TradingStatusRepositoryImpl()
            repo.update(mapOf("uid-1" to TradingAvailability.AVAILABLE))
            repo.update(mapOf("uid-2" to TradingAvailability.UNAVAILABLE))
            assertEquals(2, repo.statuses.value.size)
            assertEquals(TradingAvailability.AVAILABLE, repo.statuses.value["uid-1"])
            assertEquals(TradingAvailability.UNAVAILABLE, repo.statuses.value["uid-2"])
        }

    @Test
    fun `update overrides same key`() =
        runTest {
            val repo = TradingStatusRepositoryImpl()
            repo.update(mapOf("uid-1" to TradingAvailability.AVAILABLE))
            repo.update(mapOf("uid-1" to TradingAvailability.UNAVAILABLE))
            assertEquals(TradingAvailability.UNAVAILABLE, repo.statuses.value["uid-1"])
        }

    @Test
    fun `clear removes all`() =
        runTest {
            val repo = TradingStatusRepositoryImpl()
            repo.update(mapOf("uid-1" to TradingAvailability.AVAILABLE))
            repo.clear()
            assertTrue(repo.statuses.value.isEmpty())
        }
}
