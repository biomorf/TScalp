package com.example.tscalp.presentation.screens.orders

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBar
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
import com.example.tscalp.di.ServiceLocator
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

        // Информация о выбранном инструменте
        uiState.selectedInstrument?.let { instrument ->
            InstrumentInfoCard(instrument = instrument)
            uiState.currentPrice?.let { price ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Текущая цена: ${formatCurrency(price)}")
                        Text("Валюта: ${instrument.currency}")
                    }
                }
            }
        }

        // Поле количества
        OutlinedTextField(
            value = uiState.quantity,
            onValueChange = { quantity: String -> viewModel.onQuantityChanged(quantity) },
            label = { Text("Количество лотов") },
            placeholder = { Text("Введите целое число") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = uiState.quantity.isNotBlank() && uiState.quantityAsLong == null,
            supportingText = {
                if (uiState.quantity.isNotBlank() && uiState.quantityAsLong == null) {
                    Text("Введите корректное число")
                }
            },
            enabled = uiState.selectedInstrument != null
        )

        val quantity = uiState.quantityAsLong ?: 0L
        val price = uiState.currentPrice ?: 0.0
        if (quantity > 0 && price > 0) {
            Text("Ориентировочная стоимость: ${formatCurrency(price * quantity)}")
        }

        // Выбор счета
        AccountSelector(
            accounts = uiState.accounts,
            selectedAccountId = uiState.selectedAccountId,
            onAccountSelected = { accountId: String -> viewModel.onAccountSelected(accountId) },
            onRefresh = { viewModel.retryLoadAccounts() },
            modifier = Modifier.fillMaxWidth()
        )
        uiState.accounts.find { it.id == uiState.selectedAccountId }?.let { account ->
            AccountInfoCard(account = account)
        }

        // Последние просмотренные инструменты
        if (uiState.lastSelectedInstruments.isNotEmpty()) {
            Text("Последние просмотренные", style = MaterialTheme.typography.titleSmall)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.lastSelectedInstruments) { card ->
                    SelectedInstrumentCard(
                        card = card,
                        onSelect = { viewModel.onInstrumentSelected(card.instrument) }
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
fun InstrumentInfoCard(instrument: InstrumentUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(instrument.ticker, fontWeight = FontWeight.Bold)
                Text(instrument.currency, style = MaterialTheme.typography.bodySmall)
            }
            Text(instrument.name)
            Text("FIGI: ${instrument.figi}", style = MaterialTheme.typography.bodySmall)
            if (instrument.lot > 1) Text("Лот: ${instrument.lot} шт.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SelectedInstrumentCard(
    card: SelectedInstrumentInfo,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(card.instrument.ticker, fontWeight = FontWeight.Bold)
                    Text(card.instrument.name, style = MaterialTheme.typography.bodySmall)
                }
                if (card.currentPrice != null) {
                    Text(formatCurrency(card.currentPrice!!), fontWeight = FontWeight.Bold)
                }
            }
            if (card.quantity > 0 && card.averagePrice != null && card.currentPrice != null) {
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${card.quantity} шт.")
                    val profit = card.profit
                        ?: (card.currentPrice - card.averagePrice) * card.quantity
                    val profitPercent = card.profitPercent
                        ?: ((card.currentPrice / card.averagePrice - 1) * 100)
                    Row {
                        Text(
                            "${if (profit >= 0) "+" else ""}${formatCurrency(profit)}",
                            color = if (profit >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        Text(" (${"%.2f".format(profitPercent)}%)")
                    }
                }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSelector(
    accounts: List<AccountUi>,
    selectedAccountId: String?,
    onAccountSelected: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Торговый счёт", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Обновить") }
        }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            TextField(
                value = accounts.find { it.id == selectedAccountId }?.let { "${it.name} (${it.type})" } ?: "Выберите счёт",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                accounts.forEach { account: AccountUi ->
                    DropdownMenuItem(
                        text = { Column { Text(account.name); Text(account.type.toString(), style = MaterialTheme.typography.bodySmall) } },
                        onClick = { onAccountSelected(account.id); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun AccountInfoCard(account: AccountUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("📊 ${account.name}", style = MaterialTheme.typography.titleSmall)
            Text("Тип: ${account.type}", style = MaterialTheme.typography.bodySmall)
            Text("ID: ${account.id.take(8)}...", style = MaterialTheme.typography.bodySmall)
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