package com.example.tscalp.data.api

import android.util.Log
import com.example.tscalp.domain.models.BrokerAccount
import com.example.tscalp.domain.models.BrokerAccountType
import com.example.tscalp.domain.models.FutureUi
import com.example.tscalp.domain.models.InstrumentUi
import com.example.tscalp.domain.models.OrderDirection
import com.example.tscalp.domain.models.OrderState
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.domain.models.PositionStreamItem
import com.example.tscalp.domain.models.SandboxMoney
import com.example.tscalp.domain.models.TradeCheckResult
import com.example.tscalp.domain.models.TradingAvailability
import com.example.tscalp.util.formatCurrency
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.GetAccountsRequest
import ru.tinkoff.piapi.contract.v1.GetFuturesMarginRequest
import ru.tinkoff.piapi.contract.v1.GetLastPricesRequest
import ru.tinkoff.piapi.contract.v1.GetMarginAttributesRequest
import ru.tinkoff.piapi.contract.v1.GetTradingStatusRequest
import ru.tinkoff.piapi.contract.v1.Instrument
import ru.tinkoff.piapi.contract.v1.InstrumentIdType
import ru.tinkoff.piapi.contract.v1.InstrumentRequest
import ru.tinkoff.piapi.contract.v1.InstrumentShort
import ru.tinkoff.piapi.contract.v1.FindInstrumentRequest
import ru.tinkoff.piapi.contract.v1.LastPriceInstrument
import ru.tinkoff.piapi.contract.v1.MarketDataResponse
import ru.tinkoff.piapi.contract.v1.MarketDataServerSideStreamRequest
import ru.tinkoff.piapi.contract.v1.MarketDataStreamServiceGrpc
import ru.tinkoff.piapi.contract.v1.MoneyValue
import ru.tinkoff.piapi.contract.v1.OrderStateStreamRequest
import ru.tinkoff.piapi.contract.v1.OrderStateStreamResponse
import ru.tinkoff.piapi.contract.v1.OrdersStreamServiceGrpc
import ru.tinkoff.piapi.contract.v1.PortfolioRequest
import ru.tinkoff.piapi.contract.v1.PortfolioResponse
import ru.tinkoff.piapi.contract.v1.SandboxPayInRequest
import ru.tinkoff.piapi.contract.v1.SubscribeLastPriceRequest
import ru.tinkoff.piapi.contract.v1.SubscriptionAction

/**
 * Маркет-данные, портфель, стримы и счета T-Invest.
 * Работает на разделяемом TInvestApiState.
 */
