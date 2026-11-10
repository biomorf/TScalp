package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
class BcsTokenHelpCardTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `renders BCS-specific help content`() {
        rule.setContent {
            TScalpTheme {
                BcsTokenHelpCard()
            }
        }
        rule.onNodeWithText("БКС Мир", substring = true).assertExists()
    }
}
