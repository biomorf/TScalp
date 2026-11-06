package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.PortfolioRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccountType
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Position
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LoadPortfolioUseCaseTest {
    private val accountsRepository = mockk<AccountsRepository>(relaxed = true)
    private val portfolioRepository = mockk<PortfolioRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val useCase =
        LoadPortfolioUseCase(accountsRepository, portfolioRepository, logger)

    private val acc1 = BrokerAccount(id = "acc-1", name = "A1", type = BrokerAccountType.BROKER)
    private val acc2 = BrokerAccount(id = "acc-2", name = "A2", type = BrokerAccountType.BROKER)

    private fun emptyPositions(): List<Position> = emptyList()

    @Test
    fun `success with accounts returns PortfolioLoadResult with sheet`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1, acc2))
            coEvery { portfolioRepository.fetchPositionsResult(BrokerName.TINVEST, "acc-1", true) } returns
                AppResult.Success(emptyPositions())
            coEvery { portfolioRepository.fetchPositionsResult(BrokerName.TINVEST, "acc-2", true) } returns
                AppResult.Success(emptyPositions())
            coEvery { portfolioRepository.getBalanceResult("acc-1") } returns AppResult.Success(100.0)
            coEvery { portfolioRepository.getBalanceResult("acc-2") } returns AppResult.Success(200.0)

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals(listOf(acc1, acc2), data.accounts)
            assertEquals(2, data.sheet.accounts.size)
            assertEquals(
                1,
                data.sheet.accounts[AccountKey(BrokerName.TINVEST, "acc-1")]
                    ?.balances
                    ?.size,
            )
            assertEquals(
                200.0,
                data.sheet.accounts[AccountKey(BrokerName.TINVEST, "acc-2")]
                    ?.balances
                    ?.first()
                    ?.money,
            )
        }

    @Test
    fun `empty accounts returns empty sheet`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(emptyList())

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals(emptyList<BrokerAccount>(), data.accounts)
            assertTrue(data.sheet.accounts.isEmpty())
            coVerify(exactly = 0) {
                portfolioRepository.fetchPositionsResult(any(), any(), any())
            }
        }

    @Test
    fun `getAccounts failure passes through unchanged`() =
        runBlocking {
            val original = AppError.Network("network down", null)
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Failure(original)

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Failure)
            assertEquals(original, (r as AppResult.Failure).error)
            coVerify(exactly = 0) {
                portfolioRepository.fetchPositionsResult(any(), any(), any())
            }
        }

    @Test
    fun `positions failure for one account skips it, other preserved`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1, acc2))
            coEvery { portfolioRepository.fetchPositionsResult(BrokerName.TINVEST, "acc-1", true) } returns
                AppResult.Failure(AppError.Network("boom-1", null))
            coEvery { portfolioRepository.fetchPositionsResult(BrokerName.TINVEST, "acc-2", true) } returns
                AppResult.Success(emptyPositions())
            coEvery { portfolioRepository.getBalanceResult("acc-2") } returns AppResult.Success(200.0)

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals(2, data.accounts.size)
            assertEquals(1, data.sheet.accounts.size)
            assertTrue(data.sheet.accounts.containsKey(AccountKey(BrokerName.TINVEST, "acc-2")))
            assertTrue(!data.sheet.accounts.containsKey(AccountKey(BrokerName.TINVEST, "acc-1")))
        }

    @Test
    fun `balance failure produces empty balances, positions preserved`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1))
            coEvery { portfolioRepository.fetchPositionsResult(BrokerName.TINVEST, "acc-1", true) } returns
                AppResult.Success(emptyPositions())
            coEvery { portfolioRepository.getBalanceResult("acc-1") } returns
                AppResult.Failure(AppError.Network("balance down", null))

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            val accountPortfolio = data.sheet.accounts[AccountKey(BrokerName.TINVEST, "acc-1")]
            assertTrue(accountPortfolio != null)
            assertTrue(accountPortfolio!!.balances.isEmpty())
        }

    @Test
    fun `exception becomes Failure Unknown`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } throws
                RuntimeException("boom")

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

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
                useCase.execute(BrokerName.TINVEST, sandboxMode = true)
                fail("expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("cancel", e.message)
            }
        }
}
