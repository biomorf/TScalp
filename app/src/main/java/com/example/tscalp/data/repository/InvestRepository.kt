package com.example.tscalp.data.repository

import android.util.Log
import com.example.tscalp.data.api.TinkoffInvestService
import com.example.tscalp.domain.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.OrderDirection
import ru.tinkoff.piapi.contract.v1.AccountType as TinkoffAccountType

/**
 * Репозиторий – преобразует контракты API в доменные модели приложения.
 */
class InvestRepository(
    private val apiService: TinkoffInvestService
) {

    companion object {
        private const val TAG = "InvestRepository"
    }

    suspend fun getAccounts(sandboxMode: Boolean): List<AccountUi> = withContext(Dispatchers.IO) {
        val accounts = apiService.getAccounts(sandboxMode)
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
        val response = apiService.postMarketOrder(figi, quantity, direction, accountId, sandboxMode)
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
        val response = apiService.getPortfolio(accountId, sandboxMode)
        response.positionsList.mapNotNull { pos ->
            val instrument = try {
                apiService.getInstrumentByFigi(pos.figi)
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

    suspend fun getInstrumentByFigi(figi: String): Instrument {
        return apiService.getInstrumentByFigi(figi)
    }

    suspend fun searchInstruments(query: String): List<InstrumentUi> = withContext(Dispatchers.IO) {
        apiService.findInstruments(query).map {
            InstrumentUi(
                figi = it.figi,
                ticker = it.ticker,
                name = it.name,
                currency = it.currency,
                lot = it.lot
            )
        }
    }
}

data class InstrumentUi(
    val figi: String,
    val ticker: String,
    val name: String,
    val currency: String,
    val lot: Int
)