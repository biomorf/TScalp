package com.gitlab.biomorf.tscalp.domain.api.managers

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Тестовый [Clock] с управляемым временем.
 *
 * Начальное значение задаётся в конструкторе; [advance] сдвигает
 * его вперёд. Позволяет детерминированно проверять TTL-эвикцию
 * без реальных задержек.
 */
internal class FakeClock(
    var now: Instant,
) : Clock {
    override fun now(): Instant = now

    fun advance(duration: Duration) {
        now += duration
    }
}
