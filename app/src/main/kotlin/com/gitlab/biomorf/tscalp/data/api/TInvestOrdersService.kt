package com.gitlab.biomorf.tscalp.data.api

import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderType
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderListItem
import com.gitlab.biomorf.tscalp.domain.models.OrderResult
import com.gitlab.biomorf.tscalp.domain.models.OrderStatus
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType as DomainStopOrderExpirationType
import com.gitlab.biomorf.tscalp.domain.models.StopOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.StopOrderType as DomainStopOrderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.CancelOrderRequest
import ru.tinkoff.piapi.contract.v1.CancelStopOrderRequest
import ru.tinkoff.piapi.contract.v1.GetOrdersRequest
import ru.tinkoff.piapi.contract.v1.GetStopOrdersRequest
import ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus
import ru.tinkoff.piapi.contract.v1.PostOrderRequest
import ru.tinkoff.piapi.contract.v1.PostStopOrderRequest
import ru.tinkoff.piapi.contract.v1.Quotation
import ru.tinkoff.piapi.contract.v1.StopOrderExpirationType as ProtoStopOrderExpirationType
import ru.tinkoff.piapi.contract.v1.StopOrderType as ProtoStopOrderType

/**
 * Заявки T-Invest: обычные, стоп-заявки и их отмена.
 * Работает на разделяемом TInvestApiState.
 */
