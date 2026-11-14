package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot
import com.gitlab.biomorf.tscalp.domain.usecases.SaveFormStateUseCase
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Screen-scoped Mediator для C2 (PR-4 Phase 4).
 *
 * **2c-fixation (scope):** `@ViewModelScoped` не даёт shared state между
 * sibling-VM (Hilt — свой инстанс на VM). `@ActivityRetainedScoped` —
 * один инстанс на Activity, инжектится во все 5 VM. Single-Activity
 * приложение: state живёт до конца Activity, другие экраны Mediator
 * не инжектят -> нет утечки.
 *
 * **Phase 4 (save-only):** UI вызывает [updatePrimitives] на каждое
 * действие. Mediator держит shared state + debounce 500ms + сохраняет
 * snapshot через `SaveFormStateUseCase`. **Recalc/sync — не здесь**
 * (Phase 5: UI orchestration).
 *
 * **2d-fixation (M4/M7 pre-existing):** snapshot собирается внутри
 * Mediator (presentation конструирует domain-модель `TradingStateSnapshot`).
 * Отдельная задача `### C2: SaveFormStateUseCase primitives` устранит
 * это через 7 primitives (snapshot внутри usecase).
 */
@OptIn(FlowPreview::class)
@ActivityRetainedScoped
class OrderFormMediator
    @Inject
    constructor(
        private val saveFormStateUseCase: SaveFormStateUseCase,
        appScope: CoroutineScope,
    ) {
        private val _shared = MutableStateFlow(OrderFormSharedState())
        val shared: StateFlow<OrderFormSharedState> = _shared.asStateFlow()

        private val pending = Channel<OrderFormSharedState>(Channel.CONFLATED)

        init {
            appScope.launch {
                pending
                    .receiveAsFlow()
                    .debounce(DEBOUNCE_MS)
                    .collect { state ->
                        saveFormStateUseCase.execute(state.toSnapshot())
                    }
            }
        }

        fun updatePrimitives(
            selectedCard: CardIdentity?,
            pairedCard: CardIdentity?,
            pairTradingEnabled: Boolean,
            quantity: Long,
            pairedMultiplier: Long,
            orderType: OrderTypeSelection,
        ) {
            val next =
                OrderFormSharedState(
                    selectedCard = selectedCard,
                    pairedCard = pairedCard,
                    pairTradingEnabled = pairTradingEnabled,
                    quantity = quantity,
                    pairedMultiplier = pairedMultiplier,
                    orderType = orderType,
                )
            _shared.value = next
            pending.trySend(next)
        }

        private fun OrderFormSharedState.toSnapshot(): TradingStateSnapshot =
            TradingStateSnapshot(
                selectedCard = selectedCard,
                pairedCard = pairedCard,
                pairTradingEnabled = pairTradingEnabled,
                quantity = quantity,
                pairedMultiplier = pairedMultiplier,
                orderType = orderType,
            )

        companion object {
            const val DEBOUNCE_MS = 500L
        }
    }
