package com.example.tscalp.data.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.Account
import ru.tinkoff.piapi.contract.v1.OrderDirection
import ru.tinkoff.piapi.contract.v1.OrderType
import ru.tinkoff.piapi.contract.v1.PostOrderResponse
import ru.tinkoff.piapi.core.InvestApi

class TinkoffInvestService(private val context: Context) {
    
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
        api = if (sandboxMode) {
            InvestApi.createSandbox(token)
        } else {
            InvestApi.create(token)
        }
        securePrefs.edit().putString("api_token", token).apply()
    }
    
    fun tryInitializeFromStorage(): Boolean {
        val token = securePrefs.getString("api_token", null)
        return if (token != null) {
            initialize(token)
            true
        } else {
            false
        }
    }
    
    suspend fun getAccounts(): List<Account> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        currentApi.userService.getAccountsSync()
    }
    
    suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String
    ): PostOrderResponse = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        currentApi.ordersService.postOrderSync(
            figi = figi,
            quantity = quantity,
            direction = direction,
            accountId = accountId,
            orderType = OrderType.ORDER_TYPE_MARKET
        )
    }
    
    suspend fun getPortfolio(accountId: String) = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        currentApi.operationsService.getPortfolioSync(accountId)
    }
    
    suspend fun getInstrumentByFigi(figi: String) = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        currentApi.instrumentsService.getInstrumentByFigiSync(figi)
    }
    
    fun clearToken() {
        api?.destroy(3)
        api = null
        securePrefs.edit().remove("api_token").apply()
    }
}