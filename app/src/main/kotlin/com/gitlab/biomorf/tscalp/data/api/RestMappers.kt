package com.gitlab.biomorf.tscalp.data.api

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.OrderListItem
import com.gitlab.biomorf.tscalp.domain.models.PortfolioPosition
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.transport.ktor.RestOrder
import com.gitlab.biomorf.tscalp.transport.ktor.RestPosition
import com.gitlab.biomorf.tscalp.transport.ktor.RestQuotation
import com.gitlab.biomorf.tscalp.transport.ktor.RestStopOrder

// ---------- Мапперы REST DTO → Domain Models ----------
// Используются только при fallback через REST.

internal fun RestQuotation?.toDouble(): Double? = this?.let { (units.toLongOrNull() ?: 0L) + nano / 1_000_000_000.0 }

internal fun RestQuotation?.toLong(): Long = this?.units?.toLongOrNull() ?: 0L

internal fun RestOrder.toOrderListItem(): OrderListItem =
    OrderListItem(
        orderId = orderId,
        ticker = ticker,
        tscalpInstrumentId = instrumentUid,
        direction = direction.removePrefix("ORDER_DIRECTION_"),
        price = initialOrderPrice.toDouble() ?: 0.0,
        stopPrice = null,
        quantity = lotsRequested,
        type = orderType.removePrefix("ORDER_TYPE_"),
        status = executionReportStatus.removePrefix("EXECUTION_REPORT_STATUS_"),
        orderDate = null,
        isStopOrder = false,
    )

internal fun RestStopOrder.toOrderListItem(): OrderListItem =
    OrderListItem(
        orderId = stopOrderId,
        ticker = ticker,
        tscalpInstrumentId = instrumentUid,
        direction = direction.removePrefix("ORDER_DIRECTION_"),
        price = stopPrice.toDouble() ?: 0.0,
        stopPrice = stopPrice.toDouble(),
        quantity = lotsRequested,
        type = orderType.removePrefix("ORDER_TYPE_"),
        status = executionReportStatus.removePrefix("EXECUTION_REPORT_STATUS_"),
        orderDate = null,
        isStopOrder = true,
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

internal fun RestPosition.toPortfolioPosition(): PortfolioPosition {
    val qty = quantity.toLong()
    val curPrice = currentPrice.toDouble() ?: 0.0
    val avgPrice = averagePositionPrice.toDouble()
    val profit = expectedYield.toDouble()
    val profitPercent =
        if (profit != null && avgPrice != null && avgPrice > 0.0 && qty > 0) {
            (profit / (avgPrice * qty)) * 100.0
        } else {
            null
        }

    return PortfolioPosition(
        tscalpInstrumentId = instrumentUid,
        brokerName = BrokerName.TINVEST,
        name = "",
        ticker = "",
        classCode = "",
        isin = "",
        quantity = qty,
        currentPrice = curPrice,
        averagePrice = avgPrice,
        totalValue = curPrice * qty,
        profit = profit,
        profitPercent = profitPercent,
        instrumentType = instrumentType,
        pointValue = null,
        currency = null,
    )
}
