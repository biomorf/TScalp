package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.presentation.components.BrokerAccountDialog

@Composable
internal fun TradingBrokerDialog(
    availableBrokers: List<BrokerName>,
    uiState: OrdersUiState,
    onBrokerSelected: (BrokerName) -> Unit,
    onAccountSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    if (!uiState.showBrokerDialog) return
    BrokerAccountDialog(
        availableBrokers = availableBrokers,
        selectedBroker = uiState.selectedBroker,
        onBrokerSelected = onBrokerSelected,
        accounts = uiState.dialogAccounts,
        selectedAccountId = uiState.selectedAccountIdDialog,
        onAccountSelected = onAccountSelected,
        onDismiss = onDismiss,
        onSave = onSave,
    )
}

@Composable
internal fun SearchBrokerDialog(
    availableBrokers: List<BrokerName>,
    uiState: OrdersUiState,
    onBrokerSelected: (BrokerName) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!uiState.showSearchBrokerDialog) return
    BrokerAccountDialog(
        availableBrokers = availableBrokers,
        selectedBroker = uiState.selectedSearchBroker,
        onBrokerSelected = onBrokerSelected,
        accounts = emptyList(),
        selectedAccountId = null,
        onAccountSelected = {},
        onDismiss = onDismiss,
        onSave = onDismiss,
        showAccountSelector = false,
    )
}

@Composable
internal fun PairSearchBrokerDialog(
    availableBrokers: List<BrokerName>,
    uiState: OrdersUiState,
    onBrokerSelected: (BrokerName) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!uiState.showPairSearchBrokerDialog) return
    BrokerAccountDialog(
        availableBrokers = availableBrokers,
        selectedBroker = uiState.selectedPairSearchBroker,
        onBrokerSelected = onBrokerSelected,
        accounts = emptyList(),
        selectedAccountId = null,
        onAccountSelected = {},
        onDismiss = onDismiss,
        onSave = onDismiss,
        showAccountSelector = false,
    )
}
