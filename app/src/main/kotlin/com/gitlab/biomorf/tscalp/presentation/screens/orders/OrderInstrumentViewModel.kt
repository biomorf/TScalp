package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.PriceUpdate
import com.gitlab.biomorf.tscalp.domain.usecases.RestoredFormState
import com.gitlab.biomorf.tscalp.domain.usecases.SelectInstrumentUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SelectPairedInstrumentUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SyncPriceInterestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Instrument sub-VM (C2, PR-4 Phase 1).
 *
 * **Что делает:** выбор инструмента (primary/pair), обновление цены из
 * `priceFlowManager.prices`, sync price interest, изменение accountId.
 *
 * **2d-fixation (Phase 1):** `saveFormState` и `recalc` (updateTradeDetails)
 * при instrument-событиях **не вызываются** — перенесены в Phase 4 через
 * `OrderFormMediator` (reactive debounce + cross-state snapshot). Поведение
 * для пользователя не регрессирует: старый `OrderFormViewModel` (Coord)
 * работает параллельно до Phase 5 (UI rewrite).
 *
 * **Phase 4 wiring:** `onInstrumentSelected` → `mediator.saveSnapshot()` +
 * `mediator.scheduleRecalc()`.
 */
@HiltViewModel
class OrderInstrumentViewModel
    @Inject
    constructor(
        private val selectInstrumentUseCase: SelectInstrumentUseCase,
        private val selectPairedInstrumentUseCase: SelectPairedInstrumentUseCase,
        private val syncPriceInterestUseCase: SyncPriceInterestUseCase,
        private val priceFlowManager: PriceFlowManager,
        @Suppress("unused") private val mediator: OrderFormMediator,
        private val logger: Logger,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OrderInstrumentState())
        val state: StateFlow<OrderInstrumentState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                logger.d(TAG, "subscribed to prices")
                priceFlowManager.prices.collect { update ->
                    updateInstrumentPrice(update)
                }
            }
        }

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
                val currentAccountId = _state.value.selectedAccountId
                when (val result = selectInstrumentUseCase.execute(instrument, currentAccountId)) {
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
                        syncPriceInterest()
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
            }
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
        }

        fun onAccountSaved(newAccountId: String?) {
            _state.update { it.copy(selectedAccountId = newAccountId) }
            val s = _state.value
            logger.d(
                TAG,
                "onAccountSaved: slot=PRIMARY, uid=${s.selectedInstrument?.tscalpInstrumentId}, " +
                    "broker=${s.selectedBroker}, newAccountId=$newAccountId",
            )
        }

        fun onPairedAccountSaved(newAccountId: String?) {
            _state.update { it.copy(pairedAccountId = newAccountId) }
            val s = _state.value
            logger.d(
                TAG,
                "onPairedAccountSaved: slot=PAIR, uid=${s.pairedInstrument?.tscalpInstrumentId}, " +
                    "broker=${s.pairedBroker}, newAccountId=$newAccountId",
            )
        }

        fun restore(state: RestoredFormState) {
            _state.update {
                it.copy(
                    selectedInstrument = state.selectedInstrument,
                    ticker = state.selectedInstrument?.ticker ?: "",
                    selectedBroker = state.selectedCard?.brokerName ?: BrokerName.TINVEST,
                    selectedAccountId = state.selectedCard?.accountId,
                    pairedInstrument = state.pairedInstrument,
                    pairedBroker = state.pairedCard?.brokerName ?: BrokerName.TINVEST,
                    pairedAccountId = state.pairedCard?.accountId,
                    currentPointValue = state.currentPointValue,
                    pairedPointValue = state.pairedPointValue,
                )
            }
            syncPriceInterest()
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

        private fun syncPriceInterest() {
            val state = _state.value
            val uids =
                buildSet {
                    state.selectedInstrument?.let { add(it.tscalpInstrumentId) }
                    state.pairedInstrument?.let { add(it.tscalpInstrumentId) }
                }
            syncPriceInterestUseCase.execute(uids)
        }

        companion object {
            private const val TAG = "OrderInstrumentViewModel"
        }
    }
