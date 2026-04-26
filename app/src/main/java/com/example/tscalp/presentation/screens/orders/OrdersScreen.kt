package com.example.tscalp.presentation.screens.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tscalp.data.repository.InstrumentUi
import com.example.tscalp.di.ServiceLocator
import java.text.NumberFormat
import java.util.*
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.presentation.screens.portfolio.PortfolioPositionCard // если компонент будет вынесен
import com.example.tscalp.ui.components.SwipeablePositionCard
import com.example.tscalp.ui.components.BrokerAccountDialog
import com.example.tscalp.domain.models.AccountUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = viewModel(factory = OrdersViewModelFactory())
) {
    val uiState by viewModel.uiState.collectAsState()
    fun getAccountName(accountId: String?, accounts: List<AccountUi>): String {
        if (accountId.isNullOrBlank()) return "Не выбран"
        return accounts.find { it.id == accountId }?.name ?: accountId.take(8) + "…"
    }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingDirection by remember { mutableStateOf("") }

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
        if (!uiState.isApiInitialized) {
            ApiNotInitializedCard()
            return@Column
        }

        // Поиск инструментов (Expandable SearchBar)
        if (uiState.isSearchActive) {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { query: String -> viewModel.onSearchQueryChanged(query) },
                onSearch = { viewModel.onSearchQueryChanged(uiState.searchQuery) },
                active = uiState.isSearchActive,
                onActiveChange = { active -> viewModel.setSearchActive(active) },
                placeholder = { Text("Поиск инструментов...") },
                leadingIcon = {
                    IconButton(onClick = { viewModel.setSearchActive(false) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Закрыть")
                    }
                }
            ) {
                if (uiState.searchResults.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(uiState.searchResults) { instrument: InstrumentUi ->
                            ResultItemCard(
                                instrument = instrument,
                                onClick = {
                                    viewModel.onInstrumentSelected(instrument)
                                    viewModel.setSearchActive(false)
                                }
                            )
                        }
                    }
                } else if (uiState.isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        } else {
            // Кнопка открытия поиска
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { viewModel.setSearchActive(true) }) {
                    Icon(Icons.Default.Search, contentDescription = "Поиск")
                }
            }
        }

        // Последние просмотренные инструменты
        if (uiState.lastSelectedInstruments.isNotEmpty()) {
            Text("Последние просмотренные", style = MaterialTheme.typography.titleSmall)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.lastSelectedInstruments) { card ->
                    val position = PortfolioPosition(
                        figi = card.instrument.figi,
                        name = card.instrument.name,
                        ticker = card.instrument.ticker,
                        quantity = card.quantity,
                        currentPrice = card.currentPrice ?: card.averagePrice ?: 0.0,
                        totalValue = (card.currentPrice ?: 0.0) * card.quantity,
                        profit = card.profit ?: 0.0,
                        profitPercent = card.profitPercent ?: 0.0,
                        instrumentType = card.instrument.instrumentType,
                        priceChangePercent = card.priceChangePercent
                    )
                    val isActive = uiState.selectedInstrument?.figi == card.instrument.figi

                    SwipeablePositionCard(
                        position = position,
                        instrumentType = card.instrument.instrumentType,
                        priceChangePercent = card.priceChangePercent,
                        onDelete = { viewModel.removeLastSelectedInstrument(card.instrument.figi) },
                        onSettings = { viewModel.openBrokerDialog(card.instrument.figi) },
                        onClick = { viewModel.onInstrumentSelected(card.instrument) },
                        isSelected = isActive,
                        brokerName = card.brokerName,      // <-- строка из карточки
                        accountName = getAccountName(card.accountId, uiState.accounts) // <-- преобразуем ID в имя
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Количество лотов с кнопками +/-
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    val currentQty = uiState.quantityAsLong ?: 0L
                    if (currentQty > 0) {
                        viewModel.onQuantityChanged((currentQty - 1).toString())
                    }
                },
                enabled = (uiState.quantityAsLong ?: 0L) > 0 && uiState.selectedInstrument != null
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Уменьшить")
            }

            OutlinedTextField(
                value = uiState.quantity,
                onValueChange = { newValue -> viewModel.onQuantityChanged(newValue) },
                label = { Text("Количество лотов") },
                placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
                isError = uiState.quantity.isNotBlank() && uiState.quantityAsLong == null,
                enabled = uiState.selectedInstrument != null
            )

            IconButton(
                onClick = {
                    val currentQty = uiState.quantityAsLong ?: 0L
                    viewModel.onQuantityChanged((currentQty + 1).toString())
                },
                enabled = uiState.selectedInstrument != null
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Увеличить")
            }
        }

        // Ориентировочная стоимость
        val quantity = uiState.quantityAsLong ?: 0L
        val price = uiState.currentPrice ?: 0.0
        if (quantity > 0 && price > 0) {
            Text(
                "Ориентировочная стоимость: ${formatCurrency(price * quantity)}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Переключатель парной торговли
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

        // Блок второго поиска (появляется при включении)
        if (uiState.pairTradingEnabled) {
            InstrumentSearchField(
                query = uiState.pairSearchQuery,
                onQueryChanged = { viewModel.onPairSearchQueryChanged(it) },
                isSearching = uiState.isPairSearching,
                searchResults = uiState.pairSearchResults,
                onInstrumentSelected = { viewModel.onPairedInstrumentSelected(it) },
                onClear = { viewModel.clearPairSearch() },
                modifier = Modifier.fillMaxWidth()
            )

            uiState.pairedInstrument?.let { paired ->
                Column {
                    ResultItemCard(instrument = paired, onClick = {})  // только для отображения
                    OutlinedTextField(
                        value = uiState.pairedMultiplier,
                        onValueChange = { viewModel.onPairedMultiplierChanged(it) },
                        label = { Text("Множитель") },
                        placeholder = { Text("1") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (pendingDirection == "Покупка") viewModel.onBuyClick() else viewModel.onSellClick()
                        showConfirmDialog = false
                    }) { Text("Подтвердить") }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) { Text("Отмена") }
                }
            )
        }

        // Статус выполнения
        uiState.statusMessage?.let { message ->
            StatusCard(message = message, isError = uiState.isError, onDismiss = { viewModel.clearStatus() })
        }

        // Диалог настроек брокера/счёта
        if (uiState.showBrokerDialog) {
            val availableBrokers = remember { ServiceLocator.getBrokerManager().getAvailableBrokers() }
            // Счета для диалога пока загружаем в ViewModel (можно добавить поле dialogAccounts)
            // Для простоты будем передавать пустой список, а загрузку сделаем позже.
            // В реальном коде нужно хранить счета в состоянии (dialogAccounts) и передавать сюда.
            BrokerAccountDialog(
                availableBrokers = availableBrokers,
                selectedBroker = uiState.selectedBroker,
                onBrokerSelected = { viewModel.onBrokerSelected(it) },
                accounts = uiState.dialogAccounts, // TODO: заменить на uiState.dialogAccounts
                selectedAccountId = uiState.selectedAccountIdDialog,
                onAccountSelected = { viewModel.onAccountSelectedDialog(it) },
                onDismiss = { viewModel.closeBrokerDialog() },
                onSave = { viewModel.saveBrokerSettings() }
            )
        }
    }
}

