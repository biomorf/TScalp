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

/**
 * ///Репозиторий – преобразует контракты API в доменные модели приложения.
 */
class InvestRepository(
    private val brokerManager: BrokerManager
) {

    companion object {
        private const val TAG = "InvestRepository"
    }

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

    suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean
    ): OrderResult = withContext(Dispatchers.IO) {
        val response = brokerManager.getDefaultBroker().postMarketOrder(figi, quantity, direction, accountId, sandboxMode)
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
                profitPercent = 0.0
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
                    instrumentType = short.instrumentType
                )
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось получить полный инструмент для ${short.figi}, используем краткие данные")
                InstrumentUi(
                    figi = short.figi,
                    ticker = short.ticker,
                    name = short.name,
                    currency = "—",
                    lot = 1,
                    instrumentType = short.instrumentType
                )
            }
        }
    }

    suspend fun getLastPrice(figi: String): Double? {
        return brokerManager.getDefaultBroker().getLastPrice(figi)
    }
}

data class InstrumentUi(
    val figi: String,
    val ticker: String,
    val name: String,
    val currency: String,
    val lot: Int,
    val instrumentType: String = ""   /// тип инструмента: share, bond, etf, currency
)