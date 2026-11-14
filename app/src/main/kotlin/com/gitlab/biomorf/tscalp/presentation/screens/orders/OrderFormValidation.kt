package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection

/**
 * Cross-state валидация формы заявки.
 *
 * **2c (fixation, C2):** `isFormValid` — pure top-level extension
 * на трёх state (instrument + input + `isApiInitialized` из Coord).
 * В Coord не выносим — Coord знает только про accounts/lifecycle,
 * не про form-поля. В одном из sub-VM не выносим — валидация
 * читает поля трёх state, любой sub-VM стал бы cross-state.
 * UI вызывает: `isFormValid(instrument, input, coord.isApiInitialized)`.
 *
 * Логика 1:1 с `OrderFormState.isFormValid(isApiInitialized)`.
 */
fun isFormValid(
    instrument: OrderInstrumentState,
    input: OrderInputState,
    isApiInitialized: Boolean,
): Boolean =
    isBasicValid(instrument, input, isApiInitialized) &&
        isLimitPriceValid(input) &&
        isStopPriceValid(input) &&
        isPairValid(instrument, input)

private fun isBasicValid(
    instrument: OrderInstrumentState,
    input: OrderInputState,
    isApiInitialized: Boolean,
): Boolean =
    instrument.selectedInstrument != null &&
        input.quantity.toLongOrNull()?.let { it > 0 } == true &&
        isApiInitialized

private fun isLimitPriceValid(input: OrderInputState): Boolean =
    when (input.orderType) {
        is OrderTypeSelection.Limit, is OrderTypeSelection.StopLimit ->
            input.limitPrice.toDoubleOrNull() != null
        else -> true
    }

private fun isStopPriceValid(input: OrderInputState): Boolean =
    when (input.orderType) {
        is OrderTypeSelection.StopLoss,
        is OrderTypeSelection.TakeProfit,
        is OrderTypeSelection.StopLimit,
        ->
            input.stopPrice.toDoubleOrNull()?.let { it > 0 } == true
        else -> true
    }

private fun isPairValid(
    instrument: OrderInstrumentState,
    input: OrderInputState,
): Boolean =
    if (input.pairTradingEnabled) {
        instrument.pairedInstrument != null &&
            input.pairedMultiplier.toLongOrNull()?.let { it > 0L } == true
    } else {
        true
    }
