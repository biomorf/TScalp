package com.example.tscalp.data.api

import android.util.Log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import java.util.concurrent.ConcurrentHashMap

import io.grpc.stub.StreamObserver

import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.domain.api.BrokerApi
import com.example.tscalp.domain.models.InstrumentUi
import com.example.tscalp.domain.models.BrokerOrderType
import com.example.tscalp.domain.models.OrderDirection
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.domain.models.BrokerAccount
import com.example.tscalp.domain.models.BrokerAccountType
import com.example.tscalp.domain.models.SandboxMoney
import com.example.tscalp.domain.models.OrderResult
import com.example.tscalp.domain.models.OrderStatus
import com.example.tscalp.domain.models.StopOrderRequest
import com.example.tscalp.domain.models.StopOrderUi
import com.example.tscalp.domain.models.BrokerOrderRequest
import com.example.tscalp.domain.models.OrderListItem
import com.example.tscalp.domain.models.StopOrderType as DomainStopOrderType
import com.example.tscalp.domain.models.StopOrderExpirationType as DomainStopOrderExpirationType
import com.example.tscalp.domain.models.TradingAvailability
import com.example.tscalp.domain.models.TradeCheckResult
import com.example.tscalp.domain.models.OrderState
import com.example.tscalp.domain.models.TradingStatusDetails

import ru.ttech.piapi.core.InvestApi
import ru.tinkoff.piapi.contract.v1.GetAccountsRequest
import ru.tinkoff.piapi.contract.v1.OpenSandboxAccountRequest
import ru.tinkoff.piapi.contract.v1.CloseSandboxAccountRequest
import ru.tinkoff.piapi.contract.v1.Order
import ru.tinkoff.piapi.contract.v1.PostStopOrderRequest
import ru.tinkoff.piapi.contract.v1.StopOrderDirection
import ru.tinkoff.piapi.contract.v1.StopOrderType as ProtoStopOrderType
import ru.tinkoff.piapi.contract.v1.StopOrderExpirationType as ProtoStopOrderExpirationType
import ru.tinkoff.piapi.contract.v1.OrderType
import ru.tinkoff.piapi.contract.v1.PostOrderRequest
import ru.tinkoff.piapi.contract.v1.OrderDirection as ProtoOrderDirection
import ru.tinkoff.piapi.contract.v1.GetOrdersRequest
import ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus
import ru.tinkoff.piapi.contract.v1.CancelOrderRequest
import ru.tinkoff.piapi.contract.v1.GetStopOrdersRequest
import ru.tinkoff.piapi.contract.v1.CancelStopOrderRequest
import ru.tinkoff.piapi.contract.v1.Quotation
import ru.tinkoff.piapi.contract.v1.MoneyValue
import ru.tinkoff.piapi.contract.v1.FindInstrumentRequest
import ru.tinkoff.piapi.contract.v1.InstrumentRequest
import ru.tinkoff.piapi.contract.v1.InstrumentIdType
import ru.tinkoff.piapi.contract.v1.InstrumentResponse
import ru.tinkoff.piapi.contract.v1.InstrumentShort

import ru.tinkoff.piapi.contract.v1.PortfolioRequest
import ru.tinkoff.piapi.contract.v1.PortfolioResponse
import ru.tinkoff.piapi.contract.v1.GetLastPricesRequest
import ru.tinkoff.piapi.contract.v1.GetMarginAttributesRequest
import ru.tinkoff.piapi.contract.v1.SandboxPayInRequest
import ru.tinkoff.piapi.contract.v1.MarketDataRequest
import ru.tinkoff.piapi.contract.v1.MarketDataResponse
import ru.tinkoff.piapi.contract.v1.SubscribeLastPriceRequest
import ru.tinkoff.piapi.contract.v1.SubscriptionAction
import ru.tinkoff.piapi.contract.v1.LastPriceInstrument
import ru.tinkoff.piapi.contract.v1.MarketDataStreamServiceGrpc
import ru.tinkoff.piapi.contract.v1.MarketDataServerSideStreamRequest
import ru.tinkoff.piapi.contract.v1.GetTradingStatusRequest
import ru.tinkoff.piapi.contract.v1.OrderStateStreamRequest
import ru.tinkoff.piapi.contract.v1.OrderStateStreamResponse

