package com.example.tscalp.presentation.screens.orders

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collect

import com.example.tscalp.util.formatCurrency

import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.data.repository.InvestRepository
import com.example.tscalp.data.repository.SearchCache
import com.example.tscalp.data.api.SharedPositionStreamManager
import com.example.tscalp.data.api.TInvestInvestService
import com.example.tscalp.presentation.screens.orders.OrdersUiState
import com.example.tscalp.domain.models.InstrumentUi
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.domain.models.BrokerOrderType
import com.example.tscalp.domain.models.OrderTypeSelection
import com.example.tscalp.domain.models.BrokerOrderRequest
import com.example.tscalp.domain.models.OrderDirection
import com.example.tscalp.domain.models.StopOrderType
import com.example.tscalp.domain.models.StopOrderRequest
import com.example.tscalp.domain.models.TradingAvailability
import com.example.tscalp.domain.models.TradeCheckResult
import com.example.tscalp.domain.models.PositionStreamItem
import com.example.tscalp.domain.usecases.PairOrderMapper


class OrdersViewModel(
    private val repository: InvestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var pairSearchJob: Job? = null
    private var priceStreamJob: Job? = null
    private var positionStreamJob: Job? = null
    private val prefs = ServiceLocator.getPrefs()
    // Флаги для отображения диалога выбора брокера для основного и парного поиска
    val showSearchBrokerDialog = mutableStateOf(false)
    val showPairSearchBrokerDialog = mutableStateOf(false)

    // Текущий выбранный брокер для основного и парного поиска
    val selectedSearchBroker = mutableStateOf("TInvest")
    val selectedPairSearchBroker = mutableStateOf("TInvest")


    companion object {
        private const val TAG = "OrdersViewModel"

        private fun orderTypeToString(type: OrderTypeSelection): String = when (type) {
            OrderTypeSelection.Market -> "Market"
            OrderTypeSelection.Limit -> "Limit"
            OrderTypeSelection.StopLoss -> "StopLoss"
            OrderTypeSelection.TakeProfit -> "TakeProfit"
            OrderTypeSelection.StopLimit -> "StopLimit"
        }

        private fun stringToOrderType(str: String): OrderTypeSelection = when (str) {
            "Market" -> OrderTypeSelection.Market
            "Limit" -> OrderTypeSelection.Limit
            "StopLoss" -> OrderTypeSelection.StopLoss
            "TakeProfit" -> OrderTypeSelection.TakeProfit
            "StopLimit" -> OrderTypeSelection.StopLimit
            else -> OrderTypeSelection.Market
        }

        /**
         * Флаг, определяющий способ обновления P&L.
         * false → периодический опрос портфеля (loadPortfolio)
         * true  → стрим PositionsStream (когда SDK будет совместим)
         */
        private const val USE_POSITION_STREAM = false
    }

    init {
        checkApiInitialization()
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
        val isAnyApiInit = ServiceLocator.isAnyBrokerInitialized()
        _uiState.update { it.copy(isApiInitialized = isAnyApiInit) }
        if (isAnyApiInit) {
            // Загружаем счета только если дефолтный брокер инициализирован
            if (ServiceLocator.getBrokerManager().getDefaultBroker().isInitialized) {
                loadAccounts()
                //viewModelScope.launch { startPositionUpdates() }
            }
            startPriceUpdates()
            // startPositionUpdates() // удалить эту строку, если она дублирует вызов внутри loadAccounts()
        }
    }

    fun initializeApi(token: String, sandboxMode: Boolean) {
        try {
            ServiceLocator.saveBrokerCredentials("TInvest", token, sandboxMode)
            (ServiceLocator.getBrokerManager().getBroker("TInvest") as? TInvestInvestService)?.initializeFromSettings()

            _uiState.update {
                it.copy(
                    isApiInitialized = true,
                    statusMessage = "API подключен (режим: ${if (sandboxMode) "песочница" else "боевой"})",
                    isError = false
                )
            }
            loadAccounts()
            viewModelScope.launch { startPositionUpdates() }   // <-- обернули в корутину
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    statusMessage = "Ошибка подключения: ${e.message}",
                    isError = true
                )
            }
        }
    }

    private fun saveState() {
        val state = _uiState.value
        prefs.edit()
            .putString("selected_instrument_uid", state.selectedInstrument?.tscalpInstrumentId)
            .putString("paired_instrument_uid", state.pairedInstrument?.tscalpInstrumentId)
            .putBoolean("pair_trading_enabled", state.pairTradingEnabled)
            .putString("quantity", state.quantity)
            .putString("paired_multiplier", state.pairedMultiplier)
            .putString("order_type", orderTypeToString(state.orderType))   // ← исправлено
            .apply()
    }

    private suspend fun restoreState() {
        val repo = ServiceLocator.getInstrumentRepository()

        // Восстановление основного инструмента
        val uid = prefs.getString("selected_instrument_uid", null)
        if (uid != null) {
            val instrument = repo.getInstrument(uid)
            if (instrument != null) {
                _uiState.update { it.copy(selectedInstrument = instrument, ticker = instrument.ticker) }
                startPriceUpdates()
                startPositionUpdates()
            }
        }

        // Восстановление парного инструмента
        val pairUid = prefs.getString("paired_instrument_uid", null)
        if (pairUid != null) {
            val pairInstrument = repo.getInstrument(pairUid)
            if (pairInstrument != null) {
                _uiState.update { it.copy(pairedInstrument = pairInstrument) }
                // если не был запущен ценовой стрим для основного, запустим сейчас
                if (_uiState.value.selectedInstrument != null) {
                    startPriceUpdates()
                }
            }
        }

        val pairEnabled = prefs.getBoolean("pair_trading_enabled", false)
        val savedQty = prefs.getString("quantity", "") ?: ""
        val savedMultiplier = prefs.getString("paired_multiplier", "10") ?: "10"
        val savedOrderType = prefs.getString("order_type", null)

        _uiState.update { state ->
            state.copy(
                pairTradingEnabled = pairEnabled,
                quantity = savedQty,
                pairedMultiplier = savedMultiplier,
                orderType = savedOrderType?.let { stringToOrderType(it) } ?: OrderTypeSelection.Market  // ← исправлено
            )
        }
    }

    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val sandboxMode = ServiceLocator.isSandboxMode()
                val brokerName = "TInvest"
                val accounts = repository.getAccounts(brokerName, sandboxMode)
                if (_uiState.value.selectedAccountId == null && accounts.isNotEmpty()) {
                    _uiState.update { it.copy(selectedAccountId = accounts.first().id) }
                }
                val defaultAccount = accounts.firstOrNull()
                _uiState.update {
                    it.copy(
                        accounts = accounts,
                        selectedAccountId = defaultAccount?.id,
                        isLoading = false,
                        statusMessage = if (accounts.isEmpty()) "Нет доступных счетов"
                        else "Загружено ${accounts.size} счёт(ов)"
                    )
                }
                // Запускаем обновление позиций, если счёт выбран
                if (_uiState.value.selectedAccountId != null) {
                    startPositionUpdates()
                }
            } catch (e: Exception) {
                // ...
            }
        }
    }

