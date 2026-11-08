package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.SearchCacheRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SearchInstrumentsUseCaseTest {
    private val searchCache = mockk<SearchCacheRepository>(relaxed = true)
    private val updateStatuses = mockk<UpdateTradingStatusesUseCase>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val useCase = SearchInstrumentsUseCase(searchCache, updateStatuses, logger)

    private val sber =
        Share(
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    @Test
    fun `success for TINVEST calls statuses and returns instruments`() =
        runBlocking {
            coEvery { searchCache.search(BrokerName.TINVEST, "SBER") } returns
                AppResult.Success(listOf(sber))
            coEvery {
                updateStatuses.execute(BrokerName.TINVEST, listOf("uid-sber"))
            } returns AppResult.Success(mapOf("uid-sber" to TradingAvailability.AVAILABLE))

            val r = useCase.execute(BrokerName.TINVEST, "SBER")

            assertTrue(r is AppResult.Success)
            assertEquals(listOf(sber), (r as AppResult.Success).data)
            coVerify(exactly = 1) {
                updateStatuses.execute(BrokerName.TINVEST, listOf("uid-sber"))
            }
        }

    @Test
    fun `statuses failure does not fail search`() =
        runBlocking {
            coEvery { searchCache.search(BrokerName.TINVEST, "SBER") } returns
                AppResult.Success(listOf(sber))
            coEvery {
                updateStatuses.execute(BrokerName.TINVEST, listOf("uid-sber"))
            } returns AppResult.Failure(AppError.Network("down", null))

            val r = useCase.execute(BrokerName.TINVEST, "SBER")

            assertTrue(r is AppResult.Success)
            assertEquals(listOf(sber), (r as AppResult.Success).data)
            verify(atLeast = 1) { logger.w(any(), any(), any()) }
        }

    @Test
    fun `search failure is passthrough without statuses call`() =
        runBlocking {
            val original = AppError.Network("down", null)
            coEvery { searchCache.search(BrokerName.TINVEST, "X") } returns
                AppResult.Failure(original)

            val r = useCase.execute(BrokerName.TINVEST, "X")

            assertTrue(r is AppResult.Failure)
            assertEquals(original, (r as AppResult.Failure).error)
            coVerify(exactly = 0) { updateStatuses.execute(any(), any()) }
        }

    @Test
    fun `empty results skip statuses call`() =
        runBlocking {
            coEvery { searchCache.search(BrokerName.TINVEST, "X") } returns
                AppResult.Success(emptyList<Instrument>())

            val r = useCase.execute(BrokerName.TINVEST, "X")

            assertTrue(r is AppResult.Success)
            assertTrue((r as AppResult.Success).data.isEmpty())
            coVerify(exactly = 0) { updateStatuses.execute(any(), any()) }
        }

    @Test
    fun `non TINVEST broker skips statuses call`() =
        runBlocking {
            coEvery { searchCache.search(BrokerName.BCS, "SBER") } returns
                AppResult.Success(listOf(sber))

            val r = useCase.execute(BrokerName.BCS, "SBER")

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 0) { updateStatuses.execute(any(), any()) }
        }

    @Test
    fun `exception returns Failure Unknown`() =
        runBlocking {
            coEvery { searchCache.search(any(), any()) } throws RuntimeException("boom")

            val r = useCase.execute(BrokerName.TINVEST, "X")

            assertTrue(r is AppResult.Failure)
            assertTrue((r as AppResult.Failure).error is AppError.Unknown)
        }

    @Test
    fun `cancellation rethrows`() =
        runBlocking {
            coEvery { searchCache.search(any(), any()) } throws CancellationException()

            try {
                useCase.execute(BrokerName.TINVEST, "X")
                fail("expected CancellationException")
            } catch (expected: CancellationException) {
                // OK
            }
        }
}
