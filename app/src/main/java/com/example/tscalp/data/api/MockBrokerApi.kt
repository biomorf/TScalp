package com.example.tscalp.data.api

import android.util.Log

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

import com.example.tscalp.domain.api.BrokerApi
import com.example.tscalp.domain.models.InstrumentUi
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.domain.models.SandboxMoney
import com.example.tscalp.domain.models.BrokerAccount
import com.example.tscalp.domain.models.OrderResult
import com.example.tscalp.domain.models.OrderStatus
import com.example.tscalp.domain.models.BrokerOrderRequest
import com.example.tscalp.domain.models.BrokerAccountType
import com.example.tscalp.domain.models.OrderListItem
import com.example.tscalp.domain.models.OrderState
import com.example.tscalp.domain.models.*

/**
 * Тестовый брокер-заглушка для демонстрации мультиброкерности.
 * Возвращает предопределённые ответы и не выполняет реальных операций.
 */
class MockBrokerApi : BrokerApi {

    override val isInitialized: Boolean = true

    override suspend fun getAccounts(sandboxMode: Boolean): List<BrokerAccount> = listOf(
        BrokerAccount("mock-1", "Тестовый счёт (Mock)", BrokerAccountType.BROKER),
        BrokerAccount("mock-2", "ИИС (Mock)", BrokerAccountType.IIS)
    )

    override suspend fun openSandboxAccount(): String {
        throw UnsupportedOperationException("Открытие счёта песочницы не поддерживается для этого брокера")
    }

    override suspend fun closeSandboxAccount(accountId: String) {
        throw UnsupportedOperationException("Закрытие счетов песочницы не поддерживается для этого брокера")
    }

    override suspend fun postOrder(request: BrokerOrderRequest): OrderResult {
        return OrderResult(
            orderId = "mock-order-${System.currentTimeMillis()}",
            executedLots = request.quantity,
            totalLots = request.quantity,
            status = OrderStatus.NEW
        )
    }

//    override suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse {
//        // Возвращаем пустой портфель
//        return PortfolioResponse.newBuilder().build()
//    }

    override suspend fun getPositions(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> {
        // Для заглушки возвращаем пустой список позиций
        return emptyList()
    }



    override suspend fun resolveTicker(ticker: String): String? = ticker

    override suspend fun findInstruments(query: String): List<InstrumentUi> = emptyList()

    //override suspend fun getInstrumentByTicker(ticker: String): InstrumentUi? = null

    //override suspend fun findInstrumentShorts(query: String): List<InstrumentShort> = emptyList()

    override suspend fun sandboxPayIn(accountId: String, amount: SandboxMoney) {
        Log.w("BcsBrokerApi", "Пополнение песочницы для БКС не реализовано")
    }

    override suspend fun getBalance(accountId: String): Double = 100_000.0

//    override suspend fun getMarginAttributes(accountId: String): GetMarginAttributesResponse {
//        // Возвращаем пустой ответ – баланс будет 0, но это не критично для заглушки
//        return GetMarginAttributesResponse.newBuilder().build()
//    }

    override suspend fun getLastPricesByTicker(tickers: List<String>): Map<String, Double?> = emptyMap()

    override suspend fun postStopOrder(request: StopOrderRequest): String = "mock-stop-${System.currentTimeMillis()}"
    override suspend fun getStopOrders(accountId: String): List<OrderListItem> = emptyList()
    override suspend fun cancelStopOrder(accountId: String, stopOrderId: String) {}

    override suspend fun getOrders(accountId: String): List<OrderListItem> = emptyList()

    override suspend fun cancelOrder(accountId: String, orderId: String) {
        // Заглушка – отмена обычных заявок не эмулируется
        throw UnsupportedOperationException("cancelOrder не поддерживается для MockBrokerApi")
    }

    override suspend fun getTradingStatuses(ids: List<String>): Map<String, TradingAvailability> {
        return ids.associateWith { TradingAvailability.UNKNOWN }
    }

    override suspend fun subscribeOrderState(accountId: String): Flow<OrderState> = flowOf()

    override suspend fun checkTradeAvailability(
        accountId: String,
        tscalpInstrumentId: String,
        uid: String?,
        direction: OrderDirection,
        quantity: Long
    ): TradeCheckResult = TradeCheckResult.Success
}