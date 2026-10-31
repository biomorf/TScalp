package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderState
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.GetLastPricesRequest
import ru.tinkoff.piapi.contract.v1.GetTradingStatusRequest
import ru.tinkoff.piapi.contract.v1.LastPriceInstrument
import ru.tinkoff.piapi.contract.v1.MarketDataResponse
import ru.tinkoff.piapi.contract.v1.MarketDataServerSideStreamRequest
import ru.tinkoff.piapi.contract.v1.MarketDataStreamServiceGrpc
import ru.tinkoff.piapi.contract.v1.OrderStateStreamRequest
import ru.tinkoff.piapi.contract.v1.OrderStateStreamResponse
import ru.tinkoff.piapi.contract.v1.OrdersStreamServiceGrpc
import ru.tinkoff.piapi.contract.v1.PositionsFutures
import ru.tinkoff.piapi.contract.v1.PositionsSecurities
import ru.tinkoff.piapi.contract.v1.PositionsStreamRequest
import ru.tinkoff.piapi.contract.v1.PositionsStreamResponse
import ru.tinkoff.piapi.contract.v1.SubscribeLastPriceRequest
import ru.tinkoff.piapi.contract.v1.SubscriptionAction

/**
 * Маркет-данные, портфель, стримы и счета T-Invest.
 * Работает на разделяемом TInvestApiState.
 * // Архитектурный долг: 19 функций в классе. См. ROADMAP → «Рефакторинг data-слоя T-Invest».
 */
