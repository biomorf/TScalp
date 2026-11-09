package com.gitlab.biomorf.tscalp.presentation.screens.orders

/**
 * One-shot UI event (M6 fix, clean-audit M6): перенесено из
 * `statusMessage` / `isError` (StateFlow, replay) в `Channel<StatusEvent>`
 * (VM) + `receiveAsFlow()` (UI). Событие доставляется ровно один раз,
 * независимо от пересоздания Composable. Presentation-only.
 *
 * `isError` — только UI-стилизация (actionLabel / duration snackbar);
 * коллектор — `LaunchedEffect(Unit) { status.collect { … } }`.
 */
data class StatusEvent(
    val message: String,
    val isError: Boolean = false,
)
