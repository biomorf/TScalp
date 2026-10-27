package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderResult
import com.gitlab.biomorf.tscalp.domain.models.StopOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult

/**
 * Domain-контракт операций с заявками.
 *
 * Реализация — `data/repository/OrdersRepositoryImpl.kt` (Phase 1 step 3).
 * Перенесён из `InvestRepository` без изменения поведения (Phase 1).
 *
 * @see com.gitlab.biomorf.tscalp.domain.api.roles.OrdersApi
 */
interface OrdersRepository {
    /** Отправить обычную (рыночную или лимитную) заявку. */
    suspend fun postOrderResult(
        request: BrokerOrderRequest,
    ): AppResult<OrderResult>

    /** Выставить стоп-заявку. Возвращает stopOrderId. */
    suspend fun postStopOrderResult(
        request: StopOrderRequest,
    ): AppResult<String>

    /** Отменить стоп-заявку. */
    suspend fun cancelStopOrderResult(
        accountId: String,
        orderId: String,
    ): AppResult<Unit>

    /** Отменить обычную заявку. */
    suspend fun cancelOrderResult(
        brokerName: BrokerName,
        accountId: String,
        orderId: String,
    ): AppResult<Unit>

    /** Предварительная проверка возможности сделки. */
    suspend fun checkTradeAvailabilityResult(
        brokerName: BrokerName,
        accountId: String,
        tscalpInstrumentId: String,
        direction: OrderDirection,
        quantity: Long,
    ): AppResult<TradeCheckResult>
}
