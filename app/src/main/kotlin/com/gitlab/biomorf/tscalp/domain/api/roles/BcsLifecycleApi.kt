package com.gitlab.biomorf.tscalp.domain.api.roles

/**
 * Lifecycle BCS: login по [BrokerCredentials.Bcs], logout.
 *
 * `clientId` (`trade-api-read` / `trade-api-write`) — derived
 * внутри `BcsBrokerApi` из `isWriteMode`, не снаружи.
 *
 * Доступ через `BrokerApi.lifecycleBcs` (`null` = не поддерживает).
 */
interface BcsLifecycleApi {
    /** @return `true` — успех, `false` — ошибка. */
    suspend fun initialize(credentials: BrokerCredentials.Bcs): Boolean

    /** Деинициализация. */
    fun deinitialize()
}
