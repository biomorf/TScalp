package com.example.tscalp.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.domain.api.BrokerApi
import ru.tinkoff.piapi.contract.v1.*
import ru.ttech.piapi.core.InvestApi
import ru.ttech.piapi.core.SandboxServiceSync
import ru.ttech.piapi.core.UsersServiceSync
import ru.ttech.piapi.core.OrdersServiceSync
import ru.ttech.piapi.core.InstrumentsServiceSync
import ru.ttech.piapi.core.MarketDataServiceSync


/**
 * Реализация BrokerApi для брокера Т‑Инвестиции (Kotlin SDK).
 * Не хранит собственный экземпляр API, а получает его через ServiceLocator.
 * Таким образом, состояние разделяется между всеми экранами.
 */
/**
 * Сервис для низкоуровневых вызовов T-Invest API.
 * Использует глобальный клиент из ServiceLocator.
 * Все методы – suspend, выполняются на IO-потоке.
 */
class TinkoffInvestService : BrokerApi {

    companion object {
        private const val TAG = "TinkoffInvestService"
    }

    // Глобальный объект API из синглтона
    private val api: InvestApi
        get() = ServiceLocator.getApiOrNull() ?: throw IllegalStateException("API не инициализирован")

    override val isInitialized: Boolean
        get() = api != null

    /**
     * Вспомогательный метод, который либо возвращает api, либо выбрасывает исключение.
     */
    private fun requireApi(): InvestApi = api ?: throw IllegalStateException("API не инициализирован")

    // ------------------- Реализация методов BrokerApi -------------------

    override suspend fun getAccounts(sandboxMode: Boolean): List<Account> = withContext(Dispatchers.IO) {
        val currentApi = requireApi()
        val request = GetAccountsRequest.getDefaultInstance()
        return@withContext if (sandboxMode) {
            currentApi.sandboxServiceSync.getSandboxAccounts(request).accountsList
        } else {
            currentApi.usersServiceSync.getAccounts(request).accountsList
        }
    }

    override suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean
    ): PostOrderResponse = withContext(Dispatchers.IO) {
        val currentApi = requireApi()
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
            currentApi.sandboxServiceSync.postSandboxOrder(request)
        } else {
            currentApi.ordersServiceSync.postOrder(request)
        }
    }

    override suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse = withContext(Dispatchers.IO) {
        val currentApi = requireApi()
        val request = PortfolioRequest.newBuilder().setAccountId(accountId).build()
        return@withContext if (sandboxMode) {
            currentApi.sandboxServiceSync.getSandboxPortfolio(request)
        } else {
            currentApi.operationsServiceSync.getPortfolio(request)
        }
    }

    /**
     * Получает полную информацию об инструменте по его FIGI.
     * В запросе обязательно указывает тип идентификатора — FIGI.
     */
    override suspend fun getInstrumentByFigi(figi: String): InstrumentResponse = withContext(Dispatchers.IO) {
        val currentApi = requireApi()
        val request = InstrumentRequest.newBuilder()
            .setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_FIGI)
            .setId(figi)
            .build()
        currentApi.instrumentsServiceSync.getInstrumentBy(request)
    }

    /**
     * Поиск инструментов по строковому запросу.
     * Возвращает список кратких данных (InstrumentShort), без попытки получить полный Instrument.
     * Полные данные (валюта, лот) будут загружены позже при необходимости.
     */
    override suspend fun findInstrumentShorts(query: String): List<InstrumentShort> = withContext(Dispatchers.IO) {
        val currentApi = requireApi()
        val request = FindInstrumentRequest.newBuilder().setQuery(query).build()
        currentApi.instrumentsServiceSync.findInstrument(request).instrumentsList
    }

    override suspend fun getLastPrice(figi: String): Double? = withContext(Dispatchers.IO) {
        val currentApi = requireApi()
        try {
            val request = GetLastPricesRequest.newBuilder().addFigi(figi).build()
            val response = currentApi.marketDataServiceSync.getLastPrices(request)
            response.lastPricesList.firstOrNull()?.price?.let {
                it.units + it.nano / 1_000_000_000.0
            }
        } catch (e: Exception) {
            null
        }
    }
}
