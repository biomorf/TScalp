package com.gitlab.biomorf.tscalp.data.api

import android.content.Context
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.MetadataUtils
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext

/**
 * Фабрика gRPC-каналов для T-Invest API.
 *
 * Создаёт три канала (общий API, стрим цен, стрим статусов заявок)
 * с настроенным SSLContext (Russian Trusted Root CA через CustomTrustManager)
 * и интерцептором авторизации Bearer.
 */
@Singleton
class TInvestChannelFactory @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val SANDBOX_TARGET = "sandbox-invest-public-api.tbank.ru:443"
        private const val PROD_TARGET = "invest-public-api.tbank.ru:443"
    }

    /**
     * Три канала, необходимых TInvestBrokerAPI.
     */
    data class Channels(
        val api: ManagedChannel,
        val pricesStream: ManagedChannel,
        val ordersState: ManagedChannel
    ) {
        fun shutdownAll() {
            api.shutdownNow()
            pricesStream.shutdownNow()
            ordersState.shutdownNow()
        }
    }

    /**
     * Создаёт набор каналов для указанного режима.
     */
    fun create(token: String, sandbox: Boolean): Channels {
        val target = if (sandbox) SANDBOX_TARGET else PROD_TARGET
        return Channels(
            api = buildChannel(target, token),
            pricesStream = buildChannel(target, token),
            ordersState = buildChannel(target, token)
        )
    }

    private fun buildChannel(target: String, token: String): ManagedChannel {
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(CustomTrustManager(context)), null)
        }

        val authInterceptor = MetadataUtils.newAttachHeadersInterceptor(
            Metadata().apply {
                put(
                    Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER),
                    "Bearer ${token.trim()}"
                )
            }
        )

        return OkHttpChannelBuilder.forTarget(target)
            .sslSocketFactory(sslContext.socketFactory)
            .useTransportSecurity()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .intercept(authInterceptor)
            .build()
    }
}
