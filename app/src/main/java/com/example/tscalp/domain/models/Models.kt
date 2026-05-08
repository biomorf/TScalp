package com.example.tscalp.domain.models

data class InstrumentUi(
    val tscalpInstrumentId: String,    // универсальный идентификатор TScalp
    val ticker: String,
    val classCode: String,            // код класса (например, SPBFUT)
    val isin: String,                 // ISIN
    val ttech_uid: String,                  // instrument_uid из T-tech Invest API
    val ttech_figi: String = "",   // временно для стрима LastPrice
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
    val brokerName: String = "",
    val tscalpInstrumentId: String = ""
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
