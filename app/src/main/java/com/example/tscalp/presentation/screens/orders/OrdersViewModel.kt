package com.example.tscalp.presentation.screens.orders

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
import ru.tinkoff.piapi.contract.v1.OrderDirection

class OrdersViewModel(
    private val repository: InvestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    init { checkApiInitialization() }

    fun checkApiInitialization() {
        val isApiInit = ServiceLocator.getApiOrNull() != null
        _uiState.update { it.copy(isApiInitialized = isApiInit) }
        if (isApiInit) { loadAccounts(); loadPortfolio() }
    }

    fun initializeApi(token: String, sandboxMode: Boolean) {
        try {
            ServiceLocator.createApi(token, sandboxMode)
            _uiState.update { it.copy(isApiInitialized = true, statusMessage = "API подключен (режим: ${if (sandboxMode) "песочница" else "боевой"})", isError = false) }
            loadAccounts(); loadPortfolio()
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

    private fun loadPortfolio() {
        viewModelScope.launch {
            try {
                val sandboxMode = ServiceLocator.isSandboxMode()
                val accounts = repository.getAccounts(sandboxMode)
                if (accounts.isNotEmpty()) {
                    val accountId = accounts.first().id
                    val positions = repository.getPortfolio(accountId, sandboxMode)
                    _uiState.update { it.copy(portfolioPositions = positions) }
                }
            } catch (_: Exception) { }
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
        // Обновляем состояние формы (очищаем поисковый запрос, чтобы не было повторного поиска)
        _uiState.update {
            it.copy(
                selectedInstrument = instrument,
                figi = instrument.figi,
                searchQuery = "",          // очищаем, чтобы не триггерить поиск при открытии SearchBar
                searchResults = emptyList()
            )
        }

        // Загружаем цену и обновляем список последних просмотренных
        viewModelScope.launch {
            _uiState.update { it.copy(isPriceLoading = true) }
            val price = repository.getLastPrice(instrument.figi)
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

            // Удаляем старую карточку с таким же FIGI (если есть) и добавляем новую в начало
            val currentList = _uiState.value.lastSelectedInstruments.toMutableList()
            currentList.removeAll { it.instrument.figi == instrument.figi }
            currentList.add(0, newCard)

            // Ограничиваем двумя элементами
            _uiState.update {
                it.copy(
                    currentPrice = price,
                    isPriceLoading = false,
                    lastSelectedInstruments = currentList.take(2)
                )
            }
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
                _uiState.update { it.copy(isLoading = false, statusMessage = "✅ Заявка выполнена!\nID: ${result.orderId}\nИсполнено: ${result.executedLots}/${result.totalLots} лотов", isError = false, quantity = "") }
                loadPortfolio()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "❌ Ошибка: ${e.message}", isError = true) }
            }
        }
    }

    fun clearStatus() { _uiState.update { it.clearStatus() } }
    fun retryLoadAccounts() { loadAccounts() }
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