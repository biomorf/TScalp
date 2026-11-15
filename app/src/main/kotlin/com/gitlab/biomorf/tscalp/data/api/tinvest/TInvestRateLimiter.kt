package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.data.api.common.TransportProtocol
import com.gitlab.biomorf.tscalp.data.api.common.TransportRateLimiter
import com.gitlab.biomorf.tscalp.domain.log.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.math.ceil
import kotlin.time.Clock

/**
 * T-Invest rate limiter — token-bucket on 4 unary levels + stream semaphore.
 *
 * Unary levels (narrow to broad, all consumed atomically per request):
 *  1. per-(protocol, service.method) per-second — `postOrder` 15/sec.
 *  2. per-(protocol, service.method) per-minute — `getOrders` 200, `getStopOrders` 60, ...
 *  3. per-(protocol, service) per-minute — Instruments 200/100, MarketData 600/600, ...
 *  4. global per-second — 50/sec, all transports, all services.
 *
 * Acquire is all-or-nothing: if any level is exhausted, returns max wait, consumes
 * nothing. `block()` runs outside the mutex — no contention on the network call.
 * On timeout (> [TIMEOUT_MS]) — [RateLimitTimeoutException]; caller wraps into
 * `AppResult.Failure` at the boundary (see ROADMAP step 1a-3).
 *
 * Stream slots: per-(protocol, service) semaphore. Shared WS+gRPC budget and
 * per-connection logic — 1b (`StreamBudgetLimiter` / `WS_SHARES_STREAM_BUDGET`).
 *
 * KMP-safe: `kotlin.time.*` (injected [Clock]), `kotlinx.coroutines.sync.*`,
 * `kotlinx.coroutines.delay`. No `java.*`, `android.*`, no wall-clock time.
 *
 * Clean (2e): data-layer infrastructure. Domain unaware.
 */
