package com.gitlab.biomorf.tscalp.data.orchestrator

import com.gitlab.biomorf.tscalp.data.api.TInvestMarketDataService
import com.gitlab.biomorf.tscalp.domain.api.streamWithFallback
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import kotlinx.coroutines.flow.Flow

/**
 * Оркестратор стримов T-Invest.
 *
 * Композирует два уровня (стрим и polling) с fallback'ами.
 * Не использует Hilt-аннотации и Android-импорты —
 * готов к переносу в KMP-модуль.
 *
 * Фаза 1: только gRPC-стрим + polling (WS-транспорт пока null).
 * Фаза 2 добавит WS как fallback стрим-уровня.
 */
class TInvestOrchestrator(
    private val marketData: TInvestMarketDataService,
) {
    fun positionsStream(accountId: String): Flow<PositionStreamItem> =
        streamWithFallback(
            primary = marketData.subscribePositionsGrpcWithReconnect(accountId),
            fallback = null,
            polling = marketData.subscribePositionsPolling(accountId),
        )
}
