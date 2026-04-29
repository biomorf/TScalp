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
import androidx.compose.ui.unit.dp
import com.example.tscalp.domain.models.OrderListItem
import com.example.tscalp.presentation.screens.orders.StopOrdersViewModel

@Composable
fun StopOrdersDialog(
    viewModel: StopOrdersViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadOrders()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Активные заявки") },
        text = {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.orders.isEmpty()) {
                Text("Нет активных заявок")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(state.orders) { order ->
                        OrderListItemRow(
                            order = order,
                            onCancel = { viewModel.cancelOrder(order) }
                        )
                        HorizontalDivider()
                    }
                }
            }
            state.statusMessage?.let {
                Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
fun OrderListItemRow(order: OrderListItem, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${order.ticker} ${order.direction} ${order.quantity} лотов")
            Text("Цена: ${order.price}", style = MaterialTheme.typography.bodySmall)
            if (order.stopPrice != null) {
                Text("Стоп: ${order.stopPrice}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Тип: ${order.type} | Статус: ${order.status}", style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Delete, contentDescription = "Отменить заявку")
        }
    }
}