package com.gitlab.biomorf.tscalp.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.gitlab.biomorf.tscalp.data.repository.InvestRepository
import com.gitlab.biomorf.tscalp.data.repository.SettingsRepository
import com.gitlab.biomorf.tscalp.domain.models.OrderListItem
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.util.AppLogger

@HiltViewModel
class OrdersListViewModel @Inject constructor(
    private val repository: InvestRepository,
    private val settingsRepository: SettingsRepository
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

            val sandboxMode = settingsRepository.isSandboxMode()

            // Шаг 1: получить счета через AppResult
            val accounts = when (val result = repository.getAccountsResult(BrokerName.TINVEST, sandboxMode)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> {
                    AppLogger.e(
                        TAG,
                        "loadOrders: getAccounts failed: ${result.error.message}",
                        result.error.cause
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Ошибка: ${result.error.message}",
                            isError = true
                        )
                    }
                    return@launch
                }
            }

            if (accounts.isEmpty()) {
                _uiState.update {
                    it.copy(isLoading = false, statusMessage = "Нет доступных счетов", isError = true)
                }
                return@launch
            }
            val accountId = accounts.first().id

            // Шаг 2: получить заявки через репозиторий
            when (val result = repository.getAllOrdersResult(BrokerName.TINVEST, accountId)) {
                is AppResult.Success -> {
                    val allOrders = result.data.sortedWith(
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
                }
                is AppResult.Failure -> {
                    AppLogger.e(
                        TAG,
                        "loadOrders: getAllOrders failed: ${result.error.message}",
                        result.error.cause
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Ошибка: ${result.error.message}",
                            isError = true
                        )
                    }
                }
            }
        }
    }

    fun cancelOrder(order: OrderListItem) {
        viewModelScope.launch {
            val sandboxMode = settingsRepository.isSandboxMode()

            // Шаг 1: счета
            val accounts = when (val result = repository.getAccountsResult(BrokerName.TINVEST, sandboxMode)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> {
                    AppLogger.e(
                        TAG,
                        "cancelOrder: getAccounts failed: ${result.error.message}",
                        result.error.cause
                    )
                    _uiState.update {
                        it.copy(statusMessage = "Ошибка отмены: ${result.error.message}", isError = true)
                    }
                    return@launch
                }
            }
            if (accounts.isEmpty()) return@launch
            val accountId = accounts.first().id

            // Шаг 2: отмена. Стоп-заявки и обычные — обе через репозиторий.
            val cancelResult: AppResult<Unit> = if (order.isStopOrder) {
                repository.cancelStopOrderResult(accountId, order.orderId)
            } else {
                repository.cancelOrderResult(BrokerName.TINVEST, accountId, order.orderId)
            }

            when (cancelResult) {
                is AppResult.Success -> loadOrders()
                is AppResult.Failure -> {
                    AppLogger.e(
                        TAG,
                        "cancelOrder: failed for orderId=${order.orderId}: ${cancelResult.error.message}",
                        cancelResult.error.cause
                    )
                    _uiState.update {
                        it.copy(
                            statusMessage = "Ошибка отмены: ${cancelResult.error.message}",
                            isError = true
                        )
                    }
                }
            }
        }
    }
}
