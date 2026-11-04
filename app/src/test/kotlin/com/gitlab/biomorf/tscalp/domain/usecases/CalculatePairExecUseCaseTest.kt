package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatePairExecUseCaseTest {
    private val useCase = CalculatePairExecUseCase()

    @Test
    fun disabledPairTrading_returnsZeroAndNull() {
        val result =
            useCase.execute(
                PairExecInput(
                    pairTradingEnabled = false,
                    hasPairedInstrument = true,
                    orderType = OrderTypeSelection.Limit,
                    limitPrice = "100.0",
                    stopPrice = null,
                    pendingDirection = OrderDirection.BUY,
                    executionPrice = 100.0,
                    pairCurrentPrice = 100.0,
                ),
            )
        assertEquals(0.0, result.pairExecPrice, 0.0)
        assertNull(result.pairedOrderType)
    }

    @Test
    fun noPairedInstrument_returnsZeroAndNull() {
        val result =
            useCase.execute(
                PairExecInput(
                    pairTradingEnabled = true,
                    hasPairedInstrument = false,
                    orderType = OrderTypeSelection.Limit,
                    limitPrice = "100.0",
                    stopPrice = null,
                    pendingDirection = OrderDirection.BUY,
                    executionPrice = 100.0,
                    pairCurrentPrice = 100.0,
                ),
            )
        assertEquals(0.0, result.pairExecPrice, 0.0)
        assertNull(result.pairedOrderType)
    }

    @Test
    fun limitBuy_usesExecutionPriceAndStopLoss() {
        val result =
            useCase.execute(
                PairExecInput(
                    pairTradingEnabled = true,
                    hasPairedInstrument = true,
                    orderType = OrderTypeSelection.Limit,
                    limitPrice = "100.0",
                    stopPrice = null,
                    pendingDirection = OrderDirection.BUY,
                    executionPrice = 99.5,
                    pairCurrentPrice = null,
                ),
            )
        assertEquals(99.5, result.pairExecPrice, 0.0)
        assertEquals(OrderTypeSelection.StopLoss, result.pairedOrderType)
    }

    @Test
    fun limitSell_usesExecutionPriceAndTakeProfit() {
        val result =
            useCase.execute(
                PairExecInput(
                    pairTradingEnabled = true,
                    hasPairedInstrument = true,
                    orderType = OrderTypeSelection.Limit,
                    limitPrice = "100.0",
                    stopPrice = null,
                    pendingDirection = OrderDirection.SELL,
                    executionPrice = 100.0,
                    pairCurrentPrice = null,
                ),
            )
        assertEquals(100.0, result.pairExecPrice, 0.0)
        assertEquals(OrderTypeSelection.TakeProfit, result.pairedOrderType)
    }

    @Test
    fun marketUsesPairCurrentPrice() {
        val result =
            useCase.execute(
                PairExecInput(
                    pairTradingEnabled = true,
                    hasPairedInstrument = true,
                    orderType = OrderTypeSelection.Market,
                    limitPrice = null,
                    stopPrice = null,
                    pendingDirection = OrderDirection.BUY,
                    executionPrice = 0.0,
                    pairCurrentPrice = 105.0,
                ),
            )
        assertEquals(105.0, result.pairExecPrice, 0.0)
    }
}
