package com.example.tscalp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tscalp.domain.models.OrderTypeSelection
import com.example.tscalp.util.TradePhrases
import com.example.tscalp.util.formatPrice

/**
 * Унифицированная карточка заявки для использования в очереди заявок
 * и в диалоге подтверждения сделки.
 *
 * @param ticker Тикер инструмента
 * @param direction "BUY" или "SELL" (будет преобразовано в "Покупка"/"Продажа")
 * @param orderType Тип заявки (определяет цвет и текст)
 * @param status Статус заявки (null, если не нужно показывать)
 * @param quantity Количество лотов
 * @param price Цена (лимитная или триггерная)
 * @param instrumentType Тип инструмента (для форматирования цены)
 * @param totalCost Общая стоимость (null, если не нужно показывать)
 * @param onCancel Действие при отмене заявки (null – кнопка скрыта)
 * @param modifier Дополнительные модификаторы
 */
@Composable
fun OrderCard(
    ticker: String,
    direction: String,
    orderType: OrderTypeSelection,
    status: String?,
    quantity: Long,
    price: Double,
    instrumentType: String = "",
    totalCost: Double? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val orderColor = TradePhrases.orderTypeColor(orderType)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Цветовая полоска слева (как в оригинальном списке заявок)
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(orderColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Основной текст слева
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(ticker, fontWeight = FontWeight.Bold)
            Text(TradePhrases.directionText(direction))
            Text(
                text = TradePhrases.orderTypeDescription(orderType),
                color = orderColor,
                fontWeight = FontWeight.Medium
            )
            if (status != null) {
                Text(TradePhrases.statusLabel(status))
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Детали справа (прижаты к правому краю)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("${quantity} лотов")
            Text(formatPrice(price, instrumentType))
            if (totalCost != null) {
                Text(formatPrice(totalCost, instrumentType))
            }
        }

        // Кнопка отмены (если задана)
        if (onCancel != null) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Delete,               // ← мусорная корзина
                    contentDescription = "Отменить заявку",
                    tint = Color(0xFFC62828)             // красный, как было раньше
                )
            }
        }
    }
}