package com.example.tscalp.data.api

import android.util.Log

import kotlinx.coroutines.CancellationException
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
import com.example.tscalp.domain.models.FutureUi
import com.example.tscalp.domain.models.ShareUi
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
import com.example.tscalp.domain.models.PositionStreamItem
import com.example.tscalp.util.formatCurrency

import ru.ttech.piapi.core.InvestApi
import ru.tinkoff.piapi.contract.v1.Instrument
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
import ru.tinkoff.piapi.contract.v1.OrdersStreamServiceGrpc
import ru.tinkoff.piapi.contract.v1.PositionsStreamRequest
import ru.tinkoff.piapi.contract.v1.PositionsStreamResponse



/**
 * Реализация BrokerApi для брокера Т‑Инвестиции (Kotlin SDK).
 * Хранит ticker→figi кэш, самостоятельно управляет своим экземпляром InvestApi.
 */
class TInvestInvestService : BrokerApi {

    companion object {
        private const val TAG = "TInvestInvestService"
    }

    // Кэш ticker → figi для быстрой конвертации
    //private val tickerToFigiCache = ConcurrentHashMap<String, String>()


    // Собственный экземпляр API, создаётся при инициализации
    @Volatile
    private var api: InvestApi? = null
    @Volatile
    private lateinit var grpcChannel: io.grpc.ManagedChannel
    @Volatile
    private lateinit var pricesStreamChannel: io.grpc.ManagedChannel
    @Volatile
    private lateinit var ordersStateChannel: io.grpc.ManagedChannel

    override val isInitialized: Boolean
        get() = api != null

    fun initializeFromSettings() {
        val token = ServiceLocator.getToken("TInvest") ?: return
        val sandbox = ServiceLocator.isSandboxMode()
        val target = if (sandbox) {
            "sandbox-invest-public-api.tbank.ru:443"
        } else {
            "invest-public-api.tbank.ru:443"
        }
        grpcChannel = InvestApi.defaultChannel(token, target)
        pricesStreamChannel = InvestApi.defaultChannel(token, target)
        ordersStateChannel = InvestApi.defaultChannel(token, target)
        api = InvestApi.createApi(grpcChannel)   // 👈 API привязан к основному каналу
        //tickerToFigiCache.clear()
    }

    // ---------- Базовые методы ----------
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
            val uid = pos.instrumentUid
            if (uid.isBlank()) return@mapNotNull null

