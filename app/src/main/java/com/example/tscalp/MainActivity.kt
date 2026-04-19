package com.example.tscalp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.tscalp.presentation.screens.orders.OrdersScreen

import com.example.tscalp.presentation.MainScreen
import com.example.tscalp.ui.theme.TScalpTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TScalpTheme {
                MainScreen()
                }
            }
        }
    }
}
