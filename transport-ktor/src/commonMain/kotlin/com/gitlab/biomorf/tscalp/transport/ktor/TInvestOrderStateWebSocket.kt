package com.gitlab.biomorf.tscalp.transport.ktor

import com.gitlab.biomorf.tscalp.transport.ktor.dto.OrderStateStreamRequestDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.OrderStateStreamResponseDto
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
 * WebSocket-клиент для T-Invest OrderStateStream.
 *
 * URL: wss://invest-public-api.tbank.ru/ws (sandbox — зеркальный хост).
 * Заголовки: Authorization: Bearer <token>, Web-Socket-Protocol: json.
 * Канал: /tinkoff.public.invest.api.contract.v1.OrdersStreamService/OrderStateStream.
 *
 * Подписка — per-account, как у PositionsStream: один счёт —
 * одно соединение. Для нескольких счетов — несколько экземпляров
 * (или отдельный вызов subscribeOrderState на каждый счёт).
 *
 * Эмитит сырые DTO (OrderStateStreamResponseDto), включая:
 * - orderState — обновления заявки;
 * - subscription — подтверждение (trackingId, status, error);
 * - ping — keepalive.
 *
 * Конвертация в доменные модели — в :app (маппер).
 *
 * Переподключение — экспоненциальный backoff [1s, 5s, 15s, 30s, 60s].
 *
 * НЕ использует Hilt. НЕ использует Android API. Готов к KMP.
 */
class TInvestOrderStateWebSocket(
    private val client: HttpClient,
    private val url: String,
    private val token: String,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        },
    private val logger: (String) -> Unit = { },
) {
    companion object {
        private const val TAG = "[transport-ktor/orders-ws]"
        private val RETRY_DELAYS_MS = longArrayOf(1_000, 5_000, 15_000, 30_000, 60_000)
    }

    fun subscribeOrderState(accountId: String): Flow<OrderStateStreamResponseDto> = flow {
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
                    logger("$TAG subscribing: accountId=$accountId")
                    val subscription = OrderStateStreamRequestDto(
                        accounts = listOf(accountId),
                    )
                    val envelope = json.encodeToString(OrderStateStreamRequestDto.serializer(), subscription)
                    logger("$TAG payload: $envelope")
                    send(Frame.Text(envelope))
                    logger("$TAG subscription sent (${envelope.length} bytes)")

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        logger("$TAG frame received: $text")
                        try {
                            val response = json.decodeFromString(OrderStateStreamResponseDto.serializer(), text)
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
}
