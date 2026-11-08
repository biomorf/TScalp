package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.usecases.CardAccountsResult
import com.gitlab.biomorf.tscalp.domain.usecases.LoadCardAccountsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.OpenCardDialogOutcome
import com.gitlab.biomorf.tscalp.domain.usecases.OpenCardDialogResult
import com.gitlab.biomorf.tscalp.domain.usecases.OpenCardDialogUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SaveCardSettingsUseCase
import com.gitlab.biomorf.tscalp.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDialogsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val loadCardAccountsUseCase = mockk<LoadCardAccountsUseCase>(relaxed = true)
    private val openCardDialogUseCase = mockk<OpenCardDialogUseCase>(relaxed = true)
    private val saveCardSettingsUseCase = mockk<SaveCardSettingsUseCase>(relaxed = true)
    private val sandboxPreferencesRepository = mockk<SandboxPreferencesRepository>(relaxed = true)
    private val brokerRegistry = mockk<BrokerRegistry>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    private fun createVm() =
        OrderDialogsViewModel(
            loadCardAccountsUseCase,
            openCardDialogUseCase,
            saveCardSettingsUseCase,
            sandboxPreferencesRepository,
            brokerRegistry,
            logger,
        )

    @Test
    fun `openBrokerDialog success sets dialog state`() =
        runTest {
            coEvery { openCardDialogUseCase.execute(sber, null) } returns
                OpenCardDialogOutcome.Success(
                    OpenCardDialogResult(BrokerName.TINVEST, emptyList(), "acc-1", oldAccountId = "old-acc"),
                )

            val vm = createVm()
            vm.openBrokerDialog(sber)
            advanceUntilIdle()

            val s = vm.state.value
            assertTrue(s.showBrokerDialog)
            assertEquals(sber, s.dialogInstrument)
            assertEquals("old-acc", s.dialogOldAccountId)
            assertEquals(BrokerName.TINVEST, s.selectedBroker)
            assertEquals("acc-1", s.selectedAccountIdDialog)
            assertTrue(s.dialogAccounts.isEmpty())
            coVerify(exactly = 1) { openCardDialogUseCase.execute(sber, null) }
        }

    @Test
    fun `openBrokerDialog success with existing card uses oldAccountId`() =
        runTest {
            coEvery { openCardDialogUseCase.execute(sber, "old-A") } returns
                OpenCardDialogOutcome.Success(
                    OpenCardDialogResult(BrokerName.TINVEST, emptyList(), "old-A", oldAccountId = "old-A"),
                )

            val vm = createVm()
            vm.openBrokerDialog(sber, currentAccountId = "old-A")
            advanceUntilIdle()

            assertEquals("old-A", vm.state.value.dialogOldAccountId)
        }

    @Test
    fun `openBrokerDialog failure clears selected account and oldAccountId`() =
        runTest {
            coEvery { openCardDialogUseCase.execute(sber, null) } returns
                OpenCardDialogOutcome.Failure(BrokerName.TINVEST, AppError.Network("err", null))

            val vm = createVm()
            vm.openBrokerDialog(sber)
            advanceUntilIdle()

            val s = vm.state.value
            assertTrue(s.showBrokerDialog)
            assertEquals(sber, s.dialogInstrument)
            assertNull(s.dialogOldAccountId)
            assertNull(s.selectedAccountIdDialog)
            assertTrue(s.dialogAccounts.isEmpty())
        }

    @Test
    fun `closeBrokerDialog resets flags and toggles swipe`() =
        runTest {
            coEvery { openCardDialogUseCase.execute(sber, null) } returns
                OpenCardDialogOutcome.Success(
                    OpenCardDialogResult(BrokerName.TINVEST, emptyList(), "acc-1", oldAccountId = null),
                )

            val vm = createVm()
            vm.openBrokerDialog(sber)
            advanceUntilIdle()
            val before = vm.state.value.swipeResetTrigger

            vm.closeBrokerDialog()

            val s = vm.state.value
            assertFalse(s.showBrokerDialog)
            assertNull(s.dialogInstrument)
            assertNull(s.dialogOldAccountId)
            assertEquals(!before, s.swipeResetTrigger)
        }

    @Test
    fun `onBrokerSelected reloads accounts with null saved id`() =
        runTest {
            coEvery { openCardDialogUseCase.execute(sber, null) } returns
                OpenCardDialogOutcome.Success(
                    OpenCardDialogResult(BrokerName.TINVEST, emptyList(), "acc-1", oldAccountId = null),
                )
            coEvery { loadCardAccountsUseCase.execute(BrokerName.BCS, false, null) } returns
                AppResult.Success(CardAccountsResult(accounts = emptyList(), selectedAccountId = "bcs-acc"))

            val vm = createVm()
            vm.openBrokerDialog(sber)
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
            coEvery { openCardDialogUseCase.execute(sber, null) } returns
                OpenCardDialogOutcome.Success(
                    OpenCardDialogResult(BrokerName.TINVEST, emptyList(), "acc-1", oldAccountId = null),
                )
            coEvery { saveCardSettingsUseCase.execute(sber, null, "acc-1") } returns
                AppResult.Success(Unit)

            val vm = createVm()
            vm.openBrokerDialog(sber)
            advanceUntilIdle()
            val before = vm.state.value.swipeResetTrigger

            vm.saveBrokerSettings()
            advanceUntilIdle()

            val s = vm.state.value
            assertFalse(s.showBrokerDialog)
            assertNull(s.dialogInstrument)
            assertNull(s.dialogOldAccountId)
            assertEquals(!before, s.swipeResetTrigger)
            coVerify(exactly = 1) { saveCardSettingsUseCase.execute(sber, null, "acc-1") }
        }

    @Test
    fun `saveBrokerSettings passes oldAccountId to usecase`() =
        runTest {
            coEvery { openCardDialogUseCase.execute(sber, "old-A") } returns
                OpenCardDialogOutcome.Success(
                    OpenCardDialogResult(BrokerName.TINVEST, emptyList(), "old-A", oldAccountId = "old-A"),
                )
            coEvery { saveCardSettingsUseCase.execute(sber, "old-A", "new-B") } returns
                AppResult.Success(Unit)

            val vm = createVm()
            vm.openBrokerDialog(sber, currentAccountId = "old-A")
            advanceUntilIdle()
            vm.onAccountSelectedDialog("new-B")

            vm.saveBrokerSettings()
            advanceUntilIdle()

            coVerify(exactly = 1) { saveCardSettingsUseCase.execute(sber, "old-A", "new-B") }
        }

    @Test
    fun `saveBrokerSettings failure still closes dialog`() =
        runTest {
            coEvery { openCardDialogUseCase.execute(sber, null) } returns
                OpenCardDialogOutcome.Success(
                    OpenCardDialogResult(BrokerName.TINVEST, emptyList(), "acc-1", oldAccountId = null),
                )
            coEvery { saveCardSettingsUseCase.execute(sber, null, "acc-1") } returns
                AppResult.Failure(AppError.Network("err", null))

            val vm = createVm()
            vm.openBrokerDialog(sber)
            advanceUntilIdle()
            val before = vm.state.value.swipeResetTrigger

            vm.saveBrokerSettings()
            advanceUntilIdle()

            val s = vm.state.value
            assertFalse(s.showBrokerDialog)
            assertNull(s.dialogInstrument)
            assertEquals(!before, s.swipeResetTrigger)
        }
}
