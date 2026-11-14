package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.usecases.RestoredFormState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Input sub-VM (C2, PR-4 Phase 2).
 *
 * **Что делает:** валидация ввода (filter digit / digit+dot), переключение
 * orderType (с очисткой limitPrice при Market), pairTradingEnabled, quantity,
 * pairedMultiplier.
 *
 * **2d-fixation (Phase 2 → closed Phase 5f-b-2b):** `saveFormState`,
 * `syncPriceInterest`, `recalc` (updateTradeDetails/updatePairExecDetails)
 * при input-событиях **не вызываются** напрямую — выполняются в
 * UI-orchestration (`OrdersScreen` LaunchedEffect) через
 * `OrderFormMediator` + `OrderTradeDetailsViewModel`.
 *
 * **Особенность `setPairTradingEnabled(false)`:** в Phase 2 очищает только
 * Input-поля (`pairTradingEnabled`, `pairedMultiplier`). `pairedInstrument` /
 * `pairCurrentPrice` — Instrument-state, очищаются в Phase 4 через Mediator.
 */
@HiltViewModel
class OrderInputViewModel
    @Inject
    constructor(
        @Suppress("unused") private val mediator: OrderFormMediator,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OrderInputState())
        val state: StateFlow<OrderInputState> = _state.asStateFlow()

        fun onQuantityChanged(quantity: String) {
            _state.update { it.copy(quantity = quantity.filter { c -> c.isDigit() }) }
        }

        fun onPairedMultiplierChanged(value: String) {
            val filtered = value.filter { it.isDigit() }
            _state.update { it.copy(pairedMultiplier = filtered) }
        }

        fun setPairTradingEnabled(enabled: Boolean) {
            _state.update {
                if (enabled) {
                    it.copy(pairTradingEnabled = true)
                } else {
                    it.copy(
                        pairTradingEnabled = false,
                        pairedMultiplier = DEFAULT_PAIRED_MULTIPLIER.toString(),
                    )
                }
            }
        }

        fun onOrderTypeChanged(type: OrderTypeSelection) {
            _state.update { it.copy(orderType = type) }
            if (type == OrderTypeSelection.Market) {
                _state.update { it.copy(limitPrice = "") }
            }
        }

        fun onLimitPriceChanged(price: String) {
            val filtered = price.filter { it.isDigit() || it == '.' }
            _state.update { it.copy(limitPrice = filtered) }
        }

        fun onStopPriceChanged(price: String) {
            _state.update { it.copy(stopPrice = price.filter { it.isDigit() || it == '.' }) }
        }

        fun restore(state: RestoredFormState) {
            _state.update {
                it.copy(
                    quantity = state.quantity.toString(),
                    pairedMultiplier = state.pairedMultiplier.toString(),
                    orderType = state.orderType,
                    pairTradingEnabled = state.pairTradingEnabled,
                )
            }
        }

        companion object {
            private const val DEFAULT_PAIRED_MULTIPLIER = 10L
        }
    }
