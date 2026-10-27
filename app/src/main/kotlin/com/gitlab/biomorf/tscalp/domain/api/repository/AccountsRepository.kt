package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney

/**
 * Domain-контракт счетов и sandbox-операций.
 *
 * Реализация — `data/repository/AccountsRepositoryImpl.kt` (Phase 1 step 3).
 * Перенесён из `InvestRepository` без изменения поведения (Phase 1).
 *
 * @see com.gitlab.biomorf.tscalp.domain.api.roles.AccountsApi
 * @see com.gitlab.biomorf.tscalp.domain.api.roles.SandboxApi
 */
interface AccountsRepository {
    /** Счета указанного брокера. */
    suspend fun getAccountsResult(
        brokerName: BrokerName,
        sandboxMode: Boolean,
    ): AppResult<List<BrokerAccount>>

    /** Открыть новый счёт в песочнице. Возвращает accountId. */
    suspend fun openSandboxAccountResult(
        brokerName: BrokerName,
    ): AppResult<String>

    /** Закрыть счёт в песочнице. */
    suspend fun closeSandboxAccountResult(
        brokerName: BrokerName,
        accountId: String,
    ): AppResult<Unit>

    /** Пополнить счёт в песочнице. */
    suspend fun sandboxPayInResult(
        accountId: String,
        amount: SandboxMoney,
    ): AppResult<Unit>
}
