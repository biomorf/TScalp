package com.example.tscalp.presentation.screens.orders

import com.example.tscalp.domain.models.AccountUi

data class OrdersUiState(
    val figi: String = "",
    val quantity: String = "",
    val accounts: List<AccountUi> = emptyList(),
    val selectedAccountId: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val isApiInitialized: Boolean = false
) {
    // Проверка валидности формы
    val isFormValid: Boolean
        get() = figi.isNotBlank() &&
                figi.length >= 3 &&  // Минимальная длина FIGI
                quantity.toLongOrNull()?.let { it > 0 } == true &&
                selectedAccountId != null &&
                isApiInitialized

    // Преобразование количества в Long
    val quantityAsLong: Long?
        get() = quantity.toLongOrNull()

    // Очистка статуса
    fun clearStatus(): OrdersUiState = copy(
        statusMessage = null,
        isError = false
    )
}