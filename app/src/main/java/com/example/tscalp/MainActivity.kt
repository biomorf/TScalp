package com.example.tscalp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.tscalp.presentation.MainScreen
import androidx.compose.material3.MaterialTheme
import com.example.tscalp.ui.theme.TScalpTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            //MaterialTheme {  // Используем стандартную тему вместо TScalpTheme
            TScalpTheme {
                MainScreen()
            }
        }
    }
}