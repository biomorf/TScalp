package com.gitlab.biomorf.tscalp.domain.models

/**
 * Снимок торгового состояния сессии: что было выбрано перед закрытием приложения.
 * Восстанавливается при следующем запуске.
 *
 * Отделён от пользовательских настроек (SettingsRepository) — это не то,
 * что пользователь настраивает, а то, что приложение запоминает о ходе работы.
 */
data class TradingStateSnapshot(
    val selectedInstrumentUid: String? = null,
    val pairedInstrumentUid: String? = null,
    val pairTradingEnabled: Boolean = false,
    val quantity: String = "",
    val pairedMultiplier: String = "10",
    val orderType: OrderTypeSelection = OrderTypeSelection.Market
)