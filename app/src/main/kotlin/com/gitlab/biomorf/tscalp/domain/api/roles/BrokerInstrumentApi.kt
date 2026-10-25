package com.gitlab.biomorf.tscalp.domain.api.roles

import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi

/**
 * Опциональный контракт: полный доменный инструмент по uid.
 *
 * Реализуется брокером, если он умеет отдавать **полный**
 * [InstrumentUi] — со всеми полями, включая `pointValue` для
 * фьючерсов (через margin-запрос к брокеру). Доступ через
 * `BrokerApi.instrument` (`null` = не поддерживает).
 *
 * **Не путать** с [UserInstrumentsApi.findInstruments][UserInstrumentsApi]
 * — тот возвращает **неполный** [InstrumentUi] для фьючерсов
 * (без margin, `pointValue` вычисляется из minPriceIncrement),
 * в bulk-форме для поиска. Семантика:
 * - [BrokerInstrumentApi] — внутренняя инфраструктура (кэш,
 *   обогащение карточек, расчёты P&L), один uid → один полный
 *   инструмент.
 * - [UserInstrumentsApi] — пользовательский путь (поиск,
 *   статусы торговли), bulk.
 *
 * См. `docs/ROADMAP.md` → `### TInvest-specific:
 * fetchFullInstrument` (шаг #3 плана) и
 * `### Рефакторинг ролей BrokerApi (nullable-val, Scenario2)`.
 */
interface BrokerInstrumentApi {
    /**
     * Полный доменный инструмент по uid.
     *
     * У T-Invest: `getInstrumentBy(uid)` +
     * `getFuturesMargin(figi)` для фьючерсов (даёт
     * `pointValue`). У акций margin-запрос не нужен —
     * `pointValue` не используется.
     *
     * `null` — инструмент не найден.
     */
    suspend fun fetchFullInstrument(uid: String): InstrumentUi?
}
