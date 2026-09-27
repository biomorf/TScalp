package com.example.tscalp.ui.components

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.example.tscalp.data.api.TInvestInvestService
import com.example.tscalp.data.repository.InvestRepository
import com.example.tscalp.di.BrokerManager
import com.example.tscalp.domain.models.OrderListItem

@HiltViewModel
class OrdersListViewModel @Inject constructor(
    private val repository: InvestRepository,
    private val brokerManager: BrokerManager,
    private val sharedPrefs: SharedPreferences
) : ViewModel() {

    data class OrdersListState(
        val orders: List<OrderListItem> = emptyList(),
        val isLoading: Boolean = false,
        val statusMessage: String? = null,
        val isError: Boolean = false
    )

    private val _uiState = MutableStateFlow(OrdersListState())
    val uiState: StateFlow<OrdersListState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "OrdersListViewModel"
    }

    fun loadOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                val sandboxMode = sharedPrefs.getBoolean("TInvest_sandbox", true)
                val accounts = repository.getAccounts("TInvest", sandboxMode)
                if (accounts.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = "Нет доступных счетов", isError = true)
                    }
                    return@launch
                }
                val accountId = accounts.first().id
                val broker = brokerManager.getBroker("TInvest") as? TInvestInvestService
                    ?: throw IllegalStateException("Брокер TInvest не найден")

                val regularOrders = broker.getOrders(accountId)
                val stopOrders = broker.getStopOrders(accountId)
                val allOrders = (regularOrders + stopOrders).sortedWith(
                    compareBy<OrderListItem> { it.orderDate ?: Long.MAX_VALUE }
                        .thenBy { it.price }
                )

                _uiState.update {
                    it.copy(
                        orders = allOrders,
                        isLoading = false,
                        statusMessage = if (allOrders.isEmpty()) "Нет активных заявок" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, statusMessage = "Ошибка: ${e.message}", isError = true)
                }
                Log.e(TAG, "Ошибка загрузки заявок", e)
            }
        }
    }

    fun cancelOrder(order: OrderListItem) {
        viewModelScope.launch {
            try {
                val sandboxMode = sharedPrefs.getBoolean("TInvest_sandbox", true)
                val accounts = repository.getAccounts("TInvest", sandboxMode)
                if (accounts.isEmpty()) return@launch
                val accountId = accounts.first().id
                val broker = brokerManager.getBroker("TInvest") as? TInvestInvestService
                    ?: return@launch

                if (order.isStopOrder) {
                    repository.cancelStopOrder(accountId, order.orderId)
                } else {
                    broker.cancelOrder(accountId, order.orderId)
                }
                loadOrders()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusMessage = "Ошибка отмены: ${e.message}", isError = true)
                }
                Log.e(TAG, "Ошибка отмены заявки", e)
            }
        }
    }
}
