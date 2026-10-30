package com.gitlab.biomorf.tscalp.data.orchestrator

import io.grpc.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive

/**
 * Объединяет (merge) до трёх источников данных в один поток.
 *
 * **Это merge, не fallback.** Все переданные источники работают
 * параллельно и эмитят одновременно; переключение по отказу не
 * происходит. WS в T-Invest — резервный транспорт по смыслу, но
 * технически merge параллельный (оба идут одновременно).
 *
 * @param primary Основной источник (например, gRPC-стрим).
 * @param fallback Резервный источник (например, WebSocket).
 * @param polling Последний рубеж (например, polling раз в N секунд).
 *
 * Если источник передан как `null`, его ветка игнорируется.
 * Возвращает `Flow`, который эмитит события из всех активных
 * источников параллельно.
 */
fun <T> mergeStreamSources(
    primary: Flow<T>?,
    fallback: Flow<T>?,
    polling: Flow<T>?,
): Flow<T> {
    val sources = listOfNotNull(primary, fallback, polling)
    return when (sources.size) {
        0 -> emptyFlow()
        1 -> sources.first()
        else -> merge(*sources.toTypedArray())
    }
}

/**
 * Переподключает поток при ошибках с экспоненциальным backoff.
 *
 * Нужен для gRPC-источников: их flow завершается при обрыве канала,
 * и без обёртки merge() падает целиком — fallback теряется тоже.
 * Штатная отмена (CancellationException) пробрасывается. Нормальное
 * завершение источника тоже переподключаем: сервер мог закрыть стрим.
 *
 * **Классификация ошибок.** Non-retryable gRPC-коды (NOT_FOUND,
 * UNAUTHENTICATED, PERMISSION_DENIED, INVALID_ARGUMENT,
 * FAILED_PRECONDITION) пробрасываются наружу — повтор бессмыслен.
 * Retryable (UNAVAILABLE, DEADLINE_EXCEEDED, INTERNAL, UNKNOWN) —
 * backoff. RESOURCE_EXHAUSTED — retry с максимальной задержкой
 * (rate-limit, см. `docs/rate-limits.md`). Не-gRPC-исключения —
 * консервативно retry.
 */
private val DEFAULT_RETRY_DELAYS_MS = longArrayOf(1_000, 5_000, 15_000, 30_000, 60_000)

fun <T> retryWithBackoff(
    delays: LongArray = DEFAULT_RETRY_DELAYS_MS,
    source: () -> Flow<T>,
): Flow<T> =
    flow {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            try {
                source().collect { emit(it) }
                attempt = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isRetryable(e)) throw e
                attempt++
                delay(retryDelay(e, attempt, delays))
            }
        }
    }

private fun isRetryable(e: Throwable): Boolean =
    when (Status.fromThrowable(e).code) {
        Status.Code.NOT_FOUND,
        Status.Code.UNAUTHENTICATED,
        Status.Code.PERMISSION_DENIED,
        Status.Code.INVALID_ARGUMENT,
        Status.Code.FAILED_PRECONDITION,
        -> false

        Status.Code.UNAVAILABLE,
        Status.Code.DEADLINE_EXCEEDED,
        Status.Code.INTERNAL,
        Status.Code.UNKNOWN,
        Status.Code.RESOURCE_EXHAUSTED,
        -> true

        else -> true
    }

private fun retryDelay(
    e: Throwable,
    attempt: Int,
    delays: LongArray,
): Long {
    if (Status.fromThrowable(e).code == Status.Code.RESOURCE_EXHAUSTED) return delays.max()
    val idx = (attempt - 1).coerceIn(0, delays.lastIndex)
    return delays[idx]
}
