package com.gitlab.biomorf.tscalp.domain.api

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
 * Объединяет до трёх источников данных в один поток.
 *
 * @param primary Основной источник (например, gRPC-стрим).
 * @param fallback Резервный источник (например, WebSocket).
 * @param polling Последний рубеж (например, polling раз в N секунд).
 *
 * Если источник передан как `null`, его ветка игнорируется.
 * Возвращает `Flow`, который эмитит события из всех активных
 * источников параллельно.
 */
fun <T> streamWithFallback(
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
                attempt++
                val idx = (attempt - 1).coerceIn(0, delays.lastIndex)
                delay(delays[idx])
            }
        }
    }
