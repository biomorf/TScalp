package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.MarketDataRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.moneyPerPointOfPrice
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SelectPairedInstrumentUseCaseTest {
    private val marketDataRepository = mockk<MarketDataRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val useCase = SelectPairedInstrumentUseCase(marketDataRepository, logger)

    private val gazp =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-gazp",
            ticker = "GAZP",
            name = "Газпром",
            isin = "RU0007661625",
        )

    @Test
    fun `success returns price and pointValue`() =
        runBlocking {
            coEvery {
                marketDataRepository.getLastPricesResult(listOf("uid-gazp"))
            } returns AppResult.Success(mapOf("uid-gazp" to 200.0))

            val r = useCase.execute(gazp)

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals(200.0, data.price!!, 0.0)
            assertEquals(gazp.moneyPerPointOfPrice, data.pointValue, 0.0)
        }

    @Test
    fun `getLastPrices failure returns Success with null price`() =
        runBlocking {
            coEvery {
                marketDataRepository.getLastPricesResult(listOf("uid-gazp"))
            } returns AppResult.Failure(AppError.Network("down", null))

            val r = useCase.execute(gazp)

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertNull(data.price)
            assertEquals(gazp.moneyPerPointOfPrice, data.pointValue, 0.0)
        }

    @Test
    fun `exception returns Failure`() =
        runBlocking {
            coEvery {
                marketDataRepository.getLastPricesResult(any())
            } throws RuntimeException("boom")

            val r = useCase.execute(gazp)

            assertTrue(r is AppResult.Failure)
        }

    @Test
    fun `cancellation is rethrown`() =
        runBlocking {
            coEvery {
                marketDataRepository.getLastPricesResult(any())
            } throws CancellationException()

            try {
                useCase.execute(gazp)
                fail("expected CancellationException")
            } catch (expected: CancellationException) {
                // expected
            }
        }
}
