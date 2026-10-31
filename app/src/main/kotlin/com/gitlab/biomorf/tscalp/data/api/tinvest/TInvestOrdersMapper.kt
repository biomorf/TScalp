package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.models.Order
import com.gitlab.biomorf.tscalp.domain.models.toOrderTypeSelection
import ru.tinkoff.piapi.contract.v1.OrderState
import ru.tinkoff.piapi.contract.v1.StopOrder

/**
 * Protobuf OrderState/StopOrder → domain Order.
 *
 * Использует прямые геттеры proto-классов вместо рефлексии по дескрипторам.
 */
object TInvestOrdersMapper {
    /**
     * Обычная заявка (LIMIT / MARKET).
     *
     * В текущей версии SDK ordersList возвращает OrderState, не Order.
     */
    fun mapOrder(order: OrderState): Order.Regular {
        val instrumentType = TInvestConverters.classCodeToInstrumentType(order.classCode)
        val direction = order.direction.name.removePrefix("ORDER_DIRECTION_")
        val statusStr =
            order.executionReportStatus.name
                .removePrefix("EXECUTION_REPORT_STATUS_")
        val priceDouble = TInvestConverters.moneyToDouble(order.initialOrderPrice) ?: 0.0
        val executedPriceDouble = TInvestConverters.moneyToDouble(order.executedOrderPrice)

        return Order.Regular(
            orderId = order.orderId,
            ticker = order.ticker,
            tscalpInstrumentId = order.instrumentUid,
            instrumentType = instrumentType,
            direction = direction,
            quantity = order.lotsRequested,
            orderType =
                order.orderType.name
                    .removePrefix("ORDER_TYPE_")
                    .toOrderTypeSelection(),
            status = statusStr,
            orderDate = order.orderDate.seconds,
            price = priceDouble,
            executedPrice = executedPriceDouble,
            executedQuantity = order.lotsExecuted,
        )
    }

    /**
     * Стоп-заявка (STOP_LOSS / TAKE_PROFIT / STOP_LIMIT).
     */
    fun mapStopOrder(order: StopOrder): Order.Stop {
        val instrumentType = TInvestConverters.classCodeToInstrumentType(order.classCode)
        val directionStr = order.direction.name.removePrefix("STOP_ORDER_DIRECTION_")
        val statusStr = order.status.name.removePrefix("STOP_ORDER_STATUS_")
        val priceDouble = TInvestConverters.moneyToDouble(order.price) ?: 0.0
        val stopPriceDouble = TInvestConverters.moneyToDouble(order.stopPrice) ?: 0.0

        return Order.Stop(
            orderId = order.stopOrderId,
            ticker = order.ticker,
            tscalpInstrumentId = order.instrumentUid,
            instrumentType = instrumentType,
            direction = directionStr,
            quantity = order.lotsRequested,
            orderType =
                order.orderType.name
                    .removePrefix("STOP_ORDER_TYPE_")
                    .toOrderTypeSelection(),
            status = statusStr,
            orderDate = order.createDate.seconds,
            price = priceDouble,
            stopPrice = stopPriceDouble,
        )
    }
}
