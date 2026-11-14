package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderRequest
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderInputState
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderInstrumentState

/**
 * Boundary-маппер 4 sub-state (presentation) -> `PlaceOrderRequest`
 * (domain-DTO). Заменил legacy `OrderFormState.toPlaceOrderRequest`
 * (M017) в Phase 5f-a; legacy удалён в Phase 7.1 вместе с
 * `OrderFormStateMapper.kt`.
 *
 * **2c (fixation):** marshalling на границе presentation -> domain,
 * не use case (см. `docs/clean-audit.md` -> M017: обоснование
 * варианта D - boundary-mapper, не use case).
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
