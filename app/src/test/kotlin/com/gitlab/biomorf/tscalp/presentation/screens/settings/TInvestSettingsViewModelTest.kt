package com.gitlab.biomorf.tscalp.presentation.screens.settings

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccountType
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.usecases.CloseResult
import com.gitlab.biomorf.tscalp.domain.usecases.CloseSandboxAccountUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.DisconnectBrokerUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.InitializeBrokerUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.LoadBrokerAccountsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.OpenResult
import com.gitlab.biomorf.tscalp.domain.usecases.OpenSandboxAccountUseCase
import com.gitlab.biomorf.tscalp.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TInvestSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val initializeBrokerUseCase = mockk<InitializeBrokerUseCase>(relaxed = true)
    private val disconnectBrokerUseCase = mockk<DisconnectBrokerUseCase>(relaxed = true)
    private val loadBrokerAccountsUseCase = mockk<LoadBrokerAccountsUseCase>(relaxed = true)
    private val openSandboxAccountUseCase = mockk<OpenSandboxAccountUseCase>(relaxed = true)
    private val closeSandboxAccountUseCase = mockk<CloseSandboxAccountUseCase>(relaxed = true)
    private val accountPreferencesRepository = mockk<AccountPreferencesRepository>(relaxed = true)
    private val brokerCredentialsRepository = mockk<BrokerCredentialsRepository>(relaxed = true)
    private val sandboxPreferencesRepository = mockk<SandboxPreferencesRepository>(relaxed = true)
    private val brokerRegistry = mockk<BrokerRegistry>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private val acc1 = BrokerAccount(id = "acc-1", name = "Account 1", type = BrokerAccountType.BROKER)

    private fun createVm(): TInvestSettingsViewModel {
        every { brokerRegistry.anyInitialized } returns MutableStateFlow(false).asStateFlow()
        every { brokerCredentialsRepository.hasSavedTokenFlow(BrokerName.TINVEST) } returns flowOf(false)
        coEvery { sandboxPreferencesRepository.isSandboxMode() } returns true
        coEvery { brokerCredentialsRepository.loadBrokerCredentials(BrokerName.TINVEST) } returns null
        return TInvestSettingsViewModel(
            initializeBrokerUseCase,
            disconnectBrokerUseCase,
            loadBrokerAccountsUseCase,
            openSandboxAccountUseCase,
            closeSandboxAccountUseCase,
            accountPreferencesRepository,
            brokerCredentialsRepository,
            sandboxPreferencesRepository,
            brokerRegistry,
            logger,
        )
    }

    @Test
    fun `init subscribes to flows, sandbox mode initialised`() =
        runTest {
            every { brokerRegistry.anyInitialized } returns MutableStateFlow(true).asStateFlow()
            every { brokerCredentialsRepository.hasSavedTokenFlow(BrokerName.TINVEST) } returns flowOf(true)
            coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
            coEvery { brokerCredentialsRepository.loadBrokerCredentials(BrokerName.TINVEST) } returns null

            val vm =
                TInvestSettingsViewModel(
                    initializeBrokerUseCase,
                    disconnectBrokerUseCase,
                    loadBrokerAccountsUseCase,
                    openSandboxAccountUseCase,
                    closeSandboxAccountUseCase,
                    accountPreferencesRepository,
                    brokerCredentialsRepository,
                    sandboxPreferencesRepository,
                    brokerRegistry,
                    logger,
                )
            advanceUntilIdle()

            assertTrue(vm.uiState.value.anyApiInitialized)
            assertTrue(vm.uiState.value.connected)
            assertFalse(vm.uiState.value.sandboxMode)
            assertTrue(vm.uiState.value.isConnected)
        }

    @Test
    fun `connect success sets status message`() =
        runTest {
            val vm = createVm()
            coEvery { initializeBrokerUseCase.execute(any()) } returns AppResult.Success(true)

            vm.connect("token", sandboxMode = true)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isLoading)
            assertTrue(
                vm.uiState.value.statusMessage
                    ?.contains("Подключено") == true,
            )
            assertFalse(vm.uiState.value.isError)
        }

    @Test
    fun `connect failure sets error status`() =
        runTest {
            val vm = createVm()
            coEvery { initializeBrokerUseCase.execute(any()) } returns
                AppResult.Failure(AppError.Network("net down", null))

            vm.connect("token", sandboxMode = true)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isError)
            assertTrue(
                vm.uiState.value.statusMessage
                    ?.contains("net down") == true,
            )
        }

    @Test
    fun `disconnect clears accounts on success`() =
        runTest {
            val vm = createVm()
            coEvery { disconnectBrokerUseCase.execute(BrokerName.TINVEST) } returns AppResult.Success(Unit)

            vm.disconnect()
            advanceUntilIdle()

            assertTrue(
                vm.uiState.value.availableAccounts
                    .isEmpty(),
            )
            assertEquals("", vm.uiState.value.defaultAccountId)
            assertFalse(vm.uiState.value.isError)
        }

    @Test
    fun `openSandboxAccount updates accounts and default`() =
        runTest {
            val vm = createVm()
            coEvery { openSandboxAccountUseCase.execute(BrokerName.TINVEST, true) } returns
                AppResult.Success(OpenResult(accountId = "new-acc", accounts = listOf(acc1)))

            vm.openSandboxAccount()
            advanceUntilIdle()

            assertEquals("new-acc", vm.uiState.value.defaultAccountId)
            assertEquals(listOf(acc1), vm.uiState.value.availableAccounts)
            assertFalse(vm.uiState.value.isRefreshing)
            coVerify(exactly = 1) { openSandboxAccountUseCase.execute(BrokerName.TINVEST, true) }
        }

    @Test
    fun `closeSandboxAccount resets default, updates accounts`() =
        runTest {
            val vm = createVm()
            coEvery { closeSandboxAccountUseCase.execute(BrokerName.TINVEST, true, "acc-to-close") } returns
                AppResult.Success(CloseResult(accounts = listOf(acc1)))

            vm.closeSandboxAccount("acc-to-close")
            advanceUntilIdle()

            assertEquals("", vm.uiState.value.defaultAccountId)
            assertEquals(listOf(acc1), vm.uiState.value.availableAccounts)
            assertFalse(vm.uiState.value.isRefreshing)
        }
}
