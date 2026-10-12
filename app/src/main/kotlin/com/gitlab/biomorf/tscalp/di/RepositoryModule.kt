package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.data.repository.InstrumentRepository
import com.gitlab.biomorf.tscalp.data.repository.InvestRepository
import com.gitlab.biomorf.tscalp.data.repository.SearchCache
import com.gitlab.biomorf.tscalp.domain.usecases.CalculateTradeDetailsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PrepareOrderRequestUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideInvestRepository(brokerManager: BrokerManager): InvestRepository = InvestRepository(brokerManager)

    @Provides
    @Singleton
    fun provideInstrumentRepository(brokerManager: BrokerManager): InstrumentRepository = InstrumentRepository(brokerManager)

    @Provides
    @Singleton
    fun provideSearchCache(brokerManager: BrokerManager): SearchCache = SearchCache(brokerManager)
}

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    @Singleton
    fun provideCalculateTradeDetailsUseCase(): CalculateTradeDetailsUseCase = CalculateTradeDetailsUseCase()

    @Provides
    @Singleton
    fun providePrepareOrderRequestUseCase(): PrepareOrderRequestUseCase = PrepareOrderRequestUseCase()
}
