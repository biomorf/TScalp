package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.PortfolioRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.usecases.LoadBrokerAccountsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.LoadPortfolioUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PayInSandboxUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SetSandboxModeUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PortfolioUseCaseModule {
    @Provides
    @Singleton
    fun provideLoadPortfolioUseCase(
        accountsRepository: AccountsRepository,
        portfolioRepository: PortfolioRepository,
        logger: Logger,
    ): LoadPortfolioUseCase =
        LoadPortfolioUseCase(
            accountsRepository = accountsRepository,
            portfolioRepository = portfolioRepository,
            logger = logger,
        )

    @Provides
    @Singleton
    fun providePayInSandboxUseCase(
        accountsRepository: AccountsRepository,
        logger: Logger,
    ): PayInSandboxUseCase =
        PayInSandboxUseCase(
            accountsRepository = accountsRepository,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideSetSandboxModeUseCase(
        sandboxPreferencesRepository: SandboxPreferencesRepository,
        loadBrokerAccountsUseCase: LoadBrokerAccountsUseCase,
        logger: Logger,
    ): SetSandboxModeUseCase =
        SetSandboxModeUseCase(
            sandboxPreferencesRepository = sandboxPreferencesRepository,
            loadBrokerAccountsUseCase = loadBrokerAccountsUseCase,
            logger = logger,
        )
}
