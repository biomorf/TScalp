package com.example.tscalp.presentation.screens.portfolio

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import java.util.*
import androidx.compose.runtime.getValue
import com.example.tscalp.util.formatCurrency
import com.example.tscalp.ui.components.AssetPositionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel = viewModel(factory = PortfolioViewModelFactory())
) {
    val uiState by viewModel.uiState.collectAsState()

    // Автоматическое обновление при открытии вкладки
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Портфель") },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !uiState.isLoading && uiState.isApiInitialized
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (!uiState.isApiInitialized) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                ApiNotInitializedCard()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Индикатор загрузки
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // Свободные средства и кнопка пополнения

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Свободные средства", style = MaterialTheme.typography.titleMedium)
                            Text(
                                formatCurrency(uiState.balance),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (uiState.sandboxMode) {
                            Button(onClick = { viewModel.payInSandbox() }) {
                                Text("Пополнить")
                            }
                        }
                    }
                }


            // Сообщение об ошибке
            uiState.statusMessage?.let { message ->

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(message, modifier = Modifier.padding(16.dp))
                    }

            }

            // Пустой портфель
            if (uiState.positions.isEmpty()) {
                EmptyPortfolioCard()
            } else {
                // Группировка по брокерам
                val grouped = uiState.positions.groupBy { it.brokerName }
                for ((brokerName, positions) in grouped) {
                    Text(
                            "--- $brokerName ---",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                    positions.forEach { position ->
                        AssetPositionCard(
                            position = position,
                            instrumentType = position.instrumentType,
                            priceChangePercent = position.priceChangePercent
                            // onDelete и onSettings не передаём — полоса и анимация будут без свайпа
                        )
                    }
                    if (brokerName != grouped.keys.last()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
            // Распорный элемент (необязательно, но для теста можно оставить)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ApiNotInitializedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚠️ API не подключен",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Перейдите в Настройки и введите токен доступа",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyPortfolioCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📊 Портфель пуст",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "У вас нет активных позиций",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
