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
import com.gitlab.biomorf.tscalp.domain.models.Share
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

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    @Test
    fun `success with existing card uses its broker and accountId`() =
        runBlocking {
            val card = CardIdentity("uid-sber", BrokerName.TINVEST, "acc-2")
            every { cardsRepository.identities } returns flowOf(listOf(card))
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
            coEvery {
                loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-2")
            } returns AppResult.Success(CardAccountsResult(accounts = listOf(acc1, acc2), selectedAccountId = "acc-2"))

            val r = useCase.execute(sber, currentAccountId = "acc-2")

            assertTrue(r is OpenCardDialogOutcome.Success)
            val data = (r as OpenCardDialogOutcome.Success).result
            assertEquals(BrokerName.TINVEST, data.broker)
            assertEquals(listOf(acc1, acc2), data.accounts)
            assertEquals("acc-2", data.selectedAccountId)
            coVerify(exactly = 1) { loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-2") }
        }

    @Test
    fun `no card uses selected fallback`() =
        runBlocking {
            every { cardsRepository.identities } returns flowOf(emptyList())
            every { accountSelectionRepository.selected(BrokerName.TINVEST) } returns flowOf("acc-1")
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
            coEvery {
                loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-1")
            } returns AppResult.Success(CardAccountsResult(accounts = listOf(acc1), selectedAccountId = "acc-1"))

            val r = useCase.execute(sber, currentAccountId = null)

            assertTrue(r is OpenCardDialogOutcome.Success)
            val data = (r as OpenCardDialogOutcome.Success).result
            assertEquals(BrokerName.TINVEST, data.broker)
            assertEquals("acc-1", data.selectedAccountId)
            coVerify(exactly = 1) { loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-1") }
        }

    @Test
    fun `findCard respects currentAccountId (composite key)`() =
        runBlocking {
            // Две карточки одного инструмента у одного брокера, разные счета.
            val cardA = CardIdentity("uid-sber", BrokerName.TINVEST, "A")
            val cardB = CardIdentity("uid-sber", BrokerName.TINVEST, "B")
            every { cardsRepository.identities } returns flowOf(listOf(cardA, cardB))
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
            coEvery {
                loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "B")
            } returns AppResult.Success(CardAccountsResult(accounts = emptyList(), selectedAccountId = "B"))

            val r = useCase.execute(sber, currentAccountId = "B")

            assertTrue(r is OpenCardDialogOutcome.Success)
            val data = (r as OpenCardDialogOutcome.Success).result
        }

    @Test
    fun `no card for other broker uses instrument broker fallback`() =
        runBlocking {
            every { cardsRepository.identities } returns flowOf(emptyList())
            every { accountSelectionRepository.selected(BrokerName.BCS) } returns flowOf("bcs-1")
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
            coEvery {
                loadCardAccountsUseCase.execute(BrokerName.BCS, false, "bcs-1")
            } returns AppResult.Success(CardAccountsResult(accounts = emptyList(), selectedAccountId = "bcs-1"))

            val bcsShare =
                Share(
                    brokerName = BrokerName.BCS,
                    tscalpInstrumentId = "figi-sber",
                    ticker = "SBER",
                    name = "Сбербанк",
                    isin = "RU0009029540",
                )

            val r = useCase.execute(bcsShare, currentAccountId = null)

            assertTrue(r is OpenCardDialogOutcome.Success)
            val data = (r as OpenCardDialogOutcome.Success).result
            assertEquals(BrokerName.BCS, data.broker)
            coVerify(exactly = 1) { loadCardAccountsUseCase.execute(BrokerName.BCS, false, "bcs-1") }
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

            val r = useCase.execute(sber, currentAccountId = null)

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
                useCase.execute(sber, currentAccountId = null)
                fail("expected CancellationException")
            } catch (expected: CancellationException) {
                // expected
            }
        }
}
