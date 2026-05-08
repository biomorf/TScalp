package com.example.tscalp.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Форматирует числовое значение в российские рубли (₽).
 */
fun formatCurrency(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("ru", "RU"))
    format.currency = Currency.getInstance("RUB")
    return format.format(value)
}

/**
 * Форматирует цену в зависимости от типа инструмента.
 * Для фьючерсов – в пунктах, для остальных – в валюте.
 */
fun formatPrice(price: Double, instrumentType: String): String {
    return if (instrumentType == "futures") {
        "%.2f пт".format(price)
    } else {
        formatCurrency(price)
    }
}