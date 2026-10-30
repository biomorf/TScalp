package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.BrokerName

/**
 * Domain-контракт счёта по умолчанию (clean-audit Н68, пакет B).
 *
 * DataStore-ключ — `<brokerName.key>_default_account` (legacy-формат).
 */
interface AccountPreferencesRepository {
    suspend fun saveDefaultAccountId(
        brokerName: BrokerName,
        accountId: String,
    )

    suspend fun loadDefaultAccountId(brokerName: BrokerName): String?
}
