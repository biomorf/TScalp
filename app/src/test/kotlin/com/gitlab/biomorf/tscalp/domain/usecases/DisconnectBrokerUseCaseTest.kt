package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.managers.OrderFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.api.roles.BcsLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.FinamLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.TInvestLifecycleApi
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DisconnectBrokerUseCaseTest {
    private val brokerCredentialsRepository = mockk<BrokerCredentialsRepository>(relaxed = true)
    private val tradingStateRepository = mockk<TradingStateRepository>(relaxed = true)
    private val brokerRegistry = mockk<BrokerRegistry>(relaxed = true)
    private val positionFlowManager = mockk<PositionFlowManager>(relaxed = true)
    private val orderFlowManager = mockk<OrderFlowManager>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private val useCase =
        DisconnectBrokerUseCase(
            brokerCredentialsRepository,
            tradingStateRepository,
            brokerRegistry,
            positionFlowManager,
            orderFlowManager,
            logger,
        )

    private val tInvestLifecycle = mockk<TInvestLifecycleApi>(relaxed = true)
    private val bcsLifecycle = mockk<BcsLifecycleApi>(relaxed = true)
    private val finamLifecycle = mockk<FinamLifecycleApi>(relaxed = true)
    private val tInvestBroker = mockk<BrokerApi>(relaxed = true)
    private val bcsBroker = mockk<BrokerApi>(relaxed = true)
    private val finamBroker = mockk<BrokerApi>(relaxed = true)

    @Test
    fun `TInvest full - clear, tradingState, deinit, stop managers, refresh`() =
        runBlocking {
            every { tInvestBroker.lifecycleTInvest } returns tInvestLifecycle
            every { brokerRegistry.getBroker(BrokerName.TINVEST) } returns tInvestBroker

            val r = useCase.execute(BrokerName.TINVEST)

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 1) {
                brokerCredentialsRepository.clearBrokerCredentials(BrokerName.TINVEST)
            }
            coVerify(exactly = 1) { tradingStateRepository.clear() }
            verify(exactly = 1) { tInvestLifecycle.deinitialize() }
            verify(exactly = 1) { positionFlowManager.stop() }
            verify(exactly = 1) { orderFlowManager.stop() }
            verify(exactly = 1) { brokerRegistry.refreshInitializationState() }
        }

    @Test
    fun `BCS - clear, deinit, refresh, no tradingState, no stop`() =
        runBlocking {
            every { bcsBroker.lifecycleBcs } returns bcsLifecycle
            every { brokerRegistry.getBroker(BrokerName.BCS) } returns bcsBroker

            val r = useCase.execute(BrokerName.BCS)

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 1) {
                brokerCredentialsRepository.clearBrokerCredentials(BrokerName.BCS)
            }
            coVerify(exactly = 0) { tradingStateRepository.clear() }
            verify(exactly = 1) { bcsLifecycle.deinitialize() }
            verify(exactly = 0) { positionFlowManager.stop() }
            verify(exactly = 0) { orderFlowManager.stop() }
            verify(exactly = 1) { brokerRegistry.refreshInitializationState() }
        }

    @Test
    fun `Finam - clear, deinit, refresh, no tradingState, no stop`() =
        runBlocking {
            every { finamBroker.lifecycleFinam } returns finamLifecycle
            every { brokerRegistry.getBroker(BrokerName.FINAM) } returns finamBroker

            val r = useCase.execute(BrokerName.FINAM)

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 1) {
                brokerCredentialsRepository.clearBrokerCredentials(BrokerName.FINAM)
            }
            coVerify(exactly = 0) { tradingStateRepository.clear() }
            verify(exactly = 1) { finamLifecycle.deinitialize() }
            verify(exactly = 0) { positionFlowManager.stop() }
            verify(exactly = 0) { orderFlowManager.stop() }
            verify(exactly = 1) { brokerRegistry.refreshInitializationState() }
        }

    @Test
    fun `exception in clear becomes Failure Unknown`() =
        runBlocking {
            coEvery { brokerCredentialsRepository.clearBrokerCredentials(any()) } throws
                RuntimeException("boom")

            val r = useCase.execute(BrokerName.TINVEST)

            assertTrue(r is AppResult.Failure)
            val err = (r as AppResult.Failure).error
            assertTrue(err is AppError.Unknown)
            assertTrue(err.message.contains("boom"))
        }

    @Test
    fun `CancellationException is rethrown`() =
        runBlocking {
            coEvery { brokerCredentialsRepository.clearBrokerCredentials(any()) } throws
                CancellationException("cancel")

            try {
                useCase.execute(BrokerName.TINVEST)
                fail("expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("cancel", e.message)
            }
        }
}
