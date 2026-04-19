package com.example.tinvest.data.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ru.tinkoff.piapi.core.InvestApi

class InvestApiService(private val context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private var _api: InvestApi? = null
    val api: InvestApi
        get() = _api ?: throw IllegalStateException("API not initialized. Call initialize() first.")
    
    val isInitialized: Boolean
        get() = _api != null
    
    fun initialize(token: String, sandboxMode: Boolean = true) {
        _api = if (sandboxMode) {
            InvestApi.createSandbox(token)
        } else {
            InvestApi.create(token)
        }
        prefs.edit().putString("token", token).apply()
    }
    
    fun tryInitializeFromStorage(): Boolean {
        val token = prefs.getString("token", null) ?: return false
        initialize(token)
        return true
    }
    
    fun clearToken() {
        _api?.destroy(3)
        _api = null
        prefs.edit().remove("token").apply()
    }
}
