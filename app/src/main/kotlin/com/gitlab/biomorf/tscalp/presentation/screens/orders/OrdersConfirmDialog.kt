package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.presentation.components.ConfirmOrderDialog

@Composable
internal fun OrdersConfirmDialogSection(
    instrumentState: OrderInstrumentState,
    inputState: OrderInputState,
    tradeDetailsState: OrderTradeDetailsState,
    executionState: OrderExecutionState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmOrderDialog(
        show = executionState.showConfirmDialog,
        ticker = instrumentState.selectedInstrument?.ticker ?: "",
        quantity = inputState.quantityAsLong ?: 0L,
        pendingDirection = executionState.pendingDirection,
        orderType = inputState.orderType,
        instrumentType = instrumentState.selectedInstrument?.instrumentType ?: InstrumentType.OTHER,
        executionPrice = tradeDetailsState.executionPrice,
        pairTradingEnabled = inputState.pairTradingEnabled,
        pairedInstrumentTicker = instrumentState.pairedInstrument?.ticker,
        pairedInstrumentType = instrumentState.pairedInstrument?.instrumentType,
        pairedQuantity = inputState.pairedQuantity,
        pairExecPrice = tradeDetailsState.pairExecPrice,
        pairedOrderType = tradeDetailsState.pairedOrderType,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
