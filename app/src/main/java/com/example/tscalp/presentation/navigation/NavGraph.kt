package com.example.tscalp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.tscalp.presentation.screens.orders.OrdersScreen
import com.example.tscalp.presentation.screens.portfolio.PortfolioScreen
import com.example.tscalp.presentation.screens.settings.SettingsScreen

// Константы маршрутов как строки
object NavRoutes {
    const val ORDERS = "orders"
    const val PORTFOLIO = "portfolio"
    const val SETTINGS = "settings"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.ORDERS,
        modifier = modifier
    ) {
        composable(NavRoutes.ORDERS) {
            OrdersScreen()
        }
        composable(NavRoutes.PORTFOLIO) {
            PortfolioScreen()
        }
        composable(NavRoutes.SETTINGS) {
            SettingsScreen()
        }
    }
}