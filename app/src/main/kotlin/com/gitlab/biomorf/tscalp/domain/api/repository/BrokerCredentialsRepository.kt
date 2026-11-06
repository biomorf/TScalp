package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.flow.Flow

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

    /**
     * Реактивный аналог [hasSavedToken]: `true`, пока токен
     * брокера есть в DataStore.
     *
     * Нужен panel-VM в settings (M006) для реактивного
     * `connected`-флага: `restore*` в `TScalpApplication`
     * (VM создана раньше, credentials восстановлены позже)
     * императивный `init { hasSavedToken }` не увидит —
     * flow увидит.
     */
    fun hasSavedTokenFlow(brokerName: BrokerName): Flow<Boolean>

    suspend fun getToken(brokerName: BrokerName): String?
}
