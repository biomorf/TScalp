package com.gitlab.biomorf.tscalp.transport.ktor

import com.gitlab.biomorf.tscalp.transport.ktor.dto.OrderStateStreamRequestDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.OrderStateStreamResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
 * одно соединение. Для смены счёта — [setInterest] с новым
 * accountId, соединение пересоздаётся.
 *
 * Эмитит сырые DTO через [states] (hot SharedFlow):
 * - orderState — обновления заявки;
 * - subscription — подтверждение (trackingId, status, error);
 * - ping — keepalive.
 *
 * **SharedFlow без буфера (SUSPEND).** Для заявок каждое событие
 * критично (`CANCELLED`, `FILL` нельзя терять). Если подписчика
 * нет, WS-reader блокируется на `emit`; Ktor буферизует входящие
 * кадры (с backpressure на сокет). При появлении подписчика всё
 * доставляется без потерь. Подписчик (`OrderStreamManager.collectStream`)
 * работает быстро — долгих пауз не бывает.
 *
 * **Lifecycle.** Соединение живёт в [scope] (передаётся в
 * конструктор) и управляется методами [setInterest] / [stop].
 * `TInvestBrokerAPI.deinitialize` вызывает [stop] перед
 * обнулением поля.
 *
 * Переподключение при ошибках — экспоненциальный backoff
 * [1s, 5s, 15s, 30s, 60s].
 *
 * НЕ использует Hilt. НЕ использует Android API. Готов к KMP.
 */
class TInvestOrderStateWebSocket(
    private val client: HttpClient,
    private val url: String,
    private val token: String,
    private val scope: CoroutineScope,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        },
    private val logger: (String) -> Unit = { },
    private val logFrames: Boolean = false,
) {
    companion object {
        private const val TAG = "[transport-ktor/orders-ws]"
        private val RETRY_DELAYS_MS = longArrayOf(1_000, 5_000, 15_000, 30_000, 60_000)
    }

    // SUSPEND без буфера: см. KDoc. Подписчик должен быть готов
    // принимать до того, как WS начнёт читать кадры.
    private val _states = MutableSharedFlow<OrderStateStreamResponseDto>()
    val states: SharedFlow<OrderStateStreamResponseDto> = _states.asSharedFlow()

    private var connectionJob: Job? = null
    private var currentAccountId: String? = null

    /**
     * Устанавливает интерес на [accountId]. Если интерес уже тот же
     * и соединение активно — no-op. Иначе — пересоздаёт соединение
     * (смена счёта требует новой подписки, incremental в
     * OrderStateStream нет).
     */
    fun setInterest(accountId: String) {
        if (currentAccountId == accountId && connectionJob?.isActive == true) return
        logger("$TAG setInterest: accountId=$accountId")
        currentAccountId = accountId
        restartConnection()
    }

    /** Закрывает WS-соединение и сбрасывает интерес. */
    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        currentAccountId = null
    }

    private fun restartConnection() {
        connectionJob?.cancel()
        connectionJob = scope.launch { connectionLoop() }
    }

    private suspend fun connectionLoop() {
        val accountId = currentAccountId ?: return
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            if (currentAccountId != accountId) return
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
                        if (logFrames) logger("$TAG frame received: $text")
                        val response =
                            try {
                                json.decodeFromString(OrderStateStreamResponseDto.serializer(), text)
                            } catch (e: Exception) {
                                logger("$TAG parse error: ${e.message}")
                                continue
                            }
                        _states.emit(response)
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
