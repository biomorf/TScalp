package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.usecases.CalculatePairExecUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.CalculateTradeDetailsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PairExecInput
import com.gitlab.biomorf.tscalp.domain.usecases.PairExecResult
import com.gitlab.biomorf.tscalp.domain.usecases.TradeDetails
import com.gitlab.biomorf.tscalp.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderTradeDetailsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val calculateTradeDetails = mockk<CalculateTradeDetailsUseCase>()
    private val calculatePairExecUseCase = mockk<CalculatePairExecUseCase>()

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "\u0421\u0431\u0435\u0440\u0431\u0430\u043d\u043a",
            isin = "RU0009029540",
        )

    private fun viewModel() = OrderTradeDetailsViewModel(calculateTradeDetails, calculatePairExecUseCase)

    private fun instrumentState() =
        OrderInstrumentState(
            selectedInstrument = sber,
            currentPrice = 150.0,
        )

    private fun inputState() =
        OrderInputState(
            quantity = "10",
            orderType = OrderTypeSelection.Market,
        )

    private fun mockDetails(details: TradeDetails) {
        every {
            calculateTradeDetails.calculate(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns details
    }

    @Test
    fun `updateTradeDetails sets executionPrice and overlays`() {
        mockDetails(
            TradeDetails(
                executionPrice = 150.0,
                costPoints = 1500.0,
                costDomesticCurrency = 1500.0,
            ),
        )

        val vm = viewModel()
        vm.updateTradeDetails(instrumentState(), inputState())

        val state = vm.state.value
        assertEquals(150.0, state.executionPrice, 0.001)
        assertTrue(state.costOverlay != null)
        assertNull(state.multiplierOverlay)
    }

    @Test
    fun `updateTradeDetails empty details leave overlays null`() {
        mockDetails(TradeDetails(executionPrice = 0.0))

        val vm = viewModel()
        vm.updateTradeDetails(instrumentState(), inputState())

        val state = vm.state.value
        assertEquals(0.0, state.executionPrice, 0.001)
        assertNull(state.costOverlay)
        assertNull(state.multiplierOverlay)
    }

    @Test
    fun `updatePairExecDetails pair enabled sets price and type`() {
        every { calculatePairExecUseCase.execute(any()) } returns
            PairExecResult(pairExecPrice = 160.0, pairedOrderType = OrderTypeSelection.Limit)

        val vm = viewModel()
        vm.updatePairExecDetails(
            instrumentState(),
            inputState().copy(pairTradingEnabled = true),
            OrderExecutionState(pendingDirection = OrderDirection.BUY),
        )

        val state = vm.state.value
        assertEquals(160.0, state.pairExecPrice, 0.001)
        assertEquals(OrderTypeSelection.Limit, state.pairedOrderType)
    }

    @Test
    fun `updatePairExecDetails pair disabled sets zero and null`() {
        every { calculatePairExecUseCase.execute(any()) } returns
            PairExecResult(pairExecPrice = 0.0, pairedOrderType = null)

        val vm = viewModel()
        vm.updatePairExecDetails(instrumentState(), inputState(), OrderExecutionState())

        val state = vm.state.value
        assertEquals(0.0, state.pairExecPrice, 0.001)
        assertNull(state.pairedOrderType)
    }

    @Test
    fun `updatePairExecDetails passes pendingDirection and state executionPrice`() {
        mockDetails(TradeDetails(executionPrice = 155.0))
        val captured = slot<PairExecInput>()
        every { calculatePairExecUseCase.execute(capture(captured)) } returns
            PairExecResult(pairExecPrice = 0.0, pairedOrderType = null)

        val vm = viewModel()
        vm.updateTradeDetails(instrumentState(), inputState())
        vm.updatePairExecDetails(
            instrumentState(),
            inputState().copy(pairTradingEnabled = true),
            OrderExecutionState(pendingDirection = OrderDirection.SELL),
        )

        assertEquals(OrderDirection.SELL, captured.captured.pendingDirection)
        assertEquals(155.0, captured.captured.executionPrice, 0.001)
        assertTrue(captured.captured.pairTradingEnabled)
        assertEquals(OrderTypeSelection.Market, captured.captured.orderType)
    }
}
