package com.example.tscalp.presentation.screens.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tscalp.data.repository.InstrumentUi
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.ui.components.AssetPositionCard
import com.example.tscalp.ui.components.BrokerAccountDialog
import androidx.compose.foundation.background
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
        // Заголовок
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(
                text = "Выставление заявки",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }

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
        uiState.selectedInstrument?.let { instrument ->
            val portfolioPos = uiState.portfolioPositions.find { it.figi == instrument.figi }
            val position = PortfolioPosition(
                figi = instrument.figi,
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
                onSettings = { viewModel.openBrokerDialog(instrument.figi) },
                onClick = { },  // не обязательно
                isSelected = false
            )
        }

        // ========== Поле количества с кнопками ± ==========
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    val currentQty = uiState.quantityAsLong ?: 0L
                    if (currentQty > 0) viewModel.onQuantityChanged((currentQty - 1).toString())
                },
                enabled = (uiState.quantityAsLong ?: 0L) > 0 && uiState.selectedInstrument != null
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Уменьшить количество")
            }

            OutlinedTextField(
                value = uiState.quantity,
                onValueChange = { viewModel.onQuantityChanged(it) },
                label = null,
                placeholder = { Text("Кол-во лотов") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                enabled = uiState.selectedInstrument != null
            )

            IconButton(
                onClick = {
                    val currentQty = uiState.quantityAsLong ?: 0L
                    viewModel.onQuantityChanged((currentQty + 1).toString())
                },
                enabled = uiState.selectedInstrument != null
            ) {
                Icon(Icons.Default.Add, contentDescription = "Увеличить количество")
            }
        }

        // Ориентировочная стоимость
        val quantity = uiState.quantityAsLong ?: 0L
        val price = uiState.currentPrice ?: 0.0
        if (quantity > 0 && price > 0) {
            Text("Ориентировочная стоимость: ${formatCurrency(price * quantity)}")
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
            uiState.pairedInstrument?.let { instrument ->
                val portfolioPos = uiState.portfolioPositions.find { it.figi == instrument.figi }
                val pairPrice = uiState.currentPrice  // пока используем ту же цену, что у основного
                val position = PortfolioPosition(
                    figi = instrument.figi,
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
                    priceChangePercent = uiState.selectedPriceChangePercent, // либо из lastSelectedInstruments, если хотите
                    onDelete = { viewModel.clearSelectedInstrument() },   // или clearPairSearch() для парной
                    onSettings = { viewModel.openBrokerDialog(instrument.figi) },
                    onClick = { },  // не обязательно
                    isSelected = false
                )

                OutlinedTextField(
                    value = uiState.pairedMultiplier,
                    onValueChange = { viewModel.onPairedMultiplierChanged(it) },
                    label = null,
                    placeholder = { Text("Множитель") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
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

@Composable
fun ResultItemCard(instrument: InstrumentUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${instrument.ticker} – ${instrument.name}", fontWeight = FontWeight.Bold)
                Text(instrument.figi, style = MaterialTheme.typography.bodySmall)
                if (instrument.lot > 1) Text("Лот: ${instrument.lot} шт.", style = MaterialTheme.typography.bodySmall)
            }
            Text(instrument.currency, style = MaterialTheme.typography.bodySmall)
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
                label = { Text("Поиск инструмента") },
                placeholder = { Text("Введите тикер или название") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSearching) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        else if (query.isNotEmpty()) IconButton(onClick = {
                            onClear()
                            expanded = false
                        }) {
                            Icon(Icons.Default.Clear, "Очистить")
                        }
                    }
                },
                supportingText = { if (query.length == 1) Text("Введите минимум 2 символа для поиска") }
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
                        // История
                        recentInstruments.forEach { instrument: InstrumentUi ->
                            val typeColor = getInstrumentTypeColor(instrument.instrumentType)
                            //val typeColor = Color.Red // вместо getInstrumentTypeColor(instrument.instrumentType)
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
                                            .height(36.dp)               // фиксированная высота – работает стабильно
                                            .background(typeColor)
                                    )
                                }
                            )
                        }
                    } else {
                        // Результаты поиска
                        searchResults.forEach { instrument: InstrumentUi ->
                            val typeColor = getInstrumentTypeColor(instrument.instrumentType)
                            //val typeColor = Color.Red // вместо getInstrumentTypeColor(instrument.instrumentType)
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
                                            .height(36.dp)               // фиксированная высота – работает стабильно
                                            .background(typeColor)
                                    )
                                }
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