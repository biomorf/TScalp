package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.MarketDataRepository
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import com.gitlab.biomorf.tscalp.util.runCatchingAppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [MarketDataRepository] поверх [BrokerRegistry].
 *
 * Тела методов перенесены из `InvestRepository` без изменений
 * (Phase 1 step 3 InvestRepository split).
 */
@Singleton
class MarketDataRepositoryImpl
    @Inject
    constructor(
        private val brokerManager: BrokerRegistry,
    ) : MarketDataRepository {
        override suspend fun getLastPricesResult(
            ids: List<String>,
        ): AppResult<Map<String, Double?>> =
            withContext(Dispatchers.IO) {
                val broker = brokerManager.getDefaultBroker()
                runCatchingAppResult(BrokerName.TINVEST) { broker.marketData?.getLastPricesByTscalpInstrumentId(ids) }
            }

        override suspend fun getTradingStatusesResult(
            brokerName: BrokerName,
            ids: List<String>,
        ): AppResult<Map<String, TradingAvailability>> =
            withContext(Dispatchers.IO) {
                if (ids.isEmpty()) return@withContext AppResult.Success(emptyMap())
                val broker =
                    brokerManager.getBroker(brokerName)
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                        )
                val userInstrumentsApi =
                    broker.userInstruments
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${brokerName.displayName} не поддерживает статусы торговли"),
                        )
                runCatchingAppResult(brokerName) { userInstrumentsApi.getTradingStatuses(ids) }
            }
    }
