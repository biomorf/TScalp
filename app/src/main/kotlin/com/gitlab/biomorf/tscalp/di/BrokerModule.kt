package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.data.api.bcs.BcsBrokerApi
import com.gitlab.biomorf.tscalp.data.api.finam.FinamBrokerApi
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestChannelFactory
import com.gitlab.biomorf.tscalp.domain.api.DebugFlagsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BrokerModule {
    @Provides
    @Singleton
    fun provideTInvestBrokerAPI(
        channelFactory: TInvestChannelFactory,
        httpClient: HttpClient,
        debugFlagsRepository: DebugFlagsRepository,
        logger: Logger,
    ): TInvestBrokerAPI = TInvestBrokerAPI(channelFactory, httpClient, debugFlagsRepository, logger)

    @Provides
    @Singleton
    fun provideFinamBrokerApi(logger: Logger): FinamBrokerApi = FinamBrokerApi(logger)

    @Provides
    @Singleton
    fun provideBcsBrokerApi(logger: Logger): BcsBrokerApi = BcsBrokerApi(logger)
}
