package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gitlab.biomorf.tscalp.domain.models.BrokerName

/**
 * Общие DataStore-ключи для 4 репозиториев настроек (clean-audit Н68, пакет B).
 *
 * Legacy-формат (`<brokerName.key>_token` и т.п.) сохранён — обратная
 * совместимость с существующими данными.
 */
internal object SettingsKeys {
    fun tokenKey(brokerName: BrokerName) = stringPreferencesKey("${brokerName.key}_token")

    fun sandboxKey(brokerName: BrokerName) = booleanPreferencesKey("${brokerName.key}_sandbox")

    fun defaultAccountKey(brokerName: BrokerName) = stringPreferencesKey("${brokerName.key}_default_account")

    val CONFIRM_ORDERS_ENABLED = booleanPreferencesKey("confirm_orders_enabled")
}
