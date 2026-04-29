package com.example.tscalp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tscalp.domain.models.StopOrderUi
import com.example.tscalp.presentation.screens.orders.StopOrdersViewModel

@Composable
fun StopOrdersDialog(
    viewModel: StopOrdersViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Загружаем список при первом открытии
    LaunchedEffect(Unit) {
        viewModel.loadStopOrders()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Стоп‑заявки") },
        text = {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.orders.isEmpty() -> {
                    Text(uiState.statusMessage ?: "Нет активных стоп‑заявок")
                }
                else -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(uiState.orders) { order ->
                            StopOrderRow(
                                order = order,
                                onCancel = { viewModel.cancelStopOrder(order.stopOrderId) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun StopOrderRow(
    order: StopOrderUi,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = order.ticker,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Стоп: ${order.stopPrice} | ${order.direction}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "ID: ${order.stopOrderId.take(8)}…",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Отменить стоп‑заявку",
                    tint = Color(0xFFC62828)
                )
            }
        }
    }
}