package com.gitlab.biomorf.tscalp.transport.ktor

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Создаёт HttpClient с установленными WebSockets и ContentNegotiation.
 *
 * Движок (engine) — платформо-специфичный:
 * - Android: OkHttp (см. androidMain).
 * - iOS: Darwin (добавится в iOS-фазе).
 */
expect fun createTransportHttpClient(): HttpClient
