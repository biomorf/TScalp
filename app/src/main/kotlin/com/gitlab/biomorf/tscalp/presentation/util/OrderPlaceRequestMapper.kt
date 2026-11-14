package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderRequest
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderInputState
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderInstrumentState

/**
 * Boundary-маппер 4 sub-state (presentation) -> `PlaceOrderRequest`
 * (domain-DTO). Параллель [toPlaceOrderRequest] для `OrderFormState`
 * (M017), но на sub-state C2 (PR-4b, Phase 5f-a).
 *
 * **2d (временное решение, 2026-11-14):** две функции сосуществуют —
 * [toPlaceRequest] для sub-state (используется в `OrdersScreen` после
 * Phase 5f-b), `OrderFormState.toPlaceOrderRequest` для Coord (живёт до
 * Phase 7). Удаление legacy - Phase 7 вместе с `OrderFormStateMapper.kt`.
 *
 * **2c (fixation):** marshalling на границе presentation -> domain,
 * не use case (обоснование - `OrderFormStateMapper.kt` KDoc).
 *
 * @return `PlaceOrderRequest?` - `null`, если не хватает обязательных
 *   sub-state полей: `selectedInstrument`, `quantity`, `pairedMultiplier`.
 */
fun toPlaceRequest(
    instrumentState: OrderInstrumentState,
    inputState: OrderInputState,
): PlaceOrderRequest? {
    val instrument = instrumentState.selectedInstrument ?: return null
    val quantity = inputState.quantityAsLong ?: return null
    val pairedMultiplierLong = inputState.pairedMultiplier.toLongOrNull() ?: return null
    return PlaceOrderRequest(
        instrument = instrument,
        selectedAccountId = instrumentState.selectedAccountId,
        quantity = quantity,
        orderType = inputState.orderType,
        limitPrice = inputState.limitPrice,
        stopPrice = inputState.stopPrice,
        expirationType = inputState.expirationType,
        pairedInstrument = instrumentState.pairedInstrument,
        pairedAccountId = instrumentState.pairedAccountId,
        pairedMultiplier = pairedMultiplierLong,
    )
}
