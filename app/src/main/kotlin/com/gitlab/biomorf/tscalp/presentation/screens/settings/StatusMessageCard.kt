package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme

@Composable
internal fun StatusMessageCard(
    message: String,
    isError: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
            ),
    ) {
        Text(message, modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "StatusMessage — success", showBackground = true, widthDp = 360)
@Composable
private fun StatusMessageCardSuccessPreview() {
    TScalpTheme {
        StatusMessageCard(message = "Saved successfully", isError = false)
    }
}

@Preview(name = "StatusMessage — error", showBackground = true, widthDp = 360)
@Composable
private fun StatusMessageCardErrorPreview() {
    TScalpTheme {
        StatusMessageCard(message = "Something went wrong", isError = true)
    }
}
