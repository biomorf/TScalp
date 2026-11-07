package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import kotlinx.coroutines.flow.Flow

/**
 * Domain-контракт хранилища identity карточек «Последние
 * просмотренные» (разбиение `OrdersViewModel` на 4 VM, шаг 2).
 *
 * Хранит **только identity** (`ticker` + `brokerName` + `accountId`).
 * Полная `SelectedInstrumentInfo` (currentPrice, quantity,
 * profit, profitPercent) обогащается в Coordinator из
 * `InstrumentRepository` + `PositionFlowManager.sheet` — в
 * репозитории не хранится.
 *
 * Персистентность — DataStore, legacy-формат.
 */
interface CardsRepository {
    val identities: Flow<List<CardIdentity>>

    suspend fun save(identities: List<CardIdentity>)

    suspend fun clear()
}
