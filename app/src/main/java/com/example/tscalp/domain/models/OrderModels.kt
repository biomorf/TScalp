package com.example.tscalp.domain.models

enum class OrderStatus {
    NEW, PARTIALLY_FILLED, FILLED, REJECTED, CANCELLED
}

//enum class OrderType { MARKET, LIMIT }
enum class BrokerOrderType { MARKET, LIMIT }

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

enum class StopOrderType { TAKE_PROFIT, STOP_LOSS, STOP_LIMIT }
enum class StopOrderExpirationType { GOOD_TILL_CANCEL, GOOD_TILL_DATE }

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

data class OrderResult(
    val orderId: String,
    val executedLots: Long,
    val totalLots: Long,
    val status: OrderStatus
)
