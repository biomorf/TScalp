package com.gitlab.biomorf.tscalp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gitlab.biomorf.tscalp.presentation.MainScreen
import com.gitlab.biomorf.tscalp.ui.theme.TScalpTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // /MaterialTheme {  // Используем стандартную тему вместо TScalpTheme
            TScalpTheme {
                MainScreen()
            }
        }
    }
}
