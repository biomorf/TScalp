package com.example.tscalp.domain.usecases

import com.example.tscalp.domain.models.FutureUi
import com.example.tscalp.domain.models.InstrumentUi
import com.example.tscalp.domain.models.OrderTypeSelection
import com.example.tscalp.util.formatCurrency
import com.example.tscalp.util.formatPrice

data class TradeDetails(
    val executionPrice: Double,
    val costOverlay: String?,
    val multiplierOverlay: String?
)

class CalculateTradeDetailsUseCase {

    fun calculate(
        currentPrice: Double?,
        limitPrice: String?,
        stopPrice: String?,
        orderType: OrderTypeSelection,
        instrument: InstrumentUi?,
        pairedInstrument: InstrumentUi?,
        quantity: Long,
        pairedMultiplier: String?,
        pairCurrentPrice: Double?
    ): TradeDetails {
        val instrumentType = instrument?.instrumentType ?: ""

        // Определяем цену исполнения основной сделки
        val executionPrice = when (orderType) {
            OrderTypeSelection.Market -> currentPrice ?: 0.0
            OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> limitPrice?.toDoubleOrNull() ?: 0.0
            OrderTypeSelection.StopLoss, OrderTypeSelection.TakeProfit -> stopPrice?.toDoubleOrNull() ?: 0.0
        }

        // Подсказка для поля "Количество"
        val costOverlay = if (quantity > 0 && executionPrice > 0) {
            if (instrumentType == "futures") {
                val pointValue = (instrument as? FutureUi)?.pointValue ?: 1.0
                val totalPoints = executionPrice * quantity
                val totalRub = totalPoints * pointValue
                "${formatPrice(totalPoints, instrumentType)}  ·  ${formatCurrency(totalRub)}"
            } else {
                formatCurrency(executionPrice * quantity)
            }
        } else null

        // Подсказка для поля "Множитель"
        val multiplierOverlay = if (pairedInstrument != null && pairedMultiplier != null) {
            val totalQty = quantity * (pairedMultiplier.toDoubleOrNull() ?: 1.0)
            val pairExecPrice = when (orderType) {
                OrderTypeSelection.Market -> pairCurrentPrice ?: 0.0
                OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> limitPrice?.toDoubleOrNull() ?: 0.0
                OrderTypeSelection.StopLoss, OrderTypeSelection.TakeProfit -> stopPrice?.toDoubleOrNull() ?: 0.0
            }
            if (totalQty > 0 && pairExecPrice > 0) {
                val pairedInstrumentType = pairedInstrument.instrumentType
                if (pairedInstrumentType == "futures") {
                    val pairedPointValue = (pairedInstrument as? FutureUi)?.pointValue ?: 1.0
                    val pairTotalPoints = pairExecPrice * totalQty
                    val pairTotalRub = pairTotalPoints * pairedPointValue
                    "${formatPrice(pairTotalPoints, pairedInstrumentType)}  ·  ${formatCurrency(pairTotalRub)}"
                } else {
                    formatCurrency(pairExecPrice * totalQty)
                }
            } else null
        } else null

        return TradeDetails(executionPrice, costOverlay, multiplierOverlay)
    }
}