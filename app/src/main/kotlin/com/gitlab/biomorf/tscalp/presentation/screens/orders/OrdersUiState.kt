package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.SelectedInstrumentInfo
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability

data class OrdersUiState(
    val ticker: String = "",
    val quantity: String = "",
    val accounts: List<BrokerAccount> = emptyList(),
    val selectedAccountId: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val isApiInitialized: Boolean = false,
    val confirmOrdersEnabled: Boolean = true,
    val searchQuery: String = "",
    val searchResults: List<Instrument> = emptyList(),
    val isSearching: Boolean = false,
    val selectedInstrument: Instrument? = null,
    val selectedTicker: String? = selectedInstrument?.ticker,
    val currentPrice: Double? = null,
    val pairCurrentPrice: Double? = null,
    val isPriceLoading: Boolean = false,
    val portfolioPositions: List<Position> = emptyList(),
    val lastSelectedInstruments: List<SelectedInstrumentInfo> = emptyList(),
    val isSearchActive: Boolean = false, // <-- для управления SearchBar
    // val orderType: BrokerOrderType = BrokerOrderType.MARKET,   // тип заявки (рыночная/лимитная)
    val limitPrice: String = "", // цена для лимитной заявки
    val selectedPriceChangePercent: Double? = null, // изменение цены выбранного инструмента
    // Диалог брокера
    val showBrokerDialog: Boolean = false, // флаг открытия диалога
    val dialogInstrumentTicker: String? = null,
    val selectedBroker: BrokerName = BrokerName.TINVEST, // выбранный брокер в диалоге
    val selectedAccountIdDialog: String? = null, // выбранный счёт в диалоге
    val dialogAccounts: List<BrokerAccount> = emptyList(), // счета для диалога
    val searchBroker: BrokerName = BrokerName.TINVEST, // брокер для основного поиска
    // Парная торговля
    val pairTradingEnabled: Boolean = false,
    val pairSearchQuery: String = "", // запрос второго поиска
    val pairSearchResults: List<Instrument> = emptyList(), // результаты второго поиска
    val isPairSearching: Boolean = false, // индикатор загрузки второго поиска
    val pairedInstrument: Instrument? = null, // выбранный парный инструмент
    val pairedMultiplier: String = "1", // множитель (по умолчанию 10)
    val swipeResetTrigger: Boolean = false,
    val pairSearchBroker: BrokerName = BrokerName.TINVEST, // брокер для парного поиска
    val currentPointValue: Double? = null,
    val pairedPointValue: Double? = null,
    // M008: pairExecPrice / pairedOrderType — вычисляются в VM
    // (updatePairExecDetails), не в Composable.
    val pairExecPrice: Double = 0.0,
    val pairedOrderType: OrderTypeSelection? = null,
    // Стоп‑заявки
    val orderType: OrderTypeSelection = OrderTypeSelection.Market, // было BrokerOrderType.MARKET
    val stopPrice: String = "",
    val expirationType: StopOrderExpirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
    val expireDate: String? = null,
    // M012: диалоги выбора брокера для поиска (не путать с showBrokerDialog
    // — тот для выбора счёта при торговле).
    val showSearchBrokerDialog: Boolean = false,
    val selectedSearchBroker: BrokerName = BrokerName.TINVEST,
    val showPairSearchBrokerDialog: Boolean = false,
    val selectedPairSearchBroker: BrokerName = BrokerName.TINVEST,
    // M009: диалог подтверждения заявки + направление pending-заявки.
    // Переживает config change (в отличие от remember { mutableStateOf } в Screen).
    val showConfirmDialog: Boolean = false,
    val pendingDirection: OrderDirection? = null,
    val tradingStatuses: Map<String, TradingAvailability> = emptyMap(),
    // поле stopOrderType удалено, теперь берём из orderType.stopOrderType
    val freeBalance: Double? = null,
    val executionPrice: Double = 0.0,
    val costOverlay: String? = null,
    val multiplierOverlay: String? = null,
) {
    val isFormValid: Boolean
        get() {
            val basicValid =
                selectedInstrument != null &&
                    quantity.toLongOrNull()?.let { it > 0 } == true &&
                    isApiInitialized
            val limitPriceValid =
                if (orderType is OrderTypeSelection.Limit || orderType is OrderTypeSelection.StopLimit) {
                    limitPrice.toDoubleOrNull() != null
                } else {
                    true
                }
            val stopPriceValid =
                if (orderType is OrderTypeSelection.StopLoss ||
                    orderType is OrderTypeSelection.TakeProfit ||
                    orderType is OrderTypeSelection.StopLimit
                ) {
                    stopPrice.toDoubleOrNull()?.let { it > 0 } == true
                } else {
                    true
                }
            val pairValid =
                if (pairTradingEnabled) {
                    pairedInstrument != null && pairedMultiplier.toDoubleOrNull()?.let { it > 0.0 } == true
                } else {
                    true
                }
            return basicValid && limitPriceValid && stopPriceValid && pairValid
        }
    val quantityAsLong: Long? get() = quantity.toLongOrNull()

    fun clearStatus(): OrdersUiState = copy(statusMessage = null, isError = false)
}
