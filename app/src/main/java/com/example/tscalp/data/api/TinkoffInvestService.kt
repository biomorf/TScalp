package com.example.tscalp.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ru.ttech.piapi.core.InvestApi
import ru.tinkoff.piapi.contract.v1.Account
import ru.tinkoff.piapi.contract.v1.Instrument
import ru.tinkoff.piapi.contract.v1.OrderDirection
import ru.tinkoff.piapi.contract.v1.OrderType
import ru.tinkoff.piapi.contract.v1.PostOrderRequest
import ru.tinkoff.piapi.contract.v1.PostOrderResponse
import ru.tinkoff.piapi.contract.v1.Quotation
import ru.ttech.piapi.core.models.Portfolio

/**
 * Сервис для работы с T-Invest API через Kotlin SDK.
 * Хранит токен в зашифрованном виде и управляет клиентом API.
 */
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

    /**
     * Инициализирует клиент API с токеном и режимом (боевой/песочница).
     */
    fun initialize(token: String, sandboxMode: Boolean = true) {
        try {
            Log.d(TAG, "Инициализация Kotlin SDK, sandbox: $sandboxMode")
            this.sandboxMode = sandboxMode

            // Закрываем старый клиент, если был
            api?.close()

            // Создаём нового клиента через DSL-билдер
            api = if (sandboxMode) {
                InvestApi.newSandboxClient(token) {
                    // Здесь можно настроить таймауты, перехватчики и т.д.
                }
            } else {
                InvestApi.newClient(token) {
                    // Настройки для боевого режима
                }
            }

            securePrefs.edit().putString("api_token", token).apply()
            securePrefs.edit().putBoolean("sandbox_mode", sandboxMode).apply()
            Log.d(TAG, "API успешно инициализирован")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации API", e)
            throw e
        }
    }

    /**
     * Пытается восстановить подключение из сохранённого токена.
     * @return true, если восстановление успешно
     */
    fun tryRestoreConnection(): Boolean {
        val token = securePrefs.getString("api_token", null) ?: return false
        val savedSandbox = securePrefs.getBoolean("sandbox_mode", true)
        return try {
            initialize(token, savedSandbox)
            Log.d(TAG, "Соединение восстановлено")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось восстановить соединение", e)
            clearToken()
            false
        }
    }

    /**
     * Получает список счетов пользователя.
     */
    suspend fun getAccounts(): List<Account> {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        return try {
            val response = if (sandboxMode) {
                currentApi.sandboxService.getSandboxAccounts()
            } else {
                currentApi.userService.getAccounts()
            }
            response.accountsList
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения счетов", e)
            throw Exception("Не удалось получить счета: ${e.message}")
        }
    }

    /**
     * Отправляет рыночную заявку.
     */
    suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String
    ): PostOrderResponse {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        return try {
            val price = Quotation.newBuilder().setUnits(0).setNano(0).build()
            val request = PostOrderRequest.newBuilder()
                .setFigi(figi)
                .setQuantity(quantity)
                .setPrice(price)
                .setDirection(direction)
                .setAccountId(accountId)
                .setOrderType(OrderType.ORDER_TYPE_MARKET)
                .build()
            if (sandboxMode) {
                currentApi.sandboxService.postSandboxOrder(request)
            } else {
                currentApi.ordersService.postOrder(request)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отправки заявки", e)
            throw Exception("Не удалось выставить заявку: ${e.message}")
        }
    }

    /**
     * Получает портфель по счёту.
     */
    suspend fun getPortfolio(accountId: String): Portfolio {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        return try {
            if (sandboxMode) {
                currentApi.sandboxService.getSandboxPortfolio(accountId)
            } else {
                currentApi.portfolioService.getPortfolio(accountId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения портфеля", e)
            throw Exception("Не удалось получить портфель: ${e.message}")
        }
    }

    /**
     * Получает полную информацию об инструменте по FIGI.
     */
    suspend fun getInstrumentByFigi(figi: String): Instrument {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        return try {
            currentApi.instrumentsService.getInstrumentByFigi(figi)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения инструмента по FIGI", e)
            throw Exception("Не удалось получить инструмент: ${e.message}")
        }
    }

    /**
     * Ищет инструменты по строковому запросу (тикер, название, FIGI).
     */
    suspend fun findInstruments(query: String): List<Instrument> {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        return try {
            currentApi.instrumentsService.findInstrument(query).instrumentsList
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка поиска инструментов", e)
            throw Exception("Не удалось выполнить поиск: ${e.message}")
        }
    }

    /**
     * Очищает токен и закрывает клиент API.
     */
    fun clearToken() {
        api?.close()
        api = null
        securePrefs.edit().remove("api_token").apply()
        Log.d(TAG, "Токен очищен")
    }
}