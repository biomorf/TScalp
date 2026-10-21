package com.gitlab.biomorf.tscalp.data.orchestrator

import com.gitlab.biomorf.tscalp.BuildConfig

/**
 * Diagnostic-only флаги для изоляции источников стримов.
 *
 * **Не для прода.** В release-сборке все флаги принудительно
 * `false` — `BuildConfig.DEBUG == false`. Даже если UI случайно
 * покажет переключатели, изменения в release не применятся.
 *
 * Позволяют на лету (в рантайме) отключать транспорты для проверки
 * fallback-механики и изоляции источников в логах.
 */
internal object DebugConfig {
    // --- Флаги для рантайм-переключения (в UI Настройки -> Информация) ---
    @Volatile
    var disableGrpc: Boolean = false
        set(value) {
            field = BuildConfig.DEBUG && value
        }

    @Volatile
    var disableWs: Boolean = false
        set(value) {
            field = BuildConfig.DEBUG && value
        }

    @Volatile
    var forceRest: Boolean = false
        set(value) {
            field = BuildConfig.DEBUG && value
        }

    // --- Флаги, оставшиеся с прошлых шагов (для консистентности) ---
    const val DISABLE_POLLING: Boolean = false
}
