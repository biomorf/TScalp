package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.models.FutureUi
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection

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
    // Архитектурный долг: функция требует декомпозиции.
    // См. ROADMAP → «Рефакторинг UseCase-слоя».
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun calculate(
        currentPrice: Double?,
        limitPrice: String?,
        stopPrice: String?,
        orderType: OrderTypeSelection,
        instrument: InstrumentUi?,
        pairedInstrument: InstrumentUi?,
        quantity: Long,
        pairedMultiplier: String?,
        pairCurrentPrice: Double?,
    ): TradeDetails {
        val instrumentType = instrument?.instrumentType ?: ""

        // Определяем цену исполнения основной сделки
        val executionPrice =
            when (orderType) {
                OrderTypeSelection.Market -> currentPrice ?: 0.0
                OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> limitPrice?.toDoubleOrNull() ?: 0.0
                OrderTypeSelection.StopLoss, OrderTypeSelection.TakeProfit -> stopPrice?.toDoubleOrNull() ?: 0.0
            }

        // Подсказка для поля "Количество"
        val costPoints: Double?
        val costCurrency: Double?
        val costDomesticCurrency: Double?
        if (quantity > 0 && executionPrice > 0) {
            if (instrumentType == "futures") {
                val pointValue = (instrument as? FutureUi)?.pointValue ?: 1.0
                costPoints = executionPrice * quantity
                costCurrency = null
                costDomesticCurrency = costPoints * pointValue
            } else {
                costPoints = null
                costCurrency = executionPrice * quantity
                costDomesticCurrency = null
            }
        } else {
            costPoints = null
            costCurrency = null
            costDomesticCurrency = null
        }

        // Подсказка для поля "Множитель"
        val multiplierPoints: Double?
        val multiplierCurrency: Double?
        val multiplierDomesticCurrency: Double?
        if (pairedInstrument != null && pairedMultiplier != null) {
            val totalQty = quantity * (pairedMultiplier.toDoubleOrNull() ?: 1.0)
            val pairExecPrice =
                when (orderType) {
                    OrderTypeSelection.Market -> pairCurrentPrice ?: 0.0
                    OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> limitPrice?.toDoubleOrNull() ?: 0.0
                    OrderTypeSelection.StopLoss, OrderTypeSelection.TakeProfit -> stopPrice?.toDoubleOrNull() ?: 0.0
                }
            if (totalQty > 0 && pairExecPrice > 0) {
                val pairedInstrumentType = pairedInstrument.instrumentType
                if (pairedInstrumentType == "futures") {
                    val pairedPointValue = (pairedInstrument as? FutureUi)?.pointValue ?: 1.0
                    multiplierPoints = pairExecPrice * totalQty
                    multiplierCurrency = null
                    multiplierDomesticCurrency = multiplierPoints * pairedPointValue
                } else {
                    multiplierPoints = null
                    multiplierCurrency = pairExecPrice * totalQty
                    multiplierDomesticCurrency = null
                }
            } else {
                multiplierPoints = null
                multiplierCurrency = null
                multiplierDomesticCurrency = null
            }
        } else {
            multiplierPoints = null
            multiplierCurrency = null
            multiplierDomesticCurrency = null
        }

        return TradeDetails(executionPrice, costPoints, costCurrency, costDomesticCurrency, multiplierPoints, multiplierCurrency, multiplierDomesticCurrency)
    }
}
