package com.example.tscalp.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.conscrypt.Conscrypt
import ru.tinkoff.piapi.contract.v1.*
import ru.tinkoff.piapi.core.InvestApi
import ru.tinkoff.piapi.core.models.Portfolio

class TinkoffInvestService(private val context: Context) {

    companion object {
        private const val TAG = "TinkoffInvestService"
    }

    //init {
        // Регистрируем Conscrypt как провайдера безопасности
    //    Security.insertProviderAt(Conscrypt.newProvider(), 1)
    //}

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

    val isInitialized: Boolean
        get() = api != null

    fun initialize(token: String, sandboxMode: Boolean = true) {
        try {
            Log.d(TAG, "Инициализация API, sandbox: $sandboxMode")
            api = if (sandboxMode) {
                InvestApi.createSandbox(token)
            } else {
                InvestApi.create(token)
            }
            securePrefs.edit().putString("api_token", token).apply()
            Log.d(TAG, "API успешно инициализирован")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации API", e)
            throw e
        }
    }

    fun tryInitializeFromStorage(): Boolean {
        val token = securePrefs.getString("api_token", null)
        return if (token != null) {
            try {
                initialize(token)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка инициализации из хранилища", e)
                false
            }
        } else {
            false
        }
    }

    suspend fun getAccounts(): List<Account> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        try {
            Log.d(TAG, "Запрос списка счетов")
            val accounts = currentApi.userService.getAccountsSync()
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
        try {
            Log.d(TAG, "Отправка заявки: FIGI=$figi, кол-во=$quantity, направление=$direction")

            val zeroQuotation = Quotation.newBuilder()
                .setUnits(0)
                .setNano(0)
                .build()

            val response = currentApi.ordersService.postOrderSync(
                figi,
                quantity,
                zeroQuotation,
                direction,
                accountId,
                OrderType.ORDER_TYPE_MARKET,
                ""
            )
            Log.d(TAG, "Заявка отправлена, orderId=${response.orderId}")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отправки заявки", e)
            throw Exception("Не удалось выставить заявку: ${e.message}")
        }
    }

    suspend fun getPortfolio(accountId: String): Portfolio = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        try {
            Log.d(TAG, "Запрос портфеля для счета: $accountId")
            val portfolio = currentApi.operationsService.getPortfolioSync(accountId)
            Log.d(TAG, "Портфель получен, позиций: ${portfolio.positions.size}")
            portfolio
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения портфеля", e)
            throw Exception("Не удалось получить портфель: ${e.message}")
        }
    }

    suspend fun getInstrumentByFigi(figi: String): Instrument = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        try {
            Log.d(TAG, "Запрос инструмента по FIGI: $figi")
            val instrument = currentApi.instrumentsService.getInstrumentByFigiSync(figi)
            Log.d(TAG, "Инструмент получен: ${instrument.ticker} - ${instrument.name}")
            instrument
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения инструмента", e)
            throw Exception("Не удалось получить информацию об инструменте: ${e.message}")
        }
    }

    fun clearToken() {
        api?.destroy(3)
        api = null
        securePrefs.edit().remove("api_token").apply()
        Log.d(TAG, "Токен очищен")
    }
}