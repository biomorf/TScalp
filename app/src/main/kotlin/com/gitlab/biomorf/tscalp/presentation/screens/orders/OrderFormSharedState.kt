package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection

/**
 * Shared state Mediator'а (C2, PR-4 Phase 4).
 *
 * Содержит primitives для `SaveFormStateUseCase` — собираются из
 * `OrderInstrumentState` + `OrderInputState` в UI (Phase 5), передаются
 * через `OrderFormMediator.updatePrimitives(...)`.
 *
 * Mediator держит этот state как SSoT для persist (debounce 500ms).
 * Recalc/sync — не здесь (Phase 5, UI orchestration).
 */
data class OrderFormSharedState(
    val selectedCard: CardIdentity? = null,
    val pairedCard: CardIdentity? = null,
    val pairTradingEnabled: Boolean = false,
    val quantity: Long = 1L,
    val pairedMultiplier: Long = 10L,
    val orderType: OrderTypeSelection = OrderTypeSelection.Market,
)
