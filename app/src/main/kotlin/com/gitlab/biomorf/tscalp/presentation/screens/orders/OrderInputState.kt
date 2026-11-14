package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType

/**
 * Состояние ввода заявки — часть split Form-VM (C2).
 *
 * Источник: `OrderFormState` (13d-A). Содержит quantity / prices /
 * orderType / expiration / pairedMultiplier / pairTradingEnabled.
 * Инструмент — в [OrderInstrumentState]; trade-details — в
 * [OrderTradeDetailsState]; confirm/status — в [OrderExecutionState].
 *
 * Computed-поля (`quantityAsLong` / `pairedQuantity`) — те же, что
 * в `OrderFormState`: парсинг строк на границе, чтобы domain-usecases
 * получали `Long?`.
 */
data class OrderInputState(
    val quantity: String = "1",
    val limitPrice: String = "",
    val stopPrice: String = "",
    val orderType: OrderTypeSelection = OrderTypeSelection.Market,
    val expirationType: StopOrderExpirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
    val expireDate: String? = null,
    val pairTradingEnabled: Boolean = false,
    val pairedMultiplier: String = "10",
) {
    val quantityAsLong: Long? get() = quantity.toLongOrNull()

    /** Парная партия = quantity * pairedMultiplier (обе стороны Long). */
    val pairedQuantity: Long?
        get() =
            quantityAsLong?.let { q ->
                pairedMultiplier.toLongOrNull()?.let { m -> q * m }
            }
}
