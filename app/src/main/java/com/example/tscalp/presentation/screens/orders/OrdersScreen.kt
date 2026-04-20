package com.example.tscalp.presentation.screens.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tscalp.domain.models.AccountUi

@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = viewModel(
        factory = OrdersViewModelFactory(LocalContext.current)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null && !uiState.isError) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearStatus()
        }
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

        // Поле ввода FIGI
        OutlinedTextField(
            value = uiState.figi,
            onValueChange = viewModel::onFigiChanged,
            label = { Text("FIGI инструмента") },
            placeholder = { Text("Например: BBG004730N88") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = uiState.figi.isNotBlank() && uiState.figi.length < 3,
            supportingText = {
                if (uiState.figi.isNotBlank() && uiState.figi.length < 3) {
                    Text("FIGI должен содержать минимум 3 символа")
                }
            }
        )

        // Поле ввода количества
        OutlinedTextField(
            value = uiState.quantity,
            onValueChange = viewModel::onQuantityChanged,
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
            }
        )

        // Выбор счёта
        AccountSelector(
            accounts = uiState.accounts,
            selectedAccountId = uiState.selectedAccountId,
            onAccountSelected = viewModel::onAccountSelected,
            onRefresh = viewModel::retryLoadAccounts,
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
                onClick = viewModel::onBuyClick,
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
                onClick = viewModel::onSellClick,
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
                onDismiss = viewModel::clearStatus
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
                accounts.forEach { account ->
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
            containerColor = MaterialTheme.colorScheme.secondaryContainer
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