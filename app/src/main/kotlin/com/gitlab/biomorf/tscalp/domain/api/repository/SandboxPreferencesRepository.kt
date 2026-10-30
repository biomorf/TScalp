package com.gitlab.biomorf.tscalp.domain.api.repository

/**
 * Domain-контракт режима песочницы T-Invest (clean-audit Н68, пакет B).
 *
 * DataStore-ключ — `TInvest_sandbox`. Тот же ключ пишет
 * [BrokerCredentialsRepository.saveBrokerCredentials] при
 * `sandbox = true` (shared state, DataStore `@Singleton`).
 */
interface SandboxPreferencesRepository {
    suspend fun isSandboxMode(): Boolean

    suspend fun setSandboxMode(enabled: Boolean)
}
