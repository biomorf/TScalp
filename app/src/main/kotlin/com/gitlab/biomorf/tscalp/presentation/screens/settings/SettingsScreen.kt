package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitlab.biomorf.tscalp.BuildConfig
import com.gitlab.biomorf.tscalp.domain.models.BrokerName

@Composable
fun SettingsScreen() {
    var currentSection by remember { mutableStateOf<String?>(null) }

    if (currentSection == null) {
        SettingsHubList(onSelect = { currentSection = it })
    } else {
        when (currentSection) {
            "broker" -> BrokerSettingsContent(onBack = { currentSection = null })
            "trade" -> TradeSettingsContent(onBack = { currentSection = null })
            "info" -> InfoSettingsContent(onBack = { currentSection = null })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrokerSettingsContent(onBack: () -> Unit) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val brokerNames = remember { settingsViewModel.getAvailableBrokerNames() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { brokerNames.size })

    // Синхронизация вкладок и пейджера (мгновенный переход)
    LaunchedEffect(selectedTabIndex) {
        pagerState.scrollToPage(selectedTabIndex)
    }
    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Подключение") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                brokerNames.forEachIndexed { index, broker ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(broker.displayName) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = true,
            ) { page ->
                when (brokerNames[page]) {
                    BrokerName.TINVEST -> TInvestSettingsPanel()
                    BrokerName.BCS -> BcsSettingsPanel()
                    BrokerName.FINAM -> FinamSettingsPanel()
                }
            }
        }
    }
}

// Архитектурный долг: функция требует декомпозиции.
// См. ROADMAP → «Рефакторинг Compose-экранов».
// 2c-fixation (U3 Pass-3): body ~102 lines > 60 (LongMethod), complexity > 14 (Cyclomatic).
// Split forwards ~10 callbacks / 3 state fields; behaviour 1:1.
@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TInvestSettingsPanel(viewModel: TInvestSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // UI-only state: остаётся в Compose (ввод, разворачивание, диалог).
    var showToken by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isConnected, uiState.sandboxMode) {
        if (uiState.isConnected) {
            viewModel.loadAccounts()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        TInvestStatusCard(
            isConnected = uiState.isConnected,
            canConnect = uiState.token.isNotBlank(),
            onConnect = { viewModel.connect(uiState.token, uiState.sandboxMode) },
            onDisconnect = { viewModel.disconnect() },
        )

        OutlinedTextField(
            value = uiState.token,
            onValueChange = { viewModel.setToken(it) },
            label = { Text("Токен доступа") },
            placeholder = { Text("Введите токен из личного кабинета") },
            visualTransformation =
                if (showToken) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "Скрыть" else "Показать")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uiState.isConnected,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Режим песочницы", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Используйте для тестирования без реальных денег",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = uiState.sandboxMode,
                onCheckedChange = { viewModel.setSandboxMode(it) },
                enabled = !uiState.isConnected,
            )
        }

        if (uiState.isConnected) {
            TInvestAccountSection(
                availableAccounts = uiState.availableAccounts,
                defaultAccountId = uiState.defaultAccountId,
                sandboxMode = uiState.sandboxMode,
                isRefreshing = uiState.isRefreshing,
                accountExpanded = accountExpanded,
                onAccountExpandedChange = { accountExpanded = it },
                onAccountSelected = { viewModel.onAccountSelected(it) },
                onOpenSandbox = { viewModel.openSandboxAccount() },
                onCloseSandboxClick = { showCloseDialog = true },
            )
        }

        TInvestTokenHelpCard()

        uiState.statusMessage?.let { message ->
            StatusMessageCard(message = message, isError = uiState.isError)
        }
    }

    if (showCloseDialog) {
        CloseSandboxAccountDialog(
            accountName = uiState.availableAccounts.find { it.id == uiState.defaultAccountId }?.name,
            onConfirm = {
                viewModel.closeSandboxAccount(uiState.defaultAccountId)
                showCloseDialog = false
            },
            onDismiss = { showCloseDialog = false },
        )
    }
}

