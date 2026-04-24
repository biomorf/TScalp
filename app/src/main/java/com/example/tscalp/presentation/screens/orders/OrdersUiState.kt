package com.example.tscalp.presentation.screens.orders

import ru.tinkoff.piapi.contract.v1.Instrument
import com.example.tscalp.data.repository.InstrumentUi
import com.example.tscalp.domain.models.AccountUi
import com.example.tscalp.domain.models.PortfolioPosition

/**
 * ///Информация о выбранном инструменте для карточки последних просмотренных.
 */
data class SelectedInstrumentInfo(
    val instrument: InstrumentUi,
    val currentPrice: Double?,          // рыночная цена
    val priceChange: Double?,           // изменение цены (пока не реализовано)
    val priceChangePercent: Double?,    // изменение цены в процентах
    val quantity: Long,                 // количество в портфеле (0, если нет)
    val averagePrice: Double?,          // средняя цена покупки
    val profit: Double?,                // прибыль/убыток
    val profitPercent: Double?          // прибыль/убыток в процентах
)
data class OrdersUiState(
    val priceChange: Double? = null,           /// Изменение цены с открытия (в валюте)
    val priceChangePercent: Double? = null,    /// Изменение цены в процентах
    val isPriceLoading: Boolean = false,        /// Индикатор загрузки цены

    // Позиции портфеля (загружаются при инициализации)
    val portfolioPositions: List<PortfolioPosition> = emptyList(),
    // Две последние просмотренные карточки
    val lastSelectedInstruments: List<SelectedInstrumentInfo> = emptyList()
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