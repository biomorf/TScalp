package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccountType
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LoadCardAccountsUseCaseTest {
    private val accountsRepository = mockk<AccountsRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val useCase = LoadCardAccountsUseCase(accountsRepository, logger)

    private val acc1 = BrokerAccount(id = "acc-1", name = "A1", type = BrokerAccountType.BROKER)
    private val acc2 = BrokerAccount(id = "acc-2", name = "A2", type = BrokerAccountType.BROKER)

    @Test
    fun `saved in list selects it`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1, acc2))

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true, savedAccountId = "acc-2")

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals(listOf(acc1, acc2), data.accounts)
            assertEquals("acc-2", data.selectedAccountId)
        }

    @Test
    fun `saved not in list falls back to first`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1, acc2))

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true, savedAccountId = "stale-acc")

            assertTrue(r is AppResult.Success)
            assertEquals("acc-1", (r as AppResult.Success).data.selectedAccountId)
        }

    @Test
    fun `saved null falls back to first`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1, acc2))

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true, savedAccountId = null)

            assertTrue(r is AppResult.Success)
            assertEquals("acc-1", (r as AppResult.Success).data.selectedAccountId)
        }

    @Test
    fun `empty accounts returns null selected`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(emptyList())

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true, savedAccountId = "acc-1")

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals(emptyList<BrokerAccount>(), data.accounts)
            assertNull(data.selectedAccountId)
        }

    @Test
    fun `trim mismatch falls back to first`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1, acc2))

            // saved с пробелами, аккаунт — без; trim делает match для acc-1
            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true, savedAccountId = "  acc-2  ")

            assertTrue(r is AppResult.Success)
            // trim применён к saved и к account.id → "acc-2" matches acc-2
            assertEquals("acc-2", (r as AppResult.Success).data.selectedAccountId)
        }

    @Test
    fun `getAccounts failure passes through unchanged`() =
        runBlocking {
            val original = AppError.Network("network down", null)
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Failure(original)

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true, savedAccountId = "acc-1")

            assertTrue(r is AppResult.Failure)
            assertEquals(original, (r as AppResult.Failure).error)
        }

    @Test
    fun `exception becomes Failure Unknown`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } throws
                RuntimeException("boom")

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true, savedAccountId = "acc-1")

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
                useCase.execute(BrokerName.TINVEST, sandboxMode = true, savedAccountId = "acc-1")
                fail("expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("cancel", e.message)
            }
        }
}
