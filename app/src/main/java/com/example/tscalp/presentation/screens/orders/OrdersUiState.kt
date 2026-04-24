package com.example.tscalp.presentation.screens.orders

import com.example.tscalp.data.repository.InstrumentUi
import com.example.tscalp.domain.models.AccountUi
import com.example.tscalp.domain.models.PortfolioPosition

/**
 * Информация о выбранном инструменте для отображения в карточке "Последние просмотренные".
 * Содержит как данные из портфеля (если позиция есть), так и текущую рыночную цену.
 */
data class SelectedInstrumentInfo(
    val instrument: InstrumentUi,         // базовые данные инструмента (тикер, название, figi, валюта, лот)
    val currentPrice: Double?,           // последняя рыночная цена (из MarketDataService)
    val priceChange: Double?,            // изменение цены за день (пока не реализовано)
    val priceChangePercent: Double?,     // изменение цены в процентах (пока не реализовано)
    val quantity: Long,                  // количество в портфеле (0, если позиции нет)
    val averagePrice: Double?,           // средняя цена покупки (из портфеля)
    val profit: Double?,                 // прибыль/убыток (из портфеля или расчётный)
    val profitPercent: Double?           // прибыль/убыток в процентах
)

/**
 * Состояние экрана заявок.
 * Хранит все данные, необходимые для управления формой заявки, поиском инструментов,
 * отображения последних просмотренных позиций и информацию о портфеле.
 */
data class OrdersUiState(
    // Основные поля формы
    val figi: String = "",
    val quantity: String = "",

    // Счета
    val accounts: List<AccountUi> = emptyList(),
    val selectedAccountId: String? = null,

    // Состояние загрузки и ошибок
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,

    // Состояние подключения API
    val isApiInitialized: Boolean = false,

    // Поиск инструментов
    val searchQuery: String = "",
    val searchResults: List<InstrumentUi> = emptyList(),
    val isSearching: Boolean = false,
    val selectedInstrument: InstrumentUi? = null,

    // Рыночная цена выбранного инструмента
    val currentPrice: Double? = null,
    val isPriceLoading: Boolean = false,

    // Позиции портфеля (загружаются при инициализации и после сделок)
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