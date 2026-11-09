package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.presentation.components.OrdersListDialog
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

            // ========== СКРОЛЛИРУЕМЫЙ БЛОК с индикаторами прокрутки ==========
            val scrollState = rememberScrollState()
            val scope = rememberCoroutineScope()
            val showTopShadow by remember { derivedStateOf { scrollState.value > 0 } }
            val showBottomShadow by remember {
                derivedStateOf { scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue }
            }

            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // ========== ОСНОВНОЙ ПОИСК / КАРТОЧКА ==========
                    OrdersInstrumentSection(
                        coordState = coordState,
                        searchState = searchState,
                        formState = formState,
                        tradingStatuses = tradingStatuses,
                        resetSwipe = dialogsState.swipeResetTrigger,
                        onSearchQueryChanged = { searchViewModel.onSearchQueryChanged(it) },
                        onInstrumentSelected = {
                            searchViewModel.onSearchResultSelected(it)
                            formViewModel.onInstrumentSelected(it)
                            focusManager.clearFocus()
                        },
                        onClearSearch = {
                            searchViewModel.clearSearchQuery()
                            formViewModel.clearSelectedInstrument()
                        },
                        onRefreshSearch = { searchViewModel.refreshSearch() },
                        onOpenSearchSettings = { searchViewModel.openSearchBrokerSettings() },
                        onClearSelected = { formViewModel.clearSelectedInstrument() },
                        onOpenBrokerDialog = { inst, acc ->
                            dialogsViewModel.openBrokerDialog(
                                instrument = inst,
                                currentAccountId = acc,
                                slot = CardSlot.PRIMARY,
                            )
                        },
                    )

                    // Spacer(modifier = Modifier.height(1.dp))

                    // ========== Поле количества ==========
                    // Определяем цену исполнения в зависимости от типа заявки
                    OrdersConfirmDialogSection(
                        formState = formState,
                        onDismiss = { formViewModel.onDismissConfirmDialog() },
                        onConfirm = { formViewModel.onConfirmOrder() },
                    )

                    OrdersQuantityRow(
                        quantity = formState.quantity,
                        costOverlay = formState.costOverlay,
                        canDecrement = (formState.quantityAsLong ?: 0L) > 0 && formState.selectedInstrument != null,
                        canIncrement = formState.selectedInstrument != null,
                        onQuantityChanged = { formViewModel.onQuantityChanged(it) },
                    )

                    // ========== Выбор типа заявки ==========
                    OrdersTypeChips(
                        orderType = formState.orderType,
                        onOrderTypeChanged = { formViewModel.onOrderTypeChanged(it) },
                    )

                    // ========== Ценовые поля (статическая высота) ==========
                    OrdersPriceFields(
                        orderType = formState.orderType,
                        limitPrice = formState.limitPrice,
                        stopPrice = formState.stopPrice,
                        onLimitPriceChanged = { formViewModel.onLimitPriceChanged(it) },
                        onStopPriceChanged = { formViewModel.onStopPriceChanged(it) },
                    )

                    // ========== СЕКЦИЯ ПАРНОЙ ТОРГОВЛИ (в скролле) ==========
                    OrdersPairSection(
                        coordState = coordState,
                        searchState = searchState,
                        formState = formState,
                        tradingStatuses = tradingStatuses,
                        resetSwipe = dialogsState.swipeResetTrigger,
                        scrollState = scrollState,
                        scope = scope,
                        onPairSearchQueryChanged = { searchViewModel.onPairSearchQueryChanged(it) },
                        onPairedInstrumentSelected = {
                            searchViewModel.onPairedSearchResultSelected(it)
                            formViewModel.onPairedInstrumentSelected(it)
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
                        onOpenBrokerDialog = { inst, acc ->
                            dialogsViewModel.openBrokerDialog(
                                instrument = inst,
                                currentAccountId = acc,
                                slot = CardSlot.PAIR,
                            )
                        },
                        onPairedMultiplierChanged = { formViewModel.onPairedMultiplierChanged(it) },
                    )
                } // конец скроллируемой колонки

                // === ИНДИКАТОРЫ ПРОКРУТКИ ===
                OrdersScreenShadows(
                    showTopShadow = showTopShadow,
                    showBottomShadow = showBottomShadow,
                )
            }

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

    TradingBrokerDialog(
        availableBrokers = dialogsViewModel.getAvailableBrokerNames(),
        dialogsState = dialogsState,
        onBrokerSelected = { dialogsViewModel.onBrokerSelected(it) },
        onAccountSelected = { dialogsViewModel.onAccountSelectedDialog(it) },
        onDismiss = { dialogsViewModel.closeBrokerDialog() },
        onSave = {
            dialogsViewModel.saveBrokerSettings { slot, newAccountId ->
                when (slot) {
                    CardSlot.PRIMARY -> formViewModel.onAccountSaved(newAccountId)
                    CardSlot.PAIR -> formViewModel.onPairedAccountSaved(newAccountId)
                }
            }
        },
    )

    if (showOrdersListDialog) {
        OrdersListDialog(
            onDismiss = { showOrdersListDialog = false },
        )
    }
    // Диалог выбора брокера для основного поиска
    SearchBrokerDialog(
        availableBrokers = searchViewModel.getAvailableBrokerNames(),
        searchState = searchState,
        onBrokerSelected = { searchViewModel.saveSearchBrokerSettings(it) },
        onDismiss = { searchViewModel.dismissSearchBrokerDialog() },
    )

    // Диалог выбора брокера для парного поиска
    PairSearchBrokerDialog(
        availableBrokers = searchViewModel.getAvailableBrokerNames(),
        searchState = searchState,
        onBrokerSelected = { searchViewModel.savePairSearchBrokerSettings(it) },
        onDismiss = { searchViewModel.dismissPairSearchBrokerDialog() },
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
    OrdersSnackbarHost(snackbarHostState = snackbarHostState)
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

/**
 * One-shot status channel: `LaunchedEffect(Unit) { status.collect { … } }`.
 * `Channel(BUFFERED)` consume'ится ровно один раз; повторно snackbar не
 * показывается (M6 fix, clean-audit M6). Поведение 1:1 прежним трём
 * `StatusEffect`-блокам (удалены в M016) — actionLabel / duration по
 * `isError`, `OK`-кнопка при ошибке.
 */
@Composable
private fun StatusEventEffect(
    snackbarHostState: SnackbarHostState,
    status: Flow<StatusEvent>,
) {
    LaunchedEffect(Unit) {
        status.collect { event ->
            snackbarHostState.showSnackbar(
                StatusSnackbarVisuals(
                    message = event.message,
                    actionLabel = if (event.isError) "OK" else null,
                    withDismissAction = false,
                    duration = if (event.isError) SnackbarDuration.Indefinite else SnackbarDuration.Short,
                    isError = event.isError,
                ),
            )
        }
    }
}

/**
 * `SnackbarVisuals` с флагом `isError`: `OrdersSnackbarHost` стилизует
 * контейнер по `data.visuals.isError`, без параметра/StateFlow (M6:
 * event → visuals → host).
 */
private data class StatusSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val withDismissAction: Boolean,
    override val duration: SnackbarDuration,
    val isError: Boolean,
) : SnackbarVisuals

/**
 * Snackbar host (Fase 2, 11b): `SnackbarHost` + `Snackbar`.
 * Контейнер / контент — error vs tertiary по `data.visuals.isError`
 * (`StatusSnackbarVisuals`, M016).
 */
@Composable
private fun BoxScope.OrdersSnackbarHost(
    snackbarHostState: SnackbarHostState,
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp),
    ) { data ->
        val isError = (data.visuals as? StatusSnackbarVisuals)?.isError ?: false
        Snackbar(
            containerColor =
                if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
            contentColor =
                if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
            action = {
                val label = data.visuals.actionLabel
                if (label != null) {
                    TextButton(onClick = { data.dismiss() }) {
                        Text(label)
                    }
                }
            },
        ) {
            Text(data.visuals.message)
        }
    }
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
