package com.example.tscalp.presentation.screens.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel

import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.domain.models.*
import com.example.tscalp.ui.components.AssetPositionCard
import com.example.tscalp.ui.components.BrokerAccountDialog
import com.example.tscalp.ui.components.StopOrdersDialog
import com.example.tscalp.util.formatCurrency
import com.example.tscalp.util.formatPrice
import com.example.tscalp.domain.models.TradingAvailability

/**
 * Вспомогательная функция для создания поля ввода в стиле Material 3.
 * Высота 56dp, текст вертикально центрирован, рамка как у остальных полей,
 * цвет текста адаптирован к теме.
 */
@Composable
private fun M3TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    overlayText: String? = null,
    overlayAlpha: Float = 1f,
    overlayColor: Color = MaterialTheme.colorScheme.onSurface   // по умолчанию как основной текст
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                // Плейсхолдер
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = textColor.copy(alpha = 0.4f)
                        )
                    )
                }
                // Поле ввода
                innerTextField()

                // Оверлей справа (если задан)
                if (overlayText != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = overlayText,
                            style = MaterialTheme.typography.bodyMedium,
                            //color = textColor,
                            color = overlayColor
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = viewModel(factory = OrdersViewModelFactory())
) {
    val uiState by viewModel.uiState.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingDirection by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }

    var showStopOrdersDialog by remember { mutableStateOf(false) }
    val stopOrdersViewModel = remember { StopOrdersViewModel() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.startPriceUpdates()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.stopPriceUpdates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ========== ЗАГОЛОВОК ==========
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Выставление заявки",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showStopOrdersDialog = true }) {
                    Icon(Icons.Default.List, contentDescription = "Список заявок")
                }
            }

            if (!uiState.isApiInitialized) {
                ApiNotInitializedCard()
                return@Box
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ========== ОСНОВНОЙ ПОИСК / КАРТОЧКА ==========
            if (uiState.selectedInstrument == null) {
                InstrumentSearchField(
                    query = uiState.searchQuery,
                    onQueryChanged = { query: String -> viewModel.onSearchQueryChanged(query) },
                    isSearching = uiState.isSearching,
                    searchResults = uiState.searchResults,
                    onInstrumentSelected = { instrument: InstrumentUi ->
                        viewModel.onInstrumentSelected(instrument)
                        focusManager.clearFocus()
                    },
                    onClear = { viewModel.clearSearch() },
                    recentInstruments = uiState.lastSelectedInstruments.map { it.instrument },
                    tradingStatuses = uiState.tradingStatuses,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                uiState.selectedInstrument?.let { instrument: InstrumentUi ->
                    val portfolioPos = uiState.portfolioPositions.find { it.ticker == instrument.ticker }
                    val position = PortfolioPosition(
                        name = instrument.name,
                        ticker = instrument.ticker,
                        tscalpInstrumentId = instrument.tscalpInstrumentId,
                        quantity = portfolioPos?.quantity ?: 0L,
                        currentPrice = uiState.currentPrice ?: portfolioPos?.currentPrice ?: 0.0,
                        totalValue = (uiState.currentPrice ?: 0.0) * (portfolioPos?.quantity ?: 0L),
                        profit = portfolioPos?.profit ?: 0.0,
                        profitPercent = portfolioPos?.profitPercent ?: 0.0,
                        instrumentType = instrument.instrumentType,
                        priceChangePercent = null
                    )
                    AssetPositionCard(
                        position = position,
                        instrumentType = instrument.instrumentType,
                        priceChangePercent = uiState.selectedPriceChangePercent,
                        onDelete = { viewModel.clearSelectedInstrument() },
                        onSettings = { viewModel.openBrokerDialog(instrument.ticker) },
                        onClick = { },
                        isSelected = false,
                        resetSwipe = uiState.swipeResetTrigger,
                        tscalpInstrumentId = instrument.tscalpInstrumentId,
                        tradingAvailability = uiState.tradingStatuses[instrument.tscalpInstrumentId]
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ========== СКРОЛЛИРУЕМЫЙ БЛОК с индикаторами прокрутки ==========
            val scrollState = rememberScrollState()
            val showTopShadow by remember { derivedStateOf { scrollState.value > 0 } }
            val showBottomShadow by remember {
                derivedStateOf { scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue }
            }

            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ========== Поле количества ==========
                    // Ориентировочная стоимость
                    val currentQty = uiState.quantityAsLong ?: 0L
                    val currentPrice = uiState.currentPrice ?: 0.0
                    val costOverlay = if (currentQty > 0 && currentPrice > 0) {
                        formatCurrency(currentPrice * currentQty)
                    } else null

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val currentQty = uiState.quantityAsLong ?: 0L
                                if (currentQty > 0) viewModel.onQuantityChanged((currentQty - 1).toString())
                            },
                            enabled = (uiState.quantityAsLong ?: 0L) > 0 && uiState.selectedInstrument != null,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Remove, "Уменьшить", modifier = Modifier.size(24.dp))
                        }


                        M3TextField(
                            value = uiState.quantity,
                            onValueChange = { viewModel.onQuantityChanged(it) },
                            placeholder = "Кол-во лотов",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                            overlayText = costOverlay,
                            overlayAlpha = 0.1f,
                            overlayColor = MaterialTheme.colorScheme.outline
                        )

                        IconButton(
                            onClick = {
                                val currentQty = uiState.quantityAsLong ?: 0L
                                viewModel.onQuantityChanged((currentQty + 1).toString())
                            },
                            enabled = uiState.selectedInstrument != null,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Add, "Увеличить", modifier = Modifier.size(24.dp))
                        }
                    }


                    // ========== Выбор типа заявки ==========
                    Column(
                        modifier = Modifier.wrapContentWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.orderType == OrderTypeSelection.Market,
                                onClick = { viewModel.onOrderTypeChanged(OrderTypeSelection.Market) },
                                label = { Text("Рын.") }
                            )
                            FilterChip(
                                selected = uiState.orderType == OrderTypeSelection.Limit,
                                onClick = { viewModel.onOrderTypeChanged(OrderTypeSelection.Limit) },
                                label = { Text("Лим.") }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.orderType == OrderTypeSelection.StopLoss,
                                onClick = { viewModel.onOrderTypeChanged(OrderTypeSelection.StopLoss) },
                                label = { Text("Stop‑Loss") }
                            )
                            FilterChip(
                                selected = uiState.orderType == OrderTypeSelection.TakeProfit,
                                onClick = { viewModel.onOrderTypeChanged(OrderTypeSelection.TakeProfit) },
                                label = { Text("Take‑Profit") }
                            )
                            FilterChip(
                                selected = uiState.orderType == OrderTypeSelection.StopLimit,
                                onClick = { viewModel.onOrderTypeChanged(OrderTypeSelection.StopLimit) },
                                label = { Text("Stop‑Limit") }
                            )
                        }
                    }

                    // ========== Ценовые поля (статическая высота) ==========
