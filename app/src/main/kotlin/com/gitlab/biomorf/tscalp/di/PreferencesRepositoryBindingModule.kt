package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.data.repository.AccountPreferencesRepositoryImpl
import com.gitlab.biomorf.tscalp.data.repository.BrokerCredentialsRepositoryImpl
import com.gitlab.biomorf.tscalp.data.repository.SandboxPreferencesRepositoryImpl
import com.gitlab.biomorf.tscalp.data.repository.TradingPreferencesRepositoryImpl
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Биндинги preferences-репозиториев (SettingsRepository split,
 * clean-audit Н68, пакет B).
 *
 * Выделен из [RepositoryBindingModule] после split `SettingsRepository`
 * на 4 интерфейса — иначе `RepositoryBindingModule` превышает порог
 * detekt `TooManyFunctions` (13 > 11).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesRepositoryBindingModule {
    @Binds
    @Singleton
    abstract fun bindBrokerCredentialsRepository(
        impl: BrokerCredentialsRepositoryImpl,
    ): BrokerCredentialsRepository

    @Binds
    @Singleton
    abstract fun bindAccountPreferencesRepository(
        impl: AccountPreferencesRepositoryImpl,
    ): AccountPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindSandboxPreferencesRepository(
        impl: SandboxPreferencesRepositoryImpl,
    ): SandboxPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindTradingPreferencesRepository(
        impl: TradingPreferencesRepositoryImpl,
    ): TradingPreferencesRepository
}