// Архитектурный долг: функция требует декомпозиции.
// См. ROADMAP → «Рефакторинг Compose-экранов».
// 2c-fixation (U3 Pass-3): body ~83 lines > 60 (LongMethod).
// Split forwards ~8 callbacks; behaviour 1:1.
@Suppress("LongMethod")
@Composable
fun BcsSettingsPanel(viewModel: BcsSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // UI-only state: остаётся в Compose.
    var showToken by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ConnectStatusCard(
            isConnected = uiState.connected,
            onDisconnect = { viewModel.disconnect() },
        )

        OutlinedTextField(
            value = uiState.refreshToken,
            onValueChange = { viewModel.setRefreshToken(it) },
            label = { Text("Токен доступа") },
            placeholder = { Text("Введите refresh‑токен из личного кабинета") },
            visualTransformation =
                if (showToken) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "Скрыть" else "Показать")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uiState.connected,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Права доступа", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (uiState.isWriteMode) "Полный доступ (торговля)" else "Только чтение",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = uiState.isWriteMode,
                onCheckedChange = { viewModel.setIsWriteMode(it) },
                enabled = !uiState.connected,
            )
        }

        if (uiState.connected) {
            Button(
                onClick = { viewModel.disconnect() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Отключить")
            }
        } else {
            Button(
                onClick = { viewModel.connect(uiState.refreshToken, uiState.isWriteMode) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.refreshToken.isNotBlank(),
            ) {
                Text("Подключиться")
            }
        }

        BcsTokenHelpCard()

        uiState.statusMessage?.let { message ->
            StatusMessageCard(message = message, isError = uiState.isError)
        }
    }
}

@Composable
fun FinamSettingsPanel(viewModel: FinamSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // UI-only state: остаётся в Compose.
    var showToken by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ConnectStatusCard(
            isConnected = uiState.connected,
            onDisconnect = { viewModel.disconnect() },
        )

        OutlinedTextField(
            value = uiState.token,
            onValueChange = { viewModel.setToken(it) },
            label = { Text("Токен доступа") },
            placeholder = { Text("Введите секретный токен Finam") },
            visualTransformation =
                if (showToken) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "Скрыть" else "Показать")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uiState.connected,
        )

        if (!uiState.connected) {
            Button(
                onClick = { viewModel.connect(uiState.token) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.token.isNotBlank(),
            ) {
                Text("Подключиться")
            }
        }

        FinamTokenHelpCard()

        uiState.statusMessage?.let { message ->
            StatusMessageCard(message = message, isError = uiState.isError)
        }
    }
}

@Composable
fun TradeSettingsContent(
    onBack: () -> Unit,
    viewModel: TradeSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
        }

        Text(
            "Настройки торговли",
            style = MaterialTheme.typography.headlineSmall,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Подтверждение заявок", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Показывать диалог перед отправкой",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = uiState.isConfirmOrdersEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setConfirmOrdersEnabled(enabled)
                },
            )
        }

        // Здесь можно добавить другие настройки торговли в будущем
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSettingsContent(
    onBack: () -> Unit,
    viewModel: InfoSettingsViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("О приложении") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        Column(
            modifier =
                Modifier
                    .padding(paddingValues)
                    .padding(16.dp),
        ) {
            Text("TScalp", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Версия: ${BuildConfig.VERSION_NAME}")
            Spacer(modifier = Modifier.height(16.dp))
            Text("Разработчик: Масленников Андрей", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Репозиторий: github.com/biomorf/TScalp", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(24.dp))

            if (BuildConfig.DEBUG) {
                DebugSwitchesSection(uiState, viewModel)
            }
            Spacer(modifier = Modifier.height(8.dp))
            val context = LocalContext.current
            Text(
                text = "Проверить обновления: https://gitlab.com/biomorf/tscalp/-/releases",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier.clickable {
                        val intent =
                            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = "https://gitlab.com/biomorf/tscalp/-/releases".toUri()
                            }
                        context.startActivity(intent)
                    },
            )
        }
    }
}

@Composable
private fun DebugSwitchesSection(
    uiState: InfoUiState,
    viewModel: InfoSettingsViewModel,
) {
    Text("Диагностика транспортов (debug)", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Отключить gRPC stream")
        Switch(
            checked = uiState.disableGrpc,
            onCheckedChange = { viewModel.toggleGrpc(it) },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Отключить WS")
        Switch(
            checked = uiState.disableWs,
            onCheckedChange = { viewModel.toggleWs(it) },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Принудительный REST (цены)")
        Switch(
            checked = uiState.forceRest,
            onCheckedChange = { viewModel.toggleRest(it) },
        )
    }
}
