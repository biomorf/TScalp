package com.gitlab.biomorf.tscalp.presentation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI smoke: Robolectric-hosted (JVM, no emulator). Runs in
 * `fast-check` (`testDebugUnitTest`). Covers text semantics + a callback
 * — the baseline for the Compose UI test infra (U0 of the Compose UI
 * umbrella).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrderCardTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `renders ticker direction orderType status and quantity`() {
        rule.setContent {
            TScalpTheme {
                OrderCard(
                    ticker = "SBER",
                    direction = "BUY",
                    orderType = OrderTypeSelection.Market,
                    status = "NEW",
                    quantity = 10,
                    price = 250.5,
                    currency = "RUB",
                    instrumentType = InstrumentType.SHARE,
                )
            }
        }
        rule.onNodeWithText("SBER").assertIsDisplayed()
        rule.onNodeWithText("Покупка").assertIsDisplayed()
        rule.onNodeWithText("по рыночной цене").assertIsDisplayed()
        rule.onNodeWithText("Новая").assertIsDisplayed()
        rule.onNodeWithText("10 лотов").assertIsDisplayed()
    }

    @Test
    fun `onCancel invokes callback`() {
        var called = false
        rule.setContent {
            TScalpTheme {
                OrderCard(
                    ticker = "GAZP",
                    direction = "SELL",
                    orderType = OrderTypeSelection.Limit,
                    status = "NEW",
                    quantity = 5,
                    price = 150.0,
                    currency = "RUB",
                    instrumentType = InstrumentType.SHARE,
                    onCancel = { called = true },
                )
            }
        }
        rule.onNodeWithContentDescription("Отменить заявку").performClick()
        rule.waitForIdle()
        assertTrue(called)
    }
}
