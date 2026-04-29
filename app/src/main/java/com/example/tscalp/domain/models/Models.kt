package com.example.tscalp.domain.models

data class InstrumentUi(
    val figi: String,
    val ticker: String,
    val name: String,
    val currency: String,
    val lot: Int,
    val instrumentType: String = ""   /// тип инструмента: share, bond, etf, currency
)

data class PortfolioPosition(
    val name: String,
    val ticker: String,
    val quantity: Long,
    val currentPrice: Double,
    val totalValue: Double,
    val profit: Double = 0.0,
    val profitPercent: Double =0.0,
    val instrumentType: String = "",
    val priceChangePercent: Double? = null,
    val brokerName: String = ""
)

/**
 * Универсальное представление денежной суммы для пополнения песочницы.
 * Не зависит от protobuf.
 */
data class SandboxMoney(
    val currency: String,
    val units: Long,
    val nano: Int = 0
)
