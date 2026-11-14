package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument

/**
 * Состояние инструмента (primary + pair) — часть split Form-VM (C2).
 *
 * Источник: `OrderFormState` (13d-A). Содержит только поля,
 * связанные с выбором инструмента, ценой и identity слота.
 * Поля `quantity` / `orderType` / `prices` / `pair-multiplier` —
 * в [OrderInputState]; trade-details (executionPrice, costOverlay,
 * freeBalance) — в [OrderTradeDetailsState]; confirm/status —
 * в [OrderExecutionState].
 *
 * **2c (fixation, C2):** `saveState()` — в [OrderInstrumentViewModel]
 * (identity слота — зона инструмента); persist через
 * `TradingStateRepository`.
 */
data class OrderInstrumentState(
    // Primary slot
    val ticker: String = "",
    val selectedInstrument: Instrument? = null,
    val currentPrice: Double? = null,
    val selectedPriceChangePercent: Double? = null,
    val currentPointValue: Double? = null,
    val isPriceLoading: Boolean = false,
    val selectedBroker: BrokerName = BrokerName.TINVEST,
    val selectedAccountId: String? = null,
    // Pair slot
    val pairedInstrument: Instrument? = null,
    val pairCurrentPrice: Double? = null,
    val pairedPointValue: Double? = null,
    val pairedBroker: BrokerName = BrokerName.TINVEST,
    val pairedAccountId: String? = null,
)
