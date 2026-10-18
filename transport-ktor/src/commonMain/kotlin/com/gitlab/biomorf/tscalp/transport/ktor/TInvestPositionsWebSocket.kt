package com.gitlab.biomorf.tscalp.transport.ktor

import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsStreamResponseDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsStreamSubscribeRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * WebSocket-клиент для T-Invest PositionsStream.
 *
 * URL: wss://invest-public-api.tbank.ru/ws (без завершающего слэша —
 * только в этой форме сервер отвечает 101).
 * Заголовки: Authorization: Bearer <token>, Web-Socket-Protocol: json.
 * Формат подписки: плоский JSON ({"accounts":[...],"withInitialPositions":true}).
 *
 * Эмитит сырые DTO (PositionsStreamResponseDto).
 * Конвертация в доменные модели — в :app (оркестратор).
 *
 * Переподключение — экспоненциальный backoff [1s, 5s, 15s, 30s, 60s], cap 60s.
 *
 * НЕ использует Hilt. НЕ использует Android API. Готов к KMP.
 *
 * Engine: Ktor CIO (не OkHttp). OkHttp в handshake всегда ставит
 * Sec-WebSocket-Extensions: permessage-deflate, который ingress
 * T-Invest не принимает — handshake висит 10 секунд и падает по таймауту.
 * CIO не ставит этот заголовок по умолчанию, как websocat
 * (compress_deflate: false).
 */
class TInvestPositionsWebSocket(
    private val client: HttpClient,
    private val url: String,
    private val token: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val logger: (String) -> Unit = { },
) {
    companion object {
        private const val TAG = "[transport-ktor/positions-ws]"
        private val RETRY_DELAYS_MS = longArrayOf(1_000, 5_000, 15_000, 30_000, 60_000)
    }

    fun subscribePositions(accountId: String): Flow<PositionsStreamResponseDto> = flow {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            try {
                logger("$TAG connecting (attempt=${attempt + 1})")
                client.webSocket(
                    urlString = url,
                    request = {
                        headers.append("Authorization", "Bearer $token")
                        headers.append("Web-Socket-Protocol", "json")
                    },
                ) {
                    logger("$TAG connected")
                    val subscription = PositionsStreamSubscribeRequestDto(
                        accounts = listOf(accountId),
                        withInitialPositions = true,
                    )
                    val envelope = buildSubscriptionEnvelope(subscription)
                    send(Frame.Text(envelope))
                    logger("$TAG subscription sent (${envelope.length} bytes)")

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        logger("$TAG frame received (len=${text.length})")
                        try {
                            val response = json.decodeFromString(PositionsStreamResponseDto.serializer(), text)
                            emit(response)
                        } catch (e: Exception) {
                            logger("$TAG parse error: ${e.message}")
                        }
                    }
                    logger("$TAG stream closed by server, reconnecting")
                    attempt = 0
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                val idx = (attempt - 1).coerceIn(0, RETRY_DELAYS_MS.lastIndex)
                val delayMs = RETRY_DELAYS_MS[idx]
                logger("$TAG error (attempt=$attempt): ${e.message}, retry in ${delayMs}ms")
                delay(delayMs)
            }
        }
    }

    private fun buildSubscriptionEnvelope(request: PositionsStreamSubscribeRequestDto): String =
        json.encodeToString(PositionsStreamSubscribeRequestDto.serializer(), request)
}
