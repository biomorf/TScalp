package com.gitlab.biomorf.tscalp.domain.models

/**
 * Identity карточки «Последние просмотренные» для persistence
 * (`CardsRepository`). Только стабильные поля; enrich-версия
 * `SelectedInstrumentInfo` (currentPrice, quantity, profit, …)
 * вычисляется в Coordinator / VM из `InstrumentRepository` +
 * `PositionFlowManager.sheet` и **не хранится**.
 *
 * **`tscalpInstrumentId` — канонический идентификатор** (domain-
 * уровень). `ticker` — legacy-поле, нужно для:
 * - обратной совместимости DataStore (старые 3-польные строки);
 * - `SaveCardSettingsUseCase` (ищет карточку по ticker);
 * - `OrdersUiState.activeCard` (сопоставление с `instrument.ticker`).
 *
 * Планируемый рефакторинг (ROADMAP): domain и слои к UI
 * должны использовать `tscalpInstrumentId`; `ticker` —
 * временный legacy, удаляется после миграции `SaveCardSettings`
 * и `activeCard` на uid.
 */
data class CardIdentity(
    val tscalpInstrumentId: String,
    val ticker: String,
    val brokerName: BrokerName = BrokerName.TINVEST,
    val accountId: String? = null,
)
