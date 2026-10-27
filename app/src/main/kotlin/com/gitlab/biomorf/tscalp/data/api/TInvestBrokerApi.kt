package com.gitlab.biomorf.tscalp.data.api

import com.gitlab.biomorf.tscalp.data.orchestrator.TInvestOrchestrator
import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.DebugFlagsRepository
import com.gitlab.biomorf.tscalp.domain.api.roles.AccountsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BalanceApi
import com.gitlab.biomorf.tscalp.domain.api.roles.MarketDataApi
import com.gitlab.biomorf.tscalp.domain.api.roles.PortfolioApi
import com.gitlab.biomorf.tscalp.domain.api.roles.TradeCheckApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerInstrumentApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerSnapshotApi
import com.gitlab.biomorf.tscalp.domain.api.roles.OrdersApi
import com.gitlab.biomorf.tscalp.domain.api.roles.SandboxApi
import com.gitlab.biomorf.tscalp.domain.api.roles.StreamsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.UserInstrumentsApi
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
import com.gitlab.biomorf.tscalp.util.AppLogger
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
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
    private val debugFlagsRepository: DebugFlagsRepository,
) : BrokerApi,
    BrokerSnapshotApi,
    StreamsApi,
    BrokerLifecycleApi,
    BrokerInstrumentApi,
    OrdersApi,
    AccountsApi,
    SandboxApi,
    UserInstrumentsApi,
    BalanceApi,
    PortfolioApi,
    MarketDataApi,
    TradeCheckApi {
    private val state = TInvestApiState()
    private val marketData = TInvestMarketDataService(state)
    private val ordersService = TInvestOrdersService(state)
    private val wsService = TInvestWsService(httpClient)
    private val restService = TInvestRestService(state, httpClient)
    private val orchestrator =
        TInvestOrchestrator(
            marketData = marketData,
            debugFlagsRepository = debugFlagsRepository,
            orders = ordersService,
            wsFallback = { accountId -> wsService.buildWsFallback(accountId) },
            pricesWsFallback = { uids -> wsService.buildPricesWsFallback(uids) },
            orderStateWsFallback = { accountId -> wsService.buildOrderStateWsFallback(accountId) },
            pricesRestFallback = { uids -> restService.pricesRestFallback(uids) },
            positionsRestFallback = { accountId, _ -> restService.positionsRestFallback(accountId) },
            ordersRestFallback = { accountId -> restService.ordersRestFallback(accountId) },
            balanceRestFallback = { accountId -> restService.balanceRestFallback(accountId) },
        )

    override val name: BrokerName = BrokerName.TINVEST
    override val isInitialized: Boolean get() = state.api != null

    override val snapshot: BrokerSnapshotApi get() = this
    override val streams: StreamsApi get() = this
    override val lifecycle: BrokerLifecycleApi get() = this
    override val instrument: BrokerInstrumentApi get() = this
    override val orders: OrdersApi get() = this
    override val accounts: AccountsApi get() = this
    override val sandbox: SandboxApi get() = this
    override val userInstruments: UserInstrumentsApi get() = this
    override val balance: BalanceApi get() = this
    override val portfolio: PortfolioApi get() = this
    override val marketData: MarketDataApi get() = this
    override val tradeCheck: TradeCheckApi get() = this

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
        wsService.initialize(token, sandbox)
        restService.initialize(token, sandbox)
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
        wsService.stop()
        restService.stop()
    }

    // ---------- BrokerApi: делегирование ----------
        marketData.closeSandboxAccount(accountId)
    }

    override suspend fun fetchPortfolioPositions(
        accountId: String,
        sandboxMode: Boolean,
    ) = marketData.fetchPortfolioPositions(accountId, sandboxMode)
        accountId: String,
        amount: SandboxMoney,
    ) {
        marketData.sandboxPayIn(accountId, amount)
    }
        accountId: String,
        stopOrderId: String,
    ) {
        ordersService.cancelStopOrder(accountId, stopOrderId)
    }
        accountId: String,
        orderId: String,
    ) {
        ordersService.cancelOrder(accountId, orderId)
    }

    override suspend fun getLastPricesByTscalpInstrumentId(ids: List<String>) = marketData.getLastPricesByTscalpInstrumentId(ids)

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
     * Реализация [BrokerInstrumentApi.fetchFullInstrument]
     * (nullable-val роль, см. `BrokerApi.instrument`).
     */
    override suspend fun fetchFullInstrument(uid: String): InstrumentUi? = marketData.fetchFullInstrument(uid)

    /**
     * Стрим последних цен. Используется OrdersViewModel.startPriceUpdates().
     */}
