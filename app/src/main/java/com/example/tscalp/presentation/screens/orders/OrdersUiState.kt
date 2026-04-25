package com.example.tscalp.presentation.screens.orders

import com.example.tscalp.data.repository.InstrumentUi
import com.example.tscalp.domain.models.AccountUi
import com.example.tscalp.domain.models.PortfolioPosition

/**
 * Информация о выбранном инструменте для отображения в карточке «Последние просмотренные».
 */
data class SelectedInstrumentInfo(
    val instrument: InstrumentUi,
    val currentPrice: Double?,
    val previousPrice: Double? = null,
    val priceChange: Double?,
    val priceChangePercent: Double?,
    val quantity: Long,
    val averagePrice: Double?,
    val profit: Double?,
    val profitPercent: Double?
)

data class OrdersUiState(
    val figi: String = "",
    val quantity: String = "",
    val accounts: List<AccountUi> = emptyList(),
    val selectedAccountId: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val isApiInitialized: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<InstrumentUi> = emptyList(),
    val isSearching: Boolean = false,
    val selectedInstrument: InstrumentUi? = null,
    val currentPrice: Double? = null,
    val isPriceLoading: Boolean = false,
    val portfolioPositions: List<PortfolioPosition> = emptyList(),
    val lastSelectedInstruments: List<SelectedInstrumentInfo> = emptyList(),
    val isSearchActive: Boolean = false          // <-- для управления SearchBar
) {
    val isFormValid: Boolean
        get() = selectedInstrument != null && quantity.toLongOrNull()?.let { it > 0 } == true && selectedAccountId != null && isApiInitialized
    val quantityAsLong: Long? get() = quantity.toLongOrNull()
    fun clearStatus(): OrdersUiState = copy(statusMessage = null, isError = false)
}