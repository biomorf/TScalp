package com.example.tscalp.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tscalp.presentation.screens.orders.OrdersViewModel

@Composable
fun SettingsScreen(
    ordersViewModel: OrdersViewModel = viewModel()
) {
    var token by remember { mutableStateOf("") }
    var sandboxMode by remember { mutableStateOf(true) }
    val uiState by ordersViewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Настройки API",
            style = MaterialTheme.typography.headlineSmall
        )
        
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Токен доступа") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Режим песочницы")
            Switch(
                checked = sandboxMode,
                onCheckedChange = { sandboxMode = it }
            )
        }
        
        Button(
            onClick = {
                if (token.isNotBlank()) {
                    ordersViewModel.initializeApi(token, sandboxMode)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = token.isNotBlank()
        ) {
            Text("Подключиться к API")
        }
        
        if (uiState.isApiInitialized) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "✅ API подключен",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        uiState.statusMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isError) 
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
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        Text(
            text = "Информация",
            style = MaterialTheme.typography.titleMedium
        )
        
        Text(
            text = """
                Для работы приложения необходим токен доступа к API Т-Инвестиций.
                
                1. Зайдите в личный кабинет Т-Инвестиций
                2. Перейдите в раздел "Настройки" → "Токены API"
                3. Создайте новый токен с правами на торговлю
                4. Скопируйте токен и вставьте его здесь
                
                Рекомендуется сначала протестировать приложение в режиме песочницы.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}