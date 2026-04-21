package com.example.tscalp.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.core.InvestApi
import ru.tinkoff.piapi.contract.v1.*
import ru.tinkoff.piapi.contract.v1.MoneyValue
import io.grpc.*
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
class TinkoffInvestService(private val context: Context) {
    private var api: InvestApi? = null
    private var channel: ManagedChannel? = null
    private var sandboxMode: Boolean = true

    companion object {
        private const val TAG = "TinkoffInvestService"
        // Включите true только для отладки в эмуляторе!
        private const val DEBUG_IGNORE_SSL = false
        //private const val DEBUG_IGNORE_SSL = true
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "tinvest_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val isInitialized: Boolean
        get() = api != null

    fun tryInitializeFromStorage(): Boolean {
        val token = securePrefs.getString("api_token", null)
        val savedSandboxMode = securePrefs.getBoolean("sandbox_mode", true)
        return if (token != null) {
            try {
                initialize(token, savedSandboxMode)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка инициализации из хранилища", e)
                false
            }
        } else {
            false
        }
    }

    fun initialize(token: String, sandbox: Boolean = true) {
        try {
            Log.d(TAG, "Инициализация API, sandbox: $sandbox")
            this.sandboxMode = sandbox

            channel?.shutdown()
            api?.destroy(3)

            val target = if (sandbox) {
                "sandbox-invest-public-api.tbank.ru:443"
            } else {
                "invest-public-api.tbank.ru:443"
            }

            // 1. Создаём SSL‑фабрику в зависимости от флага отладки
            val sslSocketFactory = if (DEBUG_IGNORE_SSL) {
                // Только для отладки в эмуляторе – принимать любые сертификаты
                val trustAllCerts = arrayOf<X509TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                sslContext.socketFactory
            } else {
                // Безопасный вариант – системные доверенные сертификаты
                val trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()
                )
                trustManagerFactory.init(null as KeyStore?)
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustManagerFactory.trustManagers, null)
                sslContext.socketFactory
            }

            // 2. Строим канал с выбранной фабрикой
            channel = OkHttpChannelBuilder
                .forTarget(target)
                .useTransportSecurity()
                .sslSocketFactory(sslSocketFactory)   // <-- используем уже готовую фабрику
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .intercept(TokenInterceptor(token))
                .build()

            api = if (sandbox) {
                InvestApi.createSandbox(channel!!)
            } else {
                InvestApi.create(channel!!)
            }

            securePrefs.edit().putString("api_token", token).apply()
            securePrefs.edit().putBoolean("sandbox_mode", sandbox).apply()
            Log.d(TAG, "API успешно инициализирован")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации API", e)
            throw e
        }
    }

    fun clearToken() {
        api?.destroy(3)
        channel?.shutdown()
        api = null
        channel = null
        securePrefs.edit().remove("api_token").apply()
        Log.d(TAG, "Токен и канал очищены")
    }

    suspend fun getAccounts(): List<Account> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        try {
            Log.d(TAG, "Запрос списка счетов, sandbox: $sandboxMode")

            if (sandboxMode) {
                // 1. Пытаемся получить существующие счета
                var accounts = try {
                    currentApi.sandboxService.getAccountsSync()
                } catch (e: Exception) {
                    Log.w(TAG, "Ошибка получения счетов: ${e.message}")
                    emptyList()
                }

                if (accounts.isNotEmpty()) {
                    Log.d(TAG, "Получено ${accounts.size} счетов")
                    return@withContext accounts
                }

                // 2. Счетов нет — пробуем создать новый
                Log.d(TAG, "Счета не найдены, создаём новый счёт в песочнице...")
                val accountCreated = try {
                    val newAccountId = currentApi.sandboxService.openAccountSync()
                    Log.d(TAG, "Создан новый счёт с ID: $newAccountId")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Не удалось создать счёт автоматически: ${e.message}")
                    false
                }

                // 3. Даже если создание вернуло ошибку, пробуем снова получить счета
                accounts = try {
                    currentApi.sandboxService.getAccountsSync()
                } catch (e: Exception) {
                    Log.w(TAG, "Повторная ошибка получения счетов: ${e.message}")
                    emptyList()
                }

                if (accounts.isNotEmpty()) {
                    Log.d(TAG, "После попытки создания доступно ${accounts.size} счетов")

                    // Пополняем первый найденный счёт для удобства
                    if (accountCreated) {
                        try {
                            val moneyValue = MoneyValue.newBuilder()
                                .setUnits(100000)
                                .setNano(0)
                                .setCurrency("RUB")
                                .build()
                            currentApi.sandboxService.payInSync(accounts[0].id, moneyValue)
                            Log.d(TAG, "Счёт пополнен на 100 000 RUB")
                        } catch (e: Exception) {
                            Log.w(TAG, "Не удалось пополнить счёт: ${e.message}")
                        }
                    }

                    return@withContext accounts
                }

                // 4. Счёт так и не появился — даём понятную ошибку
                throw Exception(
                    "Не удалось получить или создать счёт в песочнице.\n\n" +
                            "Возможные причины:\n" +
                            "• Токен не имеет доступа к песочнице\n" +
                            "• Счёт уже существует, но не отображается\n\n" +
                            "Рекомендация: создайте счёт вручную через официальное приложение Т‑Инвестиций " +
                            "(раздел «Песочница» → «Открыть счёт»)."
                )
            } else {
                // Боевой режим
                val accounts = currentApi.userService.getAccountsSync()
                Log.d(TAG, "Получено ${accounts.size} боевых счетов")
                return@withContext accounts
            }
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка получения/создания счетов", e)
            throw e
        }
    }

    suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String
    ): PostOrderResponse = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")

        val zeroQuotation = Quotation.newBuilder()
            .setUnits(0)
            .setNano(0)
            .build()

        val response = if (sandboxMode) {
            currentApi.sandboxService.postOrderSync(
                figi,
                quantity,
                zeroQuotation,
                direction,
                accountId,
                OrderType.ORDER_TYPE_MARKET,
                ""
            )
        } else {
            currentApi.ordersService.postOrderSync(
                figi,
                quantity,
                zeroQuotation,
                direction,
                accountId,
                OrderType.ORDER_TYPE_MARKET,
                ""
            )
        }

        Log.d(TAG, "Заявка отправлена, orderId=${response.orderId}")
        response
    }

    suspend fun getPortfolio(accountId: String) = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")

        if (sandboxMode) {
            currentApi.sandboxService.getPortfolioSync(accountId)
        } else {
            currentApi.operationsService.getPortfolioSync(accountId)
        }
    }

    suspend fun getInstrumentByFigi(figi: String): Instrument = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        currentApi.instrumentsService.getInstrumentByFigiSync(figi)
    }

    suspend fun findInstrument(query: String): List<Instrument> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        try {
            Log.d(TAG, "Поиск инструмента по запросу: $query")

            // Выполняем поиск
            val response = currentApi.instrumentsService.findInstrumentSync(query)

            // Пробуем разные способы получить список инструментов
            val instrumentsList: List<*> = try {
                // Способ 1: через getInstrumentsList()
                response.javaClass.getMethod("getInstrumentsList").invoke(response) as List<*>
            } catch (e: Exception) {
                try {
                    // Способ 2: через поле instrumentsList
                    val field = response.javaClass.getDeclaredField("instrumentsList")
                    field.isAccessible = true
                    field.get(response) as List<*>
                } catch (e2: Exception) {
                    try {
                        // Способ 3: через поле instruments
                        val field = response.javaClass.getDeclaredField("instruments")
                        field.isAccessible = true
                        field.get(response) as List<*>
                    } catch (e3: Exception) {
                        // Способ 4: если response сам является списком
                        response as? List<*> ?: emptyList<Any>()
                    }
                }
            }

            Log.d(TAG, "Найдено элементов в ответе: ${instrumentsList.size}")

            // Преобразуем каждый элемент в полноценный Instrument
            val instruments = instrumentsList.mapNotNull { item ->
                item?.let {
                    try {
                        // Пробуем получить FIGI из элемента
                        val figi: String = try {
                            it.javaClass.getMethod("getFigi").invoke(it) as String
                        } catch (e: Exception) {
                            val field = it.javaClass.getDeclaredField("figi")
                            field.isAccessible = true
                            field.get(it) as String
                        }

                        // Получаем полную информацию об инструменте
                        try {
                            currentApi.instrumentsService.getInstrumentByFigiSync(figi)
                        } catch (e: Exception) {
                            Log.w(TAG, "Не удалось получить инструмент по FIGI: $figi")
                            null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Не удалось извлечь FIGI из элемента")
                        null
                    }
                }
            }

            Log.d(TAG, "Найдено ${instruments.size} инструментов")
            instruments
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка поиска инструментов", e)
            throw Exception("Не удалось выполнить поиск: ${e.message}")
        }
    }

}

class TokenInterceptor(private val token: String) : ClientInterceptor {
    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> {
        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, callOptions)
        ) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                // Добавляем токен в формате "Bearer <token>"[reference:2]
                headers.put(
                    Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER),
                    "Bearer $token"
                )
                super.start(responseListener, headers)
            }
        }
    }
}