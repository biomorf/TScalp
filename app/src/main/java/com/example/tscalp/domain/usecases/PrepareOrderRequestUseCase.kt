package com.example.tscalp.domain.usecases

import com.example.tscalp.domain.models.BrokerOrderRequest
import com.example.tscalp.domain.models.BrokerOrderType
import com.example.tscalp.domain.models.OrderDirection
import com.example.tscalp.domain.models.OrderTypeSelection
import com.example.tscalp.domain.models.StopOrderRequest
import com.example.tscalp.domain.models.StopOrderType
import com.example.tscalp.di.ServiceLocator

class PrepareOrderRequestUseCase(
    private val pairOrderMapper: PairOrderMapper = PairOrderMapper
) {

    data class PreparedOrders(
        val primaryRequest: Any, // BrokerOrderRequest или StopOrderRequest
        val pairedRequest: Any?, // BrokerOrderRequest или StopOrderRequest (null, если парная отключена)
        val isPrimaryStop: Boolean,
        val isPairedStop: Boolean?
    )

    fun prepare(
        brokerName: String,
        ticker: String,
        instrumentUid: String?,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean,
        orderType: OrderTypeSelection,
        limitPrice: String?,
        stopPrice: String?,
        expirationType: com.example.tscalp.domain.models.StopOrderExpirationType,
        pairedInstrumentUid: String?,
        pairedTicker: String?,
        pairedBrokerName: String?,
        pairedAccountId: String?,
        pairedMultiplier: String?
    ): PreparedOrders {
        // Основная заявка
        val primaryRequest: Any = when (orderType) {
            OrderTypeSelection.Market, OrderTypeSelection.Limit -> {
                BrokerOrderRequest(
                    brokerName = brokerName,
                    ticker = ticker,
                    instrumentUid = instrumentUid,
                    quantity = quantity,
                    direction = direction,
                    accountId = accountId,
                    sandboxMode = sandboxMode,
                    type = if (orderType == OrderTypeSelection.Market) BrokerOrderType.MARKET else BrokerOrderType.LIMIT,
                    price = limitPrice?.toDoubleOrNull()
                )
            }
            else -> {
                StopOrderRequest(
                    brokerName = brokerName,
                    ticker = ticker,
                    instrumentUid = instrumentUid,
                    quantity = quantity,
                    direction = direction,
                    accountId = accountId,
                    sandboxMode = sandboxMode,
                    stopPrice = stopPrice?.toDoubleOrNull() ?: return PreparedOrders(
                        BrokerOrderRequest(brokerName, ticker, instrumentUid, quantity, direction, accountId, sandboxMode, BrokerOrderType.MARKET, null),
                        null, true, null
                    ),
                    price = if (orderType == OrderTypeSelection.StopLimit) limitPrice?.toDoubleOrNull() else null,
                    stopOrderType = orderType.stopOrderType ?: StopOrderType.STOP_LOSS,
                    expirationType = expirationType
                )
            }
        }

        val isPrimaryStop = primaryRequest is StopOrderRequest

        // Парная сделка
        if (pairedInstrumentUid == null || pairedTicker == null || pairedBrokerName == null || pairedAccountId == null) {
            return PreparedOrders(primaryRequest, null, isPrimaryStop, null)
        }

        val pairedDirection = if (direction == OrderDirection.BUY) OrderDirection.SELL else OrderDirection.BUY
        val primaryPrice = when (orderType) {
            OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> limitPrice?.toDoubleOrNull()
            OrderTypeSelection.StopLoss, OrderTypeSelection.TakeProfit -> stopPrice?.toDoubleOrNull()
            else -> null
        }
        val pairedSpec = pairOrderMapper.map(orderType, direction, primaryPrice)

        val pairedQuantity = (quantity * (pairedMultiplier?.toDoubleOrNull() ?: 1.0)).toLong()

        val pairedRequest: Any = if (pairedSpec.isStopOrder) {
            StopOrderRequest(
                brokerName = pairedBrokerName,
                ticker = pairedTicker,
                instrumentUid = pairedInstrumentUid,
                quantity = pairedQuantity,
                direction = pairedDirection,
                accountId = pairedAccountId,
                sandboxMode = sandboxMode,
                stopPrice = pairedSpec.stopPrice ?: return PreparedOrders(primaryRequest, null, isPrimaryStop, null),
                price = pairedSpec.price,
                stopOrderType = pairedSpec.stopOrderType ?: StopOrderType.STOP_LOSS,
                expirationType = expirationType
            )
        } else {
            BrokerOrderRequest(
                brokerName = pairedBrokerName,
                ticker = pairedTicker,
                instrumentUid = pairedInstrumentUid,
                quantity = pairedQuantity,
                direction = pairedDirection,
                accountId = pairedAccountId,
                sandboxMode = sandboxMode,
                type = pairedSpec.brokerOrderType ?: BrokerOrderType.MARKET,
                price = pairedSpec.price
            )
        }

        return PreparedOrders(primaryRequest, pairedRequest, isPrimaryStop, pairedSpec.isStopOrder)
    }
}