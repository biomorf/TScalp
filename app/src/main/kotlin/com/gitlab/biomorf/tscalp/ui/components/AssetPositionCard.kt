package com.gitlab.biomorf.tscalp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.domain.models.PortfolioPosition
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import com.gitlab.biomorf.tscalp.util.PositionFormatter
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.tooling.preview.Preview
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.ui.theme.TScalpTheme

/**
 * Содержимое карточки позиции без обёртки Card.
 *
 * [tradingAvailability] в текущей реализации не используется —
 * данные передаются из UI (PortfolioScreen, OrdersScreen), но
 * визуальный индикатор статуса доступности инструмента ещё
 * не реализован. См. ROADMAP → «Индикатор доступности
 * инструмента в карточке позиции».
 *
 * Архитектурный долг: функция требует декомпозиции.
 * См. ROADMAP → «Рефакторинг Compose-компонентов».
 */
@Suppress("LongMethod")
@Composable
fun AssetPositionCard(
    modifier: Modifier = Modifier,
    position: PortfolioPosition,
    instrumentType: String = "",
    priceChangePercent: Double? = null,
    onDelete: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    resetSwipe: Boolean = false,
    tscalpInstrumentId: String? = null,
    tradingAvailability: TradingAvailability? = null,
    pointValue: Double? = null,
) {
    // --- Цветовая индикация изменения цены (состояния) ---
    var previousPrice by remember { mutableDoubleStateOf(position.currentPrice) }
    var priceDelta by remember { mutableDoubleStateOf(0.0) }

    val isPriceUp = position.currentPrice > previousPrice
    val isPriceDown = position.currentPrice < previousPrice

    // Фон карточки подсвечивается при изменении
    val backgroundColor by animateColorAsState(
        targetValue =
            when {
                isPriceUp && previousPrice != 0.0 -> Color(0x2200C853)
                isPriceDown && previousPrice != 0.0 -> Color(0x22FF1744)
                else -> Color.Transparent
            },
        animationSpec = tween(durationMillis = 300),
    )

    // Единый LaunchedEffect: и запоминание цены, и показ popup
    LaunchedEffect(position.currentPrice) {
        if (previousPrice != 0.0 && position.currentPrice != previousPrice) {
            priceDelta = position.currentPrice - previousPrice
        }
        previousPrice = position.currentPrice
    }

    // Содержимое карточки (общее для всех случаев)
    val cardContent = @Composable {
        Box(
            modifier =
                Modifier
                    .background(backgroundColor, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp)),
        ) {
            AssetCardContent(
                position = position,
                instrumentType = instrumentType,
                priceChangePercent = priceChangePercent,
                onClick = onClick,
                isSelected = isSelected,
                tscalpInstrumentId = tscalpInstrumentId,
                tradingAvailability = tradingAvailability,
                pointValue = pointValue,
            )
        }
    }

    // Если свайп не нужен — просто показываем карточку с индикацией
    if (onDelete == null && onSettings == null) {
        cardContent()
        return
    }

    // --- Карточка со свайпом ---
    var offsetX by remember { mutableFloatStateOf(0f) }
    val buttonWidth = 72.dp
    val threshold = buttonWidth * 2
    val density = LocalDensity.current

    // Плавный возврат при resetSwipe = true
    LaunchedEffect(resetSwipe) {
        if (resetSwipe) {
            val target = 0f
            androidx.compose.animation.core.animate(
                initialValue = offsetX,
                targetValue = target,
                animationSpec = tween(300),
            ) { value, _ -> offsetX = value }
        }
    }

    Box(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
        // Кнопки подложки
        Row(
            modifier =
                Modifier
                    .matchParentSize()
                    .wrapContentSize(Alignment.CenterEnd)
                    .width(buttonWidth * 2),
            horizontalArrangement = Arrangement.End,
        ) {
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier =
                        Modifier
                            .size(buttonWidth)
                            .background(Color(0xFFC62828), shape = CircleShape),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.White)
                }
            }
            if (onSettings != null) {
                IconButton(
                    onClick = onSettings,
                    modifier =
                        Modifier
                            .size(buttonWidth)
                            .background(Color(0xFF757575), shape = CircleShape),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Color.White)
                }
            }
        }

        // Перетаскиваемая карточка (внутри неё анимации и popup)
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .draggable(
                        state =
                            rememberDraggableState { delta ->
                                offsetX =
                                    (offsetX + delta).coerceIn(
                                        -with(density) { threshold.toPx() },
                                        0f,
                                    )
                            },
                        orientation = Orientation.Horizontal,
                    ),
        ) {
            cardContent()
        }
    }
}

