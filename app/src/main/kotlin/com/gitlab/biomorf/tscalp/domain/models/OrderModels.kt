package com.gitlab.biomorf.tscalp.domain.models

/**
 * Событие stream-стрима `subscribeOrderState` (gRPC + WS fallback).
 *
 * **Неполная transport-модель:** приходит только из stream, содержит
 * текущее состояние заявки, но **не содержит stop-полей**
 * (`stopPrice`, `isStopOrder`). Для stop-заявок используется
 * [OrderListItem] через unary (`getOrders` + `getStopOrders`).
 *
 * В `OrderFlowManager` события [OrderState] конвертируются в
 * [OrderListItem] (`toOrderListItem`) и мержатся с polling-снимком.
 *
 * @property orderId идентификатор заявки у брокера.
 * @property orderRequestId клиентский идентификатор запроса (если
 *   брокер вернул). `null` — нет.
 * @property ticker биржевой тикер.
 * @property direction `"BUY"` / `"SELL"` (без префикса
 *   `ORDER_DIRECTION_`).
 * @property limitPrice начальная лимитная цена (для лимитных).
 *   `null` — рыночная или не применимо.
 * @property executedPrice цена исполнения. `null` — не исполнена.
 * @property quantity заявленное количество (лоты).
 * @property executedQuantity исполненное количество (лоты).
 * @property status `"NEW"`, `"FILL"`, `"PARTIALLYFILL"` и т.д.
 *   (без префикса `EXECUTION_REPORT_STATUS_`).
 * @property updateTime время создания/обновления (epoch seconds).
 * @property orderType `"LIMIT"`, `"MARKET"`, `"BESTPRICE"` (без
 *   префикса `ORDER_TYPE_`). `null` — источник не дал (старый API,
 *   gRPC без маппинга).
 * @property tscalpInstrumentId uid инструмента для склейки с
 *   позициями / заявками из polling. `null` — не пришёл.
 */
data class OrderState(
    val orderId: String,
    val orderRequestId: String?,
    val ticker: String,
    // "BUY" / "SELL"
    val direction: String,
    // начальная лимитная цена (если лимитный)
    val limitPrice: Double?,
    // цена исполнения
    val executedPrice: Double?,
    val quantity: Long,
    val executedQuantity: Long,
    // "NEW", "FILL", "PARTIALLYFILL" и т.д.
    val status: String,
    // epoch seconds
    val updateTime: Long?,
    // "LIMIT", "MARKET", "BESTPRICE" (без ORDER_TYPE_ префикса)
    // null — если источник не дал (gRPC без маппинга, старый API)
    val orderType: String? = null,
    // uid инструмента для склейки с позициями/заявками из polling
    val tscalpInstrumentId: String? = null,
)

data class OrderResult(
    val orderId: String,
    val executedLots: Long,
    val totalLots: Long,
    val status: OrderStatus,
    val orderRequestId: String? = null,
)

/**
 * Универсальная модель заявки для UI — unary-путь
 * (`getOrders` + `getStopOrders`).
 *
 * **Sum-type через флаг:** обычная и stop-заявка в одной модели,
 * различаются полями [isStopOrder] и [stopPrice]. Это осознанный
 * временный компромисс — антипаттерн «флаг + опциональное поле».
 * Планируется рефакторинг в `sealed interface Order { Regular, Stop }`
 * (см. `### Split order domain into sealed Regular/Stop` в ROADMAP).
 *
 * @property orderId идентификатор заявки у брокера.
 * @property ticker биржевой тикер.
 * @property tscalpInstrumentId универсальный ключ инструмента.
 * @property instrumentType строковый тип: `"share"`, `"futures"`,
 *   `"currency"`, `"etf"`.
 * @property direction `"BUY"` / `"SELL"`.
 * @property price лимитная цена (для обычной) **или** цена
 *   срабатывания (для stop-заявки, дублирует `stopPrice`).
 * @property stopPrice стоп-цена. `null` для обычной заявки.
 * @property quantity заявленное количество (лоты).
 * @property type `"LIMIT"`, `"MARKET"`, `"STOP_LOSS"`,
 *   `"TAKE_PROFIT"`, `"STOP_LIMIT"` (строковый enum).
 * @property status `"NEW"`, `"FILL"`, `"PARTIALLYFILL"` и т.д.
 * @property orderDate время создания (epoch seconds).
 * @property isStopOrder `true` → отмена через `cancelStopOrder`,
 *   `false` → `cancelOrder`. См. sum-type-компромисс выше.
 */
data class OrderListItem(
    val orderId: String,
    val ticker: String,
    val tscalpInstrumentId: String,
    val instrumentType: String = "",
    // "BUY" / "SELL"
    val direction: String,
    // лимитная цена (для обычных) или стоп-цена
    val price: Double,
    // null для обычных
    val stopPrice: Double?,
    val quantity: Long,
    // "LIMIT", "MARKET", "STOP_LOSS", "TAKE_PROFIT", "STOP_LIMIT"
    val type: String,
    val status: String,
    // время создания (epoch seconds)
    val orderDate: Long?,
    // true → отмена через cancelStopOrder, false → cancelOrder
    val isStopOrder: Boolean,
)

enum class OrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    REJECTED,
    CANCELLED,
}

// enum class OrderType { MARKET, LIMIT }
enum class BrokerOrderType {
    MARKET,
    LIMIT,
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
    // для stop-limit, иначе null
    val price: Double?,
    // TAKE_PROFIT, STOP_LOSS, STOP_LIMIT
    val stopOrderType: StopOrderType,
    val expirationType: StopOrderExpirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
    // если нужна конкретная дата
    val expireDate: String? = null,
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
    val price: Double? = null,
)

sealed class OrderTypeSelection {
    object Market : OrderTypeSelection()

    object Limit : OrderTypeSelection()

    object StopLoss : OrderTypeSelection()

    object TakeProfit : OrderTypeSelection()

    object StopLimit : OrderTypeSelection()

    /** Преобразует выбранный тип в StopOrderType, если это стоп-заявка */
    val stopOrderType: StopOrderType?
        get() =
            when (this) {
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
fun OrderTypeSelection.toStorageKey(): String =
    when (this) {
        OrderTypeSelection.Market -> "Market"
        OrderTypeSelection.Limit -> "Limit"
        OrderTypeSelection.StopLoss -> "StopLoss"
        OrderTypeSelection.TakeProfit -> "TakeProfit"
        OrderTypeSelection.StopLimit -> "StopLimit"
    }

/** Восстановление типа заявки из строкового ключа. Неизвестное значение → Market. */
fun orderTypeFromStorageKey(key: String): OrderTypeSelection =
    when (key) {
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
    val nano: Int = 0,
)

/**
 * Конвертация stream-модели в модель UI-списка заявок.
 *
 * Stream (gRPC/WS) отдаёт только обычные заявки (без stop).
 * `stopPrice = null`, `isStopOrder = false` всегда.
 *
 * [instrumentType] пуст: `OrderState` не знает типа инструмента;
 * UI-карточке достаточно [OrderListItem.type] (LIMIT/MARKET).
 */
internal fun OrderState.toOrderListItem(): OrderListItem =
    OrderListItem(
        orderId = orderId,
        ticker = ticker,
        tscalpInstrumentId = tscalpInstrumentId ?: "",
        instrumentType = "",
        direction = direction,
        price = limitPrice ?: executedPrice ?: 0.0,
        stopPrice = null,
        quantity = quantity,
        type = orderType?.takeIf { it != "UNSPECIFIED" } ?: "LIMIT",
        status = status,
        orderDate = updateTime,
        isStopOrder = false,
    )
