package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.MarketDataRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStatusRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UpdateTradingStatusesUseCaseTest {
    private val marketDataRepository = mockk<MarketDataRepository>(relaxed = true)
    private val tradingStatusRepository = mockk<TradingStatusRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val useCase =
        UpdateTradingStatusesUseCase(marketDataRepository, tradingStatusRepository, logger)

    @Test
    fun `empty ids is no-op success with empty map without repository call`() =
        runBlocking {
            val r = useCase.execute(BrokerName.TINVEST, emptyList())

            assertTrue(r is AppResult.Success)
            assertEquals(emptyMap<String, TradingAvailability>(), (r as AppResult.Success).data)
            coVerify(exactly = 0) { marketDataRepository.getTradingStatusesResult(any(), any()) }
            coVerify(exactly = 0) { tradingStatusRepository.update(any()) }
        }

    @Test
    fun `success passes data to repository update and returns map`() =
        runBlocking {
            val statuses = mapOf("uid-1" to TradingAvailability.AVAILABLE)
            coEvery {
                marketDataRepository.getTradingStatusesResult(BrokerName.TINVEST, listOf("uid-1"))
            } returns AppResult.Success(statuses)

            val r = useCase.execute(BrokerName.TINVEST, listOf("uid-1"))

            assertTrue(r is AppResult.Success)
            assertEquals(statuses, (r as AppResult.Success).data)
            coVerify(exactly = 1) { tradingStatusRepository.update(statuses) }
        }

    @Test
    fun `market failure passes through`() =
        runBlocking {
            val original = AppError.Network("network down", null)
            coEvery {
                marketDataRepository.getTradingStatusesResult(BrokerName.TINVEST, listOf("uid-1"))
            } returns AppResult.Failure(original)

            val r = useCase.execute(BrokerName.TINVEST, listOf("uid-1"))

            assertTrue(r is AppResult.Failure)
            assertEquals(original, (r as AppResult.Failure).error)
            coVerify(exactly = 0) { tradingStatusRepository.update(any()) }
        }

    @Test
    fun `exception returns Failure Unknown`() =
        runBlocking {
            coEvery {
                marketDataRepository.getTradingStatusesResult(any(), any())
            } throws RuntimeException("boom")

            val r = useCase.execute(BrokerName.TINVEST, listOf("uid-1"))

            assertTrue(r is AppResult.Failure)
            assertTrue((r as AppResult.Failure).error is AppError.Unknown)
        }

    @Test
    fun `cancellation rethrows`() =
        runBlocking {
            coEvery {
                marketDataRepository.getTradingStatusesResult(any(), any())
            } throws CancellationException()

            try {
                useCase.execute(BrokerName.TINVEST, listOf("uid-1"))
                fail("expected CancellationException")
            } catch (expected: CancellationException) {
                // OK
            }
        }
}
