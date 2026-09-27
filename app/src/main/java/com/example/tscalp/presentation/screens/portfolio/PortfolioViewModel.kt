package com.example.tscalp.presentation.screens.portfolio

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.example.tscalp.data.api.TInvestBrokerAPI
import com.example.tscalp.data.api.SharedPositionStreamManager
import com.example.tscalp.data.repository.InvestRepository
import com.example.tscalp.di.BrokerManager
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.domain.models.SandboxMoney
import com.example.tscalp.domain.models.TradingAvailability
import com.example.tscalp.domain.models.PositionStreamItem

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: InvestRepository,
    private val brokerManager: BrokerManager,
    private val sharedPrefs: SharedPreferences,
    private val positionStreamManager: SharedPositionStreamManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()
    private var priceUpdateJob: Job? = null

    companion object {
        private const val TAG = "PortfolioViewModel"
    }

    init {
        checkApiInitialization()
        // Обновление статусов каждые 5 минут
        viewModelScope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L)
                val currentPositions = _uiState.value.positions
                if (currentPositions.isNotEmpty()) {
                    updateTradingStatuses(currentPositions)
                }
            }
        }
    }

    fun checkApiInitialization() {
        val isApiInit = brokerManager.getAllBrokers().any { it.isInitialized }
        val sandbox = sharedPrefs.getBoolean("TInvest_sandbox", true)
        _uiState.update { it.copy(isApiInitialized = isApiInit, sandboxMode = sandbox) }
        if (isApiInit) {
            viewModelScope.launch { loadPortfolio() }
            startPriceUpdates()
        }
    }

    fun loadPortfolio() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            val broker = brokerManager.getBroker("TInvest") as? TInvestBrokerAPI
            val accountId = sharedPrefs.getString("TInvest_default_account", null) ?: run {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Нет выбранного счёта", isError = true) }
                return@launch
            }

            // Первичный запрос для немедленного отображения
            try {
                val sandbox = sharedPrefs.getBoolean("TInvest_sandbox", true)
                val positions = broker?.fetchPositionsRest(accountId, sandbox) ?: emptyList()
                _uiState.update { it.copy(positions = positions, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Ошибка загрузки", isError = true) }
                return@launch
            }

            // Гарантируем, что общий поток запущен
            positionStreamManager.start(accountId)

            // Подписываемся на обновления
            positionStreamManager.flow.collect { item ->
                updatePortfolioItem(item)
            }
        }
    }

    private fun updatePortfolioItem(item: PositionStreamItem) {
        Log.d(TAG, "updatePortfolioItem: uid=${item.instrumentUid} type=${item.instrumentType} pointValue=${item.pointValue}")
        val current = _uiState.value.positions.toMutableList()
        val index = current.indexOfFirst { it.tscalpInstrumentId == item.instrumentUid }
        if (index == -1) {
            current.add(PortfolioPosition(
                tscalpInstrumentId = item.instrumentUid,
                ticker = item.ticker,
                isin = item.isin,
                classCode = item.classCode,
                name = item.ticker,
                quantity = item.quantity,
                currentPrice = item.currentPrice ?: 0.0,
                averagePrice = item.averagePositionPrice,
                totalValue = (item.currentPrice ?: 0.0) * item.quantity,
                profit = item.expectedYield,
                profitPercent = item.averagePositionPrice?.let { avg ->
                    if (avg > 0) ((item.currentPrice ?: 0.0) - avg) / avg * 100.0 else null
                },
                pointValue = item.pointValue,
                instrumentType = item.instrumentType
            ))
        } else {
            val old = current[index]
            val newPrice = item.currentPrice ?: old.currentPrice
            current[index] = old.copy(
                quantity = item.quantity,
                currentPrice = newPrice,
                averagePrice = item.averagePositionPrice ?: old.averagePrice,
                totalValue = newPrice * item.quantity,
                profit = item.expectedYield,
                profitPercent = item.averagePositionPrice?.let { avg ->
                    if (avg > 0) (newPrice - avg) / avg * 100.0 else null
                },
                pointValue = item.pointValue ?: old.pointValue,
                instrumentType = item.instrumentType,
                isin = item.isin.ifBlank { old.isin },
                classCode = item.classCode.ifBlank { old.classCode }
            )
        }
        _uiState.update { it.copy(positions = current) }
    }

    private suspend fun updateTradingStatuses(positions: List<PortfolioPosition>) {
        val byBroker = positions.groupBy { it.brokerName }
        val allStatuses = mutableMapOf<String, TradingAvailability>()
        for ((brokerName, posList) in byBroker) {
            val broker = brokerManager.getBroker(brokerName) ?: continue
            val ids = posList.map { it.tscalpInstrumentId }.filter { it.isNotBlank() }
            if (ids.isEmpty()) continue
            try {
                val statuses = broker.getTradingStatuses(ids)
                allStatuses.putAll(statuses)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обновления статусов для $brokerName", e)
            }
        }
        if (allStatuses.isNotEmpty()) {
            _uiState.update { it.copy(tradingStatuses = allStatuses) }
        }
    }

    fun payInSandbox() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val sandboxMode = sharedPrefs.getBoolean("TInvest_sandbox", true)
                val brokerName = "TInvest"
                val accounts = repository.getAccounts(brokerName, sandboxMode)
                if (accounts.isEmpty()) throw Exception("Нет доступных счетов")

                val defaultAccountId = sharedPrefs.getString("TInvest_default_account", null)
                val accountId = if (defaultAccountId != null && accounts.any { it.id == defaultAccountId }) {
                    defaultAccountId
                } else {
                    accounts.first().id
                }
                Log.d(TAG, "Пополнение счёта $accountId через TInvest")

                repository.sandboxPayIn(
                    accountId = accountId,
                    amount = SandboxMoney(currency = "RUB", units = 100_000)
                )
                Log.d(TAG, "Пополнение выполнено успешно")
                loadPortfolio()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка пополнения", e)
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
        val ids = positions
            .filter { it.ticker != "RUB000UTSTOM" }
            .map { it.tscalpInstrumentId }
        if (ids.isEmpty()) return
        try {
            val prices = repository.getLastPricesByTscalpInstrumentId(ids)
            val updatedPositions = positions.map { pos ->
                if (pos.ticker == "RUB000UTSTOM") {
                    pos.copy(currentPrice = 1.0, totalValue = 1.0 * pos.quantity, priceChangePercent = null)
                } else {
                    val freshPrice = prices[pos.tscalpInstrumentId]
                    val newPrice = if (freshPrice != null && freshPrice > 0.0) {
                        freshPrice
                    } else {
                        Log.w(TAG, "Нет цены для тикера ${pos.ticker}")
                        pos.currentPrice
                    }
                    val changePercent =
                        if (pos.currentPrice != 0.0 && newPrice != pos.currentPrice) {
                            ((newPrice - pos.currentPrice) / pos.currentPrice) * 100.0
                        } else null
                    pos.copy(
                        currentPrice = newPrice,
                        totalValue = newPrice * pos.quantity,
                        priceChangePercent = changePercent
                    )
                }
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
