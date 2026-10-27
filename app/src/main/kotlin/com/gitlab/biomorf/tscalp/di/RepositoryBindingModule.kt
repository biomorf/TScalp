package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.data.orchestrator.DebugConfig
import com.gitlab.biomorf.tscalp.data.repository.AccountsRepositoryImpl
import com.gitlab.biomorf.tscalp.data.repository.InstrumentRepositoryImpl
import com.gitlab.biomorf.tscalp.data.repository.MarketDataRepositoryImpl
import com.gitlab.biomorf.tscalp.data.repository.OrdersRepositoryImpl
import com.gitlab.biomorf.tscalp.data.repository.PortfolioRepositoryImpl
import com.gitlab.biomorf.tscalp.data.repository.SearchCacheImpl
import com.gitlab.biomorf.tscalp.data.repository.SettingsRepositoryImpl
import com.gitlab.biomorf.tscalp.data.repository.TradingStateRepositoryImpl
import com.gitlab.biomorf.tscalp.domain.api.DebugFlagsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.InstrumentRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.MarketDataRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.OrdersRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.PortfolioRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SearchCacheRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SettingsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Биндинги domain-интерфейсов репозиториев на их data-реализации.
 *
 * InvestRepository split (Phase 1–2): все потребители мигрированы на
 * domain-интерфейсы, `InvestRepository` удалён.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingModule {
    @Binds
    @Singleton
    abstract fun bindAccountsRepository(impl: AccountsRepositoryImpl): AccountsRepository

    @Binds
    @Singleton
    abstract fun bindMarketDataRepository(impl: MarketDataRepositoryImpl): MarketDataRepository

    @Binds
    @Singleton
    abstract fun bindOrdersRepository(impl: OrdersRepositoryImpl): OrdersRepository

    @Binds
    @Singleton
    abstract fun bindPortfolioRepository(impl: PortfolioRepositoryImpl): PortfolioRepository

    @Binds
    @Singleton
    abstract fun bindInstrumentRepository(impl: InstrumentRepositoryImpl): InstrumentRepository

    @Binds
    @Singleton
    abstract fun bindSearchCacheRepository(impl: SearchCacheImpl): SearchCacheRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindTradingStateRepository(impl: TradingStateRepositoryImpl): TradingStateRepository

    @Binds
    @Singleton
    internal abstract fun bindDebugFlagsRepository(impl: DebugConfig): DebugFlagsRepository
}