// ------ Вспомогательные Composable-функции ------

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


fun formatCurrency(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("ru", "RU"))
    format.currency = Currency.getInstance("RUB")
    return format.format(value)
}

/**
 * Возвращает название счёта по его ID или "Не выбран".
 */
fun getAccountName(accountId: String?, accounts: List<AccountUi>): String {
    if (accountId == null) return "Не выбран"
    return accounts.find { it.id == accountId }?.name ?: accountId.take(8)
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
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(searchResults) { expanded = searchResults.isNotEmpty() }

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded && searchResults.isNotEmpty(),
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    onQueryChanged(it)
                    expanded = it.isNotEmpty()
                },
                label = { Text("Поиск инструмента") },
                placeholder = { Text("Введите тикер или название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        else if (query.isNotEmpty()) IconButton(onClick = {
                            onClear()
                            expanded = false
                        }) { Icon(Icons.Default.Clear, "Очистить") }
                    }
                },
                supportingText = { if (query.length == 1) Text("Введите минимум 2 символа для поиска") }
            )

            ExposedDropdownMenu(
                expanded = expanded && searchResults.isNotEmpty(),
                onDismissRequest = { expanded = false }
            ) {
                Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    searchResults.forEach { instrument: InstrumentUi ->
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
                            }
                        )
                    }
                }
            }
        }
    }
}
