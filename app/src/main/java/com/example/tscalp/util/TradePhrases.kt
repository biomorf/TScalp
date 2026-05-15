package com.example.tscalp.util

import androidx.compose.ui.graphics.Color
import com.example.tscalp.domain.models.OrderTypeSelection

/**
 * Единый источник истины (SSOT) для текстов и цветов,
 * связанных с торговыми операциями.
 */
object TradePhrases {

    // ---------- Направления сделок ----------
    const val DIRECTION_BUY = "Покупка"
    const val DIRECTION_SELL = "Продажа"

    fun directionText(direction: String): String = when (direction) {
        "BUY" -> DIRECTION_BUY
        "SELL" -> DIRECTION_SELL
        else -> direction
    }

    // ---------- Человекочитаемое описание типа заявки ----------
    fun orderTypeDescription(type: OrderTypeSelection): String = when (type) {
        OrderTypeSelection.Market -> "по рыночной цене"
        OrderTypeSelection.Limit -> "по лимитной цене"
        OrderTypeSelection.StopLoss -> "стоп‑лосс"
        OrderTypeSelection.TakeProfit -> "тейк‑профит"
        OrderTypeSelection.StopLimit -> "стоп‑лимит"
    }

    // ---------- Цвет типа заявки (единая палитра) ----------
    fun orderTypeColor(type: OrderTypeSelection): Color = when (type) {
        OrderTypeSelection.Market -> Color(0xFF757575)      // серый
        OrderTypeSelection.Limit -> Color(0xFF1565C0)       // синий
        OrderTypeSelection.StopLoss -> Color(0xFFC62828)    // красный
        OrderTypeSelection.TakeProfit -> Color(0xFF2E7D32)  // зелёный
        OrderTypeSelection.StopLimit -> Color(0xFFE65100)   // оранжевый
    }

    // ---------- Тексты подтверждения ----------
    fun confirmOrderText(
        direction: String,
        quantity: Long,
        ticker: String,
        orderType: OrderTypeSelection
    ): String = "Вы собираетесь ${directionText(direction).lowercase()} " +
            "$quantity лотов $ticker ${orderTypeDescription(orderType)}"

    fun confirmCounterOrderText(
        orderType: OrderTypeSelection,
        direction: String,
        quantity: Long,
        ticker: String
    ): String = "Контрсделка: ${orderTypeDescription(orderType)} " +
            "${directionText(direction).lowercase()} $quantity лотов $ticker"

    // ---------- Типы ордеров (для списка заявок) ----------
    fun orderTypeLabel(type: String): String = when (type) {
        "LIMIT" -> "Лимитная"
        "MARKET" -> "Рыночная"
        "STOP_LOSS" -> "Стоп‑лосс"
        "TAKE_PROFIT" -> "Тейк‑профит"
        "STOP_LIMIT" -> "Стоп‑лимит"
        else -> type
    }

    // маппинг
    fun stringToOrderType(type: String): OrderTypeSelection = when (type) {
        "MARKET" -> OrderTypeSelection.Market
        "LIMIT" -> OrderTypeSelection.Limit
        "STOP_LOSS" -> OrderTypeSelection.StopLoss
        "TAKE_PROFIT" -> OrderTypeSelection.TakeProfit
        "STOP_LIMIT" -> OrderTypeSelection.StopLimit
        else -> OrderTypeSelection.Market
    }

    // ---------- Статусы ----------
    fun statusLabel(status: String): String = when (status) {
        "NEW" -> "Новая"
        "FILL" -> "Исполнена"
        "PARTIALLYFILL" -> "Частично исполнена"
        "CANCELLED" -> "Отменена"
        "REJECTED" -> "Отклонена"
        else -> status
    }

}