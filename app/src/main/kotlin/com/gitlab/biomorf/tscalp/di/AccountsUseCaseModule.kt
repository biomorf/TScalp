package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.usecases.CloseSandboxAccountUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.LoadBrokerAccountsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.LoadCardAccountsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.LoadOrdersAccountsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.OpenSandboxAccountUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AccountsUseCaseModule {
    @Provides
    @Singleton
    fun provideLoadBrokerAccountsUseCase(
        accountsRepository: AccountsRepository,
        accountPreferencesRepository: AccountPreferencesRepository,
        logger: Logger,
    ): LoadBrokerAccountsUseCase =
        LoadBrokerAccountsUseCase(
            accountsRepository = accountsRepository,
            accountPreferencesRepository = accountPreferencesRepository,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideLoadCardAccountsUseCase(
        accountsRepository: AccountsRepository,
        logger: Logger,
    ): LoadCardAccountsUseCase =
        LoadCardAccountsUseCase(
            accountsRepository = accountsRepository,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideLoadOrdersAccountsUseCase(
        accountsRepository: AccountsRepository,
        accountPreferencesRepository: AccountPreferencesRepository,
        logger: Logger,
    ): LoadOrdersAccountsUseCase =
        LoadOrdersAccountsUseCase(
            accountsRepository = accountsRepository,
            accountPreferencesRepository = accountPreferencesRepository,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideOpenSandboxAccountUseCase(
        accountsRepository: AccountsRepository,
        accountPreferencesRepository: AccountPreferencesRepository,
        logger: Logger,
    ): OpenSandboxAccountUseCase =
        OpenSandboxAccountUseCase(
            accountsRepository = accountsRepository,
            accountPreferencesRepository = accountPreferencesRepository,
            logger = logger,
        )

    @Provides
    @Singleton
    fun provideCloseSandboxAccountUseCase(
        accountsRepository: AccountsRepository,
        accountPreferencesRepository: AccountPreferencesRepository,
        logger: Logger,
    ): CloseSandboxAccountUseCase =
        CloseSandboxAccountUseCase(
            accountsRepository = accountsRepository,
            accountPreferencesRepository = accountPreferencesRepository,
            logger = logger,
        )
}
