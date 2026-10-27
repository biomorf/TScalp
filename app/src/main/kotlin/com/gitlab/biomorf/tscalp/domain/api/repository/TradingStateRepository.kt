package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot

/**
 * Domain-контракт торгового состояния сессии.
 *
 * Реализация — `data/repository/TradingStateRepositoryImpl.kt` (H21b).
 * Создан в рамках H21b, поведение перенесено из
 * `data.repository.TradingStateRepository` без изменений.
 */
interface TradingStateRepository {
    suspend fun save(snapshot: TradingStateSnapshot)

    suspend fun load(): TradingStateSnapshot

    suspend fun clear()
}
