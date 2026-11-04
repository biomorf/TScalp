package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.animateScrollTo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import com.gitlab.biomorf.tscalp.domain.usecases.PairOrderMapper
import com.gitlab.biomorf.tscalp.presentation.components.AssetPositionCard
import com.gitlab.biomorf.tscalp.presentation.components.BrokerAccountDialog
import com.gitlab.biomorf.tscalp.presentation.components.ConfirmOrderDialog
import com.gitlab.biomorf.tscalp.presentation.components.OrdersListDialog
import com.gitlab.biomorf.tscalp.presentation.util.formatDomesticCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Архитектурный долг: функция требует декомпозиции.
// См. ROADMAP → «Рефакторинг OrdersScreen».
private fun calculatePairExec(uiState: OrdersUiState): Pair<Double, OrderTypeSelection?> {
    if (!uiState.pairTradingEnabled || uiState.pairedInstrument == null) {
        return 0.0 to null
    }
    val primaryPrice =
        when (uiState.orderType) {
            OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> uiState.limitPrice.toDoubleOrNull()
            OrderTypeSelection.StopLoss, OrderTypeSelection.TakeProfit -> uiState.stopPrice.toDoubleOrNull()
            else -> null
        }
    val direction = uiState.pendingDirection ?: OrderDirection.BUY
    val pairedSpec = PairOrderMapper.map(uiState.orderType, direction, primaryPrice)
    val pairExecPrice =
        when (uiState.orderType) {
            OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> uiState.executionPrice
            else -> uiState.pairCurrentPrice ?: 0.0
        }
    return pairExecPrice to pairedSpec.orderType
}

@Composable
private fun OrdersConfirmDialogSection(
    uiState: OrdersUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val (pairExecPrice, pairedOrderType) = calculatePairExec(uiState)
    ConfirmOrderDialog(
        show = uiState.showConfirmDialog,
        ticker = uiState.selectedInstrument?.ticker ?: "",
        quantity = uiState.quantityAsLong ?: 0L,
        pendingDirection = uiState.pendingDirection,
        orderType = uiState.orderType,
        instrumentType = uiState.selectedInstrument?.instrumentType ?: InstrumentType.OTHER,
        executionPrice = uiState.executionPrice,
        pairTradingEnabled = uiState.pairTradingEnabled,
        pairedInstrumentTicker = uiState.pairedInstrument?.ticker,
        pairedInstrumentType = uiState.pairedInstrument?.instrumentType,
        pairedMultiplier = uiState.pairedMultiplier,
        pairExecPrice = pairExecPrice,
        pairedOrderType = pairedOrderType,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun TradingBrokerDialog(
    availableBrokers: List<BrokerName>,
    uiState: OrdersUiState,
    onBrokerSelected: (BrokerName) -> Unit,
    onAccountSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    if (!uiState.showBrokerDialog) return
    BrokerAccountDialog(
        availableBrokers = availableBrokers,
        selectedBroker = uiState.selectedBroker,
        onBrokerSelected = onBrokerSelected,
        accounts = uiState.dialogAccounts,
        selectedAccountId = uiState.selectedAccountIdDialog,
        onAccountSelected = onAccountSelected,
        onDismiss = onDismiss,
        onSave = onSave,
    )
}

@Composable
private fun SearchBrokerDialog(
    availableBrokers: List<BrokerName>,
    uiState: OrdersUiState,
    onBrokerSelected: (BrokerName) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!uiState.showSearchBrokerDialog) return
    BrokerAccountDialog(
        availableBrokers = availableBrokers,
        selectedBroker = uiState.selectedSearchBroker,
        onBrokerSelected = onBrokerSelected,
        accounts = emptyList(),
        selectedAccountId = null,
        onAccountSelected = {},
        onDismiss = onDismiss,
        onSave = onDismiss,
        showAccountSelector = false,
    )
}

@Composable
private fun PairSearchBrokerDialog(
    availableBrokers: List<BrokerName>,
    uiState: OrdersUiState,
    onBrokerSelected: (BrokerName) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!uiState.showPairSearchBrokerDialog) return
    BrokerAccountDialog(
        availableBrokers = availableBrokers,
        selectedBroker = uiState.selectedPairSearchBroker,
        onBrokerSelected = onBrokerSelected,
        accounts = emptyList(),
        selectedAccountId = null,
        onAccountSelected = {},
        onDismiss = onDismiss,
        onSave = onDismiss,
        showAccountSelector = false,
    )
}

