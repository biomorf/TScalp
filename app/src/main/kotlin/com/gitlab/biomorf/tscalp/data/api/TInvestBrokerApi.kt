package com.gitlab.biomorf.tscalp.data.api

import com.gitlab.biomorf.tscalp.BuildConfig
import com.gitlab.biomorf.tscalp.data.orchestrator.TInvestOrchestrator
import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerSnapshotApi
import com.gitlab.biomorf.tscalp.domain.api.roles.StreamsApi
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderListItem
import com.gitlab.biomorf.tscalp.domain.models.OrderState
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney
import com.gitlab.biomorf.tscalp.domain.models.StopOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult
import com.gitlab.biomorf.tscalp.transport.ktor.TInvestOrderStateWebSocket
import com.gitlab.biomorf.tscalp.transport.ktor.TInvestPositionsWebSocket
import com.gitlab.biomorf.tscalp.transport.ktor.TInvestPricesWebSocket
import com.gitlab.biomorf.tscalp.transport.ktor.TInvestRestTransport
import com.gitlab.biomorf.tscalp.util.AppLogger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transform
import ru.ttech.piapi.core.InvestApi

/**
 * Фасад T-Invest API.
 * Держит состояние (api, channels, sandboxMode), делегирует в два сервиса:
 *  - TInvestMarketDataService — счета, портфель, инструменты, цены, стримы, баланс;
 *  - TInvestOrdersService — заявки, стоп-заявки, отмена.
 *  // Архитектурный долг: 22 функции в классе. См. ROADMAP → «Рефакторинг BrokerApi-реализаций».
 */
