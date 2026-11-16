package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
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
class SearchResultItemTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `renders ticker and name`() {
        rule.setContent {
            TScalpTheme {
                SearchResultItem(tscalpInstrumentId = "uid-sber", ticker = "SBER", name = "Сбербанк", instrumentType = InstrumentType.SHARE, onClick = {})
            }
        }
        rule.onNodeWithText("SBER - Сбербанк").assertIsDisplayed()
    }

    @Test
    fun `click invokes callback`() {
        var clicked = false
        rule.setContent {
            TScalpTheme {
                SearchResultItem(tscalpInstrumentId = "uid-sber", ticker = "SBER", name = "Сбербанк", instrumentType = InstrumentType.SHARE, onClick = { clicked = true })
            }
        }
        rule.onNodeWithText("SBER - Сбербанк").performClick()
        assertTrue(clicked)
    }
}
