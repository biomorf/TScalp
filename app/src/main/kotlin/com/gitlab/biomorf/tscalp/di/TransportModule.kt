package com.gitlab.biomorf.tscalp.di

import android.content.Context
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestChannelFactory
import com.gitlab.biomorf.tscalp.data.transport.createTInvestWsHttpClient
import com.gitlab.biomorf.tscalp.domain.log.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TransportModule {
    @Provides
    @Singleton
    fun provideTInvestChannelFactory(
        @ApplicationContext context: Context,
        logger: Logger,
    ): TInvestChannelFactory = TInvestChannelFactory(context, logger)

    @Provides
    @Singleton
    fun provideTInvestWsHttpClient(
        @ApplicationContext context: Context,
        logger: Logger,
    ): HttpClient = createTInvestWsHttpClient(context, logger)
}
