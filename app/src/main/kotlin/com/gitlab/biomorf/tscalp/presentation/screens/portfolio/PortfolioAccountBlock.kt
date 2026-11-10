package com.gitlab.biomorf.tscalp.presentation.screens.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.SharePosition
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import com.gitlab.biomorf.tscalp.presentation.components.AssetPositionCard
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme

@Composable
internal fun PortfolioAccountBlock(
    accountKey: AccountKey,
    accountPortfolio: AccountPortfolio,
    prices: Map<String, Double>,
    priceChangePercents: Map<String, Double>,
    tradingStatuses: Map<String, TradingAvailability>,
    sandboxMode: Boolean,
    onPayIn: () -> Unit,
) {
    AccountHeaderCard(
        accountKey = accountKey,
        accountPortfolio = accountPortfolio,
        sandboxMode = sandboxMode,
        onPayIn = onPayIn,
    )
    val positions = accountPortfolio.positions
    if (positions.isEmpty()) {
        Text(
            "Нет позиций",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    } else {
        positions.forEach { position ->
            val id = position.instrument.tscalpInstrumentId
            AssetPositionCard(
                position = position,
                displayCurrentPrice = prices[id],
                priceChangePercent = priceChangePercents[id],
                tradingAvailability =
                    tradingStatuses[id]
                        ?: TradingAvailability.UNKNOWN,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}


@Preview(name = "AccountBlock — one position", showBackground = true, widthDp = 360)
@Composable
private fun PortfolioAccountBlockPreview() {
    TScalpTheme {
        PortfolioAccountBlock(
            accountKey = AccountKey(brokerName = BrokerName.TINVEST, accountId = "2309452027"),
            accountPortfolio =
                AccountPortfolio(
                    accountId = "2309452027",
                    brokerName = BrokerName.TINVEST,
                    positions =
                        listOf(
                            SharePosition(
                                instrument =
                                    Share(
                                        brokerName = BrokerName.TINVEST,
                                        tscalpInstrumentId = "uid-sber",
                                        ticker = "SBER",
                                        name = "Сбербанк",
                                        isin = "RU0009029540",
                                    ),
                                brokerName = BrokerName.TINVEST,
                                accountId = "2309452027",
                                quantity = 10.0,
                                currentPrice = 100.0,
                            ),
                        ),
                    balances = emptyList(),
                ),
            prices = mapOf("uid-sber" to 100.0),
            priceChangePercents = emptyMap(),
            tradingStatuses = emptyMap(),
            sandboxMode = false,
            onPayIn = {},
        )
    }
}
