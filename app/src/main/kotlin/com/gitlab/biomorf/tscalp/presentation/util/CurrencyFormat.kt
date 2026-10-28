package com.gitlab.biomorf.tscalp.presentation.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Валюта расчётов между пользователем и брокером (domestic currency). */
const val DOMESTIC_CURRENCY = "RUB"

/**
 * Форматирует числовое значение в указанной валюте.
 */
fun formatCurrency(
    value: Double,
    currency: String,
): String {
    val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU"))
    format.currency = Currency.getInstance(currency)
    return format.format(value)
}

/**
 * Форматирует значение в domestic currency (по умолчанию RUB).
 */
fun formatDomesticCurrency(value: Double): String = formatCurrency(value, DOMESTIC_CURRENCY)

/**
 * Форматирует цену в зависимости от типа инструмента.
 * Для фьючерсов – в пунктах, для остальных – в валюте.
 */
fun formatPrice(
    price: Double,
    instrumentType: String,
    currency: String,
): String =
    if (instrumentType == "futures") {
        "%.2f пт".format(price)
    } else {
        formatCurrency(price, currency)
    }
