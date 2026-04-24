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

/**
 * ViewModel для экрана выставления заявок.
 * Управляет поиском инструментов, формой заявки, загрузкой счетов,
 * получением рыночной цены и списком последних просмотренных карточек.
 */
class OrdersViewModel(
    private val repository: InvestRepository
) : ViewModel() {

    companion object {
        private const val TAG = "OrdersViewModel"
    }

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        checkApiInitialization()
    }

    /**
     * Проверяет, инициализирован ли API (через ServiceLocator), обновляет UI и загружает счета/портфель.
     */
    fun checkApiInitialization() {
        val isApiInit = ServiceLocator.getApiOrNull() != null
        _uiState.update { it.copy(isApiInitialized = isApiInit) }
        if (isApiInit) {
            loadAccounts()
            loadPortfolio()
        }
    }

    /**
     * Инициализирует API (вызывается из SettingsScreen) и обновляет состояние.
     */
    fun initializeApi(token: String, sandboxMode: Boolean) {
        try {
            ServiceLocator.createApi(token, sandboxMode)
            _uiState.update {
                it.copy(
                    isApiInitialized = true,
                    statusMessage = "API подключен (режим: ${if (sandboxMode) "песочница" else "боевой"})",
                    isError = false
                )
            }
            loadAccounts()
            loadPortfolio()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    statusMessage = "Ошибка подключения: ${e.message}",
                    isError = true
                )
            }
        }
    }

    /**
     * Загружает список торговых счетов пользователя.
     */
    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val sandboxMode = ServiceLocator.isSandboxMode()
                val accounts = repository.getAccounts(sandboxMode)
                val defaultAccount = accounts.firstOrNull()
                _uiState.update {
                    it.copy(
                        accounts = accounts,
                        selectedAccountId = defaultAccount?.id,
                        isLoading = false,
                        statusMessage = if (accounts.isEmpty()) "Нет доступных счетов" else "Загружено ${accounts.size} счёт(ов)"
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
     * Загружает портфель для первого счета и сохраняет позиции в состояние.
     */
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
            } catch (e: Exception) {
                // Портфель может быть временно недоступен — это не критично
            }
        }
    }

    // ... (методы onSearchQueryChanged, onInstrumentSelected, postOrder и т.д. – полностью сохранены из предыдущей версии)

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                selectedInstrument = null,
                figi = ""
            )
        }
        searchJob?.cancel()
        if (query.length >= 2) {
            searchJob = viewModelScope.launch {
                try {
                    delay(500) // debounce
                    _uiState.update { it.copy(isSearching = true) }
                    val results = repository.searchInstruments(query)
                    _uiState.update {
                        it.copy(
                            searchResults = results,
                            isSearching = false
                        )
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    _uiState.update { it.copy(isSearching = false) }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            searchResults = emptyList(),
                            isSearching = false,
                            statusMessage = "Ошибка поиска: ${e.message}",
                            isError = true
                        )
                    }
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearching = false
                )
            }
        }
    }

    fun onInstrumentSelected(instrument: InstrumentUi) {
        _uiState.update {
            it.copy(
                selectedInstrument = instrument,
                figi = instrument.figi,
                searchQuery = "${instrument.ticker} - ${instrument.name}",
                searchResults = emptyList()
            )
        }

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

            val updatedList = listOf(newCard) + _uiState.value.lastSelectedInstruments
            _uiState.update {
                it.copy(
                    currentPrice = price,
                    isPriceLoading = false,
                    lastSelectedInstruments = updatedList.take(2)
                )
            }
        }
    }

    fun clearSearch() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                selectedInstrument = null,
                figi = "",
                currentPrice = null,
                isPriceLoading = false
            )
        }
    }

    fun onFigiChanged(figi: String) {
        _uiState.update { it.copy(figi = figi.uppercase()) }
    }

    fun onQuantityChanged(quantity: String) {
        val filtered = quantity.filter { it.isDigit() }
        _uiState.update { it.copy(quantity = filtered) }
    }

    fun onAccountSelected(accountId: String) {
        _uiState.update { it.copy(selectedAccountId = accountId) }
    }

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
                val result = repository.postMarketOrder(
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
                        quantity = "" // сбрасываем количество, чтобы не повторить случайно
                    )
                }
                // Обновляем портфель после сделки
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
    }

    fun clearStatus() { _uiState.update { it.clearStatus() } }
    fun retryLoadAccounts() { loadAccounts() }
}

/**
 * Фабрика для создания OrdersViewModel с внедрением InvestRepository через BrokerManager.
 */
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