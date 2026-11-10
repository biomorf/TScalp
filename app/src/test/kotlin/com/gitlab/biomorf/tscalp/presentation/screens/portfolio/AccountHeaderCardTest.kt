package com.gitlab.biomorf.tscalp.presentation.screens.portfolio

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gitlab.biomorf.tscalp.domain.models.AccountBalance
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme
import org.junit.Assert.assertTrue
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
class AccountHeaderCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val tinvestKey =
        AccountKey(brokerName = BrokerName.TINVEST, accountId = "2309452027")

    private fun portfolio(balances: List<AccountBalance>) =
        AccountPortfolio(
            accountId = "2309452027",
            brokerName = BrokerName.TINVEST,
            positions = emptyList(),
            balances = balances,
        )

    @Test
    fun `renders broker display name`() {
        rule.setContent {
            TScalpTheme {
                AccountHeaderCard(
                    accountKey = tinvestKey,
                    accountPortfolio = portfolio(emptyList()),
                    sandboxMode = false,
                    onPayIn = {},
                )
            }
        }
        rule.onNodeWithText(BrokerName.TINVEST.displayName).assertExists()
    }

    @Test
    fun `masks account id except last four digits`() {
        rule.setContent {
            TScalpTheme {
                AccountHeaderCard(
                    accountKey = tinvestKey,
                    accountPortfolio = portfolio(emptyList()),
                    sandboxMode = false,
                    onPayIn = {},
                )
            }
        }
        rule.onNodeWithText("••••2027").assertExists()
    }

    @Test
    fun `renders formatted rub balance when present`() {
        rule.setContent {
            TScalpTheme {
                AccountHeaderCard(
                    accountKey = tinvestKey,
                    accountPortfolio =
                        portfolio(
                            listOf(AccountBalance(currency = "rub", money = 100.0, blocked = 0.0)),
                        ),
                    sandboxMode = false,
                    onPayIn = {},
                )
            }
        }
        rule.onNodeWithText("100", substring = true).assertExists()
    }

    @Test
    fun `shows no-data placeholder when balance absent`() {
        rule.setContent {
            TScalpTheme {
                AccountHeaderCard(
                    accountKey = tinvestKey,
                    accountPortfolio = portfolio(emptyList()),
                    sandboxMode = false,
                    onPayIn = {},
                )
            }
        }
        rule.onNodeWithText("нет данных").assertExists()
    }

    @Test
    fun `sandbox TInvest shows pay-in button that emits callback`() {
        var clicked = false
        rule.setContent {
            TScalpTheme {
                AccountHeaderCard(
                    accountKey = tinvestKey,
                    accountPortfolio = portfolio(emptyList()),
                    sandboxMode = true,
                    onPayIn = { clicked = true },
                )
            }
        }
        rule.onNodeWithText("Пополнить").performClick()
        assertTrue(clicked)
    }
}
