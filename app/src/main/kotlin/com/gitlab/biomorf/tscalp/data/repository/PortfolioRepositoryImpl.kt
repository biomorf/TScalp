package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.data.error.runCatchingAppResult
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.PortfolioRepository
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PortfolioPosition
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.moneyPerPointOfPrice
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
                    portfolioApi
                        .fetchPortfolioPositions(accountId, sandboxMode)
                        .positions
                        .map { it.toLegacyPortfolioPosition() }
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

        /**
         * Временный адаптер [Position] -> [PortfolioPosition] (legacy).
         *
         * `PortfolioPosition` ещё используется `OrdersViewModel`; миграция —
         * задача «OrdersViewModel: миграция на PortfolioSheet» (2d).
         *
         * `quantity` приводится к `Long` (lossy — Finam поддерживает дробное;
         * до миграции Finam не работает). `priceChangePercent` = `null` (нет
         * источника — задача «Position: источники priceChangePercent ...»).
         */
        private fun Position.toLegacyPortfolioPosition(): PortfolioPosition =
            PortfolioPosition(
                tscalpInstrumentId = instrument.tscalpInstrumentId,
                brokerName = brokerName,
                name = instrument.name,
                ticker = instrument.ticker,
                quantity = quantity.toLong(),
                currentPrice = currentPrice,
                averagePrice = averagePrice,
                totalValue = totalValue,
                profit = profit,
                profitPercent = profitPercent,
                moneyPerPointOfPrice = instrument.moneyPerPointOfPrice,
                instrumentType = instrument.instrumentType,
                isin = instrument.isin,
                classCode = instrument.classCode ?: "",
                currency = instrument.currency,
                accountId = accountId,
                lockedQuantity = lockedQuantity,
            )
    }
