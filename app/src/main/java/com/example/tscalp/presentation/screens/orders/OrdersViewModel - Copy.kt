package com.example.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tscalp.data.api.TinkoffInvestService
import com.example.tscalp.data.repository.InvestRepository
import com.example.tscalp.domain.models.AccountUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tinkoff.piapi.contract.v1.OrderDirection

data class OrdersUiState(
    val figi: String = "",
    val quantity: String = "",
    val accounts: List<AccountUi> = emptyList(),
    val selectedAccountId: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val isApiInitialized: Boolean = false
) {
    val isFormValid: Boolean
        get() = figi.isNotBlank() && 
                quantity.toLongOrNull()?.let { it > 0 } == true && 
                selectedAccountId != null
}

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
                    statusMessage = "API инициализирован",
                    isError = false
                ) 
            }
            loadAccounts()
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    statusMessage = "Ошибка инициализации: ${e.message}",
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
                _uiState.update { 
                    it.copy(
                        accounts = accounts,
                        selectedAccountId = accounts.firstOrNull()?.id,
                        isLoading = false,
                        statusMessage = "Загружено ${accounts.size} счетов"
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
        _uiState.update { it.copy(quantity = quantity.filter { c -> c.isDigit() }) }
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
        val quantity = state.quantity.toLongOrNull() ?: return
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
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        statusMessage = "Заявка ${result.orderId} выполнена: ${result.executedLots}/${result.totalLots} лотов",
                        isError = false
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        statusMessage = "Ошибка: ${e.message}",
                        isError = true
                    ) 
                }
            }
        }
    }
    
    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}