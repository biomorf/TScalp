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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tscalp.data.repository.InstrumentUi
import com.example.tscalp.domain.models.AccountUi
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.presentation.screens.portfolio.PortfolioPositionCard
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = viewModel(factory = OrdersViewModelFactory())
) {
    val uiState by viewModel.uiState.collectAsState()
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

        // Основной поиск (с историей)
        InstrumentSearchField(
            query = uiState.searchQuery,
            onQueryChanged = { query: String -> viewModel.onSearchQueryChanged(query) },
            isSearching = uiState.isSearching,
            searchResults = uiState.searchResults,
            onInstrumentSelected = { instrument: InstrumentUi -> viewModel.onInstrumentSelected(instrument) },
            onClear = { viewModel.clearSearch() },
            recentInstruments = uiState.lastSelectedInstruments.map { it.instrument },
            modifier = Modifier.fillMaxWidth()
        )

        // Основная карточка выбранного инструмента
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
            PortfolioPositionCard(
                position = position,
                instrumentType = instrument.instrumentType,
                priceChangePercent = null
            )
        }

        // Поле количества с кнопками +/-
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
                Icon(Icons.Default.Add, contentDescription = "Увеличить количество")
            }
        }

        // Ориентировочная стоимость
        val quantity = uiState.quantityAsLong ?: 0L
        val price = uiState.currentPrice ?: 0.0
        if (quantity > 0 && price > 0) {
            Text("Ориентировочная стоимость: ${formatCurrency(price * quantity)}")
        }

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

        // Блок парного инструмента
        if (uiState.pairTradingEnabled) {
            InstrumentSearchField(
                query = uiState.pairSearchQuery,
                onQueryChanged = { query: String -> viewModel.onPairSearchQueryChanged(query) },
                isSearching = uiState.isPairSearching,
                searchResults = uiState.pairSearchResults,
                onInstrumentSelected = { instrument: InstrumentUi -> viewModel.onPairedInstrumentSelected(instrument) },
                onClear = { viewModel.clearPairSearch() },
                modifier = Modifier.fillMaxWidth()
            )

            uiState.pairedInstrument?.let { paired ->
                Column {
                    ResultItemCard(instrument = paired, onClick = {})
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
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                onClear()
                                expanded = false
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить")
                            }
                        }
                    }
                },
                supportingText = {
                    if (query.length == 1) {
                        Text("Введите минимум 2 символа для поиска")
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
                        // Показываем историю
                        recentInstruments.forEach { instrument: InstrumentUi ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("${instrument.ticker} - ${instrument.name}")
                                        Text(
                                            instrument.figi,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    onInstrumentSelected(instrument)
                                    expanded = false
                                }
                            )
                        }
                    } else {
                        // Показываем результаты поиска
                        searchResults.forEach { instrument: InstrumentUi ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("${instrument.ticker} - ${instrument.name}")
                                        Text(
                                            instrument.figi,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
}

fun formatCurrency(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("ru", "RU"))
    format.currency = Currency.getInstance("RUB")
    return format.format(value)
}