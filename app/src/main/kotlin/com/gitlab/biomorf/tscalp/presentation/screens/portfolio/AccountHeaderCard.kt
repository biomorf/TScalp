package com.gitlab.biomorf.tscalp.presentation.screens.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.presentation.util.formatDomesticCurrency

@Composable
internal fun AccountHeaderCard(
    accountKey: AccountKey,
    accountPortfolio: AccountPortfolio?,
    sandboxMode: Boolean,
    onPayIn: () -> Unit,
) {
    val rubBalance =
        accountPortfolio
            ?.balances
            ?.firstOrNull { it.currency.equals("rub", ignoreCase = true) }
            ?.total
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    accountKey.brokerName.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    maskAccountId(accountKey.accountId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    rubBalance?.let { formatDomesticCurrency(it) } ?: "нет данных",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (sandboxMode && accountKey.brokerName == BrokerName.TINVEST) {
                Button(onClick = onPayIn) {
                    Text("Пополнить")
                }
            }
        }
    }
}

private const val MASK_VISIBLE_DIGITS = 4
private const val MASK_BULLET = "\u2022"

private fun maskAccountId(id: String): String =
    if (id.length <= MASK_VISIBLE_DIGITS) {
        id
    } else {
        MASK_BULLET.repeat(MASK_VISIBLE_DIGITS) + id.takeLast(MASK_VISIBLE_DIGITS)
    }
