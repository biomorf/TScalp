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
 * On timeout (> timeoutMs, default 5s) — [RateLimitTimeoutException]; caller wraps
 * into
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
    private val timeoutMs: Long = 5_000L,
    private val slowWaitMs: Long = 1_000L,
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
            if (waitedMs + waitMs > timeoutMs) {
                logger.e(
                    TAG,
                    "[rate-limit/$protocol/$service/${method ?: "-"}] timeout after ${waitedMs}ms",
                )
                throw RateLimitTimeoutException(
                    "[rate-limit/$protocol/$service/${method ?: "-"}] budget exhausted after ${waitedMs}ms",
                )
            }
            if (waitMs >= slowWaitMs) {
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
                streamSemaphores.getOrPut(streamSemaphoreKey(protocol, service)) {
                    Semaphore(streamLimit(protocol, service))
                }
            }
        return semaphore.withPermit { block() }
    }

    /**
     * Semaphore key for [withStreamSlot].
     *
     * When [TInvestRateLimits.WS_SHARES_STREAM_BUDGET] is true, WS and
     * gRPC-streaming share a single per-service budget (T-Invest docs:
     * WS is a proxy for gRPC streaming; the counter is assumed shared).
     * Otherwise WS and gRPC have independent per-service budgets.
     */
    private fun streamSemaphoreKey(
        protocol: TransportProtocol,
        service: String,
    ): Pair<TransportProtocol, String> =
        if (TInvestRateLimits.WS_SHARES_STREAM_BUDGET && protocol == TransportProtocol.WS) {
            TransportProtocol.GRPC to service
        } else {
            protocol to service
        }

    private fun tryAcquireAll(
        protocol: TransportProtocol,
        service: String,
        method: String?,
        nowNanos: Long,
    ): Long? {
        val secBucket = methodSecBucket(protocol, service, method)
        val minBucket = methodMinBucket(protocol, service, method)
        // Per-method override REPLACES the per-service cap (T-Invest docs:
        // postOrder is 15/sec = 900/min — cannot fit under the 100/min service cap).
        val buckets =
            if (secBucket != null || minBucket != null) {
                listOfNotNull(secBucket, minBucket, globalBucket)
            } else {
                listOfNotNull(serviceBucket(protocol, service), globalBucket)
            }
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
                // capacity = rate_per_second (1s burst), not the full per-minute limit.
                // Prevents burst N at startup from exceeding the server's per-second cap.
                capacity = limit.toDouble() / 60.0,
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
                // capacity = rate_per_second (1s burst), not the full per-minute limit.
                // See methodMinBucket above.
                capacity = limit.toDouble() / 60.0,
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

    private data class MethodKey(
        val protocol: TransportProtocol,
        val service: String,
        val method: String,
    )

    companion object {
        private const val TAG = "TInvestRateLimiter"
    }
}

internal const val NANOS_PER_MILLI = 1_000_000L

internal class TokenBucket(
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

class RateLimitTimeoutException(
    message: String,
) : Exception(message)
