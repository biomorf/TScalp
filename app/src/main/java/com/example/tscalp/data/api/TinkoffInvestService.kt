package com.example.tscalp.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ttech.piapi.core.InvestApi
import ru.tinkoff.piapi.contract.v1.*
import com.example.tscalp.di.ServiceLocator

/**
 * Сервис для низкоуровневых вызовов T-Invest API.
 * Использует глобальный клиент из ServiceLocator.
 * Все методы – suspend, выполняются на IO-потоке.
 */
class TinkoffInvestService {

    companion object {
        private const val TAG = "TinkoffInvestService"
    }

    private val api: InvestApi
        get() = ServiceLocator.getApiOrNull() ?: throw IllegalStateException("API не инициализирован")

    val isInitialized: Boolean
        get() = ServiceLocator.getApiOrNull() != null

    suspend fun getAccounts(sandboxMode: Boolean): List<Account> = withContext(Dispatchers.IO) {
        val request = GetAccountsRequest.getDefaultInstance()
        return@withContext if (sandboxMode) {
            api.sandboxServiceSync.getSandboxAccounts(request).accountsList
        } else {
            api.usersServiceSync.getAccounts(request).accountsList
        }
    }

    suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean
    ): PostOrderResponse = withContext(Dispatchers.IO) {
        val price = Quotation.newBuilder().setUnits(0).setNano(0).build()
        val request = PostOrderRequest.newBuilder()
            .setFigi(figi)
            .setQuantity(quantity)
            .setPrice(price)
            .setDirection(direction)
            .setAccountId(accountId)
            .setOrderType(OrderType.ORDER_TYPE_MARKET)
            .build()
        return@withContext if (sandboxMode) {
            api.sandboxServiceSync.postSandboxOrder(request)
        } else {
            api.ordersServiceSync.postOrder(request)
        }
    }

    suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse = withContext(Dispatchers.IO) {
        val request = PortfolioRequest.newBuilder().setAccountId(accountId).build()
        return@withContext if (sandboxMode) {
            api.sandboxServiceSync.getSandboxPortfolio(request)
        } else {
            api.operationsServiceSync.getPortfolio(request)
        }
    }

    suspend fun getInstrumentByFigi(figi: String): Instrument = withContext(Dispatchers.IO) {
        val request = InstrumentRequest.newBuilder().setId(figi).build()
        api.instrumentsServiceSync.getInstrumentBy(request).instrument
    }

    suspend fun findInstruments(query: String): List<Instrument> = withContext(Dispatchers.IO) {
        val request = FindInstrumentRequest.newBuilder().setQuery(query).build()
        val shortList = api.instrumentsServiceSync.findInstrument(request).instrumentsList
        shortList.mapNotNull { short ->
            try {
                // Запрашиваем полный инструмент по FIGI
                getInstrumentByFigi(short.figi)
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось получить полный инструмент для FIGI ${short.figi}: ${e.message}")
                null
            }
        }
    }
}