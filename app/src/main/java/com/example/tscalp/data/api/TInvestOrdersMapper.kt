package com.example.tscalp.data.api

import com.example.tscalp.domain.models.OrderListItem
import ru.tinkoff.piapi.contract.v1.OrderState
import ru.tinkoff.piapi.contract.v1.OrderType
import ru.tinkoff.piapi.contract.v1.StopOrder

/**
 * Protobuf OrderState/StopOrder → доменный OrderListItem.
 *
 * Использует прямые геттеры proto-классов вместо рефлексии по дескрипторам.
 */
object TInvestOrdersMapper {

    /**
     * Обычная заявка (LIMIT / MARKET).
     *
     * В текущей версии SDK ordersList возвращает OrderState, не Order.
     */
    fun mapOrder(order: OrderState): OrderListItem {
        val uid = order.instrumentUid
        val ticker = order.ticker
        val classCode = order.classCode
        val instrumentType = TInvestConverters.classCodeToInstrumentType(classCode)

        val orderType = when (order.orderType) {
            OrderType.ORDER_TYPE_LIMIT -> "LIMIT"
            OrderType.ORDER_TYPE_MARKET -> "MARKET"
            else -> "UNKNOWN"
        }

        val direction = order.direction.name.removePrefix("ORDER_DIRECTION_")

        val statusStr = order.executionReportStatus.name
            .removePrefix("EXECUTION_REPORT_STATUS_")

        val orderIdStr: String = order.orderId

        val priceDouble = TInvestConverters.moneyToDouble(order.initialOrderPrice) ?: 0.0

        val orderDateLong = order.orderDate.seconds

        return OrderListItem(
            orderId = orderIdStr,
            ticker = ticker,
            tscalpInstrumentId = uid,
            instrumentType = instrumentType,
            direction = direction,
            price = priceDouble,
            stopPrice = null,
            quantity = order.lotsRequested,
            type = orderType,
            status = statusStr,
            orderDate = orderDateLong,
            isStopOrder = false
        )
    }

    /**
     * Стоп-заявка (STOP_LOSS / TAKE_PROFIT / STOP_LIMIT).
     */
    fun mapStopOrder(order: StopOrder): OrderListItem {
        val uid = order.instrumentUid
        val ticker = order.ticker
        val classCode = order.classCode
        val instrumentType = TInvestConverters.classCodeToInstrumentType(classCode)

        val type = order.orderType.name.removePrefix("STOP_ORDER_TYPE_")
        val directionStr: String = order.direction.name.removePrefix("STOP_ORDER_DIRECTION_")
        val statusStr: String = order.status.name.removePrefix("STOP_ORDER_STATUS_")

        val stopPriceDouble = TInvestConverters.moneyToDouble(order.stopPrice) ?: 0.0
        val orderDateLong = order.createDate.seconds

        val orderIdStr: String = order.stopOrderId

        return OrderListItem(
            orderId = orderIdStr,
            ticker = ticker,
            tscalpInstrumentId = uid,
            direction = directionStr,
            price = stopPriceDouble,
            stopPrice = stopPriceDouble,
            quantity = order.lotsRequested,
            type = type,
            status = statusStr,
            orderDate = orderDateLong,
            isStopOrder = true,
            instrumentType = instrumentType
        )
    }
}
