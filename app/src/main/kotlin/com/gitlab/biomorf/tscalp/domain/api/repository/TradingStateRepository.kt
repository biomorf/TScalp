package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-контракт торгового состояния сессии.
 *
 * Реализация — `data/repository/TradingStateRepositoryImpl.kt` (H21b).
 * Создан в рамках H21b, поведение перенесено из
 * `data.repository.TradingStateRepository` без изменений.
 */
interface TradingStateRepository {
    val state: StateFlow<TradingStateSnapshot?>

    suspend fun save(snapshot: TradingStateSnapshot)

    suspend fun load(): TradingStateSnapshot

    suspend fun clear()
}
