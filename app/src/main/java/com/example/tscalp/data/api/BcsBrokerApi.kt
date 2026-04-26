package com.example.tscalp.data.api

import android.util.Log
import com.example.tscalp.domain.api.BrokerApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import ru.tinkoff.piapi.contract.v1.*
import java.io.IOException
import com.example.tscalp.domain.models.PortfolioPosition

/**
 * Реализация BrokerApi для брокера БКС (Мир Инвестиций).
 * Использует HTTP REST API с авторизацией OAuth2.
 */
class BcsBrokerApi : BrokerApi {

    companion object {
        private const val PROD_BASE_URL = "https://be.broker.ru"
        // Точный путь авторизации, как в вашем примере
        private const val TOKEN_PATH = "/trade-api-keycloak/realms/tradeapi/protocol/openid-connect/token"
        // Остальные пути оставляем прежними (при необходимости их тоже можно будет заменить)
        private const val ACCOUNTS_PATH = "/trade-api-bff-account/api/v1/accounts"
        private const val PORTFOLIO_PATH = "/trade-api-bff-portfolio/api/v1/portfolio"
        private const val ORDERS_PATH = "/api/v1/orders"
        private const val INSTRUMENTS_PATH = "/api/v1/securities"
        private const val MARKET_DATA_PATH = "/api/v1/marketdata"
        private const val SANDBOX_PAY_IN_PATH = "/api/v1/sandbox/operations/deposit"
    }

    private val client = OkHttpClient()
    private val gson = Gson()

    // Зависит от режима (песочница/боевой) – устанавливается при инициализации
    private var baseUrl = PROD_BASE_URL
    private var refreshToken: String? = null
    private var accessToken: String? = null
    private var tokenExpiry: Long = 0
    private var clientId: String? = null

    // Флаг инициализации
    override val isInitialized: Boolean
        get() = accessToken != null || refreshToken != null

    /**
     * Устанавливает refresh-токен и пытается получить access-токен.
     */
    suspend fun initialize(refreshToken: String, clientId: String) {
        this.refreshToken = refreshToken
        this.clientId = clientId
        this.baseUrl = PROD_BASE_URL
        Log.d("BcsBrokerApi", "Инициализация с refreshToken=$refreshToken, clientId=$clientId")
        obtainAccessToken()
    }

