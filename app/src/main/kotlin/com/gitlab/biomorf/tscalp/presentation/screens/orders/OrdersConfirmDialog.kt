package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.presentation.components.ConfirmOrderDialog

@Composable
internal fun OrdersConfirmDialogSection(
    uiState: OrdersUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmOrderDialog(
        show = uiState.showConfirmDialog,
        ticker = uiState.selectedInstrument?.ticker ?: "",
        quantity = uiState.quantityAsLong ?: 0L,
        pendingDirection = uiState.pendingDirection,
        orderType = uiState.orderType,
        instrumentType = uiState.selectedInstrument?.instrumentType ?: InstrumentType.OTHER,
        executionPrice = uiState.executionPrice,
        pairTradingEnabled = uiState.pairTradingEnabled,
        pairedInstrumentTicker = uiState.pairedInstrument?.ticker,
        pairedInstrumentType = uiState.pairedInstrument?.instrumentType,
        pairedMultiplier = uiState.pairedMultiplier,
        pairExecPrice = uiState.pairExecPrice,
        pairedOrderType = uiState.pairedOrderType,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
