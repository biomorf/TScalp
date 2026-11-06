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

class LoadOrdersAccountsUseCaseTest {
    private val accountsRepository = mockk<AccountsRepository>(relaxed = true)
    private val accountPreferencesRepository = mockk<AccountPreferencesRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val useCase =
        LoadOrdersAccountsUseCase(accountsRepository, accountPreferencesRepository, logger)

    private val acc1 = BrokerAccount(id = "acc-1", name = "A1", type = BrokerAccountType.BROKER)
    private val acc2 = BrokerAccount(id = "acc-2", name = "A2", type = BrokerAccountType.BROKER)

    @Test
    fun `saved account in list selects it, save not called`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1, acc2))
            coEvery { accountPreferencesRepository.loadDefaultAccountId(BrokerName.TINVEST) } returns "acc-2"

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals(listOf(acc1, acc2), data.accounts)
            assertEquals("acc-2", data.selectedAccountId)
            coVerify(exactly = 0) { accountPreferencesRepository.saveDefaultAccountId(any(), any()) }
        }

    @Test
    fun `saved not in list falls back to first, save not called`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1, acc2))
            coEvery { accountPreferencesRepository.loadDefaultAccountId(BrokerName.TINVEST) } returns "stale-acc"

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Success)
            assertEquals("acc-1", (r as AppResult.Success).data.selectedAccountId)
            coVerify(exactly = 0) { accountPreferencesRepository.saveDefaultAccountId(any(), any()) }
        }

    @Test
    fun `saved null falls back to first, save not called`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1, acc2))
            coEvery { accountPreferencesRepository.loadDefaultAccountId(BrokerName.TINVEST) } returns null

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Success)
            assertEquals("acc-1", (r as AppResult.Success).data.selectedAccountId)
            coVerify(exactly = 0) { accountPreferencesRepository.saveDefaultAccountId(any(), any()) }
        }

    @Test
    fun `empty accounts returns null selected`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(emptyList())
            coEvery { accountPreferencesRepository.loadDefaultAccountId(BrokerName.TINVEST) } returns "acc-1"

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals(emptyList<BrokerAccount>(), data.accounts)
            assertNull(data.selectedAccountId)
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
            coVerify(exactly = 0) { accountPreferencesRepository.loadDefaultAccountId(any()) }
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
