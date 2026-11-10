package com.gitlab.biomorf.tscalp.presentation.screens.settings

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
class CloseSandboxAccountDialogTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `confirm button invokes onConfirm`() {
        var confirmed = false
        rule.setContent {
            TScalpTheme {
                CloseSandboxAccountDialog(
                    accountName = "Test",
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("Закрыть").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun `dismiss button invokes onDismiss`() {
        var dismissed = false
        rule.setContent {
            TScalpTheme {
                CloseSandboxAccountDialog(
                    accountName = "Test",
                    onConfirm = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        rule.onNodeWithText("Отмена").performClick()
        assertTrue(dismissed)
    }
}
