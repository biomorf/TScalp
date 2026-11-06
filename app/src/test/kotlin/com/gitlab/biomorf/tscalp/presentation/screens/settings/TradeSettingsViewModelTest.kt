package com.gitlab.biomorf.tscalp.presentation.screens.settings

import com.gitlab.biomorf.tscalp.domain.api.repository.TradingPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TradeSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tradingPreferencesRepository = mockk<TradingPreferencesRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    @Test
    fun `init subscribes to confirmOrdersEnabledFlow with true`() =
        runTest {
            every { tradingPreferencesRepository.confirmOrdersEnabledFlow() } returns flowOf(true)

            val vm = TradeSettingsViewModel(tradingPreferencesRepository, logger)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isConfirmOrdersEnabled)
        }

    @Test
    fun `init subscribes to confirmOrdersEnabledFlow with false`() =
        runTest {
            every { tradingPreferencesRepository.confirmOrdersEnabledFlow() } returns flowOf(false)

            val vm = TradeSettingsViewModel(tradingPreferencesRepository, logger)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isConfirmOrdersEnabled)
        }

    @Test
    fun `setConfirmOrdersEnabled persists and updates state`() =
        runTest {
            every { tradingPreferencesRepository.confirmOrdersEnabledFlow() } returns flowOf(true)
            val vm = TradeSettingsViewModel(tradingPreferencesRepository, logger)
            advanceUntilIdle()
            coEvery { tradingPreferencesRepository.setConfirmOrdersEnabled(false) } returns Unit

            vm.setConfirmOrdersEnabled(false)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isConfirmOrdersEnabled)
            coVerify(exactly = 1) { tradingPreferencesRepository.setConfirmOrdersEnabled(false) }
        }
}
