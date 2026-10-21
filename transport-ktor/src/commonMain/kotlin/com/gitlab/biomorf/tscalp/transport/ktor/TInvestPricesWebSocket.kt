package com.gitlab.biomorf.tscalp.transport.ktor

import com.gitlab.biomorf.tscalp.transport.ktor.dto.LastPriceInstrumentDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.MarketDataRequestDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.MarketDataResponseDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.SubscribeLastPriceRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * WebSocket-клиент для T-Invest MarketDataStream (цены последней сделки).
 *
 * URL: wss://invest-public-api.tbank.ru/ws (sandbox — зеркальный хост).
 * Заголовки: Authorization: Bearer <token>, Web-Socket-Protocol: json.
 * Канал: /tinkoff.public.invest.api.contract.v1.MarketDataStreamService/MarketDataStream.
 *
 * **Incremental subscription.** В отличие от PositionsStream, где
 * подписка создаётся один раз на счёт, здесь union инструментов
 * меняется динамически (PriceStreamManager.setInterest). Клиент
 * держит одно WS-соединение и шлёт SUBSCRIBE/UNSUBSCRIBE-кадры
 * при каждом изменении interest. При реконнекте — переподписка
 * на весь текущий interest.
 *
 * **Hot SharedFlow.** [prices] — SharedFlow, один upstream на любое
 * число коллекторов. Это исключает двойное WS-подключение при
 * повторном сборе (см. docs/ISSUES.md про double subscription).
 *
 * Эмитит сырые DTO (MarketDataResponseDto).
 * Конвертация в доменные модели — в :app (маппер).
 *
 * Переподключение — экспоненциальный backoff [1s, 5s, 15s, 30s, 60s].
 *
 * НЕ использует Hilt. НЕ использует Android API. Готов к KMP.
 *
 * Lifecycle: создаётся один раз (DI-провайдер), живёт до logout.
 * Caller вызывает [setInterest] при смене union и [stop] при закрытии.
 */
class TInvestPricesWebSocket(
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
        private const val TAG = "[WS/ktor/TInvest]"
        private val RETRY_DELAYS_MS = longArrayOf(1_000, 5_000, 15_000, 30_000, 60_000)
        private const val PRICES_BUFFER = 256
    }

    private val interest = MutableStateFlow<Set<String>>(emptySet())

    // DROP_OLDEST: для цен последнее значение важнее; при медленном
    // потребителе дропаем устаревшие кадры, а не suspend-им reader.
    private val _prices =
        MutableSharedFlow<MarketDataResponseDto>(
            replay = 0,
            extraBufferCapacity = PRICES_BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val prices: SharedFlow<MarketDataResponseDto> = _prices.asSharedFlow()

    private var connectionJob: Job? = null

    /**
     * Обновляет набор инструментов, на которые подписан клиент.
     *
     * Диффится с текущей подпиской: removed → UNSUBSCRIBE,
     * added → SUBSCRIBE. Если соединение не установлено, оно
     * откроется (или уже открыто и переподпишется на новое состояние
     * при следующем реконнекте).
     */
    fun setInterest(uids: Set<String>) {
        if (interest.value == uids) return
        logger("$TAG setInterest: ${uids.size} uids")
        interest.value = uids
        ensureConnected()
    }

    /** Закрывает WS-соединение. Повторный [setInterest] пересоздаст. */
    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
    }

    private fun ensureConnected() {
        if (connectionJob?.isActive == true) return
        connectionJob = scope.launch { connectionLoop() }
    }

    private suspend fun connectionLoop() {
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
                    attempt = 0

                    val subscribed = mutableSetOf<String>()

                    coroutineScope {
                        val watcher = launch { watchInterest(subscribed) }
                        try {
                            logger("$TAG incoming loop started")
                            for (frame in incoming) {
                                if (logFrames) logger("$TAG frame type=${frame::class.simpleName}")
                                if (frame !is Frame.Text) continue
                                val text = frame.readText()
                                if (logFrames) logger("$TAG frame received: $text")
                                try {
                                    val response = json.decodeFromString(MarketDataResponseDto.serializer(), text)
                                    _prices.tryEmit(response)
                                } catch (e: Exception) {
                                    logger("$TAG parse error: ${e.message}")
                                }
                            }
                            logger("$TAG incoming loop finished")
                        } finally {
                            watcher.cancel()
                        }
                    }
                    logger("$TAG stream closed by server, reconnecting")
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

    private suspend fun DefaultClientWebSocketSession.watchInterest(subscribed: MutableSet<String>) {
        interest.collect { newInterest ->
            val toAdd = newInterest - subscribed
            val toRemove = subscribed - newInterest
            if (toAdd.isEmpty() && toRemove.isEmpty()) return@collect

            if (toRemove.isNotEmpty()) {
                sendSubscriptionRequest("SUBSCRIPTION_ACTION_UNSUBSCRIBE", toRemove)
                subscribed.removeAll(toRemove)
            }
            if (toAdd.isNotEmpty()) {
                sendSubscriptionRequest("SUBSCRIPTION_ACTION_SUBSCRIBE", toAdd)
                subscribed.addAll(toAdd)
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.sendSubscriptionRequest(
        action: String,
        uids: Set<String>,
    ) {
        val request =
            MarketDataRequestDto(
                subscribeLastPriceRequest =
                    SubscribeLastPriceRequestDto(
                        subscriptionAction = action,
                        instruments = uids.map { LastPriceInstrumentDto(instrumentId = it) },
                    ),
            )
        val envelope = json.encodeToString(MarketDataRequestDto.serializer(), request)
        logger("$TAG sending $action for ${uids.size} uids (${envelope.length} bytes)")
        send(Frame.Text(envelope))
    }
}
