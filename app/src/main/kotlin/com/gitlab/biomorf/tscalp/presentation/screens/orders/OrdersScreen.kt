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
    val uiState by viewModel.uiState.collectAsState()
    // orchestrates user-intent across 2 VMs (P4): search + form-trigger
    // split (searchVM.onSearchResultSelected + coordVM.onInstrumentSelected).
    val searchViewModel: OrderSearchViewModel = hiltViewModel()
    val searchState by searchViewModel.state.collectAsState()
    // orchestrates user-intent across dialogs (12b): dialog + save -> dialogsVM,
    // Coord remains source of form/positions state.
    val dialogsViewModel: OrderDialogsViewModel = hiltViewModel()
    val dialogsState by dialogsViewModel.state.collectAsState()
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

            if (!uiState.isApiInitialized) {
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
                        uiState = uiState,
                        searchState = searchState,
                        onSearchQueryChanged = { searchViewModel.onSearchQueryChanged(it) },
                        onInstrumentSelected = {
                            searchViewModel.onSearchResultSelected(it)
                            viewModel.onInstrumentSelected(it)
                            focusManager.clearFocus()
                        },
                        onClearSearch = {
                            searchViewModel.clearSearchQuery()
                            viewModel.clearSelectedInstrument()
                        },
                        onRefreshSearch = { searchViewModel.refreshSearch() },
                        onOpenSearchSettings = { searchViewModel.openSearchBrokerSettings() },
                        onClearSelected = { viewModel.clearSelectedInstrument() },
                        onOpenBrokerDialog = { dialogsViewModel.openBrokerDialog(it) },
                    )

                    // Spacer(modifier = Modifier.height(1.dp))

                    // ========== Поле количества ==========
                    // Определяем цену исполнения в зависимости от типа заявки
                    OrdersConfirmDialogSection(
                        uiState = uiState,
                        onDismiss = { viewModel.onDismissConfirmDialog() },
                        onConfirm = { viewModel.onConfirmOrder() },
                    )

                    OrdersQuantityRow(
                        quantity = uiState.quantity,
                        costOverlay = uiState.costOverlay,
                        canDecrement = (uiState.quantityAsLong ?: 0L) > 0 && uiState.selectedInstrument != null,
                        canIncrement = uiState.selectedInstrument != null,
                        onQuantityChanged = { viewModel.onQuantityChanged(it) },
                    )

                    // ========== Выбор типа заявки ==========
                    OrdersTypeChips(
                        orderType = uiState.orderType,
                        onOrderTypeChanged = { viewModel.onOrderTypeChanged(it) },
                    )

                    // ========== Ценовые поля (статическая высота) ==========
                    OrdersPriceFields(
                        orderType = uiState.orderType,
                        limitPrice = uiState.limitPrice,
                        stopPrice = uiState.stopPrice,
                        onLimitPriceChanged = { viewModel.onLimitPriceChanged(it) },
                        onStopPriceChanged = { viewModel.onStopPriceChanged(it) },
                    )

                    // ========== СЕКЦИЯ ПАРНОЙ ТОРГОВЛИ (в скролле) ==========
                    OrdersPairSection(
                        uiState = uiState,
                        searchState = searchState,
                        scrollState = scrollState,
                        scope = scope,
                        onPairSearchQueryChanged = { searchViewModel.onPairSearchQueryChanged(it) },
                        onPairedInstrumentSelected = {
                            searchViewModel.onPairedSearchResultSelected(it)
                            viewModel.onPairedInstrumentSelected(it)
                            focusManager.clearFocus()
                        },
                        onClearPairSearch = { searchViewModel.clearPairSearchQuery() },
                        onRefreshPairSearch = { searchViewModel.refreshPairSearch() },
                        onOpenPairSearchBrokerSettings = { searchViewModel.openPairSearchBrokerSettings() },
                        onPairTradingEnabledChanged = { viewModel.setPairTradingEnabled(it) },
                        onClearPairedInstrument = {
                            searchViewModel.clearPairSearchQuery()
                            viewModel.clearPairedInstrument()
                        },
                        onOpenBrokerDialog = { dialogsViewModel.openBrokerDialog(it) },
                        onPairedMultiplierChanged = { viewModel.onPairedMultiplierChanged(it) },
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
                freeBalance = uiState.freeBalance,
                isFormValid = uiState.isFormValid,
                isLoading = uiState.isLoading,
                onBuyClick = { viewModel.onBuyClick() },
                onSellClick = { viewModel.onSellClick() },
            )
        }

        // Snackbar (extracted, Fase 2 11b)
        OrdersScreenSnackbar(
            snackbarHostState = snackbarHostState,
            uiState = uiState,
            searchState = searchState,
            onClearStatus = { viewModel.clearStatus() },
            onClearSearchStatus = { searchViewModel.clearStatus() },
        )
    }

    TradingBrokerDialog(
        availableBrokers = dialogsViewModel.getAvailableBrokerNames(),
        dialogsState = dialogsState,
        onBrokerSelected = { dialogsViewModel.onBrokerSelected(it) },
        onAccountSelected = { dialogsViewModel.onAccountSelectedDialog(it) },
        onDismiss = { dialogsViewModel.closeBrokerDialog() },
        onSave = { dialogsViewModel.saveBrokerSettings() },
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
    uiState: OrdersUiState,
    searchState: OrderSearchState,
    onClearStatus: () -> Unit,
    onClearSearchStatus: () -> Unit,
) {
    OrdersSnackbarEffects(
        snackbarHostState = snackbarHostState,
        uiState = uiState,
        searchState = searchState,
        onClearStatus = onClearStatus,
        onClearSearchStatus = onClearSearchStatus,
    )
    OrdersSnackbarHost(snackbarHostState = snackbarHostState, isError = uiState.isError)
}

