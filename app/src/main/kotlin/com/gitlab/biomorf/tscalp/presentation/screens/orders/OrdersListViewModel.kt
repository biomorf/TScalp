package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitlab.biomorf.tscalp.domain.api.managers.OrderFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.OrdersRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrdersListViewModel
    @Inject
    constructor(
        private val accountsRepository: AccountsRepository,
        private val ordersRepository: OrdersRepository,
        private val sandboxPreferencesRepository: SandboxPreferencesRepository,
        private val orderFlowManager: OrderFlowManager,
        private val logger: Logger,
    ) : ViewModel() {
        data class OrdersListState(
            val orders: List<Order> = emptyList(),
            val isLoading: Boolean = false,
            val statusMessage: String? = null,
            val isError: Boolean = false,
        )

        private val _uiState = MutableStateFlow(OrdersListState())
        val uiState: StateFlow<OrdersListState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                logger.d(TAG, "subscribed to orders+hasLoaded")
                combine(
                    orderFlowManager.orders,
                    orderFlowManager.hasLoaded,
                ) { orders, loaded -> orders to loaded }.collect { (orders, loaded) ->
                    _uiState.update { current ->
                        current.copy(
                            orders = orders,
                            isLoading = !loaded,
                            statusMessage =
                                when {
                                    !loaded -> null
                                    orders.isNotEmpty() -> null
                                    current.isError -> current.statusMessage
                                    else -> "Нет активных заявок"
                                },
                        )
                    }
                }
            }
        }

        companion object {
            private const val TAG = "OrdersListViewModel"
        }

        fun loadOrders() {
            viewModelScope.launch {
                logger.d(TAG, "loadOrders: start")
                _uiState.update { it.copy(isLoading = true, statusMessage = null, isError = false) }

                val sandboxMode = sandboxPreferencesRepository.isSandboxMode()

                // Шаг 1: получить счета через AppResult
                val accounts =
                    when (val result = accountsRepository.getAccountsResult(BrokerName.TINVEST, sandboxMode)) {
                        is AppResult.Success -> result.data
                        is AppResult.Failure -> {
                            logger.e(
                                TAG,
                                "loadOrders: getAccounts failed: ${result.error.message}",
                                result.error.cause,
                            )
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    statusMessage = "Ошибка: ${result.error.message}",
                                    isError = true,
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

                // Шаг 2: запустить агрегацию (snapshot polling + stream).
                // Дальше UI обновляется через orderFlowManager.orders
                // (см. init). start() идемпотентен для того же accountId.
                orderFlowManager.start(accountId)
                logger.i(TAG, "loadOrders: account=$accountId, aggregation started")
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        fun cancelOrder(order: Order) {
            viewModelScope.launch {
                logger.d(TAG, "cancelOrder: orderId=${order.orderId}, isStop=${order is Order.Stop}")
                val sandboxMode = sandboxPreferencesRepository.isSandboxMode()

                // Шаг 1: счета
                val accounts =
                    when (val result = accountsRepository.getAccountsResult(BrokerName.TINVEST, sandboxMode)) {
                        is AppResult.Success -> result.data
                        is AppResult.Failure -> {
                            logger.e(
                                TAG,
                                "cancelOrder: getAccounts failed: ${result.error.message}",
                                result.error.cause,
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
                val cancelResult: AppResult<Unit> =
                    if (order is Order.Stop) {
                        ordersRepository.cancelStopOrderResult(accountId, order.orderId)
                    } else {
                        ordersRepository.cancelOrderResult(BrokerName.TINVEST, accountId, order.orderId)
                    }

                when (cancelResult) {
                    is AppResult.Success -> loadOrders()
                    is AppResult.Failure -> {
                        logger.e(
                            TAG,
                            "cancelOrder: failed for orderId=${order.orderId}: ${cancelResult.error.message}",
                            cancelResult.error.cause,
                        )
                        _uiState.update {
                            it.copy(
                                statusMessage = "Ошибка отмены: ${cancelResult.error.message}",
                                isError = true,
                            )
                        }
                    }
                }
            }
        }

        override fun onCleared() {
            logger.d(TAG, "onCleared")
            orderFlowManager.stop()
        }
    }
