package com.example.tscalp.presentation.screens.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.tscalp.domain.models.PortfolioPosition
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.*
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.scale

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
                        PortfolioPositionCard(
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
fun PortfolioPositionCard(
    position: PortfolioPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    instrumentType: String = "",
    priceChangePercent: Double? = null,
    brokerName: String? = null,
    accountName: String? = null
) {
    // --- Анимация цвета цены ---
    val targetPriceColor = when {
        priceChangePercent == null -> MaterialTheme.colorScheme.onSurface
        priceChangePercent >= 0 -> Color(0xFF2E7D32)   // зелёный
        else -> Color(0xFFC62828)                      // красный
    }
    val priceColor by animateColorAsState(targetPriceColor, animationSpec = tween(600))

    // --- Анимация масштаба при изменении цены ---
    var priceChanged by remember { mutableStateOf(false) }
    LaunchedEffect(position.currentPrice) {
        priceChanged = true
        delay(500)
        priceChanged = false
    }
    val textScale by animateFloatAsState(
        targetValue = if (priceChanged) 1.05f else 1f,
        animationSpec = spring()
    )

    // --- Остальной код карточки ---
    val typeColor = when (instrumentType) {
        "share" -> Color(0xFF1565C0)
        "bond" -> Color(0xFFE65100)
        "etf" -> Color(0xFF2E7D32)
        "currency" -> Color(0xFF6A1B9A)
        else -> Color(0xFF757575)
    }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(modifier = Modifier.padding(start = 3.dp)) { // Полоса тоньше
            // Цветовая полоса слева
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(IntrinsicSize.Max)
                    .background(typeColor)
            )

            Column(modifier = Modifier.padding(12.dp)) {
                // Верхний ряд: тикер, название, текущая цена
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(position.ticker, fontWeight = FontWeight.Bold)
                        Text(
                            position.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (position.currentPrice > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                formatCurrency(position.currentPrice),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = priceColor,
                                modifier = Modifier.scale(textScale)  // <-- вместо textScale
                            )
                            if (priceChangePercent != null) {
                                Text(
                                    "${if (priceChangePercent >= 0) "+" else ""}${"%.2f".format(priceChangePercent)}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = priceColor
                                )
                            }
                        }
                    } else {
                        Text("—", fontWeight = FontWeight.Bold)
                    }
                }

                // Блок с количеством и общей стоимостью (если позиция есть)
                if (position.quantity != 0L) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Количество", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${position.quantity} шт.", style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Стоимость", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatCurrency(position.totalValue), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Показываем брокера и счёт, если заданы
                if (!brokerName.isNullOrBlank() && !accountName.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Брокер/Счёт", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$brokerName / $accountName", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Прибыль/убыток (P&L) – отдельно, если есть
                if (position.quantity != 0L && position.profit != 0.0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("P&L", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                formatCurrency(position.profit),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (position.profit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                            Text(
                                "(${"%.2f".format(position.profitPercent)}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (position.profit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                }
            }
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

fun formatCurrency(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("ru", "RU"))
    format.currency = Currency.getInstance("RUB")
    return format.format(value)
}