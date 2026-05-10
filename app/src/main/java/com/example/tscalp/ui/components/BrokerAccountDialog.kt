package com.example.tscalp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tscalp.domain.models.BrokerAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerAccountDialog(
    availableBrokers: List<String>,
    selectedBroker: String,
    onBrokerSelected: (String) -> Unit,
    accounts: List<BrokerAccount>,
    selectedAccountId: String?,
    onAccountSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    // Устойчивое сравнение с trim, fallback на первый счёт
    val selectedAccountName = remember(accounts, selectedAccountId) {
        val trimmedSelected = selectedAccountId?.trim()
        val account = accounts.firstOrNull { it.id.trim() == trimmedSelected }
        account?.name?.ifBlank { "Счёт ${account.id.take(8)}…" }
            ?: accounts.firstOrNull()?.name?.ifBlank { "Счёт ${accounts.first().id.take(8)}…" }
            ?: "Счёт не найден"
    }

    val isSaveEnabled = selectedAccountId != null && accounts.any { it.id.trim() == selectedAccountId.trim() }

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
                        value = selectedAccountName,
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
                                text = { Text(account.name.ifBlank { "Счёт ${account.id.take(8)}…" }) },
                                onClick = {
                                    onAccountSelected(account.id)
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