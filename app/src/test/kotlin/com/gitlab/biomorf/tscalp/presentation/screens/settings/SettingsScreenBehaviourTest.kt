package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class SettingsScreenBehaviourTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Test
    fun `hub renders three section items`() {
        composeRule.setContent {
            TScalpTheme {
                SettingsScreen()
            }
        }
        composeRule.onNodeWithText("Подключение").assertExists()
        composeRule.onNodeWithText("Торговля").assertExists()
        composeRule.onNodeWithText("Информация").assertExists()
    }

    @Test
    fun `hub to Connection opens TInvest panel`() {
        composeRule.setContent {
            TScalpTheme {
                SettingsScreen()
            }
        }
        composeRule.onNodeWithText("Подключение").performClick()
        composeRule.onNodeWithText("Статус API").assertExists()
        composeRule.onNodeWithText("Токен доступа").assertExists()
    }

    @Test
    fun `hub to Trading opens trade settings`() {
        composeRule.setContent {
            TScalpTheme {
                SettingsScreen()
            }
        }
        composeRule.onNodeWithText("Торговля").performClick()
        composeRule.onNodeWithText("Настройки торговли").assertExists()
        composeRule.onNodeWithText("Подтверждение заявок").assertExists()
    }

    @Test
    fun `hub to Info opens about section`() {
        composeRule.setContent {
            TScalpTheme {
                SettingsScreen()
            }
        }
        composeRule.onNodeWithText("Информация").performClick()
        composeRule.onNodeWithText("TScalp").assertExists()
        composeRule.onNodeWithText("Версия:", substring = true).assertExists()
    }

    @Test
    fun `connection shows all three broker tabs`() {
        composeRule.setContent {
            TScalpTheme {
                SettingsScreen()
            }
        }
        composeRule.onNodeWithText("Подключение").performClick()
        composeRule.onNodeWithText("Т-Инвестиции").assertExists()
        composeRule.onNodeWithText("БКС").assertExists()
        composeRule.onNodeWithText("Финам").assertExists()
    }

    @Test
    fun `bcs tab renders access rights section`() {
        composeRule.setContent {
            TScalpTheme {
                SettingsScreen()
            }
        }
        composeRule.onNodeWithText("Подключение").performClick()
        composeRule.onNodeWithText("БКС").performClick()
        composeRule.onNodeWithText("Права доступа").assertExists()
    }

    @Test
    fun `finam tab renders finam token placeholder`() {
        composeRule.setContent {
            TScalpTheme {
                SettingsScreen()
            }
        }
        composeRule.onNodeWithText("Подключение").performClick()
        composeRule.onNodeWithText("Финам").performClick()
        composeRule.onNodeWithText("Finam", substring = true).assertExists()
    }

    @Test
    fun `tinvest panel has sandbox toggle`() {
        composeRule.setContent {
            TScalpTheme {
                SettingsScreen()
            }
        }
        composeRule.onNodeWithText("Подключение").performClick()
        composeRule.onNodeWithText("Режим песочницы").assertExists()
    }

    @Test
    fun `connection shows back navigation button`() {
        composeRule.setContent {
            TScalpTheme {
                SettingsScreen()
            }
        }
        composeRule.onNodeWithText("Подключение").performClick()
        composeRule.onNodeWithContentDescription("Назад").assertExists()
    }
}
