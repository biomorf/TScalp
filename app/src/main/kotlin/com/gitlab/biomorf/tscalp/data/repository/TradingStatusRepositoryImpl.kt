package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStatusRepository
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory реализация [TradingStatusRepository]
 * (разбиение `OrdersViewModel` на 4 VM, шаг 4).
 *
 * `update` — merge (`statuses + incoming`), как в
 * `OrdersViewModel.updateTradingStatuses`.
 */
@Singleton
class TradingStatusRepositoryImpl
    @Inject
    constructor() : TradingStatusRepository {
        private val _statuses = MutableStateFlow<Map<String, TradingAvailability>>(emptyMap())
        override val statuses: StateFlow<Map<String, TradingAvailability>> = _statuses.asStateFlow()

        override suspend fun update(statuses: Map<String, TradingAvailability>) {
            _statuses.update { it + statuses }
        }

        override suspend fun clear() {
            _statuses.value = emptyMap()
        }
    }
