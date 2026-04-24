package com.example.tscalp.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.tscalp.di.ServiceLocator
import ru.tinkoff.piapi.contract.v1.*
import ru.ttech.piapi.core.InvestApi
import ru.ttech.piapi.core.sandbox.SandboxServiceSync
import ru.ttech.piapi.core.user.UsersServiceSync
import ru.ttech.piapi.core.order.OrdersServiceSync
import ru.ttech.piapi.core.instrument.InstrumentsServiceSync
import ru.ttech.piapi.core.marketdata.MarketDataServiceSync
import com.example.tscalp.domain.api.BrokerApi

class TinkoffInvestService : BrokerApi {

    companion object {
        private const val TAG = "TinkoffInvestService"
    }

    // Вместо собственного поля api, используем глобальный объект из ServiceLocator
    private val api: InvestApi?
        get() = ServiceLocator.getApiOrNull()

    override val isInitialized: Boolean
        get() = api != null

    // Удобная функция для получения api с проверкой
    private fun getApi(): InvestApi = api ?: throw IllegalStateException("API не инициализирован")

    override suspend fun getAccounts(sandboxMode: Boolean): List<Account> = withContext(Dispatchers.IO) {
        val currentApi = getApi()
        val request = GetAccountsRequest.getDefaultInstance()
        return@withContext if (sandboxMode) {
            SandboxServiceSync(currentApi).getSandboxAccounts(request).accountsList
        } else {
            UsersServiceSync(currentApi).getAccounts(request).accountsList
        }
    }

    override suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean
    ): PostOrderResponse = withContext(Dispatchers.IO) {
        val currentApi = getApi()
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
            SandboxServiceSync(currentApi).postSandboxOrder(request)
        } else {
            OrdersServiceSync(currentApi).postOrder(request)
        }
    }

    override suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse = withContext(Dispatchers.IO) {
        val currentApi = getApi()
        val request = PortfolioRequest.newBuilder().setAccountId(accountId).build()
        return@withContext if (sandboxMode) {
            SandboxServiceSync(currentApi).getSandboxPortfolio(request)
        } else {
            OperationsServiceSync(currentApi).getPortfolio(request)
        }
    }

    override suspend fun getInstrumentByFigi(figi: String): InstrumentResponse = withContext(Dispatchers.IO) {
        val currentApi = getApi()
        val request = InstrumentRequest.newBuilder()
            .setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_FIGI)
            .setId(figi)
            .build()
        InstrumentsServiceSync(currentApi).getInstrumentBy(request)
    }

    override suspend fun findInstrumentShorts(query: String): List<InstrumentShort> = withContext(Dispatchers.IO) {
        val currentApi = getApi()
        val request = FindInstrumentRequest.newBuilder().setQuery(query).build()
        InstrumentsServiceSync(currentApi).findInstrument(request).instrumentsList
    }

    override suspend fun getLastPrice(figi: String): Double? = withContext(Dispatchers.IO) {
        val currentApi = getApi()
        try {
            val request = GetLastPricesRequest.newBuilder().addFigi(figi).build()
            val response = MarketDataServiceSync(currentApi).getLastPrices(request)
            response.lastPricesList.firstOrNull()?.price?.let {
                it.units + it.nano / 1_000_000_000.0
            }
        } catch (e: Exception) {
            null
        }
    }
}