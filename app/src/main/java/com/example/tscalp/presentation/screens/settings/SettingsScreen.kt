package com.example.tscalp.presentation.screens.settings

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.presentation.screens.orders.OrdersViewModel
import com.example.tscalp.presentation.screens.orders.OrdersViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tscalp.data.api.BcsBrokerApi
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.mutableIntStateOf
import com.example.tscalp.presentation.screens.orders.OrdersUiState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.VisualTransformation


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
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrokerSettingsContent(onBack: () -> Unit) {
    val ordersViewModel: OrdersViewModel = viewModel(factory = OrdersViewModelFactory())
    val uiState by ordersViewModel.uiState.collectAsState()

    val brokerNames = remember { ServiceLocator.getBrokerManager().getAvailableBrokers() }
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
            TabRow(selectedTabIndex = selectedTabIndex) {
                brokerNames.forEach { broker ->
                    Tab(
                        selected = selectedTabIndex == brokerNames.indexOf(broker),
                        onClick = { selectedTabIndex = brokerNames.indexOf(broker) },
                        text = { Text(broker) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = true
            ) { page ->
                val brokerName = brokerNames[page]
                when (brokerName) {
                    "TInvest" -> TInvestSettingsPanel(ordersViewModel, uiState)
                    "bcs" -> BcsSettingsPanel()
                    "mock" -> MockSettingsPanel()
                    else -> Text("Настройки для $brokerName пока не реализованы")
                }
            }
        }
    }
}

@Composable
fun TInvestSettingsPanel(ordersViewModel: OrdersViewModel, uiState: OrdersUiState) {
    var token by remember { mutableStateOf("") }
    var sandboxMode by remember { mutableStateOf(true) }
    var showToken by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val creds = ServiceLocator.loadBrokerCredentials("TInvest")
        if (creds != null) {
            token = creds.first
            sandboxMode = creds.second
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())   // ← позволяет скроллить
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val isConnected = uiState.isApiInitialized && ServiceLocator.loadBrokerCredentials("TInvest") != null
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected) MaterialTheme.colorScheme.tertiaryContainer
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
                        if (isConnected) "✅ Подключено" else "❌ Не подключено",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (isConnected) {
                    Button(
                        onClick = {
                            ServiceLocator.clearBrokerCredentials("TInvest")
                            ordersViewModel.checkApiInitialization()
                            token = ""
                            statusMessage = "Подключение к Т‑Инвестициям разорвано"
                            isError = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Отключить")
                    }
                }
            }
        }

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

        if (!isConnected) {
            Button(
                onClick = {
                    scope.launch {
                        try {
                            ServiceLocator.saveBrokerCredentials("TInvest", token, sandboxMode)
                            ordersViewModel.initializeApi(token, sandboxMode)
                            token = ""
                            statusMessage = "Подключено к Т‑Инвестициям (режим ${if (sandboxMode) "песочница" else "боевой"})"
                            isError = false
                        } catch (e: Exception) {
                            statusMessage = "Ошибка подключения: ${e.message}"
                            isError = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = token.isNotBlank()
            ) {
                Text("Подключиться")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📋 Как получить токен", style = MaterialTheme.typography.titleMedium)
                Text(
                    """
                    1. Зайдите в личный кабинет Т‑Инвестиций
                    2. Перейдите в раздел «Настройки» → «Токены API»
                    3. Нажмите «Создать новый токен»
                    4. Выберите права: чтение портфеля и совершение сделок
                    5. Скопируйте полученный токен
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
fun BcsSettingsPanel() {
    var refreshToken by remember { mutableStateOf("") }
    var isWriteMode by remember { mutableStateOf(true) }
    var connected by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val creds = ServiceLocator.loadBrokerCredentials("bcs")
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
                            ServiceLocator.clearBrokerCredentials("bcs")
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
                    ServiceLocator.clearBrokerCredentials("bcs")
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
                        val clientId = if (isWriteMode) "trade-api-write" else "trade-api-read"
                        try {
                            val bcsApi = ServiceLocator.getBrokerManager().getBroker("bcs") as? BcsBrokerApi
                            bcsApi?.initialize(refreshToken, clientId)
                            ServiceLocator.saveBrokerCredentials("bcs", refreshToken, isWriteMode)
                            connected = true
                            statusMessage = "Подключено к БКС (${if (isWriteMode) "полный доступ" else "только чтение"})"
                            isError = false
                        } catch (e: Exception) {
                            statusMessage = "Ошибка подключения: ${e.message}"
                            isError = true
                        }
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
fun MockSettingsPanel() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Text(
            "Mock‑брокер не требует подключения. Вы можете использовать его для тестирования интерфейса.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun TradeSettingsContent(onBack: () -> Unit) {
    var confirmOrdersEnabled by remember { mutableStateOf(ServiceLocator.isConfirmOrdersEnabled()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
                checked = confirmOrdersEnabled,
                onCheckedChange = { enabled ->
                    confirmOrdersEnabled = enabled
                    ServiceLocator.setConfirmOrdersEnabled(enabled)
                }
            )
        }

        // Здесь можно добавить другие настройки торговли в будущем
    }
}