package com.gitlab.biomorf.tscalp.presentation.util

import androidx.compose.ui.graphics.Color
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType

object PositionFormatter {
    /**
     * Форматирует среднюю цену и общую среднюю стоимость для левой нижней секции.
     * Возвращает пару: (строка для общей стоимости, строка для "quantity · averagePrice").
     */
    fun formatAverage(
        quantity: Double,
        averagePrice: Double?,
        currency: String?,
        instrumentType: InstrumentType,
    ): Pair<String?, String?> {
        if (averagePrice == null || averagePrice <= 0) {
            return null to null
        }
        val avgTotal = quantity * averagePrice
        val totalStr = formatPrice(avgTotal, instrumentType, currency ?: DOMESTIC_CURRENCY)
        val detailStr = "${formatQuantity(quantity)} лот  ·  ${formatPrice(averagePrice, instrumentType, currency ?: DOMESTIC_CURRENCY)}"
        return totalStr to detailStr
    }

    /**
     * Форматирует прибыль/убыток и процент изменения для правой нижней секции.
     * Возвращает тройку: (строка прибыли, цвет, строка процента).
     */
    fun formatProfit(
        profit: Double?,
        profitPercent: Double?,
        instrumentType: InstrumentType,
        pointValue: Double?,
    ): Triple<String?, Color, String?> {
        if (profit == null) {
            return Triple(null, Color.Unspecified, null)
        }

        val profitColor = if (profit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)

        val profitStr =
            if (instrumentType == InstrumentType.FUTURES) {
                val pointsStr = "%.2f".format(profit)
                val sign = if (profit >= 0) "+" else ""
                if (pointValue != null && pointValue > 0) {
                    val profitRub = profit * pointValue
                    val rubSign = if (profitRub >= 0) "+" else ""
                    "${sign}$pointsStr пт  ·  ${rubSign}${formatDomesticCurrency(profitRub)}"
                } else {
                    "${sign}$pointsStr пт"
                }
            } else {
                val sign = if (profit >= 0) "+" else ""
                sign + formatDomesticCurrency(profit)
            }

        val percentStr =
            if (profitPercent != null) {
                val percentSign = if (profitPercent >= 0) "+" else ""
                "${percentSign}${"%.2f".format(profitPercent)}%"
            } else {
                null
            }

        val percentColor =
            if (profitPercent != null) {
                if (profitPercent >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
            } else {
                Color.Unspecified
            }

        return Triple(profitStr, profitColor, percentStr)
    }

    /**
     * Форматирует текущую цену для верхней правой секции.
     * Возвращает пару: (строка цены, строка рублёвого эквивалента для фьючерсов).
     */
    fun formatCurrentPrice(
        price: Double,
        instrumentType: InstrumentType,
        pointValue: Double?,
        currency: String? = null,
    ): Pair<String?, String?> {
        if (price <= 0) return null to null
        val priceStr = formatPrice(price, instrumentType, currency ?: DOMESTIC_CURRENCY)
        val rubStr =
            if (instrumentType == InstrumentType.FUTURES && pointValue != null && pointValue > 0) {
                formatDomesticCurrency(price * pointValue)
            } else {
                null
            }
        return priceStr to rubStr
    }

    /**
     * Форматирует количество: `10.0` → `"10"`, `10.5` → `"10.5"`.
     * `Position.quantity: Double` (Finam дробное); для T-Invest/BCS
     * целые значения отображаются без `.0`.
     */
    fun formatQuantity(quantity: Double): String =
        if (quantity == quantity.toLong().toDouble()) quantity.toLong().toString() else "%.1f".format(quantity)

    /**
     * Возвращает цвет для изменения цены (процент изменения).
     */
    fun priceChangeColor(priceChangePercent: Double?): Color =
        when {
            priceChangePercent == null -> Color.Unspecified
            priceChangePercent >= 0 -> Color(0xFF2E7D32)
            else -> Color(0xFFC62828)
        }
}