@Suppress("TooManyFunctions")
class TInvestMarketDataService(
    private val state: TInvestApiState,
    private val logger: Logger,
    private val accountsService: TInvestAccountsService,
) {
    companion object {
        private const val TAG = "TInvestMarketData"

        /** Интервал polling позиций. */
        private const val RETRY_DELAY_MS = 10_000L

        /**
         * Экспоненциальный backoff для переподключения gRPC-стрима.
         * Cap 60 секунд — по аналогии с [PriceFlowManager].
         */
        private val GRPC_RETRY_DELAYS_MS = longArrayOf(1_000, 5_000, 15_000, 30_000, 60_000)
    }

    // ---------- Счета ----------

    // ---------- Стрим позиций (gRPC) ----------

    /**
     * gRPC-стрим изменений позиций через
     * `OperationsStreamServiceAsync.positionsStream`.
     *
     * Возвращает `null`, если API не инициализирован.
     *
     * Даёт только изменения количества и идентификаторы.
     * Поля `currentPrice`, `averagePositionPrice`, `expectedYield`,
     * `pointValue` в стриме отсутствуют. Обогащение P&L —
     * задача `PositionFlowManager` (см. `docs/streams.md`).
     *
     * `withInitialPositions = true` — сервер пришлёт снапшот
     * позиций сразу при подключении, отдельный REST-запрос
     * не нужен.
     */
    fun subscribePositionsGrpc(accountId: String): Flow<PositionStreamItem>? {
        val api = state.api ?: return null
        return callbackFlow {
            val request =
                PositionsStreamRequest
                    .newBuilder()
                    .addAccounts(accountId)
                    .setWithInitialPositions(true)
                    .build()

            val streamJob =
                launch {
                    try {
                        api.operationsStreamServiceAsync.positionsStream(
                            request,
                            { response: PositionsStreamResponse ->
                                logger.d(
                                    TAG,
                                    "[stream/gRPC/TInvest]: response — " +
                                        "subs=${response.hasSubscriptions()}, " +
                                        "init=${response.hasInitialPositions()}, " +
                                        "pos=${response.hasPosition()}, " +
                                        "ping=${response.hasPing()}, " +
                                        "case=${response.payloadCase}",
                                )
                                if (response.hasInitialPositions()) {
                                    val snapshot = response.initialPositions
                                    snapshot.securitiesList.forEach { trySend(convertSecurity(it)) }
                                    snapshot.futuresList.forEach { trySend(convertFuture(it)) }
                                }
                                if (response.hasPosition()) {
                                    val data = response.position
                                    data.securitiesList.forEach { trySend(convertSecurity(it)) }
                                    data.futuresList.forEach { trySend(convertFuture(it)) }
                                }
                                // hasPing / hasSubscriptions — служебные, игнорируем.
                            },
                        )
                    } catch (e: Exception) {
                        close(e)
                    }
                }

            awaitClose { streamJob.cancel() }
        }
    }

    private fun convertSecurity(s: PositionsSecurities): PositionStreamItem =
        PositionStreamItem(
            tscalpInstrumentId = s.instrumentUid,
            brokerName = BrokerName.TINVEST,
            ticker = s.ticker,
            quantity = s.balance,
            classCode = s.classCode,
            instrumentType = s.instrumentType.ifBlank { "share" },
        )

    private fun convertFuture(f: PositionsFutures): PositionStreamItem =
        PositionStreamItem(
            tscalpInstrumentId = f.instrumentUid,
            brokerName = BrokerName.TINVEST,
            ticker = f.ticker,
            quantity = f.balance,
            classCode = f.classCode,
            instrumentType = "futures",
        )

    // ---------- Стрим позиций (параллельно) ----------

    /**
     * gRPC-стрим с автопереподключением.
     *
     * Возвращает `null`, если API не инициализирован.
     * Переподключается с экспоненциальным backoff
     * [GRPC_RETRY_DELAYS_MS] при ошибках. Штатное завершение
     * сервером → повтор без задержки. NOT_FOUND → завершение
     * потока без переподключения.
     */
    internal fun subscribePositionsGrpcWithReconnect(accountId: String): Flow<PositionStreamItem> =
        flow {
            var attempt = 0
            while (currentCoroutineContext().isActive) {
                val grpcFlow = subscribePositionsGrpc(accountId)
                if (grpcFlow == null) {
                    logger.d(TAG, "[stream/gRPC/TInvest]: waiting for API init")
                    delay(1_000)
                    continue
                }
                try {
                    logger.d(TAG, "[stream/gRPC/TInvest]: connecting (attempt=${attempt + 1})")
                    grpcFlow.collect { emit(it) }
                    attempt = 0
                    logger.d(TAG, "[stream/gRPC/TInvest]: completed by server, reconnecting")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e.message?.contains("NOT_FOUND") == true) {
                        logger.w(TAG, "[stream/gRPC/TInvest]: fatal (${e.message}) — stopping")
                        return@flow
                    }
                    attempt++
                    val delayMs = retryDelayMs(attempt)
                    logger.w(
                        TAG,
                        "[stream/gRPC/TInvest]: error (attempt=$attempt, ${e.message}), retry in ${delayMs}ms",
                    )
                    delay(delayMs)
                }
            }
        }

    private fun retryDelayMs(attempt: Int): Long {
        val idx = (attempt - 1).coerceIn(0, GRPC_RETRY_DELAYS_MS.lastIndex)
        return GRPC_RETRY_DELAYS_MS[idx]
    }

    // ---------- Цены и статусы ----------

    suspend fun getLastPricesByTscalpInstrumentId(uids: List<String>): Map<String, Double?> =
        withContext(Dispatchers.IO) {
            if (uids.isEmpty()) return@withContext emptyMap()
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val request = GetLastPricesRequest.newBuilder().addAllInstrumentId(uids).build()
            logger.d(TAG, "[unary/gRPC/TInvest] GetLastPrices for ${uids.size} instruments")
            val response = currentApi.marketDataServiceSync.getLastPrices(request)
            response.lastPricesList.associate { lp ->
                lp.instrumentUid to (lp.price?.let { it.units + it.nano / 1_000_000_000.0 })
            }
        }

    suspend fun getTradingStatuses(uids: List<String>): Map<String, TradingAvailability> =
        withContext(Dispatchers.IO) {
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val result = mutableMapOf<String, TradingAvailability>()
            val statuses =
                kotlinx.coroutines.coroutineScope {
                    uids
                        .map { uid ->
                            async {
                                try {
                                    val request = GetTradingStatusRequest.newBuilder().setInstrumentId(uid).build()
                                    val response = currentApi.marketDataServiceSync.getTradingStatus(request)
                                    val available = response.apiTradeAvailableFlag
                                    uid to if (available) TradingAvailability.AVAILABLE else TradingAvailability.UNAVAILABLE
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    logger.w(TAG, "Статус для $uid недоступен: ${e.message}")
                                    uid to TradingAvailability.UNKNOWN
                                }
                            }
                        }.awaitAll()
                        .toMap()
                }
            result.putAll(statuses)
            result
        }

    // ---------- Стрим последних цен ----------

    fun subscribeLastPrices(uids: List<String>): Flow<Pair<String, Double>> =
        callbackFlow {
            val channel =
                state.channels?.pricesStream
                    ?: throw IllegalStateException("gRPC-стрим не инициализирован")
            val stub = MarketDataStreamServiceGrpc.newStub(channel)

            val instruments = uids.map { LastPriceInstrument.newBuilder().setInstrumentId(it).build() }
            val subscribe =
                SubscribeLastPriceRequest
                    .newBuilder()
                    .setSubscriptionAction(SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
                    .addAllInstruments(instruments)
                    .build()
            val serverSideRequest =
                MarketDataServerSideStreamRequest
                    .newBuilder()
                    .setSubscribeLastPriceRequest(subscribe)
                    .build()

            val responseObserver =
                object : StreamObserver<MarketDataResponse> {
                    override fun onNext(value: MarketDataResponse) {
                        if (value.hasLastPrice()) {
                            val lp = value.lastPrice
                            val price = lp.price?.let { it.units + it.nano / 1_000_000_000.0 }
                            if (price != null) {
                                logger.d(TAG, "[stream/gRPC/TInvest] LastPrice ${lp.instrumentUid} = $price")
                                trySend(lp.instrumentUid to price)
                            }
                        }
                    }

                    override fun onError(t: Throwable) {
                        close(t)
                    }

                    override fun onCompleted() {
                        close()
                    }
                }

            stub.marketDataServerSideStream(serverSideRequest, responseObserver)
            awaitClose { }
        }

    // ---------- Стрим статусов заявок ----------

    fun subscribeOrderState(accountId: String): Flow<OrderState> =
        callbackFlow {
            val channel =
                state.channels?.ordersState
                    ?: throw IllegalStateException("Канал для OrderState не инициализирован")
            val stub = OrdersStreamServiceGrpc.newStub(channel)

            val request = OrderStateStreamRequest.newBuilder().addAccounts(accountId).build()

            val responseObserver =
                object : StreamObserver<OrderStateStreamResponse> {
                    override fun onNext(value: OrderStateStreamResponse) {
                        if (!value.hasOrderState()) return
                        val s = value.orderState

                        val limitPrice =
                            if (s.hasInitialOrderPrice()) {
                                s.initialOrderPrice.let { it.units + it.nano / 1_000_000_000.0 }
                            } else {
                                null
                            }
                        val executedPrice =
                            if (s.hasExecutedOrderPrice()) {
                                s.executedOrderPrice.let { it.units + it.nano / 1_000_000_000.0 }
                            } else {
                                null
                            }
                        val updateTime = if (s.hasCreatedAt()) s.createdAt.seconds else null
                        val orderTypeValue =
                            s.orderType.name
                                .removePrefix("ORDER_TYPE_")
                                .ifBlank { null }
                        val tscalpInstrumentIdValue =
                            s.instrumentUid.ifBlank { null }

                        trySend(
                            OrderState(
                                orderId = s.orderId,
                                orderRequestId = s.orderRequestId.ifBlank { null },
                                ticker = s.ticker,
                                direction = s.direction.name.removePrefix("ORDER_DIRECTION_"),
                                limitPrice = limitPrice,
                                executedPrice = executedPrice,
                                quantity = s.lotsRequested,
                                executedQuantity = s.lotsExecuted,
                                status = s.executionReportStatus.name.removePrefix("EXECUTION_REPORT_STATUS_"),
                                updateTime = updateTime,
                                orderType = orderTypeValue,
                                tscalpInstrumentId = tscalpInstrumentIdValue,
                            ),
                        )
                    }

                    override fun onError(t: Throwable) {
                        close(t)
                    }

                    override fun onCompleted() {
                        close()
                    }
                }

            stub.orderStateStream(request, responseObserver)
            awaitClose { }
        }

    // ---------- Проверка доступности сделки ----------

    /**
     * Проверяет, хватает ли свободных средств на сделку.
     *
     * [direction] в текущей реализации не используется: проверка
     * симметрична для buy и sell (сравнивается требуемая сумма
     * с балансом). Параметр зарезервирован для будущих
     * направленно-специфичных проверок (например, учёт позиции
     * при продаже).
     */
    suspend fun checkTradeAvailability(
        accountId: String,
        uid: String,
        direction: OrderDirection,
        quantity: Long,
    ): TradeCheckResult {
        val balance = accountsService.getBalance(accountId)
        val lastPrice =
            getLastPricesByTscalpInstrumentId(listOf(uid))[uid]
                ?: return TradeCheckResult.Error("Цена не получена")
        val required = lastPrice * quantity
        return if (balance >= required) {
            TradeCheckResult.Success
        } else {
            TradeCheckResult.InsufficientFunds(required = required, balance = balance)
        }
    }
}