/**
 * Реализация BrokerApi для брокера Т‑Инвестиции (Kotlin SDK).
 * Хранит ticker→figi кэш, самостоятельно управляет своим экземпляром InvestApi.
 */
class TInvestInvestService : BrokerApi {

    companion object {
        private const val TAG = "TInvestInvestService"
    }

    // Кэш ticker → figi для быстрой конвертации
    private val tickerToFigiCache = ConcurrentHashMap<String, String>()
    private val figiToTickerCache = ConcurrentHashMap<String, String>()

    // Собственный экземпляр API, создаётся при инициализации
    @Volatile
    private var api: InvestApi? = null
    @Volatile
    private lateinit var grpcChannel: io.grpc.ManagedChannel

    override val isInitialized: Boolean
        get() = api != null

    fun initializeFromSettings() {
        val token = ServiceLocator.getToken() ?: return
        val sandbox = ServiceLocator.isSandboxMode()
        val target = if (sandbox) {
            "sandbox-invest-public-api.tbank.ru:443"
        } else {
            "invest-public-api.tbank.ru:443"
        }
        grpcChannel = InvestApi.defaultChannel(token, target)   // <-- сохраняем
        api = InvestApi.createApi(grpcChannel)                  // <-- используем
        tickerToFigiCache.clear()
    }

