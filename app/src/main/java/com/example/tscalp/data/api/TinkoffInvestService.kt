package com.example.tscalp.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.domain.api.BrokerApi
import ru.tinkoff.piapi.contract.v1.*
import ru.ttech.piapi.core.InvestApi
import ru.tinkoff.piapi.contract.v1.GetMarginAttributesRequest
import ru.tinkoff.piapi.contract.v1.GetMarginAttributesResponse
import ru.tinkoff.piapi.contract.v1.SandboxPayInRequest
import ru.tinkoff.piapi.contract.v1.MoneyValue


/**
 * ///Реализация BrokerApi для брокера Т‑Инвестиции (Kotlin SDK).
 * ///Не хранит собственный экземпляр API, а получает его через ServiceLocator.
 * ///Таким образом, состояние разделяется между всеми экранами.
 */
/**
 * ///Сервис для низкоуровневых вызовов T-Invest API.
 * ///Использует глобальный клиент из ServiceLocator.
 * ///Все методы – suspend, выполняются на IO-потоке.
 */
class TinkoffInvestService : BrokerApi {

    companion object {
        private const val TAG = "TinkoffInvestService"
    }

    /// Глобальный объект API из синглтона
    private val api: InvestApi
        get() = ServiceLocator.getApiOrNull() ?: throw IllegalStateException("API не инициализирован")

    override val isInitialized: Boolean
        get() = api != null

    /**
     * ///Вспомогательный метод, который либо возвращает api, либо выбрасывает исключение.
     */
    private fun requireApi(): InvestApi = api ?: throw IllegalStateException("API не инициализирован")

    /// ------------------- Реализация методов BrokerApi -------------------

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
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отправки заявки", e)
            // Пытаемся извлечь gRPC-статус для деталей
            val status = io.grpc.Status.fromThrowable(e)
            if (status != null) {
                Log.e(TAG, "gRPC статус: ${status.code}, описание: ${status.description}")
            }
            throw Exception("Не удалось выставить заявку: ${e.message}")
        }
    }

    /**
     * Выставляет заявку (рыночную или лимитную) через Т‑Инвестиции.
     * Реализует требований интерфейса BrokerApi.
     */
    override suspend fun postOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean,
        orderType: OrderType,
        price: Quotation
    ): PostOrderResponse = withContext(Dispatchers.IO) {
        val currentApi = requireApi()
        val request = PostOrderRequest.newBuilder()
            .setFigi(figi)
            .setQuantity(quantity)
            .setPrice(price)
            .setDirection(direction)
            .setAccountId(accountId)
            .setOrderType(orderType)
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
     * ///Получает полную информацию об инструменте по его FIGI.
     * ///В запросе обязательно указывает тип идентификатора — FIGI.
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
     * ///Поиск инструментов по строковому запросу.
     * ///Возвращает список кратких данных (InstrumentShort), без попытки получить полный Instrument.
     * ///Полные данные (валюта, лот) будут загружены позже при необходимости.
     */
    override suspend fun findInstrumentShorts(query: String): List<InstrumentShort> = withContext(Dispatchers.IO) {
        val currentApi = requireApi()
        val request = FindInstrumentRequest.newBuilder().setQuery(query).build()
        currentApi.instrumentsServiceSync.findInstrument(request).instrumentsList
    }

    override suspend fun getLastPrices(figis: List<String>): Map<String, Double?> = withContext(Dispatchers.IO) {
        if (figis.isEmpty()) return@withContext emptyMap()
        val currentApi = requireApi()
        val request = GetLastPricesRequest.newBuilder().addAllFigi(figis).build()
        val response = currentApi.marketDataServiceSync.getLastPrices(request)
        response.lastPricesList.associate { it.figi to it.price?.let { p -> p.units + p.nano / 1_000_000_000.0 } }
    }

    override suspend fun getMarginAttributes(accountId: String): GetMarginAttributesResponse = withContext(Dispatchers.IO) {
        val currentApi = requireApi()
        val request = GetMarginAttributesRequest.newBuilder().setAccountId(accountId).build()
        // Метод есть только в UsersServiceSync (боевой режим)
        currentApi.usersServiceSync.getMarginAttributes(request)
    }

    override suspend fun sandboxPayIn(accountId: String, amount: MoneyValue) {
        withContext(Dispatchers.IO) {
            val currentApi = requireApi()
            val request = SandboxPayInRequest.newBuilder()
                .setAccountId(accountId)
                .setAmount(amount)
                .build()
            currentApi.sandboxServiceSync.sandboxPayIn(request)
            Log.d(TAG, "Пополнение выполнено успешно")
            // Ничего не возвращаем, Unit
        }
    }
}
