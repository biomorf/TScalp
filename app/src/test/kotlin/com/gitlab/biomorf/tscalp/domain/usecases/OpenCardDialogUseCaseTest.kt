package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountSelectionRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccountType
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OpenCardDialogUseCaseTest {
    private val cardsRepository = mockk<CardsRepository>(relaxed = true)
    private val accountSelectionRepository = mockk<AccountSelectionRepository>(relaxed = true)
    private val loadCardAccountsUseCase = mockk<LoadCardAccountsUseCase>(relaxed = true)
    private val sandboxPreferencesRepository = mockk<SandboxPreferencesRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private val useCase =
        OpenCardDialogUseCase(
            cardsRepository = cardsRepository,
            accountSelectionRepository = accountSelectionRepository,
            loadCardAccountsUseCase = loadCardAccountsUseCase,
            sandboxPreferencesRepository = sandboxPreferencesRepository,
            logger = logger,
        )

    private val acc1 = BrokerAccount(id = "acc-1", name = "A1", type = BrokerAccountType.BROKER)
    private val acc2 = BrokerAccount(id = "acc-2", name = "A2", type = BrokerAccountType.BROKER)

    @Test
    fun `success with existing card uses its broker and accountId`() =
        runBlocking {
            val card =
                CardIdentity(
                    tscalpInstrumentId = "uid-sber",
                    ticker = "SBER",
                    brokerName = BrokerName.TINVEST,
                    accountId = "acc-2",
                )
            every { cardsRepository.identities } returns flowOf(listOf(card))
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
            coEvery {
                loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-2")
            } returns AppResult.Success(CardAccountsResult(accounts = listOf(acc1, acc2), selectedAccountId = "acc-2"))

            val r = useCase.execute("SBER")

            assertTrue(r is OpenCardDialogOutcome.Success)
            val data = (r as OpenCardDialogOutcome.Success).result
            assertEquals("SBER", data.ticker)
            assertEquals(BrokerName.TINVEST, data.broker)
            assertEquals(listOf(acc1, acc2), data.accounts)
            assertEquals("acc-2", data.selectedAccountId)
            coVerify(exactly = 1) { loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-2") }
        }

    @Test
    fun `no card uses selected(TINVEST) fallback`() =
        runBlocking {
            every { cardsRepository.identities } returns flowOf(emptyList())
            every { accountSelectionRepository.selected(BrokerName.TINVEST) } returns flowOf("acc-1")
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
            coEvery {
                loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-1")
            } returns AppResult.Success(CardAccountsResult(accounts = listOf(acc1), selectedAccountId = "acc-1"))

            val r = useCase.execute("NEWTICKER")

            assertTrue(r is OpenCardDialogOutcome.Success)
            val data = (r as OpenCardDialogOutcome.Success).result
            assertEquals("NEWTICKER", data.ticker)
            assertEquals(BrokerName.TINVEST, data.broker)
            assertEquals("acc-1", data.selectedAccountId)
            coVerify(exactly = 1) { loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-1") }
        }

    @Test
    fun `loadCardAccounts failure is passthrough`() =
        runBlocking {
            every { cardsRepository.identities } returns flowOf(emptyList())
            every { accountSelectionRepository.selected(BrokerName.TINVEST) } returns flowOf(null)
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns true
            coEvery {
                loadCardAccountsUseCase.execute(BrokerName.TINVEST, true, null)
            } returns AppResult.Failure(AppError.Network("down", null))

            val r = useCase.execute("SBER")

            assertTrue(r is OpenCardDialogOutcome.Failure)
            val failure = r as OpenCardDialogOutcome.Failure
            assertEquals(BrokerName.TINVEST, failure.broker)
            assertTrue(failure.error is AppError.Network)
        }

    @Test
    fun `cancellation is rethrown`() =
        runBlocking {
            every { cardsRepository.identities } returns flowOf(emptyList())
            every { accountSelectionRepository.selected(BrokerName.TINVEST) } returns flowOf(null)
            coEvery { sandboxPreferencesRepository.isSandboxMode() } throws CancellationException()

            try {
                useCase.execute("SBER")
                fail("expected CancellationException")
            } catch (expected: CancellationException) {
                // expected
            }
        }
}
