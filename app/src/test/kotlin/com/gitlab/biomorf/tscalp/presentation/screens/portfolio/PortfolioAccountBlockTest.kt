package com.gitlab.biomorf.tscalp.presentation.screens.portfolio

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.SharePosition
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PortfolioAccountBlockTest {
    @get:Rule
    val rule = createComposeRule()

    private val key =
        AccountKey(brokerName = BrokerName.TINVEST, accountId = "2309452027")

    private val sberPosition =
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
        )

    private fun portfolio(positions: List<Position>) =
        AccountPortfolio(
            accountId = "2309452027",
            brokerName = BrokerName.TINVEST,
            positions = positions,
            balances = emptyList(),
        )

    @Test
    fun `empty positions renders no-positions text`() {
        rule.setContent {
            TScalpTheme {
                PortfolioAccountBlock(
                    accountKey = key,
                    accountPortfolio = portfolio(emptyList()),
                    prices = emptyMap(),
                    priceChangePercents = emptyMap(),
                    tradingStatuses = emptyMap(),
                    sandboxMode = false,
                    onPayIn = {},
                )
            }
        }
        rule.onNodeWithText("Нет позиций").assertExists()
    }

    @Test
    fun `single position hides no-positions text`() {
        rule.setContent {
            TScalpTheme {
                PortfolioAccountBlock(
                    accountKey = key,
                    accountPortfolio = portfolio(listOf(sberPosition)),
                    prices = emptyMap(),
                    priceChangePercents = emptyMap(),
                    tradingStatuses = emptyMap(),
                    sandboxMode = false,
                    onPayIn = {},
                )
            }
        }
        rule.onNodeWithText("Нет позиций").assertDoesNotExist()
    }

    @Test
    fun `two positions hide no-positions text`() {
        rule.setContent {
            TScalpTheme {
                PortfolioAccountBlock(
                    accountKey = key,
                    accountPortfolio = portfolio(listOf(sberPosition, sberPosition)),
                    prices = emptyMap(),
                    priceChangePercents = emptyMap(),
                    tradingStatuses = emptyMap(),
                    sandboxMode = false,
                    onPayIn = {},
                )
            }
        }
        rule.onNodeWithText("Нет позиций").assertDoesNotExist()
    }
}
