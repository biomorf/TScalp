package com.example.tscalp.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.example.tscalp.util.AppLogger
import com.example.tscalp.data.api.TInvestBrokerAPI
import com.example.tscalp.data.api.FinamBrokerApi
import com.example.tscalp.data.api.BcsBrokerApi
import com.example.tscalp.data.api.TInvestChannelFactory
import com.example.tscalp.domain.api.BrokerApi
import com.example.tscalp.domain.models.BrokerName

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("tinvest_prefs", Context.MODE_PRIVATE)
    }

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
        channelFactory: TInvestChannelFactory,
        sharedPreferences: SharedPreferences
    ): TInvestBrokerAPI {
        val service = TInvestBrokerAPI(channelFactory)
        val token = sharedPreferences.getString("TInvest_token", null)
        AppLogger.d("DataModule", "provideTInvestBrokerAPI: token=" +
                if (token.isNullOrBlank()) "null/blank" else "present, len=${token.length}")
        if (token != null) {
            val sandbox = sharedPreferences.getBoolean("TInvest_sandbox", true)
            service.initialize(token, sandbox)
            AppLogger.d("DataModule", "TInvestBrokerAPI initialized: isInitialized=${service.isInitialized}")
        }
        return service
    }

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