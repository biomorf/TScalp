package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountSelectionRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.PortfolioRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceOrderUseCaseTest {
    private val postOrderUseCase = mockk<PostOrderUseCase>(relaxed = true)
    private val cardsRepository = mockk<CardsRepository>(relaxed = true)
    private val accountSelectionRepository = mockk<AccountSelectionRepository>(relaxed = true)
    private val portfolioRepository = mockk<PortfolioRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private val useCase =
        PlaceOrderUseCase(
            postOrderUseCase = postOrderUseCase,
            cardsRepository = cardsRepository,
            accountSelectionRepository = accountSelectionRepository,
            portfolioRepository = portfolioRepository,
            logger = logger,
        )

    private val card =
        CardIdentity(
            tscalpInstrumentId = "uid-sber",
            brokerName = BrokerName.TINVEST,
            accountId = "acc-1",
        )

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    private val request =
        PlaceOrderRequest(
            instrument = sber,
            quantity = 1L,
            orderType = OrderTypeSelection.Market,
            limitPrice = "",
            stopPrice = "",
            expirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
            pairedInstrument = null,
            pairedMultiplier = "1",
        )

    private fun stubHappyResolve() {
        every { cardsRepository.identities } returns flowOf(listOf(card))
        every { accountSelectionRepository.selected(BrokerName.TINVEST) } returns flowOf("acc-1")
    }

    @Test
    fun `success returns primary paired and freeBalance`() =
        runBlocking {
            stubHappyResolve()
            coEvery { postOrderUseCase.execute(any()) } returns
                PostOrderOutcome.Success(
                    primary = PrimaryOutcome.Regular("order-1", 1L, 1L),
                    paired = null,
                )
            coEvery { portfolioRepository.getBalanceResult("acc-1") } returns
                AppResult.Success(5000.0)

            val r = useCase.execute(request, OrderDirection.BUY)

            assertTrue(r is PlaceOrderOutcome.Success)
            val success = r as PlaceOrderOutcome.Success
            assertEquals(5000.0, success.freeBalance!!, 0.0)
            coVerify(exactly = 1) {
                postOrderUseCase.execute(
                    match { input ->
                        input.instrument.tscalpInstrumentId == "uid-sber" &&
                            input.accountId == "acc-1" &&
                            input.direction == OrderDirection.BUY
                    },
                )
            }
        }

    @Test
    fun `success without balance returns null freeBalance`() =
        runBlocking {
            stubHappyResolve()
            coEvery { postOrderUseCase.execute(any()) } returns
                PostOrderOutcome.Success(
                    primary = PrimaryOutcome.Regular("order-1", 1L, 1L),
                    paired = null,
                )
            coEvery { portfolioRepository.getBalanceResult("acc-1") } returns
                AppResult.Failure(AppError.Network("down", null))

            val r = useCase.execute(request, OrderDirection.BUY)

            assertTrue(r is PlaceOrderOutcome.Success)
            assertNull((r as PlaceOrderOutcome.Success).freeBalance)
        }

    @Test
    fun `insufficient funds maps to PlaceOrderOutcome InsufficientFunds`() =
        runBlocking {
            stubHappyResolve()
            coEvery { postOrderUseCase.execute(any()) } returns
                PostOrderOutcome.InsufficientFunds(balance = 100.0, required = 1000.0)

            val r = useCase.execute(request, OrderDirection.BUY)

            assertTrue(r is PlaceOrderOutcome.InsufficientFunds)
            val insufficient = r as PlaceOrderOutcome.InsufficientFunds
            assertEquals(100.0, insufficient.balance, 0.0)
            assertEquals(1000.0, insufficient.required, 0.0)
        }

    @Test
    fun `error from postOrder maps to PlaceOrderOutcome Error`() =
        runBlocking {
            stubHappyResolve()
            coEvery { postOrderUseCase.execute(any()) } returns
                PostOrderOutcome.Error("boom", null)

            val r = useCase.execute(request, OrderDirection.BUY)

            assertTrue(r is PlaceOrderOutcome.Error)
            assertEquals("boom", (r as PlaceOrderOutcome.Error).message)
        }

    @Test
    fun `invalid when neither card nor selected account resolves`() =
        runBlocking {
            every { cardsRepository.identities } returns flowOf(emptyList())
            every { accountSelectionRepository.selected(BrokerName.TINVEST) } returns flowOf(null)

            val r = useCase.execute(request, OrderDirection.BUY)

            assertTrue(r is PlaceOrderOutcome.InvalidRequest)
            coVerify(exactly = 0) { postOrderUseCase.execute(any()) }
        }
}
