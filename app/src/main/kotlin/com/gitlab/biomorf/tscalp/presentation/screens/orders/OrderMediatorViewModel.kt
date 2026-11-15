package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * UI-facing wrapper над [OrderFormMediator] (C2, PR-4 Phase 5).
 *
 * **Почему wrapper, а не прямой Mediator в UI:**
 * Mediator — `@ActivityRetainedScoped` (не `@HiltViewModel`),
 * `hiltViewModel()` его не создаёт. `OrderMediatorViewModel` —
 * `@HiltViewModel`, инжектит Mediator (один инстанс на Activity),
 * UI вызывает `updatePrimitives(...)` через него.
 *
 * **2c-fixation:** UI → MediatorViewModel → Mediator. Cross-VM = 0
 * (Mediator знает только state, sub-VM в него не инжектят).
 *
 * **Phase 5:** UI на каждое действие sub-VM вызывает
 * `mediatorVM.updatePrimitives(...)` (debounce 500ms → save).
 */
@HiltViewModel
class OrderMediatorViewModel
    @Inject
    constructor(
        private val mediator: OrderFormMediator,
        private val logger: Logger,
    ) : ViewModel() {
        fun updatePrimitives(
            selectedCard: CardIdentity?,
            pairedCard: CardIdentity?,
            pairTradingEnabled: Boolean,
            quantity: Long,
            pairedMultiplier: Long,
            orderType: OrderTypeSelection,
        ) {
            logger.d(
                TAG,
                "updatePrimitives (UI): primary=${selectedCard?.tscalpInstrumentId} " +
                    "pair=${pairedCard?.tscalpInstrumentId} pairTrading=$pairTradingEnabled " +
                    "qty=$quantity mult=$pairedMultiplier",
            )
            mediator.updatePrimitives(
                selectedCard = selectedCard,
                pairedCard = pairedCard,
                pairTradingEnabled = pairTradingEnabled,
                quantity = quantity,
                pairedMultiplier = pairedMultiplier,
                orderType = orderType,
            )
        }

        companion object {
            private const val TAG = "OrderMediatorViewModel"
        }
    }
