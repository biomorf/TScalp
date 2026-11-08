package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderType
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType
import com.gitlab.biomorf.tscalp.domain.models.StopOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.StopOrderType

class PrepareOrderRequestUseCase(
    private val pairOrderMapper: PairOrderMapper = PairOrderMapper,
) {
    data class PreparedOrders(
        val primaryRequest: Any, // BrokerOrderRequest или StopOrderRequest
        val pairedRequest: Any?, // BrokerOrderRequest или StopOrderRequest (null, если парная отключена)
        val isPrimaryStop: Boolean,
        val isPairedStop: Boolean?,
    )

    // Архитектурный долг: функция требует декомпозиции.
    // См. ROADMAP → «Рефакторинг UseCase-слоя».
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun prepare(
        instrument: Instrument,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        orderType: OrderTypeSelection,
        limitPrice: String?,
        stopPrice: String?,
        expirationType: StopOrderExpirationType,
        pairedInstrument: Instrument?,
        pairedAccountId: String?,
        pairedMultiplier: String?,
    ): PreparedOrders {
        // Основная заявка
        val primaryRequest: Any =
            when (orderType) {
                OrderTypeSelection.Market, OrderTypeSelection.Limit -> {
                    BrokerOrderRequest(
                        instrument = instrument,
                        quantity = quantity,
                        direction = direction,
                        accountId = accountId,
                        type = if (orderType == OrderTypeSelection.Market) BrokerOrderType.MARKET else BrokerOrderType.LIMIT,
                        price = limitPrice?.toDoubleOrNull(),
                    )
                }
                else -> {
                    StopOrderRequest(
                        instrument = instrument,
                        quantity = quantity,
                        direction = direction,
                        accountId = accountId,
                        stopPrice =
                            stopPrice?.toDoubleOrNull() ?: return PreparedOrders(
                                primaryRequest =
                                    BrokerOrderRequest(
                                        instrument = instrument,
                                        quantity = quantity,
                                        direction = direction,
                                        accountId = accountId,
                                        type = BrokerOrderType.MARKET,
                                        price = null,
                                    ),
                                pairedRequest = null,
                                isPrimaryStop = true,
                                isPairedStop = null,
                            ),
                        price = if (orderType == OrderTypeSelection.StopLimit) limitPrice?.toDoubleOrNull() else null,
                        stopOrderType = orderType.stopOrderType ?: StopOrderType.STOP_LOSS,
                        expirationType = expirationType,
                    )
                }
            }

        val isPrimaryStop = primaryRequest is StopOrderRequest

        // Парная сделка
        if (pairedInstrument == null || pairedAccountId == null) {
            return PreparedOrders(primaryRequest, null, isPrimaryStop, null)
        }

        val pairedDirection = if (direction == OrderDirection.BUY) OrderDirection.SELL else OrderDirection.BUY
        val primaryPrice =
            when (orderType) {
                OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> limitPrice?.toDoubleOrNull()
                OrderTypeSelection.StopLoss, OrderTypeSelection.TakeProfit -> stopPrice?.toDoubleOrNull()
                else -> null
            }
        val pairedSpec = pairOrderMapper.map(orderType, direction, primaryPrice)

        val pairedQuantity = (quantity * (pairedMultiplier?.toDoubleOrNull() ?: 1.0)).toLong()

        val pairedRequest: Any =
            if (pairedSpec.isStopOrder) {
                StopOrderRequest(
                    instrument = pairedInstrument,
                    quantity = pairedQuantity,
                    direction = pairedDirection,
                    accountId = pairedAccountId,
                    stopPrice = pairedSpec.stopPrice ?: return PreparedOrders(primaryRequest, null, isPrimaryStop, null),
                    price = pairedSpec.price,
                    stopOrderType = pairedSpec.stopOrderType ?: StopOrderType.STOP_LOSS,
                    expirationType = expirationType,
                )
            } else {
                BrokerOrderRequest(
                    instrument = pairedInstrument,
                    quantity = pairedQuantity,
                    direction = pairedDirection,
                    accountId = pairedAccountId,
                    type = pairedSpec.brokerOrderType ?: BrokerOrderType.MARKET,
                    price = pairedSpec.price,
                )
            }

        return PreparedOrders(primaryRequest, pairedRequest, isPrimaryStop, pairedSpec.isStopOrder)
    }
}
