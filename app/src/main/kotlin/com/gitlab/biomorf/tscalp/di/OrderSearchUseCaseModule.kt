package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.domain.api.repository.MarketDataRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SearchCacheRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStatusRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.usecases.RefreshSelectedInstrumentsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SearchInstrumentsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.UpdateTradingStatusesUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OrderSearchUseCaseModule {

    @Provides
    @Singleton
    fun provideSearchInstrumentsUseCase(
        searchCacheRepository: SearchCacheRepository,
        updateTradingStatusesUseCase: UpdateTradingStatusesUseCase,
        logger: Logger,
    ): SearchInstrumentsUseCase =
        SearchInstrumentsUseCase(
            searchCacheRepository = searchCacheRepository,
            updateTradingStatusesUseCase = updateTradingStatusesUseCase,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideUpdateTradingStatusesUseCase(
        marketDataRepository: MarketDataRepository,
        tradingStatusRepository: TradingStatusRepository,
        logger: Logger,
    ): UpdateTradingStatusesUseCase =
        UpdateTradingStatusesUseCase(
            marketDataRepository = marketDataRepository,
            tradingStatusRepository = tradingStatusRepository,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideRefreshSelectedInstrumentsUseCase(): RefreshSelectedInstrumentsUseCase =
        RefreshSelectedInstrumentsUseCase()
}
