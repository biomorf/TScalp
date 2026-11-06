package com.gitlab.biomorf.tscalp.presentation.screens.settings

import com.gitlab.biomorf.tscalp.domain.api.DebugFlagsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.usecases.DebugFlag
import com.gitlab.biomorf.tscalp.domain.usecases.ToggleDebugFlagUseCase
import com.gitlab.biomorf.tscalp.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InfoSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val debugFlagsRepository = mockk<DebugFlagsRepository>(relaxed = true)
    private val toggleDebugFlagUseCase = mockk<ToggleDebugFlagUseCase>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private fun createVm(): InfoSettingsViewModel {
        every { debugFlagsRepository.disableGrpc } returns false
        every { debugFlagsRepository.disableWs } returns false
        every { debugFlagsRepository.forceRest } returns false
        return InfoSettingsViewModel(debugFlagsRepository, toggleDebugFlagUseCase, logger)
    }

    @Test
    fun `init reads all three flags from DebugFlagsRepository`() =
        runTest {
            every { debugFlagsRepository.disableGrpc } returns true
            every { debugFlagsRepository.disableWs } returns true
            every { debugFlagsRepository.forceRest } returns true

            val vm = InfoSettingsViewModel(debugFlagsRepository, toggleDebugFlagUseCase, logger)

            assertTrue(vm.uiState.value.disableGrpc)
            assertTrue(vm.uiState.value.disableWs)
            assertTrue(vm.uiState.value.forceRest)
        }

    @Test
    fun `toggleGrpc delegates to usecase and updates state`() =
        runTest {
            val vm = createVm()
            every { debugFlagsRepository.disableGrpc } returns true

            vm.toggleGrpc(true)

            assertTrue(vm.uiState.value.disableGrpc)
            verify(exactly = 1) { toggleDebugFlagUseCase.execute(DebugFlag.GRPC, true) }
        }

    @Test
    fun `toggleWs delegates to usecase and updates state`() =
        runTest {
            val vm = createVm()
            every { debugFlagsRepository.disableWs } returns true

            vm.toggleWs(true)

            assertTrue(vm.uiState.value.disableWs)
            assertFalse(vm.uiState.value.disableGrpc)
            verify(exactly = 1) { toggleDebugFlagUseCase.execute(DebugFlag.WS, true) }
        }

    @Test
    fun `toggleRest delegates to usecase and updates state`() =
        runTest {
            val vm = createVm()
            every { debugFlagsRepository.forceRest } returns true

            vm.toggleRest(true)

            assertTrue(vm.uiState.value.forceRest)
            assertFalse(vm.uiState.value.disableWs)
            verify(exactly = 1) { toggleDebugFlagUseCase.execute(DebugFlag.REST, true) }
        }
}
