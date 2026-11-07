package com.gitlab.biomorf.tscalp.domain.models

/**
 * Identity карточки «Последние просмотренные» для persistence
 * (`CardsRepository`). Только стабильные поля; enrich-версия
 * `SelectedInstrumentInfo` (currentPrice, quantity, profit, …)
 * вычисляется в Coordinator из `InstrumentRepository` +
 * `PositionFlowManager.sheet` и **не хранится**.
 */
data class CardIdentity(
    val ticker: String,
    val brokerName: BrokerName = BrokerName.TINVEST,
    val accountId: String? = null,
)
