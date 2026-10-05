package com.example.tscalp.domain.models

sealed class TradeCheckResult {
    data object Success : TradeCheckResult()
    data class Error(val message: String) : TradeCheckResult()
}

data class TradingStatusDetails(
    val isApiTradeAvailable: Boolean,
    val buyAvailable: Boolean,
    val sellAvailable: Boolean,
    val tradingStatus: String   // например "SECURITY_TRADING_STATUS_NORMAL_TRADING"
)

enum class TradingAvailability {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN
}