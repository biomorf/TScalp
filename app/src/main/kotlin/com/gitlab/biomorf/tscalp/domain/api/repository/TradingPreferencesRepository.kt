package com.gitlab.biomorf.tscalp.domain.api.repository

import kotlinx.coroutines.flow.Flow

/**
 * Domain-контракт торговых предпочтений (clean-audit Н68, пакет B).
 *
 * DataStore-ключ — `confirm_orders_enabled` (legacy-формат).
 */
interface TradingPreferencesRepository {
    suspend fun isConfirmOrdersEnabled(): Boolean

    suspend fun setConfirmOrdersEnabled(enabled: Boolean)

    /** Поток значения «подтверждение заявок включено». */
    fun confirmOrdersEnabledFlow(): Flow<Boolean>
}
