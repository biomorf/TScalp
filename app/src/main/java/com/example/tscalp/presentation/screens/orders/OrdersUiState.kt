package com.example.tscalp.presentation.screens.orders

import android.util.Log
import com.example.tscalp.domain.models.InstrumentUi
import com.example.tscalp.domain.models.AccountUi
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.domain.models.BrokerOrderType
import com.example.tscalp.domain.models.StopOrderType
import com.example.tscalp.domain.models.StopOrderExpirationType
import com.example.tscalp.domain.models.OrderTypeSelection


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
    val profitPercent: Double?,
    val brokerName: String = "TInvest",   // брокер по умолчанию
    val accountId: String? = null
)

data class OrdersUiState(
    val ticker: String = "",
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
    val selectedTicker: String? = selectedInstrument?.ticker,
    val currentPrice: Double? = null,
    val pairCurrentPrice: Double? = null,
    val isPriceLoading: Boolean = false,
    val portfolioPositions: List<PortfolioPosition> = emptyList(),
    val lastSelectedInstruments: List<SelectedInstrumentInfo> = emptyList(),
    val isSearchActive: Boolean = false,          // <-- для управления SearchBar
    //val orderType: BrokerOrderType = BrokerOrderType.MARKET,   // тип заявки (рыночная/лимитная)
    val limitPrice: String = "",                                // цена для лимитной заявки
    val selectedPriceChangePercent: Double? = null,   // изменение цены выбранного инструмента
    // Диалог брокера
    val showBrokerDialog: Boolean = false,          // флаг открытия диалога
    val dialogInstrumentTicker: String? = null,
    val selectedBroker: String = "TInvest",         // выбранный брокер в диалоге
    val selectedAccountIdDialog: String? = null,     // выбранный счёт в диалоге
    val dialogAccounts: List<AccountUi> = emptyList(),     // счета для диалога
    // Парная торговля
    val pairTradingEnabled: Boolean = false,
    val pairSearchQuery: String = "",                   // запрос второго поиска
    val pairSearchResults: List<InstrumentUi> = emptyList(), // результаты второго поиска
    val isPairSearching: Boolean = false,               // индикатор загрузки второго поиска
    val pairedInstrument: InstrumentUi? = null,         // выбранный парный инструмент
    val pairedMultiplier: String = "10",               // множитель (по умолчанию 10)
    val swipeResetTrigger: Boolean = false,
    // Стоп‑заявки
    val orderType: OrderTypeSelection = OrderTypeSelection.Market, // было BrokerOrderType.MARKET
    val stopPrice: String = "",
    val expirationType: StopOrderExpirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
    val expireDate: String? = null
    // поле stopOrderType удалено, теперь берём из orderType.stopOrderType

) {
    val isFormValid: Boolean
        get() {
            val basicValid = selectedInstrument != null &&
                    quantity.toLongOrNull()?.let { it > 0 } == true &&
                    isApiInitialized
            val limitPriceValid = if (orderType is OrderTypeSelection.Limit || orderType is OrderTypeSelection.StopLimit) {
                limitPrice.toDoubleOrNull() != null
            } else true
            val stopPriceValid = if (orderType is OrderTypeSelection.StopLoss ||
                orderType is OrderTypeSelection.TakeProfit ||
                orderType is OrderTypeSelection.StopLimit) {
                stopPrice.toDoubleOrNull()?.let { it > 0 } == true
            } else true
            val pairValid = if (pairTradingEnabled) {
                pairedInstrument != null && pairedMultiplier.toDoubleOrNull()?.let { it > 0.0 } == true
            } else true
            return basicValid && limitPriceValid && stopPriceValid && pairValid
        }
    val quantityAsLong: Long? get() = quantity.toLongOrNull()
    fun clearStatus(): OrdersUiState = copy(statusMessage = null, isError = false)
}