    override suspend fun getAccounts(sandboxMode: Boolean): List<BrokerAccount> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = GetAccountsRequest.getDefaultInstance()
        val accounts = if (sandboxMode) {
            currentApi.sandboxServiceSync.getSandboxAccounts(request).accountsList
        } else {
            currentApi.usersServiceSync.getAccounts(request).accountsList
        }
        accounts.map { acc ->
            BrokerAccount(
                id = acc.id,
                name = acc.name,
                type = when (acc.typeValue) {
                    1 -> BrokerAccountType.BROKER
                    2 -> BrokerAccountType.IIS
                    3 -> BrokerAccountType.INVEST_BOX
                    else -> BrokerAccountType.OTHER
                }
            )
        }
    }

    override suspend fun openSandboxAccount(): String = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = ru.tinkoff.piapi.contract.v1.OpenSandboxAccountRequest.newBuilder().build()
        val response = currentApi.sandboxServiceSync.openSandboxAccount(request)
        response.accountId
    }

    override suspend fun closeSandboxAccount(accountId: String) {
        withContext(Dispatchers.IO) {
            val currentApi = api ?: throw IllegalStateException("API не инициализирован")
            val request = ru.tinkoff.piapi.contract.v1.CloseSandboxAccountRequest.newBuilder()
                .setAccountId(accountId)
                .build()
            currentApi.sandboxServiceSync.closeSandboxAccount(request)
            Log.d(TAG, "Счёт песочницы $accountId закрыт")
        }
    }

    suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = PortfolioRequest.newBuilder().setAccountId(accountId).build()
        return@withContext if (sandboxMode) {
            currentApi.sandboxServiceSync.getSandboxPortfolio(request)
        } else {
            currentApi.operationsServiceSync.getPortfolio(request)
        }
    }

    override suspend fun getPositions(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val response = getPortfolio(accountId, sandboxMode)

        response.positionsList.mapNotNull { pos ->
            val ticker = pos.ticker.ifBlank { resolveTicker(pos.figi) ?: pos.figi }
            if (ticker.isBlank()) return@mapNotNull null

            val instrument = getInstrumentByTicker(ticker)
            val quantity = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 }?.toLong() ?: 0L
            val currentPrice = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
            val totalValue = currentPrice * quantity

            PortfolioPosition(
                name = instrument?.name ?: "",
                ticker = ticker,
                tscalpInstrumentId = pos.figi,
                quantity = quantity,
                currentPrice = currentPrice,
                totalValue = totalValue,
                profit = 0.0,
                profitPercent = 0.0,
                instrumentType = instrument?.instrumentType ?: ""
            )
        }
    }


    override suspend fun getBalance(accountId: String): Double = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")

        if (ServiceLocator.isSandboxMode()) {
            val portfolioRequest = PortfolioRequest.newBuilder().setAccountId(accountId).build()
            val portfolio = currentApi.sandboxServiceSync.getSandboxPortfolio(portfolioRequest)
            val money = portfolio.totalAmountCurrencies
            val balance = (money?.units ?: 0) + (money?.nano ?: 0) / 1_000_000_000.0
            Log.d(TAG, "Баланс песочницы для счета $accountId: $balance")
            balance
        } else {
            val request = GetMarginAttributesRequest.newBuilder().setAccountId(accountId).build()
            val response = currentApi.usersServiceSync.getMarginAttributes(request)
            val money = response.liquidPortfolio
            val balance = (money?.units ?: 0) + (money?.nano ?: 0) / 1_000_000_000.0
            Log.d(TAG, "Баланс для счета $accountId: $balance")
            balance
        }
    }

    override suspend fun sandboxPayIn(accountId: String, amount: SandboxMoney) {
        withContext(Dispatchers.IO) {
            val currentApi = api ?: throw IllegalStateException("API не инициализирован")
            Log.d(TAG, "Отправка запроса на пополнение счета $accountId на сумму ${amount.units} ${amount.currency}")
            val money = MoneyValue.newBuilder()
                .setCurrency(amount.currency)
                .setUnits(amount.units)
                .setNano(amount.nano)
                .build()
            val request = SandboxPayInRequest.newBuilder()
                .setAccountId(accountId)
                .setAmount(money)
                .build()
            val response = currentApi.sandboxServiceSync.sandboxPayIn(request)
            Log.d(TAG, "Пополнение выполнено успешно, ответ: $response")
        }
    }


    override suspend fun resolveTicker(ticker: String): String? {
        tickerToFigiCache[ticker]?.let { return it }
        val shortList = findInstrumentShorts(ticker)
        val figi = shortList.firstOrNull { it.ticker.equals(ticker, ignoreCase = true) }?.figi
        if (figi != null) {
            tickerToFigiCache[ticker] = figi
            figiToTickerCache[figi] = ticker   // <-- заполняем обратный кэш
        }
        return figi
    }

    private fun getTickerByFigi(figi: String): String? = figiToTickerCache[figi]

    override suspend fun findInstruments(query: String): List<InstrumentUi> = withContext(Dispatchers.IO) {
        val shorts = findInstrumentShorts(query)
        shorts.mapNotNull { short ->
            val instrument = getInstrumentByTicker(short.ticker)
            if (instrument != null) {
                InstrumentUi(
                    ticker = instrument.ticker,
                    name = instrument.name,
                    currency = instrument.currency,
                    lot = instrument.lot,
                    instrumentType = instrument.instrumentType,
                    tscalpInstrumentId = short.figi
                )
            } else null
        }
    }

    override suspend fun getInstrumentByTicker(ticker: String): InstrumentUi? {
        val figi = resolveTicker(ticker) ?: return null
        val instrumentResponse = getInstrumentByFigi(figi)
        val inst = instrumentResponse.instrument
        return InstrumentUi(
            ticker = inst.ticker,
            tscalpInstrumentId = inst.figi,
            name = inst.name,
            currency = inst.currency,
            lot = inst.lot,
            instrumentType = inst.instrumentType ?: ""
        )
    }

    private suspend fun findInstrumentShorts(query: String): List<InstrumentShort> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = FindInstrumentRequest.newBuilder().setQuery(query).build()
        currentApi.instrumentsServiceSync.findInstrument(request).instrumentsList
    }

    private suspend fun getInstrumentByFigi(figi: String): InstrumentResponse = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = InstrumentRequest.newBuilder()
            .setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_FIGI)
            .setId(figi)
            .build()
        currentApi.instrumentsServiceSync.getInstrumentBy(request)
    }


    override suspend fun postOrder(request: BrokerOrderRequest): OrderResult = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val figi = resolveTicker(request.ticker) ?: throw IllegalArgumentException("Тикер ${request.ticker} не найден")

        val price = if (request.type == BrokerOrderType.LIMIT && request.price != null) {
            val units = request.price.toLong()
            val nano = ((request.price - units) * 1_000_000_000).toInt()
            Quotation.newBuilder().setUnits(units).setNano(nano).build()
        } else {
            Quotation.newBuilder().setUnits(0).setNano(0).build()
        }

        val apiOrderType = when (request.type) {
            BrokerOrderType.MARKET -> ru.tinkoff.piapi.contract.v1.OrderType.ORDER_TYPE_MARKET
            BrokerOrderType.LIMIT -> ru.tinkoff.piapi.contract.v1.OrderType.ORDER_TYPE_LIMIT
        }

        val apiDirection = when (request.direction) {
            OrderDirection.BUY -> ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_BUY
            OrderDirection.SELL -> ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_SELL
        }

        val apiRequest = PostOrderRequest.newBuilder()
            .setFigi(figi)
            .setQuantity(request.quantity)
            .setPrice(price)
            .setDirection(apiDirection)
            .setAccountId(request.accountId)
            .setOrderType(apiOrderType)
            .build()

        val response = if (request.sandboxMode) {
            currentApi.sandboxServiceSync.postSandboxOrder(apiRequest)
        } else {
            currentApi.ordersServiceSync.postOrder(apiRequest)
        }

        OrderResult(
            orderId = response.orderId,
            executedLots = response.lotsExecuted,
            totalLots = response.lotsRequested,
            status = OrderStatus.NEW,
            orderRequestId = response.orderRequestId
        )
    }

    // --- stop orders ---

    private fun protoDirection(direction: OrderDirection): ru.tinkoff.piapi.contract.v1.StopOrderDirection = when (direction) {
        OrderDirection.BUY -> ru.tinkoff.piapi.contract.v1.StopOrderDirection.STOP_ORDER_DIRECTION_BUY
        OrderDirection.SELL -> ru.tinkoff.piapi.contract.v1.StopOrderDirection.STOP_ORDER_DIRECTION_SELL
    }

    private fun protoStopOrderType(type: DomainStopOrderType): ru.tinkoff.piapi.contract.v1.StopOrderType = when (type) {
        DomainStopOrderType.TAKE_PROFIT -> ProtoStopOrderType.STOP_ORDER_TYPE_TAKE_PROFIT
        DomainStopOrderType.STOP_LOSS -> ProtoStopOrderType.STOP_ORDER_TYPE_STOP_LOSS
        DomainStopOrderType.STOP_LIMIT -> ProtoStopOrderType.STOP_ORDER_TYPE_STOP_LIMIT
    }

    private fun protoExpirationType(expiration: DomainStopOrderExpirationType): ru.tinkoff.piapi.contract.v1.StopOrderExpirationType = when (expiration) {
        DomainStopOrderExpirationType.GOOD_TILL_CANCEL -> ProtoStopOrderExpirationType.STOP_ORDER_EXPIRATION_TYPE_GOOD_TILL_CANCEL
        DomainStopOrderExpirationType.GOOD_TILL_DATE -> ProtoStopOrderExpirationType.STOP_ORDER_EXPIRATION_TYPE_GOOD_TILL_DATE
    }

    private fun quotationFromDouble(value: Double): Quotation {
        val units = value.toLong()
        val nano = ((value - units) * 1_000_000_000).toInt()
        return Quotation.newBuilder().setUnits(units).setNano(nano).build()
    }

    private fun moneyToDouble(money: MoneyValue?): Double {
        if (money == null) return 0.0
        return money.units + money.nano / 1_000_000_000.0
    }

    override suspend fun postStopOrder(request: StopOrderRequest): String = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val figi = resolveTicker(request.ticker) ?: throw IllegalArgumentException("Тикер ${request.ticker} не найден")

        val builder = PostStopOrderRequest.newBuilder()
            .setFigi(figi)
            .setQuantity(request.quantity)
            .setDirection(protoDirection(request.direction))
            .setAccountId(request.accountId)
            .setStopPrice(quotationFromDouble(request.stopPrice))
            .setStopOrderType(protoStopOrderType(request.stopOrderType))
            .setExpirationType(protoExpirationType(request.expirationType))
        if (request.price != null) builder.setPrice(quotationFromDouble(request.price))
        if (request.expireDate != null) builder.setExpireDate(parseDate(request.expireDate))

        val protoRequest = builder.build()
        val response = if (ServiceLocator.isSandboxMode()) {
            currentApi.sandboxServiceSync.postSandboxStopOrder(protoRequest)
        } else {
            currentApi.stopOrdersServiceSync.postStopOrder(protoRequest)
        }
        response.stopOrderId
    }
