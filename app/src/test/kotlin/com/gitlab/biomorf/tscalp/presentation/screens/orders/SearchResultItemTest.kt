package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Share
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

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    @Test
    fun `renders ticker and name`() {
        rule.setContent {
            TScalpTheme {
                SearchResultItem(instrument = sber, onClick = {})
            }
        }
        rule.onNodeWithText("SBER - Сбербанк").assertIsDisplayed()
    }

    @Test
    fun `click invokes callback`() {
        var clicked = false
        rule.setContent {
            TScalpTheme {
                SearchResultItem(instrument = sber, onClick = { clicked = true })
            }
        }
        rule.onNodeWithText("SBER - Сбербанк").performClick()
        assertTrue(clicked)
    }
}
