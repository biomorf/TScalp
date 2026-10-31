package com.gitlab.biomorf.tscalp.data.api.bcs

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.roles.AccountsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BalanceApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BcsLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerInstrumentApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerSnapshotApi
import com.gitlab.biomorf.tscalp.domain.api.roles.FinamLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.MarketDataApi
import com.gitlab.biomorf.tscalp.domain.api.roles.OrdersApi
import com.gitlab.biomorf.tscalp.domain.api.roles.PortfolioApi
import com.gitlab.biomorf.tscalp.domain.api.roles.SandboxApi
import com.gitlab.biomorf.tscalp.domain.api.roles.StreamsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.TInvestLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.TradeCheckApi
import com.gitlab.biomorf.tscalp.domain.api.roles.UserInstrumentsApi
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccountType
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
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
class BcsBrokerApi(
    private val logger: Logger,
) : BrokerApi,
    BcsLifecycleApi,
    AccountsApi,
    PortfolioApi {
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
    override val lifecycleTInvest: TInvestLifecycleApi? = null
    override val lifecycleBcs: BcsLifecycleApi get() = this
    override val lifecycleFinam: FinamLifecycleApi? = null
    override val streams: StreamsApi? = null
    override val orders: OrdersApi? = null
    override val accounts: AccountsApi get() = this
    override val sandbox: SandboxApi? = null
    override val userInstruments: UserInstrumentsApi? = null
    override val balance: BalanceApi? = null
    override val instrument: BrokerInstrumentApi? = null
    override val portfolio: PortfolioApi get() = this
    override val marketData: MarketDataApi? = null
    override val tradeCheck: TradeCheckApi? = null

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
        logger.d("BcsBrokerApi", "Инициализация с refreshToken.len=${refreshToken.length}, clientId=$clientId")
        obtainAccessToken()
    }

    /**
     * Typed lifecycle initialize via [BcsLifecycleApi].
     * Derives clientId from isWriteMode, obtains access token.
     * Returns false on any exception.
     */
    override suspend fun initialize(credentials: BrokerCredentials.Bcs): Boolean =
        try {
            val clientId = if (credentials.isWriteMode) "trade-api-write" else "trade-api-read"
            initialize(credentials.refreshToken, clientId)
            true
        } catch (e: Exception) {
            logger.e("BcsBrokerApi", "lifecycle.initialize failed", e)
            false
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
                logger.e("BcsBrokerApi", "Ошибка получения access-токена: ${response.code}, тело: $errorBody")
                throw IOException("Ошибка получения access-токена: ${response.code}")
            }
            parseAccessTokenResponse(response.body.string())
        } catch (e: Exception) {
            logger.e("BcsBrokerApi", "Ошибка обмена токена", e)
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
        logger.d("BcsBrokerApi", "Access-токен получен успешно, истекает через $expiresIn сек")
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

            logger.d("BcsBrokerApi", "Запрос: $method $fullUrl")

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

    // реализация на основе HTTP-клиента
    override suspend fun fetchPortfolioPositions(
        accountId: String,
        sandboxMode: Boolean,
    ): List<PositionStreamItem> {
        val response = makeRequest("GET", PORTFOLIO_PATH)
        if (!response.isSuccessful) throw IOException("Ошибка получения портфеля: ${response.code}")

        val json = response.body.string().ifBlank { throw IOException("Пустой ответ") }
        logger.d("BcsBrokerApi", "Ответ портфеля (позиции): $json")

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
                val quantity = (posMap["quantity"] as? Number)?.toLong() ?: 0L
                val currentPrice = (posMap["currentPrice"] as? Number)?.toDouble() ?: 0.0

                PositionStreamItem(
                    tscalpInstrumentId = figi,
                    brokerName = BrokerName.BCS,
                    ticker = ticker,
                    quantity = quantity,
                    currentPrice = currentPrice,
                )
            }.distinctBy { it.ticker }
    }
}
