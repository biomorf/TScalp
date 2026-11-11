package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.data.api.bcs.BcsBrokerApi
import com.gitlab.biomorf.tscalp.data.api.finam.FinamBrokerApi
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.data.registry.BrokerRegistryImpl
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RegistryModule {
    @Provides
    @Singleton
    fun provideBrokerRegistry(
        tinvest: TInvestBrokerAPI,
        finam: FinamBrokerApi,
        bcs: BcsBrokerApi,
    ): BrokerRegistry = BrokerRegistryImpl(setOf(tinvest, bcs, finam))
}
