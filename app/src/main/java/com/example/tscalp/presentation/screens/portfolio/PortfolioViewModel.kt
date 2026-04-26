package com.example.tscalp.presentation.screens.portfolio

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tscalp.data.api.TinkoffInvestService
import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.data.repository.InvestRepository
import com.example.tscalp.presentation.screens.portfolio.PortfolioUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.example.tscalp.domain.models.PortfolioPosition

/**
 * ViewModel для экрана портфеля.
 * Получает InvestRepository через конструктор.
 */
class PortfolioViewModel(
    private val repository: InvestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()
    private var priceUpdateJob: Job? = null

    companion object {
        private const val TAG = "PortfolioViewModel"
    }

    init {
        checkApiInitialization()
    }

    fun checkApiInitialization() {
        val isApiInit = ServiceLocator.isAnyBrokerInitialized()
        _uiState.update { it.copy(isApiInitialized = isApiInit, sandboxMode = ServiceLocator.isSandboxMode()) }
        if (isApiInit) {
            if (ServiceLocator.getBrokerManager().getDefaultBroker().isInitialized) {
                viewModelScope.launch { loadPortfolio() }
            }
            startPriceUpdates()
        }
    }

    fun loadPortfolio() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                val sandboxMode = ServiceLocator.isSandboxMode()
                val allPositions = mutableListOf<PortfolioPosition>()

                // Получаем имена всех зарегистрированных брокеров
                val brokerNames = ServiceLocator.getBrokerManager().getAvailableBrokers()
                for (brokerName in brokerNames) {
                    // Получаем брокера по имени
                    val broker = ServiceLocator.getBrokerManager().getBroker(brokerName) ?: continue
                    if (!broker.isInitialized) continue

                    try {
                        // Получаем счета брокера
                        val accounts = broker.getAccounts(sandboxMode)
                        for (account in accounts) {
                            try {
                                val portfolioResponse = broker.getPortfolio(account.id, sandboxMode)
                                // Преобразуем позиции из PortfolioResponse в PortfolioPosition
                                val positions = portfolioResponse.positionsList.mapNotNull { pos ->
                                    val instrument = try {
                                        broker.getInstrumentByFigi(pos.figi).instrument
                                    } catch (e: Exception) {
                                        null
                                    }
                                    if (instrument != null) {
                                        val quantity = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 }?.toLong() ?: 0L
                                        val currentPrice = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
                                        val totalValue = currentPrice * quantity
                                        PortfolioPosition(
                                            figi = pos.figi,
                                            name = instrument.name,
                                            ticker = instrument.ticker,
                                            quantity = quantity,
                                            currentPrice = currentPrice,
                                            totalValue = totalValue,
                                            instrumentType = instrument.instrumentType ?: "",
                                            brokerName = brokerName   // <-- используем имя из цикла
                                        )
                                    } else null
                                }
                                allPositions.addAll(positions)
                            } catch (e: Exception) {
                                Log.w(TAG, "Ошибка загрузки портфеля для счета ${account.id} брокера $brokerName", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Не удалось получить счета брокера $brokerName", e)
                    }
                }

                // Сортируем по имени брокера, чтобы группы были вместе
                allPositions.sortBy { it.brokerName }

                val totalValue = allPositions.sumOf { it.totalValue }
                _uiState.update {
                    it.copy(
                        positions = allPositions,
                        totalValue = totalValue,
                        isLoading = false,
                        statusMessage = if (allPositions.isEmpty()) "Портфель пуст" else "Загружено ${allPositions.size} позиций",
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

    fun payInSandbox() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val sandboxMode = ServiceLocator.isSandboxMode()
                val accounts = repository.getAccounts(sandboxMode)
                if (accounts.isEmpty()) throw Exception("Нет доступных счетов")
                val accountId = accounts.first().id
                repository.sandboxPayIn(accountId, 100_000L) // пополняем на 100 000 рублей
                loadPortfolio()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Ошибка пополнения: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    private fun startPriceUpdates() {
        priceUpdateJob?.cancel()
        priceUpdateJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                updatePrices()
            }
        }
    }

    private suspend fun updatePrices() {
        val positions = _uiState.value.positions
        if (positions.isEmpty()) return
        val figis = positions.map { it.figi }
        try {
            val prices = repository.getLastPrices(figis)
            val updatedPositions = positions.map { pos ->
                val newPrice = prices[pos.figi] ?: pos.currentPrice
                val previousPrice = pos.currentPrice  // сохраняем старую цену как предыдущую
                val changePercent = if (previousPrice != 0.0 && newPrice != null) {
                    ((newPrice - previousPrice) / previousPrice) * 100.0
                } else null

                pos.copy(
                    currentPrice = newPrice,
                    totalValue = newPrice * pos.quantity,
                    priceChangePercent = changePercent
                )
            }
            val newTotalValue = updatedPositions.sumOf { it.totalValue }
            _uiState.update {
                it.copy(
                    positions = updatedPositions,
                    totalValue = newTotalValue
                )
            }
        } catch (_: Exception) { }
    }

    fun refresh() { viewModelScope.launch { loadPortfolio() } }
    fun clearStatus() { _uiState.update { it.copy(statusMessage = null, isError = false) } }
}

/**
 * ///Фабрика для создания PortfolioViewModel с внедрением InvestRepository.
 */
class PortfolioViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PortfolioViewModel::class.java)) {
            val brokerManager = ServiceLocator.getBrokerManager()
            val repository = InvestRepository(brokerManager)
            @Suppress("UNCHECKED_CAST")
            return PortfolioViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}