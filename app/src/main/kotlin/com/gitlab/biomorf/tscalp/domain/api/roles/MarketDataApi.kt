package com.gitlab.biomorf.tscalp.domain.api.roles

/**
 * Опциональный контракт: последние цены по списку инструментов
 * (unary-снимок, не стрим).
 *
 * Доступ через `BrokerApi.marketData` (`null` = не поддерживает):
 * TInvest — реальный, BCS/Finam — заглушки, `null`.
 *
 * **Не путать** с `StreamsApi.subscribeLastPrices` — тот стрим.
 *
 * В реализациях data-слоя имя параметра отражает формат
 * брокера (T-Invest — `uids`, BCS — `figi`, Finam — `ticker`);
 * domain — универсальный `tscalpInstrumentIds`.
 */
interface MarketDataApi {
    suspend fun getLastPricesByTscalpInstrumentId(tscalpInstrumentIds: List<String>): Map<String, Double?>
}
