package com.example.tscalp.data.repository

import android.util.Log
//import com.example.tscalp.data.api.TinkoffInvestService
import com.example.tscalp.di.BrokerManager
import com.example.tscalp.domain.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.OrderDirection
import ru.tinkoff.piapi.contract.v1.AccountType as TinkoffAccountType
import ru.tinkoff.piapi.contract.v1.Instrument
import ru.tinkoff.piapi.contract.v1.InstrumentResponse
import ru.tinkoff.piapi.contract.v1.MoneyValue
import ru.tinkoff.piapi.contract.v1.OrderType
import ru.tinkoff.piapi.contract.v1.Quotation
import com.example.tscalp.domain.models.OrderStatus


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
     * Получает счета для брокера по умолчанию (Tinkoff).
     * Оставлен для совместимости с существующим кодом.
     */
    suspend fun getAccounts(sandboxMode: Boolean): List<AccountUi> = withContext(Dispatchers.IO) {
        val accounts = brokerManager.getDefaultBroker().getAccounts(sandboxMode)
        accounts.map { account ->
            AccountUi(
                id = account.id,
                name = account.name,
                type = when (account.typeValue) {
                    1 -> AccountType.BROKER
                    2 -> AccountType.IIS
                    3 -> AccountType.INVEST_BOX
                    else -> AccountType.BROKER
                }
            )
        }
    }

    /**
     * Получает список счетов для указанного брокера.
     * @param brokerName имя брокера (например, "tinkoff")
     * @param sandboxMode режим песочницы
     */
    suspend fun getAccounts(brokerName: String, sandboxMode: Boolean): List<AccountUi> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getBroker(brokerName)
            ?: throw IllegalArgumentException("Брокер $brokerName не найден")
        val accounts = broker.getAccounts(sandboxMode)
        accounts.map { account ->
            AccountUi(
                id = account.id,
                name = account.name,
                type = when (account.typeValue) {
                    1 -> AccountType.BROKER
                    2 -> AccountType.IIS
                    3 -> AccountType.INVEST_BOX
                    else -> AccountType.BROKER
                }
            )
        }
    }

    /**
     * Отправляет рыночную заявку через указанного брокера.
     */
    suspend fun postMarketOrder(
        brokerName: String,
        ticker: String,            // теперь ticker вместо figi
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean
    ): OrderResult = withContext(Dispatchers.IO) {
        val broker = brokerManager.getBroker(brokerName)
            ?: throw IllegalArgumentException("Брокер $brokerName не найден")
        ///val response = broker.postMarketOrder(figi, quantity, direction, accountId, sandboxMode)
        // Разрешаем ticker в нужный идентификатор
        val figi = broker.resolveTicker(ticker) ?: throw IllegalArgumentException("Инструмент с тикером $ticker не найден")
        // Вызываем старый метод с figi
        val response = broker.postMarketOrder(figi, quantity, direction, accountId, sandboxMode)
        OrderResult(
            orderId = response.orderId,
            executedLots = response.lotsExecuted,
            totalLots = response.lotsRequested,
            status = when (response.executionReportStatus) {
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_NEW -> OrderStatus.NEW
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_PARTIALLYFILL -> OrderStatus.PARTIALLY_FILLED
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_FILL -> OrderStatus.FILLED
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_REJECTED -> OrderStatus.REJECTED
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_CANCELLED -> OrderStatus.CANCELLED
                else -> OrderStatus.NEW
            }
        )
    }

    /**
     * Отправляет заявку (рыночную или лимитную) через указанного брокера.
     */
    suspend fun postOrder(
        brokerName: String,
        ticker: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean,
        orderType: OrderType,
        price: Quotation
    ): OrderResult = withContext(Dispatchers.IO) {
        val broker = brokerManager.getBroker(brokerName)
            ?: throw IllegalArgumentException("Брокер $brokerName не найден")
        val figi = broker.resolveTicker(ticker) ?: throw IllegalArgumentException("Инструмент с тикером $ticker не найден")
        val response = broker.postOrder(figi, quantity, direction, accountId, sandboxMode, orderType, price)
        OrderResult(
            orderId = response.orderId,
            executedLots = response.lotsExecuted,
            totalLots = response.lotsRequested,
            status = when (response.executionReportStatus) {
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_NEW -> OrderStatus.NEW
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_PARTIALLYFILL -> OrderStatus.PARTIALLY_FILLED
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_FILL -> OrderStatus.FILLED
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_REJECTED -> OrderStatus.REJECTED
                ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_CANCELLED -> OrderStatus.CANCELLED
                else -> OrderStatus.NEW
            }
        )
    }

    suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> = withContext(Dispatchers.IO) {
        val response = brokerManager.getDefaultBroker().getPortfolio(accountId, sandboxMode)
        response.positionsList.mapNotNull { pos ->
            val instrument = try {
                val instrumentResponse = brokerManager.getDefaultBroker().getInstrumentByFigi(pos.figi)
                instrumentResponse.instrument  /// извлекаем Instrument из InstrumentResponse
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось получить инструмент ${pos.figi}")
                return@mapNotNull null
            }
            val quantity = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 }?.toLong() ?: 0L
            if (quantity == 0L) return@mapNotNull null
            val currentPrice = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
            val totalValue = currentPrice * quantity

            PortfolioPosition(
                figi = pos.figi,
                name = instrument.name,
                ticker = instrument.ticker,
                quantity = quantity,
                currentPrice = currentPrice,
                totalValue = totalValue,
                profit = 0.0,
                profitPercent = 0.0,
                instrumentType = instrument.instrumentType ?: ""
            )
        }
    }

    /**
     * ///Получение полного инструмента по FIGI (обёртка над TinkoffInvestService).
     */
    suspend fun getInstrumentByFigi(figi: String): InstrumentResponse {
        return brokerManager.getDefaultBroker().getInstrumentByFigi(figi)
    }

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
        val shorts = brokerManager.getDefaultBroker().findInstrumentShorts(query)
        shorts.map { short ->
            try {
                val fullResponse = brokerManager.getDefaultBroker().getInstrumentByFigi(short.figi) /// InstrumentResponse
                val instrument = fullResponse.instrument /// Извлекаем Instrument
                InstrumentUi(
                    figi = instrument.figi,
                    ticker = instrument.ticker,
                    name = instrument.name,
                    currency = instrument.currency,
                    lot = instrument.lot,
                    instrumentType = instrument.instrumentType
                )
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось получить полный инструмент для ${short.figi}, используем краткие данные")
                InstrumentUi(
                    figi = short.figi,
                    ticker = short.ticker,
                    name = short.name,
                    currency = "—",
                    lot = 1,
                    instrumentType = "unknown"
                )
            }
        }
    }

    suspend fun getLastPrices(figis: List<String>): Map<String, Double?> {
        return brokerManager.getDefaultBroker().getLastPrices(figis)
    }

    /**
     * Получает последние цены для списка тикеров.
     * Внутри вызывает resolveBrokerTicker для каждого тикера и запрашивает цены через брокера.
     */
    suspend fun getLastPricesByTicker(
        brokerName: String,
        tickers: List<String>
    ): Map<String, Double?> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getBroker(brokerName) ?: return@withContext emptyMap()
        val figiByTicker = mutableMapOf<String, String>()
        for (ticker in tickers) {
            val figi = broker.resolveTicker(ticker)
            if (figi != null) {
                figiByTicker[ticker] = figi
            }
        }
        if (figiByTicker.isEmpty()) return@withContext emptyMap()
        val figiPrices = broker.getLastPrices(figiByTicker.values.toList())
        // Преобразуем обратно в ticker -> price
        figiPrices.mapKeys { (figi, price) ->
            figiByTicker.entries.firstOrNull { it.value == figi }?.key ?: figi
        }
    }

    suspend fun getBalance(accountId: String): Double = withContext(Dispatchers.IO) {
        val response = brokerManager.getDefaultBroker().getMarginAttributes(accountId)
        val money = response.liquidPortfolio
        (money?.units ?: 0) + (money?.nano ?: 0) / 1_000_000_000.0
    }

    suspend fun sandboxPayIn(accountId: String, amount: Long) {
        val money = MoneyValue.newBuilder()
            .setUnits(amount)
            .setNano(0)
            .setCurrency("RUB")
            .build()
        brokerManager.getDefaultBroker().sandboxPayIn(accountId, money)
    }
}
