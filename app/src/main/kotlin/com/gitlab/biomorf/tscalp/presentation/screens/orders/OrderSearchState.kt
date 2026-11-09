package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument

/**
 * State [OrderSearchViewModel]: только Search-поля (13 из ROADMAP)
 * (one-shot events `statusMessage` / `isError` removed by M6: now
 * `OrderSearchViewModel.status: Flow<StatusEvent>`.)
 *
 * **Форм-триггеры (`onInstrumentSelected` / `onPairedInstrumentSelected`)
 * не входят** — UI вызывает `searchVM.onSearchResultSelected` +
 * `formVM.onInstrumentSelected` (P4, KDoc в Composable).
 */
data class OrderSearchState(
    val searchQuery: String = "",
    val searchResults: List<Instrument> = emptyList(),
    val isSearching: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchBroker: BrokerName = BrokerName.TINVEST,
    val pairSearchQuery: String = "",
    val pairSearchResults: List<Instrument> = emptyList(),
    val isPairSearching: Boolean = false,
    val pairSearchBroker: BrokerName = BrokerName.TINVEST,
    val showSearchBrokerDialog: Boolean = false,
    val selectedSearchBroker: BrokerName = BrokerName.TINVEST,
    val showPairSearchBrokerDialog: Boolean = false,
    val selectedPairSearchBroker: BrokerName = BrokerName.TINVEST,
)
