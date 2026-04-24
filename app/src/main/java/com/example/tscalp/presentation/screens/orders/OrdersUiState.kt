package com.example.tscalp.presentation.screens.orders

import com.example.tscalp.domain.models.AccountUi
import ru.tinkoff.piapi.contract.v1.Instrument
import com.example.tscalp.data.repository.InstrumentUi

data class OrdersUiState(
    val figi: String = "",
    val quantity: String = "",
    val accounts: List<AccountUi> = emptyList(),
    val selectedAccountId: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val isApiInitialized: Boolean = false,
    // Новые поля для поиска
    val searchQuery: String = "",
    val searchResults: List<InstrumentUi> = emptyList(),
    val selectedInstrument: InstrumentUi? = null,
    val isSearching: Boolean = false,
    val currentPrice: Double? = null,          /// Текущая рыночная цена
    val priceChange: Double? = null,           /// Изменение цены с открытия (в валюте)
    val priceChangePercent: Double? = null,    /// Изменение цены в процентах
    val isPriceLoading: Boolean = false        /// Индикатор загрузки цены
) {
    val isFormValid: Boolean
        get() = selectedInstrument != null &&
                quantity.toLongOrNull()?.let { it > 0 } == true &&
                selectedAccountId != null &&
                isApiInitialized

    val quantityAsLong: Long?
        get() = quantity.toLongOrNull()

    fun clearStatus(): OrdersUiState = copy(
        statusMessage = null,
        isError = false
    )
}