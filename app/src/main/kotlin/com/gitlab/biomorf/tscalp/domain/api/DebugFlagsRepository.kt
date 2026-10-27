package com.gitlab.biomorf.tscalp.domain.api

/**
 * Domain-контракт diagnostic-флагов транспорта.
 *
 * Флаги существуют только для debug-сборок: реализация
 * (`data.orchestrator.DebugConfig`) принудительно держит `false`
 * в release (`BuildConfig.DEBUG == false` в сеттерах).
 *
 * Позволяют на лету (в рантайме) отключать транспорты для проверки
 * fallback-механики и изоляции источников в логах.
 *
 * Потребители (H23 scope B):
 * - `SettingsViewModel` (presentation) — чтение/запись через UI-тумблеры;
 * - `PriceFlowManager` (domain) — пропуск stream при `disableGrpc && disableWs`;
 * - `TInvestOrchestrator` (data) — выбор primary/fallback/REST источника.
 *
 * @see com.gitlab.biomorf.tscalp.data.orchestrator.DebugConfig
 */
interface DebugFlagsRepository {
    /** Отключить gRPC-транспорт. В release всегда `false`. */
    var disableGrpc: Boolean

    /** Отключить WebSocket-транспорт. В release всегда `false`. */
    var disableWs: Boolean

    /** Принудительно использовать REST для unary-запросов. В release всегда `false`. */
    var forceRest: Boolean
}
