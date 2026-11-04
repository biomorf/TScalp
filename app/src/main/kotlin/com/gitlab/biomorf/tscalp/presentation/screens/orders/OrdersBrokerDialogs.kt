package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import com.gitlab.biomorf.tscalp.domain.usecases.PairOrderMapper
import com.gitlab.biomorf.tscalp.presentation.components.AssetPositionCard
import com.gitlab.biomorf.tscalp.presentation.components.BrokerAccountDialog
import com.gitlab.biomorf.tscalp.presentation.components.ConfirmOrderDialog
import com.gitlab.biomorf.tscalp.presentation.components.OrdersListDialog
import com.gitlab.biomorf.tscalp.presentation.util.formatDomesticCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
