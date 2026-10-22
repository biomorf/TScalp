package com.gitlab.biomorf.tscalp.data.orchestrator

import com.gitlab.biomorf.tscalp.data.api.TInvestMarketDataService
import com.gitlab.biomorf.tscalp.data.api.TInvestOrdersService
import com.gitlab.biomorf.tscalp.domain.api.retryWithBackoff
import com.gitlab.biomorf.tscalp.domain.api.streamWithFallback
import com.gitlab.biomorf.tscalp.domain.models.OrderListItem
import com.gitlab.biomorf.tscalp.domain.models.OrderState
import com.gitlab.biomorf.tscalp.domain.models.PortfolioPosition
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Оркестратор стримов T-Invest.
 *
 * Композирует два уровня (стрим и polling) с fallback'ами.
 * Не использует Hilt-аннотации и Android-импорты —
 * готов к переносу в KMP-модуль.
 *
 * Фаза 1: только gRPC-стрим + polling (WS-транспорт пока null).
 * Фаза 2 добавит WS как fallback стрим-уровня.
 */
class TInvestOrchestrator(
    private val marketData: TInvestMarketDataService,
    private val orders: TInvestOrdersService,
    private val wsFallback: (String) -> Flow<PositionStreamItem>? = { null },
    private val pricesWsFallback: (Set<String>) -> Flow<Pair<String, Double>>? = { null },
    private val orderStateWsFallback: (String) -> Flow<OrderState>? = { null },
    private val pricesRestFallback: (suspend (List<String>) -> Map<String, Double?>)? = null,
    private val positionsRestFallback: (suspend (String, Boolean) -> List<PortfolioPosition>)? = null,
    private val ordersRestFallback: (suspend (String) -> List<OrderListItem>)? = null,
    private val balanceRestFallback: (suspend (String) -> Double)? = null,
) {
    private companion object {
        private const val TAG = "TInvestOrchestrator"
        private const val POSITIONS_POLLING_DELAY_MS = 10_000L
    }

    fun positionsStream(
        accountId: String,
        sandboxMode: Boolean,
    ): Flow<PositionStreamItem> =
        streamWithFallback(
            primary =
                if (DebugConfig.disableGrpc) {
                    null
                } else {
                    marketData.subscribePositionsGrpcWithReconnect(accountId)
                },
            fallback =
                if (DebugConfig.disableWs) {
                    null
                } else {
                    wsFallback(accountId)
                },
            polling =
                if (DebugConfig.DISABLE_POLLING) {
                    null
                } else {
                    positionsPolling(accountId, sandboxMode)
                },
        )

    fun pricesStream(uids: Set<String>): Flow<Pair<String, Double>> =
        streamWithFallback(
            primary =
                if (DebugConfig.disableGrpc) {
                    null
                } else {
                    retryWithBackoff { marketData.subscribeLastPrices(uids.toList()) }
                },
            fallback =
                if (DebugConfig.disableWs) {
                    null
                } else {
                    pricesWsFallback(uids)
                },
            polling = null,
        )

    fun orderStateStream(accountId: String): Flow<OrderState> =
        streamWithFallback(
            primary =
                if (DebugConfig.disableGrpc) {
                    null
                } else {
                    retryWithBackoff { marketData.subscribeOrderState(accountId) }
                },
            fallback =
                if (DebugConfig.disableWs) {
                    null
                } else {
                    orderStateWsFallback(accountId)
                },
            polling = null,
        )

    private fun positionsPolling(
        accountId: String,
        sandboxMode: Boolean,
    ): Flow<PositionStreamItem> =
        flow {
            AppLogger.d(TAG, "[orchestrator/polling/TInvest]: started (account=$accountId)")
            try {
                while (currentCoroutineContext().isActive) {
                    try {
                        val snapshot = positionsSnapshot(accountId, sandboxMode)
                        AppLogger.d(TAG, "[orchestrator/polling/TInvest]: fetched ${snapshot.size} positions")
                        for (pos in snapshot) emit(marketData.convertToStreamItem(pos))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (e.message?.contains("NOT_FOUND") == true) {
                            AppLogger.w(TAG, "[orchestrator/polling/TInvest]: account not found — stopping")
                            return@flow
                        }
                        AppLogger.w(TAG, "[orchestrator/polling/TInvest]: error (${e.message})")
                    }
                    delay(POSITIONS_POLLING_DELAY_MS)
                }
            } finally {
                AppLogger.d(TAG, "[orchestrator/polling/TInvest]: stopped (account=$accountId)")
            }
        }

    // ---------- Unary snapshots (без fallback, фаза 1a) ----------
    // Fallback gRPC → REST будет добавлен в шаге 2.

    suspend fun positionsSnapshot(
        accountId: String,
        sandboxMode: Boolean,
    ): List<PortfolioPosition> {
        if (DebugConfig.forceRest) {
            val rest = positionsRestFallback ?: throw IllegalStateException("REST fallback not configured")
            AppLogger.w(TAG, "forceRest: bypassing gRPC for positions")
            return rest(accountId, sandboxMode)
        }
        return try {
            marketData.fetchPositionsSnapshot(accountId, sandboxMode)
        } catch (e: Throwable) {
            if (isTransportError(e) && positionsRestFallback != null) {
                AppLogger.w(TAG, "gRPC transport error (${e.message}), falling back to REST for positions")
                positionsRestFallback(accountId, sandboxMode)
            } else {
                throw e
            }
        }
    }

    suspend fun pricesSnapshot(uids: List<String>): Map<String, Double?> {
        AppLogger.d(TAG, "pricesSnapshot: forceRest=${DebugConfig.forceRest}")
        if (DebugConfig.forceRest) {
            val rest = pricesRestFallback ?: throw IllegalStateException("REST fallback not configured")
            AppLogger.w(TAG, "forceRest: bypassing gRPC for prices")
            return rest(uids)
        }
        return try {
            marketData.getLastPricesByTscalpInstrumentId(uids)
        } catch (e: Throwable) {
            if (isTransportError(e) && pricesRestFallback != null) {
                AppLogger.w(TAG, "gRPC transport error (${e.message}), falling back to REST")
                pricesRestFallback(uids)
            } else {
                throw e
            }
        }
    }

    private fun isTransportError(e: Throwable): Boolean {
        val status =
            when (e) {
                is io.grpc.StatusException -> e.status
                is io.grpc.StatusRuntimeException -> e.status
                else -> return false
            }
        return status.code in
            listOf(
                io.grpc.Status.Code.UNAVAILABLE,
                io.grpc.Status.Code.DEADLINE_EXCEEDED,
                io.grpc.Status.Code.UNIMPLEMENTED,
            )
    }

    suspend fun ordersSnapshot(accountId: String): List<OrderListItem> {
        if (DebugConfig.forceRest) {
            val rest = ordersRestFallback ?: throw IllegalStateException("REST fallback not configured")
            AppLogger.w(TAG, "forceRest: bypassing gRPC for orders")
            return rest(accountId)
        }
        return try {
            orders.getOrders(accountId) + orders.getStopOrders(accountId)
        } catch (e: Throwable) {
            if (isTransportError(e) && ordersRestFallback != null) {
                AppLogger.w(TAG, "gRPC transport error (${e.message}), falling back to REST for orders")
                ordersRestFallback(accountId)
            } else {
                throw e
            }
        }
    }

    suspend fun balanceSnapshot(accountId: String): Double {
        if (DebugConfig.forceRest) {
            val rest = balanceRestFallback ?: throw IllegalStateException("REST fallback not configured")
            AppLogger.w(TAG, "forceRest: bypassing gRPC for balance")
            return rest(accountId)
        }
        return try {
            marketData.getBalance(accountId)
        } catch (e: Throwable) {
            if (isTransportError(e) && balanceRestFallback != null) {
                AppLogger.w(TAG, "gRPC transport error (${e.message}), falling back to REST for balance")
                balanceRestFallback(accountId)
            } else {
                throw e
            }
        }
    }
}
