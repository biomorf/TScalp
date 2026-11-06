package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceConsumer
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerInitState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CheckBrokerInitializationUseCaseTest {
    private val brokerRegistry = mockk<BrokerRegistry>(relaxed = true)
    private val priceFlowManager = mockk<PriceFlowManager>(relaxed = true)
    private val positionFlowManager = mockk<PositionFlowManager>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val useCase =
        CheckBrokerInitializationUseCase(
            brokerRegistry,
            priceFlowManager,
            positionFlowManager,
            logger,
        )

    @Test
    fun `at least one broker initialized returns Initialized, no cleanup`() =
        runBlocking {
            val initialized = mockk<BrokerApi> { every { isInitialized } returns true }
            val notInitialized = mockk<BrokerApi> { every { isInitialized } returns false }
            every { brokerRegistry.getAllBrokers() } returns listOf(notInitialized, initialized)

            val r = useCase.execute(PriceConsumer.ORDERS)

            assertTrue(r is AppResult.Success)
            assertEquals(BrokerInitState.AnyInitialized, (r as AppResult.Success).data)
            verify(exactly = 0) { priceFlowManager.clearInterest(any()) }
            verify(exactly = 0) { positionFlowManager.stop() }
        }

    @Test
    fun `no brokers initialized returns NotInitialized, cleanup called`() =
        runBlocking {
            every { brokerRegistry.getAllBrokers() } returns emptyList()

            val r = useCase.execute(PriceConsumer.PORTFOLIO)

            assertTrue(r is AppResult.Success)
            assertEquals(BrokerInitState.NoneInitialized, (r as AppResult.Success).data)
            verify(exactly = 1) { priceFlowManager.clearInterest(PriceConsumer.PORTFOLIO) }
            verify(exactly = 1) { positionFlowManager.stop() }
        }

    @Test
    fun `exception becomes Failure Unknown`() =
        runBlocking {
            every { brokerRegistry.getAllBrokers() } throws RuntimeException("boom")

            val r = useCase.execute(PriceConsumer.ORDERS)

            assertTrue(r is AppResult.Failure)
            val err = (r as AppResult.Failure).error
            assertTrue(err is AppError.Unknown)
            assertTrue(err.message.contains("boom"))
            assertEquals("boom", err.cause?.message)
        }

    @Test
    fun `CancellationException is rethrown`() =
        runBlocking {
            every { brokerRegistry.getAllBrokers() } throws CancellationException("cancel")

            try {
                useCase.execute(PriceConsumer.ORDERS)
                fail("expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("cancel", e.message)
            }
        }
}
