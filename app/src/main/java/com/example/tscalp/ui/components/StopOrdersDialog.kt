package com.example.tscalp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.tscalp.domain.models.OrderListItem
import com.example.tscalp.presentation.screens.orders.StopOrdersViewModel
import com.example.tscalp.util.formatCurrency

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
                val scrollState = rememberScrollState()
                val canScroll = remember { derivedStateOf { scrollState.maxValue > 0 } }

                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
                        state.orders.forEach { order ->
                            OrderListItemRow(
                                order = order,
                                onCancel = { viewModel.cancelOrder(order) }
                            )
                            HorizontalDivider()
                        }
                    }

                    // Вертикальный индикатор прокрутки
                    if (canScroll.value) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                                .width(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color.Gray.copy(alpha = 0.5f))
                        )
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
    // Цвет типа ордера
    val typeColor = when (order.type) {
        "LIMIT" -> Color(0xFF1565C0)
        "MARKET" -> Color(0xFFEF6C00)
        "STOP_LOSS" -> Color(0xFFC62828)
        "TAKE_PROFIT" -> Color(0xFF2E7D32)
        "STOP_LIMIT" -> Color(0xFF6A1B9A)
        else -> Color.Gray
    }

    // Цвет цены в зависимости от направления
    val priceColor = when (order.direction) {
        "BUY" -> Color(0xFF2E7D32)
        "SELL" -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${order.ticker} ${order.direction} ${order.quantity} лотов")

            // Тип ордера цветной
            Text(
                text = "Тип: ${order.type}",
                style = MaterialTheme.typography.bodySmall,
                color = typeColor
            )

            // Цена и стоимость с учётом вида заявки
            when {
                order.type == "STOP_LIMIT" -> {
                    val trigger = order.stopPrice ?: 0.0
                    Text("≈ Триггер-цена: ${formatCurrency(trigger)}", style = MaterialTheme.typography.bodySmall)
                    Text("Лимитная цена: ${formatCurrency(order.price)}", style = MaterialTheme.typography.bodySmall, color = priceColor)
                    Text("Общая стоимость: ${formatCurrency(order.price * order.quantity)}", style = MaterialTheme.typography.bodySmall)
                }
                order.isStopOrder -> {
                    val approx = "≈"
                    Text("$approx Цена за лот: $approx${formatCurrency(order.price)}", style = MaterialTheme.typography.bodySmall, color = priceColor)
                    Text("Общая стоимость: $approx${formatCurrency(order.price * order.quantity)}", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    // Обычные ордера
                    Text("Цена за лот: ${formatCurrency(order.price)}", style = MaterialTheme.typography.bodySmall, color = priceColor)
                    Text("Общая стоимость: ${formatCurrency(order.price * order.quantity)}", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Статус ордера
            Text("Статус: ${order.status}", style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Delete, contentDescription = "Отменить заявку")
        }
    }
}