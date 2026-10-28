package com.gitlab.biomorf.tscalp.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme
import com.gitlab.biomorf.tscalp.presentation.util.DOMESTIC_CURRENCY

@Composable
fun ConfirmOrderDialog(
    show: Boolean,
    ticker: String,
    quantity: Long,
    pendingDirection: String,
    orderType: OrderTypeSelection, // оставлен, он нужен для OrderCard основной сделки
    instrumentType: String,
    executionPrice: Double,
    pairTradingEnabled: Boolean,
    pairedInstrumentTicker: String?,
    pairedInstrumentType: String?,
    pairedMultiplier: String?,
    pairExecPrice: Double, // новое: цена исполнения контрсделки
    pairedOrderType: OrderTypeSelection?, // новое: тип контрсделки (может быть null, если парная отключена)
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
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
                    currency = DOMESTIC_CURRENCY,
                    totalCost = executionPrice * quantity,
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
                        orderType = pairedOrderType ?: orderType, // fallback на основной, если парный не задан
                        status = null,
                        quantity = pairedQty,
                        price = pairExecPrice,
                        instrumentType = pairedInstrumentType ?: "",
                        currency = DOMESTIC_CURRENCY,
                        totalCost = pairExecPrice * pairedQty,
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
        },
    )
}

@Preview(name = "Market BUY — single", showBackground = true, widthDp = 400)
@Composable
private fun ConfirmOrderDialogMarketPreview() {
    TScalpTheme {
        ConfirmOrderDialog(
            show = true,
            ticker = "SBER",
            quantity = 10,
            pendingDirection = "BUY",
            orderType = OrderTypeSelection.Market,
            instrumentType = "share",
            executionPrice = 250.5,
            pairTradingEnabled = false,
            pairedInstrumentTicker = null,
            pairedInstrumentType = null,
            pairedMultiplier = null,
            pairExecPrice = 0.0,
            pairedOrderType = null,
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "Limit SELL — single", showBackground = true, widthDp = 400)
@Composable
private fun ConfirmOrderDialogLimitPreview() {
    TScalpTheme {
        ConfirmOrderDialog(
            show = true,
            ticker = "GAZP",
            quantity = 20,
            pendingDirection = "SELL",
            orderType = OrderTypeSelection.Limit,
            instrumentType = "share",
            executionPrice = 150.0,
            pairTradingEnabled = false,
            pairedInstrumentTicker = null,
            pairedInstrumentType = null,
            pairedMultiplier = null,
            pairExecPrice = 0.0,
            pairedOrderType = null,
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "Paired trade — futures + share", showBackground = true, widthDp = 400)
@Composable
private fun ConfirmOrderDialogPairedPreview() {
    TScalpTheme {
        ConfirmOrderDialog(
            show = true,
            ticker = "SiU4",
            quantity = 1,
            pendingDirection = "BUY",
            orderType = OrderTypeSelection.Limit,
            instrumentType = "futures",
            executionPrice = 85000.0,
            pairTradingEnabled = true,
            pairedInstrumentTicker = "SBER",
            pairedInstrumentType = "share",
            pairedMultiplier = "10",
            pairExecPrice = 250.5,
            pairedOrderType = OrderTypeSelection.Market,
            onDismiss = {},
            onConfirm = {},
        )
    }
}
