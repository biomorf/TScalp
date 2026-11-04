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
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }

    var showOrdersListDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null && !uiState.isError) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearStatus()
        }
    }

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
                        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        onInstrumentSelected = {
                            viewModel.onInstrumentSelected(it)
                            focusManager.clearFocus()
                        },
                        onClearSearch = { viewModel.clearSearch() },
                        onRefreshSearch = { viewModel.refreshSearch() },
                        onOpenSearchSettings = { viewModel.openSearchBrokerSettings() },
                        onClearSelected = { viewModel.clearSelectedInstrument() },
                        onOpenBrokerDialog = { viewModel.openBrokerDialog(it) },
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
                        scrollState = scrollState,
                        scope = scope,
                        onPairSearchQueryChanged = { viewModel.onPairSearchQueryChanged(it) },
                        onPairedInstrumentSelected = {
                            viewModel.onPairedInstrumentSelected(it)
                            focusManager.clearFocus()
                        },
                        onClearPairSearch = { viewModel.clearPairSearch() },
                        onRefreshPairSearch = { viewModel.refreshPairSearch() },
                        onOpenPairSearchBrokerSettings = { viewModel.openPairSearchBrokerSettings() },
                        onPairTradingEnabledChanged = { viewModel.setPairTradingEnabled(it) },
                        onClearPairedInstrument = { viewModel.clearPairSearch() },
                        onOpenBrokerDialog = { viewModel.openBrokerDialog(it) },
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

        // Снекбар поверх всего
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp),
        ) { data ->
            Snackbar(
                containerColor =
                    if (uiState.isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                contentColor =
                    if (uiState.isError) {
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

    // ==================== ДИАЛОГИ ====================
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
            viewModel.clearStatus()
        }
    }

    TradingBrokerDialog(
        availableBrokers = viewModel.getAvailableBrokerNames(),
        uiState = uiState,
        onBrokerSelected = { viewModel.onBrokerSelected(it) },
        onAccountSelected = { viewModel.onAccountSelectedDialog(it) },
        onDismiss = { viewModel.closeBrokerDialog() },
        onSave = { viewModel.saveBrokerSettings() },
    )

    if (showOrdersListDialog) {
        OrdersListDialog(
            onDismiss = { showOrdersListDialog = false },
        )
    }
    // Диалог выбора брокера для основного поиска
    SearchBrokerDialog(
        availableBrokers = viewModel.getAvailableBrokerNames(),
        uiState = uiState,
        onBrokerSelected = { viewModel.saveSearchBrokerSettings(it) },
        onDismiss = { viewModel.dismissSearchBrokerDialog() },
    )

// Диалог выбора брокера для парного поиска
    PairSearchBrokerDialog(
        availableBrokers = viewModel.getAvailableBrokerNames(),
        uiState = uiState,
        onBrokerSelected = { viewModel.savePairSearchBrokerSettings(it) },
        onDismiss = { viewModel.dismissPairSearchBrokerDialog() },
    )
}

// ==================== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ====================

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
