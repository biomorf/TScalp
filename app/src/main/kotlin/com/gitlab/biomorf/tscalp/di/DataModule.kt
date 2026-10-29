package com.gitlab.biomorf.tscalp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.gitlab.biomorf.tscalp.data.api.BcsBrokerApi
import com.gitlab.biomorf.tscalp.data.api.CustomTrustManager
import com.gitlab.biomorf.tscalp.data.api.FinamBrokerApi
import com.gitlab.biomorf.tscalp.data.api.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.data.api.TInvestChannelFactory
import com.gitlab.biomorf.tscalp.data.registry.BrokerRegistryImpl
import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.DebugFlagsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.InstallIn
import dagger.Module
import dagger.Provides
import io.ktor.client.engine.cio.CIO
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    fun provideTInvestChannelFactory(
        @ApplicationContext context: Context,
    ): TInvestChannelFactory = TInvestChannelFactory(context)

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
    fun provideBrokerRegistry(
        tinvest: TInvestBrokerAPI,
        finam: FinamBrokerApi,
        bcs: BcsBrokerApi,
    ): BrokerRegistry {
        val brokers: Map<BrokerName, BrokerApi> =
            mapOf(
                BrokerName.TINVEST to tinvest,
                BrokerName.BCS to bcs,
                BrokerName.FINAM to finam,
            )
        return BrokerRegistryImpl(brokers)
    }

    @Provides
    @Singleton
    fun provideFinamBrokerApi(logger: Logger): FinamBrokerApi = FinamBrokerApi(logger)

    @Provides
    @Singleton
    fun provideBcsBrokerApi(logger: Logger): BcsBrokerApi = BcsBrokerApi(logger)

    @Provides
    @Singleton
    fun provideTInvestWsHttpClient(
        @ApplicationContext context: Context,
        logger: Logger,
    ): HttpClient =
        HttpClient(CIO) {
            install(WebSockets)
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                https {
                    trustManager = CustomTrustManager(context, logger)
                }
            }
        }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tinvest_prefs",
)
