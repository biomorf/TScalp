package com.example.tscalp.util

import androidx.compose.ui.graphics.Color
import com.example.tscalp.domain.models.PortfolioPosition

object PositionFormatter {

    /**
     * Форматирует среднюю цену и общую среднюю стоимость для левой нижней секции.
     * Возвращает пару: (строка для общей стоимости, строка для "quantity · averagePrice").
     */
    fun formatAverage(position: PortfolioPosition, instrumentType: String): Pair<String?, String?> {
        val avgPrice = position.averagePrice
        if (avgPrice == null || avgPrice <= 0) {
            return null to null
        }
        val avgTotal = position.quantity.toDouble() * avgPrice
        val totalStr = formatPrice(avgTotal, instrumentType)
        val detailStr = "${position.quantity} лот  ·  ${formatPrice(avgPrice, instrumentType)}"
        return totalStr to detailStr
    }

    /**
     * Форматирует прибыль/убыток и процент изменения для правой нижней секции.
     * Возвращает тройку: (строка прибыли, цвет, строка процента).
     */
    fun formatProfit(position: PortfolioPosition, instrumentType: String, pointValue: Double?): Triple<String?, Color, String?> {
        val profit = position.profit
        val profitPercent = position.profitPercent

        if (profit == null) {
            return Triple(null, Color.Unspecified, null)
        }

        val profitColor = if (profit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)

        val profitStr = if (instrumentType == "futures") {
            val pointsStr = "%.2f".format(profit)
            val sign = if (profit >= 0) "+" else ""
            if (pointValue != null && pointValue > 0) {
                val profitRub = profit * pointValue
                val rubSign = if (profitRub >= 0) "+" else ""
                "${sign}${pointsStr} пт  ·  ${rubSign}${formatCurrency(profitRub)}"
            } else {
                "${sign}${pointsStr} пт"
            }
        } else {
            val sign = if (profit >= 0) "+" else ""
            sign + formatCurrency(profit)
        }

        val percentStr = if (profitPercent != null) {
            val percentSign = if (profitPercent >= 0) "+" else ""
            "${percentSign}${"%.2f".format(profitPercent)}%"
        } else null

        val percentColor = if (profitPercent != null) {
            if (profitPercent >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
        } else Color.Unspecified

        return Triple(profitStr, profitColor, percentStr)
    }

    /**
     * Форматирует текущую цену для верхней правой секции.
     * Возвращает пару: (строка цены, строка рублёвого эквивалента для фьючерсов).
     */
    fun formatCurrentPrice(position: PortfolioPosition, instrumentType: String, pointValue: Double?): Pair<String?, String?> {
        if (position.currentPrice <= 0) return null to null
        val priceStr = formatPrice(position.currentPrice, instrumentType)
        val rubStr = if (instrumentType == "futures" && pointValue != null && pointValue > 0) {
            formatCurrency(position.currentPrice * pointValue)
        } else null
        return priceStr to rubStr
    }

    /**
     * Возвращает цвет для изменения цены (процент изменения).
     */
    fun priceChangeColor(priceChangePercent: Double?): Color {
        return when {
            priceChangePercent == null -> Color.Unspecified
            priceChangePercent >= 0 -> Color(0xFF2E7D32)
            else -> Color(0xFFC62828)
        }
    }
}