package com.example.tscalp.data.repository

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

import com.example.tscalp.domain.models.BrokerName

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
    fun saveBrokerCredentials(brokerName: BrokerName, token: String, sandbox: Boolean) {
        prefs.edit()
            .putString("${brokerName.key}_token", token)
            .putBoolean("${brokerName.key}_sandbox", sandbox)
            .commit()
    }

    /**
     * Возвращает пару (токен, sandbox) для указанного брокера или null.
     */
    fun loadBrokerCredentials(brokerName: BrokerName): Pair<String, Boolean>? {
        val token = prefs.getString("${brokerName.key}_token", null) ?: return null
        val sandbox = prefs.getBoolean("${brokerName.key}_sandbox", true)
        return Pair(token, sandbox)
    }

    fun clearBrokerCredentials(brokerName: BrokerName) {
        prefs.edit()
            .remove("${brokerName.key}_token")
            .remove("${brokerName.key}_sandbox")
            .apply()
    }

    //Параметр не используется по факту — но менять сигнатуру на «без параметра» пока не будем, вынесу в roadmap.
    fun clearTradingState(brokerName: BrokerName) {
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
    fun saveToken(brokerName: BrokerName, token: String) {
        prefs.edit()
            .putString("${brokerName.key}_token", token)
            .commit()
    }

    fun hasSavedToken(brokerName: BrokerName): Boolean =
        prefs.contains("${brokerName.key}_token")

    fun getToken(brokerName: BrokerName): String? =
        prefs.getString("${brokerName.key}_token", null)

    // ---------- Счёт по умолчанию ----------

    fun saveDefaultAccountId(brokerName: BrokerName, accountId: String) {
        prefs.edit().putString("${brokerName.key}_default_account", accountId).commit()
    }

    fun loadDefaultAccountId(brokerName: BrokerName): String? =
        prefs.getString("${brokerName.key}_default_account", null)

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
