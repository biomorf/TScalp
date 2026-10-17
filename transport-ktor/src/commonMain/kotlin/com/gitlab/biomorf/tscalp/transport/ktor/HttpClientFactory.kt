package com.gitlab.biomorf.tscalp.transport.ktor

import io.ktor.client.HttpClient

/**
 * Создаёт HttpClient с установленным WebSockets plugin.
 *
 * Движок (engine) — платформо-специфичный:
 * - Android: OkHttp (см. androidMain).
 * - iOS: Darwin (добавится в iOS-фазе).
 */
expect fun createTransportHttpClient(): HttpClient
