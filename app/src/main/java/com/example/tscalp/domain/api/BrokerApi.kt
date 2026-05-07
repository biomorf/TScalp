package com.example.tscalp.domain.api

import com.example.tscalp.domain.models.InstrumentUi
import com.example.tscalp.domain.models.TradingAvailability
import com.example.tscalp.domain.models.OrderState
import com.example.tscalp.domain.models.*

import kotlinx.coroutines.flow.Flow

interface BrokerApi {
    val isInitialized: Boolean

    suspend fun getAccounts(sandboxMode: Boolean): List<BrokerAccount>
    suspend fun openSandboxAccount(): String   // возвращает accountId нового счёта
    suspend fun closeSandboxAccount(accountId: String)

    /**
     * Возвращает позиции портфеля для указанного счёта в виде списка доменных объектов.
     * Каждый брокер реализует по‑своему: Т‑Инвестиции – через getPortfolio, БКС – парсинг JSON.
     */
    suspend fun getPositions(accountId: String, sandboxMode: Boolean): List<PortfolioPosition>

    suspend fun getBalance(accountId: String): Double
    suspend fun sandboxPayIn(accountId: String, amount: SandboxMoney)


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

    /**
     * Выставляет заявку (рыночную или лимитную) через брокера.
     * @param orderType тип заявки (ORDER_TYPE_MARKET или ORDER_TYPE_LIMIT)
     * @param price цена (для рыночной игнорируется, можно передать Quotation.getDefaultInstance())
     */
    suspend fun postOrder(request: BrokerOrderRequest): OrderResult

    /**
     * Выставляет стоп-заявку (take-profit, stop-loss, stop-limit).
     * @return StopOrderResponse с информацией о созданной заявке.
     */
    suspend fun postStopOrder(request: StopOrderRequest): String   // возвращает stopOrderId

    /**
     * Возвращает список активных стоп-заявок для указанного счёта.
     */
    suspend fun getStopOrders(accountId: String): List<OrderListItem>

    /**
     * Отменяет стоп-заявку по её идентификатору.
     */
    suspend fun cancelStopOrder(accountId: String, stopOrderId: String)

    suspend fun getOrders(accountId: String): List<OrderListItem>
    suspend fun cancelOrder(accountId: String, orderId: String)



    /**
     * Получает последние цены для списка тикеров.
     * Возвращает карту ticker -> цена (или null, если цена недоступна).
     */
    suspend fun getLastPricesByTicker(tickers: List<String>): Map<String, Double?>

    /**
     * Возвращает статусы доступности для торговли для списка идентификаторов инструментов.
     * Ключ — идентификатор инструмента (tscalpInstrumentId), значение — статус доступности.
     */
    suspend fun getTradingStatuses(ids: List<String>): Map<String, TradingAvailability>

    suspend fun subscribeOrderState(accountId: String): Flow<OrderState>

    suspend fun checkTradeAvailability(
        accountId: String,
        tscalpInstrumentId: String,
        uid: String? = null,
        direction: OrderDirection,
        quantity: Long
    ): TradeCheckResult
}