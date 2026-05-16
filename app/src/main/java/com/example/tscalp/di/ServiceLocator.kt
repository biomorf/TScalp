package com.example.tscalp.di

import android.content.Context
import android.content.SharedPreferences

import com.example.tscalp.domain.api.BrokerApi
import com.example.tscalp.data.api.TInvestInvestService
import com.example.tscalp.data.api.BcsBrokerApi
import com.example.tscalp.data.api.FinamBrokerApi
import com.example.tscalp.data.repository.InstrumentRepository
import com.example.tscalp.data.repository.SearchCache


object ServiceLocator {

    private lateinit var prefs: SharedPreferences

    fun getPrefs(): SharedPreferences = prefs

    @Volatile
    private var brokerManager: BrokerManager? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("tinvest_prefs", Context.MODE_PRIVATE)
    }

    // --- Управление брокерами ---

    private fun createBrokerManager(): BrokerManager {
        val brokers: Map<String, BrokerApi> = mapOf(
            "TInvest" to TInvestInvestService(),
            "bcs" to BcsBrokerApi(),
            "finam" to FinamBrokerApi()
        )
        return BrokerManager(brokers)
    }

    fun getBrokerManager(): BrokerManager {
        return brokerManager ?: synchronized(this) {
            brokerManager ?: createBrokerManager().also { brokerManager = it }
        }
    }

    fun isAnyBrokerInitialized(): Boolean {
        return getBrokerManager().getAllBrokers().any { it.isInitialized }
    }

    // --- Управление учётными данными брокеров ---

    fun saveBrokerCredentials(brokerName: String, token: String, sandbox: Boolean) {
        prefs.edit()
            .putString("${brokerName}_token", token)
            .putBoolean("${brokerName}_sandbox", sandbox)
            .apply()
    }

    fun loadBrokerCredentials(brokerName: String): Pair<String, Boolean>? {
        val token = prefs.getString("${brokerName}_token", null) ?: return null
        val sandbox = prefs.getBoolean("${brokerName}_sandbox", true)
        return Pair(token, sandbox)
    }

    fun clearBrokerCredentials(brokerName: String) {
        prefs.edit()
            .remove("${brokerName}_token")
            .remove("${brokerName}_sandbox")
            .apply()
    }

    /**
     * Сохраняет токен для указанного брокера.
     * Для Финама ключ будет "finam_token".
     */
    fun saveToken(brokerName: String, token: String) {
        prefs.edit().putString("${brokerName}_token", token).apply()
    }

    fun hasSavedToken(brokerName: String): Boolean =
        prefs.contains("${brokerName}_token")

    //fun getToken(): String? = prefs.getString("TInvest_token", null)

    /**
     * Возвращает токен для указанного брокера или null.
     */
    fun getToken(brokerName: String): String? {
        return prefs.getString("${brokerName}_token", null)
    }


    /**
     * Сохраняет идентификатор счёта по умолчанию для указанного брокера.
     */
    fun saveDefaultAccountId(brokerName: String, accountId: String) {
        prefs.edit().putString("${brokerName}_default_account", accountId).apply()
    }

    /**
     * Возвращает сохранённый идентификатор счёта по умолчанию или null.
     */
    fun loadDefaultAccountId(brokerName: String): String? {
        return prefs.getString("${brokerName}_default_account", null)
    }

    fun isSandboxMode(): Boolean = prefs.getBoolean("TInvest_sandbox", true)

    // --- Управление флагом подтверждения заявок ---

    fun isConfirmOrdersEnabled(): Boolean =
        prefs.getBoolean("confirm_orders_enabled", true)

    fun setConfirmOrdersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("confirm_orders_enabled", enabled).apply()
    }

    @Volatile
    private var instrumentRepository: InstrumentRepository? = null

    fun getInstrumentRepository(): InstrumentRepository {
        return instrumentRepository ?: synchronized(this) {
            instrumentRepository ?: InstrumentRepository(
                brokerManager = getBrokerManager()
            ).also { instrumentRepository = it }
        }
    }

    @Volatile
    private var searchCache: SearchCache? = null

    fun getSearchCache(): SearchCache {
        return searchCache ?: synchronized(this) {
            searchCache ?: SearchCache(getBrokerManager()).also { searchCache = it }
        }
    }
}