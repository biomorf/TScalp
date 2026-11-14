package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.OrderDirection

/**
 * Состояние отправки заявки (confirm dialog / pending / loading) —
 * часть split Form-VM (C2).
 *
 * Источник: `OrderFormState` (13d-A). Статусные события (success /
 * error / insufficient funds) — через `Channel<StatusEvent>` в
 * `OrderExecutionViewModel` (M6, clean-audit M6), не в state.
 */
data class OrderExecutionState(
    val showConfirmDialog: Boolean = false,
    val pendingDirection: OrderDirection? = null,
    val confirmOrdersEnabled: Boolean = true,
    val isLoading: Boolean = false,
)
