package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceConsumer
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.PriceUpdate
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot
import com.gitlab.biomorf.tscalp.domain.usecases.CalculatePairExecUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.CalculateTradeDetailsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PairExecInput
import com.gitlab.biomorf.tscalp.domain.usecases.PairedOutcome
import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderOutcome
import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PrimaryOutcome
import com.gitlab.biomorf.tscalp.domain.usecases.RestoreOrderFormUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SelectInstrumentUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SelectPairedInstrumentUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.TradeDetails
import com.gitlab.biomorf.tscalp.presentation.util.DOMESTIC_CURRENCY
import com.gitlab.biomorf.tscalp.presentation.util.formatCurrency
import com.gitlab.biomorf.tscalp.presentation.util.formatDomesticCurrency
import com.gitlab.biomorf.tscalp.presentation.util.formatPrice
import com.gitlab.biomorf.tscalp.presentation.util.toPlaceOrderRequest
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
        private val tradingPreferencesRepository: TradingPreferencesRepository,
        private val priceFlowManager: PriceFlowManager,
        private val logger: Logger,
        private val placeOrderUseCase: PlaceOrderUseCase,
        private val restoreOrderFormUseCase: RestoreOrderFormUseCase,
        private val selectInstrumentUseCase: SelectInstrumentUseCase,
        private val selectPairedInstrumentUseCase: SelectPairedInstrumentUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OrderFormState())
        val state: StateFlow<OrderFormState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                logger.d(TAG, "subscribed to confirmOrdersEnabledFlow")
                tradingPreferencesRepository.confirmOrdersEnabledFlow().collect { enabled ->
                    _state.update { it.copy(confirmOrdersEnabled = enabled) }
                }
            }
            viewModelScope.launch {
                logger.d(TAG, "subscribed to prices")
                priceFlowManager.prices.collect { update ->
                    updateInstrumentPrice(update)
                }
            }
            viewModelScope.launch {
                restoreOrderForm()
            }
        }

        fun onBuyClick() =
            viewModelScope.launch {
                if (_state.value.confirmOrdersEnabled) {
                    _state.update { it.copy(showConfirmDialog = true, pendingDirection = OrderDirection.BUY) }
                    updatePairExecDetails()
                } else {
                    postOrder(OrderDirection.BUY)
                }
            }

        fun onSellClick() =
            viewModelScope.launch {
                if (_state.value.confirmOrdersEnabled) {
                    _state.update { it.copy(showConfirmDialog = true, pendingDirection = OrderDirection.SELL) }
                    updatePairExecDetails()
                } else {
                    postOrder(OrderDirection.SELL)
                }
            }

        fun onConfirmOrder() {
            val direction = _state.value.pendingDirection ?: return
            _state.update { it.copy(showConfirmDialog = false, pendingDirection = null) }
            updatePairExecDetails()
            postOrder(direction)
        }

        fun onDismissConfirmDialog() {
            _state.update { it.copy(showConfirmDialog = false, pendingDirection = null) }
            updatePairExecDetails()
        }

        private fun postOrder(direction: OrderDirection) {
            val request = _state.value.toPlaceOrderRequest() ?: return
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, statusMessage = null) }
                when (val outcome = placeOrderUseCase.execute(request, direction)) {
                    is PlaceOrderOutcome.Success -> handlePostOrderSuccess(outcome)
                    is PlaceOrderOutcome.InsufficientFunds -> handlePostOrderInsufficient(outcome)
                    is PlaceOrderOutcome.Error -> handlePostOrderError(outcome)
                    is PlaceOrderOutcome.InvalidRequest -> {
                        logger.w(TAG, "postOrder: invalid request — ${outcome.reason}")
                        _state.update { it.copy(isLoading = false) }
                    }
                }
            }
        }

        /**
         * 2c (clean-audit M013 / A2, 2026-11-07): `handlePostOrderSuccess`
         * **не вызывает** `refreshLastSelectedInstruments` — это Coord-метод
         * (cross-VM call запрещён, централизация). Coord реактивен через
         * `PositionFlowManager.sheet` -> `updatePositionsFromSheet` ->
         * `refreshLastSelectedInstruments`. `freeBalance` приходит из
         * [PlaceOrderOutcome.Success] (usecase делает `getBalanceResult`).
         */
        private fun handlePostOrderSuccess(outcome: PlaceOrderOutcome.Success) {
            var message = formatPostOrderSuccess(outcome.primary, outcome.paired)
            val freeBalance = outcome.freeBalance
            logger.d(
                TAG,
                "postOrder success: primary=${outcome.primary}, paired=${outcome.paired}, freeBalance=$freeBalance",
            )
            if (freeBalance != null && freeBalance < 1000.0) {
                message += "\n⚠️ Низкий свободный остаток: ${formatDomesticCurrency(freeBalance)}"
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    statusMessage = message,
                    isError = false,
                    quantity = "",
                    limitPrice = "",
                    stopPrice = "",
                    freeBalance = freeBalance,
                )
            }
        }

        private fun handlePostOrderInsufficient(outcome: PlaceOrderOutcome.InsufficientFunds) {
            logger.w(
                TAG,
                "postOrder insufficient: balance=${outcome.balance}, required=${outcome.required}",
            )
            _state.update {
                it.copy(
                    isLoading = false,
                    statusMessage =
                        "❌ Недостаточно средств. Свободно: ${formatDomesticCurrency(outcome.balance)}, требуется: ~${formatDomesticCurrency(outcome.required)}",
                    isError = true,
                )
            }
        }

        private fun handlePostOrderError(outcome: PlaceOrderOutcome.Error) {
            logger.e(TAG, "postOrder failed: ${outcome.message}", outcome.cause)
            _state.update {
                it.copy(
                    isLoading = false,
                    statusMessage = "❌ ${outcome.message}",
                    isError = true,
                )
            }
        }

        private fun formatPostOrderSuccess(
            primary: PrimaryOutcome,
            paired: PairedOutcome?,
        ): String {
            val primaryText =
                when (primary) {
                    is PrimaryOutcome.Regular ->
                        "✅ Заявка выполнена!\nID: ${primary.orderId}\nИсполнено: ${primary.executedLots}/${primary.totalLots} лотов"
                    is PrimaryOutcome.Stop -> "✅ Стоп‑заявка выставлена, ID: ${primary.orderId.take(8)}…"
                }
            val pairedText =
                paired?.let { p ->
                    when (p) {
                        is PairedOutcome.Regular ->
                            "\n✅ Контрсделка: ${p.instrument.ticker} ${p.quantity} лотов, ID: ${p.orderId}"
                        is PairedOutcome.Stop ->
                            "\n✅ Контрсделка: ${p.instrument.ticker} ${p.quantity} лотов, ID: ${p.orderId.take(8)}…"
                        is PairedOutcome.Failed -> "\n❌ Ошибка контрсделки: ${p.message}"
                    }
                } ?: ""
            return primaryText + pairedText
        }

        private suspend fun restoreOrderForm() {
            when (val result = restoreOrderFormUseCase.execute()) {
                is AppResult.Success -> {
                    val data = result.data
                    _state.update {
                        it.copy(
                            selectedInstrument = data.selectedInstrument,
                            ticker = data.selectedInstrument?.ticker ?: "",
                            selectedBroker = data.selectedInstrument?.brokerName ?: BrokerName.TINVEST,
                            selectedAccountId = null,
                            pairedInstrument = data.pairedInstrument,
                            pairedBroker = data.pairedInstrument?.brokerName ?: BrokerName.TINVEST,
                            pairedAccountId = null,
                            pairTradingEnabled = data.pairTradingEnabled,
                            quantity = data.quantity,
                            pairedMultiplier = data.pairedMultiplier,
                            orderType = data.orderType,
                            currentPointValue = data.currentPointValue,
                            pairedPointValue = data.pairedPointValue,
                        )
                    }
                    updateTradeDetails()
                    updatePairExecDetails()
                    syncPriceInterest()
                }
                is AppResult.Failure -> {
                    logger.e(
                        TAG,
                        "restoreOrderForm failed: ${result.error.message}",
                        result.error.cause,
                    )
                }
            }
        }

        private fun updateInstrumentPrice(update: PriceUpdate) {
            val tscalpId = update.tscalpInstrumentId
            val price = update.price
            _state.update { state ->
                when (tscalpId) {
                    state.selectedInstrument?.tscalpInstrumentId -> {
                        val oldPrice = state.currentPrice
                        val newPercent =
                            if (oldPrice != null && oldPrice != 0.0) {
                                ((price - oldPrice) / oldPrice) * 100.0
                            } else {
                                null
                            }
                        state.copy(
                            currentPrice = price,
                            selectedPriceChangePercent = newPercent,
                        )
                    }
                    state.pairedInstrument?.tscalpInstrumentId -> {
                        state.copy(pairCurrentPrice = price)
                    }
                    else -> state
                }
            }
        }

        override fun onCleared() {
            logger.d(TAG, "onCleared")
            priceFlowManager.clearInterest(PriceConsumer.ORDERS)
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

        fun clearStatus() {
            _state.update { it.clearStatus() }
        }

        /**
         * 2c (clean-audit M013 / A2, 2026-11-07): в отличие от Coord
         * `OrdersViewModel.onInstrumentSelected` — **не** обновляет
         * `lastSelectedInstruments` и **не** вызывает `startPositionUpdates`:
         * - `lastSelectedInstruments` — Coord-владелец; Coord реактивен через
         *   `CardsRepository.identities` (A3/A4-b-1) + `RefreshSelectedInstrumentsUseCase`.
         *   Cross-VM call запрещён (централизация).
         * - `startPositionUpdates` — Coord-lifecycle (подписка на
         *   `PositionFlowManager.sheet`); Form не управляет lifecycle менеджеров.
         *   Coord реактивен через `updatePositionsFromSheet` -> `refreshLastSelectedInstruments`.
         */
        fun onInstrumentSelected(instrument: Instrument) {
            _state.update {
                it.copy(
                    selectedInstrument = instrument,
                    ticker = instrument.ticker,
                    selectedBroker = instrument.brokerName,
                )
            }
            viewModelScope.launch {
                _state.update { it.copy(isPriceLoading = true) }
                when (val result = selectInstrumentUseCase.execute(instrument, _state.value.selectedAccountId)) {
                    is AppResult.Success -> {
                        val data = result.data
                        val card = data.card
                        _state.update {
                            it.copy(
                                currentPrice = card.currentPrice,
                                isPriceLoading = false,
                                currentPointValue = data.pointValue,
                                selectedAccountId = card.accountId,
                            )
                        }
                        updateTradeDetails()
                        updatePairExecDetails()
                        syncPriceInterest()
                        saveState()
                    }
                    is AppResult.Failure -> {
                        logger.e(
                            TAG,
                            "onInstrumentSelected failed: ${result.error.message}",
                            result.error.cause,
                        )
                        _state.update { it.copy(isPriceLoading = false) }
                    }
                }
            }
        }

        fun clearSelectedInstrument() {
            _state.update {
                it.copy(
                    selectedInstrument = null,
                    ticker = "",
                    selectedBroker = BrokerName.TINVEST,
                    selectedAccountId = null,
                    currentPrice = null,
                    isPriceLoading = false,
                )
            }
            syncPriceInterest()
        }

        fun onPairedInstrumentSelected(instrument: Instrument) {
            _state.update {
                it.copy(
                    pairedInstrument = instrument,
                    pairedBroker = instrument.brokerName,
                    pairedAccountId = null,
                )
            }
            syncPriceInterest()
            viewModelScope.launch {
                when (val result = selectPairedInstrumentUseCase.execute(instrument)) {
                    is AppResult.Success -> {
                        val data = result.data
                        val price = data.price
                        if (price != null) {
                            _state.update { it.copy(pairCurrentPrice = price) }
                            _state.update { it.copy(pairedPointValue = data.pointValue) }
                        }
                    }
                    is AppResult.Failure -> {
                        logger.e(
                            TAG,
                            "onPairedInstrumentSelected failed: ${result.error.message}",
                            result.error.cause,
                        )
                    }
                }
                updateTradeDetails()
                updatePairExecDetails()
                saveState()
            }
        }

        /**
         * Callback из UI-оркестрации после `OrderDialogsViewModel.saveBrokerSettings`:
         * новый accountId карточки основного слота. `null` — счёт сброшен
         * (карточка `(uid, broker, null)`).
         */
        fun onAccountSaved(newAccountId: String?) {
            _state.update { it.copy(selectedAccountId = newAccountId) }
            val s = _state.value
            logger.d(
                TAG,
                "onAccountSaved: slot=PRIMARY, uid=${s.selectedInstrument?.tscalpInstrumentId}, " +
                    "broker=${s.selectedBroker}, newAccountId=$newAccountId",
            )
        }

        /**
         * Callback из UI-оркестрации после `OrderDialogsViewModel.saveBrokerSettings`:
         * новый accountId карточки парного слота.
         */
        fun onPairedAccountSaved(newAccountId: String?) {
            _state.update { it.copy(pairedAccountId = newAccountId) }
            val s = _state.value
            logger.d(
                TAG,
                "onPairedAccountSaved: slot=PAIR, uid=${s.pairedInstrument?.tscalpInstrumentId}, " +
                    "broker=${s.pairedBroker}, newAccountId=$newAccountId",
            )
        }

        fun clearPairedInstrument() {
            _state.update {
                it.copy(
                    pairedInstrument = null,
                    pairedBroker = BrokerName.TINVEST,
                    pairedAccountId = null,
                )
            }
            syncPriceInterest()
            updatePairExecDetails()
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

        companion object {
            private const val TAG = "OrderFormViewModel"
        }
    }
