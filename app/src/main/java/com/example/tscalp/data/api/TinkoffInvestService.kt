package com.example.tscalp.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ru.ttech.piapi.core.InvestApi
import ru.tinkoff.piapi.contract.v1.*
import ru.ttech.piapi.core.api.Portfolio
import kotlinx.coroutines.runBlocking

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
            Log.d(TAG, "Инициализация Kotlin SDK, sandbox: $sandbox")
            this.sandboxMode = sandbox

            api?.close()

            val target = if (sandbox) "sandbox-invest-public-api.tbank.ru:443" else "invest-public-api.tbank.ru:443"
            val channel = InvestApi.defaultChannel(token = token, target = target)
            api = InvestApi.createApi(channel)

            securePrefs.edit().putString("api_token", token).apply()
            securePrefs.edit().putBoolean("sandbox_mode", sandbox).apply()
            Log.d(TAG, "API успешно инициализирован")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации API", e)
            throw e
        }
    }

    fun tryRestoreConnection(): Boolean {
        val token = securePrefs.getString("api_token", null) ?: return false
        val savedSandbox = securePrefs.getBoolean("sandbox_mode", true)
        return try {
            initialize(token, savedSandbox)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось восстановить соединение", e)
            clearToken()
            false
        }
    }

    fun getAccountsSync(): List<Account> = runBlocking {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        if (sandboxMode) {
            currentApi.sandboxService.getSandboxAccounts().accountsList
        } else {
            currentApi.userService.getAccounts().accountsList
        }
    }

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

    suspend fun getInstrumentByFigi(figi: String): Instrument {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        return try {
            currentApi.instrumentsService.getInstrumentByFigi(figi)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения инструмента по FIGI", e)
            throw Exception("Не удалось получить инструмент: ${e.message}")
        }
    }

    suspend fun findInstruments(query: String): List<Instrument> {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        return try {
            currentApi.instrumentsService.findInstrument(query).instrumentsList
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка поиска инструментов", e)
            throw Exception("Не удалось выполнить поиск: ${e.message}")
        }
    }

    fun clearToken() {
        api?.close()
        api = null
        securePrefs.edit().remove("api_token").apply()
        Log.d(TAG, "Токен очищен")
    }
}