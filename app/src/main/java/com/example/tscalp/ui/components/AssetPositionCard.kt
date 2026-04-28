package com.example.tscalp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.tscalp.domain.models.PortfolioPosition
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.example.tscalp.util.formatCurrency
import androidx.compose.ui.draw.scale

@Composable
fun AssetPositionCard(
    position: PortfolioPosition,
    instrumentType: String = "",
    priceChangePercent: Double? = null,
    onDelete: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    resetSwipe: Boolean = false,          // <-- новый параметр
    modifier: Modifier = Modifier
) {
    // Если свайп не нужен, просто показываем карточку
    if (onDelete == null && onSettings == null) {
        PortfolioCardContent(
            position = position,
            instrumentType = instrumentType,
            priceChangePercent = priceChangePercent,
            onClick = onClick,
            isSelected = isSelected,
            modifier = modifier
        )
        return
    }

    // Свайп с кнопками
    var offsetX by remember { mutableFloatStateOf(0f) }
    val buttonWidth = 72.dp
    val threshold = buttonWidth * 2
    val density = LocalDensity.current

    // Плавный возврат при resetSwipe = true
    LaunchedEffect(resetSwipe) {
        if (resetSwipe) {
            // Анимированно возвращаем offsetX к 0
            val target = 0f
            animate(
                initialValue = offsetX,
                targetValue = target,
                animationSpec = tween(300)
            ) { value, _ -> offsetX = value }
        }
    }

    Box(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
        // Кнопки подложки (поменяли порядок: сначала Удалить, потом Настройки)
        Row(
            modifier = Modifier
                .matchParentSize()
                .wrapContentSize(Alignment.CenterEnd)
                .width(buttonWidth * 2),
            horizontalArrangement = Arrangement.End
        ) {
            // Сначала кнопка Удалить (красная) – появляется первой при свайпе
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(buttonWidth)
                        .background(Color(0xFFC62828), shape = CircleShape)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.White)
                }
            }
            // Затем кнопка Настройки (серая) – появляется при более сильном свайпе
            if (onSettings != null) {
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier
                        .size(buttonWidth)
                        .background(Color(0xFF757575), shape = CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Color.White)
                }
            }
        }

        // Перетаскиваемая карточка
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        offsetX = (offsetX + delta).coerceIn(-with(density) { threshold.toPx() }, 0f)
                    },
                    orientation = Orientation.Horizontal
                )
        ) {
            PortfolioCardContent(
                position = position,
                instrumentType = instrumentType,
                priceChangePercent = priceChangePercent,
                onClick = onClick,
                isSelected = isSelected
            )
        }
    }
}

@Composable
private fun PortfolioCardContent(
    position: PortfolioPosition,
    instrumentType: String,
    priceChangePercent: Double?,
    onClick: (() -> Unit)?,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    // --- Анимация цвета цены ---
    val targetPriceColor = when {
        priceChangePercent == null -> MaterialTheme.colorScheme.onSurface
        priceChangePercent >= 0 -> Color(0xFF2E7D32)
        else -> Color(0xFFC62828)
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

    // --- Цвет полосы в зависимости от типа инструмента ---
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
        Row(modifier = Modifier.height(IntrinsicSize.Max).padding(start = 4.dp)) {
            // Цветовая полоса слева
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
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
                                modifier = Modifier.scale(textScale)
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

                // Прибыль/убыток (P&L)
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