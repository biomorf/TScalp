package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceConsumer
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot
import com.gitlab.biomorf.tscalp.domain.usecases.CalculatePairExecUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.CalculateTradeDetailsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PairExecInput
import com.gitlab.biomorf.tscalp.domain.usecases.TradeDetails
import com.gitlab.biomorf.tscalp.presentation.util.DOMESTIC_CURRENCY
import com.gitlab.biomorf.tscalp.presentation.util.formatCurrency
import com.gitlab.biomorf.tscalp.presentation.util.formatDomesticCurrency
import com.gitlab.biomorf.tscalp.presentation.util.formatPrice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
 *
 * **2c (clean-audit A2 / OrderFormViewModel, 2026-11-07):**
 * `@Suppress("TooManyFunctions")` — 12 функций в 13d-B, 26+ в
 * 13d-C/D. Split Form на sub-VM = +N VM + cross-VM (форма —
 * единый сценарий: instrument + quantity + orderType + pair +
 * confirm). Группировка — хуже читаемость. Снятие — задача
 * `### OrderFormViewModel: reduce functions` (ROADMAP, после 14b).
 */
@Suppress("TooManyFunctions")
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

        fun onQuantityChanged(quantity: String) {
            _state.update { it.copy(quantity = quantity.filter { c -> c.isDigit() }) }
            updateTradeDetails()
            updatePairExecDetails()
            viewModelScope.launch { saveState() }
        }

        fun onPairedMultiplierChanged(value: String) {
            val filtered = value.filter { it.isDigit() || it == '.' }
            _state.update { it.copy(pairedMultiplier = filtered) }
            viewModelScope.launch { saveState() }
        }

        fun setPairTradingEnabled(enabled: Boolean) {
            _state.update {
                if (enabled) {
                    it.copy(pairTradingEnabled = true)
                } else {
                    it.copy(
                        pairTradingEnabled = false,
                        pairCurrentPrice = null,
                        pairedInstrument = null,
                        pairedMultiplier = "10",
                    )
                }
            }
            viewModelScope.launch { saveState() }
            syncPriceInterest()
            updatePairExecDetails()
        }

        fun onOrderTypeChanged(type: OrderTypeSelection) {
            _state.update { it.copy(orderType = type) }
            if (type == OrderTypeSelection.Market) {
                _state.update { it.copy(limitPrice = "") }
            }
            updateTradeDetails()
            updatePairExecDetails()
            viewModelScope.launch { saveState() }
        }

        fun onLimitPriceChanged(price: String) {
            val filtered = price.filter { it.isDigit() || it == '.' }
            _state.update { it.copy(limitPrice = filtered) }
            updateTradeDetails()
            updatePairExecDetails()
            viewModelScope.launch { saveState() }
        }

        fun onStopPriceChanged(price: String) {
            _state.update { it.copy(stopPrice = price.filter { it.isDigit() || it == '.' }) }
            updateTradeDetails()
            updatePairExecDetails()
            viewModelScope.launch { saveState() }
        }

        private suspend fun saveState() {
            val state = _state.value
            tradingStateRepository.save(
                TradingStateSnapshot(
                    selectedTscalpInstrumentId = state.selectedInstrument?.tscalpInstrumentId,
                    pairedTscalpInstrumentId = state.pairedInstrument?.tscalpInstrumentId,
                    pairTradingEnabled = state.pairTradingEnabled,
                    quantity = state.quantity,
                    pairedMultiplier = state.pairedMultiplier,
                    orderType = state.orderType,
                ),
            )
        }

        private fun syncPriceInterest() {
            val state = _state.value
            val uids =
                buildSet {
                    state.selectedInstrument?.let { add(it.tscalpInstrumentId) }
                    state.pairedInstrument?.let { add(it.tscalpInstrumentId) }
                }
            priceFlowManager.setInterest(PriceConsumer.ORDERS, uids)
        }

        private fun updateTradeDetails() {
            val state = _state.value
            val details =
                calculateTradeDetails.calculate(
                    currentPrice = state.currentPrice,
                    limitPrice = state.limitPrice,
                    stopPrice = state.stopPrice,
                    orderType = state.orderType,
                    instrument = state.selectedInstrument,
                    pairedInstrument = state.pairedInstrument,
                    quantity = state.quantityAsLong ?: 0L,
                    pairedMultiplier = state.pairedMultiplier,
                    pairCurrentPrice = state.pairCurrentPrice,
                )
            val costOverlay = formatCostOverlay(details, state)
            val multiplierOverlay = formatMultiplierOverlay(details, state)
            _state.update {
                it.copy(
                    executionPrice = details.executionPrice,
                    costOverlay = costOverlay,
                    multiplierOverlay = multiplierOverlay,
                )
            }
        }

        private fun updatePairExecDetails() {
            val state = _state.value
            val result =
                calculatePairExecUseCase.execute(
                    PairExecInput(
                        pairTradingEnabled = state.pairTradingEnabled,
                        hasPairedInstrument = state.pairedInstrument != null,
                        orderType = state.orderType,
                        limitPrice = state.limitPrice,
                        stopPrice = state.stopPrice,
                        pendingDirection = state.pendingDirection,
                        executionPrice = state.executionPrice,
                        pairCurrentPrice = state.pairCurrentPrice,
                    ),
                )
            _state.update {
                it.copy(pairExecPrice = result.pairExecPrice, pairedOrderType = result.pairedOrderType)
            }
        }

        private fun formatCostOverlay(
            details: TradeDetails,
            state: OrderFormState,
        ): String? {
            val instrumentType = state.selectedInstrument?.instrumentType ?: InstrumentType.OTHER
            val currency = state.selectedInstrument?.currency ?: DOMESTIC_CURRENCY
            return when {
                details.costPoints != null && details.costDomesticCurrency != null ->
                    "${formatPrice(details.costPoints, instrumentType, currency)}  ·  ${formatDomesticCurrency(details.costDomesticCurrency)}"
                details.costCurrency != null -> formatCurrency(details.costCurrency, currency)
                else -> null
            }
        }

        private fun formatMultiplierOverlay(
            details: TradeDetails,
            state: OrderFormState,
        ): String? {
            val pairedInstrumentType = state.pairedInstrument?.instrumentType ?: InstrumentType.OTHER
            val currency = state.pairedInstrument?.currency ?: DOMESTIC_CURRENCY
            return when {
                details.multiplierPoints != null && details.multiplierDomesticCurrency != null ->
                    "${formatPrice(details.multiplierPoints, pairedInstrumentType, currency)}  ·  ${formatDomesticCurrency(details.multiplierDomesticCurrency)}"
                details.multiplierCurrency != null -> formatCurrency(details.multiplierCurrency, currency)
                else -> null
            }
        }
    }