@Suppress("TooManyFunctions")
class TInvestBrokerAPI(
    private val channelFactory: TInvestChannelFactory,
    private val httpClient: HttpClient,
) : BrokerApi,
    BrokerSnapshotApi,
    StreamsApi,
    BrokerLifecycleApi {
    private companion object {
        const val WS_BASE_URL_PROD = "wss://invest-public-api.tbank.ru/ws"
        const val WS_BASE_URL_SANDBOX = "wss://sandbox-invest-public-api.tbank.ru/ws"
        const val REST_BASE_URL_PROD = "https://invest-public-api.tbank.ru"
        const val REST_BASE_URL_SANDBOX = "https://sandbox-invest-public-api.tbank.ru"
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

        fun buildRestUrl(sandbox: Boolean): String = if (sandbox) REST_BASE_URL_SANDBOX else REST_BASE_URL_PROD
    }

    private val state = TInvestApiState()
    private val marketData = TInvestMarketDataService(state)
    private val orders = TInvestOrdersService(state)
    private val orchestrator =
        TInvestOrchestrator(
            marketData = marketData,
            orders = orders,
            wsFallback = { accountId -> buildWsFallback(accountId) },
            pricesWsFallback = { uids -> buildPricesWsFallback(uids) },
            orderStateWsFallback = { accountId -> buildOrderStateWsFallback(accountId) },
            pricesRestFallback = { uids ->
                val rest = restTransport ?: throw IllegalStateException("REST transport not initialized")
                rest.getLastPrices(uids)
            },
            positionsRestFallback = { accountId, _ ->
                val rest = restTransport ?: throw IllegalStateException("REST transport not initialized")
                rest.fetchPositionsSnapshot(accountId).positions.map { it.toPortfolioPosition() }
            },
            ordersRestFallback = { accountId ->
                val rest = restTransport ?: throw IllegalStateException("REST transport not initialized")
                val orders = rest.getOrders(accountId).map { it.toOrderListItem() }
                val stopOrders = rest.getStopOrders(accountId).map { it.toOrderListItem() }
                orders + stopOrders
            },
            balanceRestFallback = { accountId ->
                val rest = restTransport ?: throw IllegalStateException("REST transport not initialized")
                rest.getBalance(accountId, state.sandboxMode)
            },
        )

    /**
     * WebSocket-клиент PositionsStream. Создаётся в [initialize]
     * (нужен токен) и обнуляется в [deinitialize].
     * Nullable, потому что WS зависит от инициализированного
     * состояния брокера.
     */
    var positionsWebSocket: TInvestPositionsWebSocket? = null
        private set

    var pricesWebSocket: TInvestPricesWebSocket? = null
        private set

    var orderStateWebSocket: TInvestOrderStateWebSocket? = null
        private set

    private var wsScope: CoroutineScope? = null
    private var restTransport: TInvestRestTransport? = null

    override val name: BrokerName = BrokerName.TINVEST
    override val isInitialized: Boolean get() = state.api != null

    override val snapshot: BrokerSnapshotApi get() = this
    override val streams: StreamsApi get() = this
    override val lifecycle: BrokerLifecycleApi get() = this

    /**
     * Инициализация API с явным токеном и режимом.
     * Закрывает старые каналы, создаёт новые и обновляет InvestApi.
     */
    fun initialize(
        token: String,
        sandbox: Boolean,
    ) {
        state.sandboxMode = sandbox
        state.token = token
        state.channels?.shutdownAll()
        val newChannels = channelFactory.create(token, sandbox)
        state.channels = newChannels
        state.api = InvestApi.createApi(newChannels.api)
        // Все WS-клиенты — только prod. В sandbox сервер
        // закрывает соединение без кадра (см. docs/ISSUES.md),
        // поэтому клиенты не создаём.
        if (!sandbox) {
            positionsWebSocket =
                TInvestPositionsWebSocket(
                    client = httpClient,
                    url = buildWsUrl(sandbox, WS_POSITIONS_CHANNEL),
                    token = token,
                    logger = { msg -> AppLogger.d("TInvestWS", msg) },
                )
            wsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            pricesWebSocket =
                TInvestPricesWebSocket(
                    client = httpClient,
                    url = buildWsUrl(sandbox, WS_PRICES_CHANNEL),
                    token = token,
                    scope = wsScope!!,
                    logger = { msg -> AppLogger.d("TInvestWS/prices", msg) },
                    logFrames = BuildConfig.DEBUG,
                )
            orderStateWebSocket =
                TInvestOrderStateWebSocket(
                    client = httpClient,
                    url = buildWsUrl(sandbox, WS_ORDER_STATE_CHANNEL),
                    token = token,
                    scope = wsScope!!,
                    logger = { msg -> AppLogger.d("TInvestWS/orders", msg) },
                    logFrames = BuildConfig.DEBUG,
                )
        }
        restTransport =
            TInvestRestTransport(
                client = httpClient,
                baseUrl = buildRestUrl(sandbox),
                token = token,
                logger = { msg -> AppLogger.d("TInvestRest", msg) },
            )
    }

    /**
     * Typed lifecycle initialize via [BrokerLifecycleApi].
     * Delegates to [initialize] with TInvest credentials.
     * Returns false on any exception.
     */
    override suspend fun initialize(credentials: BrokerCredentials): Boolean =
        when (credentials) {
            is BrokerCredentials.TInvest -> {
                try {
                    initialize(credentials.token, credentials.sandbox)
                    true
                } catch (e: Exception) {
                    AppLogger.e("TInvestBrokerAPI", "lifecycle.initialize failed", e)
                    false
                }
            }
            else -> throw IllegalArgumentException(
                "TInvestBrokerAPI.initialize: expected TInvest credentials, got ${credentials::class.simpleName}",
            )
        }

    /**
     * Сбрасывает состояние брокера: закрывает каналы, обнуляет api.
     * После вызова isInitialized == false.
     * Вызывается при logout через SettingsViewModel.
     */
    override fun deinitialize() {
        state.channels?.shutdownAll()
        state.channels = null
        state.api = null
        state.token = null
        positionsWebSocket = null
        pricesWebSocket?.stop()
        pricesWebSocket = null
        orderStateWebSocket?.stop()
        orderStateWebSocket = null
        restTransport = null
        wsScope?.cancel()
        wsScope = null
    }

    /**
     * WS-fallback стрим-уровня для оркестратора.
     *
     * Возвращает null, пока [initialize] не создаст [positionsWebSocket].
     * Один WS-response → несколько PositionStreamItem (initialPositions
     * или position с несколькими инструментами) — emit через transform.
     */
    private fun buildWsFallback(accountId: String): Flow<PositionStreamItem>? =
        positionsWebSocket
            ?.subscribePositions(accountId)
            ?.transform { response ->
                TInvestPositionsWsMapper.mapAll(response).forEach { emit(it) }
            }

    private fun buildPricesWsFallback(uids: Set<String>): Flow<Pair<String, Double>>? {
        val ws = pricesWebSocket ?: return null
        ws.setInterest(uids)
        return ws.prices.mapNotNull { TInvestPricesWsMapper.mapLastPrice(it) }
    }

    private fun buildOrderStateWsFallback(accountId: String): Flow<OrderState>? {
        val ws = orderStateWebSocket ?: return null
        ws.setInterest(accountId)
        return ws.states.mapNotNull { TInvestOrderStateWsMapper.mapOrderState(it) }
    }

    // ---------- BrokerApi: делегирование ----------

    override suspend fun getAccounts(sandboxMode: Boolean) = marketData.getAccounts(sandboxMode)

    override suspend fun openSandboxAccount() = marketData.openSandboxAccount()

    override suspend fun closeSandboxAccount(accountId: String) {
        marketData.closeSandboxAccount(accountId)
    }

    override suspend fun fetchPositionsSnapshot(
        accountId: String,
        sandboxMode: Boolean,
    ) = marketData.fetchPositionsSnapshot(accountId, sandboxMode)

    override fun subscribePositions(accountId: String): Flow<PositionStreamItem> = orchestrator.positionsStream(accountId)

    override suspend fun getBalance(accountId: String) = orchestrator.balanceSnapshot(accountId)

    override suspend fun sandboxPayIn(
        accountId: String,
        amount: SandboxMoney,
    ) {
        marketData.sandboxPayIn(accountId, amount)
    }

    override suspend fun findInstruments(query: String) = marketData.findInstruments(query)

    override suspend fun postOrder(request: BrokerOrderRequest) = orders.postOrder(request)

    override suspend fun postStopOrder(request: StopOrderRequest) = orders.postStopOrder(request)

    override suspend fun getStopOrders(accountId: String) = orders.getStopOrders(accountId)

    override suspend fun cancelStopOrder(
        accountId: String,
        stopOrderId: String,
    ) {
        orders.cancelStopOrder(accountId, stopOrderId)
    }

    override suspend fun getOrders(accountId: String) = orders.getOrders(accountId)

    override suspend fun cancelOrder(
        accountId: String,
        orderId: String,
    ) {
        orders.cancelOrder(accountId, orderId)
    }

    override suspend fun getLastPricesByTscalpInstrumentId(ids: List<String>) = marketData.getLastPricesByTscalpInstrumentId(ids)

    override suspend fun getTradingStatuses(ids: List<String>) = marketData.getTradingStatuses(ids)

    override suspend fun subscribeOrderState(accountId: String): Flow<OrderState> = orchestrator.orderStateStream(accountId)

    override suspend fun checkTradeAvailability(
        accountId: String,
        tscalpInstrumentId: String,
        brokerUid: String?,
        direction: OrderDirection,
        quantity: Long,
    ): TradeCheckResult {
        val resolvedUid =
            brokerUid
                ?: return TradeCheckResult.Error("Нет brokerUid")
        return marketData.checkTradeAvailability(accountId, resolvedUid, direction, quantity)
    }

    // ---------- Публичные методы вне BrokerApi ----------

    // ---------- Unary snapshots (для менеджеров) ----------
    // Делегируют в оркестратор, где будет fallback gRPC → REST (шаг 2).

    override suspend fun pricesSnapshot(ids: List<String>): Map<String, Double?> = orchestrator.pricesSnapshot(ids)

    override suspend fun ordersSnapshot(accountId: String): List<OrderListItem> = orchestrator.ordersSnapshot(accountId)

    override suspend fun positionsSnapshot(accountId: String): List<PositionStreamItem> = orchestrator.positionsSnapshot(accountId, state.sandboxMode)

    /**
     * Возвращает полный доменный инструмент по uid.
     * Используется `InstrumentRepository.loadAndCache`.
     *
     * Не входит в `BrokerApi` — TInvest-specific. Планируется
     * вынос в `BrokerInstrumentApi` (см. ROADMAP →
     * `### TInvest-specific: fetchFullInstrument`).
     */
    suspend fun fetchFullInstrument(uid: String): InstrumentUi? = marketData.fetchFullInstrument(uid)

    /**
     * Стрим последних цен. Используется OrdersViewModel.startPriceUpdates().
     */
    override fun subscribeLastPrices(uids: List<String>): Flow<Pair<String, Double>> = orchestrator.pricesStream(uids.toSet())
}
