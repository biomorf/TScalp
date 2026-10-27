package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney
import com.gitlab.biomorf.tscalp.util.AppLogger
import com.gitlab.biomorf.tscalp.util.runCatchingAppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [AccountsRepository] поверх [BrokerManager].
 *
 * Тела методов перенесены из `InvestRepository` без изменений
 * (Phase 1 step 3 InvestRepository split).
 */
@Singleton
class AccountsRepositoryImpl
    @Inject
    constructor(
        private val brokerManager: BrokerManager,
    ) : AccountsRepository {
        companion object {
            private const val TAG = "AccountsRepository"
        }

        override suspend fun getAccountsResult(
            brokerName: BrokerName,
            sandboxMode: Boolean,
        ): AppResult<List<BrokerAccount>> =
            withContext(Dispatchers.IO) {
                val broker =
                    brokerManager.getBroker(brokerName)
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                        )
                val accountsApi =
                    broker.accounts
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${brokerName.displayName} не поддерживает счета"),
                        )
                runCatchingAppResult(brokerName) { accountsApi.getAccounts(sandboxMode) }
            }

        override suspend fun openSandboxAccountResult(
            brokerName: BrokerName,
        ): AppResult<String> =
            withContext(Dispatchers.IO) {
                val broker =
                    brokerManager.getBroker(brokerName)
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                        )
                val sandboxApi =
                    broker.sandbox
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${brokerName.displayName} не поддерживает песочницу"),
                        )
                runCatchingAppResult(brokerName) { sandboxApi.openSandboxAccount() }
            }

        override suspend fun closeSandboxAccountResult(
            brokerName: BrokerName,
            accountId: String,
        ): AppResult<Unit> =
            withContext(Dispatchers.IO) {
                val broker =
                    brokerManager.getBroker(brokerName)
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                        )
                val sandboxApi =
                    broker.sandbox
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${brokerName.displayName} не поддерживает песочницу"),
                        )
                runCatchingAppResult(brokerName) {
                    sandboxApi.closeSandboxAccount(accountId)
                    Unit
                }
            }

        override suspend fun sandboxPayInResult(
            accountId: String,
            amount: SandboxMoney,
        ): AppResult<Unit> =
            withContext(Dispatchers.IO) {
                AppLogger.d(TAG, "sandboxPayIn: accountId=$accountId, amount=${amount.units} ${amount.currency}")
                val broker = brokerManager.getDefaultBroker()
                val sandboxApi =
                    broker.sandbox
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${BrokerName.TINVEST.displayName} не поддерживает песочницу"),
                        )
                runCatchingAppResult(BrokerName.TINVEST) {
                    sandboxApi.sandboxPayIn(accountId, amount)
                    Unit
                }
            }
    }
