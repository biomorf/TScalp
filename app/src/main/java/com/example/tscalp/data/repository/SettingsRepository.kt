package com.example.tscalp.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey

import com.example.tscalp.domain.models.BrokerName

/**
 * Единый репозиторий пользовательских настроек на DataStore.
 *
 * Ключи сохранены в legacy-формате ("TInvest_token", "TInvest_sandbox" и т.д.),
 * чтобы при необходимости можно было добавить SharedPreferencesMigration
 * без конфликтов имён.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val CONFIRM_ORDERS_ENABLED = booleanPreferencesKey("confirm_orders_enabled")
    }

    // ---------- Учётные данные брокеров ----------

    suspend fun saveBrokerCredentials(brokerName: BrokerName, token: String, sandbox: Boolean) {
        dataStore.edit { prefs ->
            prefs[tokenKey(brokerName)] = token
            prefs[sandboxKey(brokerName)] = sandbox
        }
    }

    suspend fun loadBrokerCredentials(brokerName: BrokerName): Pair<String, Boolean>? {
        val snapshot = dataStore.data.first()
        val token = snapshot[tokenKey(brokerName)] ?: return null
        val sandbox = snapshot[sandboxKey(brokerName)] ?: true
        return Pair(token, sandbox)
    }

    suspend fun clearBrokerCredentials(brokerName: BrokerName) {
        dataStore.edit { prefs ->
            prefs.remove(tokenKey(brokerName))
            prefs.remove(sandboxKey(brokerName))
        }
    }

    suspend fun saveToken(brokerName: BrokerName, token: String) {
        dataStore.edit { prefs ->
            prefs[tokenKey(brokerName)] = token
        }
    }

    suspend fun hasSavedToken(brokerName: BrokerName): Boolean =
        dataStore.data.first()[tokenKey(brokerName)] != null

    suspend fun getToken(brokerName: BrokerName): String? =
        dataStore.data.first()[tokenKey(brokerName)]

    // ---------- Счёт по умолчанию ----------

    suspend fun saveDefaultAccountId(brokerName: BrokerName, accountId: String) {
        dataStore.edit { prefs ->
            prefs[defaultAccountKey(brokerName)] = accountId
        }
    }

    suspend fun loadDefaultAccountId(brokerName: BrokerName): String? =
        dataStore.data.first()[defaultAccountKey(brokerName)]

    // ---------- Режим песочницы (для TInvest) ----------

    suspend fun isSandboxMode(): Boolean =
        dataStore.data.first()[sandboxKey(BrokerName.TINVEST)] ?: true

    suspend fun setSandboxMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[sandboxKey(BrokerName.TINVEST)] = enabled
        }
    }

    // ---------- Подтверждение заявок ----------

    suspend fun isConfirmOrdersEnabled(): Boolean =
        dataStore.data.first()[CONFIRM_ORDERS_ENABLED] ?: true

    suspend fun setConfirmOrdersEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[CONFIRM_ORDERS_ENABLED] = enabled
        }
    }

    /**
     * Поток значения «подтверждение заявок включено».
     * Эмитит при каждом изменении в DataStore. ViewModel'и подписываются,
     * чтобы держать кэшированное состояние актуальным.
     */
    val confirmOrdersEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[CONFIRM_ORDERS_ENABLED] ?: true
    }

    // ---------- Ключи ----------

    private fun tokenKey(brokerName: BrokerName) =
        stringPreferencesKey("${brokerName.key}_token")

    private fun sandboxKey(brokerName: BrokerName) =
        booleanPreferencesKey("${brokerName.key}_sandbox")

    private fun defaultAccountKey(brokerName: BrokerName) =
        stringPreferencesKey("${brokerName.key}_default_account")
}