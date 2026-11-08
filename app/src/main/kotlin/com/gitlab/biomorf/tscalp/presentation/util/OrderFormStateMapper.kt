package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderRequest
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderFormState

/**
 * Boundary-маппер `OrderFormState` (presentation) → `PlaceOrderRequest`
 * (domain-DTO). Вынесено из `OrderFormViewModel.buildPlaceOrderRequest`
 * (M017, вариант D).
 *
 * **Почему не use case.** Конвертация «валидированные примитивы Form →
 * domain-DTO» — это marshalling на границе presentation → domain, а не
 * пользовательский сценарий. Use case без бизнес-правила (только
 * перекладывание 10 полей) нарушает принцип «use case — сценарий, не
 * маппер». Место маппера — `presentation/util/` (рядом с
 * `InstrumentFormat`, `PositionFormatter`).
 *
 * **2c-фиксация (M017).** Чисто: VM не конструирует domain-DTO;
 * конструирование — boundary-маппер. Альтернатива `BuildPlaceOrderRequestUseCase`
 * (10 параметров на входе) отклонена: не делает работу use case.
 *
 * @return `PlaceOrderRequest?` — `null`, если не хватает обязательных
 *   Form-полей: `ticker` (или `selectedInstrument.ticker`), `quantity`,
 *   `tscalpInstrumentId`.
 */
fun OrderFormState.toPlaceOrderRequest(): PlaceOrderRequest? {
    val instrument = selectedInstrument ?: return null
    val quantity = quantityAsLong ?: return null
    return PlaceOrderRequest(
        instrument = instrument,
        quantity = quantity,
        orderType = orderType,
        limitPrice = limitPrice,
        stopPrice = stopPrice,
        expirationType = expirationType,
        pairedInstrument = pairedInstrument,
        pairedMultiplier = pairedMultiplier,
    )
}
