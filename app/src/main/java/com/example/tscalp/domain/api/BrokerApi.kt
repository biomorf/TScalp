package com.example.tscalp.domain.api

import com.example.tscalp.domain.models.InstrumentUi
import ru.tinkoff.piapi.contract.v1.*
//import com.example.tscalp.domain.models.
import com.example.tscalp.domain.models.PortfolioPosition

interface BrokerApi {
    val isInitialized: Boolean
    suspend fun getAccounts(sandboxMode: Boolean): List<Account>
    suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean
    ): PostOrderResponse
    /**
     * Выставляет заявку (рыночную или лимитную) через брокера.
     * @param orderType тип заявки (ORDER_TYPE_MARKET или ORDER_TYPE_LIMIT)
     * @param price цена (для рыночной игнорируется, можно передать Quotation.getDefaultInstance())
     */
    suspend fun postOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean,
        orderType: OrderType,
        price: Quotation
    ): PostOrderResponse
    suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse
    suspend fun getInstrumentByFigi(figi: String): InstrumentResponse
    /**
     * Возвращает специфичный для брокера идентификатор инструмента по тикеру.
     * Для Т-Инвестиций это figi, для БКС — bscticker (пока просто ticker).
     * Может вернуть null, если инструмент не найден.
     */
    suspend fun resolveTicker(ticker: String): String?

    // Опционально: если нужно получать полный InstrumentUi по тикеру
    suspend fun getInstrumentByTicker(ticker: String): InstrumentUi?
    suspend fun findInstrumentShorts(query: String): List<InstrumentShort>
    suspend fun getLastPrices(figis: List<String>): Map<String, Double?>
    suspend fun getMarginAttributes(accountId: String): GetMarginAttributesResponse
    suspend fun sandboxPayIn(accountId: String, amount: MoneyValue)

    /**
     * Возвращает позиции портфеля для указанного счёта в виде списка доменных объектов.
     * Реализация по умолчанию для обратной совместимости (использует getPortfolio).
     */
    suspend fun getPositions(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> {
        val response = getPortfolio(accountId, sandboxMode)
        return response.positionsList.mapNotNull { pos ->
            val quantity = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 }?.toLong() ?: 0L
            val currentPrice = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
            PortfolioPosition(
                name = try { getInstrumentByFigi(pos.figi).instrument.name } catch (e: Exception) { "" },
                ticker = try { getInstrumentByFigi(pos.figi).instrument.ticker } catch (e: Exception) { "" },
                quantity = quantity,
                currentPrice = currentPrice,
                totalValue = currentPrice * quantity
            )
        }
    }


}