package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class OrdersTypeChipsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `click Market chip invokes callback with Market`() {
        var captured: OrderTypeSelection? = null
        rule.setContent {
            TScalpTheme {
                OrdersTypeChips(
                    orderType = OrderTypeSelection.Limit,
                    onOrderTypeChanged = { captured = it },
                )
            }
        }
        rule.onNodeWithText("Рын.").performClick()
        org.junit.Assert.assertEquals(OrderTypeSelection.Market, captured)
    }

    @Test
    fun `click Limit chip invokes callback with Limit`() {
        var captured: OrderTypeSelection? = null
        rule.setContent {
            TScalpTheme {
                OrdersTypeChips(
                    orderType = OrderTypeSelection.Market,
                    onOrderTypeChanged = { captured = it },
                )
            }
        }
        rule.onNodeWithText("Лим.").performClick()
        org.junit.Assert.assertEquals(OrderTypeSelection.Limit, captured)
    }
}
