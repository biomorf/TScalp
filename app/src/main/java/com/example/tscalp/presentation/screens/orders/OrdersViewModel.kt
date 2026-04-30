package com.example.tscalp.presentation.screens.orders

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tscalp.domain.models.InstrumentUi
import com.example.tscalp.data.repository.InvestRepository
import com.example.tscalp.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch


import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.data.api.TInvestInvestService
import com.example.tscalp.domain.models.BrokerOrderType
import com.example.tscalp.domain.models.OrderTypeSelection
import com.example.tscalp.domain.models.BrokerOrderRequest
//import com.example.tscalp.domain.models.OrderType
import com.example.tscalp.domain.models.OrderDirection
import com.example.tscalp.domain.models.StopOrderType
import com.example.tscalp.domain.models.StopOrderRequest


class OrdersViewModel(
    private val repository: InvestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var pairSearchJob: Job? = null
    private var priceStreamJob: Job? = null



    companion object {
        private const val TAG = "OrdersViewModel"
    }

    init { checkApiInitialization() }

    fun checkApiInitialization() {
        val isAnyApiInit = ServiceLocator.isAnyBrokerInitialized()
        _uiState.update { it.copy(isApiInitialized = isAnyApiInit) }
        if (isAnyApiInit) {
            // Загружаем счета только если дефолтный брокер инициализирован
            if (ServiceLocator.getBrokerManager().getDefaultBroker().isInitialized) {
                loadAccounts()
                viewModelScope.launch { loadPortfolio() }
            }
            startPriceUpdates()
        }
    }

    fun initializeApi(token: String, sandboxMode: Boolean) {
        try {
            ServiceLocator.saveBrokerCredentials("TInvest", token, sandboxMode)
            (ServiceLocator.getBrokerManager().getBroker("TInvest") as? TInvestInvestService)?.initializeFromSettings()

            _uiState.update {
                it.copy(
                    isApiInitialized = true,
                    statusMessage = "API подключен (режим: ${if (sandboxMode) "песочница" else "боевой"})",
                    isError = false
                )
            }
            loadAccounts()
            viewModelScope.launch { loadPortfolio() }   // <-- обернули в корутину
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    statusMessage = "Ошибка подключения: ${e.message}",
                    isError = true
                )
            }
        }
    }

    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val sandboxMode = ServiceLocator.isSandboxMode()
                // По умолчанию загружаем счета для Т-Инвестиций (основной брокер)
                val brokerName = "TInvest"
                val accounts = repository.getAccounts(brokerName, sandboxMode)
                //получения счетов добавьте автоматический выбор первого счёта, если ни один не выбран
                if (_uiState.value.selectedAccountId == null && accounts.isNotEmpty()) {
                    _uiState.update { it.copy(selectedAccountId = accounts.first().id) }
                }
                val defaultAccount = accounts.firstOrNull()
                _uiState.update {
                    it.copy(
                        accounts = accounts,
                        selectedAccountId = defaultAccount?.id,
                        isLoading = false,
                        statusMessage = if (accounts.isEmpty()) "Нет доступных счетов"
                        else "Загружено ${accounts.size} счёт(ов)"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Ошибка загрузки счетов: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    /**
     * Загружает портфель для первого счета и возвращает список позиций.
     * Теперь это suspend-функция, которую можно await'ить.
     */
    private suspend fun loadPortfolio(
        brokerName: String = "TInvest",
        accountId: String? = null
    ) {
        try {
            val sandboxMode = ServiceLocator.isSandboxMode()
            val broker = ServiceLocator.getBrokerManager().getBroker(brokerName) ?: return
            // Если accountId не передан, получаем его через getAccounts (только для TInvest)
            val actualAccountId = accountId ?: run {
                val accounts = broker.getAccounts(sandboxMode)
                accounts.firstOrNull()?.id ?: return
            }
            val newPositions = broker.getPositions(actualAccountId, sandboxMode)

            // Обновляем portfolioPositions: удаляем старые позиции этого брокера и добавляем новые
            val currentPositions = _uiState.value.portfolioPositions.toMutableList()
            currentPositions.removeAll { it.brokerName == brokerName }
            currentPositions.addAll(newPositions.map { it.copy(brokerName = brokerName) })
            _uiState.update { it.copy(portfolioPositions = currentPositions) }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки портфеля для $brokerName", e)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, selectedInstrument = null, ticker = "") }
        searchJob?.cancel()
        if (query.length >= 2) {
            searchJob = viewModelScope.launch {
                try {
                    delay(500)
                    _uiState.update { it.copy(isSearching = true) }
                    val results = repository.searchInstruments(query)
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    _uiState.update { it.copy(isSearching = false) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(searchResults = emptyList(), isSearching = false, statusMessage = "Ошибка поиска: ${e.message}", isError = true) }
                }
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }

    fun onInstrumentSelected(instrument: InstrumentUi) {
        // Обновляем выбранный инструмент и поисковую строку
        _uiState.update {
            it.copy(
                selectedInstrument = instrument,
                ticker = instrument.ticker,
                searchQuery = "${instrument.ticker} - ${instrument.name}",
                searchResults = emptyList()
            )
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPriceLoading = true) }

            // 1. Мгновенно получаем последнюю цену (чтобы не ждать стрим)
            val prices = repository.getLastPricesByTicker(listOf(instrument.ticker))
            val price = prices[instrument.ticker]

            // 2. Ищем позицию в портфеле
            val portfolioPos = _uiState.value.portfolioPositions.find { it.ticker == instrument.ticker }

            // 3. Берём существующую карточку (если была), чтобы сохранить настройки брокера/счёта
            val existingCard = _uiState.value.lastSelectedInstruments.find { it.instrument.ticker == instrument.ticker }

            val newCard = SelectedInstrumentInfo(
                instrument = instrument,
                currentPrice = price,
                priceChange = null,
                priceChangePercent = null,
                quantity = portfolioPos?.quantity ?: 0L,
                averagePrice = portfolioPos?.currentPrice,
                profit = portfolioPos?.profit,
                profitPercent = portfolioPos?.profitPercent,
                brokerName = existingCard?.brokerName ?: "TInvest",
                accountId = existingCard?.accountId
            )

            // 4. Обновляем список последних выбранных инструментов
            val currentList = _uiState.value.lastSelectedInstruments.toMutableList()
            currentList.removeAll { it.instrument.ticker == instrument.ticker }
            currentList.add(0, newCard)

            _uiState.update {
                it.copy(
                    currentPrice = price,
                    isPriceLoading = false,
                    lastSelectedInstruments = currentList.take(5)
                )
            }

            // 5. Запускаем стрим для реактивного обновления цены
            startPriceUpdates()
        }
    }

    fun clearSelectedInstrument() {
        _uiState.update {
            it.copy(
                selectedInstrument = null,
                ticker = "",
                searchQuery = "",
                currentPrice = null,
                isPriceLoading = false
            )
        }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active) }
        if (active) { _uiState.update { it.copy(searchResults = emptyList(), searchQuery = "") } }
    }

    fun clearSearch() { _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), selectedInstrument = null, ticker = "", currentPrice = null, isPriceLoading = false, isSearchActive = false) } }
    fun onQuantityChanged(quantity: String) { _uiState.update { it.copy(quantity = quantity.filter { it.isDigit() }) } }
    fun onAccountSelected(accountId: String) { _uiState.update { it.copy(selectedAccountId = accountId) } }
    fun onBuyClick() = postOrder(OrderDirection.BUY)
    fun onSellClick() = postOrder(OrderDirection.SELL)

    private fun postOrder(direction: OrderDirection) {
        val state = _uiState.value
        val ticker = state.ticker.ifBlank { state.selectedInstrument?.ticker } ?: return
        val quantity = state.quantityAsLong ?: return

        val activeCard = state.lastSelectedInstruments.find { it.instrument.ticker == ticker }
        val brokerName = activeCard?.brokerName ?: "TInvest"
        val accountId = activeCard?.accountId ?: state.selectedAccountId ?: return

        when (state.orderType) {
            // ──── ОБЫЧНАЯ / ЛИМИТНАЯ ЗАЯВКА ────
            OrderTypeSelection.Market, OrderTypeSelection.Limit -> {
                val regularOrderType = if (state.orderType == OrderTypeSelection.Market)
                    BrokerOrderType.MARKET
                else
                    BrokerOrderType.LIMIT

                val price = if (regularOrderType == BrokerOrderType.LIMIT)
                    state.limitPrice.toDoubleOrNull()
                else
                    null

                val request = BrokerOrderRequest(
                    brokerName = brokerName,
                    ticker = ticker,
                    quantity = quantity,
                    direction = direction,
                    accountId = accountId,
                    sandboxMode = ServiceLocator.isSandboxMode(),
                    type = regularOrderType,
                    price = price
                )

                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true, statusMessage = null) }
                    try {
                        val result = repository.postOrder(request)

                        val directionText = when (direction) {
                            OrderDirection.BUY -> "покупка"
                            OrderDirection.SELL -> "продажа"
                            else -> "операция"
                        }
                        var finalMessage = "✅ Заявка на $directionText выполнена!\n" +
                                "ID: ${result.orderId}\n" +
                                "Исполнено: ${result.executedLots}/${result.totalLots} лотов"

                        // ──── КОНТРСДЕЛКА ────
                        if (state.pairTradingEnabled && state.pairedInstrument != null) {
                            val multiplier = state.pairedMultiplier.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
                            val pairedQuantity = (quantity * multiplier).toLong()
                            if (pairedQuantity > 0) {
                                val pairedDirection = if (direction == OrderDirection.BUY)
                                    OrderDirection.SELL
                                else
                                    OrderDirection.BUY

                                val pairedCard = state.lastSelectedInstruments.find {
                                    it.instrument.ticker == state.pairedInstrument?.ticker
                                }
                                val pairedBrokerName = pairedCard?.brokerName ?: brokerName
                                val pairedAccountId = pairedCard?.accountId ?: accountId

                                try {
                                    val pairedRequest = BrokerOrderRequest(
                                        brokerName = pairedBrokerName,
                                        ticker = state.pairedInstrument.ticker,
                                        quantity = pairedQuantity,
                                        direction = pairedDirection,
                                        accountId = pairedAccountId,
                                        sandboxMode = ServiceLocator.isSandboxMode(),
                                        type = regularOrderType,
                                        price = price
                                    )
                                    val pairedResult = repository.postOrder(pairedRequest)
                                    finalMessage += "\n✅ Контрсделка: ${state.pairedInstrument.ticker} $pairedQuantity лотов, ID: ${pairedResult.orderId}"
                                } catch (e: Exception) {
                                    finalMessage += "\n❌ Ошибка контрсделки: ${e.message}"
                                    Log.e(TAG, "Ошибка контрсделки", e)
                                }
                            }
                        }

                        loadPortfolio(brokerName, accountId)
                        refreshLastSelectedInstruments()

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                statusMessage = finalMessage,
                                isError = false,
                                quantity = "",
                                limitPrice = ""
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                statusMessage = "❌ Ошибка: ${e.message}",
                                isError = true
                            )
                        }
                    }
                }
            }

            // ──── СТОП-ЗАЯВКА ────
            OrderTypeSelection.StopLoss, OrderTypeSelection.TakeProfit, OrderTypeSelection.StopLimit -> {
                val stopPrice = state.stopPrice.toDoubleOrNull() ?: return
                if (stopPrice <= 0) return

                val stopOrderType = state.orderType.stopOrderType
                    ?: return // не должно случиться, но для безопасности

                val limitPrice = if (stopOrderType == StopOrderType.STOP_LIMIT)
                    state.limitPrice.toDoubleOrNull()
                else
                    null

                val stopRequest = StopOrderRequest(
                    brokerName = brokerName,
                    ticker = ticker,
                    quantity = quantity,
                    direction = direction,
                    accountId = accountId,
                    sandboxMode = ServiceLocator.isSandboxMode(),
                    stopPrice = stopPrice,
                    price = limitPrice,
                    stopOrderType = stopOrderType,
                    expirationType = state.expirationType
                )

                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true, statusMessage = null) }
                    try {
                        val stopId = repository.postStopOrder(stopRequest)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                statusMessage = "✅ Стоп‑заявка выставлена, ID: ${stopId.take(8)}…",
                                isError = false,
                                quantity = "",
                                stopPrice = "",
                                limitPrice = ""
                            )
                        }
                        loadPortfolio(brokerName, accountId)
                        refreshLastSelectedInstruments()
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                statusMessage = "❌ Ошибка стоп‑заявки: ${e.message}",
                                isError = true
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Обновляет список lastSelectedInstruments, подтягивая актуальные данные из портфеля.
     */
    private fun refreshLastSelectedInstruments() {
        val currentList = _uiState.value.lastSelectedInstruments
        if (currentList.isEmpty()) return

        val positions = _uiState.value.portfolioPositions
        val updatedList = currentList.map { card ->
            val pos = positions.find { it.ticker == card.instrument.ticker }
            card.copy(
                quantity = pos?.quantity ?: 0L,
                averagePrice = pos?.currentPrice ?: card.averagePrice,
                profit = pos?.profit ?: 0.0,
                profitPercent = pos?.profitPercent ?: 0.0
            )
        }
        _uiState.update { it.copy(lastSelectedInstruments = updatedList) }
    }

    fun clearStatus() { _uiState.update { it.clearStatus() } }
    fun retryLoadAccounts() { loadAccounts() }

