package com.example.tscalp.presentation.screens.portfolio

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Snackbar
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
//import java.util.*
import androidx.compose.runtime.getValue
import com.example.tscalp.util.formatCurrency
import com.example.tscalp.ui.components.AssetPositionCard
//import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel = viewModel(factory = PortfolioViewModelFactory())
) {
    val uiState by viewModel.uiState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Pull‑to‑Refresh состояние
    val pullToRefreshState = rememberPullToRefreshState()

    // Автоматическое обновление при открытии вкладки
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    // Синхронизация isRefreshing с isLoading
    LaunchedEffect(uiState.isLoading) {
        isRefreshing = uiState.isLoading
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

        // Pull-to-Refresh контейнер
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Индикатор загрузки (показываем, если не используется pull-to-refresh)
                if (uiState.isLoading && !isRefreshing) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
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
                            Text("Баланс", style = MaterialTheme.typography.titleMedium)
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
                LaunchedEffect(uiState.statusMessage) {
                    uiState.statusMessage?.let { message ->
                        val visuals = object : SnackbarVisuals {
                            override val message: String = message
                            override val actionLabel: String? = if (uiState.isError) "OK" else null
                            override val withDismissAction: Boolean = false
                            override val duration: SnackbarDuration =
                                if (uiState.isError) SnackbarDuration.Indefinite
                                else SnackbarDuration.Short
                        }
                        // Скрыть предыдущий снекбар, если висит
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(visuals)
                        viewModel.clearStatus()
                    }
                }

                // Пустой портфель
                if (uiState.positions.isEmpty() && !uiState.isLoading) {
                    EmptyPortfolioCard()
                } else if (uiState.positions.isNotEmpty()) {
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
                            )
                        }
                        if (brokerName != grouped.keys.last()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            ) { data ->
                val label = data.visuals.actionLabel
                Snackbar(
                    containerColor = if (uiState.isError)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (uiState.isError)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    action = {
                        if (label != null) {
                            TextButton(onClick = { data.dismiss() }) {
                                Text(label)
                            }
                        }
                    }
                ) {
                    Text(data.visuals.message)
                }
            }
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
