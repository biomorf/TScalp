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
import kotlinx.coroutines.flow.Flow

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
) {
    private companion object {
        private const val TAG = "TInvestOrchestrator"
    }

    fun positionsStream(accountId: String): Flow<PositionStreamItem> =
        streamWithFallback(
            primary =
                if (DebugConfig.DISABLE_GRPC_STREAM) {
                    null
                } else {
                    marketData.subscribePositionsGrpcWithReconnect(accountId)
                },
            fallback =
                if (DebugConfig.DISABLE_WS_FALLBACK) {
                    null
                } else {
                    wsFallback(accountId)
                },
            polling =
                if (DebugConfig.DISABLE_POLLING) {
                    null
                } else {
                    marketData.subscribePositionsPolling(accountId)
                },
        )

    fun pricesStream(uids: Set<String>): Flow<Pair<String, Double>> =
        streamWithFallback(
            primary =
                if (DebugConfig.DISABLE_GRPC_STREAM) {
                    null
                } else {
                    retryWithBackoff { marketData.subscribeLastPrices(uids.toList()) }
                },
            fallback =
                if (DebugConfig.DISABLE_WS_FALLBACK) {
                    null
                } else {
                    pricesWsFallback(uids)
                },
            polling = null,
        )

    fun orderStateStream(accountId: String): Flow<OrderState> =
        streamWithFallback(
            primary =
                if (DebugConfig.DISABLE_GRPC_STREAM) {
                    null
                } else {
                    retryWithBackoff { marketData.subscribeOrderState(accountId) }
                },
            fallback =
                if (DebugConfig.DISABLE_WS_FALLBACK) {
                    null
                } else {
                    orderStateWsFallback(accountId)
                },
            polling = null,
        )

    // ---------- Unary snapshots (без fallback, фаза 1a) ----------
    // Fallback gRPC → REST будет добавлен в шаге 2.

    suspend fun positionsSnapshot(
        accountId: String,
        sandboxMode: Boolean,
    ): List<PortfolioPosition> = marketData.fetchPositionsSnapshot(accountId, sandboxMode)

    suspend fun pricesSnapshot(uids: List<String>): Map<String, Double?> =
        try {
            marketData.getLastPricesByTscalpInstrumentId(uids)
        } catch (e: Throwable) {
            if (isTransportError(e) && pricesRestFallback != null) {
                AppLogger.w(TAG, "gRPC transport error (${e.message}), falling back to REST")
                pricesRestFallback(uids)
            } else {
                throw e
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

    suspend fun ordersSnapshot(accountId: String): List<OrderListItem> = orders.getOrders(accountId) + orders.getStopOrders(accountId)

    suspend fun balanceSnapshot(accountId: String): Double = marketData.getBalance(accountId)
}
