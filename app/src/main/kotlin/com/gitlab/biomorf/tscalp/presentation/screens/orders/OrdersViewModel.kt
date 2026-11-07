package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceConsumer
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountSelectionRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.InstrumentRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.PortfolioRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStatusRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerInitState
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PortfolioSheet
import com.gitlab.biomorf.tscalp.domain.models.SelectedInstrumentInfo
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import com.gitlab.biomorf.tscalp.domain.models.map
import com.gitlab.biomorf.tscalp.domain.usecases.CheckBrokerInitializationUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.LoadOrdersAccountsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.RefreshSelectedInstrumentsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.UpdateTradingStatusesUseCase
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

// Архитектурный долг: 49 функций, класс слишком большой.
// См. ROADMAP → «Рефакторинг ViewModel-слоя».
@Suppress("TooManyFunctions", "LargeClass")
@HiltViewModel
class OrdersViewModel
    @Inject
    constructor(
        private val portfolioRepository: PortfolioRepository,
        private val instrumentRepo: InstrumentRepository,
        private val sandboxPreferencesRepository: SandboxPreferencesRepository,
        private val accountSelectionRepository: AccountSelectionRepository,
        private val cardsRepository: CardsRepository,
        private val brokerManager: BrokerRegistry,
        private val priceFlowManager: PriceFlowManager,
        private val loadOrdersAccountsUseCase: LoadOrdersAccountsUseCase,
        private val refreshSelectedInstrumentsUseCase: RefreshSelectedInstrumentsUseCase,
        private val positionFlowManager: PositionFlowManager,
        private val checkBrokerInitializationUseCase: CheckBrokerInitializationUseCase,
        private val updateTradingStatusesUseCase: UpdateTradingStatusesUseCase,
        private val tradingStatusRepository: TradingStatusRepository,
        private val logger: Logger,
    ) : ViewModel() {
        private val _coordState = MutableStateFlow(OrdersCoordState())
        val coordState: StateFlow<OrdersCoordState> = _coordState.asStateFlow()

        /**
         * Passthrough из [TradingStatusRepository] (SSoT, P1).
         * Coord не дублирует статусы в `OrdersCoordState`.
         */
        val tradingStatuses: StateFlow<Map<String, TradingAvailability>> = tradingStatusRepository.statuses
        private var positionStreamJob: Job? = null

        companion object {
            private const val TAG = "OrdersViewModel"
        }

        init {
            // Реагируем на изменение состояния инициализации брокеров.
            // StateFlow отдаёт текущее значение сразу при подписке — аналог
            // прежнего прямого вызова checkApiInitialization() в init.
            viewModelScope.launch {
                logger.d(TAG, "subscribed to anyInitialized")
                brokerManager.anyInitialized.collect {
                    checkApiInitialization()
                }
            }
            // Фоновое обновление статусов каждые 5 минут.
            // 13d-F-A: restoreState() переехал в OrderFormViewModel
            // (RestoreOrderFormUseCase); tradingStatuses — passthrough
            // из TradingStatusRepository (P1).
            viewModelScope.launch {
                while (isActive) {
                    delay(5 * 60 * 1000L)
                    val idsToUpdate =
                        tradingStatuses.value.keys
                            .toList()
                    if (idsToUpdate.isNotEmpty()) {
                        updateTradingStatuses(idsToUpdate)
                    }
                }
            }
            // Восстановление lastSelectedInstruments из CardsRepository:
            // identity (uid + broker + account) сохраняются между
            // запусками; enrich (currentPrice, quantity, profit, …)
            // выполняется здесь через getInstrument(uid) + refresh.
            // Подписка также реагирует на saveBrokerSettings
            // (brokerName / accountId per-card).
            viewModelScope.launch {
                logger.d(TAG, "subscribed to cards identities")
                cardsRepository.identities.collect { identities ->
                    val existing =
                        _coordState.value.lastSelectedInstruments
                            .associateBy { it.instrument.tscalpInstrumentId }
                    val enriched =
                        identities.mapNotNull { identity ->
                            val existingCard = existing[identity.tscalpInstrumentId]
                            if (existingCard != null) {
                                existingCard.copy(
                                    brokerName = identity.brokerName,
                                    accountId = identity.accountId,
                                )
                            } else {
                                val instrument =
                                    instrumentRepo.getInstrument(identity.tscalpInstrumentId)
                                if (instrument == null) {
                                    logger.w(
                                        TAG,
                                        "cards: instrument not found: uid=${identity.tscalpInstrumentId}",
                                    )
                                    return@mapNotNull null
                                }
                                SelectedInstrumentInfo(
                                    instrument = instrument,
                                    currentPrice = null,
                                    priceChangePercent = null,
                                    quantity = 0L,
                                    averagePrice = null,
                                    profit = null,
                                    profitPercent = null,
                                    brokerName = identity.brokerName,
                                    accountId = identity.accountId,
                                )
                            }
                        }
                    _coordState.update { it.copy(lastSelectedInstruments = enriched) }
                    refreshLastSelectedInstruments()
                    startPositionUpdates()
                }
            }
        }

        fun checkApiInitialization() {
            viewModelScope.launch {
                val initResult = checkBrokerInitializationUseCase.execute(PriceConsumer.ORDERS)
                when (initResult) {
                    is AppResult.Success -> {
                        when (initResult.data) {
                            is BrokerInitState.AnyInitialized -> {
                                logger.d(TAG, "checkApiInitialization: anyApiInitialized=true")
                                _coordState.update { it.copy(isApiInitialized = true) }
                                if (brokerManager.getDefaultBroker().isInitialized) {
                                    loadAccounts()
                                }
                            }
                            is BrokerInitState.NoneInitialized -> {
                                logger.d(TAG, "checkApiInitialization: anyApiInitialized=false")
                                _coordState.update { it.copy(isApiInitialized = false) }
                                stopPositionUpdates()
                                _coordState.update {
                                    it.copy(
                                        accounts = emptyList(),
                                        selectedAccountId = null,
                                        portfolioPositions = emptyList(),
                                        lastSelectedInstruments = emptyList(),
                                    )
                                }
                            }
                        }
                    }
                    is AppResult.Failure -> {
                        logger.e(TAG, "checkApiInitialization failed: ${initResult.error.message}", initResult.error.cause)
                        _coordState.update { it.copy(isApiInitialized = false) }
                    }
                }
            }
        }

        fun loadAccounts() {
            viewModelScope.launch {
                logger.d(TAG, "loadAccounts: start")
                _coordState.update { it.copy(isLoading = true) }

                val sandboxMode = sandboxPreferencesRepository.isSandboxMode()
                val result = loadOrdersAccountsUseCase.execute(BrokerName.TINVEST, sandboxMode)
                when (result) {
                    is AppResult.Success -> {
                        val data = result.data
                        _coordState.update {
                            it.copy(
                                accounts = data.accounts,
                                selectedAccountId = data.selectedAccountId,
                                isLoading = false,
                                statusMessage =
                                    if (data.accounts.isEmpty()) {
                                        "Нет доступных счетов"
                                    } else {
                                        "Загружено ${data.accounts.size} счёт(ов)"
                                    },
                            )
                        }
                        logger.i(
                            TAG,
                            "loadAccounts: success, accounts=${data.accounts.size}, selected=${data.selectedAccountId}",
                        )
                        accountSelectionRepository.select(BrokerName.TINVEST, data.selectedAccountId)
                        if (data.selectedAccountId != null) {
                            startPositionUpdates()
                        }
                    }
                    is AppResult.Failure -> {
                        logger.e(
                            TAG,
                            "loadAccounts failed: ${result.error.message}",
                            result.error.cause,
                        )
                        _coordState.update {
                            it.copy(
                                isLoading = false,
                                statusMessage = "Ошибка загрузки счетов: ${result.error.message}",
                                isError = true,
                            )
                        }
                    }
                }
            }
        }

        fun onAccountSelected(accountId: String) {
            _coordState.update { it.copy(selectedAccountId = accountId) }
            viewModelScope.launch {
                accountSelectionRepository.select(BrokerName.TINVEST, accountId)
            }
        }

        /**
         * Пересчитывает `lastSelectedInstruments` по per-card accountId:
         * счёт из свойств карточки (`card.accountId`) приоритетнее
         * глобального (`selectedAccountId` — вкладка Настройки).
         *
         * Источник — `PositionFlowManager.sheet` (StateFlow, sync read),
         * а не `_coordState.portfolioPositions` (глобальный счёт). Это
         * делает карточки реактивными к sheet emit (polling ~10 сек,
         * WS) без перепоиска инструмента.
         *
         * Fallback (позиция не найдена на счёте карточки): `quantity = 0L`,
         * `averagePrice` сохраняется, `profit / profitPercent = 0.0` (1:1).
         */
        private fun refreshLastSelectedInstruments() {
            val state = _coordState.value
            if (state.lastSelectedInstruments.isEmpty()) return
            val sheet = positionFlowManager.sheet.value
            logger.d(
                TAG,
                "refreshLastSelectedInstruments: global=${state.selectedAccountId}, " +
                    "sheetAccounts=${sheet.accounts.keys.map { it.accountId }}, cards=${state.lastSelectedInstruments.size}",
            )
            val updatedList =
                refreshSelectedInstrumentsUseCase.execute(
                    cards = state.lastSelectedInstruments,
                    globalAccountId = state.selectedAccountId,
                    sheet = sheet,
                )
            _coordState.update { it.copy(lastSelectedInstruments = updatedList) }
        }

        fun clearStatus() {
            _coordState.update { it.clearStatus() }
        }

        fun retryLoadAccounts() {
            loadAccounts()
        }

        private suspend fun updateTradingStatuses(ids: List<String>) {
            if (ids.isEmpty()) return
            // 13d-F-A: usecase пишет в TradingStatusRepository (SSoT),
            // Coord не дублирует статусы в state (passthrough выше).
            when (val result = updateTradingStatusesUseCase.execute(BrokerName.TINVEST, ids)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> {
                    logger.e(
                        TAG,
                        "Ошибка обновления статусов: ${result.error.message}",
                        result.error.cause,
                    )
                }
            }
        }

        suspend fun startPositionUpdates() {
            stopPositionUpdates()
            val globalAccountId = _coordState.value.selectedAccountId
            val accountIds =
                buildSet {
                    globalAccountId?.let { add(it) }
                    _coordState.value.lastSelectedInstruments.forEach { card ->
                        card.accountId?.let { add(it) }
                    }
                }
            if (accountIds.isEmpty()) {
                logger.d(TAG, "startPositionUpdates: no accounts, skip")
                return
            }
            logger.d(
                TAG,
                "startPositionUpdates: global=$globalAccountId, accounts=$accountIds",
            )
            positionFlowManager.start(
                accountKeys = accountIds.map { AccountKey(BrokerName.TINVEST, it) }.toSet(),
                sandboxMode = sandboxPreferencesRepository.isSandboxMode(),
            )
            positionStreamJob =
                viewModelScope.launch {
                    // Ошибки потока логирует сам PositionFlowManager
                    // через свой CoroutineExceptionHandler — сюда они не доходят.
                    // .catch на SharedFlow смысла не имеет: поток не завершается
                    // и не бросает исключения подписчику.
                    positionFlowManager.sheet.collect { sheet ->
                        updatePositionsFromSheet(sheet)
                    }
                }
            // Если позиции ещё не загружены (например, после восстановления состояния),
            // делаем разовый прямой запрос, чтобы сразу заполнить карточку
            // (только для global-счёта; без него fallback не нужен)
            if (globalAccountId != null && _coordState.value.portfolioPositions.isEmpty()) {
                viewModelScope.launch {
                    val sandbox = sandboxPreferencesRepository.isSandboxMode()
                    when (val result = portfolioRepository.fetchPositionsResult(BrokerName.TINVEST, globalAccountId, sandbox)) {
                        is AppResult.Success ->
                            _coordState.update { it.copy(portfolioPositions = result.data) }
                        is AppResult.Failure ->
                            logger.w(
                                TAG,
                                "Не удалось получить начальный портфель: ${result.error.message}",
                                result.error.cause,
                            )
                    }
                }
            }
        }

        fun stopPositionUpdates() {
            positionStreamJob?.cancel()
            positionStreamJob = null
        }

        override fun onCleared() {
            logger.d(TAG, "onCleared")
            // 13d-F-A: clearInterest(ORDERS) переехал в OrderFormViewModel
            // (Form-lifecycle цен). Coord — lifecycle позиций.
            stopPositionUpdates()
        }

        /**
         * Multi-account: `PositionFlowManager` эмитит `PortfolioSheet` со всех
         * счетов. Обновляем карточки только по активному счёту
         * ([OrdersCoordState.selectedAccountId]).
         */
        private fun updatePositionsFromSheet(sheet: PortfolioSheet) {
            val activeAccount = _coordState.value.selectedAccountId ?: return
            val key = AccountKey(BrokerName.TINVEST, activeAccount)
            val accountPortfolio = sheet.accounts[key] ?: return
            val positions = accountPortfolio.positions
            logger.d(TAG, "updatePositionsFromSheet: account=$activeAccount, positions=${positions.size}")
            _coordState.update { state ->
                state.copy(portfolioPositions = positions)
            }
            refreshLastSelectedInstruments()
        }
    }
