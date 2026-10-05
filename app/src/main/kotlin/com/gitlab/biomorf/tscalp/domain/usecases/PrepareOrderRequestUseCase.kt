package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderType
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.StopOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.StopOrderType
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType

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
        brokerName: BrokerName,
        ticker: String,
        instrumentUid: String?,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean,
        orderType: OrderTypeSelection,
        limitPrice: String?,
        stopPrice: String?,
        expirationType: com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType,
        pairedInstrumentUid: String?,
        pairedTicker: String?,
        pairedBrokerName: BrokerName?,
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
                        primaryRequest = BrokerOrderRequest(
                            brokerName = brokerName,
                            ticker = ticker,
                            instrumentUid = instrumentUid,
                            quantity = quantity,
                            direction = direction,
                            accountId = accountId,
                            sandboxMode = sandboxMode,
                            type = BrokerOrderType.MARKET,
                            price = null
                        ),
                        pairedRequest = null,
                        isPrimaryStop = true,
                        isPairedStop = null
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