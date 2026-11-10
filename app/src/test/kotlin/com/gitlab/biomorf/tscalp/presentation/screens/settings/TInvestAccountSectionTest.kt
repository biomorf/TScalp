package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccountType
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme
import org.junit.Assert.assertEquals
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
class TInvestAccountSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val accounts =
        listOf(
            BrokerAccount(id = "1", name = "Test Account", type = BrokerAccountType.BROKER),
        )

    @Test
    fun `closed state shows placeholder`() {
        rule.setContent {
            TScalpTheme {
                TInvestAccountSection(
                    availableAccounts = emptyList(),
                    defaultAccountId = "",
                    sandboxMode = false,
                    isRefreshing = false,
                    accountExpanded = false,
                    onAccountExpandedChange = {},
                    onAccountSelected = {},
                    onOpenSandbox = {},
                    onCloseSandboxClick = {},
                )
            }
        }
        rule.onNodeWithText("Выберите счёт").assertExists()
    }

    @Test
    fun `expanded state shows account items`() {
        rule.setContent {
            TScalpTheme {
                TInvestAccountSection(
                    availableAccounts = accounts,
                    defaultAccountId = "",
                    sandboxMode = false,
                    isRefreshing = false,
                    accountExpanded = true,
                    onAccountExpandedChange = {},
                    onAccountSelected = {},
                    onOpenSandbox = {},
                    onCloseSandboxClick = {},
                )
            }
        }
        rule.onNodeWithText("Test Account (1)").assertExists()
    }

    @Test
    fun `clicking account invokes onAccountSelected`() {
        var selected: String? = null
        rule.setContent {
            TScalpTheme {
                TInvestAccountSection(
                    availableAccounts = accounts,
                    defaultAccountId = "",
                    sandboxMode = false,
                    isRefreshing = false,
                    accountExpanded = true,
                    onAccountExpandedChange = {},
                    onAccountSelected = { selected = it },
                    onOpenSandbox = {},
                    onCloseSandboxClick = {},
                )
            }
        }
        rule.onNodeWithText("Test Account (1)").performClick()
        assertEquals("1", selected)
    }
}
