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
    dialogsState: OrderDialogsState,
    onBrokerSelected: (BrokerName) -> Unit,
    onAccountSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    if (!dialogsState.showBrokerDialog) return
    BrokerAccountDialog(
        availableBrokers = availableBrokers,
        selectedBroker = dialogsState.selectedBroker,
        onBrokerSelected = onBrokerSelected,
        accounts = dialogsState.dialogAccounts,
        selectedAccountId = dialogsState.selectedAccountIdDialog,
        onAccountSelected = onAccountSelected,
        onDismiss = onDismiss,
        onSave = onSave,
    )
}

@Composable
internal fun SearchBrokerDialog(
    availableBrokers: List<BrokerName>,
    searchState: OrderSearchState,
    onBrokerSelected: (BrokerName) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!searchState.primary.showBrokerDialog) return
    BrokerAccountDialog(
        availableBrokers = availableBrokers,
        selectedBroker = searchState.primary.selectedBroker,
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
    searchState: OrderSearchState,
    onBrokerSelected: (BrokerName) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!searchState.pair.showBrokerDialog) return
    BrokerAccountDialog(
        availableBrokers = availableBrokers,
        selectedBroker = searchState.pair.selectedBroker,
        onBrokerSelected = onBrokerSelected,
        accounts = emptyList(),
        selectedAccountId = null,
        onAccountSelected = {},
        onDismiss = onDismiss,
        onSave = onDismiss,
        showAccountSelector = false,
    )
}
