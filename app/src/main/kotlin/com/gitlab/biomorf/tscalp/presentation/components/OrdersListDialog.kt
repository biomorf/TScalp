package com.gitlab.biomorf.tscalp.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.gitlab.biomorf.tscalp.domain.models.Order
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrdersListViewModel
import com.gitlab.biomorf.tscalp.presentation.util.DOMESTIC_CURRENCY

@Composable
fun OrdersListDialog(
    onDismiss: () -> Unit,
    viewModel: OrdersListViewModel = hiltViewModel(),
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
                modifier =
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()), // ← скролл сохранён
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
                            orderType = order.orderType,
                            status = order.status,
                            quantity = order.quantity,
                            price = order.price,
                            instrumentType = order.instrumentType,
                            currency = DOMESTIC_CURRENCY,
                            totalCost = (order as? Order.Stop)?.let { it.stopPrice * it.quantity },
                            onCancel = {
                                viewModel.cancelOrder(order)
                            },
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
        },
    )
}
