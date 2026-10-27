package com.gitlab.biomorf.tscalp.domain.api.roles

/**
 * Опциональный контракт: последние цены по списку инструментов
 * (unary-снимок).
 *
 * Доступ через `BrokerApi.marketData` (`null` = не поддерживает).
 *
 * **Nullable-val и supertype.** По факту 2026-10-28:
 * - TInvest — реальный (`marketData.getLastPricesByTscalpInstrumentId`);
 * - BCS / Finam — заглушки (`emptyMap()`), `marketData = null`,
 *   без supertype.
 *
 * **Не путать** с `StreamsApi.subscribeLastPrices` — тот стрим
 * (`Flow<Pair<String, Double>>`), здесь — one-shot unary.
 */
interface MarketDataApi {
    /**
     * Последние цены по списку `tscalpInstrumentId`.
     * Ключ — `tscalpInstrumentId`, значение — цена или `null`.
     */
    suspend fun getLastPricesByTscalpInstrumentId(ids: List<String>): Map<String, Double?>
}
