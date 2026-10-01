package com.example.tscalp.presentation.screens.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.runtime.mutableIntStateOf

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

import com.example.tscalp.util.AppLogger
import com.example.tscalp.domain.models.BrokerAccount
import com.example.tscalp.domain.models.BrokerName
import com.example.tscalp.domain.models.AppResult

import com.example.tscalp.presentation.screens.orders.OrdersViewModel
//import com.example.tscalp.presentation.screens.orders.OrdersViewModelFactory
import com.example.tscalp.presentation.screens.orders.OrdersUiState
import com.example.tscalp.presentation.screens.settings.SettingsViewModel
import com.example.tscalp.BuildConfig


@Composable
fun SettingsScreen() {
    // Хаб: показываем список подразделов или содержимое подраздела
    var currentSection by remember { mutableStateOf<String?>(null) }

    if (currentSection == null) {
        // Список подразделов
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Настройки",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Пункт «Подключение»
            ListItem(
                headlineContent = { Text("Подключение") },
                supportingContent = { Text("Выбор брокера, токены, режим песочницы") },
                leadingContent = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                modifier = Modifier.clickable { currentSection = "broker" }
            )
            HorizontalDivider()

            // Пункт «Торговля»
            ListItem(
                headlineContent = { Text("Торговля") },
                supportingContent = { Text("Подтверждение сделок и другие параметры") },
                leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                modifier = Modifier.clickable { currentSection = "trade" }
            )
            HorizontalDivider()

            // Пункт «Информация»
            ListItem(
                headlineContent = { Text("Информация") },
                supportingContent = { Text("Версия приложения, о разработчике") },
                leadingContent = {
                    Icon(Icons.Default.Info, contentDescription = null)
                },
                modifier = Modifier.clickable { currentSection = "info" }
            )
        }
    } else {
        // Отображение выбранного подраздела
        when (currentSection) {
            "broker" -> BrokerSettingsContent(
                onBack = { currentSection = null }
            )
            "trade" -> TradeSettingsContent(
                onBack = { currentSection = null }
            )
            "info" -> InfoSettingsContent(onBack = { currentSection = null })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrokerSettingsContent(onBack: () -> Unit) {
    val ordersViewModel: OrdersViewModel = hiltViewModel()
    val uiState by ordersViewModel.uiState.collectAsState()

    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val brokerNames = remember { settingsViewModel.getAvailableBrokerNames() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { brokerNames.size })

    // Синхронизация вкладок и пейджера (мгновенный переход)
    LaunchedEffect(selectedTabIndex) {
        pagerState.scrollToPage(selectedTabIndex)   // ← мгновенно, без анимации
    }
    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Подключение") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                brokerNames.forEachIndexed { index, broker ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(broker.displayName) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = true
            ) { page ->
                when (brokerNames[page]) {
                    BrokerName.TINVEST -> TInvestSettingsPanel(ordersViewModel, uiState)
                    BrokerName.BCS -> BcsSettingsPanel()
                    BrokerName.FINAM -> FinamSettingsPanel()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TInvestSettingsPanel(ordersViewModel: OrdersViewModel, uiState: OrdersUiState) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    var token by remember { mutableStateOf("") }
    var sandboxMode by remember { mutableStateOf(settingsViewModel.isSandboxMode()) }
    var showToken by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var availableAccounts by remember { mutableStateOf<List<BrokerAccount>>(emptyList()) }
    var defaultAccountId by remember { mutableStateOf(settingsViewModel.loadDefaultAccountId("TInvest") ?: "") }
    var accountExpanded by remember { mutableStateOf(false) }

    var showCloseDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }  // общий флаг загрузки

    // Загрузка сохранённых креденшелов
    LaunchedEffect(Unit) {
        val creds = settingsViewModel.loadBrokerCredentials("TInvest")
        if (creds != null) {
            token = creds.first
            sandboxMode = creds.second
        }
    }

    val isConnected = uiState.isApiInitialized && settingsViewModel.loadBrokerCredentials("TInvest") != null

    // При подключении/изменении режима перезагружаем счета
    LaunchedEffect(isConnected, sandboxMode) {
        if (isConnected) {
            when (val result = settingsViewModel.getAccountsResult("TInvest", sandboxMode)) {
                is AppResult.Success -> {
                    availableAccounts = result.data
                    // Если счёт по умолчанию ещё не выбран — берём первый доступный
                    if (defaultAccountId.isBlank() && availableAccounts.isNotEmpty()) {
                        defaultAccountId = availableAccounts.first().id
                        settingsViewModel.saveDefaultAccountId("TInvest", defaultAccountId)
                    }
                }
                is AppResult.Failure -> {
                    AppLogger.w("TInvestSettingsPanel", "getAccounts failed: ${result.error.message}", result.error.cause)
                    availableAccounts = emptyList()
                }
            }
        } else {
            availableAccounts = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        // --- Карточка статуса подключения (без изменений) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Статус API", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isConnected) "✅ Подключено" else "❌ Не подключено",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (isConnected) {
                    Button(
                        onClick = {
                            settingsViewModel.clearBrokerCredentials("TInvest")
                            ordersViewModel.checkApiInitialization()
                            token = ""
                            statusMessage = "Подключение к Т‑Инвестициям разорвано"
                            isError = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Отключить")
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                val success = settingsViewModel.initializeTInvest(token, sandboxMode)
                                if (success) {
                                    ordersViewModel.checkApiInitialization()
                                    token = ""
                                    statusMessage = "Подключено к Т‑Инвестициям (режим ${if (sandboxMode) "песочница" else "боевой"})"
                                    isError = false
                                } else {
                                    statusMessage = settingsViewModel.uiState.value.statusMessage ?: "Ошибка подключения"
                                    isError = true
                                }
                            }
                        },
                        enabled = token.isNotBlank()
                    ) {
                        Text("Подключиться")
                    }
                }
            }
        }

        // --- Поле токена ---
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Токен доступа") },
            placeholder = { Text("Введите токен из личного кабинета") },
            visualTransformation = if (showToken) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "Скрыть" else "Показать")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConnected
        )

        // --- Переключатель песочницы ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Режим песочницы", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Используйте для тестирования без реальных денег",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = sandboxMode,
                onCheckedChange = { sandboxMode = it },
                enabled = !isConnected
            )
        }

        // --- Выбор счёта по умолчанию ---
        if (isConnected) {
            ExposedDropdownMenuBox(
                expanded = accountExpanded,
                onExpandedChange = { accountExpanded = it }
            ) {
                val selectedAccount = availableAccounts.find { it.id == defaultAccountId }
                TextField(
                    value = selectedAccount?.let { "${it.name} (${it.id})" } ?: "Выберите счёт",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Счёт по умолчанию") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                )
                ExposedDropdownMenu(
                    expanded = accountExpanded,
                    onDismissRequest = { accountExpanded = false }
                ) {
                    availableAccounts.forEach { account: BrokerAccount ->
                        DropdownMenuItem(
                            text = { Text("${account.name} (${account.id})") },
                            onClick = {
                                defaultAccountId = account.id
                                settingsViewModel.saveDefaultAccountId("TInvest", account.id)
                                accountExpanded = false
                            }
                        )
                    }
                }
            }

            // --- Кнопка открытия счёта песочницы (только в режиме песочницы) ---
            if (sandboxMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isRefreshing = true
                            try {
                                val newAccountId = settingsViewModel.openSandboxAccount()
                                when (val accountsResult = settingsViewModel.getAccountsResult("TInvest", sandboxMode)) {
                                    is AppResult.Success -> availableAccounts = accountsResult.data
                                    is AppResult.Failure ->
                                        AppLogger.w("TInvestSettingsPanel", "reload accounts failed: ${accountsResult.error.message}", accountsResult.error.cause)
                                }
                                defaultAccountId = newAccountId
                                settingsViewModel.saveDefaultAccountId("TInvest", newAccountId)
                                statusMessage = "Новый счёт песочницы открыт (ID: ${newAccountId})"
                                isError = false
                            } catch (e: Exception) {
                                statusMessage = "Ошибка открытия счёта: ${e.message}"
                                isError = true
                            }
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRefreshing
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Открыть новый счёт песочницы")
                    }
                }
            }

            // --- Кнопка закрытия счёта песочницы (только если выбран счёт) ---
            if (sandboxMode && defaultAccountId.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showCloseDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Закрыть счёт песочницы")
                }
            }
        }

        // --- Инструкция (без изменений) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📋 Как получить токен", style = MaterialTheme.typography.titleMedium)
