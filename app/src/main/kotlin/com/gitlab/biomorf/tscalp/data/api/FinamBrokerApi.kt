package com.gitlab.biomorf.tscalp.data.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.io.IOException

import com.gitlab.biomorf.tscalp.util.AppLogger
import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.*


class FinamBrokerApi : BrokerApi {

    companion object {
        private const val TAG = "FinamBrokerApi"
        private const val BASE_URL = "https://api.finam.ru/v1/"
    }

    @Volatile
    private var client: OkHttpClient? = null
    @Volatile
    private var jwtToken: String? = null

    override val name: BrokerName = BrokerName.FINAM
    override val isInitialized: Boolean
        get() = jwtToken != null && client != null

    fun initialize(token: String) {
        client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        jwtToken = token
    }

    fun deinitialize() {
        jwtToken = null
        client = null
    }

    // ---------- Вспомогательные методы для HTTP-запросов ----------
    private fun buildGetRequest(path: String): Request {
        return Request.Builder()
            .url("${BASE_URL}$path")
            .header("Authorization", "Bearer $jwtToken")
            .get()
            .build()
    }

    private fun buildPostRequest(path: String, jsonBody: String): Request {
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("${BASE_URL}$path")
            .header("Authorization", "Bearer $jwtToken")
            .post(body)
            .build()
    }

    private suspend fun <T> executeRequest(request: Request, parser: (String) -> T): T {
        return withContext(Dispatchers.IO) {
            val response = client?.newCall(request)?.execute()
            response?.use { resp ->
                if (!resp.isSuccessful) {
                    throw Exception("HTTP ${resp.code}: ${resp.message}")
                }
                val body = resp.body.string().ifBlank { throw IOException("Пустой ответ") }
                parser(body)
            } ?: throw Exception("Клиент не инициализирован")
        }
    }

    // ---------- Реализация интерфейса BrokerApi ----------

    override suspend fun getAccounts(sandboxMode: Boolean): List<BrokerAccount> = withContext(Dispatchers.IO) {
        // TODO: запрос к /accounts
        emptyList()
    }

    override suspend fun openSandboxAccount(): String {
        TODO("Not yet implemented")
    }

    override suspend fun closeSandboxAccount(accountId: String) {
        TODO("Not yet implemented")
    }

    override fun subscribePositions(accountId: String): Flow<PositionStreamItem> = callbackFlow {
        // Стартовый снапшот
        try {
            val positions = fetchPositionsRest(accountId, false)
            positions.forEach { pos -> trySend(convertToStreamItem(pos)) }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Finam snapshot failed", e)
        }

        // Периодический опрос
        while (isActive) {
            delay(10_000)
            try {
                val positions = fetchPositionsRest(accountId, false)
                positions.forEach { pos -> trySend(convertToStreamItem(pos)) }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Finam polling error", e)
            }
        }

        awaitClose { /* cleanup if needed */ }
    }

    private fun convertToStreamItem(pos: PortfolioPosition) = PositionStreamItem(
        tscalpInstrumentId = pos.tscalpInstrumentId,
        brokerName = BrokerName.FINAM,
        ticker = pos.ticker,
        quantity = pos.quantity,
        currentPrice = pos.currentPrice,
        averagePositionPrice = pos.averagePrice,
        expectedYield = pos.profit
    )

    // реализация на основе HTTP-клиента
    override suspend fun fetchPositionsRest(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> = withContext(Dispatchers.IO) {
        // TODO: запрос портфеля
        emptyList()
    }

    override suspend fun getBalance(accountId: String): Double = withContext(Dispatchers.IO) {
        // TODO: запрос баланса
        0.0
    }

    override suspend fun sandboxPayIn(accountId: String, amount: SandboxMoney) {
        TODO("Not yet implemented")
    }

    override suspend fun findInstruments(query: String): List<InstrumentUi> {
        // TODO: поиск инструментов
        return emptyList()
    }

    override suspend fun postOrder(request: BrokerOrderRequest): OrderResult = withContext(Dispatchers.IO) {
        // TODO: выставление заявки
        OrderResult("", 0L, 0L, OrderStatus.NEW)
    }

    override suspend fun getOrders(accountId: String): List<OrderListItem> {
        return emptyList()
    }

    override suspend fun cancelOrder(accountId: String, orderId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun postStopOrder(request: StopOrderRequest): String {
        TODO("Not yet implemented")
    }

    override suspend fun getStopOrders(accountId: String): List<OrderListItem> {
        return emptyList()
    }

    override suspend fun cancelStopOrder(accountId: String, stopOrderId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun getLastPricesByTscalpInstrumentId(ids: List<String>): Map<String, Double?> {
        // TODO: запрос последних цен через REST
        return emptyMap()
    }

    override suspend fun getTradingStatuses(ids: List<String>): Map<String, TradingAvailability> {
        return emptyMap()
    }

    override suspend fun subscribeOrderState(accountId: String): Flow<OrderState> {
        // Будет реализовано через WebSocket
        return flowOf()
    }

    override suspend fun checkTradeAvailability(
        accountId: String,
        tscalpInstrumentId: String,
        uid: String?,
        direction: OrderDirection,
        quantity: Long
    ): TradeCheckResult {
        return TradeCheckResult.Success
    }


}