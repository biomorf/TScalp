package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountSelectionRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.domain.usecases.CardAccountsResult
import com.gitlab.biomorf.tscalp.domain.usecases.LoadCardAccountsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SaveCardSettingsUseCase
import com.gitlab.biomorf.tscalp.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Тесты [OrderDialogsViewModel] (split `OrdersViewModel`, шаг 12a).
 *
 * Покрытие: openBrokerDialog (success / failure), closeBrokerDialog,
 * onBrokerSelected, saveBrokerSettings (success / failure).
 *
 * `loadCardAccountsUseCase` / `saveCardSettingsUseCase` — suspend ->
 * coEvery / coVerify (AGENTS «Unit-тесты: mockk для suspend-функций»).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderDialogsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val loadCardAccountsUseCase = mockk<LoadCardAccountsUseCase>(relaxed = true)
    private val saveCardSettingsUseCase = mockk<SaveCardSettingsUseCase>(relaxed = true)
    private val cardsRepository = mockk<CardsRepository>(relaxed = true)
    private val accountSelectionRepository = mockk<AccountSelectionRepository>(relaxed = true)
    private val sandboxPreferencesRepository = mockk<SandboxPreferencesRepository>(relaxed = true)
    private val brokerRegistry = mockk<BrokerRegistry>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private fun createVm() =
        OrderDialogsViewModel(
            loadCardAccountsUseCase,
            saveCardSettingsUseCase,
            cardsRepository,
            accountSelectionRepository,
            sandboxPreferencesRepository,
            brokerRegistry,
            logger,
        )

    private fun stubExistingCard(
        ticker: String = "SBER",
        accountId: String? = "acc-1",
    ) {
        every { cardsRepository.identities } returns
            flowOf(listOf(CardIdentity("uid-$ticker", ticker, BrokerName.TINVEST, accountId)))
    }

    @Test
    fun `openBrokerDialog success sets dialog state`() =
        runTest {
            stubExistingCard()
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
            coEvery { loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-1") } returns
                AppResult.Success(CardAccountsResult(accounts = emptyList(), selectedAccountId = "acc-1"))

            val vm = createVm()
            vm.openBrokerDialog("SBER")
            advanceUntilIdle()

            val s = vm.state.value
            assertTrue(s.showBrokerDialog)
            assertEquals("SBER", s.dialogInstrumentTicker)
            assertEquals(BrokerName.TINVEST, s.selectedBroker)
            assertEquals("acc-1", s.selectedAccountIdDialog)
            assertTrue(s.dialogAccounts.isEmpty())
            coVerify(exactly = 1) { loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-1") }
        }

    @Test
    fun `openBrokerDialog failure clears selected account`() =
        runTest {
            stubExistingCard()
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
            coEvery { loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-1") } returns
                AppResult.Failure(AppError.Network("err", null))

            val vm = createVm()
            vm.openBrokerDialog("SBER")
            advanceUntilIdle()

            val s = vm.state.value
            assertTrue(s.showBrokerDialog)
            assertEquals("SBER", s.dialogInstrumentTicker)
            assertNull(s.selectedAccountIdDialog)
            assertTrue(s.dialogAccounts.isEmpty())
        }

    @Test
    fun `closeBrokerDialog resets flags and toggles swipe`() =
        runTest {
            stubExistingCard()
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
            coEvery { loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-1") } returns
                AppResult.Success(CardAccountsResult(accounts = emptyList(), selectedAccountId = "acc-1"))

            val vm = createVm()
            vm.openBrokerDialog("SBER")
            advanceUntilIdle()
            val before = vm.state.value.swipeResetTrigger

            vm.closeBrokerDialog()

            val s = vm.state.value
            assertFalse(s.showBrokerDialog)
            assertNull(s.dialogInstrumentTicker)
            assertEquals(!before, s.swipeResetTrigger)
        }

    @Test
    fun `onBrokerSelected reloads accounts with null saved id`() =
        runTest {
            stubExistingCard()
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
            coEvery { loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-1") } returns
                AppResult.Success(CardAccountsResult(accounts = emptyList(), selectedAccountId = "acc-1"))
            coEvery { loadCardAccountsUseCase.execute(BrokerName.BCS, false, null) } returns
                AppResult.Success(CardAccountsResult(accounts = emptyList(), selectedAccountId = "bcs-acc"))

            val vm = createVm()
            vm.openBrokerDialog("SBER")
            advanceUntilIdle()

            vm.onBrokerSelected(BrokerName.BCS)
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals(BrokerName.BCS, s.selectedBroker)
            assertEquals("bcs-acc", s.selectedAccountIdDialog)
            coVerify(exactly = 1) { loadCardAccountsUseCase.execute(BrokerName.BCS, false, null) }
        }

    @Test
    fun `saveBrokerSettings success closes dialog`() =
        runTest {
            stubExistingCard()
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
            coEvery { loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-1") } returns
                AppResult.Success(CardAccountsResult(accounts = emptyList(), selectedAccountId = "acc-1"))
            coEvery { saveCardSettingsUseCase.execute("SBER", BrokerName.TINVEST, "acc-1") } returns
                AppResult.Success(Unit)

            val vm = createVm()
            vm.openBrokerDialog("SBER")
            advanceUntilIdle()
            val before = vm.state.value.swipeResetTrigger

            vm.saveBrokerSettings()
            advanceUntilIdle()

            val s = vm.state.value
            assertFalse(s.showBrokerDialog)
            assertNull(s.dialogInstrumentTicker)
            assertEquals(!before, s.swipeResetTrigger)
            coVerify(exactly = 1) { saveCardSettingsUseCase.execute("SBER", BrokerName.TINVEST, "acc-1") }
        }

    @Test
    fun `saveBrokerSettings failure still closes dialog`() =
        runTest {
            stubExistingCard()
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
            coEvery { loadCardAccountsUseCase.execute(BrokerName.TINVEST, false, "acc-1") } returns
                AppResult.Success(CardAccountsResult(accounts = emptyList(), selectedAccountId = "acc-1"))
            coEvery { saveCardSettingsUseCase.execute("SBER", BrokerName.TINVEST, "acc-1") } returns
                AppResult.Failure(AppError.Network("err", null))

            val vm = createVm()
            vm.openBrokerDialog("SBER")
            advanceUntilIdle()
            val before = vm.state.value.swipeResetTrigger

            vm.saveBrokerSettings()
            advanceUntilIdle()

            val s = vm.state.value
            assertFalse(s.showBrokerDialog)
            assertNull(s.dialogInstrumentTicker)
            assertEquals(!before, s.swipeResetTrigger)
        }
}
