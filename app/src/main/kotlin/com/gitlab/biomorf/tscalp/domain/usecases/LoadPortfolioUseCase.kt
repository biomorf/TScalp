package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.PortfolioRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AccountBalance
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PortfolioSheet
import kotlinx.coroutines.CancellationException

/**
 * Загрузка портфеля: счета + seed `PortfolioSheet` (позиции + балансы).
 *
 * Вынесено из `PortfolioViewModel.loadPortfolio` / `seedSheet`
 * (clean-audit M013, M2): getAccounts + fetch positions + getBalance
 * по каждому счёту — domain-orchestration, не presentation.
 *
 * VM-часть (по Q2 + F3) остаётся в VM: `startPositionUpdates(keys,
 * sandbox, seed = sheet)` и `syncPriceInterest()` — lifecycle /
 * aggregation, не orchestration repository.
 *
 * Pre-existing: `AccountBalance.currency = "rub"` (lowercase) —
 * не в scope M013; нормализация ISO 4217 — ROADMAP →
 * `### Currency format: normalize ISO 4217 case`.
 */
class LoadPortfolioUseCase
    constructor(
        private val accountsRepository: AccountsRepository,
        private val portfolioRepository: PortfolioRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "LoadPortfolioUseCase"
        }

        /**
         * Загружает счета [brokerName] и seed `PortfolioSheet` по ним.
         *
         * @param brokerName — брокер (все call sites передают
         *   `BrokerName.TINVEST`; параметр — без хардкода в domain,
         *   мультиброкерность — при `### Реализация BCS/Finam Broker API`).
         * @param sandboxMode — режим песочницы.
         * @return `AppResult.Success(PortfolioLoadResult)` или
         *   `AppResult.Failure` (ошибка getAccounts / общая ошибка
         *   load; ошибки по отдельным счетам — skip, лог).
         */
        suspend fun execute(
            brokerName: BrokerName,
            sandboxMode: Boolean,
        ): AppResult<PortfolioLoadResult> {
            logger.d(TAG, "execute: brokerName=$brokerName, sandboxMode=$sandboxMode")
            return try {
                val accounts =
                    when (val r = accountsRepository.getAccountsResult(brokerName, sandboxMode)) {
                        is AppResult.Success -> r.data
                        is AppResult.Failure -> {
                            logger.e(TAG, "getAccounts failed: ${r.error.message}", r.error.cause)
                            return AppResult.Failure(r.error)
                        }
                    }

                if (accounts.isEmpty()) {
                    logger.d(TAG, "execute: no accounts")
                    return AppResult.Success(
                        PortfolioLoadResult(
                            accounts = emptyList(),
                            sheet = PortfolioSheet(emptyMap()),
                        ),
                    )
                }

                val sheet = seedSheet(accounts, brokerName, sandboxMode)
                AppResult.Success(
                    PortfolioLoadResult(
                        accounts = accounts,
                        sheet = sheet,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "load portfolio exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка загрузки портфеля: ${e.message}", e))
            }
        }

        private suspend fun seedSheet(
            accounts: List<BrokerAccount>,
            brokerName: BrokerName,
            sandbox: Boolean,
        ): PortfolioSheet {
            val seeded = mutableMapOf<AccountKey, AccountPortfolio>()
            for (account in accounts) {
                when (val r = portfolioRepository.fetchPositionsResult(brokerName, account.id, sandbox)) {
                    is AppResult.Success -> {
                        val key = AccountKey(brokerName, account.id)
                        val balances =
                            when (val b = portfolioRepository.getBalanceResult(account.id)) {
                                is AppResult.Success ->
                                    listOf(
                                        AccountBalance(
                                            currency = "rub",
                                            money = b.data,
                                            blocked = 0.0,
                                        ),
                                    )
                                is AppResult.Failure -> {
                                    logger.w(
                                        TAG,
                                        "seedSheet[${account.id}] balance failed: ${b.error.message}",
                                    )
                                    emptyList()
                                }
                            }
                        seeded[key] =
                            AccountPortfolio(
                                accountId = account.id,
                                brokerName = brokerName,
                                positions = r.data,
                                balances = balances,
                            )
                    }
                    is AppResult.Failure ->
                        logger.e(TAG, "seedSheet[${account.id}] failed: ${r.error.message}", r.error.cause)
                }
            }
            return PortfolioSheet(seeded)
        }
    }

/**
 * Результат [LoadPortfolioUseCase]: счета + seed `PortfolioSheet`
 * по ним. VM использует `accounts` для `startPositionUpdates(keys,
 * sandbox, seed = sheet)` (Q2 + F3 — lifecycle остаётся в VM).
 */
data class PortfolioLoadResult(
    val accounts: List<BrokerAccount>,
    val sheet: PortfolioSheet,
)
