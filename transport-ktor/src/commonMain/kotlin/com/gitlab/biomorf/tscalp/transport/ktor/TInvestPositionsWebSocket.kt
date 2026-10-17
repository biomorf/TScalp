package com.gitlab.biomorf.tscalp.transport.ktor

import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsStreamResponseDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsStreamSubscribeRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * WebSocket-клиент для T-Invest PositionsStream (OkHttp).
 *
 * URL: wss://invest-public-api.tbank.ru/ws/
 * Канал: /tinkoff.public.invest.api.contract.v1.OperationsStreamService/PositionsStream
 * Формат: protobuf JSON mapping.
 *
 * Эмитит сырые DTO (PositionsStreamResponseDto).
 * Конвертация в доменные модели — в :app (оркестратор).
 *
 * Переподключение — экспоненциальный backoff [1s, 5s, 15s, 30s, 60s], cap 60s.
 *
 * НЕ использует Hilt. НЕ использует Android API.
 *
 * Реализовано на OkHttp, а не на Ktor: Ktor client возвращал 404
 * на upgrade-handshake (проверены комбинации URL/header),
 * OkHttp — тот же стек, что у gRPC-транспорта, TLS-цепочка
 * CustomTrustManager уже проверена на этом проекте.
 */
class TInvestPositionsWebSocket(
    private val client: OkHttpClient,
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
                connectOnce(accountId).collect { emit(it) }
                attempt = 0
                logger("$TAG stream closed by server, reconnecting")
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

    private fun connectOnce(accountId: String): Flow<PositionsStreamResponseDto> = callbackFlow {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Web-Socket-Protocol", "json, $token")
                .build()

        val listener =
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) {
                    logger("$TAG connected")
                    val subscription =
                        PositionsStreamSubscribeRequestDto(
                            accounts = listOf(accountId),
                            withInitialPositions = true,
                        )
                    val envelope = buildSubscriptionEnvelope(subscription)
                    webSocket.send(envelope)
                    logger("$TAG subscription sent (${envelope.length} bytes)")
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    logger("$TAG frame received (len=${text.length})")
                    try {
                        val response = json.decodeFromString(PositionsStreamResponseDto.serializer(), text)
                        trySend(response)
                    } catch (e: Exception) {
                        logger("$TAG parse error: ${e.message}")
                    }
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    logger("$TAG closed by server: code=$code, reason=$reason")
                    close()
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    logger("$TAG failure: ${t.message}")
                    close(t)
                }
            }

        val webSocket = client.newWebSocket(request, listener)
        awaitClose {
            logger("$TAG closing")
            webSocket.cancel()
        }
    }

    private fun buildSubscriptionEnvelope(request: PositionsStreamSubscribeRequestDto): String {
        val inner = json.encodeToString(PositionsStreamSubscribeRequestDto.serializer(), request)
        return """{"positionsStream":$inner}"""
    }
}
