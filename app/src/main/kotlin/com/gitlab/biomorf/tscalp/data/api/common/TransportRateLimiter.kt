package com.gitlab.biomorf.tscalp.data.api.common

/**
 * Broker-agnostic rate-limiter SPI.
 *
 * Two axes:
 *  - unary  — [withUnaryPermission], gRPC-unary + REST-unary, token-bucket.
 *  - stream — [withStreamSlot], gRPC-streaming + WS, concurrent slots.
 *
 * KMP-safe: no `java.*`, no `android.*`, no `System.currentTimeMillis()`.
 * Domain does not know this interface (2e Clean).
 * Per-broker limits live in `data/api/<broker>/<Broker>RateLimits.kt`.
 */
enum class TransportProtocol { GRPC, REST, WS }

interface TransportRateLimiter {
    suspend fun <T> withUnaryPermission(
        protocol: TransportProtocol,
        service: String,
        method: String? = null,
        block: suspend () -> T,
    ): T

    suspend fun <T> withStreamSlot(
        protocol: TransportProtocol,
        service: String,
        block: suspend () -> T,
    ): T
}
