package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.usecases.SaveFormStateUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SyncPriceInterestUseCase
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Screen-scoped Mediator для C2 (PR-4).
 *
 * **2c-fixation (scope):** `@ViewModelScoped` не даёт shared state между
 * sibling-VM (Hilt даёт каждому VM свой инстанс). `@ActivityRetainedScoped`
 * — один инстанс на Activity, инжектится во все 5 VM (Coord + 4 sub-VM).
 * Single-Activity приложение: state живёт до конца Activity, другие
 * экраны Mediator не инжектят -> нет утечки.
 *
 * **Phase 0:** skeleton. **Phase 4:** `saveSnapshot` (debounce 500ms) +
 * `syncInterest` + orchestration.
 */
@ActivityRetainedScoped
class OrderFormMediator
    @Inject
    constructor(
        @Suppress("unused") private val saveFormStateUseCase: SaveFormStateUseCase,
        @Suppress("unused") private val syncPriceInterestUseCase: SyncPriceInterestUseCase,
    ) {
        private val _shared = MutableStateFlow(OrderFormSharedState())
        val shared: StateFlow<OrderFormSharedState> = _shared.asStateFlow()

        // Phase 4: saveSnapshot, syncInterest, orchestration (recalc).
    }
