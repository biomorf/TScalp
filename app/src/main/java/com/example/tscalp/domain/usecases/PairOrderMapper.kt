package com.example.tscalp.domain.usecases

import com.example.tscalp.domain.models.BrokerOrderType
import com.example.tscalp.domain.models.OrderDirection
import com.example.tscalp.domain.models.OrderTypeSelection
import com.example.tscalp.domain.models.StopOrderType

/**
 * Результат маппинга парного ордера.
 */
data class PairedOrderSpec(
    val orderType: OrderTypeSelection,
    val brokerOrderType: BrokerOrderType? = null,
    val stopOrderType: StopOrderType? = null,
    val price: Double?,
    val stopPrice: Double?,
    val isStopOrder: Boolean
)

/**
 * Use case, определяющий тип и параметры парного ордера на основе основного.
 */
object PairOrderMapper {

    fun map(
        primaryType: OrderTypeSelection,
        primaryDirection: OrderDirection,
        primaryPrice: Double?
    ): PairedOrderSpec {
        return when (primaryType) {
            OrderTypeSelection.Limit -> {
                when (primaryDirection) {
                    OrderDirection.BUY -> PairedOrderSpec(
                        orderType = OrderTypeSelection.StopLoss,
                        stopOrderType = StopOrderType.STOP_LOSS,
                        stopPrice = primaryPrice,
                        price = null,
                        isStopOrder = true
                    )
                    OrderDirection.SELL -> PairedOrderSpec(
                        orderType = OrderTypeSelection.TakeProfit,
                        stopOrderType = StopOrderType.TAKE_PROFIT,
                        stopPrice = primaryPrice,
                        price = null,
                        isStopOrder = true
                    )
                }
            }
            // TODO inscpert the logic
//            OrderTypeSelection.StopLoss,
//            OrderTypeSelection.TakeProfit -> {
//                PairedOrderSpec(
//                    orderType = OrderTypeSelection.Limit,
//                    brokerOrderType = BrokerOrderType.LIMIT,
//                    price = primaryPrice,
//                    stopPrice = null,
//                    isStopOrder = false
//                )
//            }
//            OrderTypeSelection.StopLimit -> {
//                PairedOrderSpec(
//                    orderType = OrderTypeSelection.StopLimit,
//                    stopOrderType = StopOrderType.STOP_LIMIT,
//                    stopPrice = primaryPrice,
//                    price = primaryPrice,
//                    isStopOrder = true
//                )
//            }
            OrderTypeSelection.StopLoss,
            OrderTypeSelection.TakeProfit,
            OrderTypeSelection.StopLimit -> {
                // Временно отключены – вернуть позже
                PairedOrderSpec(
                    orderType = OrderTypeSelection.Market,
                    brokerOrderType = BrokerOrderType.MARKET,
                    price = null,
                    stopPrice = null,
                    isStopOrder = false
                )
            }
            OrderTypeSelection.Market -> {
                PairedOrderSpec(
                    orderType = OrderTypeSelection.Market,
                    brokerOrderType = BrokerOrderType.MARKET,
                    price = null,
                    stopPrice = null,
                    isStopOrder = false
                )
            }
        }
    }
}