//    /**
//     * Загружает портфель для первого счета и возвращает список позиций.
//     * Теперь это suspend-функция, которую можно await'ить.
//     */
//    private suspend fun loadPortfolio(
//        brokerName: String = "TInvest",
//        accountId: String? = null
//    ) {
//        try {
//            val sandboxMode = ServiceLocator.isSandboxMode()
//            val broker = ServiceLocator.getBrokerManager().getBroker(brokerName) ?: return
//            // Если accountId не передан, получаем его через getAccounts (только для TInvest)
//            val actualAccountId = accountId ?: run {
//                val accounts = broker.getAccounts(sandboxMode)
//                accounts.firstOrNull()?.id ?: return
//            }
//            val newPositions = broker.getPositions(actualAccountId, sandboxMode)
//            Log.d(TAG, "Позиции загружены: ${newPositions.map { "${it.ticker} profit=${it.profit} percent=${it.profitPercent}" }}")
//
//            // Обновляем portfolioPositions: удаляем старые позиции этого брокера и добавляем новые
//            val currentPositions = _uiState.value.portfolioPositions.toMutableList()
//            currentPositions.removeAll { it.brokerName == brokerName }
//            currentPositions.addAll(newPositions.map { it.copy(brokerName = brokerName) })
//            _uiState.update { it.copy(portfolioPositions = currentPositions) }
//        } catch (e: Exception) {
//            Log.e(TAG, "Ошибка загрузки портфеля для $brokerName", e)
//        }
//    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, selectedInstrument = null, ticker = "") }
        searchJob?.cancel()
        if (query.length >= 2) {
            searchJob = viewModelScope.launch {
                try {
                    delay(500)
                    _uiState.update { it.copy(isSearching = true) }
                    val cache = ServiceLocator.getSearchCache()
                    val brokerName = _uiState.value.searchBroker
                    val results = cache.search(brokerName, query)
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

            // 🔁 Загружаем актуальный InstrumentUi с pointValue
            val repo = ServiceLocator.getInstrumentRepository()
            val actualInstrument = repo.getInstrument(instrument.tscalpInstrumentId) ?: instrument
            _uiState.update { it.copy(selectedInstrument = actualInstrument, ticker = actualInstrument.ticker) }

            // 1. Мгновенно получаем последнюю цену (чтобы не ждать стрим)
            val prices = repository.getLastPricesByTscalpInstrumentId(listOf(instrument.tscalpInstrumentId))
            val price = prices[instrument.tscalpInstrumentId]

            // 2. Ищем позицию в портфеле
            val portfolioPos = _uiState.value.portfolioPositions.find { it.ticker == instrument.ticker }

            // 3. Берём существующую карточку (если была), чтобы сохранить настройки брокера/счёта
            val existingCard = _uiState.value.lastSelectedInstruments.find { it.instrument.ticker == instrument.ticker }

            val newCard = SelectedInstrumentInfo(
                instrument = instrument,
                currentPrice = price,
                priceChange = null,
                priceChangePercent = null,
                quantity = portfolioPos?.quantity ?: 0L,
                averagePrice = portfolioPos?.currentPrice,
                profit = portfolioPos?.profit,
                profitPercent = portfolioPos?.profitPercent,
                brokerName = existingCard?.brokerName ?: "TInvest",
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

            // 5. Запускаем стрим для реактивного обновления цены
            startPriceUpdates()
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
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active) }
        if (active) { _uiState.update { it.copy(searchResults = emptyList(), searchQuery = "") } }
    }

    fun clearSearch() { _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), selectedInstrument = null, ticker = "", currentPrice = null, isPriceLoading = false, isSearchActive = false) } }
    fun onQuantityChanged(quantity: String) {
        _uiState.update { it.copy(quantity = quantity.filter { it.isDigit() }) }
        saveState()
    }
    fun onAccountSelected(accountId: String) { _uiState.update { it.copy(selectedAccountId = accountId) } }
    fun onBuyClick() = viewModelScope.launch { postOrder(OrderDirection.BUY) }
    fun onSellClick() = viewModelScope.launch { postOrder(OrderDirection.SELL) }

    private suspend fun postOrder(direction: OrderDirection) {
        val state = _uiState.value
        val ticker = state.ticker.ifBlank { state.selectedInstrument?.ticker } ?: return
        val quantity = state.quantityAsLong ?: return

        val activeCard = state.lastSelectedInstruments.find { it.instrument.ticker == ticker }
        val brokerName = activeCard?.brokerName ?: "TInvest"
        val accountId = activeCard?.accountId ?: state.selectedAccountId ?: return

        val tscalpId = state.selectedInstrument?.tscalpInstrumentId ?: return

// Проверка доступности через брокер-специфичный метод
        // Получаем брокера и проверяем доступность
        val broker = (ServiceLocator.getBrokerManager().getBroker(brokerName) as? TInvestInvestService)
            ?: run {
                _uiState.update { it.copy(statusMessage = "❌ Брокер не найден", isError = true) }
                return
            }

        val checkResult = broker.checkTradeAvailability(
            accountId,
            tscalpId,
            uid = state.selectedInstrument?.tscalpInstrumentId,
            direction,
            quantity
        )

        when (checkResult) {
            is TradeCheckResult.Success -> { /* продолжаем */ }
            is TradeCheckResult.Error -> {
                _uiState.update { it.copy(statusMessage = "❌ ${checkResult.message}", isError = true) }
                return
            }
        }

        when (state.orderType) {
            OrderTypeSelection.Market, OrderTypeSelection.Limit -> {
                val regularOrderType = if (state.orderType == OrderTypeSelection.Market)
                    BrokerOrderType.MARKET
                else
                    BrokerOrderType.LIMIT

                val price = if (regularOrderType == BrokerOrderType.LIMIT)
                    state.limitPrice.toDoubleOrNull()
                else
                    null

                val request = BrokerOrderRequest(
                    brokerName = brokerName,
                    ticker = ticker,
                    instrumentUid = state.selectedInstrument?.tscalpInstrumentId,
                    quantity = quantity,
                    direction = direction,
                    accountId = accountId,
                    sandboxMode = ServiceLocator.isSandboxMode(),
                    type = regularOrderType,
                    price = price
                )

                _uiState.update { it.copy(isLoading = true, statusMessage = null) }
                try {
                    val result = repository.postOrder(request)

                    // Стрим исполнения
                    viewModelScope.launch {
                        try {
                            broker.subscribeOrderState(accountId)
                                .filter { state -> state.orderRequestId == result.orderRequestId }
                                .collect { state ->
                                    if (state.status in listOf("FILL", "PARTIALLYFILL")) {
                                        val timeStr = state.updateTime?.let { seconds ->
                                            java.time.Instant.ofEpochSecond(seconds)
                                                .atZone(java.time.ZoneId.systemDefault())
                                                .toLocalTime()
                                                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                                        } ?: ""
                                        val message = "✅ Заявка на покупка исполнена: " +
                                                "${state.executedQuantity}/${state.quantity} лотов по цене " +
                                                "${formatCurrency(state.executedPrice ?: 0.0)} в $timeStr"
                                        _uiState.update { it.copy(statusMessage = message, isError = false) }
                                        return@collect
                                    }
                                }
                        } catch (e: Exception) {
                            Log.w(TAG, "OrderState stream error (может быть недоступен в sandbox)", e)
                        }
                    }

                    val directionText = when (direction) {
                        OrderDirection.BUY -> "покупка"
                        OrderDirection.SELL -> "продажа"
                        else -> "операция"
                    }

                    var finalMessage = "✅ Заявка на $directionText выполнена!\n" +
                            "ID: ${result.orderId}\n" +
                            "Исполнено: ${result.executedLots}/${result.totalLots} лотов"

                    // Контрсделка
                    if (state.pairTradingEnabled && state.pairedInstrument != null) {
                        val multiplier = state.pairedMultiplier.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
                        val pairedQuantity = (quantity * multiplier).toLong()
                        if (pairedQuantity > 0) {
                            val pairedDirection = if (direction == OrderDirection.BUY) OrderDirection.SELL else OrderDirection.BUY

                            val pairedCard = state.lastSelectedInstruments.find {
                                it.instrument.ticker == state.pairedInstrument?.ticker
                            }
                            val pairedBrokerName = pairedCard?.brokerName ?: brokerName
                            val pairedAccountId = pairedCard?.accountId ?: accountId

                            // Определяем цену, которую будем использовать для маппинга
                            val primaryPrice = when (state.orderType) {
                                OrderTypeSelection.Limit,
                                OrderTypeSelection.StopLimit -> state.limitPrice.toDoubleOrNull()
                                OrderTypeSelection.StopLoss,
                                OrderTypeSelection.TakeProfit -> state.stopPrice.toDoubleOrNull()
                                else -> null
                            }

                            val pairedSpec = PairOrderMapper.map(state.orderType, direction, primaryPrice)

                            try {
                                if (pairedSpec.isStopOrder) {
                                    val stopPrice = pairedSpec.stopPrice
                                    val stopOrderType = pairedSpec.stopOrderType
                                    if (stopPrice != null && stopOrderType != null) {
                                        val stopRequest = StopOrderRequest(
                                            brokerName = pairedBrokerName,
                                            ticker = state.pairedInstrument.ticker,
                                            instrumentUid = state.pairedInstrument?.tscalpInstrumentId,
                                            quantity = pairedQuantity,
                                            direction = pairedDirection,
                                            accountId = pairedAccountId,
                                            sandboxMode = ServiceLocator.isSandboxMode(),
                                            stopPrice = stopPrice,
                                            price = pairedSpec.price,
                                            stopOrderType = stopOrderType,
                                            expirationType = state.expirationType
                                        )
                                        val stopId = repository.postStopOrder(stopRequest)
                                        finalMessage += "\n✅ Контрсделка: ${state.pairedInstrument.ticker} $pairedQuantity лотов, ID: ${stopId.take(8)}…"
                                    } else {
                                        finalMessage += "\n❌ Ошибка контрсделки: не указаны стоп-цена или тип стоп-заявки"
                                    }
                                } else {
                                    val pairedRequest = BrokerOrderRequest(
                                        brokerName = pairedBrokerName,
                                        ticker = state.pairedInstrument.ticker,
                                        instrumentUid = state.pairedInstrument?.tscalpInstrumentId,
                                        quantity = pairedQuantity,
                                        direction = pairedDirection,
                                        accountId = pairedAccountId,
                                        sandboxMode = ServiceLocator.isSandboxMode(),
                                        type = pairedSpec.brokerOrderType ?: BrokerOrderType.MARKET,
                                        price = pairedSpec.price
                                    )
                                    val pairedResult = repository.postOrder(pairedRequest)
                                    finalMessage += "\n✅ Контрсделка: ${state.pairedInstrument.ticker} $pairedQuantity лотов, ID: ${pairedResult.orderId}"
                                }
                            } catch (e: Exception) {
                                finalMessage += "\n❌ Ошибка контрсделки: ${e.message}"
                                Log.e(TAG, "Ошибка контрсделки", e)
                            }
                        }
                    }

                    //loadPortfolio(brokerName, accountId)
                    refreshLastSelectedInstruments()
                    var currentBalance = try {
                        repository.getBalance(accountId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Не удалось получить баланс", e)
                        null
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
                            freeBalance = currentBalance
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "❌ Ошибка: ${e.message}",
                            isError = true
                        )
                    }
                }
            }

            OrderTypeSelection.StopLoss, OrderTypeSelection.TakeProfit, OrderTypeSelection.StopLimit -> {
                val stopPrice = state.stopPrice.toDoubleOrNull() ?: return
                if (stopPrice <= 0) return

                val stopOrderType = state.orderType.stopOrderType ?: return

                val limitPrice = if (stopOrderType == StopOrderType.STOP_LIMIT)
                    state.limitPrice.toDoubleOrNull()
                else
                    null

                val stopRequest = StopOrderRequest(
                    brokerName = brokerName,
                    ticker = ticker,
                    instrumentUid = state.selectedInstrument?.tscalpInstrumentId,
                    quantity = quantity,
                    direction = direction,
                    accountId = accountId,
                    sandboxMode = ServiceLocator.isSandboxMode(),
                    stopPrice = stopPrice,
                    price = limitPrice,
                    stopOrderType = stopOrderType,
                    expirationType = state.expirationType
                )

                _uiState.update { it.copy(isLoading = true, statusMessage = null) }
                try {
                    val stopId = repository.postStopOrder(stopRequest)
                    //loadPortfolio(brokerName, accountId)
                    refreshLastSelectedInstruments()

                    val currentBalance = try {
                        repository.getBalance(accountId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Не удалось получить баланс", e)
                        null
                    }

                    var finalMessage = "✅ Стоп‑заявка выставлена, ID: ${stopId.take(8)}…"
                    if (currentBalance != null && currentBalance < 1000.0) {
                        finalMessage += "\n⚠️ Низкий свободный остаток: ${formatCurrency(currentBalance)}"
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = finalMessage,
                            isError = false,
                            quantity = "",
                            stopPrice = "",
                            limitPrice = "",
                            freeBalance = currentBalance
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "❌ Ошибка стоп‑заявки: ${e.message}",
                            isError = true
                        )
                    }
                }
            }
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
    fun retryLoadAccounts() { loadAccounts() }

//    fun startPriceUpdates() {
//        priceUpdateJob?.cancel()
//        priceUpdateJob = viewModelScope.launch {
//            while (isActive) {
//                delay(5_000) // каждые 5 секунд
//                updatePrices()
//            }
//        }
//    }
//
//    private suspend fun updatePrices() {
//        val state = _uiState.value
//        // Собираем тикеры только из visible карточек
//        val tickersToUpdate = state.lastSelectedInstruments.map { it.instrument.ticker }.toMutableSet()
//        state.selectedInstrument?.ticker?.let { tickersToUpdate.add(it) }
//        if (tickersToUpdate.isEmpty()) return
//
//        try {
//            val prices = repository.getLastPricesByTicker(tickersToUpdate.toList())
//
//            // Обновляем lastSelectedInstruments
//            val updatedLastSelected = state.lastSelectedInstruments.map { card ->
//                val newPrice = prices[card.instrument.ticker] ?: card.currentPrice
//                val changePercent = if (card.currentPrice != null && card.currentPrice != 0.0 && newPrice != null) {
//                    ((newPrice - card.currentPrice) / card.currentPrice) * 100.0
//                } else null
//                card.copy(currentPrice = newPrice, priceChangePercent = changePercent)
//            }
//
//            // Обновляем цену для выбранного инструмента
//            val selectedTicker = state.selectedInstrument?.ticker
//            val newSelectedPrice = selectedTicker?.let { prices[it] } ?: state.currentPrice
//            val selectedChange = if (state.currentPrice != null && state.currentPrice != 0.0 && newSelectedPrice != null) {
//                ((newSelectedPrice - state.currentPrice) / state.currentPrice) * 100.0
//            } else null
//
//            _uiState.update {
//                it.copy(
//                    lastSelectedInstruments = updatedLastSelected,
//                    currentPrice = newSelectedPrice,
//                    selectedPriceChangePercent = selectedChange
//                )
//            }
//        } catch (_: Exception) { }
//    }

//    fun stopPriceUpdates() {
//        priceUpdateJob?.cancel()
//    }

    /**
     * Открывает диалог настроек для указанного инструмента.
     */
/**
 * Открывает диалог настроек брокера/счёта для указанного тикера.
 */
fun openBrokerDialog(ticker: String) {
    viewModelScope.launch {
        val existingCard = _uiState.value.lastSelectedInstruments.find { it.instrument.ticker == ticker }
        val brokerName = existingCard?.brokerName ?: "TInvest"

        val accounts = try {
            repository.getAccounts(brokerName, ServiceLocator.isSandboxMode())
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки счетов для $brokerName", e)
            emptyList()
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
                selectedBroker = brokerName,
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
    fun onBrokerSelected(brokerName: String) {
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
     * Загружает счета для указанного брокера и сохраняет их во временный список (можно добавить поле в UIState).
     * Пока для простоты будем хранить список счетов в локальной переменной диалога.
     */
    private suspend fun loadDialogAccounts(brokerName: String) {
        try {
            val accounts = repository.getAccounts(brokerName, ServiceLocator.isSandboxMode())
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
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки счетов для $brokerName", e)
            _uiState.update { it.copy(dialogAccounts = emptyList(), selectedAccountIdDialog = null) }
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
                    pairCurrentPrice = null,         // сбрасываем цену парного инструмента
                    pairedInstrument = null,         // можно заодно сбросить и выбранный парный инструмент
                    pairedMultiplier = "10",         // и множитель вернуть в умолчание (опционально)
                    pairSearchQuery = "",            // очищаем поисковую строку
                    pairSearchResults = emptyList()  // и результаты поиска
                )
            }
        }
        saveState()
    }

    fun onPairSearchQueryChanged(query: String) {
        _uiState.update { it.copy(pairSearchQuery = query) }
        pairSearchJob?.cancel()
        if (query.length >= 2) {
            pairSearchJob = viewModelScope.launch {
                delay(500)
                _uiState.update { it.copy(isPairSearching = true) }
                try {
                    val cache = ServiceLocator.getSearchCache()
                    val brokerName = _uiState.value.pairSearchBroker
                    val results = cache.search(brokerName, query)
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
        startPriceUpdates()   // перезапускаем стрим для обновления цен обоих инструментов
        viewModelScope.launch {
            val prices = repository.getLastPricesByTscalpInstrumentId(listOf(instrument.tscalpInstrumentId))
            val price = prices[instrument.tscalpInstrumentId]
            if (price != null) {
                _uiState.update { it.copy(pairCurrentPrice = price) }
            }
            saveState()
        }
    }

    fun clearPairSearch() {
        _uiState.update { it.copy(pairSearchQuery = "", pairSearchResults = emptyList(), pairedInstrument = null) }
    }

    fun onPairedMultiplierChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(pairedMultiplier = filtered) }
        saveState()
    }

    fun onOrderTypeChanged(type: OrderTypeSelection) {
        _uiState.update { it.copy(orderType = type) }
        // Если нужно сбросить лимитную цену при переходе на рыночную, можно добавить
        if (type == OrderTypeSelection.Market) {
            _uiState.update { it.copy(limitPrice = "") }
        }
        saveState()
    }


    fun onLimitPriceChanged(price: String) {
        val filtered = price.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(limitPrice = filtered) }
        saveState()
    }

    fun onStopPriceChanged(price: String) {
        _uiState.update { it.copy(stopPrice = price.filter { it.isDigit() || it == '.' }) }
        saveState()
    }

//    fun onStopOrderTypeChanged(type: StopOrderType) {
//        _uiState.update { it.copy(stopOrderType = type) }
//    }

    fun startPriceUpdates() {
        stopPriceUpdates()
        val broker = ServiceLocator.getBrokerManager().getBroker("TInvest") as? TInvestInvestService ?: return
        val state = _uiState.value

        val idToTicker = mutableMapOf<String, String>()       // tscalpInstrumentId → ticker
        state.selectedInstrument?.let { idToTicker[it.tscalpInstrumentId] = it.ticker }
        state.pairedInstrument?.let { idToTicker[it.tscalpInstrumentId] = it.ticker }

        if (idToTicker.isEmpty()) return

        val ids = idToTicker.keys.toList()

        viewModelScope.launch {
            priceStreamJob = launch {
                broker.subscribeLastPrices(ids)   // ids — это tscalpInstrumentId, которые для Т‑Инвестиций равны figi
                    .catch { e -> Log.e(TAG, "Price stream error", e) }
                    .collect { (id, price) ->
                        val ticker = idToTicker[id] ?: return@collect
                        Log.d(TAG, "Цена для $ticker: $price")
                        _uiState.update { state ->
                            val oldPrice = state.currentPrice
                            val newPercent = if (oldPrice != null && oldPrice != 0.0) {
                                ((price - oldPrice) / oldPrice) * 100.0
                            } else null
                            when (ticker) {
                                state.selectedInstrument?.ticker -> state.copy(
                                    currentPrice = price,
                                    selectedPriceChangePercent = newPercent
                                )
                                state.pairedInstrument?.ticker -> state.copy(
                                    pairCurrentPrice = price
                                )
                                else -> state
                            }
                        }
                    }
            }
        }
    }

    fun stopPriceUpdates() {
        priceStreamJob?.cancel()
        priceStreamJob = null
        stopPositionUpdates()
    }

    private suspend fun updateTradingStatuses(ids: List<String>) {
        if (ids.isEmpty()) return
        val broker = ServiceLocator.getBrokerManager().getBroker("TInvest") ?: return
        try {
            val statuses = broker.getTradingStatuses(ids)
            _uiState.update { state ->
                state.copy(tradingStatuses = state.tradingStatuses + statuses)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обновления статусов доступности", e)
        }
    }

    fun startPositionUpdates() {
        stopPositionUpdates()
        positionStreamJob = viewModelScope.launch {
            SharedPositionStreamManager.flow.collect { item: PositionStreamItem ->
                updatePositionPnl(item)
            }
        }
    }

    fun stopPositionUpdates() {
        positionStreamJob?.cancel()
        positionStreamJob = null
    }

    private fun updatePositionPnl(item: PositionStreamItem) {
        val avgPrice = item.averagePositionPrice ?: return
        val yield = item.expectedYield ?: return
        val quantity = item.quantity
        if (quantity == 0L) return

        Log.d(TAG, "updatePositionPnl: uid=${item.instrumentUid}, avgPrice=$avgPrice, yield=$yield, quantity=$quantity")
        //val positions = _uiState.value.portfolioPositions
        //Log.d(TAG, "Current positions: ${positions.map { it.tscalpInstrumentId }}")

        val profitPercent = if (avgPrice != 0.0) (yield / (avgPrice * quantity)) * 100.0 else 0.0

        _uiState.update { state ->
            val positions = state.portfolioPositions.toMutableList()
            Log.d(TAG, "Current positions: ${positions.map { it.tscalpInstrumentId }}")
            val index = positions.indexOfFirst { it.tscalpInstrumentId == item.instrumentUid }
            if (index == -1) {
                // Добавляем новую позицию, если её нет
                positions.add(PortfolioPosition(
                    name = item.ticker,                // временно, позже можно загрузить полное имя из кэша
                    tscalpInstrumentId = item.instrumentUid,
                    ticker = item.ticker,
                    quantity = quantity,
                    currentPrice = item.currentPrice ?: 0.0,
                    averagePrice = avgPrice,
                    totalValue = (item.currentPrice ?: 0.0) * quantity,
                    profit = yield,
                    profitPercent = profitPercent,
                    pointValue = item.pointValue,
                    instrumentType = item.instrumentType
                ))
            } else {
                val old = positions[index]
                positions[index] = old.copy(
                    quantity = quantity,
                    currentPrice = item.currentPrice ?: old.currentPrice,
                    averagePrice = avgPrice,
                    totalValue = (item.currentPrice ?: old.currentPrice) * quantity,
                    profit = yield,
                    profitPercent = profitPercent,
                    pointValue = item.pointValue ?: old.pointValue,
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

    fun saveSearchBrokerSettings(broker: String) {
        selectedSearchBroker.value = broker
        showSearchBrokerDialog.value = false
    }

    fun savePairSearchBrokerSettings(broker: String) {
        selectedPairSearchBroker.value = broker
        showPairSearchBrokerDialog.value = false
    }

    fun dismissSearchBrokerDialog() { showSearchBrokerDialog.value = false }
    fun dismissPairSearchBrokerDialog() { showPairSearchBrokerDialog.value = false }

    fun refreshSearch() {
        val state = _uiState.value
        if (state.searchQuery.length >= 2) {
            ServiceLocator.getSearchCache().invalidate(state.searchBroker, state.searchQuery)
            onSearchQueryChanged(state.searchQuery)
        }
    }

    fun refreshPairSearch() {
        val state = _uiState.value
        if (state.pairSearchQuery.length >= 2) {
            ServiceLocator.getSearchCache().invalidate(state.pairSearchBroker, state.pairSearchQuery)
            onPairSearchQueryChanged(state.pairSearchQuery)
        }
    }
}

class OrdersViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrdersViewModel::class.java)) {
            val brokerManager = ServiceLocator.getBrokerManager()
            val repository = InvestRepository(brokerManager)
            @Suppress("UNCHECKED_CAST")
            return OrdersViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}