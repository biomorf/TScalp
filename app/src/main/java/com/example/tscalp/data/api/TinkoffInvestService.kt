package com.example.tscalp.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.*
import ru.tinkoff.piapi.core.InvestApi

class TinkoffInvestService(private val context: Context) {

    companion object {
        private const val TAG = "TinkoffInvestService"
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

    private var api: InvestApi? = null
    private var sandboxMode: Boolean = true

    val isInitialized: Boolean
        get() = api != null

    fun initialize(token: String, sandbox: Boolean = true) {
        try {
            Log.d(TAG, "Инициализация API, sandbox: $sandbox")
            sandboxMode = sandbox

            api = if (sandbox) {
                InvestApi.createSandbox(token)
            } else {
                InvestApi.create(token)
            }

            securePrefs.edit().putString("api_token", token).apply()
            securePrefs.edit().putBoolean("sandbox_mode", sandbox).apply()
            Log.d(TAG, "API успешно инициализирован, sandbox_mode сохранён: $sandboxMode")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации API", e)
            throw e
        }
    }

    suspend fun getAccounts(): List<Account> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        try {
            Log.d(TAG, "Запрос списка счетов")

            val accounts = if (sandboxMode) {
                // В песочнице пробуем получить счета через sandboxService
                currentApi.sandboxService.getAccountsSync()
            } else {
                currentApi.userService.getAccountsSync()
            }

            Log.d(TAG, "Получено ${accounts.size} счетов")
            accounts
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения счетов", e)
            throw Exception("Не удалось получить счета: ${e.message}")
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

    fun clearToken() {
        api?.destroy(3)
        api = null
        securePrefs.edit().remove("api_token").apply()
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