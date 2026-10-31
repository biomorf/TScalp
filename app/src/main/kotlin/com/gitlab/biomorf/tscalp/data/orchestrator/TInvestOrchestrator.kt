package com.gitlab.biomorf.tscalp.data.orchestrator

import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestAccountsService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestInstrumentsService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestOrdersService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestPortfolioService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestPositionsService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestPricesService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestTradingService
import com.gitlab.biomorf.tscalp.data.orchestrator.mergeStreamSources
import com.gitlab.biomorf.tscalp.data.orchestrator.retryWithBackoff
import com.gitlab.biomorf.tscalp.domain.api.DebugFlagsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import com.gitlab.biomorf.tscalp.domain.models.Order
import com.gitlab.biomorf.tscalp.domain.models.OrderState
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
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
    private val positions: TInvestPositionsService,
    private val debugFlagsRepository: DebugFlagsRepository,
    private val orders: TInvestOrdersService,
    private val accounts: TInvestAccountsService,
    private val portfolio: TInvestPortfolioService,
    private val prices: TInvestPricesService,
    private val trading: TInvestTradingService,
    private val instruments: TInvestInstrumentsService,
    private val logger: Logger,
    private val wsFallback: (String) -> Flow<PositionStreamItem>? = { null },
    private val pricesWsFallback: (Set<String>) -> Flow<Pair<String, Double>>? = { null },
    private val orderStateWsFallback: (String) -> Flow<OrderState>? = { null },
    private val pricesRestFallback: (suspend (List<String>) -> Map<String, Double?>)? = null,
    private val positionsRestFallback: (suspend (String, Boolean) -> List<PositionStreamItem>)? = null,
    private val ordersRestFallback: (suspend (String) -> List<Order>)? = null,
    private val balanceRestFallback: (suspend (String) -> Double)? = null,
    private val findInstrumentsRestFallback: (suspend (String) -> List<InstrumentUi>)? = null,
) {
    private companion object {
        private const val TAG = "TInvestOrchestrator"
    }

    fun positionsStream(accountId: String): Flow<PositionStreamItem> =
        mergeStreamSources(
            primary =
                if (debugFlagsRepository.disableGrpc) {
                    null
                } else {
                    positions.subscribePositionsGrpcWithReconnect(accountId)
                },
            fallback =
                if (debugFlagsRepository.disableWs) {
                    null
                } else {
                    wsFallback(accountId)
                },
            polling = null,
        )

    fun pricesStream(uids: Set<String>): Flow<Pair<String, Double>> =
        mergeStreamSources(
            primary =
                if (debugFlagsRepository.disableGrpc) {
                    null
                } else {
                    retryWithBackoff { prices.subscribeLastPrices(uids.toList()) }
                },
            fallback =
                if (debugFlagsRepository.disableWs) {
                    null
                } else {
                    pricesWsFallback(uids)
                },
            polling = null,
        )

    fun orderStateStream(accountId: String): Flow<OrderState> =
        mergeStreamSources(
            primary =
                if (debugFlagsRepository.disableGrpc) {
                    null
                } else {
                    retryWithBackoff { trading.subscribeOrderState(accountId) }
                },
            fallback =
                if (debugFlagsRepository.disableWs) {
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
    ): List<PositionStreamItem> {
        if (debugFlagsRepository.forceRest) {
            val rest = positionsRestFallback ?: throw IllegalStateException("REST fallback not configured")
            logger.w(TAG, "forceRest: bypassing gRPC for positions")
            return rest(accountId, sandboxMode)
        }
        return try {
            portfolio.fetchPortfolioPositions(accountId, sandboxMode)
        } catch (e: Throwable) {
            if (isTransportError(e) && positionsRestFallback != null) {
                logger.w(TAG, "gRPC transport error (${e.message}), falling back to REST for positions")
                positionsRestFallback(accountId, sandboxMode)
            } else {
                throw e
            }
        }
    }

    suspend fun pricesSnapshot(uids: List<String>): Map<String, Double?> {
        logger.d(TAG, "pricesSnapshot: forceRest=${debugFlagsRepository.forceRest}")
        if (debugFlagsRepository.forceRest) {
            val rest = pricesRestFallback ?: throw IllegalStateException("REST fallback not configured")
            logger.w(TAG, "forceRest: bypassing gRPC for prices")
            return rest(uids)
        }
        return try {
            prices.getLastPricesByTscalpInstrumentId(uids)
        } catch (e: Throwable) {
            if (isTransportError(e) && pricesRestFallback != null) {
                logger.w(TAG, "gRPC transport error (${e.message}), falling back to REST")
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

    suspend fun ordersSnapshot(accountId: String): List<Order> {
        if (debugFlagsRepository.forceRest) {
            val rest = ordersRestFallback ?: throw IllegalStateException("REST fallback not configured")
            logger.w(TAG, "forceRest: bypassing gRPC for orders")
            return rest(accountId)
        }
        return try {
            orders.getOrders(accountId) + orders.getStopOrders(accountId)
        } catch (e: Throwable) {
            if (isTransportError(e) && ordersRestFallback != null) {
                logger.w(TAG, "gRPC transport error (${e.message}), falling back to REST for orders")
                ordersRestFallback(accountId)
            } else {
                throw e
            }
        }
    }

    suspend fun balanceSnapshot(accountId: String): Double {
        if (debugFlagsRepository.forceRest) {
            val rest = balanceRestFallback ?: throw IllegalStateException("REST fallback not configured")
            logger.w(TAG, "forceRest: bypassing gRPC for balance")
            return rest(accountId)
        }
        return try {
            accounts.getBalance(accountId)
        } catch (e: Throwable) {
            if (isTransportError(e) && balanceRestFallback != null) {
                logger.w(TAG, "gRPC transport error (${e.message}), falling back to REST for balance")
                balanceRestFallback(accountId)
            } else {
                throw e
            }
        }
    }

    suspend fun findInstrumentsWithFallback(query: String): List<InstrumentUi> {
        if (debugFlagsRepository.forceRest) {
            val rest = findInstrumentsRestFallback ?: throw IllegalStateException("REST fallback not configured")
            logger.w(TAG, "forceRest: bypassing gRPC for instruments")
            return rest(query)
        }
        return try {
            instruments.findInstruments(query)
        } catch (e: Throwable) {
            if (isTransportError(e) && findInstrumentsRestFallback != null) {
                logger.w(TAG, "gRPC transport error (${e.message}), falling back to REST for instruments")
                findInstrumentsRestFallback(query)
            } else {
                throw e
            }
        }
    }
}