internal class TInvestRateLimiter(
    private val clock: Clock,
    private val logger: Logger,
) : TransportRateLimiter {
    private val mutex = Mutex()

    private val globalBucket =
        TokenBucket(
            capacity = TInvestRateLimits.GLOBAL_MAX_PER_SECOND.toDouble(),
            refillPerNano =
                TInvestRateLimits.GLOBAL_MAX_PER_SECOND.toDouble() /
                    TInvestRateLimits.ONE_SECOND_NANOS.toDouble(),
        )

    private val perServiceBuckets: MutableMap<Pair<TransportProtocol, String>, TokenBucket> =
        mutableMapOf()
    private val perMethodMinBuckets: MutableMap<MethodKey, TokenBucket> = mutableMapOf()
    private val perMethodSecBuckets: MutableMap<MethodKey, TokenBucket> = mutableMapOf()
    private val streamSemaphores: MutableMap<Pair<TransportProtocol, String>, Semaphore> =
        mutableMapOf()

    override suspend fun <T> withUnaryPermission(
        protocol: TransportProtocol,
        service: String,
        method: String?,
        block: suspend () -> T,
    ): T {
        var waitedMs = 0L
        while (true) {
            val waitMs =
                mutex.withLock {
                    tryAcquireAll(protocol, service, method, nowNanos())
                }
            if (waitMs == null) {
                if (waitedMs > 0) {
                    logger.d(TAG, "[rate-limit/$protocol/$service/${method ?: "-"}] acquired after ${waitedMs}ms")
                }
                return block()
            }
            if (waitedMs + waitMs > TIMEOUT_MS) {
                logger.e(
                    TAG,
                    "[rate-limit/$protocol/$service/${method ?: "-"}] timeout after ${waitedMs}ms",
                )
                throw RateLimitTimeoutException(
                    "[rate-limit/$protocol/$service/${method ?: "-"}] budget exhausted after ${waitedMs}ms",
                )
            }
            if (waitMs >= SLOW_WAIT_MS) {
                logger.w(
                    TAG,
                    "[rate-limit/$protocol/$service/${method ?: "-"}] slow wait ${waitMs}ms",
                )
            } else {
                logger.d(
                    TAG,
                    "[rate-limit/$protocol/$service/${method ?: "-"}] wait ${waitMs}ms",
                )
            }
            delay(waitMs)
            waitedMs += waitMs
        }
    }

    override suspend fun <T> withStreamSlot(
        protocol: TransportProtocol,
        service: String,
        block: suspend () -> T,
    ): T {
        val semaphore =
            mutex.withLock {
                streamSemaphores.getOrPut(protocol to service) {
                    Semaphore(streamLimit(protocol, service))
                }
            }
        return semaphore.withPermit { block() }
    }

    private fun tryAcquireAll(
        protocol: TransportProtocol,
        service: String,
        method: String?,
        nowNanos: Long,
    ): Long? {
        val buckets =
            listOfNotNull(
                methodSecBucket(protocol, service, method),
                methodMinBucket(protocol, service, method),
                serviceBucket(protocol, service),
                globalBucket,
            )
        val waits = buckets.map { it.inspect(nowNanos) }
        if (waits.any { it != null }) {
            return waits.filterNotNull().maxOrNull()
        }
        buckets.forEach { it.consume() }
        return null
    }

    private fun methodSecBucket(
        protocol: TransportProtocol,
        service: String,
        method: String?,
    ): TokenBucket? {
        val m = method ?: return null
        val limit =
            when (protocol) {
                TransportProtocol.GRPC -> TInvestRateLimits.METHOD_UNARY_PER_SEC_GRPC["$service.$m"]
                TransportProtocol.REST -> TInvestRateLimits.METHOD_UNARY_PER_SEC_REST["$service.$m"]
                TransportProtocol.WS -> null
            } ?: return null
        return perMethodSecBuckets.getOrPut(MethodKey(protocol, service, m)) {
            TokenBucket(
                capacity = limit.toDouble(),
                refillPerNano = limit.toDouble() / TInvestRateLimits.ONE_SECOND_NANOS.toDouble(),
            )
        }
    }

    private fun methodMinBucket(
        protocol: TransportProtocol,
        service: String,
        method: String?,
    ): TokenBucket? {
        val m = method ?: return null
        val limit =
            when (protocol) {
                TransportProtocol.GRPC -> TInvestRateLimits.METHOD_UNARY_PER_MIN_GRPC["$service.$m"]
                TransportProtocol.REST -> TInvestRateLimits.METHOD_UNARY_PER_MIN_REST["$service.$m"]
                TransportProtocol.WS -> null
            } ?: return null
        return perMethodMinBuckets.getOrPut(MethodKey(protocol, service, m)) {
            TokenBucket(
                capacity = limit.toDouble(),
                refillPerNano = limit.toDouble() / TInvestRateLimits.ONE_MINUTE_NANOS.toDouble(),
            )
        }
    }

    private fun serviceBucket(
        protocol: TransportProtocol,
        service: String,
    ): TokenBucket? {
        val limit =
            when (protocol) {
                TransportProtocol.GRPC -> TInvestRateLimits.UNARY_PER_MIN_GRPC[service]
                TransportProtocol.REST -> TInvestRateLimits.UNARY_PER_MIN_REST[service]
                TransportProtocol.WS -> null
            } ?: return null
        return perServiceBuckets.getOrPut(protocol to service) {
            TokenBucket(
                capacity = limit.toDouble(),
                refillPerNano = limit.toDouble() / TInvestRateLimits.ONE_MINUTE_NANOS.toDouble(),
            )
        }
    }

    private fun streamLimit(
        protocol: TransportProtocol,
        service: String,
    ): Int =
        when (protocol) {
            TransportProtocol.GRPC -> TInvestRateLimits.STREAM_GRPC[service] ?: 1
            TransportProtocol.REST -> 1
            TransportProtocol.WS -> TInvestRateLimits.STREAM_WS[service] ?: 1
        }

    private fun nowNanos(): Long = clock.now().toEpochMilliseconds() * NANOS_PER_MILLI

    private class TokenBucket(
        private val capacity: Double,
        private val refillPerNano: Double,
    ) {
        private var tokens: Double = capacity
        private var lastRefillNanos: Long = 0L

        fun inspect(nowNanos: Long): Long? {
            refill(nowNanos)
            if (tokens >= 1.0) return null
            if (refillPerNano <= 0.0) return Long.MAX_VALUE / 2
            val waitNanos = ceil((1.0 - tokens) / refillPerNano).toLong()
            return maxOf(1L, waitNanos / NANOS_PER_MILLI)
        }

        fun consume() {
            tokens -= 1.0
        }

        private fun refill(nowNanos: Long) {
            if (lastRefillNanos == 0L) {
                lastRefillNanos = nowNanos
                return
            }
            val elapsed = nowNanos - lastRefillNanos
            if (elapsed <= 0L) return
            tokens = (tokens + elapsed.toDouble() * refillPerNano).coerceAtMost(capacity)
            lastRefillNanos = nowNanos
        }
    }

    private data class MethodKey(
        val protocol: TransportProtocol,
        val service: String,
        val method: String,
    )

    companion object {
        private const val TAG = "TInvestRateLimiter"
        private const val TIMEOUT_MS = 5_000L
        private const val SLOW_WAIT_MS = 1_000L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

class RateLimitTimeoutException(
    message: String,
) : Exception(message)
