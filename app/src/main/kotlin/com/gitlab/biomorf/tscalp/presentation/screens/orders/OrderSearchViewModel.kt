package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.SearchCacheRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.usecases.SearchInstrumentsUseCase
import com.gitlab.biomorf.tscalp.presentation.util.StatusEvent
import com.gitlab.biomorf.tscalp.presentation.util.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search-VM (основной + парный поиск, выбор брокера поиска) —
 * разбиение `OrdersViewModel` на 4 VM, шаг 11a (P6, P9, P4).
 *
 * **Не содержит:** `selectedInstrument` / `pairedInstrument` /
 * `onInstrumentSelected` / `onPairedInstrumentSelected` (Form-VM),
 * `tradingStatuses` (Coord passthrough, P1).
 *
 * **`onResultSelected`** — только Search-часть (`query = displayName`,
 * `results = []`, для PRIMARY — `isSearchActive = false`).
 * Form-триггер (`formVM.onInstrumentSelected`) — UI (P4).
 *
 * **Slot-параметр (2026-11-12):** 9 функций с `slot: SearchSlot` вместо
 * 16 симметричных `on*` / `onPair*` пар. Одна реализация
 * debounce/usecase/error для обоих слотов. Split на 2 VM отвергнут
 * (дублирование логики); общий кеш `SearchCacheRepository` —
 * сохраняется (ключ `"brokerName:query"`, слот не входит).
 */
@HiltViewModel
class OrderSearchViewModel
    @Inject
    constructor(
        private val searchInstrumentsUseCase: SearchInstrumentsUseCase,
        private val searchCacheRepository: SearchCacheRepository,
        private val brokerRegistry: BrokerRegistry,
        private val logger: Logger,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OrderSearchState())
        private val _status = Channel<StatusEvent>(Channel.BUFFERED)
        val status: Flow<StatusEvent> = _status.receiveAsFlow()
        val state: StateFlow<OrderSearchState> = _state.asStateFlow()

        private val searchJobs = mutableMapOf<SearchSlot, Job>()

        companion object {
            private const val TAG = "OrderSearchViewModel"
            private const val DEBOUNCE_MS = 500L
        }

        private fun updateSlot(
            slot: SearchSlot,
            block: (SlotSearchState) -> SlotSearchState,
        ) {
            _state.update {
                when (slot) {
                    SearchSlot.PRIMARY -> it.copy(primary = block(it.primary))
                    SearchSlot.PAIR -> it.copy(pair = block(it.pair))
                }
            }
        }

        fun onQueryChanged(
            slot: SearchSlot,
            query: String,
        ) {
            updateSlot(slot) { it.copy(query = query) }
            searchJobs[slot]?.cancel()
            if (query.length < 2) {
                updateSlot(slot) { it.copy(results = emptyList(), isSearching = false) }
                return
            }
            searchJobs[slot] =
                viewModelScope.launch {
                    try {
                        delay(DEBOUNCE_MS)
                        updateSlot(slot) { it.copy(isSearching = true) }
                        val broker =
                            when (slot) {
                                SearchSlot.PRIMARY -> _state.value.primary.broker
                                SearchSlot.PAIR -> _state.value.pair.broker
                            }
                        when (val r = searchInstrumentsUseCase.execute(broker, query)) {
                            is AppResult.Success ->
                                updateSlot(slot) { it.copy(results = r.data, isSearching = false) }
                            is AppResult.Failure -> {
                                updateSlot(slot) { it.copy(results = emptyList(), isSearching = false) }
                                _status.trySend(StatusEvent("Ошибка поиска: ${r.error.message}", isError = true))
                            }
                        }
                    } catch (ce: CancellationException) {
                        updateSlot(slot) { it.copy(isSearching = false) }
                        throw ce
                    } catch (e: Exception) {
                        logger.e(TAG, "search exception (slot=$slot)", e)
                        updateSlot(slot) { it.copy(results = emptyList(), isSearching = false) }
                        _status.trySend(StatusEvent("Ошибка поиска: ${e.message}", isError = true))
                    }
                }
        }

        fun refresh(slot: SearchSlot) {
            val s =
                when (slot) {
                    SearchSlot.PRIMARY -> _state.value.primary
                    SearchSlot.PAIR -> _state.value.pair
                }
            if (s.query.length >= 2) {
                searchCacheRepository.invalidate(s.broker, s.query)
                onQueryChanged(slot, s.query)
            }
        }

        fun onResultSelected(
            slot: SearchSlot,
            instrument: Instrument,
        ) {
            when (slot) {
                SearchSlot.PRIMARY ->
                    _state.update {
                        it.copy(
                            primary = it.primary.copy(query = instrument.displayName(), results = emptyList()),
                            isSearchActive = false,
                        )
                    }
                SearchSlot.PAIR ->
                    updateSlot(slot) {
                        it.copy(query = instrument.displayName(), results = emptyList())
                    }
            }
        }

        fun clearQuery(slot: SearchSlot) {
            when (slot) {
                SearchSlot.PRIMARY ->
                    _state.update {
                        it.copy(
                            primary = it.primary.copy(query = "", results = emptyList()),
                            isSearchActive = false,
                        )
                    }
                SearchSlot.PAIR ->
                    updateSlot(slot) { it.copy(query = "", results = emptyList()) }
            }
        }

        fun setSearchActive(active: Boolean) {
            _state.update {
                it.copy(
                    isSearchActive = active,
                    primary =
                        if (active) {
                            it.primary.copy(query = "", results = emptyList())
                        } else {
                            it.primary
                        },
                )
            }
        }

        fun openBrokerDialog(slot: SearchSlot) {
            updateSlot(slot) { it.copy(showBrokerDialog = true) }
        }

        fun saveBroker(
            slot: SearchSlot,
            broker: BrokerName,
        ) {
            updateSlot(slot) { it.copy(selectedBroker = broker, showBrokerDialog = false) }
        }

        fun dismissBrokerDialog(slot: SearchSlot) {
            updateSlot(slot) { it.copy(showBrokerDialog = false) }
        }

        fun getAvailableBrokerNames(): List<BrokerName> = brokerRegistry.getAvailableBrokerNames()
    }
