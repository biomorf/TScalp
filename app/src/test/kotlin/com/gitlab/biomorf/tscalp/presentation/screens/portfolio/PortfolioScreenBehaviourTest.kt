package com.gitlab.biomorf.tscalp.presentation.screens.portfolio

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme
import com.gitlab.biomorf.tscalp.testutil.HiltTestActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PortfolioScreenBehaviourTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Test
    fun `renders ApiNotInitializedCard when API not initialized (smoke)`() {
        composeRule.setContent {
            TScalpTheme {
                PortfolioScreen()
            }
        }
        composeRule.onNodeWithText("API не подключен", substring = true).assertExists()
    }
}
