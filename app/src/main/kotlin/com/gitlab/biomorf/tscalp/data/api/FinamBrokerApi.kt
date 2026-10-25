package com.gitlab.biomorf.tscalp.data.api

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerInstrumentApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerSnapshotApi
import com.gitlab.biomorf.tscalp.domain.api.roles.OrdersApi
import com.gitlab.biomorf.tscalp.domain.api.roles.StreamsApi
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * REST-клиент Finam Broker API.
 *
 * **Статус: заглушка.** Методы `BrokerApi` пока не реализованы:
 * часть возвращает пустые значения (`emptyList`, `emptyMap`,
 * `0.0`, `OrderResult` с заглушечными полями), часть бросает
 * `NotImplementedError` через `TODO("Not yet implemented")`.
 *
 * Брокер зарегистрирован в `DataModule` и отображается в UI
 * наравне с T-Invest и BCS, но фактически не работает.
 *
 * Реализация всех методов — отдельная задача.
 * См. ROADMAP → «Реализация Finam Broker API».
 *
 * Каркас: базовый URL `https://api.finam.ru/v1/`,
 * JWT-авторизация, OkHttp-клиент.
 * Архитектурный долг: 24 функции в классе. См. ROADMAP → «Рефакторинг BrokerApi-реализаций».
 */
@Suppress("TooManyFunctions")
class FinamBrokerApi :
    BrokerApi,
    BrokerLifecycleApi {
    companion object {
        private const val TAG = "FinamBrokerApi"
        private const val BASE_URL = "https://api.finam.ru/v1/"
    }

    @Volatile
    private var client: OkHttpClient? = null

    @Volatile
    private var jwtToken: String? = null

    override val name: BrokerName = BrokerName.FINAM
    override val snapshot: BrokerSnapshotApi? = null
    override val lifecycle: BrokerLifecycleApi get() = this
    override val streams: StreamsApi? = null
    override val orders: OrdersApi? = null
    override val instrument: BrokerInstrumentApi? = null
    override val isInitialized: Boolean
        get() = jwtToken != null && client != null

    fun initialize(token: String) {
        client =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        jwtToken = token
    }

    /**
     * Typed lifecycle initialize via [BrokerLifecycleApi].
     * Delegates to [initialize] with Finam credentials.
     * Returns false on any exception.
     */
    override suspend fun initialize(credentials: BrokerCredentials): Boolean =
        when (credentials) {
            is BrokerCredentials.Finam -> {
                try {
                    initialize(credentials.token)
                    true
                } catch (e: Exception) {
                    AppLogger.e("FinamBrokerApi", "lifecycle.initialize failed", e)
                    false
                }
            }
            else -> throw IllegalArgumentException(
                "FinamBrokerApi.initialize: expected Finam credentials, got ${credentials::class.simpleName}",
            )
        }

    override fun deinitialize() {
        jwtToken = null
        client = null
    }

    // ---------- Вспомогательные методы для HTTP-запросов ----------
    private fun buildGetRequest(path: String): Request =
        Request
            .Builder()
            .url("${BASE_URL}$path")
            .header("Authorization", "Bearer $jwtToken")
            .get()
            .build()

    private fun buildPostRequest(
        path: String,
        jsonBody: String,
    ): Request {
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        return Request
            .Builder()
            .url("${BASE_URL}$path")
            .header("Authorization", "Bearer $jwtToken")
            .post(body)
            .build()
    }

    private suspend fun <T> executeRequest(
        request: Request,
        parser: (String) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            val response = client?.newCall(request)?.execute()
            response?.use { resp ->
                if (!resp.isSuccessful) {
                    throw Exception("HTTP ${resp.code}: ${resp.message}")
                }
                val body = resp.body.string().ifBlank { throw IOException("Пустой ответ") }
                parser(body)
            } ?: throw Exception("Клиент не инициализирован")
        }

    // ---------- Реализация интерфейса BrokerApi ----------

    override suspend fun getAccounts(sandboxMode: Boolean): List<BrokerAccount> =
        withContext(Dispatchers.IO) {
            emptyList()
        }

    override suspend fun openSandboxAccount(): String {
        TODO("Not yet implemented")
    }

    override suspend fun closeSandboxAccount(accountId: String) {
        TODO("Not yet implemented")
    }

    override fun subscribePositions(accountId: String): Flow<PositionStreamItem> =
        callbackFlow {
            // Стартовый снапшот
            try {
                val positions = fetchPositionsSnapshot(accountId, false)
                positions.forEach { pos -> trySend(convertToStreamItem(pos)) }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Finam snapshot failed", e)
            }

            // Периодический опрос
            while (isActive) {
                delay(10_000)
                try {
                    val positions = fetchPositionsSnapshot(accountId, false)
                    positions.forEach { pos -> trySend(convertToStreamItem(pos)) }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Finam polling error", e)
                }
            }

            awaitClose { /* cleanup if needed */ }
        }

    private fun convertToStreamItem(pos: PortfolioPosition) =
        PositionStreamItem(
            tscalpInstrumentId = pos.tscalpInstrumentId,
            brokerName = BrokerName.FINAM,
            ticker = pos.ticker,
            quantity = pos.quantity,
            currentPrice = pos.currentPrice,
            averagePositionPrice = pos.averagePrice,
            expectedYield = pos.profit,
        )

    // реализация на основе HTTP-клиента
    override suspend fun fetchPositionsSnapshot(
        accountId: String,
        sandboxMode: Boolean,
    ): List<PortfolioPosition> =
        withContext(Dispatchers.IO) {
            emptyList()
        }

    override suspend fun getBalance(accountId: String): Double =
        withContext(Dispatchers.IO) {
            0.0
        }

    override suspend fun sandboxPayIn(
        accountId: String,
        amount: SandboxMoney,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun findInstruments(query: String): List<InstrumentUi> = emptyList()

    override suspend fun postOrder(request: BrokerOrderRequest): OrderResult =
        withContext(Dispatchers.IO) {
            OrderResult("", 0L, 0L, OrderStatus.NEW)
        }

    override suspend fun getOrders(accountId: String): List<OrderListItem> = emptyList()

    override suspend fun cancelOrder(
        accountId: String,
        orderId: String,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun postStopOrder(request: StopOrderRequest): String {
        TODO("Not yet implemented")
    }

    override suspend fun getStopOrders(accountId: String): List<OrderListItem> = emptyList()

    override suspend fun cancelStopOrder(
        accountId: String,
        stopOrderId: String,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun getLastPricesByTscalpInstrumentId(ids: List<String>): Map<String, Double?> = emptyMap()

    override suspend fun getTradingStatuses(ids: List<String>): Map<String, TradingAvailability> = emptyMap()

    override suspend fun subscribeOrderState(accountId: String): Flow<OrderState> {
        // Будет реализовано через WebSocket
        return flowOf()
    }

    override suspend fun checkTradeAvailability(
        accountId: String,
        tscalpInstrumentId: String,
        brokerUid: String?,
        direction: OrderDirection,
        quantity: Long,
    ): TradeCheckResult = TradeCheckResult.Success
}
