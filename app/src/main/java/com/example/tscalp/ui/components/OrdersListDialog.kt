package com.example.tscalp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.tscalp.ui.components.OrdersListViewModel
import com.example.tscalp.util.TradePhrases
import com.example.tscalp.domain.models.OrderListItem


@Composable
fun OrdersListDialog(
    viewModel: OrdersListViewModel,   // временно, пока ViewModel не переименована
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadOrders()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Список заявок") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())   // ← скролл сохранён
            ) {
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.orders.isEmpty()) {
                    Text("Нет активных заявок")
                } else {
                    uiState.orders.forEach { order ->
                        OrderCard(
                            ticker = order.ticker,
                            direction = order.direction,
                            orderType = TradePhrases.stringToOrderType(order.type),
                            status = order.status,
                            quantity = order.quantity,
                            price = order.price,
                            instrumentType = order.instrumentType,
                            totalCost = if (order.stopPrice != null) order.stopPrice * order.quantity else null,
                            onCancel = {
                                viewModel.cancelOrder(order)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

//
//@Composable
//fun OrderListItemRow(order: OrderListItem, onCancel: () -> Unit) {
//    // Цвет типа ордера
//    val typeColor = when (order.type) {
//        "LIMIT" -> Color(0xFF1565C0)
//        "MARKET" -> Color(0xFFEF6C00)
//        "STOP_LOSS" -> Color(0xFFC62828)
//        "TAKE_PROFIT" -> Color(0xFF2E7D32)
//        "STOP_LIMIT" -> Color(0xFF6A1B9A)
//        else -> Color.Gray
//    }
//
//    // Цвет цены в зависимости от направления
//    val priceColor = when (order.direction) {
//        "BUY" -> Color(0xFF2E7D32)
//        "SELL" -> Color(0xFFC62828)
//        else -> MaterialTheme.colorScheme.onSurface
//    }
//
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(vertical = 4.dp),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        Column(modifier = Modifier.weight(1f)) {
//            Text("${order.tscalpInstrumentId} ${order.direction} ${order.quantity} лотов")
//
//            // Тип ордера цветной
//            Text(
//                text = "Тип: ${order.type}",
//                style = MaterialTheme.typography.bodySmall,
//                color = typeColor
//            )
//
//            // Цена и стоимость с учётом вида заявки
//            when {
//                order.type == "STOP_LIMIT" -> {
//                    val trigger = order.stopPrice ?: 0.0
//                    Text("≈ Триггер-цена: ${formatPrice(trigger, order.instrumentType)}", style = MaterialTheme.typography.bodySmall)
//                    Text("Лимитная цена: ${formatPrice(order.price, order.instrumentType)}", style = MaterialTheme.typography.bodySmall, color = priceColor)
//                    Text("Общая стоимость: ${formatPrice(order.price * order.quantity, order.instrumentType)}", style = MaterialTheme.typography.bodySmall)
//                }
//                order.isStopOrder -> {
//                    val approx = "≈"
//                    Text("$approx Цена за лот: $approx${formatPrice(order.price, order.instrumentType)}", style = MaterialTheme.typography.bodySmall, color = priceColor)
//                    Text("Общая стоимость: $approx${formatPrice(order.price * order.quantity, order.instrumentType)}", style = MaterialTheme.typography.bodySmall)
//                }
//                else -> {
//                    // Обычные ордера
//                    Text("Цена за лот: ${formatPrice(order.price, order.instrumentType)}", style = MaterialTheme.typography.bodySmall, color = priceColor)
//                    Text("Общая стоимость: ${formatPrice(order.price * order.quantity, order.instrumentType)}", style = MaterialTheme.typography.bodySmall)
//                }
//            }
//
//            // Статус ордера
//            Text("Статус: ${order.status}", style = MaterialTheme.typography.bodySmall)
//        }
//        IconButton(onClick = onCancel) {
//            Icon(Icons.Default.Delete, contentDescription = "Отменить заявку")
//        }
//    }
//}