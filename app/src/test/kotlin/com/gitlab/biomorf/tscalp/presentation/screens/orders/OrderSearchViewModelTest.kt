package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.SearchCacheRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.usecases.SearchInstrumentsUseCase
import com.gitlab.biomorf.tscalp.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderSearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val searchInstrumentsUseCase = mockk<SearchInstrumentsUseCase>(relaxed = true)
    private val searchCacheRepository = mockk<SearchCacheRepository>(relaxed = true)
    private val brokerRegistry = mockk<BrokerRegistry>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private fun createVm() = OrderSearchViewModel(searchInstrumentsUseCase, searchCacheRepository, brokerRegistry, logger)

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    @Test
    fun `query shorter than 2 clears results without usecase call`() =
        runTest {
            val vm = createVm()

            vm.onSearchQueryChanged("S")
            advanceUntilIdle()

            assertTrue(
                vm.state.value.searchResults
                    .isEmpty(),
            )
            assertFalse(vm.state.value.isSearching)
            coVerify(exactly = 0) { searchInstrumentsUseCase.execute(any(), any()) }
        }

    @Test
    fun `query success updates results`() =
        runTest {
            coEvery {
                searchInstrumentsUseCase.execute(BrokerName.TINVEST, "SBER")
            } returns AppResult.Success(listOf(sber))
            val vm = createVm()

            vm.onSearchQueryChanged("SBER")
            advanceUntilIdle()

            assertEquals(listOf(sber), vm.state.value.searchResults)
            assertFalse(vm.state.value.isSearching)
            coVerify(exactly = 1) {
                searchInstrumentsUseCase.execute(BrokerName.TINVEST, "SBER")
            }
        }

    @Test
    fun `query failure sets statusMessage and isError`() =
        runTest {
            coEvery {
                searchInstrumentsUseCase.execute(BrokerName.TINVEST, "SBER")
            } returns AppResult.Failure(AppError.Network("down", null))
            val vm = createVm()

            vm.onSearchQueryChanged("SBER")
            advanceUntilIdle()

            assertTrue(
                vm.state.value.searchResults
                    .isEmpty(),
            )
            assertFalse(vm.state.value.isSearching)
            assertNotNull(vm.state.value.statusMessage)
            assertTrue(vm.state.value.isError)
        }

    @Test
    fun `onSearchResultSelected sets displayName and clears results`() =
        runTest {
            val vm = createVm()

            vm.onSearchResultSelected(sber)

            assertEquals("SBER - Сбербанк", vm.state.value.searchQuery)
            assertTrue(
                vm.state.value.searchResults
                    .isEmpty(),
            )
            assertFalse(vm.state.value.isSearchActive)
        }

    @Test
    fun `clearSearchQuery resets search fields`() =
        runTest {
            val vm = createVm()
            vm.onSearchResultSelected(sber)

            vm.clearSearchQuery()

            assertEquals("", vm.state.value.searchQuery)
            assertTrue(
                vm.state.value.searchResults
                    .isEmpty(),
            )
            assertFalse(vm.state.value.isSearchActive)
        }

    @Test
    fun `saveSearchBrokerSettings updates broker and closes dialog`() =
        runTest {
            val vm = createVm()
            vm.openSearchBrokerSettings()

            vm.saveSearchBrokerSettings(BrokerName.BCS)

            assertEquals(BrokerName.BCS, vm.state.value.selectedSearchBroker)
            assertFalse(vm.state.value.showSearchBrokerDialog)
        }
}
