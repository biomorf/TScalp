package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.usecases.RestoredFormState
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderInputViewModelTest {
    private val mediator = io.mockk.mockk<OrderFormMediator>(relaxed = true)

    private fun viewModel() = OrderInputViewModel(mediator)

    @Test
    fun `onQuantityChanged filters non-digit`() {
        val vm = viewModel()
        vm.onQuantityChanged("12abc34")
        assertEquals("1234", vm.state.value.quantity)
    }

    @Test
    fun `onPairedMultiplierChanged filters non-digit`() {
        val vm = viewModel()
        vm.onPairedMultiplierChanged("5x2")
        assertEquals("52", vm.state.value.pairedMultiplier)
    }

    @Test
    fun `setPairTradingEnabled true sets flag, keeps multiplier`() {
        val vm = viewModel()
        vm.onPairedMultiplierChanged("20")
        vm.setPairTradingEnabled(true)
        val s = vm.state.value
        assertEquals(true, s.pairTradingEnabled)
        assertEquals("20", s.pairedMultiplier)
    }

    @Test
    fun `setPairTradingEnabled false clears flag and multiplier`() {
        val vm = viewModel()
        vm.onPairedMultiplierChanged("20")
        vm.setPairTradingEnabled(false)
        val s = vm.state.value
        assertEquals(false, s.pairTradingEnabled)
        assertEquals("10", s.pairedMultiplier)
    }

    @Test
    fun `onOrderTypeChanged Market clears limitPrice`() {
        val vm = viewModel()
        vm.onLimitPriceChanged("150.5")
        vm.onOrderTypeChanged(OrderTypeSelection.Market)
        assertEquals("", vm.state.value.limitPrice)
    }

    @Test
    fun `onOrderTypeChanged Limit keeps limitPrice`() {
        val vm = viewModel()
        vm.onLimitPriceChanged("150.5")
        vm.onOrderTypeChanged(OrderTypeSelection.Limit)
        assertEquals("150.5", vm.state.value.limitPrice)
        assertEquals(OrderTypeSelection.Limit, vm.state.value.orderType)
    }

    @Test
    fun `onLimitPriceChanged filters digit and dot`() {
        val vm = viewModel()
        vm.onLimitPriceChanged("15a.5b")
        assertEquals("15.5", vm.state.value.limitPrice)
    }

    @Test
    fun `onStopPriceChanged filters digit and dot`() {
        val vm = viewModel()
        vm.onStopPriceChanged("2x0.0y5")
        assertEquals("20.05", vm.state.value.stopPrice)
    }

    @Test
    fun `restore maps RestoredFormState to input fields`() {
        val vm = viewModel()
        vm.restore(
            RestoredFormState(
                quantity = 3L,
                pairedMultiplier = 5L,
                orderType = OrderTypeSelection.Limit,
                pairTradingEnabled = true,
            ),
        )
        val s = vm.state.value
        assertEquals("3", s.quantity)
        assertEquals("5", s.pairedMultiplier)
        assertEquals(OrderTypeSelection.Limit, s.orderType)
        assertEquals(true, s.pairTradingEnabled)
    }
}
