package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SetSandboxModeUseCaseTest {
    private val sandboxPreferencesRepository = mockk<SandboxPreferencesRepository>(relaxed = true)
    private val loadBrokerAccountsUseCase = mockk<LoadBrokerAccountsUseCase>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private val useCase =
        SetSandboxModeUseCase(sandboxPreferencesRepository, loadBrokerAccountsUseCase, logger)

    private val acc1 = BrokerAccount(id = "acc-1", name = "Account 1", type = BrokerAccountType.BROKER)

    @Test
    fun `success saves mode and returns accounts`() =
        runBlocking {
            coEvery { sandboxPreferencesRepository.setSandboxMode(true) } returns Unit
            coEvery { loadBrokerAccountsUseCase.execute(BrokerName.TINVEST, true) } returns
                AppResult.Success(LoadAccountsResult(accounts = listOf(acc1), defaultAccountId = "acc-1"))

            val r = useCase.execute(true)

            assertTrue(r is AppResult.Success)
            assertEquals(listOf(acc1), (r as AppResult.Success).data.accounts)
            assertEquals("acc-1", r.data.defaultAccountId)
            coVerify(exactly = 1) { sandboxPreferencesRepository.setSandboxMode(true) }
            coVerify(exactly = 1) { loadBrokerAccountsUseCase.execute(BrokerName.TINVEST, true) }
        }

    @Test
    fun `setSandboxMode throw becomes Failure Unknown`() =
        runBlocking {
            coEvery { sandboxPreferencesRepository.setSandboxMode(any()) } throws
                RuntimeException("boom")

            val r = useCase.execute(false)

            assertTrue(r is AppResult.Failure)
            val err = (r as AppResult.Failure).error
            assertTrue(err is AppError.Unknown)
            assertTrue(err.message.contains("boom"))
            coVerify(exactly = 0) { loadBrokerAccountsUseCase.execute(any(), any()) }
        }

    @Test
    fun `load accounts failure passes through`() =
        runBlocking {
            coEvery { sandboxPreferencesRepository.setSandboxMode(true) } returns Unit
            val original = AppError.Network("net down", null)
            coEvery { loadBrokerAccountsUseCase.execute(BrokerName.TINVEST, true) } returns
                AppResult.Failure(original)

            val r = useCase.execute(true)

            assertTrue(r is AppResult.Failure)
            assertEquals(original, (r as AppResult.Failure).error)
        }

    @Test
    fun `CancellationException is rethrown`() =
        runBlocking {
            coEvery { sandboxPreferencesRepository.setSandboxMode(any()) } throws
                CancellationException("cancel")

            try {
                useCase.execute(true)
                fail("expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("cancel", e.message)
            }
        }
}
