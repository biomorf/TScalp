package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.domain.usecases.CalculateTradeDetailsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PrepareOrderRequestUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
