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
            Log.d(TAG, "API успешно инициализирован")

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
}