package com.example.tscalp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.presentation.screens.portfolio.PortfolioPositionCard
import kotlin.math.roundToInt

@Composable
fun SwipeablePositionCard(
    position: PortfolioPosition,
    instrumentType: String,
    priceChangePercent: Double?,
    onDelete: () -> Unit,
    onSettings: () -> Unit,
    onClick: () -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val buttonWidth = 72.dp
    val threshold = buttonWidth * 2
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
        // Кнопки подложки
        Row(
            modifier = Modifier
                .matchParentSize()
                .wrapContentSize(Alignment.CenterEnd)
                .width(buttonWidth * 2),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onSettings,
                modifier = Modifier
                    .size(buttonWidth)
                    .background(Color(0xFF757575), shape = CircleShape)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Color.White)
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(buttonWidth)
                    .background(Color(0xFFC62828), shape = CircleShape)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.White)
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
            PortfolioPositionCard(
                position = position,
                onClick = onClick,
                isSelected = isSelected,
                instrumentType = instrumentType,
                priceChangePercent = priceChangePercent
            )
        }
    }
}