package com.example.tscalp.data.api

import com.example.tscalp.domain.api.BrokerApi
import com.example.tscalp.domain.models.InstrumentUi
import ru.tinkoff.piapi.contract.v1.GetMarginAttributesResponse
import ru.tinkoff.piapi.contract.v1.*
import com.example.tscalp.domain.models.PortfolioPosition


/**
 * Тестовый брокер-заглушка для демонстрации мультиброкерности.
 * Возвращает предопределённые ответы и не выполняет реальных операций.
 */
class MockBrokerApi : BrokerApi {

    override val isInitialized: Boolean = true

    override suspend fun getAccounts(sandboxMode: Boolean): List<Account> = listOf(
        Account.newBuilder()
            .setId("mock-account-1")
            .setName("Тестовый счёт (Mock)")
            .setTypeValue(1)   // 1 = брокерский счёт
            .build(),
        Account.newBuilder()
            .setId("mock-account-2")
            .setName("ИИС (Mock)")
            .setTypeValue(2)   // 2 = ИИС
            .build()
    )

    override suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean
    ): PostOrderResponse = PostOrderResponse.newBuilder()
        .setOrderId("mock-order-${System.currentTimeMillis()}")
        .setLotsRequested(quantity)
        .setLotsExecuted(quantity)
        .setExecutionReportStatus(OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_NEW)
        .build()

    override suspend fun postOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean,
        orderType: OrderType,
        price: Quotation
    ): PostOrderResponse {
        // Заглушка – возвращаем успешный ответ с фиктивным orderId
        return PostOrderResponse.newBuilder()
            .setOrderId("mock-order-${System.currentTimeMillis()}")
            .setLotsRequested(quantity)
            .setLotsExecuted(quantity)
            .setExecutionReportStatus(OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_NEW)
            .build()
    }

    override suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse {
        // Возвращаем пустой портфель
        return PortfolioResponse.newBuilder().build()
    }

    override suspend fun getPositions(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> {
        // Для заглушки возвращаем пустой список позиций
        return emptyList()
    }

    override suspend fun getInstrumentByFigi(figi: String): InstrumentResponse {
        // Заглушка: полный инструмент не возвращаем
        throw NotImplementedError("MockBroker не поддерживает поиск инструментов")
    }

    override suspend fun resolveTicker(ticker: String): String? = ticker
    override suspend fun getInstrumentByTicker(ticker: String): InstrumentUi? = null

    override suspend fun findInstrumentShorts(query: String): List<InstrumentShort> = emptyList()
    override suspend fun getLastPrices(figis: List<String>): Map<String, Double?> = emptyMap()
    override suspend fun sandboxPayIn(accountId: String, amount: MoneyValue) {
        // Ничего не делаем в заглушке
    }
    override suspend fun getMarginAttributes(accountId: String): GetMarginAttributesResponse {
        // Возвращаем пустой ответ – баланс будет 0, но это не критично для заглушки
        return GetMarginAttributesResponse.newBuilder().build()
    }
}