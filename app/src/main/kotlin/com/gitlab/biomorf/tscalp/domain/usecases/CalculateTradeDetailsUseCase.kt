package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.moneyPerPointOfPrice

data class TradeDetails(
    val executionPrice: Double,
    val costPoints: Double? = null,
    val costCurrency: Double? = null,
    val costDomesticCurrency: Double? = null,
    val multiplierPoints: Double? = null,
    val multiplierCurrency: Double? = null,
    val multiplierDomesticCurrency: Double? = null,
)

class CalculateTradeDetailsUseCase {
    fun calculate(
        currentPrice: Double?,
        limitPrice: String?,
        stopPrice: String?,
        orderType: OrderTypeSelection,
        instrument: Instrument?,
        pairedInstrument: Instrument?,
        quantity: Long,
        pairedMultiplier: Long?,
        pairCurrentPrice: Double?,
    ): TradeDetails {
        val exec = executionPrice(orderType, currentPrice, limitPrice, stopPrice)
        val cost = calculateCost(exec, quantity, instrument)
        val mult =
            calculateMultiplier(
                quantity = quantity,
                pairedMultiplier = pairedMultiplier,
                pairedInstrument = pairedInstrument,
                pairExecPrice = pairExecutionPrice(orderType, pairCurrentPrice, limitPrice, stopPrice),
            )
        return TradeDetails(
            exec,
            cost.points,
            cost.currency,
            cost.domestic,
            mult.points,
            mult.currency,
            mult.domestic,
        )
    }

    /** Цена исполнения основной сделки по типу заявки. */
    private fun executionPrice(
        orderType: OrderTypeSelection,
        currentPrice: Double?,
        limitPrice: String?,
        stopPrice: String?,
    ): Double = priceFor(orderType, currentPrice, limitPrice, stopPrice)

    /** Цена исполнения парной сделки: для Market — `pairCurrentPrice`, иначе — limit/stop. */
    private fun pairExecutionPrice(
        orderType: OrderTypeSelection,
        pairCurrentPrice: Double?,
        limitPrice: String?,
        stopPrice: String?,
    ): Double = priceFor(orderType, pairCurrentPrice, limitPrice, stopPrice)

    /** Общий выбор цены по типу заявки (снимает дубль `when` основной / парной). */
    private fun priceFor(
        orderType: OrderTypeSelection,
        marketPrice: Double?,
        limitPrice: String?,
        stopPrice: String?,
    ): Double =
        when (orderType) {
            OrderTypeSelection.Market -> marketPrice ?: 0.0
            OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> limitPrice?.toDoubleOrNull() ?: 0.0
            OrderTypeSelection.StopLoss, OrderTypeSelection.TakeProfit -> stopPrice?.toDoubleOrNull() ?: 0.0
        }

    private fun calculateCost(
        execPrice: Double,
        quantity: Long,
        instrument: Instrument?,
    ): CostDetails {
        if (quantity <= 0L || execPrice <= 0.0) return CostDetails(null, null, null)
        return if (instrument?.instrumentType == InstrumentType.FUTURES) {
            val pointValue = instrument.moneyPerPointOfPrice
            val points = execPrice * quantity
            CostDetails(points, null, points * pointValue)
        } else {
            CostDetails(null, execPrice * quantity, null)
        }
    }

    private fun calculateMultiplier(
        quantity: Long,
        pairedMultiplier: Long?,
        pairedInstrument: Instrument?,
        pairExecPrice: Double,
    ): MultiplierDetails {
        if (pairedInstrument == null || pairedMultiplier == null) return MultiplierDetails(null, null, null)
        val totalQty = quantity * pairedMultiplier
        if (totalQty <= 0L || pairExecPrice <= 0.0) return MultiplierDetails(null, null, null)
        return if (pairedInstrument.instrumentType == InstrumentType.FUTURES) {
            val points = pairExecPrice * totalQty
            MultiplierDetails(points, null, points * pairedInstrument.moneyPerPointOfPrice)
        } else {
            MultiplierDetails(null, pairExecPrice * totalQty, null)
        }
    }

    private data class CostDetails(
        val points: Double?,
        val currency: Double?,
        val domestic: Double?,
    )

    private data class MultiplierDetails(
        val points: Double?,
        val currency: Double?,
        val domestic: Double?,
    )
}
