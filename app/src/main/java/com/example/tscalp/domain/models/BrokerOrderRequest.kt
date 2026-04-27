package com.example.tscalp.domain.models

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
    val type: OrderType = OrderType.MARKET,
    val price: Double? = null
)

enum class OrderType { MARKET, LIMIT }
enum class BrokerOrderType { MARKET, LIMIT }