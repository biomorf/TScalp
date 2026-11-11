package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument

/**
 * Slot (primary / pair) for [OrderSearchViewModel].
 */
enum class SearchSlot {
    PRIMARY,
    PAIR,
}

/**
 * Per-slot state of [OrderSearchViewModel].
 *
 * Both slots share the same shape; primary and pair are distinguished
 * only by the [SearchSlot] passed to the corresponding view-model methods.
 */
data class SlotSearchState(
    val query: String = "",
    val results: List<Instrument> = emptyList(),
    val isSearching: Boolean = false,
    val broker: BrokerName = BrokerName.TINVEST,
    val showBrokerDialog: Boolean = false,
    val selectedBroker: BrokerName = BrokerName.TINVEST,
)

/**
 * State [OrderSearchViewModel].
 *
 * Search state is split into two independent slots: [primary] and [pair].
 * Both share the same shape ([SlotSearchState]). [isSearchActive] is a
 * presentation-only flag that belongs to the primary slot.
 *
 * (one-shot events `statusMessage` / `isError` removed by M6: now
 * `OrderSearchViewModel.status: Flow<StatusEvent>`.)
 *
 * **Форм-триггеры (`onInstrumentSelected` / `onPairedInstrumentSelected`)
 * не входят** — UI вызывает `searchVM.onSearchResultSelected` +
 * `formVM.onInstrumentSelected` (P4, KDoc в Composable).
 */
data class OrderSearchState(
    val primary: SlotSearchState = SlotSearchState(),
    val pair: SlotSearchState = SlotSearchState(),
    val isSearchActive: Boolean = false,
)
