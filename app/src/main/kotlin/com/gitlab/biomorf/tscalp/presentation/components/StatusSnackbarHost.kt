package com.gitlab.biomorf.tscalp.presentation.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.presentation.util.StatusEvent
import kotlinx.coroutines.flow.Flow

@Composable
private fun StatusEventEffect(
    snackbarHostState: SnackbarHostState,
    status: Flow<StatusEvent>,
) {
    LaunchedEffect(Unit) {
        status.collect { event ->
            snackbarHostState.showSnackbar(
                StatusSnackbarVisuals(
                    message = event.message,
                    actionLabel = if (event.isError) "OK" else null,
                    withDismissAction = false,
                    duration = if (event.isError) SnackbarDuration.Indefinite else SnackbarDuration.Short,
                    isError = event.isError,
                ),
            )
        }
    }
}

private data class StatusSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val withDismissAction: Boolean,
    override val duration: SnackbarDuration,
    val isError: Boolean,
) : SnackbarVisuals

@Composable
private fun BoxScope.OrdersSnackbarHost(
    snackbarHostState: SnackbarHostState,
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp),
    ) { data ->
        val isError = (data.visuals as? StatusSnackbarVisuals)?.isError ?: false
        Snackbar(
            containerColor =
                if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
            contentColor =
                if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
            action = {
                val label = data.visuals.actionLabel
                if (label != null) {
                    TextButton(onClick = { data.dismiss() }) {
                        Text(label)
                    }
                }
            },
        ) {
            Text(data.visuals.message)
        }
    }
}
