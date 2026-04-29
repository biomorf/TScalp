package com.example.tscalp.domain.models

enum class OrderStatus {
    NEW, PARTIALLY_FILLED, FILLED, REJECTED, CANCELLED
}

//enum class OrderType { MARKET, LIMIT }
enum class BrokerOrderType { MARKET, LIMIT }

enum class OrderDirection { BUY, SELL }

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
