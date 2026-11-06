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
class FinamSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val initializeBrokerUseCase = mockk<InitializeBrokerUseCase>(relaxed = true)
    private val disconnectBrokerUseCase = mockk<DisconnectBrokerUseCase>(relaxed = true)
    private val brokerCredentialsRepository = mockk<BrokerCredentialsRepository>(relaxed = true)
    private val brokerRegistry = mockk<BrokerRegistry>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private fun createVm(): FinamSettingsViewModel {
        every { brokerCredentialsRepository.hasSavedTokenFlow(BrokerName.FINAM) } returns flowOf(false)
        coEvery { brokerCredentialsRepository.getToken(BrokerName.FINAM) } returns null
        return FinamSettingsViewModel(
            initializeBrokerUseCase,
            disconnectBrokerUseCase,
            brokerCredentialsRepository,
            brokerRegistry,
            logger,
        )
    }

    @Test
    fun `init subscribes to hasSavedTokenFlow and loads saved token`() =
        runTest {
            every { brokerCredentialsRepository.hasSavedTokenFlow(BrokerName.FINAM) } returns flowOf(true)
            coEvery { brokerCredentialsRepository.getToken(BrokerName.FINAM) } returns "saved-token"

            val vm =
                FinamSettingsViewModel(
                    initializeBrokerUseCase,
                    disconnectBrokerUseCase,
                    brokerCredentialsRepository,
                    brokerRegistry,
                    logger,
                )
            advanceUntilIdle()

            assertTrue(vm.uiState.value.connected)
            assertEquals("saved-token", vm.uiState.value.token)
        }

    @Test
    fun `connect success sets status message`() =
        runTest {
            val vm = createVm()
            coEvery { initializeBrokerUseCase.execute(any()) } returns AppResult.Success(true)

            vm.connect("finam-token")
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

            vm.connect("finam-token")
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isError)
            assertTrue(
                vm.uiState.value.statusMessage
                    ?.contains("net down") == true,
            )
        }

    @Test
    fun `disconnect clears token and sets status`() =
        runTest {
            val vm = createVm()
            coEvery { disconnectBrokerUseCase.execute(BrokerName.FINAM) } returns AppResult.Success(Unit)

            vm.disconnect()
            advanceUntilIdle()

            assertEquals("", vm.uiState.value.token)
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
            vm.connect("finam-token")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isError)

            vm.clearStatus()

            assertEquals(null, vm.uiState.value.statusMessage)
            assertFalse(vm.uiState.value.isError)
        }
}
