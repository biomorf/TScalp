package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.gitlab.biomorf.tscalp.di.RegistryModule
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme
import com.gitlab.biomorf.tscalp.testutil.FakeBrokerApi
import com.gitlab.biomorf.tscalp.testutil.FakeBrokerRegistry
import com.gitlab.biomorf.tscalp.testutil.HiltTestActivity
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@UninstallModules(RegistryModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class OrdersScreenBehaviourTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @BindValue @JvmField
    val brokerRegistry: BrokerRegistry =
        FakeBrokerRegistry(
            anyInitialized = false,
            brokers = mapOf(BrokerName.TINVEST to FakeBrokerApi(BrokerName.TINVEST)),
        )

    private fun setInitialized(initialized: Boolean) {
        (brokerRegistry as FakeBrokerRegistry).setAnyInitialized(initialized)
    }

    @Test
    fun `renders ApiNotInitializedCard when API not initialized (smoke)`() {
        composeRule.setContent {
            TScalpTheme {
                OrdersScreen()
            }
        }
        composeRule.onNodeWithText("API не подключен", substring = true).assertExists()
    }

    @Test
    fun `renders header when API is initialized`() {
        setInitialized(true)
        composeRule.setContent {
            TScalpTheme {
                OrdersScreen()
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Выставление заявки").assertExists()
    }

    @Test
    fun `hides ApiNotInitializedCard when API is initialized`() {
        setInitialized(true)
        composeRule.setContent {
            TScalpTheme {
                OrdersScreen()
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("API не подключен", substring = true).assertDoesNotExist()
    }

    @Test
    fun `renders BUY button when API is initialized`() {
        setInitialized(true)
        composeRule.setContent {
            TScalpTheme {
                OrdersScreen()
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("КУПИТЬ").assertExists()
    }

    @Test
    fun `renders SELL button when API is initialized`() {
        setInitialized(true)
        composeRule.setContent {
            TScalpTheme {
                OrdersScreen()
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ПРОДАТЬ").assertExists()
    }

    @Test
    fun `renders pair trading switch when API is initialized`() {
        setInitialized(true)
        composeRule.setContent {
            TScalpTheme {
                OrdersScreen()
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Парная торговля").assertExists()
    }
}