//    fun startPriceUpdates() {
//        priceUpdateJob?.cancel()
//        priceUpdateJob = viewModelScope.launch {
//            while (isActive) {
//                delay(5_000) // каждые 5 секунд
//                updatePrices()
//            }
//        }
//    }
//
//    private suspend fun updatePrices() {
//        val state = _uiState.value
//        // Собираем тикеры только из visible карточек
//        val tickersToUpdate = state.lastSelectedInstruments.map { it.instrument.ticker }.toMutableSet()
//        state.selectedInstrument?.ticker?.let { tickersToUpdate.add(it) }
//        if (tickersToUpdate.isEmpty()) return
//
//        try {
//            val prices = repository.getLastPricesByTicker(tickersToUpdate.toList())
//
//            // Обновляем lastSelectedInstruments
//            val updatedLastSelected = state.lastSelectedInstruments.map { card ->
//                val newPrice = prices[card.instrument.ticker] ?: card.currentPrice
//                val changePercent = if (card.currentPrice != null && card.currentPrice != 0.0 && newPrice != null) {
//                    ((newPrice - card.currentPrice) / card.currentPrice) * 100.0
//                } else null
//                card.copy(currentPrice = newPrice, priceChangePercent = changePercent)
//            }
//
//            // Обновляем цену для выбранного инструмента
//            val selectedTicker = state.selectedInstrument?.ticker
//            val newSelectedPrice = selectedTicker?.let { prices[it] } ?: state.currentPrice
//            val selectedChange = if (state.currentPrice != null && state.currentPrice != 0.0 && newSelectedPrice != null) {
//                ((newSelectedPrice - state.currentPrice) / state.currentPrice) * 100.0
//            } else null
//
//            _uiState.update {
//                it.copy(
//                    lastSelectedInstruments = updatedLastSelected,
//                    currentPrice = newSelectedPrice,
//                    selectedPriceChangePercent = selectedChange
//                )
//            }
//        } catch (_: Exception) { }
//    }

