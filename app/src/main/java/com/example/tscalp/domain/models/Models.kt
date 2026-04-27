package com.example.tscalp.domain.models

data class InstrumentUi(
    val figi: String,
    val ticker: String,
    val name: String,
    val currency: String,
    val lot: Int,
    val instrumentType: String = ""   /// тип инструмента: share, bond, etf, currency
)

data class AccountUi(
    val id: String,
    val name: String,
    val type: AccountType
)

enum class AccountType {
    BROKER, IIS, INVEST_BOX
}

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

data class OrderResult(
    val orderId: String,
    val executedLots: Long,
    val totalLots: Long,
    val status: OrderStatus
)

enum class OrderStatus {
    NEW, PARTIALLY_FILLED, FILLED, REJECTED, CANCELLED
}