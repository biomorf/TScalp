package com.example.tscalp.data.repository

import android.util.Log
import com.example.tscalp.di.BrokerManager
import com.example.tscalp.domain.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.tscalp.domain.models.AccountUi
import com.example.tscalp.domain.models.BrokerOrderRequest


/**
 * ///Репозиторий – преобразует контракты API в доменные модели приложения.
 */
class InvestRepository(
    private val brokerManager: BrokerManager
) {

    companion object {
        private const val TAG = "InvestRepository"
    }

    /**
     * Получает счета для брокера по умолчанию (TInvest).
     * Оставлен для совместимости с существующим кодом.
     */
    suspend fun getAccounts(brokerName: String, sandboxMode: Boolean): List<AccountUi> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getBroker(brokerName) ?: throw IllegalArgumentException("Брокер $brokerName не найден")
        val accounts = broker.getAccounts(sandboxMode)
        accounts.map { acc ->
            AccountUi(
                id = acc.id,
                name = acc.name,
                type = when (acc.type) {
                    BrokerAccountType.BROKER -> AccountType.BROKER
                    BrokerAccountType.IIS -> AccountType.IIS
                    BrokerAccountType.INVEST_BOX -> AccountType.INVEST_BOX
                    else -> AccountType.BROKER
                }
            )
        }
    }

    /**
     * Получает список счетов для указанного брокера.
     * @param brokerName имя брокера (например, "TInvest")
     * @param sandboxMode режим песочницы
     */
//    suspend fun getAccounts(brokerName: String, sandboxMode: Boolean): List<AccountUi> = withContext(Dispatchers.IO) {
//        val broker = brokerManager.getBroker(brokerName)
//            ?: throw IllegalArgumentException("Брокер $brokerName не найден")
//        val accounts = broker.getAccounts(sandboxMode)
//        accounts.map { account ->
//            AccountUi(
//                id = account.id,
//                name = account.name,
//                type = when (account.typeValue) {
//                    1 -> AccountType.BROKER
//                    2 -> AccountType.IIS
//                    3 -> AccountType.INVEST_BOX
//                    else -> AccountType.BROKER
//                }
//            )
//        }
//    }

    /**
     * Отправляет рыночную заявку через указанного брокера.
     */
//    suspend fun postMarketOrder(
//        brokerName: String,
//        ticker: String,            // теперь ticker вместо figi
//        quantity: Long,
//        direction: OrderDirection,
//        accountId: String,
//        sandboxMode: Boolean
//    ): OrderResult = withContext(Dispatchers.IO) {
//        val broker = brokerManager.getBroker(brokerName)
//            ?: throw IllegalArgumentException("Брокер $brokerName не найден")
//        ///val response = broker.postMarketOrder(figi, quantity, direction, accountId, sandboxMode)
//        // Разрешаем ticker в нужный идентификатор
//        val figi = broker.resolveTicker(ticker) ?: throw IllegalArgumentException("Инструмент с тикером $ticker не найден")
//        // Вызываем старый метод с figi
//        val response = broker.postMarketOrder(figi, quantity, direction, accountId, sandboxMode)
//        OrderResult(
//            orderId = response.orderId,
//            executedLots = response.lotsExecuted,
//            totalLots = response.lotsRequested,
//            status = when (response.executionReportStatus) {
//                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_NEW -> OrderStatus.NEW
//                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_PARTIALLYFILL -> OrderStatus.PARTIALLY_FILLED
//                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_FILL -> OrderStatus.FILLED
//                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_REJECTED -> OrderStatus.REJECTED
//                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_CANCELLED -> OrderStatus.CANCELLED
//                else -> OrderStatus.NEW
//            }
//        )
//    }

    /**
     * Отправляет заявку (рыночную или лимитную) через указанного брокера.
     */
    suspend fun postOrder(request: BrokerOrderRequest): OrderResult = withContext(Dispatchers.IO) {
        val broker = brokerManager.getBroker(request.brokerName)
            ?: throw IllegalArgumentException("Брокер ${request.brokerName} не найден")
        broker.postOrder(request)
    }

//    suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> = withContext(Dispatchers.IO) {
//        val response = brokerManager.getDefaultBroker().getPortfolio(accountId, sandboxMode)
//        response.positionsList.mapNotNull { pos ->
//            val ticker = pos.ticker.ifBlank { pos.figi } ?: return@mapNotNull null
//            val instrument = try {
//                brokerManager.getDefaultBroker().getInstrumentByTicker(ticker)
//            } catch (e: Exception) {
//                Log.w(TAG, "Не удалось получить инструмент $ticker", e)
//                null
//            }
//            val quantity = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 }?.toLong() ?: 0L
//            if (quantity == 0L) return@mapNotNull null
//            val currentPrice = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
//            val totalValue = currentPrice * quantity
//
//            PortfolioPosition(
//                name = instrument?.name ?: "",
//                ticker = ticker,
//                quantity = quantity,
//                currentPrice = currentPrice,
//                totalValue = totalValue,
//                profit = 0.0,
//                profitPercent = 0.0,
//                instrumentType = instrument?.instrumentType ?: ""
//            )
//        }
//    }

    suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getDefaultBroker()
        broker.getPositions(accountId, sandboxMode)
    }

    /**
     * ///Получение полного инструмента по FIGI (обёртка над TInvestInvestService).
     */
//    suspend fun getInstrumentByFigi(figi: String): InstrumentResponse {
//        return brokerManager.getDefaultBroker().getInstrumentByFigi(figi)
//    }

    /**
     * Возвращает специфичный для брокера идентификатор (figi, uid и т.д.) по тикеру.
     * @param brokerName имя брокера
     * @param ticker тикер инструмента
     */
    suspend fun resolveBrokerTicker(brokerName: String, ticker: String): String? {
        val broker = brokerManager.getBroker(brokerName) ?: return null
        return broker.resolveTicker(ticker)
    }

    /**
     * ///Поиск инструментов – возвращает список InstrumentUi, готовых для UI.
     * ///Если не удалось получить полный Instrument, поля currency и lot останутся по умолчанию.
     */
    suspend fun searchInstruments(query: String): List<InstrumentUi> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getDefaultBroker()
        broker.findInstruments(query)
    }



    /**
     * Получает последние цены для списка тикеров.
     * Внутри вызывает resolveBrokerTicker для каждого тикера и запрашивает цены через брокера.
     */
    suspend fun getLastPricesByTicker(tickers: List<String>): Map<String, Double?> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getDefaultBroker()
        broker.getLastPricesByTicker(tickers)
    }

//    suspend fun getBalance(accountId: String): Double = withContext(Dispatchers.IO) {
//        val response = brokerManager.getDefaultBroker().getMarginAttributes(accountId)
//        val money = response.liquidPortfolio
//        (money?.units ?: 0) + (money?.nano ?: 0) / 1_000_000_000.0
//    }

    suspend fun getBalance(accountId: String): Double = withContext(Dispatchers.IO) {
        val broker = brokerManager.getDefaultBroker()
        broker.getBalance(accountId)
    }

    suspend fun sandboxPayIn(accountId: String, amount: SandboxMoney) {
        Log.d("InvestRepository", "Вызов sandboxPayIn для счета $accountId, сумма ${amount.units} ${amount.currency}")
        val broker = brokerManager.getDefaultBroker()
        broker.sandboxPayIn(accountId, amount)
    }
}
