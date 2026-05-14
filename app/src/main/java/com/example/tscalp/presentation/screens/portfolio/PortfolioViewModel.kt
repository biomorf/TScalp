package com.example.tscalp.presentation.screens.portfolio

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.data.repository.InvestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.shareIn

import com.example.tscalp.data.api.TInvestInvestService
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.domain.models.SandboxMoney
import com.example.tscalp.domain.models.TradingAvailability
import com.example.tscalp.domain.models.PositionStreamItem

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
    private var isPortfolioLoading = false

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
        val isApiInit = ServiceLocator.isAnyBrokerInitialized()
        _uiState.update { it.copy(isApiInitialized = isApiInit, sandboxMode = ServiceLocator.isSandboxMode()) }
        if (isApiInit) {
            //if (ServiceLocator.getBrokerManager().getDefaultBroker().isInitialized) {
                viewModelScope.launch { loadPortfolio() }
            //}
            startPriceUpdates()
        }
    }


    fun loadPortfolio() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            val broker = ServiceLocator.getBrokerManager().getBroker("TInvest") as? TInvestInvestService ?: run {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Брокер не доступен", isError = true) }
                return@launch
            }
            val accountId = ServiceLocator.loadDefaultAccountId("TInvest") ?: run {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Нет выбранного счёта", isError = true) }
                return@launch
            }

            // Первичная загрузка через прямой запрос (чтобы сразу показать портфель)
            try {
                val sandbox = ServiceLocator.isSandboxMode()
                val positions = broker.fetchPositionsRest(accountId, sandbox)
                _uiState.update { it.copy(positions = positions, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Ошибка загрузки: ${e.message}", isError = true) }
                return@launch
            }

            // Запускаем общий источник (если ещё не запущен)
            broker.startSharedPositionStream(accountId)

            // Подписываемся на обновления
            broker.positionsSharedFlow.collect { item ->
                updatePortfolioItem(item)
            }
        }
    }

    private fun updatePortfolioItem(item: PositionStreamItem) {
        Log.d(TAG, "updatePortfolioItem: uid=${item.instrumentUid} type=${item.instrumentType} pointValue=${item.pointValue}")
        val current = _uiState.value.positions.toMutableList()
        val index = current.indexOfFirst { it.tscalpInstrumentId == item.instrumentUid }
        if (index == -1) {
            // Новая позиция (например, при первом снапшоте)
            current.add(PortfolioPosition(
                tscalpInstrumentId = item.instrumentUid,
                ticker = item.ticker,
                name = item.ticker, // позже можно подгрузить полное имя
                quantity = item.quantity,
                currentPrice = item.currentPrice ?: 0.0,
                averagePrice = item.averagePositionPrice,
                totalValue = (item.currentPrice ?: 0.0) * item.quantity,
                profit = item.expectedYield,
                profitPercent = item.averagePositionPrice?.let { avg ->
                    if (avg > 0) ((item.currentPrice ?: 0.0) - avg) / avg * 100.0 else null
                },
                pointValue = item.pointValue,
                instrumentType = item.instrumentType // TODO: заполнить из кэша инструментов
            ))
        } else {
            // Обновляем существующую позицию
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
                instrumentType = item.instrumentType
            )
        }
        _uiState.update { it.copy(positions = current) }
    }

    private suspend fun updateTradingStatuses(positions: List<PortfolioPosition>) {
        // Группируем позиции по брокеру
        val byBroker = positions.groupBy { it.brokerName }
        val allStatuses = mutableMapOf<String, TradingAvailability>()
        for ((brokerName, posList) in byBroker) {
            val broker = ServiceLocator.getBrokerManager().getBroker(brokerName) ?: continue
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
                val sandboxMode = ServiceLocator.isSandboxMode()
                val brokerName = "TInvest"
                val accounts = repository.getAccounts(brokerName, sandboxMode)
                if (accounts.isEmpty()) throw Exception("Нет доступных счетов")

                val defaultAccountId = ServiceLocator.loadDefaultAccountId("TInvest")
                val accountId = if (defaultAccountId != null && accounts.any { it.id == defaultAccountId }) {
                    defaultAccountId
                } else {
                    accounts.first().id
                }
                Log.d(TAG, "Пополнение счёта $accountId через TInvest")

                repository.sandboxPayIn(
                    accountId = accountId,
                    amount = SandboxMoney(currency = "RUB", units = 100_000) // пополняем на 100 000 рублей
                )
                Log.d(TAG, "Пополнение выполнено успешно")
                // Обновляем портфель и баланс
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
        if (ids.isEmpty()) return   // нечего обновлять, выходим без запроса
        try {
            val prices = repository.getLastPricesByTscalpInstrumentId(ids)
            val updatedPositions = positions.map { pos ->
                // RUB000UTSTOM не обновится через API, оставляем старую цену (1.0)
                if (pos.ticker == "RUB000UTSTOM") {
                    pos.copy(currentPrice = 1.0, totalValue = 1.0 * pos.quantity, priceChangePercent = null)
                } else {
                    val ticker = pos.ticker
                    val freshPrice = prices[pos.tscalpInstrumentId]
                    /// Если свежая цена пришла и она > 0 – используем её, иначе оставляем старую
                    val newPrice = if (freshPrice != null && freshPrice > 0.0) {
                        freshPrice
                    } else {
                        Log.w(TAG, "Нет цены для тикера $ticker")
                        pos.currentPrice   /// сохраняем последнее известное значение
                    }
                    // Процент изменения считаем только когда есть и старая, и новая цена
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