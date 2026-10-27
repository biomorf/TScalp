package com.gitlab.biomorf.tscalp.domain.api.roles

/**
 * Опциональный контракт: последние цены по списку инструментов
 * (unary-снимок, не стрим).
 *
 * Доступ через `BrokerApi.marketData` (`null` = не поддерживает):
 * TInvest — реальный, BCS/Finam — заглушки, `null`.
 *
 * **Не путать** с `StreamsApi.subscribeLastPrices` — тот стрим.
 */
interface MarketDataApi {
    suspend fun getLastPricesByTscalpInstrumentId(ids: List<String>): Map<String, Double?>
}
