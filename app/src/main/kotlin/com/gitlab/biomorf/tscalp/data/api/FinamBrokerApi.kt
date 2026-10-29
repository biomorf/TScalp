package com.gitlab.biomorf.tscalp.data.api

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.roles.AccountsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BalanceApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerInstrumentApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerSnapshotApi
import com.gitlab.biomorf.tscalp.domain.api.roles.MarketDataApi
import com.gitlab.biomorf.tscalp.domain.api.roles.OrdersApi
import com.gitlab.biomorf.tscalp.domain.api.roles.PortfolioApi
import com.gitlab.biomorf.tscalp.domain.api.roles.SandboxApi
import com.gitlab.biomorf.tscalp.domain.api.roles.StreamsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.TradeCheckApi
import com.gitlab.biomorf.tscalp.domain.api.roles.UserInstrumentsApi
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.Dispatchers
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
class FinamBrokerApi(
    private val logger: Logger,
) :
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
    override val accounts: AccountsApi? = null
    override val sandbox: SandboxApi? = null
    override val userInstruments: UserInstrumentsApi? = null
    override val balance: BalanceApi? = null
    override val instrument: BrokerInstrumentApi? = null
    override val portfolio: PortfolioApi? = null
    override val marketData: MarketDataApi? = null
    override val tradeCheck: TradeCheckApi? = null
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
                    logger.e("FinamBrokerApi", "lifecycle.initialize failed", e)
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
}
