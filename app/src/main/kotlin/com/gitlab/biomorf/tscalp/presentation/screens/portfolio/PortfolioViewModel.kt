package com.gitlab.biomorf.tscalp.presentation.screens.portfolio

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

import com.gitlab.biomorf.tscalp.data.repository.SettingsRepository
import com.gitlab.biomorf.tscalp.data.api.SharedPositionStreamManager
import com.gitlab.biomorf.tscalp.data.repository.InvestRepository
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PortfolioPosition
import com.gitlab.biomorf.tscalp.domain.models.toPortfolioPosition
import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.util.AppLogger

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val repository: InvestRepository,
    private val brokerManager: BrokerManager,
    private val settingsRepository: SettingsRepository,
    private val positionStreamManager: SharedPositionStreamManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()
    private var priceUpdateJob: Job? = null

    companion object {
        private const val TAG = "PortfolioViewModel"
    }

    init {
        // Реагируем на изменение состояния инициализации брокеров.
        // StateFlow отдаёт текущее значение сразу при подписке.
        viewModelScope.launch {
            brokerManager.anyInitialized.collect {
                checkApiInitialization()
            }
        }
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

    suspend fun checkApiInitialization() {
        // проверка «есть ли хоть один инициализированный брокер»
        val isApiInit = brokerManager.getAllBrokers().any { it.isInitialized }
        val sandbox = settingsRepository.isSandboxMode()
        _uiState.update { it.copy(isApiInitialized = isApiInit, sandboxMode = sandbox) }

        if (isApiInit) {
            viewModelScope.launch { loadPortfolio() }
            startPriceUpdates()
        } else {
            // Logout: останавливаем фоновые задачи и очищаем состояние,
            // чтобы UI не показывал устаревшие позиции.
            priceUpdateJob?.cancel()
            priceUpdateJob = null
            positionStreamManager.stop()
            _uiState.update {
                it.copy(
                    positions = emptyList(),
                    totalValue = 0.0,
                    tradingStatuses = emptyMap()
                )
            }
        }
    }

    fun loadPortfolio() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }

            val accountId = settingsRepository.loadDefaultAccountId(BrokerName.TINVEST) ?: run {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Нет выбранного счёта", isError = true) }
                return@launch
            }
            val sandbox = settingsRepository.isSandboxMode()

            // Первичный запрос для немедленного отображения
            when (val result = repository.fetchPositionsResult(BrokerName.TINVEST, accountId, sandbox)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(positions = result.data, isLoading = false) }
                }
                is AppResult.Failure -> {
                    AppLogger.e(
                        TAG,
                        "loadPortfolio: fetchPositions failed: ${result.error.message}",
                        result.error.cause
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Ошибка загрузки: ${result.error.message}",
                            isError = true
                        )
                    }
                    return@launch
                }
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
        AppLogger.d(TAG, "updatePortfolioItem: uid=${item.instrumentUid} type=${item.instrumentType} pointValue=${item.pointValue}")
        val current = _uiState.value.positions.toMutableList()
        val index = current.indexOfFirst { it.tscalpInstrumentId == item.instrumentUid }
        if (index == -1) {
            val profitPercent = item.averagePositionPrice?.let { avg ->
                if (avg > 0) ((item.currentPrice ?: 0.0) - avg) / avg * 100.0 else null
            }
            current.add(item.toPortfolioPosition(profitPercent = profitPercent))
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
            val ids = posList.map { it.tscalpInstrumentId }.filter { it.isNotBlank() }
            if (ids.isEmpty()) continue
            when (val result = repository.getTradingStatusesResult(brokerName, ids)) {
                is AppResult.Success -> allStatuses.putAll(result.data)
                is AppResult.Failure -> {
                    AppLogger.e(
                        TAG,
                        "Ошибка обновления статусов для ${brokerName.displayName}: ${result.error.message}",
                        result.error.cause
                    )
                }
            }
        }
        if (allStatuses.isNotEmpty()) {
            // Merge: не теряем статусы, полученные из поиска
            _uiState.update { state ->
                state.copy(tradingStatuses = state.tradingStatuses + allStatuses)
            }
        }
    }

    fun payInSandbox() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val sandboxMode = settingsRepository.isSandboxMode()

            // Шаг 1: получить счета
            val accounts = when (val result = repository.getAccountsResult(BrokerName.TINVEST, sandboxMode)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> {
                    AppLogger.e(TAG, "payInSandbox: getAccounts failed: ${result.error.message}", result.error.cause)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Ошибка пополнения: ${result.error.message}",
                            isError = true
                        )
                    }
                    return@launch
                }
            }

            if (accounts.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Ошибка пополнения: Нет доступных счетов",
                        isError = true
                    )
                }
                return@launch
            }

            val defaultAccountId = settingsRepository.loadDefaultAccountId(BrokerName.TINVEST)
            val accountId = if (defaultAccountId != null && accounts.any { it.id == defaultAccountId }) {
                defaultAccountId
            } else {
                accounts.first().id
            }
            AppLogger.d(TAG, "Пополнение счёта $accountId через ${BrokerName.TINVEST.displayName}")

            // Шаг 2: пополнить
            when (val result = repository.sandboxPayInResult(
                accountId = accountId,
                amount = SandboxMoney(currency = "RUB", units = 100_000)
            )) {
                is AppResult.Success -> {
                    AppLogger.d(TAG, "Пополнение выполнено успешно")
                    loadPortfolio()
                }
                is AppResult.Failure -> {
                    AppLogger.e(TAG, "payInSandbox: sandboxPayIn failed: ${result.error.message}", result.error.cause)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Ошибка пополнения: ${result.error.message}",
                            isError = true
                        )
                    }
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

        val prices = when (val result = repository.getLastPricesResult(ids)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> {
                AppLogger.w(TAG, "updatePrices failed: ${result.error.message}", result.error.cause)
                return
            }
        }

        val updatedPositions = positions.map { pos ->
            if (pos.ticker == "RUB000UTSTOM") {
                pos.copy(currentPrice = 1.0, totalValue = 1.0 * pos.quantity, priceChangePercent = null)
            } else {
                val freshPrice = prices[pos.tscalpInstrumentId]
                val newPrice = if (freshPrice != null && freshPrice > 0.0) {
                    freshPrice
                } else {
                    AppLogger.w(TAG, "Нет цены для тикера ${pos.ticker}")
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
    }

    fun refresh() { viewModelScope.launch { loadPortfolio() } }
    fun clearStatus() { _uiState.update { it.copy(statusMessage = null, isError = false) } }
}
