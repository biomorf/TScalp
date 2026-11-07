package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.usecases.CalculatePairExecUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.CalculateTradeDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel формы заявки (Form-VM, шаг 13d-A — скелет).
 *
 * Вынесено из `OrdersViewModel` (split на 4 VM, M013 / A2).
 * Этот шаг — только `_state` + inject (минимум для 13d-B);
 * методы (`onQuantityChanged`, `postOrder`, `onInstrumentSelected`,
 * `onCleared`, `init`-подписки) добавляются в 13d-B / 13d-C / 13d-D.
 *
 * **Cross-VM — только через domain или UI-оркестрацию**
 * (принцип централизации): Form не читает `_uiState` другого VM;
 * Coord не читает `_state` Form. Координация:
 * - Form -> `priceFlowManager.setInterest` / `clearInterest`
 *   (domain API) — 13d-B / 13d-D;
 * - Coord -> `PositionFlowManager.sheet` / `CardsRepository.identities`
 *   (domain) — Coord реактивен, Form не вызывает Coord-методы;
 * - UI-оркестрация — Composable вызывает методы двух VM
 *   (например, `searchVM.onSearchResultSelected` + `formVM.onInstrumentSelected`).
 *
 * **2c (фиксация):**
 * - `saveState()` — в Form-VM (не в Coord): все callsites —
 *   Form-методы; Coord-State после 13d-F не содержит Form-полей.
 *   Добавляется в 13d-B.
 * - `onCleared` — `priceFlowManager.clearInterest(ORDERS)` (Form).
 *   Coord `onCleared` — только `stopPositionUpdates()`. Добавляется
 *   в 13d-D.
 */
@HiltViewModel
class OrderFormViewModel
    @Inject
    constructor(
        private val calculateTradeDetails: CalculateTradeDetailsUseCase,
        private val calculatePairExecUseCase: CalculatePairExecUseCase,
        private val tradingStateRepository: TradingStateRepository,
        private val priceFlowManager: PriceFlowManager,
        private val logger: Logger,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OrderFormState())
        val state: StateFlow<OrderFormState> = _state.asStateFlow()

        companion object {
            private const val TAG = "OrderFormViewModel"
        }
    }
