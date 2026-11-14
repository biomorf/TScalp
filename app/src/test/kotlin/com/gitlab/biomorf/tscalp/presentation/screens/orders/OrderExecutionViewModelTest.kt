package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.api.repository.TradingPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType
import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderOutcome
import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderRequest
import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PrimaryOutcome
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderExecutionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val placeOrderUseCase = mockk<PlaceOrderUseCase>(relaxed = true)
    private val tradingPreferencesRepository = mockk<TradingPreferencesRepository>(relaxed = true)
    private val mediator = mockk<OrderFormMediator>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private val sber: Instrument =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "\u0421\u0431\u0435\u0440\u0431\u0430\u043d\u043a",
            isin = "RU0009029540",
        )

    private val request =
        PlaceOrderRequest(
            instrument = sber,
            selectedAccountId = "acc-1",
            quantity = 10L,
            orderType = OrderTypeSelection.Market,
            limitPrice = "",
            stopPrice = "",
            expirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
            pairedInstrument = null,
            pairedAccountId = null,
            pairedMultiplier = 10L,
        )

    private fun stubDefaults() {
        every { tradingPreferencesRepository.confirmOrdersEnabledFlow() } returns flowOf(true)
    }

    private fun viewModel(): OrderExecutionViewModel {
        stubDefaults()
        return OrderExecutionViewModel(placeOrderUseCase, tradingPreferencesRepository, mediator, logger)
    }

    @Test
    fun `init subscribes confirmOrdersEnabledFlow`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()
            assertTrue(vm.state.value.confirmOrdersEnabled)
        }

    @Test
    fun `onBuyClick opens dialog with BUY direction`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onBuyClick()
            assertEquals(true, vm.state.value.showConfirmDialog)
            assertEquals(OrderDirection.BUY, vm.state.value.pendingDirection)
        }

    @Test
    fun `onSellClick opens dialog with SELL direction`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onSellClick()
            assertEquals(true, vm.state.value.showConfirmDialog)
            assertEquals(OrderDirection.SELL, vm.state.value.pendingDirection)
        }

    @Test
    fun `onDismissConfirmDialog clears flag and direction`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onBuyClick()
            vm.onDismissConfirmDialog()
            assertEquals(false, vm.state.value.showConfirmDialog)
            assertNull(vm.state.value.pendingDirection)
        }

    @Test
    fun `onConfirmOrder success emits success status`() =
        runTest {
            coEvery { placeOrderUseCase.execute(any(), any()) } returns
                PlaceOrderOutcome.Success(
                    primary = PrimaryOutcome.Regular(orderId = "ord-1", executedLots = 10L, totalLots = 10L),
                    paired = null,
                    freeBalance = 5000.0,
                )
            val vm = viewModel()
            advanceUntilIdle()
            vm.onBuyClick()
            vm.onConfirmOrder(request)
            advanceUntilIdle()
            val s = vm.state.value
            assertEquals(false, s.showConfirmDialog)
            assertNull(s.pendingDirection)
            assertEquals(false, s.isLoading)
            coVerify { placeOrderUseCase.execute(request, OrderDirection.BUY) }
        }

    @Test
    fun `onConfirmOrder insufficient funds emits error status`() =
        runTest {
            coEvery { placeOrderUseCase.execute(any(), any()) } returns
                PlaceOrderOutcome.InsufficientFunds(balance = 100.0, required = 1500.0)
            val vm = viewModel()
            advanceUntilIdle()
            vm.onBuyClick()
            vm.onConfirmOrder(request)
            advanceUntilIdle()
            assertEquals(false, vm.state.value.isLoading)
        }

    @Test
    fun `onConfirmOrder error emits error status`() =
        runTest {
            coEvery { placeOrderUseCase.execute(any(), any()) } returns
                PlaceOrderOutcome.Error(message = "boom", cause = null)
            val vm = viewModel()
            advanceUntilIdle()
            vm.onBuyClick()
            vm.onConfirmOrder(request)
            advanceUntilIdle()
            assertEquals(false, vm.state.value.isLoading)
        }

    @Test
    fun `onConfirmOrder invalid request emits error status`() =
        runTest {
            coEvery { placeOrderUseCase.execute(any(), any()) } returns
                PlaceOrderOutcome.InvalidRequest(reason = "stopPrice required")
            val vm = viewModel()
            advanceUntilIdle()
            vm.onBuyClick()
            vm.onConfirmOrder(request)
            advanceUntilIdle()
            assertEquals(false, vm.state.value.isLoading)
        }

    @Test
    fun `onConfirmOrder without pendingDirection is no-op`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onConfirmOrder(request)
            advanceUntilIdle()
            coVerify(exactly = 0) { placeOrderUseCase.execute(any(), any()) }
        }
}
