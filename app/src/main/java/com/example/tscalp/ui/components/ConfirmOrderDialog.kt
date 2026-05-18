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
    orderType: OrderTypeSelection,          // оставлен, он нужен для OrderCard основной сделки
    instrumentType: String,
    executionPrice: Double,
    pairTradingEnabled: Boolean,
    pairedInstrumentTicker: String?,
    pairedInstrumentType: String?,
    pairedMultiplier: String?,
    pairExecPrice: Double,                  // новое: цена исполнения контрсделки
    pairedOrderType: OrderTypeSelection?,   // новое: тип контрсделки (может быть null, если парная отключена)
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!show) return

    val direction = if (pendingDirection == "Покупка") "BUY" else "SELL"

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

                    OrderCard(
                        ticker = pairedInstrumentTicker,
                        direction = pairDirection,
                        orderType = pairedOrderType ?: orderType,  // fallback на основной, если парный не задан
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