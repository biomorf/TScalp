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