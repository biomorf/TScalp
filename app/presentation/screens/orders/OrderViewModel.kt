package com.example.tinvest.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tinvest.data.repository.InvestRepository
import com.example.tinvest.domain.models.AccountUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tinkoff.piapi.contract.v1.OrderDirection

data class OrderUiState(
    val figi: String = "",
    val quantity: String = "",
    val accounts: List<AccountUi> = emptyList(),
    val selectedAccountId: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false
) {
    val isFormValid: Boolean
        get() = figi.isNotBlank() && 
                quantity.toLongOrNull()?.let { it > 0 } == true && 
                selectedAccountId != null
}

class OrderViewModel(
    private val repository: InvestRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()
    
    init {
        loadAccounts()
    }
    
    private fun loadAccounts() {
        viewModelScope.launch {
            try {
                val accounts = repository.getAccounts()
                _uiState.update { 
                    it.copy(
                        accounts = accounts,
                        selectedAccountId = accounts.firstOrNull()?.id
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
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
                val orderId = repository.postMarketOrder(
                    figi = figi,
                    quantity = quantity,
                    direction = direction,
                    accountId = accountId
                )
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        statusMessage = "Заявка выставлена! ID: $orderId",
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
}
