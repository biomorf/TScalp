package com.example.tscalp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tscalp.domain.models.AccountUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerAccountDialog(
    availableBrokers: List<String>,
    selectedBroker: String,
    onBrokerSelected: (String) -> Unit,
    accounts: List<AccountUi>,
    selectedAccountId: String?,
    onAccountSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    // Локальный флаг, чтобы кнопка сразу активировалась при выборе
    var isSaveEnabled by remember { mutableStateOf(selectedAccountId != null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки торговли") },
        text = {
            Column {
                // Выбор брокера
                Text("Брокер", style = MaterialTheme.typography.titleSmall)
                var brokerExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = brokerExpanded,
                    onExpandedChange = { brokerExpanded = it }
                ) {
                    TextField(
                        value = selectedBroker,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brokerExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = brokerExpanded,
                        onDismissRequest = { brokerExpanded = false }
                    ) {
                        availableBrokers.forEach { broker ->
                            DropdownMenuItem(
                                text = { Text(broker) },
                                onClick = {
                                    onBrokerSelected(broker)
                                    brokerExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Выбор счёта
                Text("Счёт", style = MaterialTheme.typography.titleSmall)
                var accountExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { accountExpanded = it }
                ) {
                    TextField(
                        value = accounts.find { it.id == selectedAccountId }?.name ?: "Выберите счёт",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = accountExpanded,
                        onDismissRequest = { accountExpanded = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    onAccountSelected(account.id)
                                    isSaveEnabled = true   // ← включаем кнопку
                                    accountExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = isSaveEnabled) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}