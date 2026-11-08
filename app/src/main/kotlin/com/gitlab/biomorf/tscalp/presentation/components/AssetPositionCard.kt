package com.gitlab.biomorf.tscalp.presentation.components

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Future
import com.gitlab.biomorf.tscalp.domain.models.FuturePosition
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.SharePosition
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import com.gitlab.biomorf.tscalp.domain.models.moneyPerPointOfPrice
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme
import com.gitlab.biomorf.tscalp.presentation.theme.getInstrumentTypeColor
import com.gitlab.biomorf.tscalp.presentation.util.PositionFormatter
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Содержимое карточки позиции без обёртки Card.
 *
 * [tradingAvailability] в текущей реализации не используется —
 * данные передаются из UI (PortfolioScreen, OrdersScreen), но
 * визуальный индикатор статуса доступности инструмента ещё
 * не реализован. См. ROADMAP → «Индикатор доступности
 * инструмента в карточке позиции».
 */
@Composable
private fun BoxScope.SwipeActionButtons(
    buttonWidth: Dp,
    onDelete: (() -> Unit)?,
    onSettings: (() -> Unit)?,
) {
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
}

@Composable
private fun DraggableCardContent(
    offsetX: () -> Float,
    onOffsetChange: (Float) -> Unit,
    thresholdPx: Float,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .offset { IntOffset(offsetX().roundToInt(), 0) }
                .draggable(
                    state =
                        rememberDraggableState { delta ->
                            onOffsetChange((offsetX() + delta).coerceIn(-thresholdPx, 0f))
                        },
                    orientation = Orientation.Horizontal,
                ),
    ) {
        content()
    }
}

// Архитектурный долг: 70 строк > 60. Extract сделан (SwipeActionButtons +
// DraggableCardContent), дальнейшая декомпозиция — ROADMAP →
// «Рефакторинг архитектурного долга» → «Compose-компоненты» (Н94).
@Suppress("LongMethod")
@Composable
fun AssetPositionCard(
    position: Position,
    modifier: Modifier = Modifier,
    displayCurrentPrice: Double? = null,
    priceChangePercent: Double? = null,
    onDelete: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    resetSwipe: Boolean = false,
    tradingAvailability: TradingAvailability? = null,
) {
    // --- Эффективная цена: свежий tick-to-tick overlay или структурная ---
    val effectivePrice = displayCurrentPrice ?: position.currentPrice

    // --- Цветовая индикация изменения цены (состояния) ---
    var previousPrice by remember { mutableDoubleStateOf(effectivePrice) }
    var priceDelta by remember { mutableDoubleStateOf(0.0) }

    val isPriceUp = effectivePrice > previousPrice
    val isPriceDown = effectivePrice < previousPrice

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
    LaunchedEffect(effectivePrice) {
        if (previousPrice != 0.0 && effectivePrice != previousPrice) {
            priceDelta = effectivePrice - previousPrice
        }
        previousPrice = effectivePrice
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
                displayCurrentPrice = displayCurrentPrice,
                priceChangePercent = priceChangePercent,
                onClick = onClick,
                isSelected = isSelected,
                tradingAvailability = tradingAvailability,
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
        SwipeActionButtons(
            buttonWidth = buttonWidth,
            onDelete = onDelete,
            onSettings = onSettings,
        )
        DraggableCardContent(
            offsetX = { offsetX },
            onOffsetChange = { offsetX = it },
            thresholdPx = with(density) { threshold.toPx() },
            content = { cardContent() },
        )
    }
}

