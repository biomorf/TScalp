package com.example.tscalp.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.presentation.screens.orders.OrdersViewModel
import com.example.tscalp.presentation.screens.orders.OrdersViewModelFactory

/**
 * Экран настроек приложения.
 * Позволяет ввести токен доступа, выбрать режим (боевой/песочница),
 * подключиться к API или отключиться от него.
 */
@Composable
fun SettingsScreen(
    ordersViewModel: OrdersViewModel   // получаем извне
) {
    // Получаем OrdersViewModel для доступа к общему состоянию API
    //val ordersViewModel: OrdersViewModel = viewModel(factory = OrdersViewModelFactory())
    val uiState by ordersViewModel.uiState.collectAsState()

    // Локальное состояние для ввода токена и режима
    var token by remember { mutableStateOf("") }
    var sandboxMode by remember { mutableStateOf(true) }
    var showToken by remember { mutableStateOf(false) }

    // Локальное состояние для статусных сообщений (ошибки/успех)
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ordersViewModel.checkApiInitialization()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                text = "Настройки API",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Текущий статус подключения
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.isApiInitialized)
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
                    Text(
                        text = "Статус API",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (uiState.isApiInitialized)
                            "✅ Подключено"
                        else
                            "❌ Не подключено",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                // Кнопка отключения (появляется только при активном соединении)
                if (uiState.isApiInitialized) {
                    Button(
                        onClick = {
                            // Очищаем глобальное состояние API
                            ServiceLocator.clear()
                            // Обновляем состояние ViewModel
                            ordersViewModel.checkApiInitialization()
                            // Показываем сообщение об успехе
                            statusMessage = "API отключён"
                            isError = false
                            // Сбрасываем поле ввода токена
                            token = ""
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

        // Поле ввода токена
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Токен доступа") },
            placeholder = { Text("Введите токен из личного кабинета") },
            visualTransformation = if (showToken)
                androidx.compose.ui.text.input.VisualTransformation.None
            else
                PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "Скрыть" else "Показать")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uiState.isApiInitialized  // блокируем при активном подключении
        )

        // Переключатель режима песочницы
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Режим песочницы",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Используйте для тестирования без реальных денег",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = sandboxMode,
                onCheckedChange = { sandboxMode = it },
                enabled = !uiState.isApiInitialized
            )
        }

        // Кнопка подключения (видна только при отсутствии соединения)
        if (!uiState.isApiInitialized) {
            Button(
                onClick = {
                    if (token.isNotBlank()) {
                        try {
                            // Вызываем инициализацию API через ViewModel
                            ordersViewModel.initializeApi(token, sandboxMode)
                            // После успеха очищаем поле токена и показываем сообщение
                            token = ""
                            statusMessage = "API подключен (режим: ${if (sandboxMode) "песочница" else "боевой"})"
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
                Text("Подключиться к API")
            }
        }

        // Отображение статусных сообщений (ошибки/успех)
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
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Инструкция по получению токена
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📋 Как получить токен",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = """
                    1. Зайдите в личный кабинет Т-Инвестиций
                    2. Перейдите в раздел "Настройки" → "Токены API"
                    3. Нажмите "Создать новый токен"
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
}