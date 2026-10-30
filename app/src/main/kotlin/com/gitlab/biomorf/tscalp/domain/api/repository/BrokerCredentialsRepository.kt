package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.BrokerName

/**
 * Domain-контракт учётных данных брокеров (clean-audit Н68, пакет B).
 *
 * Выделен из `SettingsRepository` (SRP). DataStore-ключи —
 * legacy-формат (`<brokerName.key>_token`, `<brokerName.key>_sandbox`),
 * обратная совместимость сохранена.
 */
interface BrokerCredentialsRepository {
    suspend fun saveBrokerCredentials(
        brokerName: BrokerName,
        token: String,
        sandbox: Boolean,
    )

    suspend fun loadBrokerCredentials(brokerName: BrokerName): Pair<String, Boolean>?

    suspend fun clearBrokerCredentials(brokerName: BrokerName)

    suspend fun saveToken(
        brokerName: BrokerName,
        token: String,
    )

    suspend fun hasSavedToken(brokerName: BrokerName): Boolean

    suspend fun getToken(brokerName: BrokerName): String?
}
