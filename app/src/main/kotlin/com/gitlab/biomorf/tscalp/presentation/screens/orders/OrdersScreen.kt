package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.presentation.components.StatusEventEffect
import com.gitlab.biomorf.tscalp.presentation.components.StatusSnackbarHost
import com.gitlab.biomorf.tscalp.presentation.util.StatusEvent
import kotlinx.coroutines.flow.Flow

@Composable
private fun OrdersScreenHeader(onShowOrdersList: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Выставление заявки",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onShowOrdersList) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Список заявок")
        }
    }
}

// Архитектурный долг: 221 строка > 60. Extract сделан (10 подкомпонентов),
// CyclomaticComplexMethod снят (complexity < 14). Дальнейшая декомпозиция
// (вынести sidebar-навигацию, статусы) — Н94 / #### Compose-экраны.
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen() {
    val viewModel: OrdersViewModel = hiltViewModel()
    val coordState by viewModel.coordState.collectAsState()
    val tradingStatuses by viewModel.tradingStatuses.collectAsState()
    // orchestrates user-intent across 2 VMs (P4): search + form-trigger
    // split (searchVM.onSearchResultSelected + coordVM.onInstrumentSelected).
    val searchViewModel: OrderSearchViewModel = hiltViewModel()
    val searchState by searchViewModel.state.collectAsState()
    // orchestrates user-intent across dialogs (12b): dialog + save -> dialogsVM,
    // Coord remains source of form/positions state.
    val dialogsViewModel: OrderDialogsViewModel = hiltViewModel()
    val dialogsState by dialogsViewModel.state.collectAsState()
    // Form-VM (split 13d): form-only state (instrument / quantity / orderType /
    // prices / pair / confirm / status). Coord `uiState` owns accounts /
    // lastSelectedInstruments / tradingStatuses. Cross-VM via domain only.
    val formViewModel: OrderFormViewModel = hiltViewModel()
    val formState by formViewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }

    var showOrdersListDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkApiInitialization()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ========== ЗАГОЛОВОК ==========
            OrdersScreenHeader(onShowOrdersList = { showOrdersListDialog = true })

            if (!coordState.isApiInitialized) {
                ApiNotInitializedCard()
                return@Box
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ========== СКРОЛЛИРУЕМЫЙ БЛОК (OrdersScrollableContent) ==========
            OrdersScrollableContent(
                state =
                    OrdersScrollableState(
                        coordState = coordState,
                        searchState = searchState,
                        formState = formState,
                        tradingStatuses = tradingStatuses,
                        resetSwipe = dialogsState.swipeResetTrigger,
                    ),
                callbacks =
                    OrdersScrollableCallbacks(
                        onSearchQueryChanged = { searchViewModel.onSearchQueryChanged(it) },
                        onInstrumentSelected = { inst ->
                            searchViewModel.onSearchResultSelected(inst)
                            formViewModel.onInstrumentSelected(inst)
                            focusManager.clearFocus()
                        },
                        onClearSearch = {
                            searchViewModel.clearSearchQuery()
                            formViewModel.clearSelectedInstrument()
                        },
                        onRefreshSearch = { searchViewModel.refreshSearch() },
                        onOpenSearchSettings = { searchViewModel.openSearchBrokerSettings() },
                        onClearSelected = { formViewModel.clearSelectedInstrument() },
                        onOpenBrokerDialog = { inst, acc, slot ->
                            dialogsViewModel.openBrokerDialog(inst, acc, slot)
                        },
                        onDismissConfirmDialog = { formViewModel.onDismissConfirmDialog() },
                        onConfirmOrder = { formViewModel.onConfirmOrder() },
                        onQuantityChanged = { formViewModel.onQuantityChanged(it) },
                        onOrderTypeChanged = { formViewModel.onOrderTypeChanged(it) },
                        onLimitPriceChanged = { formViewModel.onLimitPriceChanged(it) },
                        onStopPriceChanged = { formViewModel.onStopPriceChanged(it) },
                        onPairSearchQueryChanged = { searchViewModel.onPairSearchQueryChanged(it) },
                        onPairedInstrumentSelected = { inst ->
                            searchViewModel.onPairedSearchResultSelected(inst)
                            formViewModel.onPairedInstrumentSelected(inst)
                            focusManager.clearFocus()
                        },
                        onClearPairSearch = { searchViewModel.clearPairSearchQuery() },
                        onRefreshPairSearch = { searchViewModel.refreshPairSearch() },
                        onOpenPairSearchBrokerSettings = { searchViewModel.openPairSearchBrokerSettings() },
                        onPairTradingEnabledChanged = { formViewModel.setPairTradingEnabled(it) },
                        onClearPairedInstrument = {
                            searchViewModel.clearPairSearchQuery()
                            formViewModel.clearPairedInstrument()
                        },
                        onPairedMultiplierChanged = { formViewModel.onPairedMultiplierChanged(it) },
                    ),
            )

            // ========== ФИКСИРОВАННАЯ НИЖНЯЯ СЕКЦИЯ (только кнопки) ==========
            OrdersBottomButtons(
                freeBalance = formState.freeBalance,
                isFormValid = formState.isFormValid(coordState.isApiInitialized),
                isLoading = formState.isLoading || coordState.isLoading,
                onBuyClick = { formViewModel.onBuyClick() },
                onSellClick = { formViewModel.onSellClick() },
            )
        }

        // Snackbar (extracted, Fase 2 11b; 13d-E-A: coord + form channels)
        OrdersScreenSnackbar(
            snackbarHostState = snackbarHostState,
            coordStatus = viewModel.coordStatus,
            formStatus = formViewModel.status,
            searchStatus = searchViewModel.status,
        )
    }

    OrdersDialogsSection(
        dialogsViewModel = dialogsViewModel,
        searchViewModel = searchViewModel,
        formViewModel = formViewModel,
        dialogsState = dialogsState,
        searchState = searchState,
        showOrdersListDialog = showOrdersListDialog,
        onShowOrdersListDialogChange = { showOrdersListDialog = it },
    )
}

