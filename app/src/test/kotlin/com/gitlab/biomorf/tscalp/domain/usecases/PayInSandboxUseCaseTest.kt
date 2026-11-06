package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PayInSandboxUseCaseTest {
    private val accountsRepository = mockk<AccountsRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val useCase = PayInSandboxUseCase(accountsRepository, logger)

    private val amount = SandboxMoney(currency = "RUB", units = 100_000)

    @Test
    fun `success returns Success Unit`() =
        runBlocking {
            coEvery { accountsRepository.sandboxPayInResult(any(), any()) } returns
                AppResult.Success(Unit)

            val r = useCase.execute("acc-1", amount)

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 1) {
                accountsRepository.sandboxPayInResult("acc-1", amount)
            }
        }

    @Test
    fun `repository failure passes through unchanged`() =
        runBlocking {
            val original = AppError.Network("network down", null)
            coEvery { accountsRepository.sandboxPayInResult(any(), any()) } returns
                AppResult.Failure(original)

            val r = useCase.execute("acc-1", amount)

            assertTrue(r is AppResult.Failure)
            assertEquals(original, (r as AppResult.Failure).error)
        }

    @Test
    fun `exception becomes Failure Unknown`() =
        runBlocking {
            coEvery { accountsRepository.sandboxPayInResult(any(), any()) } throws
                RuntimeException("boom")

            val r = useCase.execute("acc-1", amount)

            assertTrue(r is AppResult.Failure)
            val err = (r as AppResult.Failure).error
            assertTrue(err is AppError.Unknown)
            assertTrue(err.message.contains("boom"))
            assertEquals("boom", err.cause?.message)
        }

    @Test
    fun `CancellationException is rethrown`() =
        runBlocking {
            coEvery { accountsRepository.sandboxPayInResult(any(), any()) } throws
                CancellationException("cancel")

            try {
                useCase.execute("acc-1", amount)
                fail("expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("cancel", e.message)
            }
        }
}
