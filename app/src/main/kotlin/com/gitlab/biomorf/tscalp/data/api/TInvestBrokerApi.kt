package com.gitlab.biomorf.tscalp.data.api

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderListItem
import com.gitlab.biomorf.tscalp.domain.models.OrderResult
import com.gitlab.biomorf.tscalp.domain.models.OrderState
import com.gitlab.biomorf.tscalp.domain.models.PortfolioPosition
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney
import com.gitlab.biomorf.tscalp.domain.models.StopOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import kotlinx.coroutines.flow.Flow
import ru.ttech.piapi.core.InvestApi

/**
 * Фасад T-Invest API.
 * Держит состояние (api, channels, sandboxMode), делегирует в два сервиса:
 *  - TInvestMarketDataService — счета, портфель, инструменты, цены, стримы, баланс;
 *  - TInvestOrdersService — заявки, стоп-заявки, отмена.
 */
class TInvestBrokerAPI(
    private val channelFactory: TInvestChannelFactory
) : BrokerApi {

    private val state = TInvestApiState()
    private val marketData = TInvestMarketDataService(state)
    private val orders = TInvestOrdersService(state)

    override val name: BrokerName = BrokerName.TINVEST
    override val isInitialized: Boolean get() = state.api != null

    /**
     * Инициализация API с явным токеном и режимом.
     * Закрывает старые каналы, создаёт новые и обновляет InvestApi.
     */
    fun initialize(token: String, sandbox: Boolean) {
        state.sandboxMode = sandbox
        state.channels?.shutdownAll()
        val newChannels = channelFactory.create(token, sandbox)
        state.channels = newChannels
        state.api = InvestApi.createApi(newChannels.api)
    }

    // ---------- BrokerApi: делегирование ----------

    override suspend fun getAccounts(sandboxMode: Boolean) = marketData.getAccounts(sandboxMode)
    override suspend fun openSandboxAccount() = marketData.openSandboxAccount()
    override suspend fun closeSandboxAccount(accountId: String) {
        marketData.closeSandboxAccount(accountId)
    }

    override suspend fun fetchPositionsRest(accountId: String, sandboxMode: Boolean) =
        marketData.fetchPositionsRest(accountId, sandboxMode)

    override fun subscribePositions(accountId: String): Flow<PositionStreamItem> =
        marketData.subscribePositions(accountId)

    override suspend fun getBalance(accountId: String) = marketData.getBalance(accountId)
    override suspend fun sandboxPayIn(accountId: String, amount: SandboxMoney) {
        marketData.sandboxPayIn(accountId, amount)
    }

    override suspend fun findInstruments(query: String) = marketData.findInstruments(query)

    override suspend fun postOrder(request: BrokerOrderRequest) = orders.postOrder(request)
    override suspend fun postStopOrder(request: StopOrderRequest) = orders.postStopOrder(request)
    override suspend fun getStopOrders(accountId: String) = orders.getStopOrders(accountId)
    override suspend fun cancelStopOrder(accountId: String, stopOrderId: String) {
        orders.cancelStopOrder(accountId, stopOrderId)
    }
    override suspend fun getOrders(accountId: String) = orders.getOrders(accountId)
    override suspend fun cancelOrder(accountId: String, orderId: String) {
        orders.cancelOrder(accountId, orderId)
    }

    override suspend fun getLastPricesByTscalpInstrumentId(ids: List<String>) =
        marketData.getLastPricesByTscalpInstrumentId(ids)
    override suspend fun getTradingStatuses(ids: List<String>) =
        marketData.getTradingStatuses(ids)
    override suspend fun subscribeOrderState(accountId: String): Flow<OrderState> =
        marketData.subscribeOrderState(accountId)

    override suspend fun checkTradeAvailability(
        accountId: String,
        tscalpInstrumentId: String,
        uid: String?,
        direction: OrderDirection,
        quantity: Long
    ): TradeCheckResult {
        val resolvedUid = uid
            ?: return TradeCheckResult.Error("Нет uid")
        return marketData.checkTradeAvailability(accountId, resolvedUid, direction, quantity)
    }

    // ---------- Публичные методы вне BrokerApi ----------

    /**
     * Возвращает полный доменный инструмент по uid.
     * Используется InstrumentRepository и OrdersViewModel.
     */
    suspend fun fetchFullInstrument(uid: String): InstrumentUi? =
        marketData.fetchFullInstrument(uid)

    /**
     * Стрим последних цен. Используется OrdersViewModel.startPriceUpdates().
     */
    override fun subscribeLastPrices(uids: List<String>): Flow<Pair<String, Double>> =
        marketData.subscribeLastPrices(uids)
}
