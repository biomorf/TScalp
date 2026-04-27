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
import ru.tinkoff.piapi.contract.v1.OrderDirection
import ru.tinkoff.piapi.contract.v1.OrderType
import ru.tinkoff.piapi.contract.v1.Quotation
import com.example.tscalp.domain.models.PortfolioPosition

class OrdersViewModel(
    private val repository: InvestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var priceUpdateJob: Job? = null
    private var pairSearchJob: Job? = null

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
            ServiceLocator.createApi(token, sandboxMode)
            _uiState.update { it.copy(isApiInitialized = true, statusMessage = "API подключен (режим: ${if (sandboxMode) "песочница" else "боевой"})", isError = false) }
            loadAccounts()
            viewModelScope.launch {
                loadPortfolio()
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(statusMessage = "Ошибка подключения: ${e.message}", isError = true) }
        }
    }

    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val sandboxMode = ServiceLocator.isSandboxMode()
                val accounts = repository.getAccounts(sandboxMode)
                val defaultAccount = accounts.firstOrNull()
                _uiState.update { it.copy(accounts = accounts, selectedAccountId = defaultAccount?.id, isLoading = false, statusMessage = if (accounts.isEmpty()) "Нет доступных счетов" else "Загружено ${accounts.size} счёт(ов)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Ошибка загрузки счетов: ${e.message}", isError = true) }
            }
        }
    }

    /**
     * Загружает портфель для первого счета и возвращает список позиций.
     * Теперь это suspend-функция, которую можно await'ить.
     */
    private suspend fun loadPortfolio(): List<PortfolioPosition> {
        return try {
            val sandboxMode = ServiceLocator.isSandboxMode()
            val accounts = repository.getAccounts(sandboxMode)
            if (accounts.isNotEmpty()) {
                val accountId = accounts.first().id
                val positions = repository.getPortfolio(accountId, sandboxMode)
                _uiState.update { it.copy(portfolioPositions = positions) }
                positions
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки портфеля", e)
            emptyList()
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
        _uiState.update {
            it.copy(
                selectedInstrument = instrument,
                ticker = instrument.ticker,          // <-- теперь ticker
                searchQuery = "${instrument.ticker} - ${instrument.name}",
                searchResults = emptyList()
            )
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPriceLoading = true) }
            val prices = repository.getLastPrices(listOf(instrument.ticker))
            val price = prices[instrument.ticker]
            val portfolioPos = _uiState.value.portfolioPositions.find { it.ticker == instrument.ticker }

            val newCard = SelectedInstrumentInfo(
                instrument = instrument,
                currentPrice = price,
                priceChange = null,
                priceChangePercent = null,
                quantity = portfolioPos?.quantity ?: 0L,
                averagePrice = portfolioPos?.currentPrice,
                profit = portfolioPos?.profit,
                profitPercent = portfolioPos?.profitPercent
            )

            val currentList = _uiState.value.lastSelectedInstruments.toMutableList()
            currentList.removeAll { it.instrument.ticker == instrument.ticker }
            currentList.add(0, newCard)

            _uiState.update {
                it.copy(
                    currentPrice = price,
                    isPriceLoading = false,
                    lastSelectedInstruments = currentList.take(5)   // храним 5 последних
                )
            }
        }
    }

    fun removeLastSelectedInstrument(figi: String) {
        _uiState.update { state ->
            state.copy(lastSelectedInstruments = state.lastSelectedInstruments.filter { it.instrument.figi != figi })
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
    fun onFigiChanged(figi: String) { _uiState.update { it.copy(ticker = figi.uppercase()) } }
    fun onQuantityChanged(quantity: String) { _uiState.update { it.copy(quantity = quantity.filter { it.isDigit() }) } }
    fun onAccountSelected(accountId: String) { _uiState.update { it.copy(selectedAccountId = accountId) } }
    fun onBuyClick() = postOrder(OrderDirection.ORDER_DIRECTION_BUY)
    fun onSellClick() = postOrder(OrderDirection.ORDER_DIRECTION_SELL)

    private fun postOrder(direction: OrderDirection) {
        val state = _uiState.value
        val ticker = state.ticker.ifBlank { state.selectedInstrument?.ticker } ?: return
        val quantity = state.quantityAsLong ?: return
        val activeCard = state.lastSelectedInstruments.find { it.instrument.ticker == ticker }
        val brokerName = activeCard?.brokerName ?: "tinkoff"
        val accountId = activeCard?.accountId ?: state.selectedAccountId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                val sandboxMode = ServiceLocator.isSandboxMode()
                val result = repository.postMarketOrder(
                    brokerName = brokerName,
                    ticker = ticker,
                    quantity = quantity,
                    direction = direction,
                    accountId = accountId,
                    sandboxMode = sandboxMode
                )

                val directionText = when (direction) {
                    OrderDirection.ORDER_DIRECTION_BUY -> "покупка"
                    OrderDirection.ORDER_DIRECTION_SELL -> "продажа"
                    else -> "операция"
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "✅ Заявка на $directionText выполнена!\nID: ${result.orderId}\nИсполнено: ${result.executedLots}/${result.totalLots} лотов",
                        isError = false,
                        quantity = ""
                    )
                }
                loadPortfolio()

                // --- Контрсделка (внутри того же try-catch, после успеха основной заявки) ---
                if (state.pairTradingEnabled && state.pairedInstrument != null) {
                    val multiplier = state.pairedMultiplier.toDoubleOrNull() ?: 1.0
                    val pairedQuantity = (multiplier * quantity).toLong()

                    if (pairedQuantity > 0) {
                        val pairedDirection = if (direction == OrderDirection.ORDER_DIRECTION_BUY)
                            OrderDirection.ORDER_DIRECTION_SELL
                        else
                            OrderDirection.ORDER_DIRECTION_BUY

                        val orderType = state.orderType
                        val price = if (orderType == OrderType.ORDER_TYPE_LIMIT) {
                            val limitPrice = state.limitPrice.toDoubleOrNull() ?: 0.0
                            val units = limitPrice.toLong()
                            val nano = ((limitPrice - units) * 1_000_000_000).toInt()
                            Quotation.newBuilder().setUnits(units).setNano(nano).build()
                        } else {
                            Quotation.newBuilder().setUnits(0).setNano(0).build()
                        }

                        try {
                            val pairedResult = repository.postOrder(
                                brokerName = brokerName,
                                ticker = state.pairedInstrument.ticker,
                                quantity = pairedQuantity,
                                direction = pairedDirection,
                                accountId = accountId,
                                sandboxMode = sandboxMode,
                                orderType = orderType,
                                price = price
                            )
                            _uiState.update {
                                it.copy(statusMessage = it.statusMessage + "\n✅ Контрсделка: ${state.pairedInstrument.ticker} $pairedQuantity лотов")
                            }
                        } catch (e: Exception) {
                            _uiState.update {
                                it.copy(statusMessage = it.statusMessage + "\n❌ Ошибка контрсделки: ${e.message}", isError = true)
                            }
                        }
                    }
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

    fun clearStatus() { _uiState.update { it.clearStatus() } }
    fun retryLoadAccounts() { loadAccounts() }

    private fun startPriceUpdates() {
        priceUpdateJob?.cancel()
        priceUpdateJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000) // каждые 5 секунд
                updatePrices()
            }
        }
    }

    private suspend fun updatePrices() {
        val state = _uiState.value
        val tickersToUpdate = state.lastSelectedInstruments.map { it.instrument.ticker }.toMutableSet()
        state.selectedInstrument?.ticker?.let { tickersToUpdate.add(it) }

        if (tickersToUpdate.isEmpty()) return

        val prices = repository.getLastPricesByTicker(tickersToUpdate.toList())

            // Обновляем lastSelectedInstruments
            val updatedLastSelected = state.lastSelectedInstruments.map { card ->
                val newPrice = prices[card.instrument.ticker] ?: card.currentPrice
                val changePercent = if (card.currentPrice != null && card.currentPrice != 0.0 && newPrice != null) {
                    ((newPrice - card.currentPrice) / card.currentPrice) * 100.0
                } else null
                card.copy(
                    currentPrice = newPrice,
                    previousPrice = card.currentPrice,
                    priceChangePercent = changePercent
                )
            }

            // Обновляем цену и изменение для выбранного инструмента
            var newCurrentPrice = state.currentPrice
            var selectedChange = state.selectedPriceChangePercent
            val selectedTicker = state.selectedInstrument?.ticker
            if (selectedTicker != null) {
                val freshPrice = prices[selectedTicker]
                if (freshPrice != null) {
                    val prevPrice = state.currentPrice
                    selectedChange = if (prevPrice != null && prevPrice != 0.0) {
                        ((freshPrice - prevPrice) / prevPrice) * 100.0
                    } else null
                    newCurrentPrice = freshPrice
                }
            }

            _uiState.update {
                it.copy(
                    lastSelectedInstruments = updatedLastSelected,
                    currentPrice = newCurrentPrice,
                    selectedPriceChangePercent = selectedChange
                )
            }
    }


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
            dialogInstrumentTicker = ticker,   // новое поле вместо figi
            selectedBroker = existingCard?.brokerName ?: "tinkoff",
            selectedAccountIdDialog = existingCard?.accountId
        )
    }
    viewModelScope.launch {
        loadDialogAccounts(existingCard?.brokerName ?: "tinkoff")
    }
}

    /**
     * Закрывает диалог без сохранения.
     */
    fun closeBrokerDialog() {
        _uiState.update { it.copy(showBrokerDialog = false, dialogInstrumentTicker = null) }
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
                dialogInstrumentTicker = null
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