// Архитектурный долг: функция требует декомпозиции.
// См. ROADMAP → «Рефакторинг Compose-компонентов».
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun AssetCardContent(
    position: PortfolioPosition,
    instrumentType: String,
    priceChangePercent: Double?,
    onClick: (() -> Unit)?,
    isSelected: Boolean,
    tscalpInstrumentId: String? = null,
    tradingAvailability: TradingAvailability? = null,
    pointValue: Double? = null,
    modifier: Modifier = Modifier,
) {
    // --- Анимация цвета цены ---
    val targetPriceColor = PositionFormatter.priceChangeColor(priceChangePercent)
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
        animationSpec = spring(),
    )

    val typeColor =
        when (instrumentType) {
            "share" -> Color(0xFF1565C0)
            "bond" -> Color(0xFFE65100)
            "etf" -> Color(0xFF2E7D32)
            "currency" -> Color(0xFF6A1B9A)
            else -> Color(0xFF757575)
        }

    val backgroundColor =
        if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Max).padding(start = 4.dp)) {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(typeColor),
            )

            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(position.ticker, fontWeight = FontWeight.Bold)
                        Text(
                            position.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Упрощённое нейтральное отображение идентификатора
                        val displayId = if (tscalpInstrumentId.isNullOrBlank()) "000000" else tscalpInstrumentId
                        Text(
                            text = displayId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val (priceStr, rubStr) = PositionFormatter.formatCurrentPrice(position, instrumentType, pointValue)
                    if (priceStr != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                priceStr,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = priceColor,
                                modifier = Modifier.scale(textScale),
                            )
                            if (rubStr != null) {
                                Text(
                                    rubStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Text("—", fontWeight = FontWeight.Bold)
                    }
                }

                if (position.quantity != 0L || priceChangePercent != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Левая часть: средняя стоимость + детали
                        Column {
                            val (totalStr, detailStr) = PositionFormatter.formatAverage(position, instrumentType)
                            Text(
                                text = totalStr ?: "-",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = detailStr ?: "${position.quantity} лот  ·  -",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        // Правая часть: прибыль / убыток
                        Column(horizontalAlignment = Alignment.End) {
                            val (profitStr, profitColor, percentStr) = PositionFormatter.formatProfit(position, instrumentType, pointValue)
                            if (profitStr != null) {
                                Text(
                                    text = profitStr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = profitColor,
                                    fontWeight = FontWeight.Medium,
                                )
                            } else {
                                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (percentStr != null) {
                                Text(
                                    text = percentStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (position.profitPercent != null && position.profitPercent >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                )
                            } else {
                                Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Preview(name = "Share — profit", showBackground = true, widthDp = 400)
@Composable
private fun AssetPositionCardShareProfitPreview() {
    TScalpTheme {
        AssetPositionCard(
            position = PortfolioPosition(
                tscalpInstrumentId = "uid-1",
                brokerName = BrokerName.TINVEST,
                name = "Сбербанк",
                ticker = "SBER",
                quantity = 10,
                currentPrice = 250.5,
                totalValue = 2505.0,
                averagePrice = 200.0,
                profit = 505.0,
                profitPercent = 25.25,
                instrumentType = "share",
                isin = "RU0009029540",
                classCode = "TQBR",
            ),
            instrumentType = "share",
            priceChangePercent = 1.5,
            onClick = {},
            onDelete = {},
            onSettings = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "Share — loss", showBackground = true, widthDp = 400)
@Composable
private fun AssetPositionCardShareLossPreview() {
    TScalpTheme {
        AssetPositionCard(
            position = PortfolioPosition(
                tscalpInstrumentId = "uid-2",
                brokerName = BrokerName.TINVEST,
                name = "Газпром",
                ticker = "GAZP",
                quantity = 10,
                currentPrice = 150.0,
                totalValue = 1500.0,
                averagePrice = 200.0,
                profit = -500.0,
                profitPercent = -25.0,
                instrumentType = "share",
                isin = "RU0007661625",
                classCode = "TQBR",
            ),
            instrumentType = "share",
            priceChangePercent = -2.0,
            onClick = {},
            onDelete = {},
            onSettings = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "Futures", showBackground = true, widthDp = 400)
@Composable
private fun AssetPositionCardFuturesPreview() {
    TScalpTheme {
        AssetPositionCard(
            position = PortfolioPosition(
                tscalpInstrumentId = "uid-3",
                brokerName = BrokerName.TINVEST,
                name = "Фьючерс Si",
                ticker = "SiU4",
                quantity = 2,
                currentPrice = 85000.0,
                totalValue = 170000.0,
                averagePrice = 84000.0,
                profit = 2000.0,
                profitPercent = 2.38,
                instrumentType = "futures",
                classCode = "SPBFUT",
                pointValue = 1.0,
            ),
            instrumentType = "futures",
            priceChangePercent = 0.5,
            pointValue = 1.0,
            onClick = {},
            onDelete = {},
            onSettings = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
