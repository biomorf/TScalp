package com.gitlab.biomorf.tscalp.data.transport

import android.content.Context
import com.gitlab.biomorf.tscalp.data.api.common.CustomTrustManager
import com.gitlab.biomorf.tscalp.domain.log.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createTInvestWsHttpClient(
    context: Context,
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
