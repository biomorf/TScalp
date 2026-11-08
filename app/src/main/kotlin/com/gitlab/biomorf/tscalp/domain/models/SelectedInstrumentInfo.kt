package com.gitlab.biomorf.tscalp.domain.models

/**
 * Информация о выбранном инструменте для отображения в карточке
 * «Последние просмотренные».
 *
 * Domain-модель: используется presentation (карточки Orders) и будет
 * использоваться `CardsRepository` (domain, StateFlow). Перенесена из
 * `presentation/screens/orders/OrdersUiState.kt` (разбиение
 * `OrdersViewModel` на 4 VM, домен-prep шаг 1).
 */
data class SelectedInstrumentInfo(
    val instrument: Instrument,
    val currentPrice: Double?,
    val priceChangePercent: Double?,
    val quantity: Long,
    val averagePrice: Double?,
    val profit: Double?,
    val profitPercent: Double?,
    val brokerName: BrokerName = BrokerName.TINVEST,
    val accountId: String? = null,
)
