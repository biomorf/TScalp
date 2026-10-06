package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateOf

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.data.api.PositionStreamManager
import com.gitlab.biomorf.tscalp.data.repository.InvestRepository
import com.gitlab.biomorf.tscalp.data.repository.InstrumentRepository
import com.gitlab.biomorf.tscalp.data.repository.SearchCache
import com.gitlab.biomorf.tscalp.data.repository.SettingsRepository
import com.gitlab.biomorf.tscalp.data.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.api.PriceConsumer
import com.gitlab.biomorf.tscalp.domain.api.PriceStreamManager
import com.gitlab.biomorf.tscalp.domain.usecases.PrepareOrderRequestUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.CalculateTradeDetailsUseCase
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import com.gitlab.biomorf.tscalp.domain.models.toPortfolioPosition
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.StopOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.calculateProfitPercent
import com.gitlab.biomorf.tscalp.domain.models.FutureUi
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.map
import com.gitlab.biomorf.tscalp.util.formatCurrency
import com.gitlab.biomorf.tscalp.util.toAppError
import com.gitlab.biomorf.tscalp.util.AppLogger

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val repository: InvestRepository,
    private val instrumentRepo: InstrumentRepository,
    private val searchCache: SearchCache,
    private val settingsRepository: SettingsRepository,
    private val tradingStateRepository: TradingStateRepository,
    private val brokerManager: BrokerManager,
    private val priceStreamManager: PriceStreamManager,
    private val calculateTradeDetails: CalculateTradeDetailsUseCase,
    private val prepareOrderRequest: PrepareOrderRequestUseCase,
    private val positionStreamManager: PositionStreamManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var pairSearchJob: Job? = null
    private var positionStreamJob: Job? = null
    // Флаги для отображения диалога выбора брокера для основного и парного поиска
    val showSearchBrokerDialog = mutableStateOf(false)
    val showPairSearchBrokerDialog = mutableStateOf(false)

    // Текущий выбранный брокер для основного и парного поиска
    val selectedSearchBroker = mutableStateOf(BrokerName.TINVEST)
    val selectedPairSearchBroker = mutableStateOf(BrokerName.TINVEST)

    companion object {
        private const val TAG = "OrdersViewModel"
    }

    init {
        // Реагируем на изменение состояния инициализации брокеров.
        // StateFlow отдаёт текущее значение сразу при подписке — аналог
        // прежнего прямого вызова checkApiInitialization() в init.
        viewModelScope.launch {
            brokerManager.anyInitialized.collect {
                checkApiInitialization()
            }
        }
        // Подписка на настройку «подтверждение заявок».
        // Значение в state всегда актуальное: при переключении тумблера
        // в Настройках DataStore эмитит изменение, Flow доставляет,
        // state обновляется, onClick видит свежее значение.
        viewModelScope.launch {
            settingsRepository.confirmOrdersEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(confirmOrdersEnabled = enabled) }
            }
        }
        // Подписка на единый поток цен. Обновляет currentPrice /
        // pairCurrentPrice / selectedPriceChangePercent. Набор инструментов
        // задаётся через syncPriceInterest() при изменении
        // selectedInstrument / pairedInstrument.
        viewModelScope.launch {
            priceStreamManager.prices.collect { (uid, price) ->
                updateInstrumentPrice(uid, price)
            }
        }
        // Фоновое обновление статусов каждые 5 минут
        viewModelScope.launch {
            restoreState()
            while (isActive) {
                delay(5 * 60 * 1000L)
                val idsToUpdate = _uiState.value.tradingStatuses.keys.toList()
                if (idsToUpdate.isNotEmpty()) {
                    updateTradingStatuses(idsToUpdate)
                }
            }
        }
    }

    fun checkApiInitialization() {
        val isAnyApiInit = brokerManager.getAllBrokers().any { it.isInitialized }
        _uiState.update { it.copy(isApiInitialized = isAnyApiInit) }

        if (isAnyApiInit) {
            if (brokerManager.getDefaultBroker().isInitialized) {
                loadAccounts()
            }
            syncPriceInterest()
        } else {
            // Logout: останавливаем фоновые задачи, снимаем интерес к ценам
            // и очищаем состояние.
            stopPositionUpdates()
            priceStreamManager.clearInterest(PriceConsumer.ORDERS)
            positionStreamManager.stop()
            _uiState.update {
                it.copy(
                    accounts = emptyList(),
                    selectedAccountId = null,
                    portfolioPositions = emptyList(),
                    lastSelectedInstruments = emptyList(),
                    currentPrice = null,
                    pairCurrentPrice = null,
                    freeBalance = null,
                    tradingStatuses = emptyMap()
                )
            }
        }
    }

    private suspend fun saveState() {
        val state = _uiState.value
        tradingStateRepository.save(
            TradingStateSnapshot(
                selectedInstrumentUid = state.selectedInstrument?.tscalpInstrumentId,
                pairedInstrumentUid = state.pairedInstrument?.tscalpInstrumentId,
                pairTradingEnabled = state.pairTradingEnabled,
                quantity = state.quantity,
                pairedMultiplier = state.pairedMultiplier,
                orderType = state.orderType
            )
        )
    }

    private suspend fun restoreState() {
        val repo = instrumentRepo
        val brokerReady = brokerManager.getDefaultBroker().isInitialized

        val snapshot = tradingStateRepository.load()

        // Восстановление основного инструмента
        val uid = snapshot.selectedInstrumentUid
        if (uid != null && brokerReady) {
            val instrument = repo.getInstrument(uid)
            if (instrument != null) {
                _uiState.update { it.copy(selectedInstrument = instrument, ticker = instrument.ticker) }

                val pointVal = (instrument as? FutureUi)?.pointValue
                _uiState.update { it.copy(currentPointValue = pointVal) }

                syncPriceInterest()
            }
        }

        // Восстановление парного инструмента
        val pairUid = snapshot.pairedInstrumentUid
        if (pairUid != null && brokerReady) {
            val pairInstrument = repo.getInstrument(pairUid)
            if (pairInstrument != null) {
                _uiState.update { it.copy(pairedInstrument = pairInstrument) }

                val pairPointVal = (pairInstrument as? FutureUi)?.pointValue
                _uiState.update { it.copy(pairedPointValue = pairPointVal) }

                // если не был запущен ценовой стрим для основного, запустим сейчас
                if (_uiState.value.selectedInstrument != null) {
                    syncPriceInterest()
                }
            }
        }

        _uiState.update { state ->
            state.copy(
                pairTradingEnabled = snapshot.pairTradingEnabled,
                quantity = snapshot.quantity,
                pairedMultiplier = snapshot.pairedMultiplier,
                orderType = snapshot.orderType
            )
        }
        updateTradeDetails()
    }

    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val sandboxMode = settingsRepository.isSandboxMode()

            when (val result = repository.getAccountsResult(BrokerName.TINVEST, sandboxMode)) {
                is AppResult.Success -> {
                    val accounts = result.data
                    val savedAccountId = settingsRepository.loadDefaultAccountId(BrokerName.TINVEST)
                    val chosenAccount = accounts.firstOrNull { it.id == savedAccountId }
                        ?: accounts.firstOrNull()

                    _uiState.update {
                        it.copy(
                            accounts = accounts,
                            selectedAccountId = chosenAccount?.id,
                            isLoading = false,
                            statusMessage = if (accounts.isEmpty()) "Нет доступных счетов"
                            else "Загружено ${accounts.size} счёт(ов)"
                        )
                    }
                    if (_uiState.value.selectedAccountId != null) {
                        startPositionUpdates()
                    }
                }

                is AppResult.Failure -> {
                    AppLogger.e(
                        TAG,
                        "loadAccounts failed: ${result.error.message}",
                        result.error.cause
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Ошибка загрузки счетов: ${result.error.message}",
                            isError = true
                        )
                    }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, selectedInstrument = null, ticker = "") }
        searchJob?.cancel()
        if (query.length >= 2) {
            searchJob = viewModelScope.launch {
                try {
                    delay(500)
                    _uiState.update { it.copy(isSearching = true) }
                    val results = searchCache.search(_uiState.value.searchBroker, query)
                    // Обновляем статусы доступности для найденных инструментов
                    if (results.isNotEmpty()) {
                        launch {
                            updateTradingStatuses(results.map { it.tscalpInstrumentId })
                        }
                    }
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    _uiState.update { it.copy(isSearching = false) }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            searchResults = emptyList(),
                            isSearching = false,
                            statusMessage = "Ошибка поиска: ${e.message}",
                            isError = true
                        )
                    }
                }
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }

    fun onInstrumentSelected(instrument: InstrumentUi) {
        // Обновляем выбранный инструмент и поисковую строку
        _uiState.update {
            it.copy(
                selectedInstrument = instrument,
                ticker = instrument.ticker,
                searchQuery = "${instrument.ticker} - ${instrument.name}",
                searchResults = emptyList()
            )
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPriceLoading = true) }

            // Загружаем актуальный InstrumentUi с pointValue
            val actualInstrument = instrumentRepo.getInstrument(instrument.tscalpInstrumentId) ?: instrument

            // 1. Мгновенно получаем последнюю цену (чтобы не ждать стрим)
            val price = when (val pricesResult = repository.getLastPricesResult(listOf(instrument.tscalpInstrumentId))) {
                is AppResult.Success -> pricesResult.data[instrument.tscalpInstrumentId]
                is AppResult.Failure -> {
                    AppLogger.w(
                        TAG,
                        "onInstrumentSelected: getLastPrices failed: ${pricesResult.error.message}",
                        pricesResult.error.cause
                    )
                    null
                }
            }

            // 2. Ищем позицию в портфеле
            val portfolioPos = _uiState.value.portfolioPositions.find { it.ticker == instrument.ticker }

            // 3. Берём существующую карточку (если была), чтобы сохранить настройки брокера/счёта
            val existingCard = _uiState.value.lastSelectedInstruments.find { it.instrument.ticker == instrument.ticker }

            val newCard = SelectedInstrumentInfo(
                instrument = instrument,
                currentPrice = price,
                priceChangePercent = null,
                quantity = portfolioPos?.quantity ?: 0L,
                averagePrice = portfolioPos?.currentPrice,
                profit = portfolioPos?.profit,
                profitPercent = portfolioPos?.profitPercent,
                brokerName = existingCard?.brokerName ?: BrokerName.TINVEST,
                accountId = existingCard?.accountId
            )

            // 4. Обновляем список последних выбранных инструментов
            val currentList = _uiState.value.lastSelectedInstruments.toMutableList()
            currentList.removeAll { it.instrument.ticker == instrument.ticker }
            currentList.add(0, newCard)

            _uiState.update {
                it.copy(
                    currentPrice = price,
                    isPriceLoading = false,
                    lastSelectedInstruments = currentList.take(5)
                )
            }
            // Сохраняем pointValue для UI
            val pointVal = (actualInstrument as? FutureUi)?.pointValue
            _uiState.update { it.copy(currentPointValue = pointVal) }

            updateTradeDetails()

            // 5. Запускаем стрим для реактивного обновления цены
            syncPriceInterest()
            startPositionUpdates()
            saveState()
        }
    }

    fun clearSelectedInstrument() {
        _uiState.update {
            it.copy(
                selectedInstrument = null,
                ticker = "",
                searchQuery = "",
                currentPrice = null,
                isPriceLoading = false
            )
        }
        syncPriceInterest()
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active) }
        if (active) { _uiState.update { it.copy(searchResults = emptyList(), searchQuery = "") } }
    }

    fun clearSearch() { _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), selectedInstrument = null, ticker = "", currentPrice = null, isPriceLoading = false, isSearchActive = false) } }
    fun onQuantityChanged(quantity: String) {
        _uiState.update { it.copy(quantity = quantity.filter { it.isDigit() }) }
        updateTradeDetails()
        viewModelScope.launch { saveState() }
    }
    fun onAccountSelected(accountId: String) { _uiState.update { it.copy(selectedAccountId = accountId) } }
    fun onBuyClick() = viewModelScope.launch { postOrder(OrderDirection.BUY) }
    fun onSellClick() = viewModelScope.launch { postOrder(OrderDirection.SELL) }

    private suspend fun postOrder(direction: OrderDirection) {
        val state = _uiState.value
        val ticker = state.ticker.ifBlank { state.selectedInstrument?.ticker } ?: return
        val quantity = state.quantityAsLong ?: return

        val activeCard = state.lastSelectedInstruments.find { it.instrument.ticker == ticker }
        val brokerName = activeCard?.brokerName ?: BrokerName.TINVEST
        val accountId = activeCard?.accountId ?: state.selectedAccountId ?: return

        val tscalpId = state.selectedInstrument?.tscalpInstrumentId ?: return

        // Проверка доступности. Ошибки сети/API → понятное сообщение вместо краша.
        val checkResult = when (val result = repository.checkTradeAvailabilityResult(
            brokerName = brokerName,
            accountId = accountId,
            uid = tscalpId,
            direction = direction,
            quantity = quantity
        )) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> {
                AppLogger.e(TAG, "checkTradeAvailability failed: ${result.error.message}", result.error.cause)
                _uiState.update {
                    it.copy(statusMessage = "❌ ${result.error.message}", isError = true)
                }
                return
            }
        }

        when (checkResult) {
            is TradeCheckResult.Success -> { /* продолжаем */ }
            is TradeCheckResult.Error -> {
                _uiState.update {
                    it.copy(statusMessage = "❌ ${checkResult.message}", isError = true)
                }
                return
            }
        }

        // Готовим основную и контрсделку
        val prepared = prepareOrderRequest.prepare(
            brokerName = brokerName,
            ticker = ticker,
            instrumentUid = tscalpId,
            quantity = quantity,
            direction = direction,
            accountId = accountId,
            sandboxMode = settingsRepository.isSandboxMode(),
            orderType = state.orderType,
            limitPrice = state.limitPrice,
            stopPrice = state.stopPrice,
            expirationType = state.expirationType,
            pairedInstrumentUid = state.pairedInstrument?.tscalpInstrumentId,
            pairedTicker = state.pairedInstrument?.ticker,
            pairedBrokerName = state.lastSelectedInstruments
                .find { it.instrument.ticker == state.pairedInstrument?.ticker }?.brokerName,
            pairedAccountId = state.lastSelectedInstruments
                .find { it.instrument.ticker == state.pairedInstrument?.ticker }?.accountId,
            pairedMultiplier = state.pairedMultiplier
        )

        _uiState.update { it.copy(isLoading = true, statusMessage = null) }

        // --- Основная заявка ---
        val primaryOutcome: AppResult<String> = try {
            if (prepared.isPrimaryStop) {
                repository.postStopOrderResult(prepared.primaryRequest as StopOrderRequest)
                    .map { id -> "✅ Стоп‑заявка выставлена, ID: ${id.take(8)}…" }
            } else {
                repository.postOrderResult(prepared.primaryRequest as BrokerOrderRequest)
                    .map { r ->
                        "✅ Заявка выполнена!\nID: ${r.orderId}\n" +
                                "Исполнено: ${r.executedLots}/${r.totalLots} лотов"
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }

        val primaryMessage = when (primaryOutcome) {
            is AppResult.Success -> primaryOutcome.data
            is AppResult.Failure -> {
                AppLogger.e(
                    TAG,
                    "primary order failed: ${primaryOutcome.error.message}",
                    primaryOutcome.error.cause
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "❌ ${primaryOutcome.error.message}",
                        isError = true
                    )
                }
                return
            }
        }

        // --- Контрсделка (опционально) ---
        var finalMessage = primaryMessage
        if (prepared.pairedRequest != null) {
            finalMessage += try {
                if (prepared.isPairedStop == true) {
                    val pairedStop = prepared.pairedRequest as StopOrderRequest
                    when (val r = repository.postStopOrderResult(pairedStop)) {
                        is AppResult.Success ->
                            "\n✅ Контрсделка: ${state.pairedInstrument?.ticker} " +
                                    "${pairedStop.quantity} лотов, ID: ${r.data.take(8)}…"
                        is AppResult.Failure -> {
                            AppLogger.e(
                                TAG,
                                "paired stop order failed: ${r.error.message}",
                                r.error.cause
                            )
                            "\n❌ Ошибка контрсделки: ${r.error.message}"
                        }
                    }
                } else {
                    val pairedRegular = prepared.pairedRequest as BrokerOrderRequest
                    when (val r = repository.postOrderResult(pairedRegular)) {
                        is AppResult.Success ->
                            "\n✅ Контрсделка: ${state.pairedInstrument?.ticker} " +
                                    "${pairedRegular.quantity} лотов, ID: ${r.data.orderId}"
                        is AppResult.Failure -> {
                            AppLogger.e(
                                TAG,
                                "paired order failed: ${r.error.message}",
                                r.error.cause
                            )
                            "\n❌ Ошибка контрсделки: ${r.error.message}"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val appError = e.toAppError()
                AppLogger.e(TAG, "paired order unexpected error: ${appError.message}", e)
                "\n❌ Ошибка контрсделки: ${appError.message}"
            }
        }

        // --- Пост-обработка: обновляем карточки и баланс ---
        refreshLastSelectedInstruments()
        val currentBalance = when (val balanceResult = repository.getBalanceResult(accountId)) {
            is AppResult.Success -> balanceResult.data
            is AppResult.Failure -> {
                AppLogger.w(
                    TAG,
                    "postOrder: getBalance failed: ${balanceResult.error.message}",
                    balanceResult.error.cause
                )
                null
            }
        }

        if (currentBalance != null && currentBalance < 1000.0) {
            finalMessage += "\n⚠️ Низкий свободный остаток: ${formatCurrency(currentBalance)}"
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                statusMessage = finalMessage,
                isError = false,
                quantity = "",
                limitPrice = "",
                stopPrice = "",
                freeBalance = currentBalance
            )
        }
    }

    /**
     * Обновляет список lastSelectedInstruments, подтягивая актуальные данные из портфеля.
     */
    private fun refreshLastSelectedInstruments() {
        val currentList = _uiState.value.lastSelectedInstruments
        if (currentList.isEmpty()) return

        val positions = _uiState.value.portfolioPositions
        val updatedList = currentList.map { card ->
            val pos = positions.find { it.ticker == card.instrument.ticker }
            card.copy(
                quantity = pos?.quantity ?: 0L,
                averagePrice = pos?.currentPrice ?: card.averagePrice,
                profit = pos?.profit ?: 0.0,
                profitPercent = pos?.profitPercent ?: 0.0
            )
        }
        _uiState.update { it.copy(lastSelectedInstruments = updatedList) }
    }

    fun clearStatus() { _uiState.update { it.clearStatus() } }

    fun isConfirmOrdersEnabled(): Boolean =
        _uiState.value.confirmOrdersEnabled

    fun getAvailableBrokerNames(): List<BrokerName> =
        brokerManager.getAvailableBrokerNames()

    fun retryLoadAccounts() { loadAccounts() }


    /**
     * Открывает диалог настроек брокера/счёта для указанного тикера.
     */
    fun openBrokerDialog(ticker: String) {
        viewModelScope.launch {
            val existingCard = _uiState.value.lastSelectedInstruments.find { it.instrument.ticker == ticker }
            val broker = existingCard?.brokerName ?: BrokerName.TINVEST

            val accounts = when (val result = repository.getAccountsResult(
                broker,
                settingsRepository.isSandboxMode()
            )) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> {
                    AppLogger.e(
                        TAG,
                        "Ошибка загрузки счетов для ${broker.displayName}: ${result.error.message}",
                        result.error.cause
                    )
                    emptyList()
                }
            }

            val savedAccountId = existingCard?.accountId ?: _uiState.value.selectedAccountId
            val selectedId = if (savedAccountId != null && accounts.any { it.id.trim() == savedAccountId.trim() }) {
                savedAccountId
            } else {
                accounts.firstOrNull()?.id
            }

            _uiState.update {
                it.copy(
                    showBrokerDialog = true,
                    dialogInstrumentTicker = ticker,
                    selectedBroker = broker,
                    selectedAccountIdDialog = selectedId,
                    dialogAccounts = accounts
                )
            }
        }
    }

    /**
     * Закрывает диалог без сохранения.
     */
    fun closeBrokerDialog() {
        _uiState.update { it.copy(showBrokerDialog = false, dialogInstrumentTicker = null,
            swipeResetTrigger = !it.swipeResetTrigger) }
    }

    /**
     * Обрабатывает выбор брокера в диалоге – загружает его счета.
     */
    fun onBrokerSelected(brokerName: BrokerName) {
        _uiState.update { it.copy(selectedBroker = brokerName, selectedAccountIdDialog = null) }
        viewModelScope.launch {
            loadDialogAccounts(brokerName)
        }
    }

    /**
     * Обрабатывает выбор счёта в диалоге.
     */
    fun onAccountSelectedDialog(accountId: String) {
        _uiState.update { it.copy(selectedAccountIdDialog = accountId) }
    }

    /**
     * Загружает счета для указанного брокера в UIState диалога.
     */
     private suspend fun loadDialogAccounts(brokerName: BrokerName) {
         when (val result = repository.getAccountsResult(
             brokerName,
             settingsRepository.isSandboxMode()
         )) {
             is AppResult.Success -> {
                 val accounts = result.data
                 _uiState.update { state ->
                     val current = state.selectedAccountIdDialog?.trim()
                     val newSelected = if (current != null && accounts.any { it.id.trim() == current }) {
                         state.selectedAccountIdDialog
                     } else {
                         accounts.firstOrNull()?.id
                     }
                     state.copy(
                         dialogAccounts = accounts,
                         selectedAccountIdDialog = newSelected
                     )
                 }
             }
             is AppResult.Failure -> {
                 AppLogger.e(
                     TAG,
                     "Ошибка загрузки счетов для $brokerName: ${result.error.message}",
                     result.error.cause
                 )
                 _uiState.update { it.copy(dialogAccounts = emptyList(), selectedAccountIdDialog = null) }
             }
         }
     }

    /**
     * Сохраняет выбранные настройки для инструмента и закрывает диалог.
     */
    fun saveBrokerSettings() {
        val ticker = _uiState.value.dialogInstrumentTicker ?: return
        val broker = _uiState.value.selectedBroker
        val accountId = _uiState.value.selectedAccountIdDialog

        _uiState.update { state ->
            state.copy(
                lastSelectedInstruments = state.lastSelectedInstruments.map { card ->
                    if (card.instrument.ticker == ticker) {
                        card.copy(brokerName = broker, accountId = accountId)
                    } else card
                },
                showBrokerDialog = false,
                dialogInstrumentTicker = null,
                swipeResetTrigger = !state.swipeResetTrigger
            )
        }
    }

    fun setPairTradingEnabled(enabled: Boolean) {
        _uiState.update {
            if (enabled) {
                it.copy(pairTradingEnabled = true)
            } else {
                it.copy(
                    pairTradingEnabled = false,
                    pairCurrentPrice = null,
                    pairedInstrument = null,
                    pairedMultiplier = "10",
                    pairSearchQuery = "",
                    pairSearchResults = emptyList()
                )
            }
        }
        viewModelScope.launch { saveState() }
        syncPriceInterest()
    }

    fun onPairSearchQueryChanged(query: String) {
        _uiState.update { it.copy(pairSearchQuery = query) }
        pairSearchJob?.cancel()
        if (query.length >= 2) {
            pairSearchJob = viewModelScope.launch {
                delay(500)
                _uiState.update { it.copy(isPairSearching = true) }
                try {
                    val results = searchCache.search(_uiState.value.pairSearchBroker, query)
                    // Обновляем статусы доступности для найденных инструментов
                    if (results.isNotEmpty()) {
                        launch {
                            updateTradingStatuses(results.map { it.tscalpInstrumentId })
                        }
                    }
                    _uiState.update { it.copy(pairSearchResults = results, isPairSearching = false) }
                } catch (ce: CancellationException) {
                    _uiState.update { it.copy(isPairSearching = false) }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            pairSearchResults = emptyList(),
                            isPairSearching = false,
                            statusMessage = "Ошибка поиска: ${e.message}",
                            isError = true
                        )
                    }
                }
            }
        } else {
            _uiState.update { it.copy(pairSearchResults = emptyList(), isPairSearching = false) }
        }
    }

    fun onPairedInstrumentSelected(instrument: InstrumentUi) {
        _uiState.update { it.copy(pairedInstrument = instrument, pairSearchQuery = "${instrument.ticker} - ${instrument.name}", pairSearchResults = emptyList()) }
        syncPriceInterest()   // обновляем интерес к ценам обоих инструментов
        viewModelScope.launch {
            val price = when (val pricesResult = repository.getLastPricesResult(listOf(instrument.tscalpInstrumentId))) {
                is AppResult.Success -> pricesResult.data[instrument.tscalpInstrumentId]
                is AppResult.Failure -> {
                    AppLogger.w(
                        TAG,
                        "onPairedInstrumentSelected: getLastPrices failed: ${pricesResult.error.message}",
                        pricesResult.error.cause
                    )
                    null
                }
            }
            if (price != null) {
                _uiState.update { it.copy(pairCurrentPrice = price) }
                // Сохраняем pointValue парного инструмента
                val pairedPointVal = (instrument as? FutureUi)?.pointValue
                _uiState.update { it.copy(pairedPointValue = pairedPointVal) }
            }
            updateTradeDetails()
            saveState()
        }
    }

    fun clearPairSearch() {
        _uiState.update { it.copy(pairSearchQuery = "", pairSearchResults = emptyList(), pairedInstrument = null) }
        syncPriceInterest()
    }

    fun onPairedMultiplierChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(pairedMultiplier = filtered) }
        viewModelScope.launch { saveState() }
    }

    fun onOrderTypeChanged(type: OrderTypeSelection) {
        _uiState.update { it.copy(orderType = type) }
        if (type == OrderTypeSelection.Market) {
            _uiState.update { it.copy(limitPrice = "") }
        }
        updateTradeDetails()
        viewModelScope.launch { saveState() }
    }


    fun onLimitPriceChanged(price: String) {
        val filtered = price.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(limitPrice = filtered) }
        updateTradeDetails()
        viewModelScope.launch { saveState() }
    }

    fun onStopPriceChanged(price: String) {
        _uiState.update { it.copy(stopPrice = price.filter { it.isDigit() || it == '.' }) }
        updateTradeDetails()
        viewModelScope.launch { saveState() }
    }

    private suspend fun updateTradingStatuses(ids: List<String>) {
        if (ids.isEmpty()) return
        when (val result = repository.getTradingStatusesResult(BrokerName.TINVEST, ids)) {
            is AppResult.Success -> {
                _uiState.update { state ->
                    state.copy(tradingStatuses = state.tradingStatuses + result.data)
                }
            }
            is AppResult.Failure -> {
                AppLogger.e(
                    TAG,
                    "Ошибка обновления статусов: ${result.error.message}",
                    result.error.cause
                )
            }
        }
    }

    fun startPositionUpdates() {
        stopPositionUpdates()
        val accountId = _uiState.value.selectedAccountId ?: return
        positionStreamManager.start(accountId)
        positionStreamJob = viewModelScope.launch {
            // Ошибки потока логирует сам PositionStreamManager
            // через свой CoroutineExceptionHandler — сюда они не доходят.
            // .catch на SharedFlow смысла не имеет: поток не завершается
            // и не бросает исключения подписчику.
            positionStreamManager.flow.collect { item ->
                updatePositionPnl(item)
            }
        }
        // Если позиции ещё не загружены (например, после восстановления состояния),
        // делаем разовый прямой запрос, чтобы сразу заполнить карточку
        if (_uiState.value.portfolioPositions.isEmpty()) {
            viewModelScope.launch {
                val sandbox = settingsRepository.isSandboxMode()
                when (val result = repository.fetchPositionsResult(BrokerName.TINVEST, accountId, sandbox)) {
                    is AppResult.Success ->
                        _uiState.update { it.copy(portfolioPositions = result.data) }
                    is AppResult.Failure ->
                        AppLogger.w(
                            TAG,
                            "Не удалось получить начальный портфель: ${result.error.message}",
                            result.error.cause
                        )
                }
            }
        }
    }

    fun stopPositionUpdates() {
        positionStreamJob?.cancel()
        positionStreamJob = null
    }

    /**
     * Собирает текущий набор uid (selectedInstrument + pairedInstrument)
     * и передаёт в PriceStreamManager. Менеджер пересоздаст стрим только
     * при фактическом изменении union.
     */
    private fun syncPriceInterest() {
        val state = _uiState.value
        val uids = buildSet {
            state.selectedInstrument?.let { add(it.tscalpInstrumentId) }
            state.pairedInstrument?.let { add(it.tscalpInstrumentId) }
        }
        priceStreamManager.setInterest(PriceConsumer.ORDERS, uids)
    }

    /**
     * Применяет обновление цены из PriceStreamManager к соответствующему
     * инструменту в UI: selectedInstrument или pairedInstrument.
     * Для неизвестного uid — no-op.
     */
    private fun updateInstrumentPrice(uid: String, price: Double) {
        _uiState.update { state ->
            when (uid) {
                state.selectedInstrument?.tscalpInstrumentId -> {
                    val oldPrice = state.currentPrice
                    val newPercent = if (oldPrice != null && oldPrice != 0.0) {
                        ((price - oldPrice) / oldPrice) * 100.0
                    } else null
                    state.copy(
                        currentPrice = price,
                        selectedPriceChangePercent = newPercent
                    )
                }
                state.pairedInstrument?.tscalpInstrumentId -> {
                    state.copy(pairCurrentPrice = price)
                }
                else -> state
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        priceStreamManager.clearInterest(PriceConsumer.ORDERS)
    }

    private fun updatePositionPnl(item: PositionStreamItem) {
        val avgPrice = item.averagePositionPrice ?: return
        val yield = item.expectedYield ?: return
        val quantity = item.quantity
        if (quantity == 0L) return

        AppLogger.d(TAG, "updatePositionPnl: uid=${item.instrumentUid}, avgPrice=$avgPrice, yield=$yield, quantity=$quantity")

        val profitPercent = calculateProfitPercent(
            yield = yield,
            avgPrice = avgPrice,
            quantity = quantity,
            pointValue = item.pointValue
        )

        _uiState.update { state ->
            val positions = state.portfolioPositions.toMutableList()
            val index = positions.indexOfFirst { it.tscalpInstrumentId == item.instrumentUid }
            if (index == -1) {
                positions.add(item.toPortfolioPosition())
            } else {
                val old = positions[index]
                val newPointValue = item.pointValue ?: old.pointValue
                positions[index] = old.copy(
                    quantity = quantity,
                    currentPrice = item.currentPrice ?: old.currentPrice,
                    averagePrice = avgPrice,
                    totalValue = (item.currentPrice ?: old.currentPrice) * quantity,
                    profit = yield,
                    profitPercent = profitPercent,
                    pointValue = newPointValue,
                    instrumentType = item.instrumentType
                )
            }
            state.copy(portfolioPositions = positions)
        }
    }

    fun openSearchBrokerSettings() {
        showSearchBrokerDialog.value = true
    }

    fun openPairSearchBrokerSettings() {
        showPairSearchBrokerDialog.value = true
    }

    fun saveSearchBrokerSettings(broker: BrokerName) {
        selectedSearchBroker.value = broker
        showSearchBrokerDialog.value = false
    }

    fun savePairSearchBrokerSettings(broker: BrokerName) {
        selectedPairSearchBroker.value = broker
        showPairSearchBrokerDialog.value = false
    }

    fun dismissSearchBrokerDialog() { showSearchBrokerDialog.value = false }
    fun dismissPairSearchBrokerDialog() { showPairSearchBrokerDialog.value = false }

    fun refreshSearch() {
        val state = _uiState.value
        if (state.searchQuery.length >= 2) {
            searchCache.invalidate(state.searchBroker, state.searchQuery)
            onSearchQueryChanged(state.searchQuery)
        }
    }

    fun refreshPairSearch() {
        val state = _uiState.value
        if (state.pairSearchQuery.length >= 2) {
            searchCache.invalidate(state.pairSearchBroker, state.pairSearchQuery)
            onPairSearchQueryChanged(state.pairSearchQuery)
        }
    }

    private fun updateTradeDetails() {
        val state = _uiState.value
        val details = calculateTradeDetails.calculate(
            currentPrice = state.currentPrice,
            limitPrice = state.limitPrice,
            stopPrice = state.stopPrice,
            orderType = state.orderType,
            instrument = state.selectedInstrument,
            pairedInstrument = state.pairedInstrument,
            quantity = state.quantityAsLong ?: 0L,
            pairedMultiplier = state.pairedMultiplier,
            pairCurrentPrice = state.pairCurrentPrice
        )
        _uiState.update { it.copy(executionPrice = details.executionPrice, costOverlay = details.costOverlay, multiplierOverlay = details.multiplierOverlay) }
    }
}
