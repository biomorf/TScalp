package com.gitlab.biomorf.tscalp.presentation.screens.settings

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.usecases.DisconnectBrokerUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.InitializeBrokerUseCase
import com.gitlab.biomorf.tscalp.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BcsSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val initializeBrokerUseCase = mockk<InitializeBrokerUseCase>(relaxed = true)
    private val disconnectBrokerUseCase = mockk<DisconnectBrokerUseCase>(relaxed = true)
    private val brokerCredentialsRepository = mockk<BrokerCredentialsRepository>(relaxed = true)
    private val brokerRegistry = mockk<BrokerRegistry>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private fun createVm(): BcsSettingsViewModel {
        every { brokerCredentialsRepository.hasSavedTokenFlow(BrokerName.BCS) } returns flowOf(false)
        coEvery { brokerCredentialsRepository.loadBrokerCredentials(BrokerName.BCS) } returns null
        return BcsSettingsViewModel(
            initializeBrokerUseCase,
            disconnectBrokerUseCase,
            brokerCredentialsRepository,
            brokerRegistry,
            logger,
        )
    }

    @Test
    fun `init subscribes to hasSavedTokenFlow and loads saved credentials`() =
        runTest {
            every { brokerCredentialsRepository.hasSavedTokenFlow(BrokerName.BCS) } returns flowOf(true)
            coEvery { brokerCredentialsRepository.loadBrokerCredentials(BrokerName.BCS) } returns
                Pair("saved-refresh", false)

            val vm =
                BcsSettingsViewModel(
                    initializeBrokerUseCase,
                    disconnectBrokerUseCase,
                    brokerCredentialsRepository,
                    brokerRegistry,
                    logger,
                )
            advanceUntilIdle()

            assertTrue(vm.uiState.value.connected)
            assertEquals("saved-refresh", vm.uiState.value.refreshToken)
            assertFalse(vm.uiState.value.isWriteMode)
        }

    @Test
    fun `connect success sets status message`() =
        runTest {
            val vm = createVm()
            coEvery { initializeBrokerUseCase.execute(any()) } returns AppResult.Success(true)

            vm.connect("refresh", isWriteMode = true)
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

            vm.connect("refresh", isWriteMode = false)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isError)
            assertTrue(
                vm.uiState.value.statusMessage
                    ?.contains("net down") == true,
            )
        }

    @Test
    fun `disconnect clears refresh token and sets status`() =
        runTest {
            val vm = createVm()
            coEvery { disconnectBrokerUseCase.execute(BrokerName.BCS) } returns AppResult.Success(Unit)

            vm.disconnect()
            advanceUntilIdle()

            assertEquals("", vm.uiState.value.refreshToken)
            assertTrue(
                vm.uiState.value.statusMessage
                    ?.contains("разорвано") == true,
            )
            assertFalse(vm.uiState.value.isError)
        }

    @Test
    fun `clearStatus resets status message and error flag`() =
        runTest {
            val vm = createVm()
            coEvery { initializeBrokerUseCase.execute(any()) } returns
                AppResult.Failure(AppError.Network("net down", null))
            vm.connect("refresh", isWriteMode = true)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isError)

            vm.clearStatus()

            assertEquals(null, vm.uiState.value.statusMessage)
            assertFalse(vm.uiState.value.isError)
        }
}