            val instrument = getInstrumentByUid(uid)
            val quantity = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 }?.toLong() ?: 0L
            val currentPrice = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
            val totalValue = currentPrice * quantity
            val expectedYield = pos.expectedYield?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
            val avgPrice = pos.averagePositionPrice?.let { it.units + it.nano / 1_000_000_000.0 }

            // Используем expectedYield, если он положительный, иначе считаем сами
            val profit: Double? = if (expectedYield != null && expectedYield > 0.0) {
                expectedYield
            } else if (avgPrice != null && avgPrice > 0.0) {
                (currentPrice - avgPrice) * quantity
            } else null

            val profitPercent: Double? = when {
                profit != null && avgPrice != null && avgPrice > 0.0 && quantity > 0 -> {
                    (profit / (avgPrice * quantity)) * 100.0
                }
                else -> null
            }

            Log.d(TAG, "Позиция $uid: expectedYield=$expectedYield, avgPrice=$avgPrice")

            PortfolioPosition(
                tscalpInstrumentId = instrument?.uid ?: "",   // теперь uid
                name = instrument?.name ?: "",
                ticker = instrument?.ticker ?: "",
                quantity = quantity,
                currentPrice = currentPrice,
                averagePrice = avgPrice,
                totalValue = totalValue,
                profit = profit,
                profitPercent = profitPercent,
                instrumentType = instrument?.instrumentType ?: ""
            )
        }
    }


    override suspend fun getBalance(accountId: String): Double = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        if (ServiceLocator.isSandboxMode()) {
            val portfolioRequest = PortfolioRequest.newBuilder().setAccountId(accountId).build()
            val portfolio = currentApi.sandboxServiceSync.getSandboxPortfolio(portfolioRequest)

            val totalRub = (portfolio.totalAmountCurrencies?.units ?: 0) +
                    (portfolio.totalAmountCurrencies?.nano ?: 0) / 1_000_000_000.0

            // Вычитаем стоимость всех позиций в рублях
            val positionsValue = portfolio.positionsList
                .filterNot { it.instrumentType == "currency" }   // исключаем все валютные позиции (в песочнице это рубли)
                .sumOf { pos ->
                    val price = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
                    val qty = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
                    price * qty
                }

            val freeBalance = totalRub - positionsValue
            Log.d(TAG, "Свободный баланс песочницы: $freeBalance (общий: $totalRub, позиций: $positionsValue)")
            freeBalance
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


    // ---------- FIGI / Ticker ----------

    private suspend fun getInstrumentByUid(uid: String): Instrument? = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = InstrumentRequest.newBuilder()
            .setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_UID)
            .setId(uid)
            .build()
        val response = currentApi.instrumentsServiceSync.getInstrumentBy(request)
        response.instrument
    }

    /**
     * Преобразует protobuf‑объект Instrument в универсальный InstrumentUi.
     * tscalpInstrumentId заполняется из uid (рекомендованный идентификатор Т‑Инвестиций).
     * Для фьючерсов возвращает FutureUi, для акций – ShareUi, для остальных – базовый InstrumentUi.
     */
    private fun mapInstrumentToUi(instrument: Instrument): InstrumentUi {
        val uid = instrument.uid
        val figi = instrument.figi ?: ""
        val type = instrument.instrumentType ?: ""
        val minInc = instrument.minPriceIncrement?.let { it.units + it.nano / 1_000_000_000.0 }
        val tradingStatus = instrument.tradingStatus.name

        return when {
            type == "futures" -> FutureUi(
                tscalpInstrumentId = uid,
                ticker = instrument.ticker,
                classCode = instrument.classCode ?: "",
                isin = instrument.isin ?: "",
                ttech_uid = uid,
                ttech_figi = figi,
                name = instrument.name,
                currency = instrument.currency,
                lot = instrument.lot,
                exchange = instrument.exchange,
                tradingStatus = tradingStatus,
                apiTradeAvailableFlag = instrument.apiTradeAvailableFlag,
                buyAvailableFlag = instrument.buyAvailableFlag,
                sellAvailableFlag = instrument.sellAvailableFlag,
                shortEnabledFlag = instrument.shortEnabledFlag,
                minPriceIncrement = minInc,
                minPriceIncrementAmount = null, // SDK не предоставляет, оставим null
                klong = null, kshort = null, dlong = null, dshort = null,
                dlongMin = null, dshortMin = null,
                first1minCandleDate = null,
                first1dayCandleDate = null,
                forIisFlag = instrument.forIisFlag,
                forQualInvestorFlag = instrument.forQualInvestorFlag,
                weekendFlag = instrument.weekendFlag,
                blockedTcaFlag = instrument.blockedTcaFlag,
                countryOfRisk = instrument.countryOfRisk,
                countryOfRiskName = instrument.countryOfRiskName,
                sector = null, // отсутствует в SDK
                brand = null,
                requiredTests = null,
                expirationDate = null,
                firstTradeDate = null,
                lastTradeDate = null,
                futuresType = null, // можно попробовать instrument.futuresType, но его нет в текущем SDK
                assetType = null,
                basicAsset = null,                 // ← теперь null вместо instrument.basicAsset
                basicAssetSize = null,
                positionUid = instrument.positionUid,
                basicAssetPositionUid = null,      // ← теперь null вместо instrument.basicAssetPositionUid
                initialMarginOnBuy = null,
                initialMarginOnSell = null,
                dlongClient = null,
                dshortClient = null
            )
            type == "share" -> ShareUi(
                tscalpInstrumentId = uid,
                ticker = instrument.ticker,
                classCode = instrument.classCode ?: "",
                isin = instrument.isin ?: "",
                ttech_uid = uid,
                ttech_figi = figi,
                name = instrument.name,
                currency = instrument.currency,
                lot = instrument.lot,
                exchange = instrument.exchange,
                tradingStatus = tradingStatus,
                apiTradeAvailableFlag = instrument.apiTradeAvailableFlag,
                buyAvailableFlag = instrument.buyAvailableFlag,
                sellAvailableFlag = instrument.sellAvailableFlag,
                shortEnabledFlag = instrument.shortEnabledFlag,
                minPriceIncrement = minInc,
                minPriceIncrementAmount = null,
                klong = null, kshort = null, dlong = null, dshort = null,
                dlongMin = null, dshortMin = null,
                first1minCandleDate = null,
                first1dayCandleDate = null,
                forIisFlag = instrument.forIisFlag,
                forQualInvestorFlag = instrument.forQualInvestorFlag,
                weekendFlag = instrument.weekendFlag,
                blockedTcaFlag = instrument.blockedTcaFlag,
                countryOfRisk = instrument.countryOfRisk,
                countryOfRiskName = instrument.countryOfRiskName,
                sector = null,
                brand = null,
                requiredTests = null,
                ipoDate = null,
                issueSize = null,
                issueSizePlan = null,
                nominal = null,
                divYieldFlag = null,
                shareType = null,
                liquidityFlag = null,
                assetUid = null,
                instrumentExchange = null
            )
            else -> InstrumentUi(
                tscalpInstrumentId = uid,
                ticker = instrument.ticker,
                classCode = instrument.classCode ?: "",
                isin = instrument.isin ?: "",
                ttech_uid = uid,
                ttech_figi = figi,
                name = instrument.name,
                currency = instrument.currency,
                lot = instrument.lot,
                instrumentType = type
            )
        }
    }

    private suspend fun findInstrumentShorts(query: String): List<InstrumentShort> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = FindInstrumentRequest.newBuilder().setQuery(query).build()
        currentApi.instrumentsServiceSync.findInstrument(request).instrumentsList
    }


    override suspend fun findInstruments(query: String): List<InstrumentUi> = withContext(Dispatchers.IO) {
        val shorts = findInstrumentShorts(query)  // возвращает List<InstrumentShort> (содержит uid)
        shorts.mapNotNull { short ->
            try {
                val instrument = getInstrumentByUid(short.uid) ?: return@mapNotNull null
                mapInstrumentToUi(instrument)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка получения инструмента по uid=${short.uid}", e)
                null
            }
        }
    }


    // ---------- Orders ----------
    override suspend fun postOrder(request: BrokerOrderRequest): OrderResult = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")

        val uid = request.instrumentUid
        Log.d(TAG, "postOrder: ticker=${request.ticker}, uid=${uid ?: "null"}, " +
                "confirmMarginTrade=true, sandbox=${request.sandboxMode}")

        val price = if (request.type == BrokerOrderType.LIMIT && request.price != null) {
            val units = request.price.toLong()
            val nano = ((request.price - units) * 1_000_000_000).toInt()
            Quotation.newBuilder().setUnits(units).setNano(nano).build()
        } else {
            // Для рыночных заявок API требует ненулевое значение цены
            Quotation.newBuilder().setUnits(1).setNano(0).build()
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
            .setInstrumentId(uid!!)
            .setQuantity(request.quantity)
            .setPrice(price)
            .setDirection(apiDirection)
            .setAccountId(request.accountId)
            .setOrderType(apiOrderType)
            .setConfirmMarginTrade(true)
            .build()

        Log.d(TAG, "postOrder request fields: ${apiRequest.allFields}")

        val response = if (request.sandboxMode) {
            currentApi.sandboxServiceSync.postSandboxOrder(apiRequest)
        } else {
            currentApi.ordersServiceSync.postOrder(apiRequest)
        }
        Log.d(TAG, "postOrder response: $response")

        OrderResult(
            orderId = response.orderId,
            executedLots = response.lotsExecuted,
            totalLots = response.lotsRequested,
            status = OrderStatus.NEW,
            orderRequestId = response.orderRequestId
        )
    }

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
                // Извлекаем instrument_uid через дескриптор
                val uidField = order.descriptorForType.findFieldByName("instrument_uid")
                val uid = uidField?.let { order.getField(it) as? String } ?: order.figi