class TInvestMarketDataService(
    private val state: TInvestApiState
) {

    companion object {
        private const val TAG = "TInvestMarketData"
    }

    // ---------- Счета ----------

    suspend fun getAccounts(sandboxMode: Boolean): List<BrokerAccount> = withContext(Dispatchers.IO) {
        val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
        val request = GetAccountsRequest.getDefaultInstance()
        val accounts = if (sandboxMode) {
            currentApi.sandboxServiceSync.getSandboxAccounts(request).accountsList
        } else {
            currentApi.usersServiceSync.getAccounts(request).accountsList
        }
        accounts.map { acc ->
            BrokerAccount(
                id = acc.id,
                name = acc.name.ifBlank { "Счёт ${acc.id.take(8)}…" },
                type = when (acc.typeValue) {
                    1 -> BrokerAccountType.BROKER
                    2 -> BrokerAccountType.IIS
                    3 -> BrokerAccountType.INVEST_BOX
                    else -> BrokerAccountType.OTHER
                }
            )
        }
    }

    suspend fun openSandboxAccount(): String = withContext(Dispatchers.IO) {
        val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
        val request = ru.tinkoff.piapi.contract.v1.OpenSandboxAccountRequest.newBuilder().build()
        currentApi.sandboxServiceSync.openSandboxAccount(request).accountId
    }

    suspend fun closeSandboxAccount(accountId: String) = withContext(Dispatchers.IO) {
        val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
        val request = ru.tinkoff.piapi.contract.v1.CloseSandboxAccountRequest.newBuilder()
            .setAccountId(accountId)
            .build()
        currentApi.sandboxServiceSync.closeSandboxAccount(request)
        Log.d(TAG, "Счёт песочницы $accountId закрыт")
    }

    // ---------- Портфель ----------

    suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse =
        withContext(Dispatchers.IO) {
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val request = PortfolioRequest.newBuilder().setAccountId(accountId).build()
            if (sandboxMode) currentApi.sandboxServiceSync.getSandboxPortfolio(request)
            else currentApi.operationsServiceSync.getPortfolio(request)
        }

    suspend fun fetchPositionsRest(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> =
        withContext(Dispatchers.IO) {
            val response = getPortfolio(accountId, sandboxMode)

            response.positionsList.mapNotNull { pos ->
                val uid = pos.instrumentUid
                if (uid.isBlank()) return@mapNotNull null

                val protoInstrument = fetchProtoInstrument(uid)
                var marginAmount: Double? = null
                if (protoInstrument?.instrumentType == "futures" && protoInstrument.figi != null) {
                    marginAmount = getFuturesMargin(protoInstrument.figi)
                }

                val instrumentUi = protoInstrument?.let {
                    TInvestInstrumentMapper.mapProtoToDomain(it, marginAmount)
                }
                val pointVal = (instrumentUi as? FutureUi)?.pointValue

                val quantity = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 }?.toLong() ?: 0L
                val currentPrice = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
                val totalValue = currentPrice * quantity
                val expectedYield = pos.expectedYield?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
                val avgPrice = pos.averagePositionPrice?.let { it.units + it.nano / 1_000_000_000.0 }

                val profit: Double? = if (expectedYield > 0.0) {
                    expectedYield
                } else if (avgPrice != null && avgPrice > 0.0) {
                    (currentPrice - avgPrice) * quantity
                } else null

                val profitPercent: Double? = when {
                    profit != null && avgPrice != null && avgPrice > 0.0 && quantity > 0 ->
                        (profit / (avgPrice * quantity)) * 100.0
                    else -> null
                }

                PortfolioPosition(
                    tscalpInstrumentId = instrumentUi?.tscalpInstrumentId ?: "",
                    name = instrumentUi?.name ?: "",
                    ticker = instrumentUi?.ticker ?: "",
                    classCode = instrumentUi?.classCode ?: "",
                    isin = instrumentUi?.isin ?: "",
                    quantity = quantity,
                    currentPrice = currentPrice,
                    averagePrice = avgPrice,
                    totalValue = totalValue,
                    profit = profit,
                    profitPercent = profitPercent,
                    instrumentType = instrumentUi?.instrumentType ?: "",
                    pointValue = pointVal
                )
            }
        }

    // ---------- Стрим позиций (polling) ----------

    fun subscribePositions(accountId: String): Flow<PositionStreamItem> = callbackFlow {
        try {
            val sandbox = state.sandboxMode
            val snapshot = fetchPositionsRest(accountId, sandbox)
            for (pos in snapshot) trySend(convertToStreamItem(pos))
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось получить стартовый снапшот: ${e.message}")
        }

        while (isActive) {
            delay(10_000)
            try {
                val sandbox = state.sandboxMode
                val positions = fetchPositionsRest(accountId, sandbox)
                for (pos in positions) trySend(convertToStreamItem(pos))
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("NOT_FOUND")) {
                    Log.w(TAG, "Счёт $accountId не найден, останавливаю polling")
                    break
                }
                Log.w(TAG, "Polling error: $msg")
            }
        }

        awaitClose { }
    }

    private fun convertToStreamItem(pos: PortfolioPosition): PositionStreamItem =
        PositionStreamItem(
            instrumentUid = pos.tscalpInstrumentId,
            isin = pos.isin,
            ticker = pos.ticker,
            classCode = pos.classCode,
            quantity = pos.quantity,
            currentPrice = pos.currentPrice,
            averagePositionPrice = pos.averagePrice,
            expectedYield = pos.profit,
            instrumentType = pos.instrumentType,
            pointValue = pos.pointValue
        )

    // ---------- Баланс и пополнение ----------

    suspend fun getBalance(accountId: String): Double = withContext(Dispatchers.IO) {
        val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
        if (state.sandboxMode) {
            val request = PortfolioRequest.newBuilder().setAccountId(accountId).build()
            val portfolio = currentApi.sandboxServiceSync.getSandboxPortfolio(request)
            val totalRub = (portfolio.totalAmountCurrencies?.units ?: 0) +
                    (portfolio.totalAmountCurrencies?.nano ?: 0) / 1_000_000_000.0
            val positionsValue = portfolio.positionsList
                .filterNot { it.instrumentType == "currency" }
                .sumOf { pos ->
                    val price = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
                    val qty = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
                    price * qty
                }
            totalRub - positionsValue
        } else {
            val request = GetMarginAttributesRequest.newBuilder().setAccountId(accountId).build()
            val response = currentApi.usersServiceSync.getMarginAttributes(request)
            val money = response.liquidPortfolio
            (money?.units ?: 0) + (money?.nano ?: 0) / 1_000_000_000.0
        }
    }

    suspend fun sandboxPayIn(accountId: String, amount: SandboxMoney) = withContext(Dispatchers.IO) {
        val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
        val money = MoneyValue.newBuilder()
            .setCurrency(amount.currency).setUnits(amount.units).setNano(amount.nano).build()
        val request = SandboxPayInRequest.newBuilder()
            .setAccountId(accountId).setAmount(money).build()
        currentApi.sandboxServiceSync.sandboxPayIn(request)
    }

    // ---------- Инструменты ----------

    suspend fun fetchFullInstrument(uid: String): InstrumentUi? {
        val protoInstrument = fetchProtoInstrument(uid) ?: return null
        var marginAmount: Double? = null
        if (protoInstrument.instrumentType == "futures" && protoInstrument.figi != null) {
            marginAmount = getFuturesMargin(protoInstrument.figi)
        }
        return TInvestInstrumentMapper.mapProtoToDomain(protoInstrument, marginAmount)
    }

    private suspend fun fetchProtoInstrument(uid: String): Instrument? = withContext(Dispatchers.IO) {
        val currentApi = state.api ?: run {
            Log.w(TAG, "fetchProtoInstrument: API не инициализирован, uid=$uid")
            return@withContext null
        }
        val request = InstrumentRequest.newBuilder()
            .setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_UID)
            .setId(uid).build()
        currentApi.instrumentsServiceSync.getInstrumentBy(request).instrument
    }

    @Suppress("DEPRECATION")
    private suspend fun getFuturesMargin(figi: String): Double? = try {
        val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
        val request = GetFuturesMarginRequest.newBuilder().setFigi(figi).build()
        val response = currentApi.instrumentsServiceSync.getFuturesMargin(request)
        response.minPriceIncrementAmount?.let { it.units + it.nano / 1_000_000_000.0 }
    } catch (e: Exception) {
        Log.w(TAG, "Не удалось получить стоимость шага цены для $figi: ${e.message}")
        null
    }

    private suspend fun findInstrumentShorts(query: String): List<InstrumentShort> =
        withContext(Dispatchers.IO) {
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val request = FindInstrumentRequest.newBuilder().setQuery(query).build()
            currentApi.instrumentsServiceSync.findInstrument(request).instrumentsList
        }

    suspend fun findInstruments(query: String): List<InstrumentUi> = withContext(Dispatchers.IO) {
        findInstrumentShorts(query).mapNotNull { short ->
            try {
                val instrument = fetchProtoInstrument(short.uid) ?: return@mapNotNull null
                TInvestInstrumentMapper.mapProtoToDomain(instrument)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка получения инструмента по uid=${short.uid}", e)
                null
            }
        }
    }

    // ---------- Цены и статусы ----------

    suspend fun getLastPricesByTscalpInstrumentId(ids: List<String>): Map<String, Double?> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyMap()
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val request = GetLastPricesRequest.newBuilder().addAllInstrumentId(ids).build()
            val response = currentApi.marketDataServiceSync.getLastPrices(request)
            response.lastPricesList.associate { lp ->
                lp.instrumentUid to (lp.price?.let { it.units + it.nano / 1_000_000_000.0 })
            }
        }

    suspend fun getTradingStatuses(ids: List<String>): Map<String, TradingAvailability> =
        withContext(Dispatchers.IO) {
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val result = mutableMapOf<String, TradingAvailability>()
            val statuses = kotlinx.coroutines.coroutineScope {
                ids.map { uid ->
                    async {
                        try {
                            val request = GetTradingStatusRequest.newBuilder().setInstrumentId(uid).build()
                            val response = currentApi.marketDataServiceSync.getTradingStatus(request)
                            val available = response.apiTradeAvailableFlag
                            uid to if (available) TradingAvailability.AVAILABLE else TradingAvailability.UNAVAILABLE
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Статус для $uid недоступен: ${e.message}")
                            uid to TradingAvailability.UNKNOWN
                        }
                    }
                }.awaitAll().toMap()
            }
            result.putAll(statuses)
            result
        }

    // ---------- Стрим последних цен ----------

    fun subscribeLastPrices(uids: List<String>): Flow<Pair<String, Double>> = callbackFlow {
        val channel = state.channels?.pricesStream
            ?: throw IllegalStateException("gRPC-стрим не инициализирован")
        val stub = MarketDataStreamServiceGrpc.newStub(channel)

        val instruments = uids.map { LastPriceInstrument.newBuilder().setInstrumentId(it).build() }
        val subscribe = SubscribeLastPriceRequest.newBuilder()
            .setSubscriptionAction(SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
            .addAllInstruments(instruments).build()
        val serverSideRequest = MarketDataServerSideStreamRequest.newBuilder()
            .setSubscribeLastPriceRequest(subscribe).build()

        val responseObserver = object : StreamObserver<MarketDataResponse> {
            override fun onNext(value: MarketDataResponse) {
                if (value.hasLastPrice()) {
                    val lp = value.lastPrice
                    val price = lp.price?.let { it.units + it.nano / 1_000_000_000.0 }
                    if (price != null) trySend(lp.instrumentUid to price)
                }
            }
            override fun onError(t: Throwable) { close(t) }
            override fun onCompleted() { close() }
        }

        stub.marketDataServerSideStream(serverSideRequest, responseObserver)
        awaitClose { }
    }

    // ---------- Стрим статусов заявок ----------

    fun subscribeOrderState(accountId: String): Flow<OrderState> = callbackFlow {
        val channel = state.channels?.ordersState
            ?: throw IllegalStateException("Канал для OrderState не инициализирован")
        val stub = OrdersStreamServiceGrpc.newStub(channel)

        val request = OrderStateStreamRequest.newBuilder().addAccounts(accountId).build()

        val responseObserver = object : StreamObserver<OrderStateStreamResponse> {
            override fun onNext(value: OrderStateStreamResponse) {
                if (!value.hasOrderState()) return
                val s = value.orderState

                val figi = s.descriptorForType.findFieldByName("figi")
                    ?.let { s.getField(it) } as? String ?: ""
                val ticker = s.descriptorForType.findFieldByName("ticker")
                    ?.let { s.getField(it) } as? String ?: figi
                val date = (s.descriptorForType.findFieldByName("order_date")
                    ?: s.descriptorForType.findFieldByName("create_date"))
                    ?.let { s.getField(it) } as? com.google.protobuf.Timestamp
                val epochSeconds = date?.seconds

                val initPrice = s.descriptorForType.findFieldByName("initial_order_price")
                    ?.let { s.getField(it) } as? MoneyValue
                val execPrice = s.descriptorForType.findFieldByName("executed_order_price")
                    ?.let { s.getField(it) } as? MoneyValue

                trySend(
                    OrderState(
                        orderId = s.orderId,
                        orderRequestId = s.orderRequestId.ifBlank { null },
                        ticker = ticker,
                        direction = s.direction.name.removePrefix("ORDER_DIRECTION_"),
                        limitPrice = initPrice?.let { it.units + it.nano / 1_000_000_000.0 },
                        executedPrice = execPrice?.let { it.units + it.nano / 1_000_000_000.0 },
                        quantity = s.lotsRequested,
                        executedQuantity = s.lotsExecuted,
                        status = s.executionReportStatus.name.removePrefix("EXECUTION_REPORT_STATUS_"),
                        updateTime = epochSeconds
                    )
                )
            }
            override fun onError(t: Throwable) { close(t) }
            override fun onCompleted() { close() }
        }

        stub.orderStateStream(request, responseObserver)
        awaitClose { }
    }

    // ---------- Проверка доступности сделки ----------

    suspend fun checkTradeAvailability(
        accountId: String,
        uid: String,
        direction: OrderDirection,
        quantity: Long
    ): TradeCheckResult {
        val balance = getBalance(accountId)
        val lastPrice = getLastPricesByTscalpInstrumentId(listOf(uid))[uid]
            ?: return TradeCheckResult.Error("Цена не получена")
        val required = lastPrice * quantity
        return if (balance >= required) TradeCheckResult.Success
        else TradeCheckResult.Error(
            "Недостаточно средств. Свободно: ${formatCurrency(balance)}, требуется: ~${formatCurrency(required)}"
        )
    }
}
