package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Input sub-VM (C2, PR-4 Phase 0 skeleton).
 *
 * **Phase 2:** 6 fun (onQuantityChanged, onPairedMultiplierChanged,
 * setPairTradingEnabled, onOrderTypeChanged, onLimitPriceChanged,
 * onStopPriceChanged).
 */
@HiltViewModel
class OrderInputViewModel
    @Inject
    constructor(
        @Suppress("unused") private val mediator: OrderFormMediator,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OrderInputState())
        val state: StateFlow<OrderInputState> = _state.asStateFlow()
    }
