package com.gitlab.biomorf.tscalp.domain.api.managers

import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
 * [clock.now()]). В тестах передаётся fake-time,
 * без `delay` / `advanceTimeBy` — переходы детерминированы.
 *
 * Пороги:
 * - [streamSilence] — сколько стрим должен молчать, чтобы
 *   LIVE понизился до POLLING (если polling при этом жив).
 * - [stale] — сколько polling должен молчать, чтобы состояние
 *   стало STALE.
 *
 * @param tag префикс для логов смены состояния.
 * @param streamSilence порог молчания стрима (default 15 секунд).
 * @param stale порог молчания polling (default 30 секунд).
 */
internal class StreamStateTracker(
    private val logger: Logger,
    private val tag: String = "StreamStateTracker",
    private val streamSilence: Duration = 15.seconds,
    private val stale: Duration = 30.seconds,
    private val clock: Clock = Clock.System,
) {
    private val _state = MutableStateFlow(StreamState.STOPPED)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private var lastStreamAt: Instant = Instant.DISTANT_PAST
    private var lastPollingAt: Instant = Instant.DISTANT_PAST

    /** Менеджер запущен. Обе метки = now, состояние INITIALIZING. */
    fun onStart(now: Instant = clock.now()) {
        lastStreamAt = now
        lastPollingAt = now
        set(StreamState.INITIALIZING)
    }

    /** Пришло событие из стрима. Стрим жив → LIVE (безусловно). */
    fun onStreamEmit(now: Instant = clock.now()) {
        lastStreamAt = now
        set(StreamState.LIVE)
    }

    /**
     * Polling успешно завершил цикл (транспорт жив, даже если
     * вернул пустой список). Обновляет метку polling; из
     * INITIALIZING или STALE переводит в POLLING. Из LIVE и
     * POLLING — не трогает (polling не может «понизить» LIVE).
     */
    fun onPollingSuccess(now: Instant = clock.now()) {
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
    fun tick(now: Instant = clock.now()) {
        val streamSilent = now - lastStreamAt
        val pollingSilent = now - lastPollingAt
        when (_state.value) {
            StreamState.LIVE -> {
                when {
                    streamSilent <= streamSilence -> Unit
                    pollingSilent > stale -> set(StreamState.STALE)
                    else -> set(StreamState.POLLING)
                }
            }
            StreamState.POLLING, StreamState.INITIALIZING -> {
                if (pollingSilent > stale) set(StreamState.STALE)
            }
            StreamState.STALE, StreamState.STOPPED -> Unit
        }
    }

    /** Менеджер остановлен. Метки обнуляются, состояние STOPPED. */
    fun onStop() {
        lastStreamAt = Instant.DISTANT_PAST
        lastPollingAt = Instant.DISTANT_PAST
        set(StreamState.STOPPED)
    }

    private fun set(next: StreamState) {
        val prev = _state.value
        if (prev == next) return
        _state.value = next
        logger.d(tag, "state: $prev → $next")
    }
}
