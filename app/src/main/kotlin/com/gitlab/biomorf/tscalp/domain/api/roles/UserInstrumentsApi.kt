package com.gitlab.biomorf.tscalp.domain.api.roles

import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability

/**
 * Опциональный контракт: инструменты для пользовательского
 * пути (поиск и статусы торговли).
 *
 * Реализуется брокером, если он умеет искать инструменты по
 * запросу и/или отдавать статусы торговли. Доступ через
 * `BrokerApi.userInstruments` (`null` = не поддерживает).
 *
 * **Не путать с `BrokerInstrumentApi` (#3).** Тот —
 * внутренняя инфраструктура: один uid → один **полный**
 * `InstrumentUi` (с margin для фьючерсов, `pointValue`).
 * `UserInstrumentsApi` — пользовательский путь:
 * - `findInstruments` — bulk-поиск, для фьючерсов возвращает
 *   **неполный** `InstrumentUi` (без margin, `pointValue` из
 *   minPriceIncrement). Полный инструмент — через
 *   `BrokerInstrumentApi.fetchFullInstrument`, когда нужно;
 * - `getTradingStatuses` — статусы доступности торговли для
 *   списка `tscalpInstrumentId`.
 *
 * **Nullable-val и supertype.** По правилу AGENTS.md
 * «Nullable-val роли»: supertype = «реализую все методы».
 * По grep 2026-10-25:
 * - TInvest — 2/2 реальных (`marketData.findInstruments`,
 *   `marketData.getTradingStatuses`), supertype +
 *   `userInstruments = this`;
 * - BCS — 0/2 (`emptyList` / все `UNKNOWN`),
 *   `userInstruments = null`, без supertype;
 * - Finam — 0/2 (`emptyList` / `emptyMap`),
 *   `userInstruments = null`, без supertype.
 *
 * См. `docs/ROADMAP.md` → `### UserInstrumentsApi` (шаг #7) и
 * `### Рефакторинг ролей BrokerApi (nullable-val, Scenario2)`.
 */
interface UserInstrumentsApi {
    /**
     * Bulk-поиск инструментов по запросу.
     *
     * Для фьючерсов возвращает **неполный** `InstrumentUi`
     * (без margin). Если нужен полный инструмент
     * (`pointValue` для расчётов) — использовать
     * `BrokerInstrumentApi.fetchFullInstrument(uid)` по
     * найденному `tscalpInstrumentId`.
     */
    suspend fun findInstruments(query: String): List<InstrumentUi>

    /**
     * Статусы доступности торговли для списка
     * `tscalpInstrumentId`.
     *
     * `Map<tscalpInstrumentId, TradingAvailability>`.
     * Отсутствующий ключ означает, что брокер не вернул
     * статус для этого инструмента.
     */
    suspend fun getTradingStatuses(ids: List<String>): Map<String, TradingAvailability>
}
