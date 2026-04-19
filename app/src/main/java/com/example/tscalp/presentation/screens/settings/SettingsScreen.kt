package com.example.tscalp.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    var token by remember { mutableStateOf("") }
    var sandboxMode by remember { mutableStateOf(true) }
    var showToken by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }

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

        // Статус подключения
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected)
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
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Статус API",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isConnected) "✅ Подключено" else "❌ Не подключено",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (isConnected) {
                    Button(
                        onClick = { isConnected = false },
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
            enabled = !isConnected
        )

        // Переключатель режима песочницы
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
                enabled = !isConnected
            )
        }

        // Кнопка подключения
        Button(
            onClick = {
                if (token.isNotBlank()) {
                    isConnected = true
                    token = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = token.isNotBlank() && !isConnected
        ) {
            Text("Подключиться к API")
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Инструкция
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