//                    Box(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .height(130.dp)
//                    ) {
                        Column(
                            verticalArrangement = Arrangement.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AnimatedVisibility(
                                visible = uiState.orderType is OrderTypeSelection.Limit,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                M3TextField(
                                    value = uiState.limitPrice,
                                    onValueChange = { viewModel.onLimitPriceChanged(it) },
                                    placeholder = "Цена за лот",
                                    keyboardType = KeyboardType.Decimal
                                )
                            }

                            AnimatedVisibility(
                                visible = uiState.orderType is OrderTypeSelection.StopLoss ||
                                        uiState.orderType is OrderTypeSelection.TakeProfit ||
                                        uiState.orderType is OrderTypeSelection.StopLimit,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Column {
                                    M3TextField(
                                        value = uiState.stopPrice,
                                        onValueChange = { viewModel.onStopPriceChanged(it) },
                                        placeholder = "Триггер стоп-цена",
                                        keyboardType = KeyboardType.Decimal
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AnimatedVisibility(
                                        visible = uiState.orderType is OrderTypeSelection.StopLimit,
                                        enter = fadeIn(),
                                        exit = fadeOut()
                                    ) {
                                        M3TextField(
                                            value = uiState.limitPrice,
                                            onValueChange = { viewModel.onLimitPriceChanged(it) },
                                            placeholder = "Лимитная цена",
                                            keyboardType = KeyboardType.Decimal
                                        )
                                    }
                                }
                            }
                        }
                    //}

                    // ========== СЕКЦИЯ ПАРНОЙ ТОРГОВЛИ (в скролле) ==========
//                    if (uiState.orderType is OrderTypeSelection.Market ||
//                        uiState.orderType is OrderTypeSelection.Limit) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Парная торговля", style = MaterialTheme.typography.titleSmall)
                            Switch(
                                checked = uiState.pairTradingEnabled,
                                onCheckedChange = { viewModel.setPairTradingEnabled(it) }
                            )
                        }
                    //}

                    if (uiState.pairTradingEnabled) {
                        if (uiState.pairedInstrument == null) {
                            // Парный поиск с историей (общая с основным поиском)
                            InstrumentSearchField(
                                query = uiState.pairSearchQuery,
                                onQueryChanged = { query: String -> viewModel.onPairSearchQueryChanged(query) },
                                isSearching = uiState.isPairSearching,
                                searchResults = uiState.pairSearchResults,
                                onInstrumentSelected = { instrument: InstrumentUi ->
                                    viewModel.onPairedInstrumentSelected(instrument)
                                    focusManager.clearFocus()
                                },
                                onClear = { viewModel.clearPairSearch() },
                                recentInstruments = uiState.lastSelectedInstruments.map { it.instrument },
                                tradingStatuses = uiState.tradingStatuses,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Карточка парного инструмента и множитель
                            uiState.pairedInstrument?.let { instrument: InstrumentUi ->
                                val portfolioPos = uiState.portfolioPositions.find { it.ticker == instrument.ticker }
                                val pairPrice = uiState.pairCurrentPrice ?: 0.0
                                val position = PortfolioPosition(
                                    name = instrument.name,
                                    ticker = instrument.ticker,
                                    tscalpInstrumentId = instrument.tscalpInstrumentId,
                                    quantity = portfolioPos?.quantity ?: 0L,
                                    currentPrice = pairPrice,
                                    totalValue = pairPrice * (portfolioPos?.quantity ?: 0L),
                                    profit = portfolioPos?.profit ?: 0.0,
                                    profitPercent = portfolioPos?.profitPercent ?: 0.0,
                                    instrumentType = instrument.instrumentType,
                                    priceChangePercent = null
                                )

                                AssetPositionCard(
                                    position = position,
                                    instrumentType = instrument.instrumentType,
                                    priceChangePercent = uiState.selectedPriceChangePercent,
                                    onDelete = { viewModel.clearPairSearch() },
                                    onSettings = { viewModel.openBrokerDialog(instrument.ticker) },
                                    onClick = { },
                                    isSelected = false,
                                    resetSwipe = uiState.swipeResetTrigger,
                                    tscalpInstrumentId = instrument.tscalpInstrumentId,
                                    tradingAvailability = uiState.tradingStatuses[instrument.tscalpInstrumentId]
                                )

                                // Оверлей стоимости для множителя
                                val currentQty = uiState.quantityAsLong ?: 0L
                                val totalQty = currentQty * (uiState.pairedMultiplier.toDoubleOrNull() ?: 1.0)
                                val multiplierOverlay = if (totalQty > 0 && pairPrice > 0) {
                                    formatCurrency(pairPrice * totalQty)
                                } else null

                                M3TextField(
                                    value = uiState.pairedMultiplier,
                                    onValueChange = { viewModel.onPairedMultiplierChanged(it) },
                                    placeholder = "Множитель",
                                    keyboardType = KeyboardType.Decimal,
                                    overlayText = multiplierOverlay,
                                    overlayColor = MaterialTheme.colorScheme.outline   // полусерый, как в поле количества
                                )
                            }
                        }
                    }
                } // конец скроллируемой колонки

                // === ИНДИКАТОРЫ ПРОКРУТКИ ===
                if (showTopShadow) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.08f), Color.Transparent)
                                )
                            )
                            .align(Alignment.TopCenter)
                    )
                }
                if (showBottomShadow) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.08f))
                                )
                            )
                            .align(Alignment.BottomCenter)
                    )
                }
            }

            // ========== ФИКСИРОВАННАЯ НИЖНЯЯ СЕКЦИЯ (только кнопки) ==========
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Кнопки КУПИТЬ / ПРОДАТЬ
                val balance = uiState.freeBalance
                if (balance != null) {
                    Text(
                        text = "Свободно: ${formatCurrency(balance)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            if (ServiceLocator.isConfirmOrdersEnabled()) {
                                pendingDirection = "Покупка"
                                showConfirmDialog = true
                            } else {
                                viewModel.onBuyClick()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.isFormValid && !uiState.isLoading
                    ) {
                        Text("КУПИТЬ")
                    }
                    Button(
                        onClick = {
                            if (ServiceLocator.isConfirmOrdersEnabled()) {
                                pendingDirection = "Продажа"
                                showConfirmDialog = true
                            } else {
                                viewModel.onSellClick()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.isFormValid && !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("ПРОДАТЬ")
                    }
                }
            }
        }

        // Снекбар поверх всего
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp)
        ) { data ->
            Snackbar(
                containerColor = if (uiState.isError)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = if (uiState.isError)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    MaterialTheme.colorScheme.onTertiaryContainer,
                action = {
                    val label = data.visuals.actionLabel
                    if (label != null) {
                        TextButton(onClick = { data.dismiss() }) {
                            Text(label)
                        }
                    }
                }
            ) {
                Text(data.visuals.message)
            }
        }
    }

    // ==================== ДИАЛОГИ ====================
    if (showConfirmDialog) {
        val ticker = uiState.selectedInstrument?.ticker ?: ""
        val quantity = uiState.quantityAsLong ?: 0L

        val isMarket = uiState.orderType is OrderTypeSelection.Market
        val isLimit = uiState.orderType is OrderTypeSelection.Limit
        val isStopLoss = uiState.orderType is OrderTypeSelection.StopLoss
        val isTakeProfit = uiState.orderType is OrderTypeSelection.TakeProfit
        val isStopLimit = uiState.orderType is OrderTypeSelection.StopLimit

        // Цена исполнения и признак приблизительности
        val executionPrice: Double
        val executionLabel: String
        val approximate: Boolean

        when {
            isLimit || isStopLimit -> {
                executionPrice = uiState.limitPrice.toDoubleOrNull() ?: 0.0
                executionLabel = "Лимитная цена"
                approximate = false
            }
            isStopLoss || isTakeProfit -> {
                // для стоп‑лосса/тейк‑профита исполнение по триггер‑цене
                executionPrice = uiState.stopPrice.toDoubleOrNull() ?: 0.0
                executionLabel = "Триггер‑цена"
                approximate = true
            }
            else -> { // Market
                executionPrice = uiState.currentPrice ?: 0.0
                executionLabel = "Текущая цена (исполнение по рынку)"
                approximate = true
            }
        }

        // Триггер‑цена для стоп‑лимита (уже учтена выше как executionPrice для StopLoss/TakeProfit)
        val triggerPrice: Double? = if (isStopLoss || isTakeProfit || isStopLimit) {
            uiState.stopPrice.toDoubleOrNull()
        } else null

        val priceSymbol = if (approximate) "≈" else ""

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Подтверждение заявки") },
            text = {
                Column {
                    Text("Вы собираетесь ${pendingDirection.lowercase()} $quantity лотов $ticker")

                    // Триггер‑цена (для всех стоп‑заявок) выводится первой
                    if (triggerPrice != null && triggerPrice > 0) {
                        // Для стоп‑лимита триггер‑цена отдельно, для остальных уже выведена как executionPrice
                        if (isStopLimit || isStopLoss || isTakeProfit) {
                            Text("Триггер‑цена выставления заявки: ≈${formatPrice(triggerPrice, uiState.selectedInstrument?.instrumentType ?: "")}")
                        }
                    }

                    // Цена исполнения и общая стоимость
                    if (executionPrice > 0) {
                        if (!(isStopLoss || isTakeProfit)) { // для этих типов не показываем текущую цену
                            Text("$executionLabel: $priceSymbol${formatPrice(executionPrice, uiState.selectedInstrument?.instrumentType ?: "")}")
                        }
                        Text("Общая стоимость: $priceSymbol${formatPrice(executionPrice * quantity, uiState.selectedInstrument?.instrumentType ?: "")}")
                    }

                    // Контрсделка
                    if (uiState.pairTradingEnabled && uiState.pairedInstrument != null) {
                        val pairedQty = (quantity * (uiState.pairedMultiplier.toDoubleOrNull() ?: 1.0)).toLong()
                        val pairDirection = if (pendingDirection == "Покупка") "продажа" else "покупка"
                        val pairTicker = uiState.pairedInstrument?.ticker ?: ""

                        // Определяем цену для контрсделки
                        val pairExecPrice: Double
                        val pairApprox: Boolean
                        when {
                            isLimit || isStopLimit -> {
                                // для лимитных заявок – та же лимитная цена
                                pairExecPrice = executionPrice
                                pairApprox = false
                            }
                            else -> {
                                // для рыночных, стоп‑лосс, тейк‑профит – рыночная цена парного инструмента
                                pairExecPrice = uiState.pairCurrentPrice ?: 0.0
                                pairApprox = true
                            }
                        }
                        val pairSymbol = if (pairApprox) "≈" else ""

                        Text("Контрсделка: $pairDirection $pairedQty лотов $pairTicker")
                        if (pairExecPrice > 0) {
                            Text("Цена исполнения: $pairSymbol${formatPrice(pairExecPrice, uiState.pairedInstrument?.instrumentType ?: "")}")
                            Text("Общая стоимость: $pairSymbol${formatPrice(pairExecPrice * pairedQty, uiState.pairedInstrument?.instrumentType ?: "")}")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pendingDirection == "Покупка") viewModel.onBuyClick() else viewModel.onSellClick()
                    showConfirmDialog = false
                }) {
                    Text("Подтвердить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { message ->
            val visuals = object : SnackbarVisuals {
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

    if (uiState.showBrokerDialog) {
        val availableBrokers = ServiceLocator.getBrokerManager().getAvailableBrokers()
        BrokerAccountDialog(
            availableBrokers = availableBrokers,
            selectedBroker = uiState.selectedBroker,
            onBrokerSelected = { viewModel.onBrokerSelected(it) },
            accounts = uiState.dialogAccounts,
            selectedAccountId = uiState.selectedAccountIdDialog,
            onAccountSelected = { viewModel.onAccountSelectedDialog(it) },
            onDismiss = { viewModel.closeBrokerDialog() },
            onSave = { viewModel.saveBrokerSettings() }
        )
    }

    if (showStopOrdersDialog) {
        StopOrdersDialog(
            viewModel = stopOrdersViewModel,
            onDismiss = { showStopOrdersDialog = false }
        )
    }
}

// ==================== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ====================

@Composable
fun ApiNotInitializedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠️ API не подключен", style = MaterialTheme.typography.titleMedium)
            Text("Перейдите в Настройки и введите токен доступа", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun StatusCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f))
            if (!isError) TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<InstrumentUi>,
    onInstrumentSelected: (InstrumentUi) -> Unit,
    onClear: () -> Unit,
    recentInstruments: List<InstrumentUi> = emptyList(),
    tradingStatuses: Map<String, TradingAvailability> = emptyMap(),
    modifier: Modifier = Modifier
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
            query = inputText,
            onQueryChange = { newText: String ->
                inputText = newText
                onQueryChanged(newText)
                expanded = newText.isNotEmpty() || recentInstruments.isNotEmpty()
            },
            onSearch = { expanded = false },
            active = expanded,
            onActiveChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Введите тикер или название",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
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
            }
        ) {
            if (expanded && (searchResults.isNotEmpty() || (inputText.isEmpty() && recentInstruments.isNotEmpty()))) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (inputText.isEmpty() && recentInstruments.isNotEmpty()) {
                        recentInstruments.forEach { instrument ->
                            val typeColor = getInstrumentTypeColor(instrument.instrumentType)
                            ListItem(
                                headlineContent = { Text("${instrument.ticker} - ${instrument.name}") },
                                supportingContent = {
                                    val status = tradingStatuses[instrument.tscalpInstrumentId]
                                    val textColor = when (status) {
                                        TradingAvailability.AVAILABLE -> Color(0xFF2E7D32)
                                        TradingAvailability.UNAVAILABLE -> Color(0xFFC62828)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Text(
                                        text = instrument.tscalpInstrumentId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor
                                    )
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(36.dp)
                                            .background(typeColor)
                                    )
                                },
                                modifier = Modifier.clickable {
                                    onInstrumentSelected(instrument)
                                    inputText = "${instrument.ticker} - ${instrument.name}"
                                    expanded = false
                                }
                            )
                        }
                    } else {
                        searchResults.forEach { instrument ->
                            val typeColor = getInstrumentTypeColor(instrument.instrumentType)
                            ListItem(
                                headlineContent = { Text("${instrument.ticker} - ${instrument.name}") },
                                supportingContent = {
                                    val status = tradingStatuses[instrument.tscalpInstrumentId]
                                    val textColor = when (status) {
                                        TradingAvailability.AVAILABLE -> Color(0xFF2E7D32)
                                        TradingAvailability.UNAVAILABLE -> Color(0xFFC62828)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Text(
                                        text = instrument.tscalpInstrumentId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor
                                    )
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(36.dp)
                                            .background(typeColor)
                                    )
                                },
                                modifier = Modifier.clickable {
                                    onInstrumentSelected(instrument)
                                    inputText = "${instrument.ticker} - ${instrument.name}"
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getInstrumentTypeColor(instrumentType: String): Color {
    return when (instrumentType) {
        "share" -> Color(0xFF1565C0)
        "bond" -> Color(0xFFE65100)
        "etf" -> Color(0xFF2E7D32)
        "currency" -> Color(0xFF6A1B9A)
        else -> Color(0xFF757575)
    }
}