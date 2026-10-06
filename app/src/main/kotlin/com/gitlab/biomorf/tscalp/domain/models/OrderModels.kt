package com.gitlab.biomorf.tscalp.domain.models

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
    val instrumentType: String = "",
    val direction: String,      // "BUY" / "SELL"
    val price: Double,          // лимитная цена (для обычных) или стоп-цена
    val stopPrice: Double?,     // null для обычных
    val quantity: Long,
    val type: String,           // "LIMIT", "MARKET", "STOP_LOSS", "TAKE_PROFIT", "STOP_LIMIT"
    val status: String,
    val orderDate: Long?,       // время создания (epoch seconds)
    val isStopOrder: Boolean    // true → отмена через cancelStopOrder, false → cancelOrder
)

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
    val brokerName: BrokerName,
    val ticker: String,
    val tscalpInstrumentId: String? = null,
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
/**
 * Универсальная модель заявки, не зависящая от protobuf.
 * @param type тип заявки: MARKET или LIMIT
 * @param price цена (для рыночной игнорируется)
 */
data class BrokerOrderRequest(
    val brokerName: BrokerName,
    val ticker: String,
    val tscalpInstrumentId: String? = null,
    val quantity: Long,
    val direction: OrderDirection,
    val accountId: String,
    val sandboxMode: Boolean,
    val type: BrokerOrderType = BrokerOrderType.MARKET,
    val price: Double? = null
)

sealed class OrderTypeSelection {
    object Market : OrderTypeSelection()
    object Limit : OrderTypeSelection()
    object StopLoss : OrderTypeSelection()
    object TakeProfit : OrderTypeSelection()
    object StopLimit : OrderTypeSelection()

    /** Преобразует выбранный тип в StopOrderType, если это стоп-заявка */
    val stopOrderType: StopOrderType?
        get() = when (this) {
            is StopLoss -> StopOrderType.STOP_LOSS
            is TakeProfit -> StopOrderType.TAKE_PROFIT
            is StopLimit -> StopOrderType.STOP_LIMIT
            else -> null
        }

    /** Является ли тип обычной заявкой (Market/Limit) */
    val isRegular: Boolean
        get() = this is Market || this is Limit
}

/** Строковый ключ для хранения типа заявки в DataStore. */
fun OrderTypeSelection.toStorageKey(): String = when (this) {
    OrderTypeSelection.Market -> "Market"
    OrderTypeSelection.Limit -> "Limit"
    OrderTypeSelection.StopLoss -> "StopLoss"
    OrderTypeSelection.TakeProfit -> "TakeProfit"
    OrderTypeSelection.StopLimit -> "StopLimit"
}

/** Восстановление типа заявки из строкового ключа. Неизвестное значение → Market. */
fun orderTypeFromStorageKey(key: String): OrderTypeSelection = when (key) {
    "Market" -> OrderTypeSelection.Market
    "Limit" -> OrderTypeSelection.Limit
    "StopLoss" -> OrderTypeSelection.StopLoss
    "TakeProfit" -> OrderTypeSelection.TakeProfit
    "StopLimit" -> OrderTypeSelection.StopLimit
    else -> OrderTypeSelection.Market
}

/**
 * Универсальное представление денежной суммы для пополнения песочницы.
 * Не зависит от protobuf.
 */
data class SandboxMoney(
    val currency: String,
    val units: Long,
    val nano: Int = 0
)
