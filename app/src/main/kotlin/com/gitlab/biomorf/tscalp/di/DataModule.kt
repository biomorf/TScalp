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
import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import okhttp3.OkHttpClient
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

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
        okHttpClient: OkHttpClient,
    ): TInvestBrokerAPI = TInvestBrokerAPI(channelFactory, okHttpClient)

    @Provides
    @Singleton
    fun provideBrokerManager(
        tinvest: TInvestBrokerAPI,
        finam: FinamBrokerApi,
        bcs: BcsBrokerApi,
    ): BrokerManager {
        val brokers: Map<BrokerName, BrokerApi> =
            mapOf(
                BrokerName.TINVEST to tinvest,
                BrokerName.BCS to bcs,
                BrokerName.FINAM to finam,
            )
        return BrokerManager(brokers)
    }

    @Provides
    @Singleton
    fun provideFinamBrokerApi(): FinamBrokerApi = FinamBrokerApi()

    @Provides
    @Singleton
    fun provideBcsBrokerApi(): BcsBrokerApi = BcsBrokerApi()

    @Provides
    @Singleton
    fun provideTInvestTransportHttpClient(
        @ApplicationContext context: Context,
    ): HttpClient {
        val trustManager = CustomTrustManager(context)
        val sslContext =
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), null)
            }
        return HttpClient(OkHttp) {
            install(WebSockets)
            engine {
                config {
                    followRedirects(false)
                    followSslRedirects(false)
                    retryOnConnectionFailure(true)
                    sslSocketFactory(sslContext.socketFactory, trustManager)
                }
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
    ): OkHttpClient {
        val trustManager = CustomTrustManager(context)
        val sslContext =
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), null)
            }
        return OkHttpClient
            .Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .addInterceptor(
                okhttp3.logging.HttpLoggingInterceptor { message ->
                    com.gitlab.biomorf.tscalp.util.AppLogger.d("OkHttpWS", message)
                }.apply { level = okhttp3.logging.HttpLoggingInterceptor.Level.HEADERS },
            )
            .build()
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tinvest_prefs",
)
