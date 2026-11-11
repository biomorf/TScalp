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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

            vm.onQueryChanged(SearchSlot.PRIMARY, "S")
            advanceUntilIdle()

            assertTrue(
                vm.state.value.primary.results
                    .isEmpty(),
            )
            assertFalse(vm.state.value.primary.isSearching)
            coVerify(exactly = 0) { searchInstrumentsUseCase.execute(any(), any()) }
        }

    @Test
    fun `query success updates results`() =
        runTest {
            coEvery {
                searchInstrumentsUseCase.execute(BrokerName.TINVEST, "SBER")
            } returns AppResult.Success(listOf(sber))
            val vm = createVm()

            vm.onQueryChanged(SearchSlot.PRIMARY, "SBER")
            advanceUntilIdle()

            assertEquals(listOf(sber), vm.state.value.primary.results)
            assertFalse(vm.state.value.primary.isSearching)
            coVerify(exactly = 1) {
                searchInstrumentsUseCase.execute(BrokerName.TINVEST, "SBER")
            }
        }

    @Test
    fun `query failure emits StatusEvent`() =
        runTest {
            coEvery {
                searchInstrumentsUseCase.execute(BrokerName.TINVEST, "SBER")
            } returns AppResult.Failure(AppError.Network("down", null))
            val vm = createVm()

            vm.onQueryChanged(SearchSlot.PRIMARY, "SBER")
            advanceUntilIdle()

            assertTrue(
                vm.state.value.primary.results
                    .isEmpty(),
            )
            assertFalse(vm.state.value.primary.isSearching)

            val event =
                vm.status
                    .first()
            assertTrue(event.isError)
            assertTrue(event.message.contains("Ошибка поиска"))
            assertTrue(event.message.contains("down"))
        }

    @Test
    fun `onSearchResultSelected sets displayName and clears results`() =
        runTest {
            val vm = createVm()

            vm.onResultSelected(SearchSlot.PRIMARY, sber)

            assertEquals("SBER - Сбербанк", vm.state.value.primary.query)
            assertTrue(
                vm.state.value.primary.results
                    .isEmpty(),
            )
            assertFalse(vm.state.value.isSearchActive)
        }

    @Test
    fun `clearSearchQuery resets search fields`() =
        runTest {
            val vm = createVm()
            vm.onResultSelected(SearchSlot.PRIMARY, sber)

            vm.clearQuery(SearchSlot.PRIMARY)

            assertEquals("", vm.state.value.primary.query)
            assertTrue(
                vm.state.value.primary.results
                    .isEmpty(),
            )
            assertFalse(vm.state.value.isSearchActive)
        }

    @Test
    fun `saveBroker updates broker and closes dialog`() =
        runTest {
            val vm = createVm()
            vm.openBrokerDialog(SearchSlot.PRIMARY)

            vm.saveBroker(SearchSlot.PRIMARY, BrokerName.BCS)

            assertEquals(BrokerName.BCS, vm.state.value.primary.broker)
            assertFalse(vm.state.value.primary.showBrokerDialog)
        }

    @Test
    fun `pair onQueryChanged updates results without touching primary`() =
        runTest {
            coEvery {
                searchInstrumentsUseCase.execute(BrokerName.TINVEST, "SBER")
            } returns AppResult.Success(listOf(sber))
            val vm = createVm()

            vm.onQueryChanged(SearchSlot.PAIR, "SBER")
            advanceUntilIdle()

            assertEquals(listOf(sber), vm.state.value.pair.results)
            assertFalse(vm.state.value.pair.isSearching)
            assertTrue(
                vm.state.value.primary.results
                    .isEmpty(),
            )
            assertFalse(vm.state.value.primary.isSearching)
        }

    @Test
    fun `pair onResultSelected sets displayName without touching isSearchActive`() =
        runTest {
            val vm = createVm()
            vm.setSearchActive(true)

            vm.onResultSelected(SearchSlot.PAIR, sber)

            assertEquals("SBER - Сбербанк", vm.state.value.pair.query)
            assertTrue(
                vm.state.value.pair.results
                    .isEmpty(),
            )
            // isSearchActive — primary-only; pair-слот его не трогает
            assertTrue(vm.state.value.isSearchActive)
        }

    @Test
    fun `pair clearQuery resets pair fields only`() =
        runTest {
            val vm = createVm()
            vm.onResultSelected(SearchSlot.PRIMARY, sber)
            vm.onResultSelected(SearchSlot.PAIR, sber)

            vm.clearQuery(SearchSlot.PAIR)

            assertEquals("", vm.state.value.pair.query)
            assertTrue(
                vm.state.value.pair.results
                    .isEmpty(),
            )
            // primary не задет
            assertEquals("SBER - Сбербанк", vm.state.value.primary.query)
        }

    @Test
    fun `pair saveBroker updates broker and closes dialog`() =
        runTest {
            val vm = createVm()
            vm.openBrokerDialog(SearchSlot.PAIR)

            vm.saveBroker(SearchSlot.PAIR, BrokerName.BCS)

            assertEquals(BrokerName.BCS, vm.state.value.pair.broker)
            assertFalse(vm.state.value.pair.showBrokerDialog)
            // primary не задет
            assertEquals(BrokerName.TINVEST, vm.state.value.primary.broker)
            assertFalse(vm.state.value.primary.showBrokerDialog)
        }

    @Test
    fun `saveBroker updates broker used by execute`() =
        runTest {
            coEvery {
                searchInstrumentsUseCase.execute(BrokerName.BCS, "SBER")
            } returns AppResult.Success(listOf(sber))
            val vm = createVm()

            vm.saveBroker(SearchSlot.PRIMARY, BrokerName.BCS)
            vm.onQueryChanged(SearchSlot.PRIMARY, "SBER")
            advanceUntilIdle()

            coVerify(exactly = 1) {
                searchInstrumentsUseCase.execute(BrokerName.BCS, "SBER")
            }
        }
}
