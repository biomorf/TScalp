package com.gitlab.biomorf.tscalp.data.api

import com.gitlab.biomorf.tscalp.data.orchestrator.TInvestOrchestrator
import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderState
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney
import com.gitlab.biomorf.tscalp.domain.models.StopOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult
import com.gitlab.biomorf.tscalp.transport.ktor.TInvestPositionsWebSocket
import com.gitlab.biomorf.tscalp.util.AppLogger
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
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
) : BrokerApi {
    private companion object {
        const val WS_BASE_URL = "wss://invest-public-api.tbank.ru/ws"
        const val WS_POSITIONS_CHANNEL = "tinkoff.public.invest.api.contract.v1.OperationsStreamService/PositionsStream"
        const val WS_URL = "$WS_BASE_URL/$WS_POSITIONS_CHANNEL"
    }

    private val state = TInvestApiState()
    private val marketData = TInvestMarketDataService(state)
    private val orchestrator =
        TInvestOrchestrator(
            marketData = marketData,
            wsFallback = { accountId -> buildWsFallback(accountId) },
        )
    private val orders = TInvestOrdersService(state)

    /**
     * WebSocket-клиент PositionsStream. Создаётся в [initialize]
     * (нужен токен) и обнуляется в [deinitialize].
     * Nullable, потому что WS зависит от инициализированного
     * состояния брокера.
     */
    var positionsWebSocket: TInvestPositionsWebSocket? = null
        private set

    override val name: BrokerName = BrokerName.TINVEST
    override val isInitialized: Boolean get() = state.api != null

    /**
     * Инициализация API с явным токеном и режимом.
     * Закрывает старые каналы, создаёт новые и обновляет InvestApi.
     */
    fun initialize(
        token: String,
        sandbox: Boolean,
    ) {
        state.sandboxMode = sandbox
        state.channels?.shutdownAll()
        val newChannels = channelFactory.create(token, sandbox)
        state.channels = newChannels
        state.api = InvestApi.createApi(newChannels.api)
        positionsWebSocket =
            TInvestPositionsWebSocket(
                client = httpClient,
                url = WS_URL,
                token = token,
                logger = { msg -> AppLogger.d("TInvestWS", msg) },
            )
    }

    /**
     * Сбрасывает состояние брокера: закрывает каналы, обнуляет api.
     * После вызова isInitialized == false.
     * Вызывается при logout через SettingsViewModel.
     */
    fun deinitialize() {
        state.channels?.shutdownAll()
        state.channels = null
        state.api = null
        positionsWebSocket = null
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

    override suspend fun getBalance(accountId: String) = marketData.getBalance(accountId)

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

    override suspend fun subscribeOrderState(accountId: String): Flow<OrderState> = marketData.subscribeOrderState(accountId)

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

    /**
     * Возвращает полный доменный инструмент по uid.
     * Используется InstrumentRepository и OrdersViewModel.
     */
    suspend fun fetchFullInstrument(uid: String): InstrumentUi? = marketData.fetchFullInstrument(uid)

    /**
     * Стрим последних цен. Используется OrdersViewModel.startPriceUpdates().
     */
    override fun subscribeLastPrices(uids: List<String>): Flow<Pair<String, Double>> = marketData.subscribeLastPrices(uids)
}
