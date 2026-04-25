package com.example.tscalp.domain.api

import ru.tinkoff.piapi.contract.v1.*
import ru.ttech.piapi.core.InvestApi

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
    suspend fun findInstrumentShorts(query: String): List<InstrumentShort>
    suspend fun getLastPrices(figis: List<String>): Map<String, Double?>
    suspend fun getMarginAttributes(accountId: String): GetMarginAttributesResponse
    suspend fun sandboxPayIn(accountId: String, amount: MoneyValue)
}