//////////!!!!!!!!!!!!!!!!!11111111111!!!!!!!!!!!!!!
    override suspend fun getStopOrders(accountId: String): List<OrderListItem> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = GetStopOrdersRequest.newBuilder().setAccountId(accountId).build()
        val response = if (ServiceLocator.isSandboxMode()) {
            currentApi.sandboxServiceSync.getSandboxStopOrders(request)
        } else {
            currentApi.stopOrdersServiceSync.getStopOrders(request)
        }

        response.stopOrdersList.map { order ->
            val ticker = resolveTicker(order.figi) ?: order.figi

            // Тип стоп-заявки через дескриптор с явным кастом
            val fieldDescriptor = order.descriptorForType.findFieldByName("order_type")
            val type = if (fieldDescriptor != null) {
                val enumValue = order.getField(fieldDescriptor) as? com.google.protobuf.Descriptors.EnumValueDescriptor
                enumValue?.name?.removePrefix("STOP_ORDER_TYPE_") ?: "UNKNOWN"
            } else {
                "UNKNOWN"
            }

            // Явное приведение String (убирает String!)
            val orderIdStr: String = order.stopOrderId as String
            val figiStr: String = order.figi as String

            // Направление и статус через enum (избавляемся от String!)
            val directionStr: String = (order.direction as Enum<*>).name.removePrefix("STOP_ORDER_DIRECTION_")
            val statusStr: String = (order.status as Enum<*>).name.removePrefix("STOP_ORDER_STATUS_")

            // Явное извлечение MoneyValue с объявлением типа
            val sp: MoneyValue? = order.stopPrice
            val stopPriceDouble = sp?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0

            val orderDateLong = order.getCreateDate()?.seconds

            OrderListItem(
                orderId = orderIdStr,
                ticker = ticker,
                tscalpInstrumentId = figiStr,
                direction = directionStr,
                price = stopPriceDouble,
                stopPrice = stopPriceDouble,
                quantity = order.lotsRequested,
                type = type,
                status = statusStr,
                orderDate = orderDateLong,
                isStopOrder = true
            )
        }
    }

    override suspend fun cancelStopOrder(accountId: String, stopOrderId: String) {
        withContext(Dispatchers.IO) {
            val currentApi = api ?: throw IllegalStateException("API не инициализирован")
            val request = CancelStopOrderRequest.newBuilder()
                .setAccountId(accountId)
                .setStopOrderId(stopOrderId)
                .build()
            if (ServiceLocator.isSandboxMode()) {
                currentApi.sandboxServiceSync.cancelSandboxStopOrder(request)
            } else {
                currentApi.stopOrdersServiceSync.cancelStopOrder(request)
            }
        }
    }

    private fun parseDate(dateStr: String): com.google.protobuf.Timestamp {
        // Упрощённый парсинг (реализуйте по необходимости)
        return com.google.protobuf.Timestamp.newBuilder().build()
    }


