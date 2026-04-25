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

    companion object {
        private const val TAG = "OrdersViewModel"
    }

    init { checkApiInitialization() }

    fun checkApiInitialization() {
        val isApiInit = ServiceLocator.getApiOrNull() != null
        _uiState.update { it.copy(isApiInitialized = isApiInit) }
        if (isApiInit) {
            loadAccounts()
            viewModelScope.launch {
                loadPortfolio()
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
        val accountId = state.selectedAccountId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                val sandboxMode = ServiceLocator.isSandboxMode()
                val result = repository.postMarketOrder(figi = figi, quantity = quantity, direction = direction, accountId = accountId, sandboxMode = sandboxMode)
                // Дожидаемся актуального портфеля
                val updatedPositions = loadPortfolio()

// Обновляем карточки последних просмотренных
                val updatedLastSelected = _uiState.value.lastSelectedInstruments.map { card ->
                    val pos = updatedPositions.find { it.figi == card.instrument.figi }
                    card.copy(
                        quantity = pos?.quantity ?: 0L,
                        averagePrice = pos?.currentPrice ?: card.averagePrice,
                        profit = pos?.profit ?: 0.0,
                        profitPercent = pos?.profitPercent ?: 0.0
                    )
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "✅ Заявка выполнена!...",
                        isError = false,
                        quantity = "",
                        lastSelectedInstruments = updatedLastSelected
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "❌ Ошибка: ${e.message}", isError = true) }
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