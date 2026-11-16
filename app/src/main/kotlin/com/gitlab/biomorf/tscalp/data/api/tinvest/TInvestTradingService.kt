package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.data.api.common.TransportProtocol
import com.gitlab.biomorf.tscalp.data.api.common.TransportRateLimiter
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderState
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.GetTradingStatusRequest
import ru.tinkoff.piapi.contract.v1.GetTradingStatusesRequest
import ru.tinkoff.piapi.contract.v1.OrderStateStreamRequest
import ru.tinkoff.piapi.contract.v1.OrderStateStreamResponse
import ru.tinkoff.piapi.contract.v1.OrdersStreamServiceGrpc

/**
 * Торговля T-Invest: статусы инструментов, стрим статусов заявок, проверка доступности сделки.
 *
 * Выделен из `TInvestMarketDataService` (clean-audit Н63, шаг 6).
 * Зависит от [TInvestAccountsService] (баланс) и [TInvestPricesService]
 * (последняя цена) для [checkTradeAvailability].
 */
class TInvestTradingService(
    private val state: TInvestApiState,
    private val logger: Logger,
    private val accountsService: TInvestAccountsService,
    private val pricesService: TInvestPricesService,
    private val rateLimiter: TransportRateLimiter,
) {
    companion object {
        private const val TAG = "TInvestTrading"
        private const val GET_TRADING_STATUSES_CONCURRENCY = 10
        private const val TRADING_STATUSES_BATCH_SIZE = 100
    }

    // Concurrency cap for N parallel unary calls. Rate-limiter serializes at
    // bucket level, but N coroutines still contend on its mutex -> context-switch
    // storm at N=1000. Revisit if T-Invest adds a batch API (umbrella subtask 4).
    private val tradingStatusesSemaphore = Semaphore(GET_TRADING_STATUSES_CONCURRENCY)

    suspend fun getTradingStatuses(uids: List<String>): Map<String, TradingAvailability> =
        withContext(Dispatchers.IO) {
            if (uids.isEmpty()) return@withContext emptyMap()
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            tryBatch(currentApi, uids) ?: getTradingStatusesSingular(currentApi, uids)
        }

    /**
     * Batch path (T-8): one unary per chunk of [TRADING_STATUSES_BATCH_SIZE]
     * instead of N parallel singular calls. Returns `null` on any failure —
     * the caller falls back to the singular path (per-uid error mapping
     * stays there).
     */
    private suspend fun tryBatch(
        currentApi: ru.ttech.piapi.core.InvestApi,
        uids: List<String>,
    ): Map<String, TradingAvailability>? {
        val merged = mutableMapOf<String, TradingAvailability>()
        try {
            uids.chunked(TRADING_STATUSES_BATCH_SIZE).forEach { chunk ->
                merged.putAll(fetchBatchChunk(currentApi, chunk))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RateLimitTimeoutException) {
            logger.w(TAG, "batch getTradingStatuses rate-limited, falling back to singular: ${e.message}")
            return null
        } catch (e: Exception) {
            logger.w(TAG, "batch getTradingStatuses failed, falling back to singular: ${e.message}")
            return null
        }
        uids.forEach { uid -> merged.putIfAbsent(uid, TradingAvailability.UNKNOWN) }
        return merged
    }

    /** One batch unary per chunk; returns uid -> availability. */
    private suspend fun fetchBatchChunk(
        currentApi: ru.ttech.piapi.core.InvestApi,
        chunk: List<String>,
    ): Map<String, TradingAvailability> {
        val request =
            GetTradingStatusesRequest
                .newBuilder()
                .addAllInstrumentId(chunk)
                .build()
        val response =
            rateLimiter.withUnaryPermission(
                protocol = TransportProtocol.GRPC,
                service = "marketdata",
                method = "getTradingStatuses",
            ) {
                currentApi.marketDataServiceSync.getTradingStatuses(request)
            }
        return response.tradingStatusesList
            .filter { it.instrumentUid.isNotEmpty() }
            .associate { status ->
                status.instrumentUid to
                    if (status.apiTradeAvailableFlag) {
                        TradingAvailability.AVAILABLE
                    } else {
                        TradingAvailability.UNAVAILABLE
                    }
            }
    }

    /**
     * Singular path (pre-T-8): N parallel per-uid unary calls capped by
     * [tradingStatusesSemaphore]. Used as fallback when batch fails.
     */
    private suspend fun getTradingStatusesSingular(
        currentApi: ru.ttech.piapi.core.InvestApi,
        uids: List<String>,
    ): Map<String, TradingAvailability> {
        val result = mutableMapOf<String, TradingAvailability>()
        val statuses =
            kotlinx.coroutines.coroutineScope {
                uids
                    .map { uid ->
                        async {
                            try {
                                val request = GetTradingStatusRequest.newBuilder().setInstrumentId(uid).build()
                                val response =
                                    tradingStatusesSemaphore.withPermit {
                                        rateLimiter.withUnaryPermission(
                                            protocol = TransportProtocol.GRPC,
                                            service = "marketdata",
                                            method = "getTradingStatus",
                                        ) {
                                            currentApi.marketDataServiceSync.getTradingStatus(request)
                                        }
                                    }
                                val available = response.apiTradeAvailableFlag
                                uid to if (available) TradingAvailability.AVAILABLE else TradingAvailability.UNAVAILABLE
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: RateLimitTimeoutException) {
                                logger.e(TAG, "Статус для $uid: rate-limited, ${e.message}", e)
                                uid to TradingAvailability.UNKNOWN
                            } catch (e: Exception) {
                                logger.w(TAG, "Статус для $uid недоступен: ${e.message}")
                                uid to TradingAvailability.UNKNOWN
                            }
                        }
                    }.awaitAll()
                    .toMap()
            }
        result.putAll(statuses)
        return result
    }

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

    suspend fun checkTradeAvailability(
        accountId: String,
        uid: String,
        direction: OrderDirection,
        quantity: Long,
    ): TradeCheckResult {
        val balance = accountsService.getBalance(accountId)
        val lastPrice =
            pricesService.getLastPricesByTscalpInstrumentId(listOf(uid))[uid]
                ?: return TradeCheckResult.Error("Цена не получена")
        val required = lastPrice * quantity
        return if (balance >= required) {
            TradeCheckResult.Success
        } else {
            TradeCheckResult.InsufficientFunds(required = required, balance = balance)
        }
    }
}
