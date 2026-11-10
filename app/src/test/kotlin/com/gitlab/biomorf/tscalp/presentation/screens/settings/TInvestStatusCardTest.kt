package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class TInvestStatusCardTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `disconnected with canConnect shows enabled connect`() {
        rule.setContent {
            TScalpTheme {
                TInvestStatusCard(
                    isConnected = false,
                    canConnect = true,
                    onConnect = {},
                    onDisconnect = {},
                )
            }
        }
        rule.onNodeWithText("Подключиться").assertIsEnabled()
    }

    @Test
    fun `disconnected without canConnect shows disabled connect`() {
        rule.setContent {
            TScalpTheme {
                TInvestStatusCard(
                    isConnected = false,
                    canConnect = false,
                    onConnect = {},
                    onDisconnect = {},
                )
            }
        }
        rule.onNodeWithText("Подключиться").assertIsNotEnabled()
    }

    @Test
    fun `connected shows disconnect that invokes callback`() {
        var fired = false
        rule.setContent {
            TScalpTheme {
                TInvestStatusCard(
                    isConnected = true,
                    canConnect = false,
                    onConnect = {},
                    onDisconnect = { fired = true },
                )
            }
        }
        rule.onNodeWithText("Отключить").performClick()
        assertTrue(fired)
    }
}
