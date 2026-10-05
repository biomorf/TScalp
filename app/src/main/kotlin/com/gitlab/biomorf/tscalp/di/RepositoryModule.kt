package com.gitlab.biomorf.tscalp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.gitlab.biomorf.tscalp.data.repository.InstrumentRepository
import com.gitlab.biomorf.tscalp.data.repository.InvestRepository
import com.gitlab.biomorf.tscalp.data.repository.SearchCache
import com.gitlab.biomorf.tscalp.domain.usecases.PrepareOrderRequestUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.CalculateTradeDetailsUseCase


@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {


    @Provides
    @Singleton
    fun provideInvestRepository(brokerManager: BrokerManager): InvestRepository {
        return InvestRepository(brokerManager)
    }

    @Provides
    @Singleton
    fun provideInstrumentRepository(brokerManager: BrokerManager): InstrumentRepository {
        return InstrumentRepository(brokerManager)
    }

    @Provides
    @Singleton
    fun provideSearchCache(brokerManager: BrokerManager): SearchCache {
        return SearchCache(brokerManager)
    }
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