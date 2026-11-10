package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
class SettingsHubListTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `selecting connection fires broker`() {
        var captured: String? = null
        rule.setContent {
            TScalpTheme {
                SettingsHubList(onSelect = { captured = it })
            }
        }
        rule.onNodeWithText("Подключение").performClick()
        assertEquals("broker", captured)
    }

    @Test
    fun `selecting trading fires trade`() {
        var captured: String? = null
        rule.setContent {
            TScalpTheme {
                SettingsHubList(onSelect = { captured = it })
            }
        }
        rule.onNodeWithText("Торговля").performClick()
        assertEquals("trade", captured)
    }

    @Test
    fun `selecting info fires info`() {
        var captured: String? = null
        rule.setContent {
            TScalpTheme {
                SettingsHubList(onSelect = { captured = it })
            }
        }
        rule.onNodeWithText("Информация").performClick()
        assertEquals("info", captured)
    }
}
