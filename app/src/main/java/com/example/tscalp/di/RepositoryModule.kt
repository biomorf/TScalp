package com.example.tscalp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.content.Context
import android.content.SharedPreferences

import com.example.tscalp.data.repository.InstrumentRepository
import com.example.tscalp.data.repository.InvestRepository
import com.example.tscalp.data.repository.SearchCache
import com.example.tscalp.domain.usecases.PrepareOrderRequestUseCase
import com.example.tscalp.domain.usecases.CalculateTradeDetailsUseCase


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