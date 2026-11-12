package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.presentation.components.ConfirmOrderDialog

@Composable
internal fun OrdersConfirmDialogSection(
    formState: OrderFormState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmOrderDialog(
        show = formState.showConfirmDialog,
        ticker = formState.selectedInstrument?.ticker ?: "",
        quantity = formState.quantityAsLong ?: 0L,
        pendingDirection = formState.pendingDirection,
        orderType = formState.orderType,
        instrumentType = formState.selectedInstrument?.instrumentType ?: InstrumentType.OTHER,
        executionPrice = formState.executionPrice,
        pairTradingEnabled = formState.pairTradingEnabled,
        pairedInstrumentTicker = formState.pairedInstrument?.ticker,
        pairedInstrumentType = formState.pairedInstrument?.instrumentType,
        pairedQuantity = formState.pairedQuantity,
        pairExecPrice = formState.pairExecPrice,
        pairedOrderType = formState.pairedOrderType,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
