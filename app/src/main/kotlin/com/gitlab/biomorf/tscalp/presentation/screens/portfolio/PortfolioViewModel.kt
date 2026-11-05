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
import com.gitlab.biomorf.tscalp.domain.models.AccountBalance
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PortfolioSheet
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.PriceUpdate
import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
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
                    _uiState.update { it.copy(isLoading = false, sheet = PortfolioSheet(emptyMap())) }
                    return@launch
                }
                logger.d(TAG, "loadPortfolio: ${accounts.size} accounts, sandbox=$sandbox")

                val sheet = seedSheet(accounts, sandbox)
                _uiState.update {
                    it.copy(sheet = sheet, isLoading = false)
                }

                val keys = accounts.map { AccountKey(BrokerName.TINVEST, it.id) }.toSet()
                startPositionUpdates(keys, sandbox, seed = sheet)
                syncPriceInterest()
            }
        }

        /**
         * Seed `PortfolioSheet` из первичного fetch по каждому счёту
         * (до первого polling emit из `PositionFlowManager`).
         *
         * Баланс: `GetPortfolio` не отдаёт `money/blocked`, отдельный
         * [PortfolioRepository.getBalanceResult]. Пока только RUB
         * (`blocked = 0.0`); полный `AccountBalance` (валюты, blocked)
         * — задача `Position: источники priceChangePercent и lockedQuantity`.
         *
         * При ошибке баланса — счет попадает в sheet с пустыми `balances`
         * (не блокирует показ позиций; `AccountHeaderCard` покажет «нет данных»).
         */
        private suspend fun seedSheet(
            accounts: List<BrokerAccount>,
            sandbox: Boolean,
        ): PortfolioSheet {
            val seeded = mutableMapOf<AccountKey, AccountPortfolio>()
            for (account in accounts) {
                when (val r = portfolioRepository.fetchPositionsResult(BrokerName.TINVEST, account.id, sandbox)) {
                    is AppResult.Success -> {
                        val key = AccountKey(BrokerName.TINVEST, account.id)
                        val balances =
                            when (val b = portfolioRepository.getBalanceResult(account.id)) {
                                is AppResult.Success ->
                                    listOf(
                                        AccountBalance(
                                            currency = "rub",
                                            money = b.data,
                                            blocked = 0.0,
                                        ),
                                    )
                                is AppResult.Failure -> {
                                    logger.w(
                                        TAG,
                                        "seedSheet[${account.id}] balance failed: ${b.error.message}",
                                    )
                                    emptyList()
                                }
                            }
                        seeded[key] =
                            AccountPortfolio(
                                accountId = account.id,
                                brokerName = BrokerName.TINVEST,
                                positions = r.data,
                                balances = balances,
                            )
                    }
                    is AppResult.Failure ->
                        logger.e(TAG, "seedSheet[${account.id}] failed: ${r.error.message}", r.error.cause)
                }
            }
            return PortfolioSheet(seeded)
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

        // Архитектурный долг: функция требует декомпозиции.
        // См. ROADMAP → «Рефакторинг ViewModel-слоя».
        @Suppress("LongMethod")
        fun payInSandbox(accountId: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
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
