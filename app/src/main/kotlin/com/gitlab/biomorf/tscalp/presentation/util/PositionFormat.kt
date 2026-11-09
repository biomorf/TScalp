package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.models.BondPosition
import com.gitlab.biomorf.tscalp.domain.models.CurrencyPosition
import com.gitlab.biomorf.tscalp.domain.models.EtfPosition
import com.gitlab.biomorf.tscalp.domain.models.FuturePosition
import com.gitlab.biomorf.tscalp.domain.models.OptionPosition
import com.gitlab.biomorf.tscalp.domain.models.OtherPosition
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.SelectedInstrumentInfo
import com.gitlab.biomorf.tscalp.domain.models.SharePosition

/**
 * 2c-фиксация (M1): UI применяет per-card [SelectedInstrumentInfo]
 * к domain [Position] для карточки. Временное до шага 13/15 (Coord на
 * `PositionFlowManager.sheet` per-account даст готовые Position; тогда
 * extension и `activeCard` удаляются). Перенесено в presentation/util
 * (M001, clean-audit). `null` card — no-op.
 */
internal fun Position.withCard(card: SelectedInstrumentInfo?): Position {
    if (card == null) return this
    return when (this) {
        is SharePosition ->
            copy(quantity = card.quantity.toDouble(), averagePrice = card.averagePrice, profit = card.profit)
        is FuturePosition ->
            copy(quantity = card.quantity.toDouble(), averagePrice = card.averagePrice, profit = card.profit)
        is BondPosition ->
            copy(quantity = card.quantity.toDouble(), averagePrice = card.averagePrice, profit = card.profit)
        is OptionPosition ->
            copy(quantity = card.quantity.toDouble(), averagePrice = card.averagePrice, profit = card.profit)
        is EtfPosition ->
            copy(quantity = card.quantity.toDouble(), averagePrice = card.averagePrice, profit = card.profit)
        is CurrencyPosition ->
            copy(quantity = card.quantity.toDouble(), averagePrice = card.averagePrice, profit = card.profit)
        is OtherPosition ->
            copy(quantity = card.quantity.toDouble(), averagePrice = card.averagePrice, profit = card.profit)
    }
}
