package com.example.tscalp.presentation.screens.orders

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tscalp.data.repository.InstrumentUi
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
import ru.tinkoff.piapi.contract.v1.OrderDirection
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
        _uiState.update { it.copy(searchQuery = query, selectedInstrument = null, figi = "") }
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
        // Очищаем поисковый запрос, чтобы не было повторного поиска при открытии SearchBar
        _uiState.update {
            it.copy(
                selectedInstrument = instrument,
                figi = instrument.figi,
                searchQuery = "",                     // <-- очищаем, а не заполняем
                searchResults = emptyList()
            )
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPriceLoading = true) }
            val prices = repository.getLastPrices(listOf(instrument.figi))
            val price = prices[instrument.figi]
            val portfolioPos = _uiState.value.portfolioPositions.find { it.figi == instrument.figi }

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

            // Удаляем старую карточку с таким же FIGI (если была) и добавляем новую в начало
            val currentList = _uiState.value.lastSelectedInstruments.toMutableList()
            currentList.removeAll { it.instrument.figi == instrument.figi }
            currentList.add(0, newCard)

            val updatedList = currentList.take(2)   // ограничиваем двумя элементами

            _uiState.update {
                it.copy(
                    currentPrice = price,
                    isPriceLoading = false,
                    lastSelectedInstruments = updatedList
                )
            }
        }
    }

    fun removeLastSelectedInstrument(figi: String) {
        _uiState.update { state ->
            state.copy(lastSelectedInstruments = state.lastSelectedInstruments.filter { it.instrument.figi != figi })
        }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active) }
        if (active) { _uiState.update { it.copy(searchResults = emptyList(), searchQuery = "") } }
    }

    fun clearSearch() { _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), selectedInstrument = null, figi = "", currentPrice = null, isPriceLoading = false, isSearchActive = false) } }
    fun onFigiChanged(figi: String) { _uiState.update { it.copy(figi = figi.uppercase()) } }
    fun onQuantityChanged(quantity: String) { _uiState.update { it.copy(quantity = quantity.filter { it.isDigit() }) } }
    fun onAccountSelected(accountId: String) { _uiState.update { it.copy(selectedAccountId = accountId) } }
    fun onBuyClick() = postOrder(OrderDirection.ORDER_DIRECTION_BUY)
    fun onSellClick() = postOrder(OrderDirection.ORDER_DIRECTION_SELL)

    private fun postOrder(direction: OrderDirection) {
        val state = _uiState.value
        val figi = state.figi
        val quantity = state.quantityAsLong ?: return
        // Определяем, есть ли персональные настройки для выбранного инструмента
        val activeCard = state.lastSelectedInstruments.find { it.instrument.figi == figi }
        val brokerName = activeCard?.brokerName ?: "tinkoff"
        val accountId = activeCard?.accountId ?: state.selectedAccountId ?: return
        val sandboxMode = ServiceLocator.isSandboxMode() // или можно разрешить переопределение в карточке, но пока так
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                val sandboxMode = ServiceLocator.isSandboxMode()
                // Используем новый метод с указанием брокера
                val result = repository.postMarketOrder(
                    brokerName = brokerName,
                    figi = figi,
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
                // Обновляем портфель (пока только для дефолтного брокера)
                loadPortfolio()
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

        // После успешной отправки основной заявки и до того, как метод завершится
        if (state.pairTradingEnabled && state.pairedInstrument != null) {
            val multiplier = state.pairedMultiplier.toDoubleOrNull() ?: 1.0
            val pairedQuantity = (multiplier * quantity).toLong()

            if (pairedQuantity > 0) {
                val pairedDirection = if (direction == OrderDirection.ORDER_DIRECTION_BUY)
                    OrderDirection.ORDER_DIRECTION_SELL
                else
                    OrderDirection.ORDER_DIRECTION_BUY

                // orderType и price уже есть из состояния выше
                // Перед вызовом контрсделки явно возьмите тип заявки и цену из стейта
                val orderType = state.orderType
                val price = if (orderType == OrderType.ORDER_TYPE_LIMIT) {
                    // Преобразование строки limitPrice в Quotation (как в основной заявке)
                    val limitPrice = state.limitPrice.toDoubleOrNull() ?: 0.0
                    val units = limitPrice.toLong()
                    val nano = ((limitPrice - units) * 1_000_000_000).toInt()
                    Quotation.newBuilder().setUnits(units).setNano(nano).build()
                } else {
                    Quotation.newBuilder().setUnits(0).setNano(0).build()
                }

                try {
                    // ... затем используйте orderType и price при вызове repository.postOrder для контрсделки
                    val pairedResult = repository.postOrder(
                        brokerName = brokerName,
                        figi = state.pairedInstrument.figi,
                        quantity = pairedQuantity,
                        direction = pairedDirection,
                        accountId = accountId,
                        sandboxMode = sandboxMode,
                        orderType = orderType,   // теперь объявлена
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
        // Собираем FIGI из последних просмотренных и выбранного инструмента
        val figisToUpdate = state.lastSelectedInstruments.map { it.instrument.figi }.toMutableSet()
        state.selectedInstrument?.let { figisToUpdate.add(it.figi) }
        if (figisToUpdate.isEmpty()) return

        try {
            val prices = repository.getLastPrices(figisToUpdate.toList())
            // Обновляем lastSelectedInstruments
            val updatedLastSelected = state.lastSelectedInstruments.map { card ->
                val newPrice = prices[card.instrument.figi] ?: card.currentPrice
                // Сравниваем с предыдущей ценой (если есть)
                val previousPrice = card.currentPrice ?: newPrice
                val changePercent = if (previousPrice != null && previousPrice != 0.0 && newPrice != null) {
                    ((newPrice - previousPrice) / previousPrice) * 100.0
                } else null

                card.copy(
                    currentPrice = newPrice,
                    previousPrice = previousPrice,
                    priceChangePercent = changePercent
                )
            }
            // Обновляем currentPrice, если выбранный инструмент совпадает
            val newCurrentPrice = state.selectedInstrument?.let { sel ->
                prices[sel.figi] ?: state.currentPrice
            }
            _uiState.update {
                it.copy(
                    lastSelectedInstruments = updatedLastSelected,
                    currentPrice = newCurrentPrice
                )
            }
        } catch (e: Exception) {
            // тихо игнорируем, чтобы не спамить ошибками
        }
    }

    /**
     * Открывает диалог настроек для указанного инструмента.
     */
    fun openBrokerDialog(instrumentFigi: String) {
        _uiState.update {
            it.copy(
                showBrokerDialog = true,
                dialogInstrumentFigi = instrumentFigi,
                selectedBroker = "tinkoff",                // по умолчанию Т‑Инвестиции
                selectedAccountIdDialog = null              // счёт будет выбран позже
            )
        }
        // Загружаем счета для брокера по умолчанию (можно сделать асинхронно, но пока синхронно через launch)
        viewModelScope.launch {
            loadDialogAccounts("tinkoff")
        }
    }

    /**
     * Закрывает диалог без сохранения.
     */
    fun closeBrokerDialog() {
        _uiState.update { it.copy(showBrokerDialog = false, dialogInstrumentFigi = null) }
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
        val figi = _uiState.value.dialogInstrumentFigi ?: return
        val broker = _uiState.value.selectedBroker
        val accountId = _uiState.value.selectedAccountIdDialog

        // Обновляем соответствующую карточку в lastSelectedInstruments
        _uiState.update { state ->
            state.copy(
                lastSelectedInstruments = state.lastSelectedInstruments.map { card ->
                    if (card.instrument.figi == figi) {
                        card.copy(brokerName = broker, accountId = accountId)
                    } else card
                },
                showBrokerDialog = false,
                dialogInstrumentFigi = null
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