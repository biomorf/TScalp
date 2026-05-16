package com.example.tscalp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tscalp.domain.models.OrderDirection
import com.example.tscalp.domain.models.OrderTypeSelection
import com.example.tscalp.domain.usecases.PairOrderMapper
import com.example.tscalp.util.TradePhrases

@Composable
fun ConfirmOrderDialog(
    show: Boolean,
    ticker: String,
    quantity: Long,
    pendingDirection: String,
    orderType: OrderTypeSelection,
    instrumentType: String,
    executionPrice: Double,
    pairTradingEnabled: Boolean,
    pairedInstrumentTicker: String?,
    pairedInstrumentType: String?,
    pairedMultiplier: String?,
    pairCurrentPrice: Double?,
    limitPrice: String?,
    stopPrice: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!show) return

    val direction = if (pendingDirection == "Покупка") "BUY" else "SELL"

    // Вычисляем тип контрсделки через PairOrderMapper
    val primaryPrice = when (orderType) {
        OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> limitPrice?.toDoubleOrNull()
        OrderTypeSelection.StopLoss, OrderTypeSelection.TakeProfit -> stopPrice?.toDoubleOrNull()
        else -> null
    }
    val pairedSpec = PairOrderMapper.map(orderType, OrderDirection.valueOf(direction), primaryPrice)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подтверждение сделки") },
        text = {
            Column {
                Text("Вы собираетесь совершить сделку:", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                OrderCard(
                    ticker = ticker,
                    direction = direction,
                    orderType = orderType,
                    status = null,
                    quantity = quantity,
                    price = executionPrice,
                    instrumentType = instrumentType,
                    totalCost = executionPrice * quantity
                )

                if (pairTradingEnabled && pairedInstrumentTicker != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("и парную сделку:", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))

                    val pairedQty = (quantity * (pairedMultiplier?.toDoubleOrNull() ?: 1.0)).toLong()
                    val pairDirection = if (direction == "BUY") "SELL" else "BUY"
                    val pairExecPrice = when (orderType) {
                        OrderTypeSelection.Limit, OrderTypeSelection.StopLimit -> executionPrice
                        else -> pairCurrentPrice ?: 0.0
                    }

                    OrderCard(
                        ticker = pairedInstrumentTicker,
                        direction = pairDirection,
                        orderType = pairedSpec.orderType, // правильный тип контрсделки
                        status = null,
                        quantity = pairedQty,
                        price = pairExecPrice,
                        instrumentType = pairedInstrumentType ?: "",
                        totalCost = pairExecPrice * pairedQty
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Подтвердить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}