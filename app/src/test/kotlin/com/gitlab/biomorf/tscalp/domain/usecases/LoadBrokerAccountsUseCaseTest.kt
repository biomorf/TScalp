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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadBrokerAccountsUseCaseTest {
    private val accountsRepository = mockk<AccountsRepository>(relaxed = true)
    private val accountPreferencesRepository = mockk<AccountPreferencesRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val useCase =
        LoadBrokerAccountsUseCase(accountsRepository, accountPreferencesRepository, logger)

    private val acc1 = BrokerAccount(id = "acc-1", name = "Account 1", type = BrokerAccountType.BROKER)
    private val acc2 = BrokerAccount(id = "acc-2", name = "Account 2", type = BrokerAccountType.BROKER)

    @Test
    fun `blank default + non-empty accounts selects and saves first`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1, acc2))
            coEvery { accountPreferencesRepository.loadDefaultAccountId(BrokerName.TINVEST) } returns null

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals(listOf(acc1, acc2), data.accounts)
            assertEquals("acc-1", data.defaultAccountId)
            coVerify(exactly = 1) {
                accountPreferencesRepository.saveDefaultAccountId(BrokerName.TINVEST, "acc-1")
            }
        }

    @Test
    fun `existing default is preserved, no save`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(listOf(acc1, acc2))
            coEvery { accountPreferencesRepository.loadDefaultAccountId(BrokerName.TINVEST) } returns "acc-2"

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Success)
            assertEquals("acc-2", (r as AppResult.Success).data.defaultAccountId)
            coVerify(exactly = 0) {
                accountPreferencesRepository.saveDefaultAccountId(any(), any())
            }
        }

    @Test
    fun `empty accounts keeps blank default, no save`() =
        runBlocking {
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Success(emptyList())
            coEvery { accountPreferencesRepository.loadDefaultAccountId(BrokerName.TINVEST) } returns null

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals(emptyList<BrokerAccount>(), data.accounts)
            assertEquals("", data.defaultAccountId)
            coVerify(exactly = 0) {
                accountPreferencesRepository.saveDefaultAccountId(any(), any())
            }
        }

    @Test
    fun `repository failure passes through unchanged`() =
        runBlocking {
            val original = AppError.Network("network down", null)
            coEvery { accountsRepository.getAccountsResult(BrokerName.TINVEST, true) } returns
                AppResult.Failure(original)

            val r = useCase.execute(BrokerName.TINVEST, sandboxMode = true)

            assertTrue(r is AppResult.Failure)
            assertEquals(original, (r as AppResult.Failure).error)
            coVerify(exactly = 0) {
                accountPreferencesRepository.loadDefaultAccountId(any())
            }
        }
}
