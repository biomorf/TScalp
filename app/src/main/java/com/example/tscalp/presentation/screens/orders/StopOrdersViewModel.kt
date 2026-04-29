package com.example.tscalp.presentation.screens.orders

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tscalp.data.api.TInvestInvestService
import com.example.tscalp.data.repository.InvestRepository
import com.example.tscalp.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.tscalp.domain.models.StopOrderUi
import com.example.tscalp.domain.models.StopOrdersUiState

class StopOrdersViewModel : ViewModel() {

    private val repository = InvestRepository(ServiceLocator.getBrokerManager())

    private val _uiState = MutableStateFlow(StopOrdersUiState())
    val uiState: StateFlow<StopOrdersUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "StopOrdersViewModel"
    }

    fun loadStopOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                val sandboxMode = ServiceLocator.isSandboxMode()
                val accounts = repository.getAccounts("TInvest", sandboxMode)
                if (accounts.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = "Нет доступных счетов", isError = true)
                    }
                    return@launch
                }
                // Единственный accountId, который используется дальше
                val accountId: String = accounts.first().id

                val broker = ServiceLocator.getBrokerManager().getBroker("TInvest") as? TInvestInvestService
                    ?: throw IllegalStateException("Брокер TInvest не найден")
                // Вызов getStopOrders с явно типизированным параметром
                val protoOrders: List<com.example.tscalp.domain.models.StopOrderUi> = broker.getStopOrders(accountId)

                val uiOrders = protoOrders.map { order ->
                    order.copy()   // просто копируем, уже содержит все поля
                }

                _uiState.update {
                    it.copy(
                        orders = uiOrders,
                        isLoading = false,
                        statusMessage = if (uiOrders.isEmpty()) "Нет активных стоп-заявок" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, statusMessage = "Ошибка: ${e.message}", isError = true)
                }
                Log.e(TAG, "Ошибка загрузки стоп-заявок", e)
            }
        }
    }

    fun cancelStopOrder(stopOrderId: String) {
        viewModelScope.launch {
            try {
                val sandboxMode = ServiceLocator.isSandboxMode()
                val accounts = repository.getAccounts("TInvest", sandboxMode)
                if (accounts.isEmpty()) return@launch
                val accountId: String = accounts.first().id

                val broker = ServiceLocator.getBrokerManager().getBroker("TInvest") as? TInvestInvestService
                    ?: return@launch
                broker.cancelStopOrder(accountId, stopOrderId)

                loadStopOrders()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusMessage = "Ошибка отмены: ${e.message}", isError = true)
                }
                Log.e(TAG, "Ошибка отмены стоп-заявки", e)
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StopOrdersViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return StopOrdersViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}