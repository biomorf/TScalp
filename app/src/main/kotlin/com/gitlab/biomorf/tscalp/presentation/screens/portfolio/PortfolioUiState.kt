package com.gitlab.biomorf.tscalp.presentation.screens.portfolio

import com.gitlab.biomorf.tscalp.domain.models.PortfolioSheet
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability

/**
 * UI-состояние экрана портфеля.
 *
 * Структура — из [PortfolioSheet] (`uiState.sheet.accounts`). Overlay
 * цен / процентов (`prices`, `priceChangePercents`) — tick-to-tick
 * из `PriceFlowManager`; в `Position.priceChangePercent` значение
 * `null` (источник — задача `### Position: источники priceChangePercent`).
 */
data class PortfolioUiState(
    val accountId: String? = null,
    val sheet: PortfolioSheet = PortfolioSheet(emptyMap()),
    val prices: Map<String, Double> = emptyMap(),
    val priceChangePercents: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val isApiInitialized: Boolean = false,
    val sandboxMode: Boolean = false,
    val tradingStatuses: Map<String, TradingAvailability> = emptyMap(),
)