/**
 * Эффекты Snackbar (Fase 2, 11b): автоочистка + показ.
 *
 * 2 × `LaunchedEffect(uiState.statusMessage)` (автоочистка через 5 с +
 * показ, дубль pre-existing, 1:1) + `LaunchedEffect(searchState.statusMessage)`.
 */
@Composable
private fun OrdersSnackbarEffects(
    snackbarHostState: SnackbarHostState,
    uiState: OrdersUiState,
    searchState: OrderSearchState,
    onClearStatus: () -> Unit,
    onClearSearchStatus: () -> Unit,
) {
    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null && !uiState.isError) {
            kotlinx.coroutines.delay(5000)
            onClearStatus()
        }
    }

    LaunchedEffect(searchState.statusMessage) {
        searchState.statusMessage?.let { message ->
            val visuals =
                object : SnackbarVisuals {
                    override val message: String = message
                    override val actionLabel: String? = if (searchState.isError) "OK" else null
                    override val withDismissAction: Boolean = false
                    override val duration: SnackbarDuration =
                        if (searchState.isError) SnackbarDuration.Indefinite else SnackbarDuration.Short
                }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(visuals)
            onClearSearchStatus()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { message ->
            val visuals =
                object : SnackbarVisuals {
                    override val message: String = message
                    override val actionLabel: String? = if (uiState.isError) "OK" else null
                    override val withDismissAction: Boolean = false
                    override val duration: SnackbarDuration =
                        if (uiState.isError) SnackbarDuration.Indefinite else SnackbarDuration.Short
                }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(visuals)
            onClearStatus()
        }
    }
}

/**
 * Snackbar host (Fase 2, 11b): `SnackbarHost` + `Snackbar`.
 * Контейнер / контент — error vs tertiary по [isError].
 */
@Composable
private fun BoxScope.OrdersSnackbarHost(
    snackbarHostState: SnackbarHostState,
    isError: Boolean,
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp),
    ) { data ->
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
