package com.gitlab.biomorf.tscalp.domain.models

/**
 * Снимок торгового состояния сессии: что было выбрано перед закрытием приложения.
 * Восстанавливается при следующем запуске.
 *
 * Отделён от пользовательских настроек (preferences-репозитории) — это не то,
 * что пользователь настраивает, а то, что приложение запоминает о ходе работы.
 */
data class TradingStateSnapshot(
    val selectedCard: CardIdentity? = null,
    val pairedCard: CardIdentity? = null,
    val pairTradingEnabled: Boolean = false,
    val quantity: Long = 1L,
    val pairedMultiplier: Long = 10L,
    val orderType: OrderTypeSelection = OrderTypeSelection.Market,
)
