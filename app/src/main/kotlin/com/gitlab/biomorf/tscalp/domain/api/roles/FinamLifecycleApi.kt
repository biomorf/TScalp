package com.gitlab.biomorf.tscalp.domain.api.roles

/**
 * Lifecycle Finam: login по [BrokerCredentials.Finam], logout.
 *
 * Доступ через `BrokerApi.lifecycleFinam` (`null` = не поддерживает).
 */
interface FinamLifecycleApi {
    /** @return `true` — успех, `false` — ошибка. */
    suspend fun initialize(credentials: BrokerCredentials.Finam): Boolean

    /** Деинициализация. */
    fun deinitialize()
}