class TInvestOrdersService(
    private val state: TInvestApiState
) {

    companion object {
        private const val TAG = "TInvestOrders"
    }

    // ---------- Обычные заявки ----------

    suspend fun postOrder(request: BrokerOrderRequest): OrderResult = withContext(Dispatchers.IO) {
        val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
        val uid = request.tscalpInstrumentId
            ?: throw IllegalArgumentException("BrokerOrderRequest.tscalpInstrumentId не может быть null")

        val price = if (request.type == BrokerOrderType.LIMIT && request.price != null) {
            TInvestConverters.doubleToQuotation(request.price)
        } else {
            Quotation.newBuilder().setUnits(1).setNano(0).build()
        }

        val apiOrderType = when (request.type) {
            BrokerOrderType.MARKET -> ru.tinkoff.piapi.contract.v1.OrderType.ORDER_TYPE_MARKET
            BrokerOrderType.LIMIT -> ru.tinkoff.piapi.contract.v1.OrderType.ORDER_TYPE_LIMIT
        }
        val apiDirection = when (request.direction) {
            OrderDirection.BUY -> ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_BUY
            OrderDirection.SELL -> ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_SELL
        }

        val apiRequest = PostOrderRequest.newBuilder()
            .setInstrumentId(uid)
            .setQuantity(request.quantity)
            .setPrice(price)
            .setDirection(apiDirection)
            .setAccountId(request.accountId)
            .setOrderType(apiOrderType)
            .setConfirmMarginTrade(true)
            .build()

        val response = if (request.sandboxMode) {
            currentApi.sandboxServiceSync.postSandboxOrder(apiRequest)
        } else {
            currentApi.ordersServiceSync.postOrder(apiRequest)
        }

        OrderResult(
            orderId = response.orderId,
            executedLots = response.lotsExecuted,
            totalLots = response.lotsRequested,
            status = OrderStatus.NEW,
            orderRequestId = response.orderRequestId
        )
    }

    suspend fun getOrders(accountId: String): List<OrderListItem> = withContext(Dispatchers.IO) {
        val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
        val request = GetOrdersRequest.newBuilder().setAccountId(accountId).build()
        val response = if (state.sandboxMode) {
            currentApi.sandboxServiceSync.getSandboxOrders(request)
        } else {
            currentApi.ordersServiceSync.getOrders(request)
        }

        val activeStatuses = setOf(
            OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_NEW,
            OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_PARTIALLYFILL
        )
        response.ordersList
            .filter { it.executionReportStatus in activeStatuses }
            .map { order -> TInvestOrdersMapper.mapOrder(order) }
    }

    suspend fun cancelOrder(accountId: String, orderId: String) = withContext(Dispatchers.IO) {
        val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
        val request = CancelOrderRequest.newBuilder()
            .setAccountId(accountId).setOrderId(orderId).build()
        if (state.sandboxMode) currentApi.sandboxServiceSync.cancelSandboxOrder(request)
        else currentApi.ordersServiceSync.cancelOrder(request)
    }

    // ---------- Стоп-заявки ----------

    suspend fun postStopOrder(request: StopOrderRequest): String = withContext(Dispatchers.IO) {
        val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
        val uid = request.tscalpInstrumentId
            ?: throw IllegalArgumentException("StopOrderRequest.tscalpInstrumentId не может быть null")

        val builder = PostStopOrderRequest.newBuilder()
            .setInstrumentId(uid)
            .setQuantity(request.quantity)
            .setDirection(protoDirection(request.direction))
            .setAccountId(request.accountId)
            .setStopPrice(TInvestConverters.doubleToQuotation(request.stopPrice))
            .setStopOrderType(protoStopOrderType(request.stopOrderType))
            .setExpirationType(protoExpirationType(request.expirationType))
            .setConfirmMarginTrade(true)
        if (request.price != null) builder.setPrice(TInvestConverters.doubleToQuotation(request.price))
        if (request.expireDate != null) builder.setExpireDate(parseDate(request.expireDate))

        val response = if (state.sandboxMode) {
            currentApi.sandboxServiceSync.postSandboxStopOrder(builder.build())
        } else {
            currentApi.stopOrdersServiceSync.postStopOrder(builder.build())
        }
        response.stopOrderId
    }

    suspend fun getStopOrders(accountId: String): List<OrderListItem> = withContext(Dispatchers.IO) {
        val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
        val request = GetStopOrdersRequest.newBuilder().setAccountId(accountId).build()
        val response = if (state.sandboxMode) {
            currentApi.sandboxServiceSync.getSandboxStopOrders(request)
        } else {
            currentApi.stopOrdersServiceSync.getStopOrders(request)
        }
        response.stopOrdersList.map { order -> TInvestOrdersMapper.mapStopOrder(order) }
    }

    suspend fun cancelStopOrder(accountId: String, stopOrderId: String) = withContext(Dispatchers.IO) {
        val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
        val request = CancelStopOrderRequest.newBuilder()
            .setAccountId(accountId).setStopOrderId(stopOrderId).build()
        if (state.sandboxMode) currentApi.sandboxServiceSync.cancelSandboxStopOrder(request)
        else currentApi.stopOrdersServiceSync.cancelStopOrder(request)
    }

    // ---------- Хелперы ----------

    private fun protoDirection(direction: OrderDirection) = when (direction) {
        OrderDirection.BUY -> ru.tinkoff.piapi.contract.v1.StopOrderDirection.STOP_ORDER_DIRECTION_BUY
        OrderDirection.SELL -> ru.tinkoff.piapi.contract.v1.StopOrderDirection.STOP_ORDER_DIRECTION_SELL
    }

    private fun protoStopOrderType(type: DomainStopOrderType) = when (type) {
        DomainStopOrderType.TAKE_PROFIT -> ProtoStopOrderType.STOP_ORDER_TYPE_TAKE_PROFIT
        DomainStopOrderType.STOP_LOSS -> ProtoStopOrderType.STOP_ORDER_TYPE_STOP_LOSS
        DomainStopOrderType.STOP_LIMIT -> ProtoStopOrderType.STOP_ORDER_TYPE_STOP_LIMIT
    }

    private fun protoExpirationType(expiration: DomainStopOrderExpirationType) = when (expiration) {
        DomainStopOrderExpirationType.GOOD_TILL_CANCEL ->
            ProtoStopOrderExpirationType.STOP_ORDER_EXPIRATION_TYPE_GOOD_TILL_CANCEL
        DomainStopOrderExpirationType.GOOD_TILL_DATE ->
            ProtoStopOrderExpirationType.STOP_ORDER_EXPIRATION_TYPE_GOOD_TILL_DATE
    }

    private fun parseDate(dateStr: String): com.google.protobuf.Timestamp =
        com.google.protobuf.Timestamp.newBuilder().build()
}
