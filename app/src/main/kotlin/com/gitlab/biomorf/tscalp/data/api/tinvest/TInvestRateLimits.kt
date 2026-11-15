package com.gitlab.biomorf.tscalp.data.api.tinvest

/**
 * T-Invest unary and stream limits (per-broker config, D6=A).
 *
 * Source: `docs/rate-limits.md` — T-Invest official limits.
 * Static constants (D5=A); `GetUserTariff` dynamic refresh — follow-up task.
 *
 * Keys: service name passed to `TransportRateLimiter`; `service.method` uses `.`.
 * Unary values are per-minute call counts; per-second overrides listed separately.
 * Stream values are concurrent-connection slots.
 */
@Suppress("MagicNumber")
internal object TInvestRateLimits {
    /** Global per-IP cap, all transports, all services. */
    const val GLOBAL_MAX_PER_SECOND: Int = 50

    /** Window constants (KMP-safe; no `java.util.concurrent.TimeUnit`). */
    const val ONE_SECOND_NANOS: Long = 1_000_000_000L
    const val ONE_MINUTE_NANOS: Long = 60_000_000_000L

    /** Per-service unary budgets (per minute). */
    val UNARY_PER_MIN_GRPC: Map<String, Int> =
        mapOf(
            "instruments" to 200,
            "users" to 100,
            "operations" to 200,
            "marketdata" to 600,
            "stoporders" to 50,
            "sandbox" to 200,
            "orders" to 100,
            "signals" to 100,
            "trailing" to 100,
        )
    val UNARY_PER_MIN_REST: Map<String, Int> =
        UNARY_PER_MIN_GRPC.toMutableMap().apply {
            this["instruments"] = 100
        }

    /** Per-method unary overrides (per minute). */
    val METHOD_UNARY_PER_MIN_GRPC: Map<String, Int> =
        mapOf(
            "instruments.bonds" to 15,
            "instruments.shares" to 15,
            "instruments.options" to 15,
            "instruments.futures" to 15,
            "instruments.etfs" to 15,
            "instruments.assets" to 15,
            "operations.report" to 5,
            "orders.getOrders" to 200,
            "orders.cancelOrder" to 300,
            "orders.replaceOrder" to 300,
            "orders.postOrderAsync" to 600,
            "stoporders.getStopOrders" to 60,
        )
    val METHOD_UNARY_PER_MIN_REST: Map<String, Int> =
        METHOD_UNARY_PER_MIN_GRPC
            .minus("operations.report")
            .plus("trailing.getHistory" to 30)

    /** Per-method unary overrides (per second). T-Invest: `postOrder` = 15/sec. */
    val METHOD_UNARY_PER_SEC_GRPC: Map<String, Int> = mapOf("orders.postOrder" to 15)
    val METHOD_UNARY_PER_SEC_REST: Map<String, Int> = mapOf("orders.postOrder" to 15)

    /** Stream concurrent-connection budgets (gRPC-streaming and WS). */
    val STREAM_GRPC: Map<String, Int> =
        mapOf(
            "marketdata" to 32,
            "orders" to 16,
            "operations" to 11,
        )
    val STREAM_WS: Map<String, Int> =
        mapOf(
            "marketdata" to 32,
            "orders" to 16,
            "operations" to 11,
        )

    /**
     * WS shares the stream-connection budget with gRPC-streaming (conservative).
     * Flip to `false` if T-Invest confirms separate budgets.
     */
    const val WS_SHARES_STREAM_BUDGET: Boolean = true
}
