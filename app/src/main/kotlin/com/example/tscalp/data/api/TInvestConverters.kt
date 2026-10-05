package com.example.tscalp.data.api

import ru.tinkoff.piapi.contract.v1.MoneyValue
import ru.tinkoff.piapi.contract.v1.Quotation

/**
 * Утилиты для конвертации значений protobuf (MoneyValue, Quotation)
 * в Kotlin Double и обратно.
 */
object TInvestConverters {

    /**
     * Quotation → Double. Возвращает null, если quotation == null.
     */
    fun quotationToDouble(q: Quotation?): Double? =
        q?.let { it.units + it.nano / 1_000_000_000.0 }

    /**
     * MoneyValue → Double. Возвращает null, если value == null.
     */
    fun moneyToDouble(m: MoneyValue?): Double? =
        m?.let { it.units + it.nano / 1_000_000_000.0 }

    /**
     * Double → Quotation. Требуется API во всех запросах с ценами.
     */
    fun doubleToQuotation(value: Double): Quotation {
        val units = value.toLong()
        val nano = ((value - units) * 1_000_000_000).toInt()
        return Quotation.newBuilder().setUnits(units).setNano(nano).build()
    }

    /**
     * Код класса инструмента → доменный тип (share / bond / futures / etf / currency).
     * Взято из classCodeToInstrumentType в TInvestBrokerAPI.
     */
    fun classCodeToInstrumentType(classCode: String): String = when (classCode) {
        "SPBFUT", "SPBOPT" -> "futures"
        "TQBR", "TQBS", "TQIF", "TQIR" -> "share"
        "TQOB", "TQCB", "TQRD" -> "bond"
        "TQTF" -> "etf"
        "CETS" -> "currency"
        else -> ""
    }
}