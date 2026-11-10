package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccountType
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TInvestAccountSection(
    availableAccounts: List<BrokerAccount>,
    defaultAccountId: String,
    sandboxMode: Boolean,
    isRefreshing: Boolean,
    accountExpanded: Boolean,
    onAccountExpandedChange: (Boolean) -> Unit,
    onAccountSelected: (String) -> Unit,
    onOpenSandbox: () -> Unit,
    onCloseSandboxClick: () -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = accountExpanded,
        onExpandedChange = onAccountExpandedChange,
    ) {
        val selectedAccount = availableAccounts.find { it.id == defaultAccountId }
        TextField(
            value = selectedAccount?.let { "${it.name} (${it.id})" } ?: "Выберите счёт",
            onValueChange = {},
            readOnly = true,
            label = { Text("Счёт по умолчанию") },
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
            onDismissRequest = { onAccountExpandedChange(false) },
        ) {
            availableAccounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text("${account.name} (${account.id})") },
                    onClick = {
                        onAccountSelected(account.id)
                        onAccountExpandedChange(false)
                    },
                )
            }
        }
    }

    if (sandboxMode) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onOpenSandbox,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRefreshing,
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            } else {
                Text("Открыть новый счёт песочницы")
            }
        }
    }

    if (sandboxMode && defaultAccountId.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onCloseSandboxClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Закрыть счёт песочницы")
        }
    }
}

@Composable
internal fun CloseSandboxAccountDialog(
    accountName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Закрытие счёта песочницы") },
        text = {
            Text(
                "Вы уверены, что хотите закрыть счёт «$accountName»? Это действие необратимо.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Закрыть")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Preview(name = "AccountSection — expanded", showBackground = true, widthDp = 360)
@Composable
private fun TInvestAccountSectionExpandedPreview() {
    TScalpTheme {
        TInvestAccountSection(
            availableAccounts =
                listOf(
                    BrokerAccount(id = "1", name = "Main Account", type = BrokerAccountType.BROKER),
                    BrokerAccount(id = "2", name = "IIS Account", type = BrokerAccountType.IIS),
                ),
            defaultAccountId = "1",
            sandboxMode = true,
            isRefreshing = false,
            accountExpanded = true,
            onAccountExpandedChange = {},
            onAccountSelected = {},
            onOpenSandbox = {},
            onCloseSandboxClick = {},
        )
    }
}

@Preview(name = "CloseSandboxDialog", showBackground = true, widthDp = 360)
@Composable
private fun CloseSandboxAccountDialogPreview() {
    TScalpTheme {
        CloseSandboxAccountDialog(
            accountName = "Main Account",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
