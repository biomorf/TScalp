package com.gitlab.biomorf.tscalp.domain.models

sealed interface TradeCheckResult {
    data object Success : TradeCheckResult

    data class Error(
        val message: String,
    ) : TradeCheckResult

    data class InsufficientFunds(
        val required: Double,
        val balance: Double,
    ) : TradeCheckResult
}

data class TradingStatusDetails(
    val isApiTradeAvailable: Boolean,
    val buyAvailable: Boolean,
    val sellAvailable: Boolean,
    // например "SECURITY_TRADING_STATUS_NORMAL_TRADING"
    val tradingStatus: String,
)

enum class TradingAvailability {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN,
}
