package com.example.tscalp.domain.models

sealed class OrderTypeSelection {
    object Market : OrderTypeSelection()
    object Limit : OrderTypeSelection()
    object StopLoss : OrderTypeSelection()
    object TakeProfit : OrderTypeSelection()
    object StopLimit : OrderTypeSelection()

    /** Преобразует выбранный тип в StopOrderType, если это стоп-заявка */
    val stopOrderType: StopOrderType?
        get() = when (this) {
            is StopLoss -> StopOrderType.STOP_LOSS
            is TakeProfit -> StopOrderType.TAKE_PROFIT
            is StopLimit -> StopOrderType.STOP_LIMIT
            else -> null
        }

    /** Является ли тип обычной заявкой (Market/Limit) */
    val isRegular: Boolean
        get() = this is Market || this is Limit
}