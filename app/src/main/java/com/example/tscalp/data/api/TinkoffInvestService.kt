package com.example.tscalp.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ttech.piapi.core.InvestApi
import ru.tinkoff.piapi.contract.v1.*
import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.domain.api.BrokerApi
import ru.tinkoff.piapi.contract.v1.InstrumentIdType
import ru.tinkoff.piapi.contract.v1.InstrumentRequest

/**
 * Сервис для низкоуровневых вызовов T-Invest API.
 * Использует глобальный клиент из ServiceLocator.
 * Все методы – suspend, выполняются на IO-потоке.
 */
class TinkoffInvestService : BrokerApi {

    companion object {
        private const val TAG = "TinkoffInvestService"
    }

    private val api: InvestApi
        get() = ServiceLocator.getApiOrNull() ?: throw IllegalStateException("API не инициализирован")

    override val isInitialized: Boolean
        get() = api != null

    override suspend fun getAccounts(sandboxMode: Boolean): List<Account> = withContext(Dispatchers.IO) {
        val request = GetAccountsRequest.getDefaultInstance()
        return@withContext if (sandboxMode) {
            api.sandboxServiceSync.getSandboxAccounts(request).accountsList
        } else {
            api.usersServiceSync.getAccounts(request).accountsList
        }
    }

    override suspend fun postMarketOrder(
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

    override suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse = withContext(Dispatchers.IO) {
        val request = PortfolioRequest.newBuilder().setAccountId(accountId).build()
        return@withContext if (sandboxMode) {
            api.sandboxServiceSync.getSandboxPortfolio(request)
        } else {
            api.operationsServiceSync.getPortfolio(request)
        }
    }

    /**
     * Получает полную информацию об инструменте по его FIGI.
     * В запросе обязательно указывает тип идентификатора — FIGI.
     */
    override suspend fun getInstrumentByFigi(figi: String): Instrument = withContext(Dispatchers.IO) {
        try {
            // Создаём запрос с указанием типа идентификатора и самого FIGI
            val request = InstrumentRequest.newBuilder()
                .setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_FIGI) // <-- ключевое изменение
                .setId(figi)
                .build()
            val response = api.instrumentsServiceSync.getInstrumentBy(request)
            response.instrument
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения инструмента по FIGI $figi", e)
            throw Exception("Не удалось получить инструмент: ${e.message}")
        }
    }

    /**
     * Поиск инструментов по строковому запросу.
     * Возвращает список кратких данных (InstrumentShort), без попытки получить полный Instrument.
     * Полные данные (валюта, лот) будут загружены позже при необходимости.
     */
    override suspend fun findInstrumentShorts(query: String): List<InstrumentShort> = withContext(Dispatchers.IO) {
        try {
            val request = FindInstrumentRequest.newBuilder().setQuery(query).build()
            val response = api.instrumentsServiceSync.findInstrument(request)
            response.instrumentsList
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка поиска инструментов", e)
            throw Exception("Не удалось выполнить поиск: ${e.message}")
        }
    }

    override suspend fun getLastPrice(figi: String): Double? = withContext(Dispatchers.IO) {
        try {
            val request = GetLastPricesRequest.newBuilder().addFigi(figi).build()
            val response = api.marketDataServiceSync.getLastPrices(request)
            response.lastPricesList.firstOrNull()?.price?.let {
                it.units + it.nano / 1_000_000_000.0
            }
        } catch (e: Exception) {
            null
        }
    }
}