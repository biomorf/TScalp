package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import com.gitlab.biomorf.tscalp.domain.usecases.CalculatePairExecUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.CalculateTradeDetailsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PairExecInput
import com.gitlab.biomorf.tscalp.presentation.util.formatCostOverlay
import com.gitlab.biomorf.tscalp.presentation.util.formatMultiplierOverlay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel trade-details (executionPrice / costOverlay / multiplierOverlay /
 * pairExecPrice / pairedOrderType) — часть split Form-VM (C2).
 *
 * Вынесено из `OrderFormViewModel` (13d-A → C2). Семантика 1:1 с
 * `updateTradeDetails` / `updatePairExecDetails` в [OrderFormViewModel].
 *
 * **2c (fixation, C2):** публичные методы принимают явные параметры
 * (sub-state) — VM не читает `_state.value` других VM. `executionPrice`
 * для `updatePairExecDetails` берётся из **своего** state (SSoT:
 * единственный результат `calculateTradeDetails`). `freeBalance` —
 * в `OrderExecutionState` (`OrderExecutionViewModel`, PR-3);
 * в этом VM не хранится.
 */
@HiltViewModel
class OrderTradeDetailsViewModel
    @Inject
    constructor(
        private val calculateTradeDetails: CalculateTradeDetailsUseCase,
        private val calculatePairExecUseCase: CalculatePairExecUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OrderTradeDetailsState())
        val state: StateFlow<OrderTradeDetailsState> = _state.asStateFlow()

        fun updateTradeDetails(
            instrument: OrderInstrumentState,
            input: OrderInputState,
        ) {
            val details =
                calculateTradeDetails.calculate(
                    currentPrice = instrument.currentPrice,
                    limitPrice = input.limitPrice,
                    stopPrice = input.stopPrice,
                    orderType = input.orderType,
                    instrument = instrument.selectedInstrument,
                    pairedInstrument = instrument.pairedInstrument,
                    quantity = input.quantityAsLong ?: 0L,
                    pairedMultiplier = input.pairedMultiplier.toLongOrNull(),
                    pairCurrentPrice = instrument.pairCurrentPrice,
                )
            val costOverlay = formatCostOverlay(details, instrument.selectedInstrument)
            val multiplierOverlay = formatMultiplierOverlay(details, instrument.pairedInstrument)
            _state.update {
                it.copy(
                    executionPrice = details.executionPrice,
                    costOverlay = costOverlay,
                    multiplierOverlay = multiplierOverlay,
                )
            }
        }

        fun updatePairExecDetails(
            instrument: OrderInstrumentState,
            input: OrderInputState,
            execution: OrderExecutionState,
        ) {
            val result =
                calculatePairExecUseCase.execute(
                    PairExecInput(
                        pairTradingEnabled = input.pairTradingEnabled,
                        hasPairedInstrument = instrument.pairedInstrument != null,
                        orderType = input.orderType,
                        limitPrice = input.limitPrice,
                        stopPrice = input.stopPrice,
                        pendingDirection = execution.pendingDirection,
                        executionPrice = _state.value.executionPrice,
                        pairCurrentPrice = instrument.pairCurrentPrice,
                    ),
                )
            _state.update {
                it.copy(
                    pairExecPrice = result.pairExecPrice,
                    pairedOrderType = result.pairedOrderType,
                )
            }
        }
    }