// Архитектурный долг: 68 строк > 60 (split price + name + id + animations).
// См. ROADMAP → «Compose-компоненты» (Н94).
@Suppress("LongMethod")
@Composable
private fun AssetCardHeader(
    position: Position,
    displayCurrentPrice: Double?,
    priceChangePercent: Double?,
) {
    val effectivePrice = displayCurrentPrice ?: position.currentPrice
    val instrument = position.instrument
    val instrumentType = instrument.instrumentType
    val pointValue = instrument.moneyPerPointOfPrice
    val currency = instrument.currency
    val targetPriceColor = PositionFormatter.priceChangeColor(priceChangePercent)
    val priceColor by animateColorAsState(targetPriceColor, animationSpec = tween(600))
    var priceChanged by remember { mutableStateOf(false) }
    LaunchedEffect(effectivePrice) {
        priceChanged = true
        delay(500)
        priceChanged = false
    }
    val textScale by animateFloatAsState(
        targetValue = if (priceChanged) 1.05f else 1f,
        animationSpec = spring(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(instrument.ticker, fontWeight = FontWeight.Bold)
            Text(
                instrument.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val displayId =
                if (instrument.tscalpInstrumentId.isBlank()) "000000" else instrument.tscalpInstrumentId
            Text(
                text = displayId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val (priceStr, rubStr) =
            PositionFormatter.formatCurrentPrice(
                price = effectivePrice,
                instrumentType = instrumentType,
                pointValue = pointValue,
                currency = currency,
            )
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
}

// Архитектурный долг: 72 строки > 60 (avg | profit, две колонки).
// См. ROADMAP → «Compose-компоненты» (Н94).
@Suppress("LongMethod")
@Composable
private fun AssetCardLowerSection(position: Position) {
    val instrument = position.instrument
    val instrumentType = instrument.instrumentType
    val pointValue = instrument.moneyPerPointOfPrice
    val currency = instrument.currency
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outline,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            val (totalStr, detailStr) =
                PositionFormatter.formatAverage(
                    quantity = position.quantity,
                    averagePrice = position.averagePrice,
                    currency = currency,
                    instrumentType = instrumentType,
                )
            Text(
                text = totalStr ?: "-",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = detailStr ?: "${PositionFormatter.formatQuantity(position.quantity)} лот  ·  -",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val locked = position.lockedQuantity
            if (locked != null && locked > 0.0) {
                Text(
                    text = "В блоке: ${locked.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val (profitStr, profitColor, percentStr) =
                PositionFormatter.formatProfit(
                    profit = position.profit,
                    profitPercent = position.profitPercent,
                    instrumentType = instrumentType,
                    pointValue = pointValue,
                )
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
            val pct = position.profitPercent
            if (percentStr != null) {
                Text(
                    text = percentStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pct != null && pct >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                )
            } else {
                Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AssetCardContent(
    position: Position,
    displayCurrentPrice: Double?,
    priceChangePercent: Double?,
    onClick: (() -> Unit)?,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    tradingAvailability: TradingAvailability? = null,
) {
    val instrument = position.instrument
    val typeColor = getInstrumentTypeColor(instrument.instrumentType)
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
                AssetCardHeader(
                    position = position,
                    displayCurrentPrice = displayCurrentPrice,
                    priceChangePercent = priceChangePercent,
                )
                if (position.quantity != 0.0 || priceChangePercent != null) {
                    AssetCardLowerSection(position = position)
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
            position =
                SharePosition(
                    instrument =
                        Share(
                            brokerName = BrokerName.TINVEST,
                            tscalpInstrumentId = "uid-1",
                            ticker = "SBER",
                            name = "Сбербанк",
                            isin = "RU0009029540",
                            classCode = "TQBR",
                        ),
                    brokerName = BrokerName.TINVEST,
                    accountId = "acc-preview",
                    quantity = 10.0,
                    currentPrice = 250.5,
                    averagePrice = 200.0,
                    profit = 505.0,
                ),
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
            position =
                SharePosition(
                    instrument =
                        Share(
                            brokerName = BrokerName.TINVEST,
                            tscalpInstrumentId = "uid-2",
                            ticker = "GAZP",
                            name = "Газпром",
                            isin = "RU0007661625",
                            classCode = "TQBR",
                        ),
                    brokerName = BrokerName.TINVEST,
                    accountId = "acc-preview",
                    quantity = 10.0,
                    currentPrice = 150.0,
                    averagePrice = 200.0,
                    profit = -500.0,
                ),
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
            position =
                FuturePosition(
                    instrument =
                        Future(
                            brokerName = BrokerName.TINVEST,
                            tscalpInstrumentId = "uid-3",
                            ticker = "SiU4",
                            name = "Фьючерс Si",
                            isin = "FUT-SI-2024",
                            classCode = "SPBFUT",
                        ),
                    brokerName = BrokerName.TINVEST,
                    accountId = "acc-preview",
                    quantity = 2.0,
                    currentPrice = 85000.0,
                    averagePrice = 84000.0,
                    profit = 2000.0,
                ),
            priceChangePercent = 0.5,
            onClick = {},
            onDelete = {},
            onSettings = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
