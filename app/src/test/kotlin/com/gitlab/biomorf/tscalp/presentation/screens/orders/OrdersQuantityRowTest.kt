package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
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
class OrdersQuantityRowTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `increment button emits quantity plus one`() {
        var captured: String? = null
        rule.setContent {
            TScalpTheme {
                OrdersQuantityRow(
                    quantity = "5",
                    quantityAsLong = 5L,
                    costOverlay = null,
                    canDecrement = true,
                    canIncrement = true,
                    onQuantityChanged = { captured = it },
                )
            }
        }
        rule.onNodeWithContentDescription("Увеличить").performClick()
        assertEquals("6", captured)
    }

    @Test
    fun `decrement button emits quantity minus one`() {
        var captured: String? = null
        rule.setContent {
            TScalpTheme {
                OrdersQuantityRow(
                    quantity = "5",
                    quantityAsLong = 5L,
                    costOverlay = null,
                    canDecrement = true,
                    canIncrement = true,
                    onQuantityChanged = { captured = it },
                )
            }
        }
        rule.onNodeWithContentDescription("Уменьшить").performClick()
        assertEquals("4", captured)
    }

    @Test
    fun `increment button is disabled when canIncrement is false`() {
        rule.setContent {
            TScalpTheme {
                OrdersQuantityRow(
                    quantity = "5",
                    quantityAsLong = 5L,
                    costOverlay = null,
                    canDecrement = true,
                    canIncrement = false,
                    onQuantityChanged = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Увеличить").assertIsNotEnabled()
    }
}
