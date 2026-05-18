package com.example.tscalp.di

import android.content.Context
import android.content.SharedPreferences
import com.example.tscalp.data.api.TInvestInvestService
import com.example.tscalp.data.api.FinamBrokerApi
import com.example.tscalp.data.api.BcsBrokerApi
import com.example.tscalp.domain.api.BrokerApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
    fun provideTInvestService(
        @ApplicationContext context: Context,
        sharedPreferences: SharedPreferences
    ): TInvestInvestService {
        val service = TInvestInvestService()
        val token = sharedPreferences.getString("TInvest_token", null)
        if (token != null) {
            service.initializeFromSettings()
        }
        return service
    }

    @Provides
    @Singleton
    fun provideBrokerManager(
        tinvest: TInvestInvestService,
        finam: FinamBrokerApi,
        bcs: BcsBrokerApi
    ): BrokerManager {
        val brokers: Map<String, BrokerApi> = mapOf(
            "TInvest" to tinvest,
            "finam" to finam,
            "bcs" to bcs
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