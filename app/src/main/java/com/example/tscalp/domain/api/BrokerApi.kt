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
    suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse
    suspend fun getInstrumentByFigi(figi: String): InstrumentResponse
    suspend fun findInstrumentShorts(query: String): List<InstrumentShort>
    suspend fun getLastPrice(figi: String): Double?
}