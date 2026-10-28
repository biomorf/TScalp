package com.gitlab.biomorf.tscalp.domain.models

/**
 * Событие stream-стрима `subscribeOrderState` (gRPC + WS fallback).
 *
 * **Неполная transport-модель:** приходит только из stream, содержит
 * текущее состояние заявки, но **не содержит stop-полей**
 * (`stopPrice`). Для stop-заявок используется
 * [Order] через unary (`getOrders` + `getStopOrders`).
 *
 * В `OrderFlowManager` события [OrderState] конвертируются в
 * [Order] (`toOrder`) и мержатся с polling-снимком.
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
 * (`getOrders` + `getStopOrders`) и stream-путь (`subscribeOrderState`).
 *
 * **Sum-type:** обычная и stop-заявка — разные типы.
 * `when (order) { is Order.Regular -> ...; is Order.Stop -> ... }`
 * проверяется компилятором.
 *
 * @property orderId идентификатор заявки у брокера.
 * @property ticker биржевой тикер.
 * @property tscalpInstrumentId универсальный ключ инструмента.
 * @property instrumentType строковый тип: `"share"`, `"futures"`,
 *   `"currency"`, `"etf"`.
 * @property direction `"BUY"` / `"SELL"`.
 * @property quantity заявленное количество (лоты).
 * @property orderType тип заявки.
 * @property status `"NEW"`, `"FILL"`, `"PARTIALLYFILL"` и т.д.
 * @property orderDate время создания (epoch seconds).
 */
sealed interface Order {
    val orderId: String
    val ticker: String
    val tscalpInstrumentId: String
    val instrumentType: String
    val direction: String
    val quantity: Long
    val price: Double
    val orderType: OrderTypeSelection
    val status: String
    val orderDate: Long?

    /**
     * Обычная заявка (лимитная / рыночная).
     *
     * @property price цена сделки (лимитная или исполнения).
     * @property executedPrice фактическая цена исполнения.
     *   `null` — не исполнена.
     * @property executedQuantity исполненное количество (лоты).
     *   `0` — не исполнена. Может быть меньше [quantity], если
     *   заявка исполнена частично.
     */
    data class Regular(
        override val orderId: String,
        override val ticker: String,
        override val tscalpInstrumentId: String,
        override val instrumentType: String,
        override val direction: String,
        override val quantity: Long,
        override val orderType: OrderTypeSelection,
        override val status: String,
        override val orderDate: Long?,
        override val price: Double,
        val executedPrice: Double? = null,
        val executedQuantity: Long = 0L,
    ) : Order

    /**
     * Стоп-заявка (STOP_LOSS / TAKE_PROFIT / STOP_LIMIT).
     *
     * Исполнение (`executedPrice`/`executedQuantity`) не входит в
     * модель: после активации брокер превращает stop-заявку в
     * обычную, исполнение видно через [Regular] по новому `orderId`.
     *
     * @property price цена сделки (для STOP_LIMIT — лимитная,
     *   иначе совпадает со [stopPrice] или `0.0`, если источник
     *   не дал).
     * @property stopPrice триггер-цена: при её достижении брокер
     *   выставляет заявку на бирже.
     */
    data class Stop(
        override val orderId: String,
        override val ticker: String,
        override val tscalpInstrumentId: String,
        override val instrumentType: String,
        override val direction: String,
        override val quantity: Long,
        override val orderType: OrderTypeSelection,
        override val status: String,
        override val orderDate: Long?,
        override val price: Double,
        val stopPrice: Double,
    ) : Order
}

/** Строковый `String` из data-слоя → [OrderTypeSelection]. */
internal fun String.toOrderTypeSelection(): OrderTypeSelection =
    when (this) {
        "LIMIT" -> OrderTypeSelection.Limit
        "MARKET" -> OrderTypeSelection.Market
        "STOP_LOSS" -> OrderTypeSelection.StopLoss
        "TAKE_PROFIT" -> OrderTypeSelection.TakeProfit
        "STOP_LIMIT" -> OrderTypeSelection.StopLimit
        else -> OrderTypeSelection.Market
    }

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
 * Конвертация stream-модели в [Order.Regular].
 *
 * Stream (gRPC/WS) отдаёт только обычные заявки (без stop).
 *
 * [Order.instrumentType] пуст: `OrderState` не знает типа инструмента;
 * UI-карточке достаточно [Order.orderType] (LIMIT/MARKET).
 */
internal fun OrderState.toOrder(): Order.Regular =
    Order.Regular(
        orderId = orderId,
        ticker = ticker,
        tscalpInstrumentId = tscalpInstrumentId ?: "",
        instrumentType = "",
        direction = direction,
        quantity = quantity,
        orderType = (orderType?.takeIf { it != "UNSPECIFIED" } ?: "LIMIT").toOrderTypeSelection(),
        status = status,
        orderDate = updateTime,
        price = limitPrice ?: executedPrice ?: 0.0,
        executedPrice = executedPrice,
        executedQuantity = executedQuantity,
    )
