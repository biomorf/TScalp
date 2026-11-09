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
import com.gitlab.biomorf.tscalp.domain.models.BrokerInitState
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PortfolioSheet
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.PriceUpdate
import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import com.gitlab.biomorf.tscalp.domain.usecases.CheckBrokerInitializationUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.LoadPortfolioUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PayInSandboxUseCase
import com.gitlab.biomorf.tscalp.presentation.util.StatusEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

// Архитектурный долг: 12 функций в классе. См. ROADMAP → «Рефакторинг ViewModel-слоя».
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
        private val payInSandboxUseCase: PayInSandboxUseCase,
        private val loadPortfolioUseCase: LoadPortfolioUseCase,
        private val checkBrokerInitializationUseCase: CheckBrokerInitializationUseCase,
        private val logger: Logger,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(PortfolioUiState())
        val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()
        private val _status = Channel<StatusEvent>(Channel.BUFFERED)
        val status: Flow<StatusEvent> = _status.receiveAsFlow()

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
                    val currentPositions =
                        _uiState.value.sheet.accounts.values
                            .flatMap { it.positions }
                    if (currentPositions.isNotEmpty()) {
                        updateTradingStatuses(currentPositions)
                    }
                }
            }
        }

        suspend fun checkApiInitialization() {
            val sandbox = sandboxPreferencesRepository.isSandboxMode()
            val initResult = checkBrokerInitializationUseCase.execute(PriceConsumer.PORTFOLIO)
            when (initResult) {
                is AppResult.Success -> {
                    when (initResult.data) {
                        is BrokerInitState.AnyInitialized -> {
                            logger.d(TAG, "checkApiInitialization: anyApiInitialized=true, sandbox=$sandbox")
                            _uiState.update { it.copy(isApiInitialized = true, sandboxMode = sandbox) }
                            viewModelScope.launch { loadPortfolio() }
                        }
                        is BrokerInitState.NoneInitialized -> {
                            logger.d(TAG, "checkApiInitialization: anyApiInitialized=false, sandbox=$sandbox")
                            _uiState.update { it.copy(isApiInitialized = false, sandboxMode = sandbox) }
                            stopPositionUpdates()
                            _uiState.update {
                                it.copy(
                                    sheet = PortfolioSheet(emptyMap()),
                                    prices = emptyMap(),
                                    priceChangePercents = emptyMap(),
                                    tradingStatuses = emptyMap(),
                                )
                            }
                        }
                    }
                }
                is AppResult.Failure -> {
                    logger.e(TAG, "checkApiInitialization failed: ${initResult.error.message}", initResult.error.cause)
                    _uiState.update { it.copy(isApiInitialized = false, sandboxMode = sandbox) }
                }
            }
        }

        fun loadPortfolio() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                val sandbox = sandboxPreferencesRepository.isSandboxMode()

                when (val result = loadPortfolioUseCase.execute(BrokerName.TINVEST, sandbox)) {
                    is AppResult.Success -> {
                        val data = result.data
                        logger.d(TAG, "loadPortfolio: ${data.accounts.size} accounts, sandbox=$sandbox")
                        _uiState.update {
                            it.copy(sheet = data.sheet, isLoading = false)
                        }
                        if (data.accounts.isEmpty()) return@launch
                        val keys = data.accounts.map { AccountKey(BrokerName.TINVEST, it.id) }.toSet()
                        startPositionUpdates(keys, sandbox, seed = data.sheet)
                        syncPriceInterest()
                    }
                    is AppResult.Failure -> {
                        logger.e(TAG, "loadPortfolio failed: ${result.error.message}", result.error.cause)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                            )
                        }
                        _status.trySend(StatusEvent("Ошибка загрузки: ${result.error.message}", isError = true))
                    }
                }
            }
        }

        private fun startPositionUpdates(
            keys: Set<AccountKey>,
            sandbox: Boolean,
            seed: PortfolioSheet? = null,
        ) {
            stopPositionUpdates()
            positionFlowManager.start(
                accountKeys = keys,
                sandboxMode = sandbox,
                seed = seed,
            )
            positionStreamJob =
                viewModelScope.launch {
                    positionFlowManager.sheet.collect { sheet ->
                        _uiState.update { it.copy(sheet = sheet) }
                        syncPriceInterest()
                    }
                }
        }

        private fun stopPositionUpdates() {
            positionStreamJob?.cancel()
            positionStreamJob = null
        }

        private fun syncPriceInterest() {
            val ids =
                _uiState.value.sheet.accounts.values
                    .flatMap { it.positions }
                    .map { it.instrument.tscalpInstrumentId }
                    .filter { it.isNotBlank() && it != "RUB000UTSTOM" }
                    .toSet()
            priceFlowManager.setInterest(PriceConsumer.PORTFOLIO, ids)
        }

        private fun updatePriceFromStream(update: PriceUpdate) {
            if (update.price <= 0.0) return
            val id = update.tscalpInstrumentId
            _uiState.update { state ->
                val prev = state.prices[id]
                val newPrices = state.prices + (id to update.price)
                val newPcts =
                    if (prev != null && prev != 0.0 && prev != update.price) {
                        state.priceChangePercents + (id to ((update.price - prev) / prev) * 100.0)
                    } else {
                        state.priceChangePercents
                    }
                state.copy(prices = newPrices, priceChangePercents = newPcts)
            }
        }

        override fun onCleared() {
            logger.d(TAG, "onCleared")
            priceFlowManager.clearInterest(PriceConsumer.PORTFOLIO)
        }

        private suspend fun updateTradingStatuses(positions: List<Position>) {
            val byBroker = positions.groupBy { it.brokerName }
            val allStatuses = mutableMapOf<String, TradingAvailability>()
            for ((brokerName, posList) in byBroker) {
                val ids = posList.map { it.instrument.tscalpInstrumentId }.filter { it.isNotBlank() }
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

        fun payInSandbox(accountId: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                logger.d(TAG, "Пополнение счёта $accountId через ${BrokerName.TINVEST.displayName}")

                when (
                    val result =
                        payInSandboxUseCase.execute(
                            accountId = accountId,
                            amount = SandboxMoney(currency = "RUB", units = 100_000),
                        )
                ) {
                    is AppResult.Success -> loadPortfolio()
                    is AppResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                            )
                        }
                        _status.trySend(StatusEvent("Ошибка пополнения: ${result.error.message}", isError = true))
                    }
                }
            }
        }

        fun refresh() {
            viewModelScope.launch { loadPortfolio() }
        }
    }
