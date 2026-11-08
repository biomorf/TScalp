package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.OrdersRepository
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderResult
import com.gitlab.biomorf.tscalp.domain.models.OrderStatus
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostOrderUseCaseTest {
    private val ordersRepository = mockk<OrdersRepository>(relaxed = true)
    private val prepareOrderRequest = mockk<PrepareOrderRequestUseCase>()
    private val useCase = PostOrderUseCase(ordersRepository, prepareOrderRequest)

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    private val tmon =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-pair",
            ticker = "TMON",
            name = "ТМОН",
            isin = "RU0000000000",
        )

    private val baseInput =
        PostOrderInput(
            instrument = sber,
            quantity = 2L,
            direction = OrderDirection.BUY,
            accountId = "acc-1",
            orderType = OrderTypeSelection.Market,
            limitPrice = null,
            stopPrice = null,
            expirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
            paired = null,
        )

    private fun marketPrepared(quantity: Long = 2L): PrepareOrderRequestUseCase.PreparedOrders =
        PrepareOrderRequestUseCase.PreparedOrders(
            primaryRequest =
                BrokerOrderRequest(
                    instrument = sber,
                    quantity = quantity,
                    direction = OrderDirection.BUY,
                    accountId = "acc-1",
                ),
            pairedRequest = null,
            isPrimaryStop = false,
            isPairedStop = null,
        )

    private fun stubPrepare(prepared: PrepareOrderRequestUseCase.PreparedOrders) {
        coEvery {
            prepareOrderRequest.prepare(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns prepared
    }

    @Test
    fun `invalid request returns InvalidRequest`() =
        runBlocking {
            val r = useCase.execute(baseInput.copy(accountId = ""))
            assertTrue(r is PostOrderOutcome.InvalidRequest)
        }

    @Test
    fun `success regular order returns Success with PrimaryOutcome_Regular`() =
        runBlocking {
            coEvery { ordersRepository.checkTradeAvailabilityResult(any(), any(), any(), any(), any()) } returns
                AppResult.Success(TradeCheckResult.Success)
            stubPrepare(marketPrepared())
            coEvery { ordersRepository.postOrderResult(any()) } returns
                AppResult.Success(OrderResult("ord-1", 2, 2, OrderStatus.FILLED))

            val r = useCase.execute(baseInput)

            assertTrue(r is PostOrderOutcome.Success)
            val s = r as PostOrderOutcome.Success
            assertTrue(s.primary is PrimaryOutcome.Regular)
            assertEquals("ord-1", (s.primary as PrimaryOutcome.Regular).orderId)
            assertNull(s.paired)
            coVerify(exactly = 1) { ordersRepository.postOrderResult(any()) }
        }

    @Test
    fun `insufficient funds returns InsufficientFunds`() =
        runBlocking {
            coEvery { ordersRepository.checkTradeAvailabilityResult(any(), any(), any(), any(), any()) } returns
                AppResult.Success(TradeCheckResult.InsufficientFunds(balance = 100.0, required = 500.0))

            val r = useCase.execute(baseInput)

            assertTrue(r is PostOrderOutcome.InsufficientFunds)
            assertEquals(100.0, (r as PostOrderOutcome.InsufficientFunds).balance, 0.0)
            assertEquals(500.0, r.required, 0.0)
        }

    @Test
    fun `check availability error returns Error`() =
        runBlocking {
            coEvery { ordersRepository.checkTradeAvailabilityResult(any(), any(), any(), any(), any()) } returns
                AppResult.Failure(AppError.Unknown("network down", null))

            val r = useCase.execute(baseInput)

            assertTrue(r is PostOrderOutcome.Error)
            assertEquals("network down", (r as PostOrderOutcome.Error).message)
        }

    @Test
    fun `primary order failure returns Error`() =
        runBlocking {
            coEvery { ordersRepository.checkTradeAvailabilityResult(any(), any(), any(), any(), any()) } returns
                AppResult.Success(TradeCheckResult.Success)
            stubPrepare(marketPrepared())
            coEvery { ordersRepository.postOrderResult(any()) } returns
                AppResult.Failure(AppError.Unknown("INVALID_ARGUMENT", null))

            val r = useCase.execute(baseInput)

            assertTrue(r is PostOrderOutcome.Error)
            assertEquals("INVALID_ARGUMENT", (r as PostOrderOutcome.Error).message)
        }

    @Test
    fun `paired success returns Success with PairedOutcome_Regular`() =
        runBlocking {
            val pairedInput =
                PairedOrderInput(
                    instrument = tmon,
                    accountId = "acc-1",
                    multiplier = "2",
                )
            val preparedWithPair =
                marketPrepared().copy(
                    pairedRequest =
                        BrokerOrderRequest(
                            instrument = tmon,
                            quantity = 4,
                            direction = OrderDirection.SELL,
                            accountId = "acc-1",
                        ),
                    isPairedStop = false,
                )
            coEvery { ordersRepository.checkTradeAvailabilityResult(any(), any(), any(), any(), any()) } returns
                AppResult.Success(TradeCheckResult.Success)
            stubPrepare(preparedWithPair)
            coEvery { ordersRepository.postOrderResult(any()) } returns
                AppResult.Success(OrderResult("ord-1", 2, 2, OrderStatus.FILLED)) andThen
                AppResult.Success(OrderResult("ord-2", 4, 4, OrderStatus.FILLED))

            val r = useCase.execute(baseInput.copy(paired = pairedInput))

            assertTrue(r is PostOrderOutcome.Success)
            val s = r as PostOrderOutcome.Success
            assertTrue(s.paired is PairedOutcome.Regular)
            assertEquals("ord-2", (s.paired as PairedOutcome.Regular).orderId)
        }

    @Test
    fun `paired failure returns Success with PairedOutcome_Failed`() =
        runBlocking {
            val pairedInput =
                PairedOrderInput(
                    instrument = tmon,
                    accountId = "acc-1",
                    multiplier = "1",
                )
            val preparedWithPair =
                marketPrepared().copy(
                    pairedRequest =
                        BrokerOrderRequest(
                            instrument = tmon,
                            quantity = 2,
                            direction = OrderDirection.SELL,
                            accountId = "acc-1",
                        ),
                    isPairedStop = false,
                )
            coEvery { ordersRepository.checkTradeAvailabilityResult(any(), any(), any(), any(), any()) } returns
                AppResult.Success(TradeCheckResult.Success)
            stubPrepare(preparedWithPair)
            coEvery { ordersRepository.postOrderResult(any()) } returns
                AppResult.Success(OrderResult("ord-1", 2, 2, OrderStatus.FILLED)) andThen
                AppResult.Failure(AppError.Unknown("paired failed", null))

            val r = useCase.execute(baseInput.copy(paired = pairedInput))

            assertTrue(r is PostOrderOutcome.Success)
            val s = r as PostOrderOutcome.Success
            assertTrue(s.paired is PairedOutcome.Failed)
            assertEquals("paired failed", (s.paired as PairedOutcome.Failed).message)
        }
}
