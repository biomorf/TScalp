package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.flow.Flow

/**
 * Domain-контракт пользовательских настроек.
 *
 * Реализация — `data/repository/SettingsRepositoryImpl.kt` (H21b).
 * Создан в рамках H21b, поведение перенесено из
 * `data.repository.SettingsRepository` без изменений.
 *
 * `confirmOrdersEnabledFlow` — функция (не свойство): консистентно
 * с остальными членами интерфейса (только `fun`); решение 2026-10-27.
 */
@Suppress("TooManyFunctions")
interface SettingsRepository {
    // ---------- Учётные данные брокеров ----------

    suspend fun saveBrokerCredentials(
        brokerName: BrokerName,
        token: String,
        sandbox: Boolean,
    )

    suspend fun loadBrokerCredentials(brokerName: BrokerName): Pair<String, Boolean>?

    suspend fun clearBrokerCredentials(brokerName: BrokerName)

    suspend fun saveToken(
        brokerName: BrokerName,
        token: String,
    )

    suspend fun hasSavedToken(brokerName: BrokerName): Boolean

    suspend fun getToken(brokerName: BrokerName): String?

    // ---------- Счёт по умолчанию ----------

    suspend fun saveDefaultAccountId(
        brokerName: BrokerName,
        accountId: String,
    )

    suspend fun loadDefaultAccountId(brokerName: BrokerName): String?

    // ---------- Режим песочницы ----------

    suspend fun isSandboxMode(): Boolean

    suspend fun setSandboxMode(enabled: Boolean)

    // ---------- Подтверждение заявок ----------

    suspend fun isConfirmOrdersEnabled(): Boolean

    suspend fun setConfirmOrdersEnabled(enabled: Boolean)

    /** Поток значения «подтверждение заявок включено». */
    fun confirmOrdersEnabledFlow(): Flow<Boolean>
}
