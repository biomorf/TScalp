package com.example.tscalp.presentation.screens.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tscalp.domain.models.AccountUi
import ru.tinkoff.piapi.contract.v1.Instrument

@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = viewModel(factory = OrdersViewModelFactory()
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    // Автоматически скрываем статусное сообщение через 5 секунд (если не ошибка)
    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null && !uiState.isError) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearStatus()
        }
    }

    // !!! НОВЫЙ БЛОК: проверяем актуальное состояние API при каждом отображении экрана
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
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                text = "Выставление заявки",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }

        // Проверка инициализации API
        if (!uiState.isApiInitialized) {
            ApiNotInitializedCard()
            return@Column
        }

        // Умный поиск инструмента
        InstrumentSearchField(
            query = uiState.searchQuery,
            onQueryChanged = { query: String -> viewModel.onSearchQueryChanged(query) },
            isSearching = uiState.isSearching,
            searchResults = uiState.searchResults,
            onInstrumentSelected = { instrument: Instrument ->
                viewModel.onInstrumentSelected(instrument)
            },
            onClear = { viewModel.clearSearch() },
            modifier = Modifier.fillMaxWidth()
        )

        // Поле ввода количества
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

        // Выбор счёта
        AccountSelector(
            accounts = uiState.accounts,
            selectedAccountId = uiState.selectedAccountId,
            onAccountSelected = { accountId: String -> viewModel.onAccountSelected(accountId) },
            onRefresh = { viewModel.retryLoadAccounts() },
            modifier = Modifier.fillMaxWidth()
        )

        // Информация о выбранном счёте
        uiState.accounts.find { it.id == uiState.selectedAccountId }?.let { account ->
            AccountInfoCard(account = account)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Кнопки Купить / Продать
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { viewModel.onBuyClick() },
                modifier = Modifier.weight(1f),
                enabled = uiState.isFormValid && !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("КУПИТЬ")
                }
            }

            Button(
                onClick = { viewModel.onSellClick() },
                modifier = Modifier.weight(1f),
                enabled = uiState.isFormValid && !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onError
                    )
                } else {
                    Text("ПРОДАТЬ")
                }
            }
        }

        // Статус выполнения
        uiState.statusMessage?.let { message ->
            StatusCard(
                message = message,
                isError = uiState.isError,
                onDismiss = { viewModel.clearStatus() }
            )
        }
    }
}

@Composable
fun ApiNotInitializedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚠️ API не подключен",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Перейдите в Настройки и введите токен доступа",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<Instrument>,
    onInstrumentSelected: (Instrument) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded && searchResults.isNotEmpty(),
            onExpandedChange = {
                expanded = if (searchResults.isNotEmpty()) it else false
            }
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                label = { Text("Поиск инструмента") },
                placeholder = { Text("Введите тикер или название") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else if (query.isNotEmpty()) {
                            IconButton(onClick = onClear) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Очистить"
                                )
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
                expanded = expanded && searchResults.isNotEmpty(),
                onDismissRequest = { expanded = false }
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(items = searchResults) { instrument: Instrument ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "${instrument.ticker} - ${instrument.name}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = instrument.figi,
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

@Composable
fun InstrumentInfoCard(instrument: Instrument) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = instrument.ticker,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = instrument.currency,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = instrument.name,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "FIGI: ${instrument.figi}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (instrument.lot > 1) {
                Text(
                    text = "Лот: ${instrument.lot} шт.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Торговый счёт",
                style = MaterialTheme.typography.titleSmall
            )

            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Обновить список счетов"
                )
            }
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            TextField(
                value = accounts.find { it.id == selectedAccountId }?.let {
                    "${it.name} (${it.type})"
                } ?: "Выберите счёт",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                accounts.forEach { account: AccountUi ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(account.name)
                                Text(
                                    text = account.type.toString(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        onClick = {
                            onAccountSelected(account.id)
                            expanded = false
                        }
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "📊 ${account.name}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Тип: ${account.type}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "ID: ${account.id.take(8)}...",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun StatusCard(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            if (!isError) {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        }
    }
}