/////////////!!!!!!!1111111111111!1!!!!!!!!!!
override suspend fun getOrders(accountId: String): List<OrderListItem> = withContext(Dispatchers.IO) {
    val currentApi = api ?: throw IllegalStateException("API не инициализирован")
    val request = GetOrdersRequest.newBuilder()
        .setAccountId(accountId)
        .build()
    val response = if (ServiceLocator.isSandboxMode()) {
        currentApi.sandboxServiceSync.getSandboxOrders(request)
    } else {
        currentApi.ordersServiceSync.getOrders(request)
    }

    val activeStatuses = setOf(
        OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_NEW,
        OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_PARTIALLYFILL
    )

    response.ordersList
        .filter { it.executionReportStatus in activeStatuses }
        .map { order ->
            val ticker = resolveTicker(order.figi) ?: order.figi

            val orderType = when (order.orderType) {
                OrderType.ORDER_TYPE_LIMIT -> "LIMIT"
                OrderType.ORDER_TYPE_MARKET -> "MARKET"
                else -> "UNKNOWN"
            }

            val direction = when (order.directionValue) {
                1 -> "BUY"
                2 -> "SELL"
                else -> "UNKNOWN"
            }

            // --- Извлечение статуса через дескриптор ---
            val statusField = order.descriptorForType.findFieldByName("execution_report_status")
            val statusStr: String = if (statusField != null) {
                val rawStatus = order.getField(statusField)
                if (rawStatus is com.google.protobuf.Descriptors.EnumValueDescriptor) {
                    rawStatus.name.removePrefix("EXECUTION_REPORT_STATUS_")
                } else {
                    "UNKNOWN"
                }
            } else {
                "UNKNOWN"
            }
            Log.d(TAG, "Order ${order.orderId} rawStatusValue=${statusField?.let{order.getField(it)}}, mapped=$statusStr")

            // Строки
            val orderIdStr: String = (order.orderId ?: "").toString()
            val figiStr: String = (order.figi ?: "").toString()

            // Цена через дескриптор
            val priceField = order.descriptorForType.findFieldByName("initial_order_price")
                ?: order.descriptorForType.findFieldByName("price")
            val priceValue = priceField?.let { order.getField(it) }
            val priceDouble = when (priceValue) {
                is MoneyValue -> priceValue.units + priceValue.nano / 1_000_000_000.0
                is Quotation -> priceValue.units + priceValue.nano / 1_000_000_000.0
                else -> 0.0
            }

            // Дата через дескриптор
            val dateField = order.descriptorForType.findFieldByName("create_date")
            val dateValue = dateField?.let { order.getField(it) }
            val orderDateLong = (dateValue as? com.google.protobuf.Timestamp)?.seconds

            OrderListItem(
                orderId = orderIdStr,
                ticker = ticker,
                tscalpInstrumentId = figiStr,
                direction = direction,
                price = priceDouble,
                stopPrice = null,
                quantity = order.lotsRequested,
                type = orderType,
                status = statusStr,
                orderDate = orderDateLong,
                isStopOrder = false
            )
        }
}

    override suspend fun cancelOrder(accountId: String, orderId: String) {
        withContext(Dispatchers.IO) {
            val currentApi = api ?: throw IllegalStateException("API не инициализирован")
            val request = CancelOrderRequest.newBuilder()
                .setAccountId(accountId)
                .setOrderId(orderId)
                .build()
            if (ServiceLocator.isSandboxMode()) {
                currentApi.sandboxServiceSync.cancelSandboxOrder(request)
            } else {
                currentApi.ordersServiceSync.cancelOrder(request)
            }
        }
    }

    override suspend fun getLastPricesByTicker(tickers: List<String>): Map<String, Double?> = withContext(Dispatchers.IO) {
        if (tickers.isEmpty()) return@withContext emptyMap()
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")

        val tickerToFigi = mutableMapOf<String, String>()
        for (ticker in tickers) {
            resolveTicker(ticker)?.let { tickerToFigi[ticker] = it }
        }
        if (tickerToFigi.isEmpty()) return@withContext emptyMap()

        val figis = tickerToFigi.values.toList()
        val request = GetLastPricesRequest.newBuilder().addAllFigi(figis).build()
        val response = currentApi.marketDataServiceSync.getLastPrices(request)

        val result = mutableMapOf<String, Double?>()
        for (lastPrice in response.lastPricesList) {
            val originalTicker = tickerToFigi.entries.find { it.value == lastPrice.figi }?.key
            if (originalTicker != null) {
                val price = lastPrice.price?.let { it.units + it.nano / 1_000_000_000.0 }
                result[originalTicker] = price
            }
        }
        result
    }


    fun subscribeLastPrices(figis: List<String>): Flow<Pair<String, Double>> = callbackFlow {
        if (!::grpcChannel.isInitialized) {
            throw IllegalStateException("gRPC-канал не инициализирован")
        }
        val stub = MarketDataStreamServiceGrpc.newStub(grpcChannel)

        val instruments = figis.map { figi ->
            LastPriceInstrument.newBuilder().setFigi(figi).build()
        }

        val subscribe = SubscribeLastPriceRequest.newBuilder()
            .setSubscriptionAction(SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
            .addAllInstruments(instruments)
            .build()

        val serverSideRequest = MarketDataServerSideStreamRequest.newBuilder()
            .setSubscribeLastPriceRequest(subscribe)
            .build()

        Log.d(TAG, "subscribeLastPrices via server-side StreamObserver for $figis")

        val responseObserver = object : StreamObserver<MarketDataResponse> {
            override fun onNext(value: MarketDataResponse) {
                Log.d(TAG, "stream onNext: hasLastPrice=${value.hasLastPrice()}, " +
                        "hasPing=${value.hasPing()}, " +
                        "hasSubscribeLastPriceResponse=${value.hasSubscribeLastPriceResponse()}, " +
                        "fields=${value.allFields.keys}")
                if (value.hasLastPrice()) {
                    val lp = value.lastPrice
                    Log.d(TAG, "✅ lastPrice: figi=${lp.figi}, price=${lp.price}")
                    val price = lp.price?.let { it.units + it.nano / 1_000_000_000.0 }
                    if (price != null) trySend(lp.figi to price)
                }
            }

            override fun onError(t: Throwable) {
                Log.e(TAG, "stream error", t)
                close(t)
            }

            override fun onCompleted() {
                Log.d(TAG, "stream completed")
                close()
            }
        }

        // Правильный вызов: передаём запрос и observer ответа
        stub.marketDataServerSideStream(serverSideRequest, responseObserver)

        awaitClose {
            Log.d(TAG, "closing server-side stream")
            // gRPC сам завершит стрим при отмене
        }
    }

    override suspend fun getTradingStatuses(ids: List<String>): Map<String, TradingAvailability> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val result = mutableMapOf<String, TradingAvailability>()

        // Параллельные запросы с ограничением
        val statuses = kotlinx.coroutines.coroutineScope {
            ids.map { figi ->
                async {
                    try {
                        val request = GetTradingStatusRequest.newBuilder().setFigi(figi).build()
                        val response = currentApi.marketDataServiceSync.getTradingStatus(request)
                        val available = response.apiTradeAvailableFlag
                        figi to if (available) TradingAvailability.AVAILABLE else TradingAvailability.UNAVAILABLE
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка статуса $figi: ${e.message}")
                        figi to TradingAvailability.UNKNOWN
                    }
                }
            }.awaitAll().toMap()
        }
        result.putAll(statuses)
        result
    }

    override suspend fun subscribeOrderState(accountId: String): Flow<OrderState> = callbackFlow {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = OrderStateStreamRequest.newBuilder()
            .addAccounts(accountId)
            .build()

        val job = currentApi.ordersStreamServiceAsync.orderStateStream(
            request,
            { /* статус подписки – можно оставить пустым */ },
            { response: OrderStateStreamResponse ->
                Log.d(TAG, "OrderStateStream received response: ${response.allFields}")
                if (response.hasOrderState()) {
                    val state = response.orderState

                    val figiField = state.descriptorForType.findFieldByName("figi")
                    val figi = figiField?.let { state.getField(it) } as? String ?: ""
                    val ticker = getTickerByFigi(figi) ?: figi

                    val dateField = state.descriptorForType.findFieldByName("order_date")
                        ?: state.descriptorForType.findFieldByName("create_date")
                    val updateTime = dateField?.let { state.getField(it) } as? com.google.protobuf.Timestamp
                    val epochSeconds = updateTime?.seconds

                    val initPriceField = state.descriptorForType.findFieldByName("initial_order_price")
                    val execPriceField = state.descriptorForType.findFieldByName("executed_order_price")
                    val initPrice = initPriceField?.let { state.getField(it) } as? MoneyValue
                    val execPrice = execPriceField?.let { state.getField(it) } as? MoneyValue

                    val initPriceDouble = initPrice?.let { it.units + it.nano / 1_000_000_000.0 }
                    val execPriceDouble = execPrice?.let { it.units + it.nano / 1_000_000_000.0 }

                    trySend(
                        OrderState(
                            orderId = state.orderId,
                            orderRequestId = state.orderRequestId.ifBlank { null },
                            ticker = ticker,
                            direction = state.direction.name.removePrefix("ORDER_DIRECTION_"),
                            limitPrice = initPriceDouble,
                            executedPrice = execPriceDouble,
                            quantity = state.lotsRequested,
                            executedQuantity = state.lotsExecuted,
                            status = state.executionReportStatus.name.removePrefix("EXECUTION_REPORT_STATUS_"),
                            updateTime = epochSeconds
                        )
                    )
                }
            }
        )
        awaitClose { job.cancel() }
    }

    override suspend fun checkTradeAvailability(
        accountId: String,
        tscalpInstrumentId: String,
        direction: OrderDirection,
        quantity: Long
    ): TradeCheckResult = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")

        // 1. Получаем figi по tscalpInstrumentId
        val figi = resolveTicker(tscalpInstrumentId) ?: return@withContext TradeCheckResult.Error("Инструмент не найден")

        // 2. Получаем торговый статус инструмента
        val tradingStatusRequest = GetTradingStatusRequest.newBuilder().setFigi(figi).build()
        val tradingStatusResponse = currentApi.marketDataServiceSync.getTradingStatus(tradingStatusRequest)

        // Проверка apiTradeAvailableFlag
        if (!tradingStatusResponse.apiTradeAvailableFlag) {
            return@withContext TradeCheckResult.Error("Инструмент недоступен для торговли через API")
        }

        // Проверка торговой сессии
        val statusField = tradingStatusResponse.descriptorForType.findFieldByName("trading_status")
        val tradingStatus = statusField?.let { tradingStatusResponse.getField(it) }?.toString() ?: "UNKNOWN"
        if (tradingStatus !in listOf("SECURITY_TRADING_STATUS_NORMAL_TRADING", "SECURITY_TRADING_STATUS_DEALER_NORMAL_TRADING")) {
            return@withContext TradeCheckResult.Error("Торги по инструменту приостановлены (статус: $tradingStatus)")
        }

        // Проверка флагов покупки/продажи
        val buyAvailableField = tradingStatusResponse.descriptorForType.findFieldByName("buy_available_flag")
        val sellAvailableField = tradingStatusResponse.descriptorForType.findFieldByName("sell_available_flag")
        val buyAvailable = (buyAvailableField?.let { tradingStatusResponse.getField(it) } as? Boolean) ?: false
        val sellAvailable = (sellAvailableField?.let { tradingStatusResponse.getField(it) } as? Boolean) ?: false

        if (direction == OrderDirection.BUY && !buyAvailable) {
            return@withContext TradeCheckResult.Error("Покупка недоступна")
        }
        if (direction == OrderDirection.SELL && !sellAvailable) {
            return@withContext TradeCheckResult.Error("Продажа недоступна")
        }

        // 3. Маржинальная проверка
        try {
            val marginRequest = GetMarginAttributesRequest.newBuilder().setAccountId(accountId).build()
            val marginResponse = currentApi.usersServiceSync.getMarginAttributes(marginRequest)

            val buyMarginField = marginResponse.descriptorForType.findFieldByName("buy_margin_available")
            val sellMarginField = marginResponse.descriptorForType.findFieldByName("sell_margin_available")
            val missingFundsField = marginResponse.descriptorForType.findFieldByName("amount_of_missing_funds")

            val buyMarginAvailable = (buyMarginField?.let { marginResponse.getField(it) } as? Boolean) ?: true
            val sellMarginAvailable = (sellMarginField?.let { marginResponse.getField(it) } as? Boolean) ?: true
            val missingFunds = (missingFundsField?.let { marginResponse.getField(it) } as? MoneyValue)?.let {
                it.units + it.nano / 1_000_000_000.0
            } ?: 0.0

            if (direction == OrderDirection.BUY && !buyMarginAvailable) {
                return@withContext TradeCheckResult.Error("Покупка недоступна (не подключена маржинальная торговля)")
            }
            if (direction == OrderDirection.SELL && !sellMarginAvailable) {
                return@withContext TradeCheckResult.Error("Продажа недоступна (не подключена маржинальная торговля)")
            }
            if (missingFunds > 0) {
                return@withContext TradeCheckResult.Error("Недостаточно средств для обеспечения заявки (необходимо: ${"%.2f".format(missingFunds)} ₽)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения маржинальных атрибутов", e)
            return@withContext TradeCheckResult.Error("Не удалось проверить маржинальную доступность")
        }

        TradeCheckResult.Success
    }
}

