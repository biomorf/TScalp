package com.example.tscalp.presentation.ui.theme  // или com.example.tscalp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun TScalpTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        content = content
    )
}