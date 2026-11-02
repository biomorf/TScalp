package com.gitlab.biomorf.tscalp.presentation.screens.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceConsumer
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.MarketDataRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.PortfolioRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PortfolioPosition
import com.gitlab.biomorf.tscalp.domain.models.PortfolioSheet
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.PriceUpdate
import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import com.gitlab.biomorf.tscalp.domain.models.moneyPerPointOfPrice
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

// Архитектурный долг: 12 функций в классе. См. ROADMAP → «Рефакторинг ViewModel-слоя».
@Suppress("TooManyFunctions")
@HiltViewModel
class PortfolioViewModel
    @Inject
    constructor(
        private val portfolioRepository: PortfolioRepository,
        private val marketDataRepository: MarketDataRepository,
        private val accountsRepository: AccountsRepository,
        private val brokerManager: BrokerRegistry,
        private val sandboxPreferencesRepository: SandboxPreferencesRepository,
        private val priceFlowManager: PriceFlowManager,
        private val positionFlowManager: PositionFlowManager,
        private val logger: Logger,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(PortfolioUiState())
        val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

        private val sheetState = MutableStateFlow(PortfolioSheet(emptyMap()))
        private val pricesMap = mutableMapOf<String, Double>()
        private val priceChangePercentsMap = mutableMapOf<String, Double>()
        private var positionStreamJob: Job? = null

        companion object {
            private const val TAG = "PortfolioViewModel"
        }

        init {
            viewModelScope.launch {
                logger.d(TAG, "subscribed to anyInitialized")
                brokerManager.anyInitialized.collect {
                    checkApiInitialization()
                }
            }
            viewModelScope.launch {
                logger.d(TAG, "subscribed to prices")
                priceFlowManager.prices.collect { update ->
                    updatePriceFromStream(update)
                }
            }
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
            val isApiInit = brokerManager.getAllBrokers().any { it.isInitialized }
            val sandbox = sandboxPreferencesRepository.isSandboxMode()
            logger.d(TAG, "checkApiInitialization: anyApiInitialized=$isApiInit, sandbox=$sandbox")
            _uiState.update { it.copy(isApiInitialized = isApiInit, sandboxMode = sandbox) }

            if (isApiInit) {
                viewModelScope.launch { loadPortfolio() }
            } else {
                stopPositionUpdates()
                priceFlowManager.clearInterest(PriceConsumer.PORTFOLIO)
                positionFlowManager.stop()
                sheetState.value = PortfolioSheet(emptyMap())
                pricesMap.clear()
                priceChangePercentsMap.clear()
                _uiState.update {
                    it.copy(
                        positions = emptyList(),
                        totalValue = 0.0,
                        tradingStatuses = emptyMap(),
                        sheet = PortfolioSheet(emptyMap()),
                    )
                }
            }
        }

        fun loadPortfolio() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, statusMessage = null) }
                val sandbox = sandboxPreferencesRepository.isSandboxMode()

                val accounts =
                    when (val r = accountsRepository.getAccountsResult(BrokerName.TINVEST, sandbox)) {
                        is AppResult.Success -> r.data
                        is AppResult.Failure -> {
                            logger.e(TAG, "loadPortfolio: getAccounts failed: ${r.error.message}", r.error.cause)
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    statusMessage = "Ошибка загрузки: ${r.error.message}",
                                    isError = true,
                                )
                            }
                            return@launch
                        }
                    }
                if (accounts.isEmpty()) {
                    logger.d(TAG, "loadPortfolio: no accounts")
                    _uiState.update { it.copy(isLoading = false, positions = emptyList(), totalValue = 0.0) }
                    return@launch
                }
                logger.d(TAG, "loadPortfolio: ${accounts.size} accounts, sandbox=$sandbox")

                // Первичный fetch по каждому счёту (сразу заполнить UI)
                val allPositions = mutableListOf<PortfolioPosition>()
                for (account in accounts) {
                    when (val r = portfolioRepository.fetchPositionsResult(BrokerName.TINVEST, account.id, sandbox)) {
                        is AppResult.Success -> allPositions.addAll(r.data)
                        is AppResult.Failure ->
                            logger.e(TAG, "loadPortfolio[${account.id}] failed: ${r.error.message}", r.error.cause)
                    }
                }
                _uiState.update {
                    it.copy(
                        positions = allPositions,
                        totalValue = allPositions.sumOf { p -> p.totalValue },
                        isLoading = false,
                    )
                }

                val keys = accounts.map { AccountKey(BrokerName.TINVEST, it.id) }.toSet()
                startPositionUpdates(keys, sandbox)
                syncPriceInterest()
            }
        }

        private fun startPositionUpdates(
            keys: Set<AccountKey>,
            sandbox: Boolean,
        ) {
            stopPositionUpdates()
            positionFlowManager.start(accountKeys = keys, sandboxMode = sandbox)
            positionStreamJob =
                viewModelScope.launch {
                    positionFlowManager.sheet.collect { sheet ->
                        sheetState.value = sheet
                        _uiState.update { it.copy(sheet = sheet) }
                        recomputePositions()
                    }
                }
        }

        private fun stopPositionUpdates() {
            positionStreamJob?.cancel()
            positionStreamJob = null
        }

        private fun syncPriceInterest() {
            val ids =
                _uiState.value.positions
                    .map { it.tscalpInstrumentId }
                    .filter { it.isNotBlank() && it != "RUB000UTSTOM" }
                    .toSet()
            priceFlowManager.setInterest(PriceConsumer.PORTFOLIO, ids)
        }

        private fun updatePriceFromStream(update: PriceUpdate) {
            if (update.price <= 0.0) return
            val id = update.tscalpInstrumentId
            val prev = pricesMap[id]
            pricesMap[id] = update.price
            if (prev != null && prev != 0.0 && prev != update.price) {
                priceChangePercentsMap[id] = ((update.price - prev) / prev) * 100.0
            }
            recomputePositions()
        }

        override fun onCleared() {
            logger.d(TAG, "onCleared")
            priceFlowManager.clearInterest(PriceConsumer.PORTFOLIO)
        }

        /**
         * Пересобирает `uiState.positions` из `sheetState` (структурные данные) +
         * overlay `pricesMap` / `priceChangePercentsMap` (tick-to-tick из PriceFlowManager).
         *
         * `PortfolioPosition` — legacy-модель для `PortfolioScreen` (до миграции
         * на `sheet`). `Position.priceChangePercent` = null (источник — задача
         * «Position: источники priceChangePercent...»), здесь подставляется
         * tick-to-tick из overlay.
         */
        private fun recomputePositions() {
            val sheet = sheetState.value
            val positions = sheet.accounts.values.flatMap { acc -> acc.positions.map { it.toUiPosition() } }
            _uiState.update { it.copy(positions = positions, totalValue = positions.sumOf { p -> p.totalValue }) }
            syncPriceInterest()
        }

        private fun Position.toUiPosition(): PortfolioPosition {
            val id = instrument.tscalpInstrumentId
            val price = pricesMap[id] ?: currentPrice
            val pct = priceChangePercentsMap[id]
            return PortfolioPosition(
                tscalpInstrumentId = id,
                brokerName = brokerName,
                name = instrument.name,
                ticker = instrument.ticker,
                quantity = quantity.toLong(),
                currentPrice = price,
                averagePrice = averagePrice,
                totalValue = price * quantity.toLong() * instrument.moneyPerPointOfPrice,
                profit = profit,
                profitPercent = profitPercent,
                moneyPerPointOfPrice = instrument.moneyPerPointOfPrice,
                instrumentType = instrument.instrumentType,
                isin = instrument.isin,
                classCode = instrument.classCode ?: "",
                currency = instrument.currency,
                priceChangePercent = pct,
            )
        }

        private suspend fun updateTradingStatuses(positions: List<PortfolioPosition>) {
            val byBroker = positions.groupBy { it.brokerName }
            val allStatuses = mutableMapOf<String, TradingAvailability>()
            for ((brokerName, posList) in byBroker) {
                val ids = posList.map { it.tscalpInstrumentId }.filter { it.isNotBlank() }
                if (ids.isEmpty()) continue
                when (val result = marketDataRepository.getTradingStatusesResult(brokerName, ids)) {
                    is AppResult.Success -> allStatuses.putAll(result.data)
                    is AppResult.Failure ->
                        logger.e(
                            TAG,
                            "Ошибка обновления статусов для ${brokerName.displayName}: ${result.error.message}",
                            result.error.cause,
                        )
                }
            }
            if (allStatuses.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(tradingStatuses = state.tradingStatuses + allStatuses)
                }
            }
        }

        // Архитектурный долг: функция требует декомпозиции.
        // См. ROADMAP → «Рефакторинг ViewModel-слоя».
        @Suppress("LongMethod")
        fun payInSandbox() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }

                val sandboxMode = sandboxPreferencesRepository.isSandboxMode()

                val accounts =
                    when (val result = accountsRepository.getAccountsResult(BrokerName.TINVEST, sandboxMode)) {
                        is AppResult.Success -> result.data
                        is AppResult.Failure -> {
                            logger.e(TAG, "payInSandbox: getAccounts failed: ${result.error.message}", result.error.cause)
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    statusMessage = "Ошибка пополнения: ${result.error.message}",
                                    isError = true,
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
                            isError = true,
                        )
                    }
                    return@launch
                }

                val accountId = accounts.first().id
                logger.d(TAG, "Пополнение счёта $accountId через ${BrokerName.TINVEST.displayName}")

                when (
                    val result =
                        accountsRepository.sandboxPayInResult(
                            accountId = accountId,
                            amount = SandboxMoney(currency = "RUB", units = 100_000),
                        )
                ) {
                    is AppResult.Success -> {
                        logger.d(TAG, "Пополнение выполнено успешно")
                        loadPortfolio()
                    }
                    is AppResult.Failure -> {
                        logger.e(TAG, "payInSandbox: sandboxPayIn failed: ${result.error.message}", result.error.cause)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                statusMessage = "Ошибка пополнения: ${result.error.message}",
                                isError = true,
                            )
                        }
                    }
                }
            }
        }

        fun refresh() {
            viewModelScope.launch { loadPortfolio() }
        }

        fun clearStatus() {
            _uiState.update { it.copy(statusMessage = null, isError = false) }
        }
    }
