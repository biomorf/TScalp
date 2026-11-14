package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Instrument sub-VM (C2, PR-4 Phase 0 skeleton).
 *
 * **Phase 1:** 9 fun (onInstrumentSelected, clearSelectedInstrument,
 * onPairedInstrumentSelected, clearPairedInstrument, onAccountSaved,
 * onPairedAccountSaved, updateInstrumentPrice, syncPriceInterest, saveState).
 */
@HiltViewModel
class OrderInstrumentViewModel
    @Inject
    constructor(
        @Suppress("unused") private val mediator: OrderFormMediator,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OrderInstrumentState())
        val state: StateFlow<OrderInstrumentState> = _state.asStateFlow()
    }
