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

class OpenSandboxAccountUseCaseTest {
    private val accountsRepository = mockk<AccountsRepository>(relaxed = true)
    private val accountPreferencesRepository = mockk<AccountPreferencesRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val useCase =
        OpenSandboxAccountUseCase(accountsRepository, accountPreferencesRepository, logger)

    private val acc1 = BrokerAccount(id = "acc-1", name = "Account 1", type = BrokerAccountType.BROKER)

    @Test
    fun `success opens account, saves default, reloads list`() =
        runBlocking {
            coEvery { accountsRepository.openSandboxAccountResult(BrokerName.TINVEST) } returns
                AppResult.Success("new-acc")
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1))

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals("new-acc", data.accountId)
            assertEquals(listOf(acc1), data.accounts)
            coVerify(exactly = 1) {
                accountPreferencesRepository.saveDefaultAccountId(BrokerName.TINVEST, "new-acc")
            }
        }

    @Test
    fun `open failure passes through, no save, no reload`() =
        runBlocking {
            val original = AppError.Network("open failed", null)
            coEvery { accountsRepository.openSandboxAccountResult(BrokerName.TINVEST) } returns
                AppResult.Failure(original)

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

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
            coEvery { accountsRepository.openSandboxAccountResult(BrokerName.TINVEST) } returns
                AppResult.Success("new-acc")
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Failure(AppError.Network("reload failed", null))

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals("new-acc", data.accountId)
            assertNull(data.accounts)
            coVerify(exactly = 1) {
                accountPreferencesRepository.saveDefaultAccountId(BrokerName.TINVEST, "new-acc")
            }
        }

    @Test
    fun `exception during open becomes Failure Unknown`() =
        runBlocking {
            coEvery { accountsRepository.openSandboxAccountResult(BrokerName.TINVEST) } throws
                RuntimeException("boom")

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Failure)
            val err = (r as AppResult.Failure).error
            assertTrue(err is AppError.Unknown)
            assertTrue(err.message.contains("boom"))
        }

    @Test
    fun `CancellationException is rethrown`() =
        runBlocking {
            coEvery { accountsRepository.openSandboxAccountResult(BrokerName.TINVEST) } throws
                CancellationException("cancel")

            try {
                useCase.execute(BrokerName.TINVEST, sandboxMode = true)
                fail("expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("cancel", e.message)
            }
        }
}
