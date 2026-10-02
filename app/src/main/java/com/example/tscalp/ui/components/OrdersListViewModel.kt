package com.example.tscalp.ui.components

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

import com.example.tscalp.data.api.TInvestBrokerAPI
import com.example.tscalp.data.repository.InvestRepository
import com.example.tscalp.di.BrokerManager
import com.example.tscalp.domain.models.OrderListItem
import com.example.tscalp.domain.models.AppResult
import com.example.tscalp.domain.models.BrokerName
import com.example.tscalp.util.toAppError
import com.example.tscalp.util.AppLogger

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

            val sandboxMode = sharedPrefs.getBoolean("TInvest_sandbox", true)

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

            // Шаг 2: получить заявки (напрямую у брокера — см. ISSUES.md,
            //         методы getOrders / getStopOrders пока не в репозитории)
            try {
                val broker = brokerManager.getBroker(BrokerName.TINVEST) as? TInvestBrokerAPI
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val appError = e.toAppError()
                AppLogger.e(TAG, "loadOrders: broker call failed: ${appError.message}", e)
                _uiState.update {
                    it.copy(isLoading = false, statusMessage = "Ошибка: ${appError.message}", isError = true)
                }
            }
        }
    }

    fun cancelOrder(order: OrderListItem) {
        viewModelScope.launch {
            val sandboxMode = sharedPrefs.getBoolean("TInvest_sandbox", true)

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

            val broker = brokerManager.getBroker(BrokerName.TINVEST) as? TInvestBrokerAPI
            if (broker == null) {
                _uiState.update {
                    it.copy(statusMessage = "Ошибка отмены: Брокер TInvest не найден", isError = true)
                }
                return@launch
            }

            // Шаг 2: отмена. Стоп-заявки через репозиторий (AppResult),
            //         обычные — напрямую у брокера (см. ISSUES.md).
            if (order.isStopOrder) {
                when (val result = repository.cancelStopOrderResult(accountId, order.orderId)) {
                    is AppResult.Success -> loadOrders()
                    is AppResult.Failure -> {
                        AppLogger.e(
                            TAG,
                            "cancelOrder: cancelStopOrder failed: ${result.error.message}",
                            result.error.cause
                        )
                        _uiState.update {
                            it.copy(statusMessage = "Ошибка отмены: ${result.error.message}", isError = true)
                        }
                    }
                }
            } else {
                try {
                    broker.cancelOrder(accountId, order.orderId)
                    loadOrders()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val appError = e.toAppError()
                    AppLogger.e(TAG, "cancelOrder: broker.cancelOrder failed: ${appError.message}", e)
                    _uiState.update {
                        it.copy(statusMessage = "Ошибка отмены: ${appError.message}", isError = true)
                    }
                }
            }
        }
    }
}
