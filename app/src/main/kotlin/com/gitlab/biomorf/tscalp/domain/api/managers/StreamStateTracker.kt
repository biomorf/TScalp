package com.gitlab.biomorf.tscalp.domain.managers

import com.gitlab.biomorf.tscalp.domain.models.StreamState
import com.gitlab.biomorf.tscalp.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Машина состояний источника данных менеджера.
 *
 * Чистая логика переходов [StreamState] без корутин и таймеров:
 * менеджер вызывает хуки ([onStart], [onStreamEmit],
 * [onPollingSuccess], [tick], [onStop]), трекер решает, как
 * меняется состояние. Watchdog-цикл (тик раз в 1 сек) живёт
 * в менеджере — трекер не знает про scope.
 *
 * Все методы принимают [now] параметром (default —
 * [System.currentTimeMillis]). В тестах передаётся fake-time,
 * без `delay` / `advanceTimeBy` — переходы детерминированы.
 *
 * Пороги:
 * - [streamSilenceMs] — сколько стрим должен молчать, чтобы
 *   LIVE понизился до POLLING (если polling при этом жив).
 * - [staleMs] — сколько polling должен молчать, чтобы состояние
 *   стало STALE.
 *
 * @param tag префикс для логов смены состояния.
 * @param streamSilenceMs порог молчания стрима (default 15 сек).
 * @param staleMs порог молчания polling (default 30 сек).
 */
internal class StreamStateTracker(
    private val tag: String = "StreamStateTracker",
    private val streamSilenceMs: Long = 15_000L,
    private val staleMs: Long = 30_000L,
) {
    private val _state = MutableStateFlow(StreamState.STOPPED)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private var lastStreamAt: Long = 0L
    private var lastPollingAt: Long = 0L

    /** Менеджер запущен. Обе метки = now, состояние INITIALIZING. */
    fun onStart(now: Long = System.currentTimeMillis()) {
        lastStreamAt = now
        lastPollingAt = now
        set(StreamState.INITIALIZING)
    }

    /** Пришло событие из стрима. Стрим жив → LIVE (безусловно). */
    fun onStreamEmit(now: Long = System.currentTimeMillis()) {
        lastStreamAt = now
        set(StreamState.LIVE)
    }

    /**
     * Polling успешно завершил цикл (транспорт жив, даже если
     * вернул пустой список). Обновляет метку polling; из
     * INITIALIZING или STALE переводит в POLLING. Из LIVE и
     * POLLING — не трогает (polling не может «понизить» LIVE).
     */
    fun onPollingSuccess(now: Long = System.currentTimeMillis()) {
        lastPollingAt = now
        when (_state.value) {
            StreamState.INITIALIZING, StreamState.STALE -> set(StreamState.POLLING)
            else -> Unit
        }
    }

    /**
     * Watchdog-тик. Переходы:
     * - LIVE: стрим молчит > [streamSilenceMs] и polling жив → POLLING;
     *   polling молчит > [staleMs] → STALE.
     * - POLLING / INITIALIZING: polling молчит > [staleMs] → STALE.
     * - STALE / STOPPED: ничего (реанимация — через onStreamEmit / onPollingSuccess).
     */
    fun tick(now: Long = System.currentTimeMillis()) {
        val streamSilentMs = now - lastStreamAt
        val pollingSilentMs = now - lastPollingAt
        when (_state.value) {
            StreamState.LIVE -> {
                when {
                    streamSilentMs <= streamSilenceMs -> Unit
                    pollingSilentMs > staleMs -> set(StreamState.STALE)
                    else -> set(StreamState.POLLING)
                }
            }
            StreamState.POLLING, StreamState.INITIALIZING -> {
                if (pollingSilentMs > staleMs) set(StreamState.STALE)
            }
            StreamState.STALE, StreamState.STOPPED -> Unit
        }
    }

    /** Менеджер остановлен. Метки обнуляются, состояние STOPPED. */
    fun onStop() {
        lastStreamAt = 0L
        lastPollingAt = 0L
        set(StreamState.STOPPED)
    }

    private fun set(next: StreamState) {
        val prev = _state.value
        if (prev == next) return
        _state.value = next
        AppLogger.d(tag, "state: $prev → $next")
    }
}
