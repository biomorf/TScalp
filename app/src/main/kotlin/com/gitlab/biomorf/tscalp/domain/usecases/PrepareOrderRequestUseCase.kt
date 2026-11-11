package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderType
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderRequest
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType
import com.gitlab.biomorf.tscalp.domain.models.StopOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.StopOrderType

class PrepareOrderRequestUseCase(
    private val pairOrderMapper: PairOrderMapper = PairOrderMapper,
) {
    /**
     * Результат подготовки заявок: либо пара готовых [OrderRequest],
     * либо причина отказа (валидационная ошибка до API).
     */
    sealed interface PrepareResult {
        data class Prepared(
            val primary: OrderRequest,
            val paired: OrderRequest?,
        ) : PrepareResult

        data class Invalid(
            val reason: String,
        ) : PrepareResult
    }

    // Архитектурный долг: функция требует декомпозиции.
    // См. ROADMAP → «PrepareOrderRequestUseCase: sealed OrderRequest + decompose (B1+B2)».
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
    ): PrepareResult {
        val primaryRequest: OrderRequest =
            when (orderType) {
                OrderTypeSelection.Market, OrderTypeSelection.Limit -> {
                    OrderRequest.Broker(
                        BrokerOrderRequest(
                            instrument = instrument,
                            quantity = quantity,
                            direction = direction,
                            accountId = accountId,
                            type = if (orderType == OrderTypeSelection.Market) BrokerOrderType.MARKET else BrokerOrderType.LIMIT,
                            price = limitPrice?.toDoubleOrNull(),
                        ),
                    )
                }
                else -> {
                    val parsedStop =
                        stopPrice?.toDoubleOrNull()?.takeIf { it > 0.0 }
                            ?: return PrepareResult.Invalid("stopPrice required for ${orderTypeName(orderType)}")
                    OrderRequest.Stop(
                        StopOrderRequest(
                            instrument = instrument,
                            quantity = quantity,
                            direction = direction,
                            accountId = accountId,
                            stopPrice = parsedStop,
                            price = if (orderType == OrderTypeSelection.StopLimit) limitPrice?.toDoubleOrNull() else null,
                            stopOrderType = orderType.stopOrderType ?: StopOrderType.STOP_LOSS,
                            expirationType = expirationType,
                        ),
                    )
                }
            }

        if (pairedInstrument == null || pairedAccountId == null) {
            return PrepareResult.Prepared(primaryRequest, null)
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

        val pairedRequest: OrderRequest =
            if (pairedSpec.isStopOrder) {
                val parsedPairedStop =
                    pairedSpec.stopPrice
                        ?: return PrepareResult.Invalid("paired stopPrice required")
                OrderRequest.Stop(
                    StopOrderRequest(
                        instrument = pairedInstrument,
                        quantity = pairedQuantity,
                        direction = pairedDirection,
                        accountId = pairedAccountId,
                        stopPrice = parsedPairedStop,
                        price = pairedSpec.price,
                        stopOrderType = pairedSpec.stopOrderType ?: StopOrderType.STOP_LOSS,
                        expirationType = expirationType,
                    ),
                )
            } else {
                OrderRequest.Broker(
                    BrokerOrderRequest(
                        instrument = pairedInstrument,
                        quantity = pairedQuantity,
                        direction = pairedDirection,
                        accountId = pairedAccountId,
                        type = pairedSpec.brokerOrderType ?: BrokerOrderType.MARKET,
                        price = pairedSpec.price,
                    ),
                )
            }

        return PrepareResult.Prepared(primaryRequest, pairedRequest)
    }

    private fun orderTypeName(orderType: OrderTypeSelection): String =
        when (orderType) {
            OrderTypeSelection.Market -> "Market"
            OrderTypeSelection.Limit -> "Limit"
            OrderTypeSelection.StopLoss -> "StopLoss"
            OrderTypeSelection.TakeProfit -> "TakeProfit"
            OrderTypeSelection.StopLimit -> "StopLimit"
        }
}
