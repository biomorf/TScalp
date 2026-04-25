package com.example.tscalp.presentation.screens.portfolio

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tscalp.domain.models.PortfolioPosition
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.*

@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel = viewModel(factory = PortfolioViewModelFactory())
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkApiInitialization()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        /// Заголовок с кнопкой обновления
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Портфель",
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(
                onClick = { viewModel.refresh() },
                enabled = !uiState.isLoading && uiState.isApiInitialized
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Обновить"
                )
            }
        }

        /// Проверка инициализации API
        if (!uiState.isApiInitialized) {
            ApiNotInitializedCard()
            return@Column
        }

        /// Индикатор загрузки
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Загрузка портфеля...")
                }
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
                // Кнопка пополнения только в песочнице
                if (uiState.sandboxMode) {
                    Button(onClick = { viewModel.payInSandbox() }) {
                        Text("Пополнить")
                    }
                }
            }
        }

        /// Общая стоимость портфеля
        if (uiState.totalValue > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Общая стоимость",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = formatCurrency(uiState.totalValue),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        /// Статусное сообщение
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

        /// Список позиций
        if (uiState.positions.isEmpty() && !uiState.isLoading) {
            EmptyPortfolioCard()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.positions) { position ->
                    PortfolioPositionCard(
                        position = position,
                        instrumentType = position.instrumentType
                    )
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

@Composable
fun PortfolioPositionCard(
    modifier: Modifier = Modifier,
    position: PortfolioPosition,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    instrumentType: position.instrumentType
) {

    // Цвет полосы в зависимости от типа инструмента
    val typeColor = when (instrumentType) {
        "share" -> Color(0xFF1976D2)      // синий для акций
        "bond" -> Color(0xFFFFA000)       // оранжевый для облигаций
        "etf" -> Color(0xFF388E3C)        // зелёный для фондов
        "currency" -> Color(0xFF7B1FA2)   // фиолетовый для валют
        else -> Color.Gray
    }

    // Цвет цены в зависимости от изменения
    val priceColor = if (position.priceChangePercent != null) {
        if (position.priceChangePercent >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
    } else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(start = 4.dp)) {
            // Цветовая полоса слева
            Box(
                modifier = Modifier
                    .width(4.dp)
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
                            maxLines = 1
                        )
                    }
                    if (position.currentPrice > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                formatCurrency(position.currentPrice),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = priceColor
                            )
                            // Процент изменения
                            if (position.priceChangePercent != null) {
                                Text(
                                    "${if (position.priceChangePercent >= 0) "+" else ""}${
                                        "%.2f".format(
                                            position.priceChangePercent
                                        )
                                    }%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = priceColor
                                )
                            }
                        }
                    } else {
                        Text("—", fontWeight = FontWeight.Bold)
                    }
                }
                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Количество, средняя цена, P&L – показываем только если позиция есть (quantity != 0)
                if (position.quantity != 0L) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Количество",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${position.quantity} шт.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Текущая цена",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatCurrency(position.currentPrice),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                }
            }
        }

    }

}

fun formatCurrency(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("ru", "RU"))
    format.currency = Currency.getInstance("RUB")
    return format.format(value)
}