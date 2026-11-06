package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.OrdersRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccountType
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Order
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CancelOrderUseCaseTest {
    private val accountsRepository = mockk<AccountsRepository>(relaxed = true)
    private val ordersRepository = mockk<OrdersRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val useCase = CancelOrderUseCase(accountsRepository, ordersRepository, logger)

    private val acc1 = BrokerAccount(id = "acc-1", name = "A1", type = BrokerAccountType.BROKER)
    private val acc2 = BrokerAccount(id = "acc-2", name = "A2", type = BrokerAccountType.BROKER)

    private fun stopOrder(id: String): Order.Stop = mockk(relaxed = true) { every { orderId } returns id }

    private fun regularOrder(id: String): Order.Regular = mockk(relaxed = true) { every { orderId } returns id }

    @Test
    fun `stop order dispatches to cancelStopOrderResult`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1, acc2))
            coEvery { ordersRepository.cancelStopOrderResult("acc-1", "ord-1") } returns
                AppResult.Success(Unit)

            val r =
                useCase.execute(
                    BrokerName.TINVEST,
                    order = stopOrder("ord-1"),
                    sandboxMode = true,
                )

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 1) { ordersRepository.cancelStopOrderResult("acc-1", "ord-1") }
            coVerify(exactly = 0) { ordersRepository.cancelOrderResult(any(), any(), any()) }
        }

    @Test
    fun `regular order dispatches to cancelOrderResult`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1))
            coEvery { ordersRepository.cancelOrderResult(BrokerName.TINVEST, "acc-1", "ord-2") } returns
                AppResult.Success(Unit)

            val r =
                useCase.execute(
                    BrokerName.TINVEST,
                    order = regularOrder("ord-2"),
                    sandboxMode = true,
                )

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 1) {
                ordersRepository.cancelOrderResult(BrokerName.TINVEST, "acc-1", "ord-2")
            }
            coVerify(exactly = 0) { ordersRepository.cancelStopOrderResult(any(), any()) }
        }

    @Test
    fun `getAccounts failure passes through unchanged`() =
        runBlocking {
            val original = AppError.Network("network down", null)
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Failure(original)

            val r =
                useCase.execute(
                    BrokerName.TINVEST,
                    order = stopOrder("ord-1"),
                    sandboxMode = true,
                )

            assertTrue(r is AppResult.Failure)
            assertEquals(original, (r as AppResult.Failure).error)
            coVerify(exactly = 0) { ordersRepository.cancelStopOrderResult(any(), any()) }
            coVerify(exactly = 0) { ordersRepository.cancelOrderResult(any(), any(), any()) }
        }

    @Test
    fun `empty accounts returns Failure with message, no cancel call`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(emptyList())

            val r =
                useCase.execute(
                    BrokerName.TINVEST,
                    order = stopOrder("ord-1"),
                    sandboxMode = true,
                )

            assertTrue(r is AppResult.Failure)
            val err = (r as AppResult.Failure).error
            assertTrue(err.message.contains("Нет доступных счетов"))
            coVerify(exactly = 0) { ordersRepository.cancelStopOrderResult(any(), any()) }
            coVerify(exactly = 0) { ordersRepository.cancelOrderResult(any(), any(), any()) }
        }

    @Test
    fun `cancel failure passes through unchanged`() =
        runBlocking {
            val original = AppError.Network("cancel down", null)
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1))
            coEvery { ordersRepository.cancelStopOrderResult("acc-1", "ord-1") } returns
                AppResult.Failure(original)

            val r =
                useCase.execute(
                    BrokerName.TINVEST,
                    order = stopOrder("ord-1"),
                    sandboxMode = true,
                )

            assertTrue(r is AppResult.Failure)
            assertEquals(original, (r as AppResult.Failure).error)
        }

    @Test
    fun `exception becomes Failure Unknown`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } throws
                RuntimeException("boom")

            val r =
                useCase.execute(
                    BrokerName.TINVEST,
                    order = stopOrder("ord-1"),
                    sandboxMode = true,
                )

            assertTrue(r is AppResult.Failure)
            val err = (r as AppResult.Failure).error
            assertTrue(err is AppError.Unknown)
            assertTrue(err.message.contains("boom"))
            assertEquals("boom", err.cause?.message)
        }

    @Test
    fun `CancellationException is rethrown`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } throws
                CancellationException("cancel")

            try {
                useCase.execute(
                    BrokerName.TINVEST,
                    order = stopOrder("ord-1"),
                    sandboxMode = true,
                )
                fail("expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("cancel", e.message)
            }
        }
}
