package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.usecases.PairedOutcome
import com.gitlab.biomorf.tscalp.domain.usecases.PrimaryOutcome
import com.gitlab.biomorf.tscalp.domain.usecases.TradeDetails
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderFormState

// Formatting for trade details and post-order success messages —
// extracted from `OrderFormViewModel` (M001, clean-audit: VM built
// presentation strings, M1).
//
// Pure functions on domain + presentation-state parameters; no VM,
// no Compose, no coroutines.

/**
 * Строка-подтверждение успешного поста заявки (primary + опциональная
 * paired-сделка). Ранее `private fun formatPostOrderSuccess` в
 * `OrderFormViewModel`.
 */
internal fun formatPostOrderSuccess(
    primary: PrimaryOutcome,
    paired: PairedOutcome?,
): String {
    val primaryText =
        when (primary) {
            is PrimaryOutcome.Regular ->
                "✅ Заявка выполнена!\nID: ${primary.orderId}\nИсполнено: ${primary.executedLots}/${primary.totalLots} лотов"
            is PrimaryOutcome.Stop -> "✅ Стоп‑заявка выставлена, ID: ${primary.orderId.take(8)}…"
        }
    val pairedText =
        paired?.let { p ->
            when (p) {
                is PairedOutcome.Regular ->
                    "\n✅ Контрсделка: ${p.instrument.ticker} ${p.quantity} лотов, ID: ${p.orderId}"
                is PairedOutcome.Stop ->
                    "\n✅ Контрсделка: ${p.instrument.ticker} ${p.quantity} лотов, ID: ${p.orderId.take(8)}…"
                is PairedOutcome.Failed -> "\n❌ Ошибка контрсделки: ${p.message}"
            }
        } ?: ""
    return primaryText + pairedText
}

/**
 * Overlay-строка стоимости основного слота (пункты + domestic, или
 * валюта, или `null`). Ранее `private fun formatCostOverlay` в
 * `OrderFormViewModel`.
 */
internal fun formatCostOverlay(
    details: TradeDetails,
    state: OrderFormState,
): String? {
    val instrumentType = state.selectedInstrument?.instrumentType ?: InstrumentType.OTHER
    val currency = state.selectedInstrument?.currency ?: DOMESTIC_CURRENCY
    return when {
        details.costPoints != null && details.costDomesticCurrency != null ->
            "${formatPrice(details.costPoints, instrumentType, currency)}  ·  ${formatDomesticCurrency(details.costDomesticCurrency)}"
        details.costCurrency != null -> formatCurrency(details.costCurrency, currency)
        else -> null
    }
}

/**
 * Overlay-строка множителя парного слота (аналог [formatCostOverlay] для
 * `multiplierPoints`). Ранее `private fun formatMultiplierOverlay` в
 * `OrderFormViewModel`.
 */
internal fun formatMultiplierOverlay(
    details: TradeDetails,
    state: OrderFormState,
): String? {
    val pairedInstrumentType = state.pairedInstrument?.instrumentType ?: InstrumentType.OTHER
    val currency = state.pairedInstrument?.currency ?: DOMESTIC_CURRENCY
    return when {
        details.multiplierPoints != null && details.multiplierDomesticCurrency != null ->
            "${formatPrice(details.multiplierPoints, pairedInstrumentType, currency)}  ·  ${formatDomesticCurrency(details.multiplierDomesticCurrency)}"
        details.multiplierCurrency != null -> formatCurrency(details.multiplierCurrency, currency)
        else -> null
    }
}
