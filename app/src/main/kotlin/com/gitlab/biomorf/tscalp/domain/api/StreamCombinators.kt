package com.gitlab.biomorf.tscalp.domain.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge

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
