package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection

/**
 * Состояние trade-details (execution / cost / free balance) —
 * часть split Form-VM (C2).
 *
 * Источник: `OrderFormState` (13d-A). Trade-details вычисляются
 * через `CalculateTradeDetailsUseCase` / `CalculatePairExecUseCase`
 * (см. `OrderTradeDetailsViewModel`); state — только результаты.
 */
data class OrderTradeDetailsState(
    val executionPrice: Double = 0.0,
    val costOverlay: String? = null,
    val multiplierOverlay: String? = null,
    val freeBalance: Double? = null,
    val pairExecPrice: Double = 0.0,
    val pairedOrderType: OrderTypeSelection? = null,
)
