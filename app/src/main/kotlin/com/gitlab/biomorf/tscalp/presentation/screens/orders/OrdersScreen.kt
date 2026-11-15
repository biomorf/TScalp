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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.presentation.components.ApiNotInitializedCard
import com.gitlab.biomorf.tscalp.presentation.components.StatusEventEffect
import com.gitlab.biomorf.tscalp.presentation.components.StatusSnackbarHost
import com.gitlab.biomorf.tscalp.presentation.util.StatusEvent
import com.gitlab.biomorf.tscalp.presentation.util.toPairedCardIdentity
import com.gitlab.biomorf.tscalp.presentation.util.toPlaceRequest
import com.gitlab.biomorf.tscalp.presentation.util.toPrimaryCardIdentity
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

// Архитектурный долг: функция > 60 строк. Extract сделан (10 подкомпонентов),
// CyclomaticComplexMethod снят (complexity < 14). Дальнейшая декомпозиция
// (вынести sidebar-навигацию, статусы) - Н94 / #### Compose-экраны.
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen() {
    // Coord (Phase 5f-b-2b): accounts / lifecycle / tradingStatuses.
    // clearInterest(ORDERS) - в OrderInstrumentViewModel (Phase 5f-b-2a).
    // Coord OrderFormViewModel удалён в Phase 6.1 (не используется с 5f-b-2b).
    val coordVM: OrdersViewModel = hiltViewModel()
    val coordState by coordVM.coordState.collectAsState()
    val tradingStatuses by coordVM.tradingStatuses.collectAsState()

    val searchVM: OrderSearchViewModel = hiltViewModel()
    val searchState by searchVM.state.collectAsState()

    val dialogsVM: OrderDialogsViewModel = hiltViewModel()
    val dialogsState by dialogsVM.state.collectAsState()

    // 4 sub-VM Form (C2, Phase 5f-b-2b)
    val instrVM: OrderInstrumentViewModel = hiltViewModel()
    val instrState by instrVM.state.collectAsState()

    val inputVM: OrderInputViewModel = hiltViewModel()
    val inputState by inputVM.state.collectAsState()

    val tradeDetailsVM: OrderTradeDetailsViewModel = hiltViewModel()
    val tradeDetailsState by tradeDetailsVM.state.collectAsState()

    val execVM: OrderExecutionViewModel = hiltViewModel()
    val execState by execVM.state.collectAsState()

    // UI-facing wrapper над @ActivityRetainedScoped OrderFormMediator.
    val mediatorVM: OrderMediatorViewModel = hiltViewModel()

    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showOrdersListDialog by remember { mutableStateOf(false) }

    // Coord: checkApiInitialization (остаётся в Coord до Phase 6)
    LaunchedEffect(Unit) {
        coordVM.checkApiInitialization()
    }

    // Restore (Phase 5f-b-2b): Coord init { restoreOrderForm() } -> UI orchestration.
    LaunchedEffect(Unit) {
        val restored = instrVM.restoreFromUseCase()
        if (restored != null) {
            instrVM.restore(restored)
            inputVM.restore(restored)
        }
    }

    // Reactive recalc: trade-details на изменение instr/input/exec.pendingDirection.
    LaunchedEffect(instrState, inputState, execState.pendingDirection) {
        tradeDetailsVM.updateTradeDetails(instrState, inputState)
        tradeDetailsVM.updatePairExecDetails(instrState, inputState, execState)
    }

    // Reactive save: persist на изменение primitives (debounce 500ms в Mediator).
    // 2e/M5: keying на primitives, не на весь state. currentPrice обновляется
    // каждые 5-10 сек от polling -> на весь state = save каждые 5 сек = rate-limit.
    // currentPrice нужен ТОЛЬКО recalc (executionPrice = currentPrice * qty) - выше.
    LaunchedEffect(
        instrState.selectedInstrument?.tscalpInstrumentId,
        instrState.selectedBroker,
        instrState.selectedAccountId,
        instrState.pairedInstrument?.tscalpInstrumentId,
        instrState.pairedBroker,
        instrState.pairedAccountId,
        inputState.pairTradingEnabled,
        inputState.quantityAsLong,
        inputState.pairedMultiplier.toLongOrNull(),
        inputState.orderType,
    ) {
        mediatorVM.updatePrimitives(
            selectedCard = instrState.toPrimaryCardIdentity(),
            pairedCard = instrState.toPairedCardIdentity(),
            pairTradingEnabled = inputState.pairTradingEnabled,
            quantity = inputState.quantityAsLong ?: 1L,
            pairedMultiplier = inputState.pairedMultiplier.toLongOrNull() ?: 10L,
            orderType = inputState.orderType,
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OrdersScreenHeader(onShowOrdersList = { showOrdersListDialog = true })

            if (!coordState.isApiInitialized) {
                ApiNotInitializedCard()
                return@Box
            }

            Spacer(modifier = Modifier.height(12.dp))

            OrdersScrollableContent(
                state =
                    OrdersScrollableState(
                        coordState = coordState,
                        searchState = searchState,
                        instrumentState = instrState,
                        inputState = inputState,
                        tradeDetailsState = tradeDetailsState,
                        executionState = execState,
                        tradingStatuses = tradingStatuses,
                        resetSwipe = dialogsState.swipeResetTrigger,
                    ),
                callbacks =
                    OrdersScrollableCallbacks(
                        onSearchQueryChanged = { searchVM.onQueryChanged(SearchSlot.PRIMARY, it) },
                        onInstrumentSelected = { inst ->
                            searchVM.onResultSelected(SearchSlot.PRIMARY, inst)
                            instrVM.onInstrumentSelected(inst)
                            focusManager.clearFocus()
                        },
                        onClearSearch = {
                            searchVM.clearQuery(SearchSlot.PRIMARY)
                            instrVM.clearSelectedInstrument()
                        },
                        onRefreshSearch = { searchVM.refresh(SearchSlot.PRIMARY) },
                        onOpenSearchSettings = { searchVM.openBrokerDialog(SearchSlot.PRIMARY) },
                        onClearSelected = { instrVM.clearSelectedInstrument() },
                        onOpenBrokerDialog = { inst, acc, slot ->
                            dialogsVM.openBrokerDialog(inst, acc, slot)
                        },
                        onDismissConfirmDialog = { execVM.onDismissConfirmDialog() },
                        onConfirmOrder = {
                            val req = toPlaceRequest(instrState, inputState)
                            if (req != null) execVM.onConfirmOrder(req)
                        },
                        onQuantityChanged = { inputVM.onQuantityChanged(it) },
                        onOrderTypeChanged = { inputVM.onOrderTypeChanged(it) },
                        onLimitPriceChanged = { inputVM.onLimitPriceChanged(it) },
                        onStopPriceChanged = { inputVM.onStopPriceChanged(it) },
                        onPairSearchQueryChanged = { searchVM.onQueryChanged(SearchSlot.PAIR, it) },
                        onPairedInstrumentSelected = { inst ->
                            searchVM.onResultSelected(SearchSlot.PAIR, inst)
                            instrVM.onPairedInstrumentSelected(inst)
                            focusManager.clearFocus()
                        },
                        onClearPairSearch = { searchVM.clearQuery(SearchSlot.PAIR) },
                        onRefreshPairSearch = { searchVM.refresh(SearchSlot.PAIR) },
                        onOpenPairSearchBrokerSettings = { searchVM.openBrokerDialog(SearchSlot.PAIR) },
                        onPairTradingEnabledChanged = { enabled ->
                            inputVM.setPairTradingEnabled(enabled)
                            if (!enabled) instrVM.clearPairedInstrument()
                        },
                        onClearPairedInstrument = {
                            searchVM.clearQuery(SearchSlot.PAIR)
                            instrVM.clearPairedInstrument()
                        },
                        onPairedMultiplierChanged = { inputVM.onPairedMultiplierChanged(it) },
                    ),
            )

            OrdersBottomButtons(
                freeBalance = execState.freeBalance,
                isFormValid = isFormValid(instrState, inputState, coordState.isApiInitialized),
                isLoading = execState.isLoading || coordState.isLoading,
                onBuyClick = {
                    if (execState.confirmOrdersEnabled) {
                        execVM.onBuyClick()
                    } else {
                        val req = toPlaceRequest(instrState, inputState)
                        if (req != null) execVM.onBuyClickDirect(req)
                    }
                },
                onSellClick = {
                    if (execState.confirmOrdersEnabled) {
                        execVM.onSellClick()
                    } else {
                        val req = toPlaceRequest(instrState, inputState)
                        if (req != null) execVM.onSellClickDirect(req)
                    }
                },
            )
        }

        OrdersScreenSnackbar(
            snackbarHostState = snackbarHostState,
            coordStatus = coordVM.coordStatus,
            formStatus = execVM.status,
            searchStatus = searchVM.status,
        )
    }

    OrdersDialogsSection(
        dialogsViewModel = dialogsVM,
        searchViewModel = searchVM,
        instrumentViewModel = instrVM,
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
 * BoxScope - для Modifier.align(Alignment.BottomCenter).
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
 * Три one-shot канала: Coord / Exec / Search (StatusEventEffect).
 * Channel(BUFFERED) в каждой VM consume-ится ровно один раз; M6,
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
// Архитектурный долг: функция требует декомпозиции.
// См. ROADMAP -> Рефакторинг OrdersScreen.
