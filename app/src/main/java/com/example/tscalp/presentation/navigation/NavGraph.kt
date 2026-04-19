package com.example.tscalp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.tscalp.presentation.screens.orders.OrdersScreen
import com.example.tscalp.presentation.screens.portfolio.PortfolioScreen
import com.example.tscalp.presentation.screens.settings.SettingsScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "orders") {
        composable("orders") { OrdersScreen() }
        composable("portfolio") { PortfolioScreen() }
        composable("settings") { SettingsScreen() }
    }
}