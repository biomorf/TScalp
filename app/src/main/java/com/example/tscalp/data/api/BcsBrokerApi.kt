package com.example.tscalp.data.api

import android.util.Log
import com.example.tscalp.domain.api.BrokerApi
import com.example.tscalp.domain.models.PortfolioPosition
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import ru.tinkoff.piapi.contract.v1.*
import java.io.IOException

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

    private var refreshToken: String? = null
    private var clientId: String? = null
    private var accessToken: String? = null
    private var tokenExpiry: Long = 0

    // Флаг инициализации
    override val isInitialized: Boolean
        get() = accessToken != null || refreshToken != null

    /**
     * Устанавливает refresh-токен и пытается получить access-токен.
     */
    suspend fun initialize(refreshToken: String, clientId: String) {
        this.refreshToken = refreshToken
        this.clientId = clientId
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
            .url("$PROD_BASE_URL$TOKEN_PATH")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()
        try {
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e("BcsBrokerApi", "Ошибка получения access-токена: ${response.code}, тело: $errorBody")
                throw IOException("Ошибка получения access-токена: ${response.code}")
            }
            val responseBody = response.body?.string() ?: throw IOException("Пустой ответ")
            val map: Map<String, Any> = gson.fromJson(responseBody, object : TypeToken<Map<String, Any>>() {}.type)
            accessToken = map["access_token"] as? String ?: throw IOException("Не найден access_token в ответе")
            val expiresIn = (map["expires_in"] as? Double)?.toLong() ?: 3600
            tokenExpiry = System.currentTimeMillis() + expiresIn * 1000
            Log.d("BcsBrokerApi", "Access-токен получен успешно, истекает через $expiresIn сек")
        } catch (e: Exception) {
            Log.e("BcsBrokerApi", "Ошибка обмена токена", e)
            throw e
        }
    }

    private suspend fun ensureAccessToken() {
        if (accessToken == null || System.currentTimeMillis() > tokenExpiry - 60000) {
            obtainAccessToken()        // без аргументов
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
        body: RequestBody? = null
    ): Response = withContext(Dispatchers.IO) {
        ensureAccessToken()
        val fullUrl = "$PROD_BASE_URL$path"

        // Временное логирование – увидим точный URL
        Log.d("BcsBrokerApi", "Запрос: $method $fullUrl")

        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json") // на случай POST/PUT, не помешает

        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(body ?: "{}".toRequestBody("application/json".toMediaType()))
            "PUT" -> requestBuilder.put(body ?: "{}".toRequestBody("application/json".toMediaType()))
        }

        client.newCall(requestBuilder.build()).execute()
    }

    // ---------- Реализация интерфейса BrokerApi ----------

    /**
     * Получаем счета через эндпоинт портфеля.
     * Из ответа извлекаем accountId и название счета, чтобы создать список Account.
     */
    /**
     * Получает счета. Сначала запрашивает портфель, чтобы извлечь accountId.
     * Если ответ содержит массив "accounts" – использует его, иначе – создаёт один счёт из "brokerAccountId".
     */
    /**
     * Получает счета на основе ответа портфеля.
     * Используем первый элемент массива, чтобы извлечь номер счёта (поле "account").
     */
    override suspend fun getAccounts(sandboxMode: Boolean): List<Account> {
        val response = makeRequest("GET", PORTFOLIO_PATH)
        if (!response.isSuccessful) throw IOException("Ошибка получения данных портфеля: ${response.code}")

        val json = response.body?.string() ?: throw IOException("Пустой ответ")
        // Ответ – это массив позиций
        val positionsArray: List<Map<String, Any>> = gson.fromJson(json, object : TypeToken<List<Map<String, Any>>>() {}.type)

        if (positionsArray.isEmpty()) {
            return listOf(
                Account.newBuilder()
                    .setId("bcs-default")
                    .setName("БКС Счёт")
                    .setTypeValue(1)
                    .build()
            )
        }

        val firstPosition = positionsArray[0]
        val accountId = firstPosition["account"] as? String ?: "bcs-default"
        val accountName = firstPosition["account"] as? String ?: "БКС Счёт"

        return listOf(
            Account.newBuilder()
                .setId(accountId)
                .setName(accountName)
                .setTypeValue(1)
                .build()
        )
    }

    /**
     * Получает позиции портфеля.
     * Ответ от сервера — массив JSON-объектов.
     */
    override suspend fun getPositions(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> {
        val response = makeRequest("GET", PORTFOLIO_PATH)
        if (!response.isSuccessful) throw IOException("Ошибка получения портфеля: ${response.code}")

        val json = response.body?.string() ?: throw IOException("Пустой ответ")
        Log.d("BcsBrokerApi", "Ответ портфеля (позиции): $json")

        val allPositions: List<Map<String, Any>> = gson.fromJson(
            json,
            object : TypeToken<List<Map<String, Any>>>() {}.type
        )

        // Фильтруем только те позиции, которые принадлежат указанному счёту
        val filtered = allPositions.filter { posMap ->
            val acc = posMap["account"] as? String ?: ""
            acc == accountId
        }

        return filtered.mapNotNull { posMap: Map<String, Any> ->
            val ticker = posMap["ticker"] as? String ?: return@mapNotNull null
            val exchange = posMap["exchange"] as? String ?: ""
            val figi = if (exchange.isNotEmpty()) "$ticker:$exchange" else ticker
            val name = posMap["displayName"] as? String ?: ticker
            val quantity = (posMap["quantity"] as? Number)?.toLong() ?: 0L
            val currentPrice = (posMap["currentPrice"] as? Number)?.toDouble() ?: 0.0
            val totalValue = (posMap["currentValue"] as? Number)?.toDouble() ?: (currentPrice * quantity)

            PortfolioPosition(
                figi = figi,
                name = name,
                ticker = ticker,
                quantity = quantity,
                currentPrice = currentPrice,
                totalValue = totalValue,
                brokerName = "bcs"
            )
        }.distinctBy { it.ticker } // убираем дубликаты по FIGI внутри одного брокера
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