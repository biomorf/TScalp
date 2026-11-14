package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import com.gitlab.biomorf.tscalp.presentation.util.StatusEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

/**
 * Execution sub-VM (C2, PR-4 Phase 0 skeleton).
 *
 * **Phase 3:** 8 fun (onBuyClick, onSellClick, onConfirmOrder,
 * onDismissConfirmDialog, postOrder, handlePostOrderSuccess,
 * handlePostOrderInsufficient, handlePostOrderError).
 */
@HiltViewModel
class OrderExecutionViewModel
    @Inject
    constructor(
        @Suppress("unused") private val mediator: OrderFormMediator,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OrderExecutionState())
        val state: StateFlow<OrderExecutionState> = _state.asStateFlow()

        private val _status = Channel<StatusEvent>(Channel.BUFFERED)
        val status: Flow<StatusEvent> = _status.receiveAsFlow()
    }
