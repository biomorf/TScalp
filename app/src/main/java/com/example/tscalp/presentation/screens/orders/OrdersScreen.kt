package com.example.tscalp.presentation.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.domain.models.InstrumentUi
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.domain.models.BrokerOrderType
import com.example.tscalp.ui.components.AssetPositionCard
import com.example.tscalp.ui.components.BrokerAccountDialog
import com.example.tscalp.util.formatCurrency
import com.example.tscalp.presentation.screens.orders.StopOrdersViewModel
import com.example.tscalp.ui.components.StopOrdersDialog

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

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null && !uiState.isError) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearStatus()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkApiInitialization()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Заголовок с кнопкой "Список заявок"
        TopAppBar(
            title = { Text("Выставление заявки") },
            actions = {
                IconButton(onClick = { showStopOrdersDialog = true }) {
                    Icon(Icons.Default.List, contentDescription = "Список заявок")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        if (!uiState.isApiInitialized) {
            ApiNotInitializedCard()
            return@Column
        }

        // ========== Основной поиск (с историей) ==========
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

        // ========== Основная карточка (свайпабельная) ==========
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
                priceChangePercent = uiState.selectedPriceChangePercent, // либо из lastSelectedInstruments, если хотите
                onDelete = { viewModel.clearSelectedInstrument() },   // или clearPairSearch() для парной
                onSettings = { viewModel.openBrokerDialog(instrument.ticker) },
                onClick = { },  // не обязательно
                isSelected = false,
                resetSwipe = uiState.swipeResetTrigger
            )
        }

        // ========== Поле количества с кнопками ± ==========
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),   // высота строки подстраивается под самое высокое дочернее view (поле ввода)
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Кнопка "−"
            IconButton(
                onClick = {
                    val currentQty = uiState.quantityAsLong ?: 0L
                    if (currentQty > 0) viewModel.onQuantityChanged((currentQty - 1).toString())
                },
                enabled = (uiState.quantityAsLong ?: 0L) > 0 && uiState.selectedInstrument != null,
                modifier = Modifier
                    .fillMaxHeight()   // растягиваем на всю высоту Row
                    .width(28.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    "Уменьшить",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Поле ввода с подсказкой стоимости
            BasicTextField(
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

                        // Ориентировочная стоимость (используем прямо uiState)
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

            // Кнопка "+"
            IconButton(
                onClick = {
                    val currentQty = uiState.quantityAsLong ?: 0L
                    viewModel.onQuantityChanged((currentQty + 1).toString())
                },
                enabled = uiState.selectedInstrument != null,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(28.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    "Увеличить",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

//        // Ориентировочная стоимость
//        val quantity = uiState.quantityAsLong ?: 0L
//        val price = uiState.currentPrice ?: 0.0
//        if (quantity > 0 && price > 0) {
//            Text("Ориентировочная стоимость: ${formatCurrency(price * quantity)}")
//        }

        // Выбор типа заявки (рыночная / лимитная)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = uiState.orderType == BrokerOrderType.MARKET,
                onClick = { viewModel.onOrderTypeChanged(BrokerOrderType.MARKET) },
                label = { Text("Рыночная") }
            )
            FilterChip(
                selected = uiState.orderType == BrokerOrderType.LIMIT,
                onClick = { viewModel.onOrderTypeChanged(BrokerOrderType.LIMIT) },
                label = { Text("Лимитная") }
            )
        }

        // Поле цены (только для лимитной заявки)
        if (uiState.orderType == BrokerOrderType.LIMIT) {
            BasicTextField(
                value = uiState.limitPrice,
                onValueChange = { viewModel.onLimitPriceChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp),   // такая же минимальная высота
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(4.dp)
                            )
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

        // ========== Переключатель «Парная торговля» ==========
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Парная торговля", style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = uiState.pairTradingEnabled,
                onCheckedChange = { viewModel.setPairTradingEnabled(it) }
            )
        }

        // ========== Блок парного инструмента (появляется при включённом переключателе) ==========
        if (uiState.pairTradingEnabled) {
            // Второй поиск
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

            // ========== Парная карточка (свайпабельная) и поле множителя ==========
            uiState.pairedInstrument?.let { instrument: InstrumentUi ->
                val portfolioPos = uiState.portfolioPositions.find { it.ticker == instrument.ticker }
                val pairPrice = uiState.currentPrice  // пока используем ту же цену, что у основного
                val position = PortfolioPosition(
                    name = instrument.name,
                    ticker = instrument.ticker,
                    quantity = portfolioPos?.quantity ?: 0L,
                    currentPrice = pairPrice ?: portfolioPos?.currentPrice ?: 0.0,
                    totalValue = (pairPrice ?: 0.0) * (portfolioPos?.quantity ?: 0L),
                    profit = portfolioPos?.profit ?: 0.0,
                    profitPercent = portfolioPos?.profitPercent ?: 0.0,
                    instrumentType = instrument.instrumentType,
                    priceChangePercent = null,
                )

                AssetPositionCard(
                    position = position,
                    instrumentType = instrument.instrumentType,
                    priceChangePercent = uiState.selectedPriceChangePercent, // либо из lastSelectedInstruments, если хотите
                    onDelete = { viewModel.clearSelectedInstrument() },   // или clearPairSearch() для парной
                    onSettings = { viewModel.openBrokerDialog(instrument.ticker) },
                    onClick = { },  // не обязательно
                    isSelected = false,
                    resetSwipe = uiState.swipeResetTrigger
                )

                BasicTextField(
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
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(4.dp)
                                )
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

        Spacer(modifier = Modifier.weight(1f))

        // Кнопки Купить / Продать
        Row(
            modifier = Modifier.fillMaxWidth(),
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

        // Диалог подтверждения
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

        // Статусное сообщение
        uiState.statusMessage?.let { message ->
            StatusCard(
                message = message,
                isError = uiState.isError,
                onDismiss = { viewModel.clearStatus() }
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
    }
    if (showStopOrdersDialog) {
        StopOrdersDialog(
            viewModel = stopOrdersViewModel,
            onDismiss = { showStopOrdersDialog = false }
        )
    }
}

// --- Вспомогательные Composable функции ---

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

//@Composable
//fun ResultItemCard(instrument: InstrumentUi, onClick: () -> Unit) {
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .clickable { onClick() },
//        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
//    ) {
//        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
//            Column(modifier = Modifier.weight(1f)) {
//                Text("${instrument.ticker} – ${instrument.name}", fontWeight = FontWeight.Bold)
//                Text(instrument.ticker, style = MaterialTheme.typography.bodySmall)
//                if (instrument.lot > 1) Text("Лот: ${instrument.lot} шт.", style = MaterialTheme.typography.bodySmall)
//            }
//            Text(instrument.currency, style = MaterialTheme.typography.bodySmall)
//        }
//    }
//}

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
            OutlinedTextField(
                value = query,
                onValueChange = {
                    onQueryChanged(it)
                    expanded = it.isNotEmpty() || recentInstruments.isNotEmpty()
                },
                label = null,
                placeholder = {
                    Text("Введите тикер или название", fontSize = 14.sp)
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)          // минимальная высота, но не жёсткая
                    .menuAnchor(),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSearching) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        else if (query.isNotEmpty()) IconButton(
                            onClick = {
                                onClear()
                                expanded = false
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Clear, "Очистить", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            // Выпадающий список (без изменений)
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
                        // История недавних инструментов
                        recentInstruments.forEach { instrument: InstrumentUi ->
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
                        // Результаты поиска
                        searchResults.forEach { instrument: InstrumentUi ->
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

/**
 * Возвращает цветовую индикацию типа инструмента.
 * Используется как в карточках, так и в выпадающем списке поиска.
 */
fun getInstrumentTypeColor(instrumentType: String): Color {
    return when (instrumentType) {
        "share" -> Color(0xFF1565C0)
        "bond" -> Color(0xFFE65100)
        "etf" -> Color(0xFF2E7D32)
        "currency" -> Color(0xFF6A1B9A)
        else -> Color(0xFF757575)
    }
}