package com.gitlab.biomorf.tscalp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.gitlab.biomorf.tscalp.data.api.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.data.api.FinamBrokerApi
import com.gitlab.biomorf.tscalp.data.api.BcsBrokerApi
import com.gitlab.biomorf.tscalp.data.api.TInvestChannelFactory
import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.models.BrokerName

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideTInvestChannelFactory(
        @ApplicationContext context: Context
    ): TInvestChannelFactory = TInvestChannelFactory(context)

    @Provides
    @Singleton
    fun provideTInvestBrokerAPI(
        channelFactory: TInvestChannelFactory
    ): TInvestBrokerAPI = TInvestBrokerAPI(channelFactory)

    @Provides
    @Singleton
    fun provideBrokerManager(
        tinvest: TInvestBrokerAPI,
        finam: FinamBrokerApi,
        bcs: BcsBrokerApi
    ): BrokerManager {
        val brokers: Map<BrokerName, BrokerApi> = mapOf(
            BrokerName.TINVEST to tinvest,
            BrokerName.BCS to bcs,
            BrokerName.FINAM to finam
        )
        return BrokerManager(brokers)
    }

    @Provides
    @Singleton
    fun provideFinamBrokerApi(): FinamBrokerApi = FinamBrokerApi()

    @Provides
    @Singleton
    fun provideBcsBrokerApi(): BcsBrokerApi = BcsBrokerApi()
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tinvest_prefs"
)