//    fun stopPriceUpdates() {
//        priceUpdateJob?.cancel()
//    }

    /**
     * Открывает диалог настроек для указанного инструмента.
     */
/**
 * Открывает диалог настроек брокера/счёта для указанного тикера.
 */
fun openBrokerDialog(ticker: String) {
    val existingCard = _uiState.value.lastSelectedInstruments.find { it.instrument.ticker == ticker }
    _uiState.update {
        it.copy(
            showBrokerDialog = true,
            dialogInstrumentTicker = ticker,
            selectedBroker = existingCard?.brokerName ?: "TInvest",
            selectedAccountIdDialog = existingCard?.accountId
        )
    }
    viewModelScope.launch {
        loadDialogAccounts(existingCard?.brokerName ?: "TInvest")
    }
}

    /**
     * Закрывает диалог без сохранения.
     */
    fun closeBrokerDialog() {
        _uiState.update { it.copy(showBrokerDialog = false, dialogInstrumentTicker = null,
            swipeResetTrigger = !it.swipeResetTrigger) }
    }

    /**
     * Обрабатывает выбор брокера в диалоге – загружает его счета.
     */
    fun onBrokerSelected(brokerName: String) {
        _uiState.update { it.copy(selectedBroker = brokerName, selectedAccountIdDialog = null) }
        viewModelScope.launch {
            loadDialogAccounts(brokerName)
        }
    }

    /**
     * Обрабатывает выбор счёта в диалоге.
     */
    fun onAccountSelectedDialog(accountId: String) {
        _uiState.update { it.copy(selectedAccountIdDialog = accountId) }
    }

    /**
     * Сохраняет выбранные настройки для инструмента и закрывает диалог.
     */
    fun saveBrokerSettings() {
        val ticker = _uiState.value.dialogInstrumentTicker ?: return
        val broker = _uiState.value.selectedBroker
        val accountId = _uiState.value.selectedAccountIdDialog

        // Обновляем соответствующую карточку в lastSelectedInstruments
        _uiState.update { state ->
            state.copy(
                lastSelectedInstruments = state.lastSelectedInstruments.map { card ->
                    if (card.instrument.ticker == ticker) {
                        card.copy(brokerName = broker, accountId = accountId)
                    } else card
                },
                showBrokerDialog = false,
                dialogInstrumentTicker = null,
                swipeResetTrigger = !state.swipeResetTrigger   // вызываем анимацию
            )
        }
    }

    /**
     * Загружает счета для указанного брокера и сохраняет их во временный список (можно добавить поле в UIState).
     * Пока для простоты будем хранить список счетов в локальной переменной диалога.
     */
    private suspend fun loadDialogAccounts(brokerName: String) {
        try {
            val sandboxMode = ServiceLocator.isSandboxMode()
            val accounts = repository.getAccounts(brokerName, sandboxMode)
            // Сохраним счета в состоянии для диалога (можно добавить поле dialogAccounts: List<AccountUi>)
            _uiState.update { it.copy(dialogAccounts = accounts) }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось загрузить счета для $brokerName", e)
            //_uiState.update { it.copy(dialogAccounts = emptyList()) }
        }
    }

    fun setPairTradingEnabled(enabled: Boolean) {
        _uiState.update { it.copy(pairTradingEnabled = enabled) }
    }

    fun onPairSearchQueryChanged(query: String) {
        _uiState.update { it.copy(pairSearchQuery = query) }
        pairSearchJob?.cancel()
        if (query.length >= 2) {
            pairSearchJob = viewModelScope.launch {
                delay(500)
                _uiState.update { it.copy(isPairSearching = true) }
                try {
                    val results = repository.searchInstruments(query)
                    _uiState.update { it.copy(pairSearchResults = results, isPairSearching = false) }
                } catch (ce: CancellationException) {
                    _uiState.update { it.copy(isPairSearching = false) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(pairSearchResults = emptyList(), isPairSearching = false, statusMessage = "Ошибка поиска: ${e.message}", isError = true) }
                }
            }
        } else {
            _uiState.update { it.copy(pairSearchResults = emptyList(), isPairSearching = false) }
        }
    }

    fun onPairedInstrumentSelected(instrument: InstrumentUi) {
        _uiState.update { it.copy(pairedInstrument = instrument, pairSearchQuery = "${instrument.ticker} - ${instrument.name}", pairSearchResults = emptyList()) }
    }

    fun clearPairSearch() {
        _uiState.update { it.copy(pairSearchQuery = "", pairSearchResults = emptyList(), pairedInstrument = null) }
    }

    fun onPairedMultiplierChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(pairedMultiplier = filtered) }
    }

    fun onOrderTypeChanged(type: OrderTypeSelection) {
        _uiState.update { it.copy(orderType = type) }
        // Если нужно сбросить лимитную цену при переходе на рыночную, можно добавить
        if (type == OrderTypeSelection.Market) {
            _uiState.update { it.copy(limitPrice = "") }
        }
    }


    fun onLimitPriceChanged(price: String) {
        val filtered = price.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(limitPrice = filtered) }
    }

    fun onStopPriceChanged(price: String) {
        _uiState.update { it.copy(stopPrice = price.filter { it.isDigit() || it == '.' }) }
    }

