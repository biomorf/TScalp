package com.example.tscalp.data.repository

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единый репозиторий для пользовательских настроек, хранящихся в SharedPreferences.
 * Заменяет собой работу с prefs из ServiceLocator.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val prefs: SharedPreferences
) {

    // ---------- Учётные данные брокеров ----------

    /**
     * Сохраняет токен и режим sandbox для указанного брокера.
     * Для TInvest ключ "TInvest_token", для Finam "finam_token" и т.д.
     */
    fun saveBrokerCredentials(brokerName: String, token: String, sandbox: Boolean) {
        prefs.edit()
            .putString("${brokerName}_token", token)
            .putBoolean("${brokerName}_sandbox", sandbox)
            .commit()  // синхронная запись — токен не потеряется при kill процесса
    }

    /**
     * Возвращает пару (токен, sandbox) для указанного брокера или null.
     */
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

    fun clearTradingState(brokerName: String) {
        prefs.edit()
            .remove("selected_instrument_uid")
            .remove("paired_instrument_uid")
            .remove("pair_trading_enabled")
            .remove("quantity")
            .remove("paired_multiplier")
            .remove("order_type")
            .apply()
    }

    /**
     * Сохраняет только токен для брокера (без sandbox-флага).
     */
    fun saveToken(brokerName: String, token: String) {
        prefs.edit()
            .putString("${brokerName}_token", token)
            .commit()  // синхронная запись — токен не потеряется при kill процесса
    }

    fun hasSavedToken(brokerName: String): Boolean =
        prefs.contains("${brokerName}_token")

    fun getToken(brokerName: String): String? =
        prefs.getString("${brokerName}_token", null)

    // ---------- Счёт по умолчанию ----------

    fun saveDefaultAccountId(brokerName: String, accountId: String) {
        prefs.edit().putString("${brokerName}_default_account", accountId).apply()
    }

    fun loadDefaultAccountId(brokerName: String): String? =
        prefs.getString("${brokerName}_default_account", null)

    // ---------- Режим песочницы (для TInvest) ----------

    fun isSandboxMode(): Boolean = prefs.getBoolean("TInvest_sandbox", true)

    fun setSandboxMode(enabled: Boolean) {
        prefs.edit().putBoolean("TInvest_sandbox", enabled).apply()
    }

    // ---------- Подтверждение заявок ----------

    fun isConfirmOrdersEnabled(): Boolean =
        prefs.getBoolean("confirm_orders_enabled", true)

    fun setConfirmOrdersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("confirm_orders_enabled", enabled).apply()
    }
}
