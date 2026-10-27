package com.gitlab.biomorf.tscalp.data.orchestrator

import com.gitlab.biomorf.tscalp.BuildConfig
import com.gitlab.biomorf.tscalp.domain.api.DebugFlagsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [DebugFlagsRepository] для diagnostic-флагов транспорта.
 *
 * **Не для прода.** В release-сборке все флаги принудительно
 * `false` — `BuildConfig.DEBUG == false`. Даже если UI случайно
 * покажет переключатели, изменения в release не применятся.
 *
 * Позволяют на лету (в рантайме) отключать транспорты для проверки
 * fallback-механики и изоляции источников в логах.
 *
 * Реализация на Hilt-синглтоне: флаги глобальные, один экземпляр
 * на всё приложение. Все потребители (presentation, domain, data)
 * инжектят `DebugFlagsRepository`, не этот класс напрямую.
 */
@Singleton
class DebugConfig
    @Inject
    constructor() : DebugFlagsRepository {
        // --- Флаги для рантайм-переключения (в UI Настройки -> Информация) ---
        @Volatile
        override var disableGrpc: Boolean = false
            set(value) {
                field = BuildConfig.DEBUG && value
            }

        @Volatile
        override var disableWs: Boolean = false
            set(value) {
                field = BuildConfig.DEBUG && value
            }

        @Volatile
        override var forceRest: Boolean = false
            set(value) {
                field = BuildConfig.DEBUG && value
            }
    }
