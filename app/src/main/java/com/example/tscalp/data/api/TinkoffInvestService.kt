package com.example.tscalp.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ttech.piapi.core.InvestApi
import ru.tinkoff.piapi.contract.v1.*
import com.example.tscalp.di.ServiceLocator
import ru.tinkoff.piapi.contract.v1.InstrumentIdType
import ru.tinkoff.piapi.contract.v1.InstrumentRequest

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

    /**
     * Получает полную информацию об инструменте по его FIGI.
     * В запросе обязательно указывает тип идентификатора — FIGI.
     */
    suspend fun getInstrumentByFigi(figi: String): Instrument = withContext(Dispatchers.IO) {
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

    suspend fun findInstruments(figi: String): List<Instrument> = withContext(Dispatchers.IO) {
        // Создаём запрос с указанием типа идентификатора и самого FIGI
        val request = InstrumentRequest.newBuilder()
            .setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_FIGI) // <-- ключевое изменение
            .setId(figi)
            .build()
        val shortList = api.instrumentsServiceSync.getInstrumentBy(request)
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