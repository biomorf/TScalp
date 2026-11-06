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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

// Архитектурный долг: функция требует декомпозиции.
// См. ROADMAP → «Рефакторинг Compose-экранов».
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun SettingsScreen() {
    // Хаб: показываем список подразделов или содержимое подраздела
    var currentSection by remember { mutableStateOf<String?>(null) }

    if (currentSection == null) {
        // Список подразделов
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Настройки",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Пункт «Подключение»
            ListItem(
                headlineContent = { Text("Подключение") },
                supportingContent = { Text("Выбор брокера, токены, режим песочницы") },
                leadingContent = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                modifier = Modifier.clickable { currentSection = "broker" },
            )
            HorizontalDivider()

            // Пункт «Торговля»
            ListItem(
                headlineContent = { Text("Торговля") },
                supportingContent = { Text("Подтверждение сделок и другие параметры") },
                leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                modifier = Modifier.clickable { currentSection = "trade" },
            )
            HorizontalDivider()

            // Пункт «Информация»
            ListItem(
                headlineContent = { Text("Информация") },
                supportingContent = { Text("Версия приложения, о разработчике") },
                leadingContent = {
                    Icon(Icons.Default.Info, contentDescription = null)
                },
                modifier = Modifier.clickable { currentSection = "info" },
            )
        }
    } else {
        // Отображение выбранного подраздела
        when (currentSection) {
            "broker" ->
                BrokerSettingsContent(
                    onBack = { currentSection = null },
                )
            "trade" ->
                TradeSettingsContent(
                    onBack = { currentSection = null },
                )
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (uiState.isConnected) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Статус API", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (uiState.isConnected) "✅ Подключено" else "❌ Не подключено",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (uiState.isConnected) {
                    Button(
                        onClick = { viewModel.disconnect() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Отключить")
                    }
                } else {
                    Button(
                        onClick = { viewModel.connect(uiState.token, uiState.sandboxMode) },
                        enabled = uiState.token.isNotBlank(),
                    ) {
                        Text("Подключиться")
                    }
                }
            }
        }

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
            ExposedDropdownMenuBox(
                expanded = accountExpanded,
                onExpandedChange = { accountExpanded = it },
            ) {
                val selectedAccount = uiState.availableAccounts.find { it.id == uiState.defaultAccountId }
                TextField(
                    value = selectedAccount?.let { "${it.name} (${it.id})" } ?: "Выберите счёт",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Счёт по умолчанию") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true,
                            ),
                )
                ExposedDropdownMenu(
                    expanded = accountExpanded,
                    onDismissRequest = { accountExpanded = false },
                ) {
                    uiState.availableAccounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text("${account.name} (${account.id})") },
                            onClick = {
                                viewModel.onAccountSelected(account.id)
                                accountExpanded = false
                            },
                        )
                    }
                }
            }

            if (uiState.sandboxMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.openSandboxAccount() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isRefreshing,
                ) {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Открыть новый счёт песочницы")
                    }
                }
            }

            if (uiState.sandboxMode && uiState.defaultAccountId.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showCloseDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Закрыть счёт песочницы")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📋 Как получить токен", style = MaterialTheme.typography.titleMedium)
                Text(
                    """
                    1. Управляйте токенами с компьютера — с телефона может перекинуть в приложение, где этого раздела нет.
                    2. Откройте https://www.tbank.ru/invest/settings/ и войдите в личный кабинет Т-Инвестиций.
                    3. Убедитесь, что функция «Подтверждение сделок кодом» отключена. Иначе API не сможет выставлять заявки. Крупные сделки (>30 млн ₽) через API недоступны в любом случае.
                    4. Найдите подраздел «Токены T-Bank Invest API» и нажмите кнопку "Создать токен". Токен — это просто очень длинный специальный пароль для доступа к брокеру.
                    5. Выберите тип, нажав кнопку в соответствующем разделе на странице: песочница (для тестов, безопасно) или биржа (реальная торговля). Для биржи настройте права: только чтение / торговля, и доступ: все счета / конкретный счёт. Рекомендуем сначала потренироваться в песочнице.
                    6. Пройдите доп. авторизацию, если система попросит.
                    7. Скопируйте токен сразу — он показывается только один раз. Сохраните в надёжном месте (менеджер паролей).
                    8. Токен действует 3 месяца с последнего использования. Если перестал работать — выпустите новый.
                    9. Отозвать старый токен можно на https://id.tbank.ru/account в подразделе "Приложения, у которых есть доступ к данным для входа".
                    10. Никому не передавайте токен. Если он попал в логи или в публичные источники — немедленно отзовите и создайте новый.
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        uiState.statusMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (uiState.isError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                    ),
            ) {
                Text(message, modifier = Modifier.padding(16.dp))
            }
        }
    }

    if (showCloseDialog) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { Text("Закрытие счёта песочницы") },
            text = {
                Text(
                    "Вы уверены, что хотите закрыть счёт «${uiState.availableAccounts.find { it.id == uiState.defaultAccountId }?.name}»? Это действие необратимо.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.closeSandboxAccount(uiState.defaultAccountId)
                        showCloseDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Закрыть")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

// Архитектурный долг: функция требует декомпозиции.
// См. ROADMAP → «Рефакторинг Compose-экранов».
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (uiState.connected) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Статус API", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (uiState.connected) "✅ Подключено" else "❌ Не подключено",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (uiState.connected) {
                    Button(
                        onClick = { viewModel.disconnect() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Отключить")
                    }
                }
            }
        }

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

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📋 Как получить токен", style = MaterialTheme.typography.titleMedium)
                Text(
                    """
                    1. Зайдите в личный кабинет БКС Мир Инвестиций
                    2. Перейдите в раздел «Настройки» → «API»
                    3. Нажмите «Создать новый токен»
                    4. Выберите права: чтение портфеля и совершение сделок
                    5. Скопируйте полученный refresh‑токен
                    6. Вставьте его в поле выше

                    ⚠️ Рекомендации по безопасности:
                    • Сначала тестируйте в режиме песочницы
                    • Не передавайте токен третьим лицам
                    • Токен хранится в зашифрованном виде
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        uiState.statusMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (uiState.isError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                    ),
            ) {
                Text(message, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

// Архитектурный долг: функция требует декомпозиции.
// См. ROADMAP → «Рефакторинг Compose-экранов».
@Suppress("LongMethod")
@Composable
fun FinamSettingsPanel() {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsState()

    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(settingsUiState.finamConnected) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Загрузка сохранённых токенов
    LaunchedEffect(Unit) {
        val savedToken = settingsViewModel.getToken(BrokerName.FINAM)
        if (savedToken != null) {
            token = savedToken
            connected = true
        }
    }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Статус подключения
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (connected) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Статус API", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (connected) "✅ Подключено" else "❌ Не подключено",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (connected) {
                    Button(
                        onClick = {
                            settingsViewModel.clearBrokerCredentials(BrokerName.FINAM)
                            connected = false
                            token = ""
                            showToken = false
                            statusMessage = "Подключение к Finam разорвано"
                            isError = false
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Text("Отключить")
                    }
                }
            }
        }

        // Поле токена
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
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
            enabled = !connected,
        )

        // Кнопка подключения
        if (!connected) {
            Button(
                onClick = {
                    scope.launch {
                        val success = settingsViewModel.connectFinam(token)
                        connected = success
                        statusMessage = if (success) "Подключено к Finam" else "Ошибка подключения"
                        isError = !success
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = token.isNotBlank(),
            ) {
                Text("Подключиться")
            }
        }

        // Инструкция
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Как получить токен", style = MaterialTheme.typography.titleMedium)
                Text(
                    """
                    1. Зайдите в личный кабинет Finam
                    2. Перейдите в раздел «Настройки» → «API»
                    3. Создайте новый токен с правами на торговлю и чтение
                    4. Скопируйте секретный токен
                    5. Вставьте его в поле выше

                    ⚠️ Рекомендации:
                    • Храните токен в безопасном месте
                    • Не передавайте третьим лицам
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Статусное сообщение
        statusMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (isError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                    ),
            ) {
                Text(message, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
fun TradeSettingsContent(onBack: () -> Unit) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val uiState by settingsViewModel.uiState.collectAsState()

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
                    settingsViewModel.setConfirmOrdersEnabled(enabled)
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
    viewModel: SettingsViewModel = hiltViewModel(),
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
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
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