//    fun onStopOrderTypeChanged(type: StopOrderType) {
//        _uiState.update { it.copy(stopOrderType = type) }
//    }

    fun startPriceUpdates() {
        stopPriceUpdates()
        val broker = ServiceLocator.getBrokerManager().getBroker("TInvest") as? TInvestInvestService ?: return
        val state = _uiState.value

        // Собираем figi для основного и парного инструментов
        val tickers = mutableListOf<String>()
        state.selectedInstrument?.let { tickers.add(it.ticker) }
        state.pairedInstrument?.let { tickers.add(it.ticker) }

        if (tickers.isEmpty()) return

        viewModelScope.launch {
            val figiList = tickers.mapNotNull { broker.resolveTicker(it) }
            if (figiList.isEmpty()) return@launch

            priceStreamJob = launch {
                broker.subscribeLastPrices(figiList)
                    .catch { e -> Log.e(TAG, "Price stream error", e) }
                    .collect { (figi, price) ->
                        _uiState.update { currentState ->
                            when (figi) {
                                currentState.selectedInstrument?.ticker?.let { broker.resolveTicker(it) } -> {
                                    currentState.copy(currentPrice = price)
                                }
                                // Для парного инструмента можно обновлять отдельное поле (пока пропускаем)
                                else -> currentState
                            }
                        }
                    }
            }
        }
    }

    fun stopPriceUpdates() {
        priceStreamJob?.cancel()
        priceStreamJob = null
    }
}

class OrdersViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrdersViewModel::class.java)) {
            val brokerManager = ServiceLocator.getBrokerManager()
            val repository = InvestRepository(brokerManager)
            @Suppress("UNCHECKED_CAST")
            return OrdersViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}