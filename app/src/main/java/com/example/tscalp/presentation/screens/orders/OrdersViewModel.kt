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

class OrdersViewModel(
    private val apiService: TinkoffInvestService
) : ViewModel() {

    private val repository = InvestRepository(apiService)

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    init {
        checkApiInitialization()
    }

    private fun checkApiInitialization() {
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