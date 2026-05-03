package com.example.tscalp.presentation.screens.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField as FoundationBasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.tscalp.domain.models.InstrumentUi
import com.example.tscalp.domain.models.OrderTypeSelection
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.ui.components.AssetPositionCard
import com.example.tscalp.ui.components.BrokerAccountDialog
import com.example.tscalp.ui.components.StopOrdersDialog
import com.example.tscalp.util.formatCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = viewModel(factory = OrdersViewModelFactory())
) {
    val uiState by viewModel.uiState.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingDirection by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

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

    val snackbarHostState = remember { SnackbarHostState() }

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

    LaunchedEffect(Unit) {
        viewModel.checkApiInitialization()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Заголовок с кнопкой "Список заявок"
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

            // ========== Основной поиск ==========
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
                modifier = Modifier.fillMaxWidth()
            )

            // ========== Основная карточка ==========
            uiState.selectedInstrument?.let { instrument: InstrumentUi ->
                val portfolioPos = uiState.portfolioPositions.find { it.ticker == instrument.ticker }
                val position = PortfolioPosition(
                    name = instrument.name,
                    ticker = instrument.ticker,
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
                    resetSwipe = uiState.swipeResetTrigger
                )
            }

            // ========== Поле количества ==========
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        val currentQty = uiState.quantityAsLong ?: 0L
                        if (currentQty > 0) viewModel.onQuantityChanged((currentQty - 1).toString())
                    },
                    enabled = (uiState.quantityAsLong ?: 0L) > 0 && uiState.selectedInstrument != null,
                    modifier = Modifier.fillMaxHeight().width(28.dp)
                ) {
                    Icon(Icons.Default.Remove, "Уменьшить", modifier = Modifier.size(18.dp))
                }

                FoundationBasicTextField(
                    value = uiState.quantity,
                    onValueChange = { viewModel.onQuantityChanged(it) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    decorationBox = { innerTextField ->
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = if (uiState.quantity.isNotBlank() && uiState.quantityAsLong == null)
                                            MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (uiState.quantity.isEmpty()) {
                                    Text(
                                        "Кол-во лотов",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                            val currentQty = uiState.quantityAsLong ?: 0L
                            val currentPrice = uiState.currentPrice ?: 0.0
                            if (currentQty > 0 && currentPrice > 0) {
                                Text(
                                    "Ориентировочно: ${formatCurrency(currentPrice * currentQty)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                )

                IconButton(
                    onClick = {
                        val currentQty = uiState.quantityAsLong ?: 0L
                        viewModel.onQuantityChanged((currentQty + 1).toString())
                    },
                    enabled = uiState.selectedInstrument != null,
                    modifier = Modifier.fillMaxHeight().width(28.dp)
                ) {
                    Icon(Icons.Default.Add, "Увеличить", modifier = Modifier.size(18.dp))
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

            // ========== Ценовые поля ==========
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedVisibility(
                        visible = uiState.orderType is OrderTypeSelection.Limit,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        FoundationBasicTextField(
                            value = uiState.limitPrice,
                            onValueChange = { viewModel.onLimitPriceChanged(it) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    if (uiState.limitPrice.isEmpty()) {
                                        Text(
                                            "Цена за лот",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
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
                            FoundationBasicTextField(
                                value = uiState.stopPrice,
                                onValueChange = { viewModel.onStopPriceChanged(it) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        if (uiState.stopPrice.isEmpty()) {
                                            Text(
                                                "Триггер стоп-цена",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                            AnimatedVisibility(
                                visible = uiState.orderType is OrderTypeSelection.StopLimit,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                FoundationBasicTextField(
                                    value = uiState.limitPrice,
                                    onValueChange = { viewModel.onLimitPriceChanged(it) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            if (uiState.limitPrice.isEmpty()) {
                                                Text(
                                                    "Лимитная цена",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                    )
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ========== Парная торговля ==========
            if (uiState.orderType is OrderTypeSelection.Market || uiState.orderType is OrderTypeSelection.Limit) {
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
            }

            if (uiState.pairTradingEnabled) {
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
                    modifier = Modifier.fillMaxWidth()
                )

                uiState.pairedInstrument?.let { instrument: InstrumentUi ->
                    val portfolioPos = uiState.portfolioPositions.find { it.ticker == instrument.ticker }
                    val pairPrice = uiState.pairCurrentPrice
                    val position = PortfolioPosition(
                        name = instrument.name,
                        ticker = instrument.ticker,
                        quantity = portfolioPos?.quantity ?: 0L,
                        currentPrice = pairPrice ?: portfolioPos?.currentPrice ?: 0.0,
                        totalValue = (pairPrice ?: 0.0) * (portfolioPos?.quantity ?: 0L),
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
                        resetSwipe = uiState.swipeResetTrigger
                    )

                    FoundationBasicTextField(
                        value = uiState.pairedMultiplier,
                        onValueChange = { viewModel.onPairedMultiplierChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 36.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (uiState.pairedMultiplier.isEmpty()) {
                                    Text(
                                        "Множитель",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            // Кнопки КУПИТЬ / ПРОДАТЬ
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

        // Снекбар поверх всего
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp)
        ) { data ->
            val label = data.visuals.actionLabel
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
        val price = uiState.currentPrice ?: 0.0
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Подтверждение заявки") },
            text = {
                Column {
                    Text("Вы собираетесь ${pendingDirection.lowercase()} $quantity лотов $ticker")
                    if (price > 0) {
                        Text("Текущая цена: ${formatCurrency(price)}")
                        Text("Общая стоимость: ${formatCurrency(price * quantity)}")
                    }
                    if (uiState.pairTradingEnabled && uiState.pairedInstrument != null) {
                        val pairedQty = (quantity * (uiState.pairedMultiplier.toDoubleOrNull() ?: 1.0)).toLong()
                        Text("Контрсделка: ${uiState.pairedInstrument?.ticker} ${if (pendingDirection == "Покупка") "продажа" else "покупка"} $pairedQty лотов")
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
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⚠️ API не подключен", style = MaterialTheme.typography.titleMedium)
            Text(
                "Перейдите в Настройки и введите токен доступа",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun StatusCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, modifier = Modifier.weight(1f))
            if (!isError) {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
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
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val showDropdown = expanded && (searchResults.isNotEmpty() || (query.isEmpty() && recentInstruments.isNotEmpty()))

    LaunchedEffect(searchResults) {
        if (searchResults.isNotEmpty()) expanded = true
    }

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = showDropdown,
            onExpandedChange = { expanded = it }
        ) {
            TextField(
                value = query,
                onValueChange = {
                    onQueryChanged(it)
                    expanded = it.isNotEmpty() || recentInstruments.isNotEmpty()
                },
                placeholder = {
                    Text(
                        "Введите тикер или название",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)                               // компактная высота
                    .menuAnchor()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp), // минимальные отступы для читаемости
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSearching) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        else if (query.isNotEmpty()) IconButton(
                            onClick = { onClear(); expanded = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Clear, "Очистить", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )

            ExposedDropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { expanded = false }
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (query.isEmpty() && recentInstruments.isNotEmpty()) {
                        recentInstruments.forEach { instrument ->
                            val typeColor = getInstrumentTypeColor(instrument.instrumentType)
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("${instrument.ticker} - ${instrument.name}")
                                        Text(instrument.figi, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    onInstrumentSelected(instrument)
                                    expanded = false
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(36.dp)
                                            .background(typeColor)
                                    )
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    } else {
                        searchResults.forEach { instrument ->
                            val typeColor = getInstrumentTypeColor(instrument.instrumentType)
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("${instrument.ticker} - ${instrument.name}")
                                        Text(instrument.figi, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    onInstrumentSelected(instrument)
                                    expanded = false
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(36.dp)
                                            .background(typeColor)
                                    )
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
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