package com.gitlab.biomorf.tscalp.presentation.screens.portfolio

import androidx.compose.ui.test.assertExists
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
class ApiNotInitializedCardTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `renders warning and hint`() {
        rule.setContent {
            TScalpTheme {
                ApiNotInitializedCard()
            }
        }
        rule.onNodeWithText("⚠️ API не подключен").assertExists()
        rule.onNodeWithText("Перейдите в Настройки и введите токен доступа").assertExists()
    }
}
