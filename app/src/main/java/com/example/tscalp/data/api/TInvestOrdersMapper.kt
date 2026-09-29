package com.example.tscalp.data.api

import com.google.protobuf.Timestamp
import com.google.protobuf.Descriptors.EnumValueDescriptor
import com.example.tscalp.domain.models.OrderListItem
import ru.tinkoff.piapi.contract.v1.MoneyValue
import ru.tinkoff.piapi.contract.v1.OrderState
import ru.tinkoff.piapi.contract.v1.OrderType
import ru.tinkoff.piapi.contract.v1.Quotation
import ru.tinkoff.piapi.contract.v1.StopOrder

/**
 * Protobuf Order/StopOrder → доменный OrderListItem.
 */
object TInvestOrdersMapper {

    /**
     * Обычная заявка (LIMIT / MARKET).
     */
    fun mapOrder(order: OrderState): OrderListItem {
        val uidField = order.descriptorForType.findFieldByName("instrument_uid")
        val uid = uidField?.let { order.getField(it) as? String } ?: order.figi

        val tickerField = order.descriptorForType.findFieldByName("ticker")
        val ticker = tickerField?.let { order.getField(it) } as? String ?: uid

        val classCodeField = order.descriptorForType.findFieldByName("class_code")
        val classCode = classCodeField?.let { order.getField(it) } as? String ?: ""
        val instrumentType = TInvestConverters.classCodeToInstrumentType(classCode)

        val orderType = when (order.orderType) {
            OrderType.ORDER_TYPE_LIMIT -> "LIMIT"
            OrderType.ORDER_TYPE_MARKET -> "MARKET"
            else -> "UNKNOWN"
        }

        val direction = when (order.directionValue) {
            1 -> "BUY"
            2 -> "SELL"
            else -> "UNKNOWN"
        }

        val statusField = order.descriptorForType.findFieldByName("execution_report_status")
        val statusStr: String = if (statusField != null) {
            val rawStatus = order.getField(statusField)
            if (rawStatus is EnumValueDescriptor) {
                rawStatus.name.removePrefix("EXECUTION_REPORT_STATUS_")
            } else "UNKNOWN"
        } else "UNKNOWN"

        val orderIdStr: String = order.orderId ?: ""

        val priceField = order.descriptorForType.findFieldByName("initial_order_price")
            ?: order.descriptorForType.findFieldByName("price")
        val priceValue = priceField?.let { order.getField(it) }
        val priceDouble = when (priceValue) {
            is MoneyValue -> TInvestConverters.moneyToDouble(priceValue) ?: 0.0
            is Quotation -> TInvestConverters.quotationToDouble(priceValue) ?: 0.0
            else -> 0.0
        }

        val dateField = order.descriptorForType.findFieldByName("create_date")
        val dateValue = dateField?.let { order.getField(it) }
        val orderDateLong = (dateValue as? Timestamp)?.seconds

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
        val uidField = order.descriptorForType.findFieldByName("instrument_uid")
        val uid = uidField?.let { order.getField(it) } as? String ?: order.figi

        val tickerField = order.descriptorForType.findFieldByName("ticker")
        val ticker = tickerField?.let { order.getField(it) } as? String ?: uid

        val classCodeField = order.descriptorForType.findFieldByName("class_code")
        val classCode = classCodeField?.let { order.getField(it) } as? String ?: ""
        val instrumentType = TInvestConverters.classCodeToInstrumentType(classCode)

        val fieldDescriptor = order.descriptorForType.findFieldByName("order_type")
        val type = if (fieldDescriptor != null) {
            val enumValue = order.getField(fieldDescriptor) as? EnumValueDescriptor
            enumValue?.name?.removePrefix("STOP_ORDER_TYPE_") ?: "UNKNOWN"
        } else "UNKNOWN"

        val orderIdStr: String = order.stopOrderId ?: ""

        val directionStr: String = order.direction.name.removePrefix("STOP_ORDER_DIRECTION_")
        val statusStr: String = order.status.name.removePrefix("STOP_ORDER_STATUS_")

        val stopPriceDouble = TInvestConverters.moneyToDouble(order.stopPrice) ?: 0.0

        val orderDateLong = order.getCreateDate()?.seconds

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