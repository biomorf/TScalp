package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.BuildConfig
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.OrderState
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.transport.ktor.TInvestOrderStateWebSocket
import com.gitlab.biomorf.tscalp.transport.ktor.TInvestPositionsWebSocket
import com.gitlab.biomorf.tscalp.transport.ktor.TInvestPricesWebSocket
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transform

/**
 * WebSocket-транспорт T-Invest: 3 WS-клиента (positions, prices,
 * orderState) + scope. Вынесен из фасада [TInvestBrokerAPI]
 * (см. ROADMAP -> "### Рефакторинг BrokerApi-реализаций (D)").
 *
 * Клиенты создаются в [initialize] только для prod: в sandbox
 * сервер закрывает соединение без кадра (см. docs/ISSUES.md).
 * [stop] отменяет scope и обнуляет клиентов.
 */
class TInvestWsService(
    private val httpClient: HttpClient,
    private val logger: Logger,
) {
    private companion object {
        const val WS_BASE_URL_PROD = "wss://invest-public-api.tbank.ru/ws"
        const val WS_BASE_URL_SANDBOX = "wss://sandbox-invest-public-api.tbank.ru/ws"
        const val WS_POSITIONS_CHANNEL = "tinkoff.public.invest.api.contract.v1.OperationsStreamService/PositionsStream"
        const val WS_PRICES_CHANNEL = "tinkoff.public.invest.api.contract.v1.MarketDataStreamService/MarketDataStream"
        const val WS_ORDER_STATE_CHANNEL = "tinkoff.public.invest.api.contract.v1.OrdersStreamService/OrderStateStream"

        fun buildWsUrl(
            sandbox: Boolean,
            channel: String,
        ): String {
            val base = if (sandbox) WS_BASE_URL_SANDBOX else WS_BASE_URL_PROD
            return "$base/$channel"
        }
    }

    var positionsWebSocket: TInvestPositionsWebSocket? = null
        private set

    var pricesWebSocket: TInvestPricesWebSocket? = null
        private set

    var orderStateWebSocket: TInvestOrderStateWebSocket? = null
        private set

    private var wsScope: CoroutineScope? = null

    fun initialize(
        token: String,
        sandbox: Boolean,
    ) {
        // Предыдущие клиенты закрываются до создания новых:
        // ре-логин / переключение prod ↔ sandbox иначе оставляли
        // старый wsScope и активные WS-сессии (clean-audit Н62).
        stop()
        if (sandbox) return
        positionsWebSocket =
            TInvestPositionsWebSocket(
                client = httpClient,
                url = buildWsUrl(sandbox, WS_POSITIONS_CHANNEL),
                token = token,
                logger = { msg -> logger.d("TInvestWS", msg) },
            )
        wsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        pricesWebSocket =
            TInvestPricesWebSocket(
                client = httpClient,
                url = buildWsUrl(sandbox, WS_PRICES_CHANNEL),
                token = token,
                scope = wsScope!!,
                logger = { msg -> logger.d("TInvestWS/prices", msg) },
                logFrames = BuildConfig.DEBUG,
            )
        orderStateWebSocket =
            TInvestOrderStateWebSocket(
                client = httpClient,
                url = buildWsUrl(sandbox, WS_ORDER_STATE_CHANNEL),
                token = token,
                scope = wsScope!!,
                logger = { msg -> logger.d("TInvestWS/orders", msg) },
                logFrames = BuildConfig.DEBUG,
            )
    }

    fun stop() {
        positionsWebSocket = null
        pricesWebSocket?.stop()
        pricesWebSocket = null
        orderStateWebSocket?.stop()
        orderStateWebSocket = null
        wsScope?.cancel()
        wsScope = null
    }

    fun buildWsFallback(accountId: String): Flow<PositionStreamItem>? =
        positionsWebSocket
            ?.subscribePositions(accountId)
            ?.transform { response ->
                TInvestPositionsWsMapper.mapAll(response).forEach { emit(it) }
            }

    fun buildPricesWsFallback(uids: Set<String>): Flow<Pair<String, Double>>? {
        val ws = pricesWebSocket ?: return null
        ws.setInterest(uids)
        return ws.prices.mapNotNull { TInvestPricesWsMapper.mapLastPrice(it) }
    }

    fun buildOrderStateWsFallback(accountId: String): Flow<OrderState>? {
        val ws = orderStateWebSocket ?: return null
        ws.setInterest(accountId)
        return ws.states.mapNotNull { TInvestOrderStateWsMapper.mapOrderState(it) }
    }
}
