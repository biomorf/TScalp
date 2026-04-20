package com.example.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tscalp.data.api.TinkoffInvestService
import com.example.tscalp.data.repository.InvestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tinkoff.piapi.contract.v1.OrderDirection
import ru.tinkoff.piapi.contract.v1.Instrument
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class OrdersViewModel(
    private val apiService: TinkoffInvestService
) : ViewModel() {

    private val repository = InvestRepository(apiService)

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    init {
        checkApiInitialization()
    }

    fun checkApiInitialization() {
        _uiState.update {
            it.copy(isApiInitialized = apiService.isInitialized)
        }
        if (apiService.isInitialized) {
            loadAccounts()
        }
    }

    fun initializeApi(token: String, sandboxMode: Boolean = true) {
        try {
            apiService.initialize(token, sandboxMode)
            _uiState.update {
                it.copy(
                    isApiInitialized = true,
                    statusMessage = "API подключен (режим: ${if (sandboxMode) "песочница" else "боевой"})",
                    isError = false
                )
            }
            loadAccounts()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    statusMessage = "Ошибка подключения: ${e.message}",
                    isError = true
                )
            }
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val accounts = repository.getAccounts()
                val defaultAccount = accounts.firstOrNull()
                _uiState.update {
                    it.copy(
                        accounts = accounts,
                        selectedAccountId = defaultAccount?.id,
                        isLoading = false,
                        statusMessage = if (accounts.isEmpty()) {
                            "Нет доступных счетов"
                        } else {
                            "Загружено ${accounts.size} счёт(ов)"
                        }
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

    fun onBuyClick() {
        postOrder(OrderDirection.ORDER_DIRECTION_BUY)
    }

    fun onSellClick() {
        postOrder(OrderDirection.ORDER_DIRECTION_SELL)
    }


    //FIGI to ticker
    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                selectedInstrument = null,
                figi = ""
            )
        }

        // Отменяем предыдущий поиск
        searchJob?.cancel()

        if (query.length >= 2) {
            searchJob = viewModelScope.launch {
                // Debounce — ждем 500мс перед поиском
                delay(500)

                _uiState.update { it.copy(isSearching = true) }
                try {
                    val results = repository.searchInstruments(query)
                    _uiState.update {
                        it.copy(
                            searchResults = results,
                            isSearching = false
                        )
                    }
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

    fun onInstrumentSelected(instrument: Instrument) {
        _uiState.update {
            it.copy(
                selectedInstrument = instrument,
                figi = instrument.figi,
                searchQuery = "${instrument.ticker} - ${instrument.name}",
                searchResults = emptyList()
            )
        }
    }

    fun clearSearch() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                selectedInstrument = null,
                figi = ""
            )
        }
    }


    private fun postOrder(direction: OrderDirection) {
        val state = _uiState.value
        val figi = state.figi
        val quantity = state.quantityAsLong ?: return
        val accountId = state.selectedAccountId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                val result = repository.postMarketOrder(
                    figi = figi,
                    quantity = quantity,
                    direction = direction,
                    accountId = accountId
                )

                val directionText = when (direction) {
                    OrderDirection.ORDER_DIRECTION_BUY -> "покупка"
                    OrderDirection.ORDER_DIRECTION_SELL -> "продажа"
                    else -> "операция"
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "✅ Заявка на $directionText выполнена!\n" +
                                "ID: ${result.orderId}\n" +
                                "Исполнено: ${result.executedLots}/${result.totalLots} лотов",
                        isError = false,
                        figi = "",
                        quantity = ""
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

    fun clearStatus() {
        _uiState.update { it.clearStatus() }
    }

    fun retryLoadAccounts() {
        if (apiService.isInitialized) {
            loadAccounts()
        }
    }
}