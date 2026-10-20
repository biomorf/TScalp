package com.gitlab.biomorf.tscalp.transport.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets

actual fun createTransportHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(WebSockets)
}