    private suspend fun obtainAccessToken() {
        val token = refreshToken ?: throw IllegalStateException("Refresh token not set")
        val cid = clientId ?: throw IllegalStateException("Client ID not set")

        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", token)
            .add("client_id", cid)
            .build()

        val request = Request.Builder()
            .url("$baseUrl$TOKEN_PATH")
            .post(formBody)
            .build()

        withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("Ошибка получения access-токена: ${response.code}")
            val responseBody = response.body?.string() ?: throw IOException("Пустой ответ")
            val map: Map<String, Any> = gson.fromJson(responseBody, object : TypeToken<Map<String, Any>>() {}.type)
            accessToken = map["access_token"] as? String
            val expiresIn = (map["expires_in"] as? Double)?.toLong() ?: 3600
            tokenExpiry = System.currentTimeMillis() + expiresIn * 1000
        }
    }

    private suspend fun ensureAccessToken() {
        if (accessToken == null || System.currentTimeMillis() > tokenExpiry - 60000) {
            obtainAccessToken()        // без аргументов
        }
    }

    // Вспомогательный метод для отправки запросов
    private suspend fun makeRequest(
        method: String,
        path: String,
        body: RequestBody? = null
    ): Response = withContext(Dispatchers.IO) {
        ensureAccessToken()
        val requestBuilder = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $accessToken")
        when (method) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(body ?: "{}".toRequestBody())
            "PUT" -> requestBuilder.put(body ?: "{}".toRequestBody())
        }
        client.newCall(requestBuilder.build()).execute()
    }

    // ---------- Реализация интерфейса BrokerApi ----------

    override suspend fun getAccounts(sandboxMode: Boolean): List<Account> {
        val response = makeRequest("GET", ACCOUNTS_PATH)
        if (!response.isSuccessful) throw IOException("Ошибка получения счетов: ${response.code}")
        val json = response.body?.string() ?: throw IOException("Пустой ответ")
        // Предполагаем, что возвращается массив счетов. Адаптируем под реальный ответ.
        val accounts: List<Map<String, Any>> = gson.fromJson(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
        return accounts.map { acc ->
            Account.newBuilder()
                .setId(acc["brokerAccountId"] as? String ?: "")
                .setName(acc["accountName"] as? String ?: "")
                // Тип счета в БКС обычно "Брокерский", для простоты ставим BROKER
                .setTypeValue(1) // 1 = ACCOUNT_TYPE_BROKER
                .build()
        }
    }

    override suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean
    ): PostOrderResponse {
        // БКС использует параметры: symbol (тикер, не FIGI!), side (buy/sell), quantity, accountId
        // Нам нужно преобразовать figi в тикер? Лучше передавать figi как symbol, но может не работать.
        // Временно будем передавать figi. В будущем заменим на тикер.
        val side = if (direction == OrderDirection.ORDER_DIRECTION_BUY) "buy" else "sell"
        val body = """
            {
                "symbol": "$figi",
                "side": "$side",
                "quantity": $quantity,
                "accountId": "$accountId",
                "type": "market"
            }
        """.trimIndent().toRequestBody("application/json".toMediaType())

        val response = makeRequest("POST", ORDERS_PATH, body)
        if (!response.isSuccessful) throw IOException("Ошибка выставления заявки: ${response.code}")
        val json = response.body?.string() ?: throw IOException("Пустой ответ")
        val orderMap: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
        val orderId = orderMap["orderId"] as? String ?: ""
        val executedLots = (orderMap["executedQuantity"] as? Double)?.toLong() ?: 0L
        val requestedLots = quantity

        return PostOrderResponse.newBuilder()
            .setOrderId(orderId)
            .setLotsExecuted(executedLots)
            .setLotsRequested(requestedLots)
            .setExecutionReportStatus(OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_NEW)
            .build()
    }

    override suspend fun getPositions(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> {
        val response = makeRequest("GET", "$PORTFOLIO_PATH?accountId=$accountId")
        if (!response.isSuccessful) throw IOException("Ошибка получения портфеля: ${response.code}")

        val json = response.body?.string() ?: throw IOException("Пустой ответ")
        val portfolioData: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)

        val positionsList = portfolioData["positions"] as? List<*> ?: emptyList<Any>()
        return positionsList.mapNotNull { posItem ->
            val posMap = posItem as? Map<String, Any> ?: return@mapNotNull null
            val figi = posMap["figi"] as? String ?: return@mapNotNull null
            val name = posMap["name"] as? String ?: ""
            val ticker = posMap["ticker"] as? String ?: ""
            val quantity = (posMap["quantity"] as? Number)?.toLong() ?: 0L
            val currentPrice = (posMap["currentPrice"] as? Number)?.toDouble() ?: 0.0
            PortfolioPosition(
                figi = figi,
                name = name,
                ticker = ticker,
                quantity = quantity,
                currentPrice = currentPrice,
                totalValue = currentPrice * quantity,
                brokerName = "bcs"
            )
        }
    }

    // Старый getPortfolio можно оставить пустым или вовсе удалить, но для совместимости пусть возвращает пустой ответ.
    override suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse {
        return PortfolioResponse.newBuilder().build()
    }

    override suspend fun getInstrumentByFigi(figi: String): InstrumentResponse {
        // БКС API использует ticker, а не figi. Нужен отдельный поиск.
        // Пока заглушка.
        throw NotImplementedError("Метод не реализован для БКС")
    }

    override suspend fun findInstrumentShorts(query: String): List<InstrumentShort> {
        // Аналогично, потребуется адаптация
        return emptyList()
    }

    override suspend fun getLastPrices(figis: List<String>): Map<String, Double?> {
        // Потребуется запрос рыночных данных
        return emptyMap()
    }

    override suspend fun sandboxPayIn(accountId: String, amount: MoneyValue) {
        // БКС может иметь свой метод пополнения демо-счета
        val body = """
            {
                "accountId": "$accountId",
                "currency": "${amount.currency}",
                "amount": ${amount.units}
            }
        """.trimIndent().toRequestBody("application/json".toMediaType())
        makeRequest("POST", SANDBOX_PAY_IN_PATH, body)
    }

    override suspend fun getMarginAttributes(accountId: String): GetMarginAttributesResponse {
        // Заглушка
        return GetMarginAttributesResponse.newBuilder().build()
    }

    override suspend fun postOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean,
        orderType: OrderType,
        price: Quotation
    ): PostOrderResponse {
        // Аналогично postMarketOrder, но с ценой
        val side = if (direction == OrderDirection.ORDER_DIRECTION_BUY) "buy" else "sell"
        val type = if (orderType == OrderType.ORDER_TYPE_MARKET) "market" else "limit"
        val body = """
            {
                "symbol": "$figi",
                "side": "$side",
                "quantity": $quantity,
                "accountId": "$accountId",
                "type": "$type",
                "limitPrice": ${if (orderType == OrderType.ORDER_TYPE_LIMIT) "${price.units}.${price.nano}" else ""}
            }
        """.trimIndent().toRequestBody("application/json".toMediaType())
        val response = makeRequest("POST", ORDERS_PATH, body)
        if (!response.isSuccessful) throw IOException("Ошибка выставления заявки: ${response.code}")
        // Парсим ответ аналогично postMarketOrder
        val json = response.body?.string() ?: throw IOException("Пустой ответ")
        val orderMap: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
        val orderId = orderMap["orderId"] as? String ?: ""
        return PostOrderResponse.newBuilder()
            .setOrderId(orderId)
            .setLotsExecuted((orderMap["executedQuantity"] as? Double)?.toLong() ?: 0L)
            .setLotsRequested(quantity)
            .setExecutionReportStatus(OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_NEW)
            .build()
    }

    suspend fun getSandboxAccounts(): List<Account> {
        // БКС может не иметь отдельного песочного API
        return getAccounts(sandboxMode = true)
    }
}