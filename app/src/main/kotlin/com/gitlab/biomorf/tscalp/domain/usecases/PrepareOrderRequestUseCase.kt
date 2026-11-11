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
        val primary =
            when (
                val step =
                    buildPrimaryRequest(
                        instrument = instrument,
                        quantity = quantity,
                        direction = direction,
                        accountId = accountId,
                        orderType = orderType,
                        limitPrice = limitPrice,
                        stopPrice = stopPrice,
                        expirationType = expirationType,
                    )
            ) {
                is Step.Invalid -> return PrepareResult.Invalid(step.reason)
                is Step.Ok -> step.request
            }

        if (pairedInstrument == null || pairedAccountId == null) {
            return PrepareResult.Prepared(primary, null)
        }

        return when (
            val step =
                buildPairedRequest(
                    quantity = quantity,
                    direction = direction,
                    orderType = orderType,
                    limitPrice = limitPrice,
                    stopPrice = stopPrice,
                    expirationType = expirationType,
                    pairedInstrument = pairedInstrument,
                    pairedAccountId = pairedAccountId,
                    pairedMultiplier = pairedMultiplier,
                )
        ) {
            is Step.Invalid -> PrepareResult.Invalid(step.reason)
            is Step.Ok -> PrepareResult.Prepared(primary, step.request)
        }
    }

    private fun buildPrimaryRequest(
        instrument: Instrument,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        orderType: OrderTypeSelection,
        limitPrice: String?,
        stopPrice: String?,
        expirationType: StopOrderExpirationType,
    ): Step =
        when (orderType) {
            OrderTypeSelection.Market, OrderTypeSelection.Limit ->
                Step.Ok(
                    OrderRequest.Broker(
                        BrokerOrderRequest(
                            instrument = instrument,
                            quantity = quantity,
                            direction = direction,
                            accountId = accountId,
                            type = if (orderType == OrderTypeSelection.Market) BrokerOrderType.MARKET else BrokerOrderType.LIMIT,
                            price = limitPrice?.toDoubleOrNull(),
                        ),
                    ),
                )
            else -> {
                val parsedStop =
                    stopPrice?.toDoubleOrNull()?.takeIf { it > 0.0 }
                        ?: return Step.Invalid("stopPrice required for ${orderTypeName(orderType)}")
                Step.Ok(
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
                    ),
                )
            }
        }

    private fun buildPairedRequest(
        quantity: Long,
        direction: OrderDirection,
        orderType: OrderTypeSelection,
        limitPrice: String?,
        stopPrice: String?,
        expirationType: StopOrderExpirationType,
        pairedInstrument: Instrument,
        pairedAccountId: String,
        pairedMultiplier: String?,
    ): Step {
        val pairedDirection = if (direction == OrderDirection.BUY) OrderDirection.SELL else OrderDirection.BUY
        val pairedSpec = pairOrderMapper.map(orderType, direction, primaryPriceForPair(orderType, limitPrice, stopPrice))
        val pairedQuantity = (quantity * (pairedMultiplier?.toDoubleOrNull() ?: 1.0)).toLong()

        return if (pairedSpec.isStopOrder) {
            val parsedPairedStop =
                pairedSpec.stopPrice
                    ?: return Step.Invalid("paired stopPrice required")
            Step.Ok(
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
                ),
            )
        } else {
            Step.Ok(
                OrderRequest.Broker(
                    BrokerOrderRequest(
                        instrument = pairedInstrument,
                        quantity = pairedQuantity,
                        direction = pairedDirection,
                        accountId = pairedAccountId,
                        type = pairedSpec.brokerOrderType ?: BrokerOrderType.MARKET,
                        price = pairedSpec.price,
                    ),
                ),
            )
        }
    }

    private fun primaryPriceForPair(
        orderType: OrderTypeSelection,
        limitPrice: String?,
        stopPrice: String?,
    ): Double? =
        when (orderType) {
            OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> limitPrice?.toDoubleOrNull()
            OrderTypeSelection.StopLoss, OrderTypeSelection.TakeProfit -> stopPrice?.toDoubleOrNull()
            else -> null
        }

    private fun orderTypeName(orderType: OrderTypeSelection): String =
        when (orderType) {
            OrderTypeSelection.Market -> "Market"
            OrderTypeSelection.Limit -> "Limit"
            OrderTypeSelection.StopLoss -> "StopLoss"
            OrderTypeSelection.TakeProfit -> "TakeProfit"
            OrderTypeSelection.StopLimit -> "StopLimit"
        }

    /** Внутренний шаг построения одной заявки: результат или причина отказа. */
    private sealed interface Step {
        data class Ok(
            val request: OrderRequest,
        ) : Step

        data class Invalid(
            val reason: String,
        ) : Step
    }
}