// Тикер берём напрямую из ответа API
                val tickerField = order.descriptorForType.findFieldByName("ticker")
                val ticker = tickerField?.let { order.getField(it) } as? String ?: uid

// Определяем instrumentType
                val classCodeField = order.descriptorForType.findFieldByName("class_code")
                val classCode = classCodeField?.let { order.getField(it) } as? String ?: ""
                val instrumentType = classCodeToInstrumentType(classCode)

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


                Log.d(TAG, "Order ${order.orderId} rawStatusValue=${statusField?.let { order.getField(it) }}, mapped=$statusStr")
                val orderIdStr: String = (order.orderId ?: "").toString()
                val figiStr: String = (order.figi ?: "").toString()


                val priceField = order.descriptorForType.findFieldByName("initial_order_price")
                    ?: order.descriptorForType.findFieldByName("price")
                val priceValue = priceField?.let { order.getField(it) }
                val priceDouble = when (priceValue) {
                    is MoneyValue -> priceValue.units + priceValue.nano / 1_000_000_000.0
                    is Quotation -> priceValue.units + priceValue.nano / 1_000_000_000.0
                    else -> 0.0
                }

                val dateField = order.descriptorForType.findFieldByName("create_date")
                val dateValue = dateField?.let { order.getField(it) }
                val orderDateLong = (dateValue as? com.google.protobuf.Timestamp)?.seconds

                OrderListItem(
                    orderId = orderIdStr,
                    ticker = ticker,
                    tscalpInstrumentId = uid,
                    instrumentType = instrumentType,
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



    // ---------- Stop Orders ----------
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

    override suspend fun postStopOrder(request: StopOrderRequest): String = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")

        val uid = request.instrumentUid
            ?: throw IllegalArgumentException("StopOrderRequest.instrumentUid не может быть null")

        val builder = PostStopOrderRequest.newBuilder()
            .setInstrumentId(uid)
            .setQuantity(request.quantity)
            .setDirection(protoDirection(request.direction))
            .setAccountId(request.accountId)
            .setStopPrice(quotationFromDouble(request.stopPrice))
            .setStopOrderType(protoStopOrderType(request.stopOrderType))
            .setExpirationType(protoExpirationType(request.expirationType))
            .setConfirmMarginTrade(true)
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

    override suspend fun getStopOrders(accountId: String): List<OrderListItem> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = GetStopOrdersRequest.newBuilder().setAccountId(accountId).build()
        val response = if (ServiceLocator.isSandboxMode()) {
            currentApi.sandboxServiceSync.getSandboxStopOrders(request)
        } else {
            currentApi.stopOrdersServiceSync.getStopOrders(request)
        }

        response.stopOrdersList.map { order ->
            // Извлекаем instrument_uid через дескриптор
            val uidField = order.descriptorForType.findFieldByName("instrument_uid")
            val uid = uidField?.let { order.getField(it) } as? String ?: order.figi
// Тикер берём напрямую из ответа API
            val tickerField = order.descriptorForType.findFieldByName("ticker")
            val ticker = tickerField?.let { order.getField(it) } as? String ?: uid

            // Определяем instrumentType
            val classCodeField = order.descriptorForType.findFieldByName("class_code")
            val classCode = classCodeField?.let { order.getField(it) } as? String ?: ""
            val instrumentType = classCodeToInstrumentType(classCode)

            // Тип стоп-заявки через дескриптор с явным кастом
            val fieldDescriptor = order.descriptorForType.findFieldByName("order_type")
            val type = if (fieldDescriptor != null) {
                val enumValue = order.getField(fieldDescriptor) as? com.google.protobuf.Descriptors.EnumValueDescriptor
                enumValue?.name?.removePrefix("STOP_ORDER_TYPE_") ?: "UNKNOWN"
            } else {
                "UNKNOWN"
            }


            // Явное приведение String (убирает String!)
            val orderIdStr = order.stopOrderId.toString()
            val figiStr = order.figi.toString()

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
                tscalpInstrumentId = uid,   // напрямую, без figi,
                direction = directionStr,
                price = stopPriceDouble,
                stopPrice = stopPriceDouble,
                quantity = order.lotsRequested,
                type = type,
                status = statusStr,
                orderDate = orderDateLong,
                isStopOrder = true,
                instrumentType = instrumentType
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





    override suspend fun getLastPricesByTscalpInstrumentId(ids: List<String>): Map<String, Double?> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyMap()
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = GetLastPricesRequest.newBuilder().addAllInstrumentId(ids).build()
        val response = currentApi.marketDataServiceSync.getLastPrices(request)
        response.lastPricesList.associate { lp ->
            lp.instrumentUid to (lp.price?.let { it.units + it.nano / 1_000_000_000.0 })
        }
    }

    fun subscribeLastPrices(uids: List<String>): Flow<Pair<String, Double>> = callbackFlow {
        if (!::pricesStreamChannel.isInitialized) {
            throw IllegalStateException("gRPC-стрим не инициализирован")
        }
        val stub = MarketDataStreamServiceGrpc.newStub(pricesStreamChannel)

        val instruments = uids.map { uid ->
            LastPriceInstrument.newBuilder().setInstrumentId(uid).build()
        }

        val subscribe = SubscribeLastPriceRequest.newBuilder()
            .setSubscriptionAction(SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
            .addAllInstruments(instruments)
            .build()

        val serverSideRequest = MarketDataServerSideStreamRequest.newBuilder()
            .setSubscribeLastPriceRequest(subscribe)
            .build()

        Log.d(TAG, "subscribeLastPrices via server-side StreamObserver for $uids")

        val responseObserver = object : StreamObserver<MarketDataResponse> {
            override fun onNext(value: MarketDataResponse) {
//                Log.d(TAG, "stream onNext: hasLastPrice=${value.hasLastPrice()}, " +
//                        "hasPing=${value.hasPing()}, " +
//                        "hasSubscribeLastPriceResponse=${value.hasSubscribeLastPriceResponse()}, " +
//                        "fields=${value.allFields.keys}")
                if (value.hasLastPrice()) {
                    val lp = value.lastPrice
                    //Log.d(TAG, "✅ lastPrice: figi=${lp.figi}, price=${lp.price}")
                    val price = lp.price?.let { it.units + it.nano / 1_000_000_000.0 }
                    if (price != null) trySend(lp.instrumentUid to price)
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
            ids.map { uid ->
                async {
                    try {
                        val request = GetTradingStatusRequest.newBuilder().setInstrumentId(uid).build()
                        val response = currentApi.marketDataServiceSync.getTradingStatus(request)
                        val available = response.apiTradeAvailableFlag
                        uid to if (available) TradingAvailability.AVAILABLE else TradingAvailability.UNAVAILABLE
                    } catch (e: CancellationException) {
                        throw e   // обязательно перебросить
                    } catch (e: Exception) {
                        Log.w(TAG, "Статус для $uid временно недоступен: ${e.message}")
                        uid to TradingAvailability.UNKNOWN
                    }
                }
            }.awaitAll().toMap()
        }
        result.putAll(statuses)
        result
    }

    override suspend fun subscribeOrderState(accountId: String): Flow<OrderState> = callbackFlow {
        if (!::ordersStateChannel.isInitialized) {
            throw IllegalStateException("Канал для OrderState не инициализирован")
        }
        val stub = OrdersStreamServiceGrpc.newStub(ordersStateChannel)

        val request = OrderStateStreamRequest.newBuilder()
            .addAccounts(accountId)
            .build()

        Log.d(TAG, "subscribeOrderState via StreamObserver for account $accountId")

        val responseObserver = object : StreamObserver<OrderStateStreamResponse> {
            override fun onNext(value: OrderStateStreamResponse) {
                if (value.hasOrderState()) {
                    val state = value.orderState

                    val figiField = state.descriptorForType.findFieldByName("figi")
                    val figi = figiField?.let { state.getField(it) } as? String ?: ""
                    // Извлекаем тикер напрямую из OrderState (есть в ответе API)
                    val tickerField = state.descriptorForType.findFieldByName("ticker")
                    val ticker = tickerField?.let { state.getField(it) } as? String ?: figi
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

            override fun onError(t: Throwable) {
                Log.e(TAG, "OrderState stream error", t)
                close(t)
            }

            override fun onCompleted() {
                Log.d(TAG, "OrderState stream completed")
                close()
            }
        }

        // Открываем серверный стрим – запрос передаётся сразу, requestObserver не нужен
        stub.orderStateStream(request, responseObserver)

        awaitClose {
            Log.d(TAG, "Closing OrderState stream")
            // gRPC сам завершит стрим при отмене
        }
    }

    override suspend fun checkTradeAvailability(
        accountId: String,
        tscalpInstrumentId: String,
        uid: String?,
        direction: OrderDirection,
        quantity: Long
    ): TradeCheckResult {
        val balance = getBalance(accountId)
        // Для оценки стоимости сделки берём последнюю цену (можно получить из кэша или запросить)
        val lastPrice = getLastPricesByTscalpInstrumentId(listOf(uid ?: return TradeCheckResult.Error("Нет uid")))[uid] ?: return TradeCheckResult.Error("Цена не получена")
        val required = lastPrice * quantity // комиссию пока не учитываем для простоты
        return if (balance >= required) TradeCheckResult.Success
        else TradeCheckResult.Error("Недостаточно средств. Свободно: ${formatCurrency(balance)}, требуется: ~${formatCurrency(required)}")
    }

    override suspend fun subscribePositionsStream(accountId: String): Flow<PositionStreamItem> = callbackFlow {
        if (!::ordersStateChannel.isInitialized) {
            throw IllegalStateException("Канал для PositionsStream не инициализирован")
        }

        val request = PositionsStreamRequest.newBuilder()
            .addAccounts(accountId)
            .build()

        // Дескриптор метода для PositionsStream
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<PositionsStreamRequest, PositionsStreamResponse>()
            .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
            .setFullMethodName("tinkoff.public.invest.api.contract.v1.OperationsStreamService/PositionsStream")
            .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(PositionsStreamRequest.getDefaultInstance()))
            .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(PositionsStreamResponse.getDefaultInstance()))
            .build()

        val call = ordersStateChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)

        val responseObserver = object : io.grpc.ClientCall.Listener<PositionsStreamResponse>() {
            override fun onMessage(response: PositionsStreamResponse) {
                if (response.hasPosition()) {
                    val pos = response.position

                    // Извлекаем поля через дескрипторы
                    val uidField = pos.descriptorForType.findFieldByName("instrument_uid")
                    val uid = uidField?.let { pos.getField(it) } as? String ?: ""

                    val tickerField = pos.descriptorForType.findFieldByName("ticker")
                    val ticker = tickerField?.let { pos.getField(it) } as? String ?: uid

                    val quantityField = pos.descriptorForType.findFieldByName("quantity")
                    val quantity = (quantityField?.let { pos.getField(it) } as? MoneyValue)?.let {
                        it.units + it.nano / 1_000_000_000.0
                    }?.toLong() ?: 0L

                    val currentPriceField = pos.descriptorForType.findFieldByName("current_price")
                    val currentPrice = (currentPriceField?.let { pos.getField(it) } as? MoneyValue)?.let {
                        it.units + it.nano / 1_000_000_000.0
                    }

                    val avgPriceField = pos.descriptorForType.findFieldByName("average_position_price")
                    val avgPrice = (avgPriceField?.let { pos.getField(it) } as? MoneyValue)?.let {
                        it.units + it.nano / 1_000_000_000.0
                    }

                    val yieldField = pos.descriptorForType.findFieldByName("expected_yield")
                    val yield = (yieldField?.let { pos.getField(it) } as? MoneyValue)?.let {
                        it.units + it.nano / 1_000_000_000.0
                    }

                    trySend(
                        PositionStreamItem(
                            instrumentUid = uid,
                            ticker = ticker,
                            quantity = quantity,
                            currentPrice = currentPrice,
                            averagePositionPrice = avgPrice,
                            expectedYield = yield
                        )
                    )
                }
            }

            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    Log.w(TAG, "PositionsStream not available (sandbox?): $status")
                }
                close()
            }
        }

        call.start(responseObserver, io.grpc.Metadata())
        call.sendMessage(request)
        call.halfClose()
        call.request(1)

        awaitClose {
            Log.d(TAG, "Closing PositionsStream")
            call.cancel("Cancelled by client", null)
        }
    }




///======================UTILS================================
    private fun classCodeToInstrumentType(classCode: String): String {
        return when (classCode) {
            "SPBFUT", "SPBOPT" -> "futures"   // срочный рынок
            "TQBR", "TQBS", "TQIF", "TQIR" -> "share"   // акции
            "TQOB", "TQCB", "TQRD" -> "bond"            // облигации
            "TQTF" -> "etf"                             // фонды
            "CETS" -> "currency"                        // валюта
            else -> ""                                   // неизвестный
        }
    }
}

