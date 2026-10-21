package com.gitlab.biomorf.tscalp.data.orchestrator

import com.gitlab.biomorf.tscalp.BuildConfig

/**
 * Diagnostic-only флаги для изоляции источников стримов.
 *
 * **Не для прода.** В release-сборке все флаги принудительно
 * `false` — `BuildConfig.DEBUG == false`, и R8 сворачивает
 * guard в compile-time константу. Даже если `*_RAW` случайно
 * выставлены в `true`, в проде они не сработают.
 *
 * Назначение: при диагностике WS отключить gRPC и/или polling,
 * чтобы в логах `PositionStream` видеть события только от
 * нужного источника. Пример: чтобы проверить, что WS реально
 * даёт данные — выставить `DISABLE_GRPC_STREAM_RAW = true` и
 * `DISABLE_POLLING_RAW = true` в debug, пересобрать, установить.
 *
 * Используется в [TInvestOrchestrator.positionsStream] для
 * передачи `null` в `streamWithFallback` вместо соответствующего
 * Flow. Отключение WS (`DISABLE_WS_FALLBACK`) также
 * предотвращает вызов `wsFallback(accountId)` — лямбда не
 * дёргается, подписка не создаётся, WS-клиент остаётся idle.
 *
 * Жизненный цикл: удалить после стабилизации WS в
 * `stream-orchestrator` (см. docs/ROADMAP.md).
 */
internal object DebugConfig {
    // --- RAW значения. Менять только при диагностике. ---
    private const val DISABLE_GRPC_STREAM_RAW = false
    private const val DISABLE_WS_FALLBACK_RAW = false
    private const val DISABLE_POLLING_RAW = false
    private const val ENABLE_ORDER_STATE_DEBUG_RAW = false
    private const val FORCE_REST_FOR_POLLING_RAW = false

    // --- Публичные флаги. В release всегда false. ---
    val DISABLE_GRPC_STREAM: Boolean = BuildConfig.DEBUG && DISABLE_GRPC_STREAM_RAW
    val DISABLE_WS_FALLBACK: Boolean = BuildConfig.DEBUG && DISABLE_WS_FALLBACK_RAW
    val DISABLE_POLLING: Boolean = BuildConfig.DEBUG && DISABLE_POLLING_RAW
    val ENABLE_ORDER_STATE_DEBUG: Boolean = BuildConfig.DEBUG && ENABLE_ORDER_STATE_DEBUG_RAW
    val FORCE_REST_FOR_POLLING: Boolean = BuildConfig.DEBUG && FORCE_REST_FOR_POLLING_RAW
}