// ==================== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ====================

/**
 * Snackbar для Orders-экрана (Fase 2, 11b): 2 эффекта + host.
 * Разбито на [OrdersSnackbarEffects] + [OrdersSnackbarHost] (detekt
 * LongMethod < 60, без @Suppress).
 *
 * `BoxScope` — для `Modifier.align(Alignment.BottomCenter)`.
 */
@Composable
private fun BoxScope.OrdersScreenSnackbar(
    snackbarHostState: SnackbarHostState,
    coordStatus: Flow<StatusEvent>,
    formStatus: Flow<StatusEvent>,
    searchStatus: Flow<StatusEvent>,
) {
    OrdersSnackbarEffects(
        snackbarHostState = snackbarHostState,
        coordStatus = coordStatus,
        formStatus = formStatus,
        searchStatus = searchStatus,
    )
    StatusSnackbarHost(snackbarHostState = snackbarHostState)
}

/**
 * Три one-shot канала: Coord / Form / Search (`StatusEventEffect`).
 * `Channel(BUFFERED)` в каждой VM consume'ится ровно один раз; M6,
 * clean-audit M6.
 */
@Composable
private fun OrdersSnackbarEffects(
    snackbarHostState: SnackbarHostState,
    coordStatus: Flow<StatusEvent>,
    formStatus: Flow<StatusEvent>,
    searchStatus: Flow<StatusEvent>,
) {
    StatusEventEffect(snackbarHostState = snackbarHostState, status = coordStatus)
    StatusEventEffect(snackbarHostState = snackbarHostState, status = formStatus)
    StatusEventEffect(snackbarHostState = snackbarHostState, status = searchStatus)
}

@Composable
fun ApiNotInitializedCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠️ API не подключен", style = MaterialTheme.typography.titleMedium)
            Text("Перейдите в Настройки и введите токен доступа", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

// Архитектурный долг: функция требует декомпозиции.
// См. ROADMAP → «Рефакторинг OrdersScreen».