@Composable
private fun BoxScope.OrdersScreenShadows(
    showTopShadow: Boolean,
    showBottomShadow: Boolean,
) {
    if (showTopShadow) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.08f), Color.Transparent),
                            ),
                    ).align(Alignment.TopCenter),
        )
    }
    if (showBottomShadow) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.08f)),
                            ),
                    ).align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun OrdersBottomButtons(
    freeBalance: Double?,
    isFormValid: Boolean,
    isLoading: Boolean,
    onBuyClick: () -> Unit,
    onSellClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (freeBalance != null) {
            Text(
                text = "Свободно: ${formatDomesticCurrency(freeBalance)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = onBuyClick,
                modifier = Modifier.weight(1f),
                enabled = isFormValid && !isLoading,
            ) {
                Text("КУПИТЬ")
            }
            Button(
                onClick = onSellClick,
                modifier = Modifier.weight(1f),
                enabled = isFormValid && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("ПРОДАТЬ")
            }
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun OrdersPairSection(
    uiState: OrdersUiState,
    scrollState: ScrollState,
    scope: CoroutineScope,
    onPairSearchQueryChanged: (String) -> Unit,
    onPairedInstrumentSelected: (Instrument) -> Unit,
    onClearPairSearch: () -> Unit,
    onRefreshPairSearch: () -> Unit,
    onOpenPairSearchBrokerSettings: () -> Unit,
    onPairTradingEnabledChanged: (Boolean) -> Unit,
    onClearPairedInstrument: () -> Unit,
    onOpenBrokerDialog: (String) -> Unit,
    onPairedMultiplierChanged: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Парная торговля", style = MaterialTheme.typography.titleSmall)
        Switch(
            checked = uiState.pairTradingEnabled,
            onCheckedChange = onPairTradingEnabledChanged,
        )
    }
    if (!uiState.pairTradingEnabled) return
    if (uiState.pairedInstrument == null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InstrumentSearchField(
                    query = uiState.pairSearchQuery,
                    onQueryChanged = onPairSearchQueryChanged,
                    isSearching = uiState.isPairSearching,
                    searchResults = uiState.pairSearchResults,
                    onInstrumentSelected = onPairedInstrumentSelected,
                    onClear = onClearPairSearch,
                    recentInstruments = uiState.lastSelectedInstruments.map { it.instrument },
                    tradingStatuses = uiState.tradingStatuses,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.pairSearchQuery.length >= 2 && !uiState.isPairSearching) {
                    IconButton(onClick = onRefreshPairSearch) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить поиск")
                    }
                }
                IconButton(onClick = onOpenPairSearchBrokerSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки поиска")
                }
            }
        }
    } else {
        uiState.pairedInstrument?.let { instrument: Instrument ->
            val portfolioPos =
                uiState.portfolioPositions.find { it.instrument.ticker == instrument.ticker }
            val pairPrice =
                uiState.pairCurrentPrice
                    ?: portfolioPos?.currentPrice
                    ?: 0.0
            val position =
                portfolioPos
                    ?: emptyPosition(
                        instrument = instrument,
                        currentPrice = pairPrice,
                        accountId = uiState.selectedAccountId ?: "",
                        brokerName = BrokerName.TINVEST,
                    )
            AssetPositionCard(
                position = position,
                displayCurrentPrice = pairPrice,
                priceChangePercent = uiState.selectedPriceChangePercent,
                onDelete = onClearPairedInstrument,
                onSettings = { onOpenBrokerDialog(instrument.ticker) },
                onClick = { },
                isSelected = false,
                resetSwipe = uiState.swipeResetTrigger,
                tradingAvailability = uiState.tradingStatuses[instrument.tscalpInstrumentId],
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.pairedMultiplier,
                    onValueChange = onPairedMultiplierChanged,
                    label = { Text("Множитель") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    scope.launch {
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                            },
                )
                val multiplierOverlay = uiState.multiplierOverlay
                if (multiplierOverlay != null) {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .padding(end = 12.dp, top = 8.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            text = multiplierOverlay,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier =
                                Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        RoundedCornerShape(4.dp),
                                    ).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrdersPriceFields(
    orderType: OrderTypeSelection,
    limitPrice: String,
    stopPrice: String,
    onLimitPriceChanged: (String) -> Unit,
    onStopPriceChanged: (String) -> Unit,
) {
    AnimatedContent(
        targetState = orderType,
        transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith
                fadeOut(animationSpec = tween(200))
        },
        label = "price_fields",
    ) { type: OrderTypeSelection ->
        when (type) {
            OrderTypeSelection.Limit -> {
                OutlinedTextField(
                    value = limitPrice,
                    onValueChange = onLimitPriceChanged,
                    label = { Text("Цена за лот") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OrderTypeSelection.StopLoss,
            OrderTypeSelection.TakeProfit,
            -> {
                Column {
                    OutlinedTextField(
                        value = stopPrice,
                        onValueChange = onStopPriceChanged,
                        label = { Text("Триггер стоп-цена") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            OrderTypeSelection.StopLimit -> {
                Column {
                    OutlinedTextField(
                        value = stopPrice,
                        onValueChange = onStopPriceChanged,
                        label = { Text("Триггер стоп-цена") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = limitPrice,
                        onValueChange = onLimitPriceChanged,
                        label = { Text("Лимитная цена") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            else -> { /* Market – ничего не показываем */ }
        }
    }
}

@Composable
private fun OrdersTypeChips(
    orderType: OrderTypeSelection,
    onOrderTypeChanged: (OrderTypeSelection) -> Unit,
) {
    Column(
        modifier = Modifier.wrapContentWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = orderType == OrderTypeSelection.Market,
                onClick = { onOrderTypeChanged(OrderTypeSelection.Market) },
                label = { Text("Рын.") },
            )
            FilterChip(
                selected = orderType == OrderTypeSelection.Limit,
                onClick = { onOrderTypeChanged(OrderTypeSelection.Limit) },
                label = { Text("Лим.") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = orderType == OrderTypeSelection.StopLoss,
                onClick = { onOrderTypeChanged(OrderTypeSelection.StopLoss) },
                label = { Text("Stop‑Loss") },
            )
            FilterChip(
                selected = orderType == OrderTypeSelection.TakeProfit,
                onClick = { onOrderTypeChanged(OrderTypeSelection.TakeProfit) },
                label = { Text("Take‑Profit") },
            )
            FilterChip(
                selected = orderType == OrderTypeSelection.StopLimit,
                onClick = { onOrderTypeChanged(OrderTypeSelection.StopLimit) },
                label = { Text("Stop‑Limit") },
            )
        }
    }
}

@Composable
private fun OrdersQuantityRow(
    quantity: String,
    costOverlay: String?,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onQuantityChanged: (String) -> Unit,
) {
    val quantityAsLong = quantity.toLongOrNull() ?: 0L
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(
            onClick = {
                if (quantityAsLong > 0) onQuantityChanged((quantityAsLong - 1).toString())
            },
            enabled = canDecrement,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Default.Remove, "Уменьшить", modifier = Modifier.size(24.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = quantity,
                onValueChange = onQuantityChanged,
                label = { Text("Кол-во лотов") },
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (costOverlay != null) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .padding(end = 12.dp, top = 8.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = costOverlay,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    RoundedCornerShape(4.dp),
                                ).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        IconButton(
            onClick = {
                onQuantityChanged((quantityAsLong + 1).toString())
            },
            enabled = canIncrement,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Default.Add, "Увеличить", modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun OrdersInstrumentSection(
    uiState: OrdersUiState,
    onSearchQueryChanged: (String) -> Unit,
    onInstrumentSelected: (Instrument) -> Unit,
    onClearSearch: () -> Unit,
    onRefreshSearch: () -> Unit,
    onOpenSearchSettings: () -> Unit,
    onClearSelected: () -> Unit,
    onOpenBrokerDialog: (String) -> Unit,
) {
    if (uiState.selectedInstrument == null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InstrumentSearchField(
                    query = uiState.searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                    isSearching = uiState.isSearching,
                    searchResults = uiState.searchResults,
                    onInstrumentSelected = onInstrumentSelected,
                    onClear = onClearSearch,
                    recentInstruments = uiState.lastSelectedInstruments.map { it.instrument },
                    tradingStatuses = uiState.tradingStatuses,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.searchQuery.length >= 2 && !uiState.isSearching) {
                    IconButton(onClick = onRefreshSearch) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить поиск")
                    }
                }
                IconButton(onClick = onOpenSearchSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки поиска")
                }
            }
        }
    } else {
        uiState.selectedInstrument?.let { instrument: Instrument ->
            val portfolioPos =
                uiState.portfolioPositions.find { it.instrument.ticker == instrument.ticker }
            val currentPrice = uiState.currentPrice ?: portfolioPos?.currentPrice ?: 0.0
            val position =
                portfolioPos
                    ?: emptyPosition(
                        instrument = instrument,
                        currentPrice = currentPrice,
                        accountId = uiState.selectedAccountId ?: "",
                        brokerName = BrokerName.TINVEST,
                    )
            AssetPositionCard(
                position = position,
                displayCurrentPrice = currentPrice,
                priceChangePercent = uiState.selectedPriceChangePercent,
                onDelete = onClearSelected,
                onSettings = { onOpenBrokerDialog(instrument.ticker) },
                onClick = { },
                isSelected = false,
                resetSwipe = uiState.swipeResetTrigger,
                tradingAvailability = uiState.tradingStatuses[instrument.tscalpInstrumentId],
            )
        }
    }
}

@Composable
private fun OrdersScreenHeader(onShowOrdersList: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Выставление заявки",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onShowOrdersList) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Список заявок")
        }
    }
}

// Архитектурный долг: 221 строка > 60. Extract сделан (10 подкомпонентов),
// CyclomaticComplexMethod снят (complexity < 14). Дальнейшая декомпозиция
// (вынести sidebar-навигацию, статусы) — Н94 / #### Compose-экраны.
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen() {
    val viewModel: OrdersViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }

    var showOrdersListDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null && !uiState.isError) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearStatus()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkApiInitialization()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ========== ЗАГОЛОВОК ==========
            OrdersScreenHeader(onShowOrdersList = { showOrdersListDialog = true })

            if (!uiState.isApiInitialized) {
                ApiNotInitializedCard()
                return@Box
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ========== СКРОЛЛИРУЕМЫЙ БЛОК с индикаторами прокрутки ==========
            val scrollState = rememberScrollState()
            val scope = rememberCoroutineScope()
            val showTopShadow by remember { derivedStateOf { scrollState.value > 0 } }
            val showBottomShadow by remember {
                derivedStateOf { scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue }
            }

            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // ========== ОСНОВНОЙ ПОИСК / КАРТОЧКА ==========
                    OrdersInstrumentSection(
                        uiState = uiState,
                        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        onInstrumentSelected = {
                            viewModel.onInstrumentSelected(it)
                            focusManager.clearFocus()
                        },
                        onClearSearch = { viewModel.clearSearch() },
                        onRefreshSearch = { viewModel.refreshSearch() },
                        onOpenSearchSettings = { viewModel.openSearchBrokerSettings() },
                        onClearSelected = { viewModel.clearSelectedInstrument() },
                        onOpenBrokerDialog = { viewModel.openBrokerDialog(it) },
                    )

                    // Spacer(modifier = Modifier.height(1.dp))

                    // ========== Поле количества ==========
                    // Определяем цену исполнения в зависимости от типа заявки
                    OrdersConfirmDialogSection(
                        uiState = uiState,
                        onDismiss = { viewModel.onDismissConfirmDialog() },
                        onConfirm = { viewModel.onConfirmOrder() },
                    )

                    OrdersQuantityRow(
                        quantity = uiState.quantity,
                        costOverlay = uiState.costOverlay,
                        canDecrement = (uiState.quantityAsLong ?: 0L) > 0 && uiState.selectedInstrument != null,
                        canIncrement = uiState.selectedInstrument != null,
                        onQuantityChanged = { viewModel.onQuantityChanged(it) },
                    )

                    // ========== Выбор типа заявки ==========
                    OrdersTypeChips(
                        orderType = uiState.orderType,
                        onOrderTypeChanged = { viewModel.onOrderTypeChanged(it) },
                    )

                    // ========== Ценовые поля (статическая высота) ==========
                    OrdersPriceFields(
                        orderType = uiState.orderType,
                        limitPrice = uiState.limitPrice,
                        stopPrice = uiState.stopPrice,
                        onLimitPriceChanged = { viewModel.onLimitPriceChanged(it) },
                        onStopPriceChanged = { viewModel.onStopPriceChanged(it) },
                    )

                    // ========== СЕКЦИЯ ПАРНОЙ ТОРГОВЛИ (в скролле) ==========
                    OrdersPairSection(
                        uiState = uiState,
                        scrollState = scrollState,
                        scope = scope,
                        onPairSearchQueryChanged = { viewModel.onPairSearchQueryChanged(it) },
                        onPairedInstrumentSelected = {
                            viewModel.onPairedInstrumentSelected(it)
                            focusManager.clearFocus()
                        },
                        onClearPairSearch = { viewModel.clearPairSearch() },
                        onRefreshPairSearch = { viewModel.refreshPairSearch() },
                        onOpenPairSearchBrokerSettings = { viewModel.openPairSearchBrokerSettings() },
                        onPairTradingEnabledChanged = { viewModel.setPairTradingEnabled(it) },
                        onClearPairedInstrument = { viewModel.clearPairSearch() },
                        onOpenBrokerDialog = { viewModel.openBrokerDialog(it) },
                        onPairedMultiplierChanged = { viewModel.onPairedMultiplierChanged(it) },
                    )
                } // конец скроллируемой колонки

                // === ИНДИКАТОРЫ ПРОКРУТКИ ===
                OrdersScreenShadows(
                    showTopShadow = showTopShadow,
                    showBottomShadow = showBottomShadow,
                )
            }

            // ========== ФИКСИРОВАННАЯ НИЖНЯЯ СЕКЦИЯ (только кнопки) ==========
            OrdersBottomButtons(
                freeBalance = uiState.freeBalance,
                isFormValid = uiState.isFormValid,
                isLoading = uiState.isLoading,
                onBuyClick = { viewModel.onBuyClick() },
                onSellClick = { viewModel.onSellClick() },
            )
        }

        // Снекбар поверх всего
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp),
        ) { data ->
            Snackbar(
                containerColor =
                    if (uiState.isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                contentColor =
                    if (uiState.isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                action = {
                    val label = data.visuals.actionLabel
                    if (label != null) {
                        TextButton(onClick = { data.dismiss() }) {
                            Text(label)
                        }
                    }
                },
            ) {
                Text(data.visuals.message)
            }
        }
    }

    // ==================== ДИАЛОГИ ====================
    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { message ->
            val visuals =
                object : SnackbarVisuals {
                    override val message: String = message
                    override val actionLabel: String? = if (uiState.isError) "OK" else null
                    override val withDismissAction: Boolean = false
                    override val duration: SnackbarDuration =
                        if (uiState.isError) SnackbarDuration.Indefinite else SnackbarDuration.Short
                }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(visuals)
            viewModel.clearStatus()
        }
    }

    TradingBrokerDialog(
        availableBrokers = viewModel.getAvailableBrokerNames(),
        uiState = uiState,
        onBrokerSelected = { viewModel.onBrokerSelected(it) },
        onAccountSelected = { viewModel.onAccountSelectedDialog(it) },
        onDismiss = { viewModel.closeBrokerDialog() },
        onSave = { viewModel.saveBrokerSettings() },
    )

    if (showOrdersListDialog) {
        OrdersListDialog(
            onDismiss = { showOrdersListDialog = false },
        )
    }
    // Диалог выбора брокера для основного поиска
    SearchBrokerDialog(
        availableBrokers = viewModel.getAvailableBrokerNames(),
        uiState = uiState,
        onBrokerSelected = { viewModel.saveSearchBrokerSettings(it) },
        onDismiss = { viewModel.dismissSearchBrokerDialog() },
    )

// Диалог выбора брокера для парного поиска
    PairSearchBrokerDialog(
        availableBrokers = viewModel.getAvailableBrokerNames(),
        uiState = uiState,
        onBrokerSelected = { viewModel.savePairSearchBrokerSettings(it) },
        onDismiss = { viewModel.dismissPairSearchBrokerDialog() },
    )
}

// ==================== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ====================

@Composable
fun ApiNotInitializedCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠️ API не подключен", style = MaterialTheme.typography.titleMedium)
            Text("Перейдите в Настройки и введите токен доступа", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

// Архитектурный долг: функция требует декомпозиции.
// См. ROADMAP → «Рефакторинг OrdersScreen».
@Composable
private fun SearchResultItem(
    instrument: Instrument,
    onClick: () -> Unit,
) {
    val typeColor = getInstrumentTypeColor(instrument.instrumentType)
    ListItem(
        headlineContent = { Text("${instrument.ticker} - ${instrument.name}") },
        supportingContent = {
            Text(
                text = instrument.tscalpInstrumentId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .background(typeColor),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<Instrument>,
    onInstrumentSelected: (Instrument) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    recentInstruments: List<Instrument> = emptyList(),
    tradingStatuses: Map<String, TradingAvailability> = emptyMap(),
) {
    var inputText by remember { mutableStateOf(query) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.isEmpty() && inputText.isNotEmpty()) {
            inputText = ""
            expanded = false
        }
    }

    Column(modifier = modifier) {
        DockedSearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = inputText,
                    onQueryChange = { newText: String ->
                        inputText = newText
                        onQueryChanged(newText)
                        expanded = newText.isNotEmpty() || recentInstruments.isNotEmpty()
                    },
                    onSearch = { expanded = false },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    placeholder = {
                        Text(
                            "Введите тикер или название",
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                ),
                        )
                    },
                    leadingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(onClick = {
                                inputText = ""
                                onClear()
                                expanded = false
                            }) {
                                Icon(Icons.Default.Clear, "Очистить")
                            }
                        } else {
                            Icon(Icons.Default.Search, "Поиск")
                        }
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        }
                    },
                )
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (expanded && (searchResults.isNotEmpty() || (inputText.isEmpty() && recentInstruments.isNotEmpty()))) {
                Column(
                    modifier =
                        Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    if (inputText.isEmpty() && recentInstruments.isNotEmpty()) {
                        recentInstruments.forEach { instrument ->
                            val status = tradingStatuses[instrument.tscalpInstrumentId]
                            if (status == TradingAvailability.UNAVAILABLE) return@forEach
                            SearchResultItem(
                                instrument = instrument,
                                onClick = {
                                    onInstrumentSelected(instrument)
                                    inputText = "${instrument.ticker} - ${instrument.name}"
                                    expanded = false
                                },
                            )
                        }
                    } else {
                        searchResults.forEach { instrument ->
                            val status = tradingStatuses[instrument.tscalpInstrumentId]
                            if (status == TradingAvailability.UNAVAILABLE) return@forEach
                            SearchResultItem(
                                instrument = instrument,
                                onClick = {
                                    onInstrumentSelected(instrument)
                                    inputText = "${instrument.ticker} - ${instrument.name}"
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getInstrumentTypeColor(instrumentType: InstrumentType): Color =
    when (instrumentType) {
        InstrumentType.SHARE -> Color(0xFF1565C0)
        InstrumentType.BOND -> Color(0xFFE65100)
        InstrumentType.ETF -> Color(0xFF2E7D32)
        InstrumentType.CURRENCY -> Color(0xFF6A1B9A)
        else -> Color(0xFF757575)
    }
