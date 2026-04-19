package com.example.tscalp.domain.models

data class AccountUi(
    val id: String,
    val name: String,
    val type: AccountType
)

enum class AccountType {
    BROKER, IIS, INVEST_BOX
}

data class PortfolioPosition(
    val figi: String,
    val name: String,
    val ticker: String,
    val quantity: Long,
    val currentPrice: Double,
    val totalValue: Double,
    val profit: Double,
    val profitPercent: Double
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