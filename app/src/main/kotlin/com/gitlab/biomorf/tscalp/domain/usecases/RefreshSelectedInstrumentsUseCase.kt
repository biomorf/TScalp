package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PortfolioSheet
import com.gitlab.biomorf.tscalp.domain.models.SelectedInstrumentInfo

/**
 * Обогащение карточек «Последние просмотренные» из портфеля
 * (`PortfolioSheet`) — разбиение `OrdersViewModel` на 4 VM, шаг 13b.
 *
 * Вынесено из `OrdersViewModel.refreshLastSelectedInstruments`
 * (clean-audit M1, не M2 — см. «Классификация» ниже).
 *
 * **Классификация (2a, 2026-11-07):** ROADMAP 13b назвал это M2
 * (orchestration) по инерции с 13a/13c. По факту — **M1** (pure
 * enrichment): нет вызовов usecase/repo, нет suspend, нет side-effects,
 * кроме одного `_uiState.update` в VM-обёртке. Логика — чистая
 * трансформация `List<SelectedInstrumentInfo>` + `selectedAccountId` +
 * `PortfolioSheet` → `List<SelectedInstrumentInfo>`.
 *
 * **Альтернатива** — pure extension в `domain/models/Portfolio.kt`
 * (`fun List<SelectedInstrumentInfo>.refreshFrom(sheet, globalAccountId)`).
 * Не выбрана: usecase единообразно тестируется и консистентен с
 * остальными 13x (`SelectInstrumentUseCase`, `RestoreOrderFormUseCase`).
 *
 * **Stateless, без зависимостей** — DI через `@Provides` в `OrderSearchUseCaseModule`.
 * Логика внутри pure (не suspend, deterministic).
 *
 * **Поведение 1:1** с `OrdersViewModel.refreshLastSelectedInstruments`:
 * - `cards` пустой -> `emptyList` (в VM-обёртке — early return до
 *   `_uiState.update`; в usecase — `emptyList()`, эквивалентно);
 * - `accountId = card.accountId ?: globalAccountId` (per-card
 *   приоритетнее глобального);
 * - `averagePrice = pos?.currentPrice ?: card.averagePrice` — **как в
 *   оригинале** (использует `currentPrice` позиции, не
 *   `averagePrice`; не чиню — 1:1);
 * - `quantity = pos?.quantity?.toLong() ?: 0L`;
 * - `profit = pos?.profit ?: 0.0`;
 * - `profitPercent = pos?.profitPercent ?: 0.0`;
 * - `position` ищется по composite `(tscalpInstrumentId, brokerName)`;
 * - `brokerName` позиции — `TINVEST` (жёстко, 1:1 с оригиналом).
 */
class RefreshSelectedInstrumentsUseCase {
    fun execute(
        cards: List<SelectedInstrumentInfo>,
        globalAccountId: String?,
        sheet: PortfolioSheet,
    ): List<SelectedInstrumentInfo> {
        if (cards.isEmpty()) return emptyList()
        return cards.map { card ->
            val accountId = card.accountId ?: globalAccountId
            val positions =
                accountId
                    ?.let { sheet.accounts[AccountKey(BrokerName.TINVEST, it)]?.positions }
                    .orEmpty()
            val pos =
                positions.find { p ->
                    p.instrument.tscalpInstrumentId == card.instrument.tscalpInstrumentId &&
                        p.instrument.brokerName == card.instrument.brokerName
                }
            card.copy(
                quantity = pos?.quantity?.toLong() ?: 0L,
                averagePrice = pos?.currentPrice ?: card.averagePrice,
                profit = pos?.profit ?: 0.0,
                profitPercent = pos?.profitPercent ?: 0.0,
            )
        }
    }
}
