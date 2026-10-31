package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Order
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.toOrderTypeSelection
import com.gitlab.biomorf.tscalp.transport.ktor.RestOrder
import com.gitlab.biomorf.tscalp.transport.ktor.RestPosition
import com.gitlab.biomorf.tscalp.transport.ktor.RestQuotation
import com.gitlab.biomorf.tscalp.transport.ktor.RestStopOrder

// ---------- Мапперы REST DTO → Domain Models ----------
// Используются только при fallback через REST.

internal fun RestQuotation?.toDouble(): Double? = this?.let { (units.toLongOrNull() ?: 0L) + nano / 1_000_000_000.0 }

internal fun RestQuotation?.toLong(): Long = this?.units?.toLongOrNull() ?: 0L

internal fun RestOrder.toOrder(): Order.Regular =
    Order.Regular(
        orderId = orderId,
        ticker = ticker,
        tscalpInstrumentId = instrumentUid,
        instrumentType = "",
        direction = direction.removePrefix("ORDER_DIRECTION_"),
        quantity = lotsRequested,
        orderType = orderType.removePrefix("ORDER_TYPE_").toOrderTypeSelection(),
        status = executionReportStatus.removePrefix("EXECUTION_REPORT_STATUS_"),
        orderDate = null,
        price = initialOrderPrice.toDouble() ?: 0.0,
        executedPrice = executedOrderPrice.toDouble(),
        executedQuantity = lotsExecuted,
    )

internal fun RestStopOrder.toOrder(): Order.Stop =
    Order.Stop(
        orderId = stopOrderId,
        ticker = ticker,
        tscalpInstrumentId = instrumentUid,
        instrumentType = "",
        direction = direction.removePrefix("ORDER_DIRECTION_"),
        quantity = lotsRequested,
        orderType = orderType.removePrefix("ORDER_TYPE_").toOrderTypeSelection(),
        status = executionReportStatus.removePrefix("EXECUTION_REPORT_STATUS_"),
        orderDate = null,
        price = limitPrice.toDouble() ?: 0.0,
        stopPrice = stopPrice.toDouble() ?: 0.0,
    )

internal fun RestPosition.toStreamItem(): PositionStreamItem =
    PositionStreamItem(
        tscalpInstrumentId = instrumentUid,
        brokerName = BrokerName.TINVEST,
        ticker = "",
        quantity = quantity.toLong(),
        currentPrice = currentPrice.toDouble() ?: 0.0,
        averagePositionPrice = averagePositionPrice.toDouble(),
        expectedYield = expectedYield.toDouble(),
        classCode = "",
        isin = "",
        pointValue = null,
        instrumentType = instrumentType,
        currency = null,
    )
