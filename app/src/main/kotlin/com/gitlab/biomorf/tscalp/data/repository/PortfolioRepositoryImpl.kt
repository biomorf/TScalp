package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.PortfolioRepository
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PortfolioPosition
import com.gitlab.biomorf.tscalp.domain.models.toPortfolioPosition
import com.gitlab.biomorf.tscalp.util.runCatchingAppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [PortfolioRepository] поверх [BrokerRegistry].
 *
 * Тела методов перенесены из `InvestRepository` без изменений
 * (Phase 1 step 3 InvestRepository split).
 */
@Singleton
class PortfolioRepositoryImpl
    @Inject
    constructor(
        private val brokerManager: BrokerRegistry,
    ) : PortfolioRepository {
        override suspend fun fetchPositionsResult(
            brokerName: BrokerName,
            accountId: String,
            sandboxMode: Boolean,
        ): AppResult<List<PortfolioPosition>> =
            withContext(Dispatchers.IO) {
                val broker =
                    brokerManager.getBroker(brokerName)
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                        )
                val portfolioApi =
                    broker.portfolio
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${brokerName.displayName} не поддерживает портфель"),
                        )
                runCatchingAppResult(brokerName) {
                    portfolioApi.fetchPortfolioPositions(accountId, sandboxMode).map { it.toPortfolioPosition() }
                }
            }

        override suspend fun getBalanceResult(
            accountId: String,
        ): AppResult<Double> =
            withContext(Dispatchers.IO) {
                val broker = brokerManager.getDefaultBroker()
                val balanceApi =
                    broker.balance
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${BrokerName.TINVEST.displayName} не поддерживает баланс"),
                        )
                runCatchingAppResult(BrokerName.TINVEST) { balanceApi.getBalance(accountId) }
            }
    }
