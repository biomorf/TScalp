package com.gitlab.biomorf.tscalp.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme

@Composable
fun ApiNotInitializedCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠️ API не подключен", style = MaterialTheme.typography.titleMedium)
            Text(
                "Перейдите в Настройки и введите токен доступа",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "API not initialized", showBackground = true, widthDp = 360)
@Composable
private fun ApiNotInitializedCardPreview() {
    TScalpTheme {
        ApiNotInitializedCard()
    }
}
