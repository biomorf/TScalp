package com.gitlab.biomorf.tscalp.presentation.screens.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme

@Composable
fun EmptyPortfolioCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "📊 Портфель пуст",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "У вас нет активных позиций",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "Empty portfolio", showBackground = true, widthDp = 360)
@Composable
private fun EmptyPortfolioCardPreview() {
    TScalpTheme {
        EmptyPortfolioCard()
    }
}
