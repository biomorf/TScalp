package com.example.tscalp.presentation.screens.settings

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerSettingsContent(onBack: () -> Unit) {
    val ordersViewModel: OrdersViewModel = viewModel(factory = OrdersViewModelFactory())
    val uiState by ordersViewModel.uiState.collectAsState()

    var selectedBroker by remember { mutableStateOf("tinkoff") }
    var token by remember { mutableStateOf("") }
    var sandboxMode by remember { mutableStateOf(true) }
    var showToken by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(selectedBroker) {
        val creds = ServiceLocator.loadBrokerCredentials(selectedBroker)
        if (creds != null) {
            token = creds.first
            sandboxMode = creds.second
        } else {
            token = ""
            sandboxMode = true
        }
        statusMessage = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Кнопка «Назад»
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
        }

        Text(
            "Настройки подключения",
            style = MaterialTheme.typography.headlineSmall
        )

        // Выбор брокера
        var brokerExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = brokerExpanded,
            onExpandedChange = { brokerExpanded = it }
        ) {
            TextField(
                value = selectedBroker,
                onValueChange = {},
                readOnly = true,
                label = { Text("Брокер") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brokerExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = brokerExpanded,
                onDismissRequest = { brokerExpanded = false }
            ) {
                ServiceLocator.getBrokerManager().getAvailableBrokers().forEach { broker ->
                    DropdownMenuItem(
                        text = { Text(broker) },
                        onClick = {
                            selectedBroker = broker
                            brokerExpanded = false
                        }
                    )
                }
            }
        }

        when (selectedBroker) {
            "tinkoff" -> {
                val isConnected = uiState.isApiInitialized && ServiceLocator.loadBrokerCredentials("tinkoff") != null
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
                                    ServiceLocator.clearBrokerCredentials("tinkoff")
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
                    visualTransformation = if (showToken)
                        androidx.compose.ui.text.input.VisualTransformation.None
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
                            if (token.isNotBlank()) {
                                try {
                                    ServiceLocator.saveBrokerCredentials("tinkoff", token, sandboxMode)
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
            }

            "mock" -> {
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

            "bcs" -> {
                var refreshToken by remember { mutableStateOf("") }
                var sandbox by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    val creds = ServiceLocator.loadBrokerCredentials("bcs")
                    if (creds != null) {
                        refreshToken = creds.first
                        sandbox = creds.second
                    }
                }

                Column {
                    Text("Refresh-токен (из личного кабинета БКС)")
                    OutlinedTextField(
                        value = refreshToken,
                        onValueChange = { refreshToken = it },
                        label = { Text("Refresh Token") },
                        singleLine = true
                    )
                    Row {
                        Text("Режим песочницы")
                        Switch(
                            checked = sandbox,
                            onCheckedChange = { sandbox = it }
                        )
                    }
                    Button(onClick = {
                        if (refreshToken.isNotBlank()) {
                            ServiceLocator.saveBrokerCredentials("bcs", refreshToken, sandbox)
                            // Можно сразу инициализировать API
                            try {
                                val bcsApi = ServiceLocator.getBrokerManager().getBroker("bcs") as? BcsBrokerApi
                                bcsApi?.initialize(refreshToken, sandbox)
                                statusMessage = "Подключено к БКС (${if (sandbox) "песочница" else "боевой"})"
                                isError = false
                            } catch (e: Exception) {
                                statusMessage = "Ошибка подключения: ${e.message}"
                                isError = true
                            }
                        }
                    }) {
                        Text("Подключиться")
                    }
                }
            }

            else -> {
                Text("Настройки для брокера $selectedBroker пока не реализованы.")
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