//                Text(
//                    """
//                    1. Зайдите в личный кабинет Т‑Инвестиций
//                    2. Перейдите в раздел «Настройки» → «Токены API»
//                    3. Нажмите «Создать новый токен»
//                    4. Выберите права: чтение портфеля и совершение сделок
//                    5. Скопируйте полученный токен
//                    6. Вставьте его в поле выше
//
//                    ⚠️ Рекомендации по безопасности:
//                    • Сначала тестируйте в режиме песочницы
//                    • Не передавайте токен третьим лицам
//                    • Токен хранится в зашифрованном виде
//                    """.trimIndent(),
//                    style = MaterialTheme.typography.bodySmall
//                )
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
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // --- Статусное сообщение ---
        statusMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text(message, modifier = Modifier.padding(16.dp))
            }
        }
    }

    // --- Диалог подтверждения закрытия счёта ---
    if (showCloseDialog) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { Text("Закрытие счёта песочницы") },
            text = { Text("Вы уверены, что хотите закрыть счёт «${availableAccounts.find { it.id == defaultAccountId }?.name}»? Это действие необратимо.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isRefreshing = true
                            try {
                                settingsViewModel.closeSandboxAccount(defaultAccountId)
                                when (val accountsResult = settingsViewModel.getAccountsResult("TInvest", sandboxMode)) {
                                    is AppResult.Success -> availableAccounts = accountsResult.data
                                    is AppResult.Failure ->
                                        AppLogger.w("TInvestSettingsPanel", "reload accounts failed: ${accountsResult.error.message}", accountsResult.error.cause)
                                }
                                defaultAccountId = ""
                                settingsViewModel.saveDefaultAccountId("TInvest", "")
                                statusMessage = "Счёт песочницы закрыт"
                                isError = false
                            } catch (e: Exception) {
                                statusMessage = "Ошибка закрытия счёта: ${e.message}"
                                isError = true
                            }
                            isRefreshing = false
                            showCloseDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Закрыть")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun BcsSettingsPanel() {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    var refreshToken by remember { mutableStateOf("") }
    var isWriteMode by remember { mutableStateOf(true) }
    var connected by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val creds = settingsViewModel.loadBrokerCredentials("bcs")
        if (creds != null) {
            refreshToken = creds.first
            connected = true
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Статус подключения
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (connected) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Статус API", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (connected) "✅ Подключено" else "❌ Не подключено",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (connected) {
                    Button(
                        onClick = {
                            settingsViewModel.clearBrokerCredentials("bcs")
                            connected = false
                            refreshToken = ""
                            showToken = false
                            statusMessage = "Подключение к БКС разорвано"
                            isError = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Отключить")
                    }
                }
            }
        }

        // Поле токена
        OutlinedTextField(
            value = refreshToken,
            onValueChange = { refreshToken = it },
            label = { Text("Токен доступа") },
            placeholder = { Text("Введите refresh‑токен из личного кабинета") },
            visualTransformation = if (showToken) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "Скрыть" else "Показать")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !connected
        )

        // Права доступа
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Права доступа", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (isWriteMode) "Полный доступ (торговля)" else "Только чтение",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isWriteMode,
                onCheckedChange = { isWriteMode = it },
                enabled = !connected
            )
        }

        // Кнопки
        if (connected) {
            Button(
                onClick = {
                    settingsViewModel.clearBrokerCredentials("bcs")
                    connected = false
                    refreshToken = ""
                    showToken = false
                    statusMessage = "Подключение к БКС разорвано"
                    isError = false
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Отключить")
            }
        } else {
            Button(
                    onClick = {
                        scope.launch {
                            val success = settingsViewModel.initializeBcs(refreshToken, isWriteMode)
                            connected = success
                            statusMessage = if (success) {
                                "Подключено к БКС (${if (isWriteMode) "полный доступ" else "только чтение"})"
                            } else {
                                "Ошибка подключения"
                            }
                            isError = !success
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = refreshToken.isNotBlank()
                ) {
                    Text("Подключиться")
                }
        }

        // Инструкция
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
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
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Статусное сообщение
        statusMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text(message, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
fun FinamSettingsPanel() {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Загрузка сохранённых токенов
    LaunchedEffect(Unit) {
        val savedToken = settingsViewModel.getToken("finam")
        if (savedToken != null) {
            token = savedToken
            connected = true
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Статус подключения
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (connected)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Статус API", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (connected) "✅ Подключено" else "❌ Не подключено",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (connected) {
                    Button(
                        onClick = {
                            settingsViewModel.clearBrokerCredentials("finam")
                            connected = false
                            token = ""
                            showToken = false
                            statusMessage = "Подключение к Finam разорвано"
                            isError = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
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
            visualTransformation = if (showToken)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "Скрыть" else "Показать")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !connected
        )

        // Кнопка подключения
        if (!connected) {
            Button(
                onClick = {
                    scope.launch {
                        val success = settingsViewModel.initializeFinam(token)
                        connected = success
                        statusMessage = if (success) "Подключено к Finam" else "Ошибка подключения"
                        isError = !success
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = token.isNotBlank()
            ) {
                Text("Подключиться")
            }
        }

        // Инструкция
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
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
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Статусное сообщение
        statusMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isError)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.tertiaryContainer
                )
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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
        }

        Text(
            "Настройки торговли",
            style = MaterialTheme.typography.headlineSmall
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Подтверждение заявок", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Показывать диалог перед отправкой",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = uiState.isConfirmOrdersEnabled,
                onCheckedChange = { enabled ->
                    settingsViewModel.setConfirmOrdersEnabled(enabled)
                }
            )
        }

        // Здесь можно добавить другие настройки торговли в будущем
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSettingsContent(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("О приложении") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text("TScalp", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Версия: ${BuildConfig.VERSION_NAME}")   // ← достаточно
            Spacer(modifier = Modifier.height(16.dp))
            Text("Разработчик: Масленников Андрей", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Репозиторий: github.com/biomorf/TScalp", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            val context = LocalContext.current
            Text(
                text = "Проверить обновления: https://github.com/biomorf/TScalp/releases",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://github.com/biomorf/TScalp/releases")
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}