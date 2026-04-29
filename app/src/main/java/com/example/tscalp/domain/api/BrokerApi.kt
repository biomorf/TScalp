package com.example.tscalp.domain.api

import com.example.tscalp.domain.models.InstrumentUi
import com.example.tscalp.domain.models.*


interface BrokerApi {
    val isInitialized: Boolean
    suspend fun getAccounts(sandboxMode: Boolean): List<BrokerAccount>


    /**
     * Выставляет заявку (рыночную или лимитную) через брокера.
     * @param orderType тип заявки (ORDER_TYPE_MARKET или ORDER_TYPE_LIMIT)
     * @param price цена (для рыночной игнорируется, можно передать Quotation.getDefaultInstance())
     */
    suspend fun postOrder(request: BrokerOrderRequest): OrderResult

    //suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse

    /**
     * Возвращает специфичный для брокера идентификатор инструмента по тикеру.
     * Для Т-Инвестиций это figi, для БКС — bscticker (пока просто ticker).
     * Может вернуть null, если инструмент не найден.
     */
    suspend fun resolveTicker(ticker: String): String?

    // Опционально: если нужно получать полный InstrumentUi по тикеру
    suspend fun getInstrumentByTicker(ticker: String): InstrumentUi?
    suspend fun findInstruments(query: String): List<InstrumentUi>
    //suspend fun findInstrumentShorts(query: String): List<InstrumentShort>
    //suspend fun getLastPrices(figis: List<String>): Map<String, Double?>
    //suspend fun getMarginAttributes(accountId: String): GetMarginAttributesResponse
    suspend fun getBalance(accountId: String): Double
    suspend fun sandboxPayIn(accountId: String, amount: SandboxMoney)

    /**
     * Возвращает позиции портфеля для указанного счёта в виде списка доменных объектов.
     * Реализация по умолчанию для обратной совместимости (использует getPortfolio).
     */
//    suspend fun getPositions(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> {
//        val response = getPortfolio(accountId, sandboxMode)
//        return response.positionsList.mapNotNull { pos ->
//            val quantity = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 }?.toLong() ?: 0L
//            val currentPrice = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
//            PortfolioPosition(
//                name = try { getInstrumentByFigi(pos.figi).instrument.name } catch (e: Exception) { "" },
//                ticker = try { getInstrumentByFigi(pos.figi).instrument.ticker } catch (e: Exception) { "" },
//                quantity = quantity,
//                currentPrice = currentPrice,
//                totalValue = currentPrice * quantity
//            )
//        }
//    }

    /**
     * Возвращает позиции портфеля для указанного счёта в виде списка доменных объектов.
     * Каждый брокер реализует по‑своему: Т‑Инвестиции – через getPortfolio, БКС – парсинг JSON.
     */
    suspend fun getPositions(accountId: String, sandboxMode: Boolean): List<PortfolioPosition>


    /**
     * Получает последние цены для списка тикеров.
     * Возвращает карту ticker -> цена (или null, если цена недоступна).
     */
    suspend fun getLastPricesByTicker(tickers: List<String>): Map<String, Double?>

//    /**
//     * Выставляет стоп-заявку (take-profit, stop-loss, stop-limit).
//     * @return StopOrderResponse с информацией о созданной заявке.
//     */
//    suspend fun postStopOrder(request: StopOrderRequest): StopOrderResponse
//
//    /**
//     * Возвращает список активных стоп-заявок для указанного счёта.
//     */
//    suspend fun getStopOrders(accountId: String): List<StopOrder>
//
//    /**
//     * Отменяет стоп-заявку по её идентификатору.
//     */
//    suspend fun cancelStopOrder(accountId: String, stopOrderId: String)
}