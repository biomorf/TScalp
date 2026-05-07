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

data class OrderState(
    val orderId: String,
    val orderRequestId: String?,
    val ticker: String,
    val direction: String,          // "BUY" / "SELL"
    val limitPrice: Double?,        // начальная лимитная цена (если лимитный)
    val executedPrice: Double?,     // цена исполнения
    val quantity: Long,
    val executedQuantity: Long,
    val status: String,             // "NEW", "FILL", "PARTIALLYFILL" и т.д.
    val updateTime: Long?           // epoch seconds
)

data class OrderResult(
    val orderId: String,
    val executedLots: Long,
    val totalLots: Long,
    val status: OrderStatus,
    val orderRequestId: String? = null
)

data class OrderListItem(
    val orderId: String,
    val ticker: String,
    val tscalpInstrumentId: String,
    val direction: String,      // "BUY" / "SELL"
    val price: Double,          // лимитная цена (для обычных) или стоп-цена
    val stopPrice: Double?,     // null для обычных
    val quantity: Long,
    val type: String,           // "LIMIT", "MARKET", "STOP_LOSS", "TAKE_PROFIT", "STOP_LIMIT"
    val status: String,
    val orderDate: Long?,       // время создания (epoch seconds)
    val isStopOrder: Boolean    // true → отмена через cancelStopOrder, false → cancelOrder
)

enum class TradingAvailability {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN
}

enum class OrderStatus {
    NEW, PARTIALLY_FILLED, FILLED, REJECTED, CANCELLED
}

//enum class OrderType { MARKET, LIMIT }
enum class BrokerOrderType {
    MARKET,
    LIMIT
}
enum class StopOrderType { TAKE_PROFIT, STOP_LOSS, STOP_LIMIT }
enum class OrderDirection { BUY, SELL }

/**
 * Универсальная модель стоп-заявки, не зависящая от protobuf.
 */
data class StopOrderRequest(
    val brokerName: String,
    val ticker: String,
    val quantity: Long,
    val direction: OrderDirection,
    val accountId: String,
    val sandboxMode: Boolean,
    val stopPrice: Double,
    val price: Double?,                // для stop-limit, иначе null
    val stopOrderType: StopOrderType,  // TAKE_PROFIT, STOP_LOSS, STOP_LIMIT
    val expirationType: StopOrderExpirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
    val expireDate: String? = null     // если нужна конкретная дата
)


enum class StopOrderExpirationType { GOOD_TILL_CANCEL, GOOD_TILL_DATE }

data class StopOrdersUiState(
    val orders: List<StopOrderUi> = emptyList(),
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

data class StopOrderUi(
    val stopOrderId: String,
    val ticker: String,
    val figi: String,
    val direction: String,          // "BUY" или "SELL"
    val stopPrice: Double,
    val limitPrice: Double?,        // цена для stop-limit
    val quantity: Long,
    val type: String,              // "TAKE_PROFIT", "STOP_LOSS", "STOP_LIMIT"
    val status: String             // "ACTIVE", "EXECUTED", "CANCELLED"
)

/**
 * Универсальная модель заявки, не зависящая от protobuf.
 * @param type тип заявки: MARKET или LIMIT
 * @param price цена (для рыночной игнорируется)
 */
data class BrokerOrderRequest(
    val brokerName: String,
    val ticker: String,
    val quantity: Long,
    val direction: OrderDirection,
    val accountId: String,
    val sandboxMode: Boolean,
    val type: BrokerOrderType = BrokerOrderType.MARKET,
    val price: Double? = null
)

