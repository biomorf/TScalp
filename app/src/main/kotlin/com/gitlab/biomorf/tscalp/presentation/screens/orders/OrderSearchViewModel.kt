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
import com.gitlab.biomorf.tscalp.presentation.util.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * **`onSearchResultSelected`** — только Search-часть
 * (`searchQuery = displayName`, results = [], isSearchActive = false).
 * Form-триггер (`formVM.onInstrumentSelected`) — UI (P4).
 *
 * **2c (clean-audit M2, 2026-11-07):** 16 функций = 2 симметричных
 * search-флоу (search / pairSearch) x 8. Split на 2 VM = +1 VM
 * (не 4-VM плана) + cross-VM через selectedBroker (M5).
 * Группировка (sealed SearchDialogAction) - хуже читаемость.
 * Снятие - задача `### OrderSearchViewModel: reduce functions`
 * (ROADMAP, после 14b).
 */
@Suppress("TooManyFunctions")
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
        val state: StateFlow<OrderSearchState> = _state.asStateFlow()

        private var searchJob: Job? = null
        private var pairSearchJob: Job? = null

        companion object {
            private const val TAG = "OrderSearchViewModel"
            private const val DEBOUNCE_MS = 500L
        }

        fun onSearchQueryChanged(query: String) {
            _state.update { it.copy(searchQuery = query) }
            searchJob?.cancel()
            if (query.length < 2) {
                _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
                return
            }
            searchJob =
                viewModelScope.launch {
                    try {
                        delay(DEBOUNCE_MS)
                        _state.update { it.copy(isSearching = true) }
                        when (
                            val r =
                                searchInstrumentsUseCase.execute(_state.value.searchBroker, query)
                        ) {
                            is AppResult.Success ->
                                _state.update {
                                    it.copy(searchResults = r.data, isSearching = false)
                                }
                            is AppResult.Failure ->
                                _state.update {
                                    it.copy(
                                        searchResults = emptyList(),
                                        isSearching = false,
                                        statusMessage = "Ошибка поиска: ${r.error.message}",
                                        isError = true,
                                    )
                                }
                        }
                    } catch (ce: CancellationException) {
                        _state.update { it.copy(isSearching = false) }
                        throw ce
                    } catch (e: Exception) {
                        logger.e(TAG, "search exception", e)
                        _state.update {
                            it.copy(
                                searchResults = emptyList(),
                                isSearching = false,
                                statusMessage = "Ошибка поиска: ${e.message}",
                                isError = true,
                            )
                        }
                    }
                }
        }

        fun onPairSearchQueryChanged(query: String) {
            _state.update { it.copy(pairSearchQuery = query) }
            pairSearchJob?.cancel()
            if (query.length < 2) {
                _state.update { it.copy(pairSearchResults = emptyList(), isPairSearching = false) }
                return
            }
            pairSearchJob =
                viewModelScope.launch {
                    try {
                        delay(DEBOUNCE_MS)
                        _state.update { it.copy(isPairSearching = true) }
                        when (
                            val r =
                                searchInstrumentsUseCase.execute(
                                    _state.value.pairSearchBroker,
                                    query,
                                )
                        ) {
                            is AppResult.Success ->
                                _state.update {
                                    it.copy(pairSearchResults = r.data, isPairSearching = false)
                                }
                            is AppResult.Failure ->
                                _state.update {
                                    it.copy(
                                        pairSearchResults = emptyList(),
                                        isPairSearching = false,
                                        statusMessage = "Ошибка поиска: ${r.error.message}",
                                        isError = true,
                                    )
                                }
                        }
                    } catch (ce: CancellationException) {
                        _state.update { it.copy(isPairSearching = false) }
                        throw ce
                    } catch (e: Exception) {
                        logger.e(TAG, "pair search exception", e)
                        _state.update {
                            it.copy(
                                pairSearchResults = emptyList(),
                                isPairSearching = false,
                                statusMessage = "Ошибка поиска: ${e.message}",
                                isError = true,
                            )
                        }
                    }
                }
        }

        fun refreshSearch() {
            val s = _state.value
            if (s.searchQuery.length >= 2) {
                searchCacheRepository.invalidate(s.searchBroker, s.searchQuery)
                onSearchQueryChanged(s.searchQuery)
            }
        }

        fun refreshPairSearch() {
            val s = _state.value
            if (s.pairSearchQuery.length >= 2) {
                searchCacheRepository.invalidate(s.pairSearchBroker, s.pairSearchQuery)
                onPairSearchQueryChanged(s.pairSearchQuery)
            }
        }

        fun setSearchActive(active: Boolean) {
            _state.update { it.copy(isSearchActive = active) }
            if (active) {
                _state.update { it.copy(searchResults = emptyList(), searchQuery = "") }
            }
        }

        fun onSearchResultSelected(instrument: Instrument) {
            _state.update {
                it.copy(
                    searchQuery = instrument.displayName(),
                    searchResults = emptyList(),
                    isSearchActive = false,
                )
            }
        }

        fun onPairedSearchResultSelected(instrument: Instrument) {
            _state.update {
                it.copy(
                    pairSearchQuery = instrument.displayName(),
                    pairSearchResults = emptyList(),
                )
            }
        }

        fun clearSearchQuery() {
            _state.update {
                it.copy(searchQuery = "", searchResults = emptyList(), isSearchActive = false)
            }
        }

        fun clearPairSearchQuery() {
            _state.update { it.copy(pairSearchQuery = "", pairSearchResults = emptyList()) }
        }

        fun openSearchBrokerSettings() {
            _state.update { it.copy(showSearchBrokerDialog = true) }
        }

        fun openPairSearchBrokerSettings() {
            _state.update { it.copy(showPairSearchBrokerDialog = true) }
        }

        fun saveSearchBrokerSettings(broker: BrokerName) {
            _state.update {
                it.copy(selectedSearchBroker = broker, showSearchBrokerDialog = false)
            }
        }

        fun savePairSearchBrokerSettings(broker: BrokerName) {
            _state.update {
                it.copy(selectedPairSearchBroker = broker, showPairSearchBrokerDialog = false)
            }
        }

        fun dismissSearchBrokerDialog() {
            _state.update { it.copy(showSearchBrokerDialog = false) }
        }

        fun dismissPairSearchBrokerDialog() {
            _state.update { it.copy(showPairSearchBrokerDialog = false) }
        }

        fun getAvailableBrokerNames(): List<BrokerName> = brokerRegistry.getAvailableBrokerNames()
    }
