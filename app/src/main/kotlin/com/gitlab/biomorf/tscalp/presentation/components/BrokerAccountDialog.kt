package com.gitlab.biomorf.tscalp.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccountType
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme

// Архитектурный долг: функция требует декомпозиции.
// См. ROADMAP → «Рефакторинг Compose-компонентов».
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerAccountDialog(
    availableBrokers: List<BrokerName>,
    selectedBroker: BrokerName,
    onBrokerSelected: (BrokerName) -> Unit,
    accounts: List<BrokerAccount>,
    selectedAccountId: String?,
    onAccountSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    showAccountSelector: Boolean = true,
) {
    // Устойчивое сравнение с trim, fallback на первый счёт
    val selectedAccountName =
        remember(accounts, selectedAccountId) {
            val trimmedSelected = selectedAccountId?.trim()
            val account = accounts.firstOrNull { it.id.trim() == trimmedSelected }
            account?.name?.ifBlank { "Счёт ${account.id.take(8)}…" }
                ?: accounts.firstOrNull()?.name?.ifBlank { "Счёт ${accounts.first().id.take(8)}…" }
                ?: "Счёт не найден"
        }

    // Кнопка активна, если выбран счёт ИЛИ если выбор счёта скрыт (тогда достаточно выбрать брокера)
    val isSaveEnabled =
        if (showAccountSelector) {
            selectedAccountId != null && accounts.any { it.id.trim() == selectedAccountId.trim() }
        } else {
            true // сразу активна, как только показан диалог (пользователь может просто нажать "Сохранить")
        }

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
                    onExpandedChange = { brokerExpanded = it },
                ) {
                    TextField(
                        value = selectedBroker.displayName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brokerExpanded) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .menuAnchor(
                                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true,
                                ),
                    )
                    ExposedDropdownMenu(
                        expanded = brokerExpanded,
                        onDismissRequest = { brokerExpanded = false },
                    ) {
                        availableBrokers.forEach { broker ->
                            DropdownMenuItem(
                                text = { Text(broker.displayName) },
                                onClick = {
                                    onBrokerSelected(broker)
                                    brokerExpanded = false
                                },
                            )
                        }
                    }
                }

                // Выбор счёта – показываем только если showAccountSelector = true
                if (showAccountSelector) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Счёт", style = MaterialTheme.typography.titleSmall)
                    var accountExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = it },
                    ) {
                        TextField(
                            value = selectedAccountName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(
                                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                        enabled = true,
                                    ),
                        )
                        ExposedDropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false },
                        ) {
                            accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name.ifBlank { "Счёт ${account.id.take(8)}…" }) },
                                    onClick = {
                                        onAccountSelected(account.id)
                                        accountExpanded = false
                                    },
                                )
                            }
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
        },
    )
}

@Preview(name = "T-Invest — with accounts", showBackground = true, widthDp = 400)
@Composable
private fun BrokerAccountDialogTInvestPreview() {
    TScalpTheme {
        BrokerAccountDialog(
            availableBrokers = listOf(BrokerName.TINVEST, BrokerName.BCS, BrokerName.FINAM),
            selectedBroker = BrokerName.TINVEST,
            onBrokerSelected = {},
            accounts =
                listOf(
                    BrokerAccount("acc-1", "Основной", BrokerAccountType.BROKER),
                    BrokerAccount("acc-2", "ИИС", BrokerAccountType.IIS),
                ),
            selectedAccountId = "acc-1",
            onAccountSelected = {},
            onDismiss = {},
            onSave = {},
        )
    }
}

@Preview(name = "BCS — empty accounts", showBackground = true, widthDp = 400)
@Composable
private fun BrokerAccountDialogBcsEmptyPreview() {
    TScalpTheme {
        BrokerAccountDialog(
            availableBrokers = listOf(BrokerName.TINVEST, BrokerName.BCS, BrokerName.FINAM),
            selectedBroker = BrokerName.BCS,
            onBrokerSelected = {},
            accounts = emptyList(),
            selectedAccountId = null,
            onAccountSelected = {},
            onDismiss = {},
            onSave = {},
        )
    }
}

@Preview(name = "FINAM — no account selector", showBackground = true, widthDp = 400)
@Composable
private fun BrokerAccountDialogFinamNoSelectorPreview() {
    TScalpTheme {
        BrokerAccountDialog(
            availableBrokers = listOf(BrokerName.TINVEST, BrokerName.BCS, BrokerName.FINAM),
            selectedBroker = BrokerName.FINAM,
            onBrokerSelected = {},
            accounts = emptyList(),
            selectedAccountId = null,
            onAccountSelected = {},
            onDismiss = {},
            onSave = {},
            showAccountSelector = false,
        )
    }
}
