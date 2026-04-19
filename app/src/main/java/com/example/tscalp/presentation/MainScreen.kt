package com.example.tscalp.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.tscalp.presentation.navigation.NavGraph
import com.example.tscalp.presentation.navigation.NavRoute

data class BottomNavItem(
    val route: NavRoute,
    val title: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        route = NavRoute.Orders,
        title = "Заявки",
        icon = Icons.Default.List
    ),
    BottomNavItem(
        route = NavRoute.Portfolio,
        title = "Портфель",
        icon = Icons.Default.ShowChart
    ),
    BottomNavItem(
        route = NavRoute.Settings,
        title = "Настройки",
        icon = Icons.Default.Settings
    )
)

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            TScalpBottomNavigation(navController = navController)
        }
    ) { paddingValues ->
        NavGraph(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@Composable
fun TScalpBottomNavigation(navController: NavHostController) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        bottomNavItems.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any {
                it.hasRoute(item.route::class)
            } == true

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = { Text(item.title) },
                selected = isSelected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}