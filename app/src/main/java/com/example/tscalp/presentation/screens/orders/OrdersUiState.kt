package com.example.tscalp.presentation.screens.orders

import com.example.tscalp.domain.models.AccountUi
import ru.tinkoff.piapi.contract.v1.Instrument

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
    val searchResults: List<Instrument> = emptyList(),
    val isSearching: Boolean = false,
    val selectedInstrument: Instrument? = null
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