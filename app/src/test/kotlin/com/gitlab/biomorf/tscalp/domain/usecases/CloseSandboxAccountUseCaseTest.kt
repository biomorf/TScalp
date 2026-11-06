package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccountType
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CloseSandboxAccountUseCaseTest {
    private val accountsRepository = mockk<AccountsRepository>(relaxed = true)
    private val accountPreferencesRepository = mockk<AccountPreferencesRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val useCase =
        CloseSandboxAccountUseCase(accountsRepository, accountPreferencesRepository, logger)

    private val acc1 = BrokerAccount(id = "acc-1", name = "Account 1", type = BrokerAccountType.BROKER)

    @Test
    fun `success closes account, resets default, reloads list`() =
        runBlocking {
            coEvery {
                accountsRepository.closeSandboxAccountResult(BrokerName.TINVEST, "acc-to-close")
            } returns AppResult.Success(Unit)
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1))

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true, accountId = "acc-to-close")

            assertTrue(r is AppResult.Success)
            assertEquals(listOf(acc1), (r as AppResult.Success).data.accounts)
            coVerify(exactly = 1) {
                accountPreferencesRepository.saveDefaultAccountId(BrokerName.TINVEST, "")
            }
        }

    @Test
    fun `close failure passes through, no save, no reload`() =
        runBlocking {
            val original = AppError.Network("close failed", null)
            coEvery {
                accountsRepository.closeSandboxAccountResult(BrokerName.TINVEST, "acc-to-close")
            } returns AppResult.Failure(original)

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true, accountId = "acc-to-close")

            assertTrue(r is AppResult.Failure)
            assertEquals(original, (r as AppResult.Failure).error)
            coVerify(exactly = 0) {
                accountPreferencesRepository.saveDefaultAccountId(any(), any())
            }
            coVerify(exactly = 0) {
                accountsRepository.getAccountsResult(any(), any())
            }
        }

    @Test
    fun `reload failure keeps success, accounts is null`() =
        runBlocking {
            coEvery {
                accountsRepository.closeSandboxAccountResult(BrokerName.TINVEST, "acc-to-close")
            } returns AppResult.Success(Unit)
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Failure(AppError.Network("reload failed", null))

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true, accountId = "acc-to-close")

            assertTrue(r is AppResult.Success)
            assertNull((r as AppResult.Success).data.accounts)
            coVerify(exactly = 1) {
                accountPreferencesRepository.saveDefaultAccountId(BrokerName.TINVEST, "")
            }
        }

    @Test
    fun `exception during close becomes Failure Unknown`() =
        runBlocking {
            coEvery {
                accountsRepository.closeSandboxAccountResult(BrokerName.TINVEST, "acc-to-close")
            } throws RuntimeException("boom")

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true, accountId = "acc-to-close")

            assertTrue(r is AppResult.Failure)
            val err = (r as AppResult.Failure).error
            assertTrue(err is AppError.Unknown)
            assertTrue(err.message.contains("boom"))
        }

    @Test
    fun `CancellationException is rethrown`() =
        runBlocking {
            coEvery {
                accountsRepository.closeSandboxAccountResult(BrokerName.TINVEST, "acc-to-close")
            } throws CancellationException("cancel")

            try {
                useCase.execute(BrokerName.TINVEST, sandboxMode = true, accountId = "acc-to-close")
                fail("expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("cancel", e.message)
            }
        }
}
