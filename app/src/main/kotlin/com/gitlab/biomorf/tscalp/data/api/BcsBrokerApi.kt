package com.gitlab.biomorf.tscalp.data.api

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.roles.AccountsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BalanceApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerInstrumentApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerSnapshotApi
import com.gitlab.biomorf.tscalp.domain.api.roles.OrdersApi
import com.gitlab.biomorf.tscalp.domain.api.roles.SandboxApi
import com.gitlab.biomorf.tscalp.domain.api.roles.StreamsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.UserInstrumentsApi
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccountType
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderType
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderListItem
import com.gitlab.biomorf.tscalp.domain.models.OrderResult
import com.gitlab.biomorf.tscalp.domain.models.OrderStatus
import com.gitlab.biomorf.tscalp.domain.models.PortfolioPosition
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult
import com.gitlab.biomorf.tscalp.util.AppLogger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Реализация BrokerApi для брокера БКС (Мир Инвестиций).
 * Использует HTTP REST API с авторизацией OAuth2.
 *
 * Архитектурный долг: 25 функций в классе.
 * См. ROADMAP → «Рефакторинг BrokerApi-реализаций».
 */
@Suppress("TooManyFunctions")
class BcsBrokerApi :
    BrokerApi,
    BrokerLifecycleApi,
    AccountsApi {
    companion object {
        private const val TAG = "BcsBrokerApi"
        private const val PROD_BASE_URL = "https://be.broker.ru"

        // Точный путь авторизации, как в вашем примере
        private const val TOKEN_PATH = "/trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token"

        // Остальные пути оставляем прежними (при необходимости их тоже можно будет заменить)
        private const val PORTFOLIO_PATH = "/trade-api-bff-portfolio/api/v1/portfolio"
        private const val ORDERS_PATH = "/api/v1/orders"
    }

    private val client = OkHttpClient()
    private val gson = Gson()

    private var refreshToken: String? = null
    private var clientId: String? = null
    private var accessToken: String? = null
    private var tokenExpiry: Long = 0

    override val name: BrokerName = BrokerName.BCS
    override val snapshot: BrokerSnapshotApi? = null
    override val lifecycle: BrokerLifecycleApi get() = this
    override val streams: StreamsApi? = null
    override val orders: OrdersApi? = null
    override val accounts: AccountsApi get() = this
    override val sandbox: SandboxApi? = null
    override val userInstruments: UserInstrumentsApi? = null
    override val balance: BalanceApi? = null
    override val instrument: BrokerInstrumentApi? = null

    // Флаг инициализации
    override val isInitialized: Boolean
        get() = accessToken != null || refreshToken != null

    /**
     * Устанавливает refresh-токен и пытается получить access-токен.
     */
    suspend fun initialize(
        refreshToken: String,
        clientId: String,
    ) {
        this.refreshToken = refreshToken
        this.clientId = clientId
        AppLogger.d("BcsBrokerApi", "Инициализация с refreshToken=$refreshToken, clientId=$clientId")
        obtainAccessToken()
    }

    /**
     * Typed lifecycle initialize via [BrokerLifecycleApi].
     * Derives clientId from isWriteMode, obtains access token.
     * Returns false on any exception.
     */
    override suspend fun initialize(credentials: BrokerCredentials): Boolean =
        when (credentials) {
            is BrokerCredentials.Bcs -> {
                try {
                    val clientId = if (credentials.isWriteMode) "trade-api-write" else "trade-api-read"
                    initialize(credentials.refreshToken, clientId)
                    true
                } catch (e: Exception) {
                    AppLogger.e("BcsBrokerApi", "lifecycle.initialize failed", e)
                    false
                }
            }
            else -> throw IllegalArgumentException(
                "BcsBrokerApi.initialize: expected Bcs credentials, got ${credentials::class.simpleName}",
            )
        }

    override fun deinitialize() {
        accessToken = null
        refreshToken = null
        clientId = null
        tokenExpiry = 0
    }

    private suspend fun obtainAccessToken() {
        val token = requireNotNull(refreshToken) { "Refresh token not set" }
        val cid = requireNotNull(clientId) { "Client ID not set" }
        val formBody =
            FormBody
                .Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", token)
                .add("client_id", cid)
                .build()
        val request =
            Request
                .Builder()
                .url("$PROD_BASE_URL$TOKEN_PATH")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody)
                .build()
        try {
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                AppLogger.e("BcsBrokerApi", "Ошибка получения access-токена: ${response.code}, тело: $errorBody")
                throw IOException("Ошибка получения access-токена: ${response.code}")
            }
            parseAccessTokenResponse(response.body.string())
        } catch (e: Exception) {
            AppLogger.e("BcsBrokerApi", "Ошибка обмена токена", e)
            throw e
        }
    }

    /**
     * Разбирает JSON-ответ BCS на access-токен и срок действия.
     * Устанавливает [accessToken] и [tokenExpiry].
     *
     * @throws IOException если тело пустое или в нём нет access_token.
     */
    private fun parseAccessTokenResponse(responseBody: String) {
        val body = responseBody.ifBlank { throw IOException("Пустой ответ") }
        val map: Map<String, Any> = gson.fromJson(body, object : TypeToken<Map<String, Any>>() {}.type)
        accessToken = map["access_token"] as? String ?: throw IOException("Не найден access_token в ответе")
        val expiresIn = (map["expires_in"] as? Double)?.toLong() ?: 3600
        tokenExpiry = System.currentTimeMillis() + expiresIn * 1000
        AppLogger.d("BcsBrokerApi", "Access-токен получен успешно, истекает через $expiresIn сек")
    }

    private suspend fun ensureAccessToken() {
        if (accessToken == null || System.currentTimeMillis() > tokenExpiry - 60000) {
            obtainAccessToken() // без аргументов
        }
    }

    // Вспомогательный метод для отправки запросов

    /**
     * Выполняет HTTP-запрос к API БКС с автоматическим добавлением access-токена.
     * @param method HTTP-метод (GET, POST, PUT)
     * @param path путь относительно baseUrl
     * @param body тело запроса (для POST/PUT)
     * @return Response
     */
    private suspend fun makeRequest(
        method: String,
        path: String,
        body: RequestBody? = null,
    ): Response =
        withContext(Dispatchers.IO) {
            ensureAccessToken()
            val fullUrl = "$PROD_BASE_URL$path"

            AppLogger.d("BcsBrokerApi", "Запрос: $method $fullUrl")

            val requestBuilder =
                Request
                    .Builder()
                    .url(fullUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Content-Type", "application/json")

            when (method.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> requestBuilder.post(body ?: "{}".toRequestBody("application/json".toMediaType()))
                "PUT" -> requestBuilder.put(body ?: "{}".toRequestBody("application/json".toMediaType()))
            }

            client.newCall(requestBuilder.build()).execute()
        }

    // ---------- Реализация интерфейса BrokerApi ----------

    /**
     * Получает счета на основе ответа портфеля.
     * Использует поле "accounts" из ответа, если оно есть,
     * иначе — создаёт один счёт из первого элемента портфеля.
     */
    @Suppress("UNCHECKED_CAST")
    override suspend fun getAccounts(sandboxMode: Boolean): List<BrokerAccount> {
        val response = makeRequest("GET", PORTFOLIO_PATH)
        if (!response.isSuccessful) throw IOException("Ошибка получения портфеля: ${response.code}")

        val json = response.body.string().ifBlank { throw IOException("Пустой ответ") }
        val portfolioData: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)

        // Пытаемся найти массив счетов в ответе
        val accountsList = portfolioData["accounts"] as? List<*>
        if (!accountsList.isNullOrEmpty()) {
            return accountsList.mapNotNull { acc ->
                val accMap = acc as? Map<String, Any> ?: return@mapNotNull null
                BrokerAccount(
                    id = accMap["id"] as? String ?: accMap["brokerAccountId"] as? String ?: "",
                    name = accMap["name"] as? String ?: accMap["accountName"] as? String ?: "",
                    type = BrokerAccountType.BROKER,
                )
            }
        }

        // Если счетов нет – создаём один счёт из первого элемента портфеля
        val firstPosition = portfolioData["positions"] as? List<*>
        val accountId = (firstPosition?.firstOrNull() as? Map<String, Any>)?.get("account") as? String ?: "bcs-default"
        return listOf(
            BrokerAccount(
                id = accountId,
                name = accountId,
                type = BrokerAccountType.BROKER,
            ),
        )
    }

    override suspend fun openSandboxAccount(): String = throw UnsupportedOperationException("Открытие счёта песочницы не поддерживается для БКС")

    override suspend fun closeSandboxAccount(accountId: String): Unit = throw UnsupportedOperationException("Закрытие счёта песочницы не поддерживается для БКС")

    override suspend fun sandboxPayIn(
        accountId: String,
        amount: SandboxMoney,
    ) {
        AppLogger.w("BcsBrokerApi", "Пополнение песочницы для БКС не реализовано")
    }

    override suspend fun getBalance(accountId: String): Double {
        // Пока возвращаем 0, так как API БКС не предоставляет прямого метода баланса
        return 0.0
    }

    /**
     * Заглушка: стрим позиций BCS не реализован.
     *
     * Polling-loop был сломан (транспорт BCS не отлажен до
     * успешного соединения), удалён. Восстановление — задача
     * `### Реализация BCS Broker API` в ROADMAP.
     */
    override fun subscribePositions(accountId: String): Flow<PositionStreamItem> = emptyFlow()

    private fun convertToStreamItem(pos: PortfolioPosition) =
        PositionStreamItem(
            tscalpInstrumentId = pos.tscalpInstrumentId,
            brokerName = BrokerName.BCS,
            ticker = pos.ticker,
            quantity = pos.quantity,
            currentPrice = pos.currentPrice,
            averagePositionPrice = pos.averagePrice,
            expectedYield = pos.profit,
        )

    // реализация на основе HTTP-клиента
    override suspend fun fetchPortfolioPositions(
        accountId: String,
        sandboxMode: Boolean,
    ): List<PortfolioPosition> {
        val response = makeRequest("GET", PORTFOLIO_PATH)
        if (!response.isSuccessful) throw IOException("Ошибка получения портфеля: ${response.code}")

        val json = response.body.string().ifBlank { throw IOException("Пустой ответ") }
        AppLogger.d("BcsBrokerApi", "Ответ портфеля (позиции): $json")

        val allPositions: List<Map<String, Any>> =
            gson.fromJson(
                json,
                object : TypeToken<List<Map<String, Any>>>() {}.type,
            )

        // Фильтруем только те позиции, которые принадлежат указанному счёту
        val filtered =
            allPositions.filter { posMap ->
                val acc = posMap["account"] as? String ?: ""
                acc == accountId
            }

        return filtered
            .mapNotNull { posMap: Map<String, Any> ->
                val ticker = posMap["ticker"] as? String ?: return@mapNotNull null
                val exchange = posMap["exchange"] as? String ?: ""
                val figi = if (exchange.isNotEmpty()) "$ticker:$exchange" else ticker
                val name = posMap["displayName"] as? String ?: ticker
                val quantity = (posMap["quantity"] as? Number)?.toLong() ?: 0L
                val currentPrice = (posMap["currentPrice"] as? Number)?.toDouble() ?: 0.0
                val totalValue = (posMap["currentValue"] as? Number)?.toDouble() ?: (currentPrice * quantity)

                PortfolioPosition(
                    tscalpInstrumentId = figi,
                    brokerName = BrokerName.BCS,
                    name = name,
                    ticker = ticker,
                    quantity = quantity,
                    currentPrice = currentPrice,
                    totalValue = totalValue,
                    profit = null,
                    profitPercent = null,
                    averagePrice = null, // если нет
                )
            }.distinctBy { it.ticker } // убираем дубликаты по FIGI внутри одного брокера
    }

    override suspend fun findInstruments(query: String): List<InstrumentUi> = emptyList()

    override suspend fun postOrder(request: BrokerOrderRequest): OrderResult {
        val side = if (request.direction == OrderDirection.BUY) "buy" else "sell"
        val type = if (request.type == BrokerOrderType.MARKET) "market" else "limit"
        val priceField = if (request.price != null) """, "limitPrice": ${request.price}""" else ""
        val body =
            """
            {
                "symbol": "${request.ticker}",
                "side": "$side",
                "quantity": ${request.quantity},
                "accountId": "${request.accountId}",
                "type": "$type"
                $priceField
            }
            """.trimIndent().toRequestBody("application/json".toMediaType())

        val response = makeRequest("POST", ORDERS_PATH, body)
        if (!response.isSuccessful) throw IOException("Ошибка выставления заявки: ${response.code}")

        val json = response.body.string().ifBlank { throw IOException("Пустой ответ") }
        val orderMap: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)

        return OrderResult(
            orderId = orderMap["orderId"] as? String ?: "",
            executedLots = (orderMap["executedQuantity"] as? Double)?.toLong() ?: 0L,
            totalLots = request.quantity,
            status = OrderStatus.NEW,
        )
    }

    override suspend fun getLastPricesByTscalpInstrumentId(ids: List<String>): Map<String, Double?> = emptyMap()

    override suspend fun postStopOrder(request: StopOrderRequest): String = "bcs-stop-${System.currentTimeMillis()}"

    override suspend fun getStopOrders(accountId: String): List<OrderListItem> = emptyList()

    override suspend fun cancelStopOrder(
        accountId: String,
        stopOrderId: String,
    ) {
        // Заглушка: отмена стоп-заявок не поддерживается для BCS
        throw UnsupportedOperationException("cancelStopOrder не реализован для BcsBrokerApi")
    }

    override suspend fun getOrders(accountId: String): List<OrderListItem> = emptyList()

    override suspend fun cancelOrder(
        accountId: String,
        orderId: String,
    ) {
        // Заглушка: отмена обычных заявок не поддерживается для BCS
        throw UnsupportedOperationException("cancelOrder не реализован для BcsBrokerApi")
    }

    override suspend fun getTradingStatuses(ids: List<String>): Map<String, TradingAvailability> = ids.associateWith { TradingAvailability.UNKNOWN }

    override suspend fun subscribeOrderState(accountId: String): Flow<OrderState> = flowOf()

    override suspend fun checkTradeAvailability(
        accountId: String,
        tscalpInstrumentId: String,
        brokerUid: String?,
        direction: OrderDirection,
        quantity: Long,
    ): TradeCheckResult = TradeCheckResult.Success
}
