package com.example.tscalp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.tscalp.presentation.screens.orders.OrdersScreen
import com.example.tscalp.presentation.screens.portfolio.PortfolioScreen
import com.example.tscalp.presentation.screens.settings.SettingsScreen
import kotlinx.serialization.Serializable

// Типобезопасные маршруты
sealed class NavRoute {
    @Serializable
    data object Orders : NavRoute()

    @Serializable
    data object Portfolio : NavRoute()

    @Serializable
    data object Settings : NavRoute()
}

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavRoute.Orders,
        modifier = modifier
    ) {
        composable<NavRoute.Orders> {
            OrdersScreen()
        }
        composable<NavRoute.Portfolio> {
            PortfolioScreen()
        }
        composable(NavRoute.Settings) {
            SettingsScreen()
        }
    }
}