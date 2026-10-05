package com.gitlab.biomorf.tscalp

import dagger.hilt.android.AndroidEntryPoint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gitlab.biomorf.tscalp.presentation.MainScreen
import androidx.compose.material3.MaterialTheme
import com.gitlab.biomorf.tscalp.ui.theme.TScalpTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ///MaterialTheme {  // Используем стандартную тему вместо TScalpTheme
            TScalpTheme {
                MainScreen()
            }
        }
    }
}