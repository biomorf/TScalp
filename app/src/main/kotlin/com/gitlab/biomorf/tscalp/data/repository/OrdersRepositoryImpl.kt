package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.api.repository.OrdersRepository
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderResult
import com.gitlab.biomorf.tscalp.domain.models.StopOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult
import com.gitlab.biomorf.tscalp.util.runCatchingAppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [OrdersRepository] поверх [BrokerManager].
 *
 * Тела методов перенесены из `InvestRepository` без изменений
 * (Phase 1 step 3 InvestRepository split).
 */
@Singleton
class OrdersRepositoryImpl
    @Inject
    constructor(
        private val brokerManager: BrokerManager,
    ) : OrdersRepository {
        override suspend fun postOrderResult(
            request: BrokerOrderRequest,
        ): AppResult<OrderResult> =
            withContext(Dispatchers.IO) {
                val broker =
                    brokerManager.getBroker(request.brokerName)
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${request.brokerName.displayName} не зарегистрирован"),
                        )
                val ordersApi =
                    broker.orders
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${request.brokerName.displayName} не поддерживает заявки"),
                        )
                runCatchingAppResult(request.brokerName) { ordersApi.postOrder(request) }
            }

        override suspend fun postStopOrderResult(
            request: StopOrderRequest,
        ): AppResult<String> =
            withContext(Dispatchers.IO) {
                val broker =
                    brokerManager.getBroker(request.brokerName)
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${request.brokerName.displayName} не зарегистрирован"),
                        )
                val ordersApi =
                    broker.orders
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${request.brokerName.displayName} не поддерживает заявки"),
                        )
                runCatchingAppResult(request.brokerName) { ordersApi.postStopOrder(request) }
            }

        override suspend fun cancelStopOrderResult(
            accountId: String,
            orderId: String,
        ): AppResult<Unit> =
            withContext(Dispatchers.IO) {
                val broker = brokerManager.getDefaultBroker()
                val ordersApi =
                    broker.orders
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${BrokerName.TINVEST.displayName} не поддерживает заявки"),
                        )
                runCatchingAppResult(BrokerName.TINVEST) {
                    ordersApi.cancelStopOrder(accountId, orderId)
                    Unit
                }
            }

        override suspend fun cancelOrderResult(
            brokerName: BrokerName,
            accountId: String,
            orderId: String,
        ): AppResult<Unit> =
            withContext(Dispatchers.IO) {
                val broker =
                    brokerManager.getBroker(brokerName)
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                        )
                val ordersApi =
                    broker.orders
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${brokerName.displayName} не поддерживает заявки"),
                        )
                runCatchingAppResult(brokerName) {
                    ordersApi.cancelOrder(accountId, orderId)
                    Unit
                }
            }

        override suspend fun checkTradeAvailabilityResult(
            brokerName: BrokerName,
            accountId: String,
            tscalpInstrumentId: String,
            direction: OrderDirection,
            quantity: Long,
        ): AppResult<TradeCheckResult> =
            withContext(Dispatchers.IO) {
                val broker =
                    brokerManager.getBroker(brokerName)
                        ?: return@withContext AppResult.Failure(
                            AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                        )
                runCatchingAppResult(brokerName) {
                    broker.checkTradeAvailability(
                        accountId = accountId,
                        tscalpInstrumentId = tscalpInstrumentId,
                        brokerUid = tscalpInstrumentId,
                        direction = direction,
                        quantity = quantity,
                    )
                }
            }
    }
