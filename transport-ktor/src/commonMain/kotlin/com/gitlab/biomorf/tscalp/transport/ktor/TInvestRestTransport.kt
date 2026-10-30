package com.gitlab.biomorf.tscalp.transport.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * REST-транспорт для T-Invest API.
 *
 * REST-эндпоинты T-Invest — это прокси Swagger над gRPC unary-методами.
 * Формат запросов и ответов — protobuf JSON mapping (lowerCamelCase).
 *
 * Используется как fallback, когда gRPC unary недоступен
 * (UNAVAILABLE / DEADLINE_EXCEEDED / UNIMPLEMENTED).
 *
 * В отличие от gRPC, здесь нет стримов — только unary-запросы.
 */
class TInvestRestTransport(
    private val client: HttpClient,
    private val baseUrl: String,
    private val token: String,
    private val logger: (String) -> Unit = { },
) {
    companion object {
        private const val TAG = "[REST/ktor/TInvest]"
        private const val SERVICE_PREFIX = "tinkoff.public.invest.api.contract.v1"
    }

    /**
     * Получить последние цены по списку инструментов.
     * Аналог gRPC GetLastPrices.
     */
    suspend fun getLastPrices(ids: List<String>): Map<String, Double?> {
        if (ids.isEmpty()) return emptyMap()

        val url = "$baseUrl/rest/$SERVICE_PREFIX.MarketDataService/GetLastPrices"
        logger("$TAG POST $url (${ids.size} instruments)")

        val request = RestGetLastPricesRequest(instrumentId = ids)
        val response: RestGetLastPricesResponse =
            client
                .post(url) {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()

        return response.lastPrices.associate { lp ->
            lp.instrumentUid to (lp.price?.let { it.units.toLongOrNull()?.toDouble()?.plus(it.nano / 1_000_000_000.0) })
        }
    }

    suspend fun getOrders(accountId: String): List<RestOrder> {
        val url = "$baseUrl/rest/$SERVICE_PREFIX.OrdersService/GetOrders"
        logger("$TAG POST $url (account=$accountId)")
        val request = RestGetOrdersRequest(accountId)
        val response: RestOrdersResponse =
            client
                .post(url) {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()
        return response.orders
    }

    suspend fun getStopOrders(accountId: String): List<RestStopOrder> {
        val url = "$baseUrl/rest/$SERVICE_PREFIX.StopOrdersService/GetStopOrders"
        logger("$TAG POST $url (account=$accountId)")
        val request = RestGetOrdersRequest(accountId)
        val response: RestStopOrdersResponse =
            client
                .post(url) {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()
        return response.stopOrders
    }

    suspend fun fetchPositionsSnapshot(accountId: String): RestPortfolioResponse {
        val url = "$baseUrl/rest/$SERVICE_PREFIX.OperationsService/GetPortfolio"
        logger("$TAG POST $url (account=$accountId)")
        val request = RestGetOrdersRequest(accountId)
        return client
            .post(url) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
    }

    suspend fun getBalance(accountId: String, isSandbox: Boolean): Double {
        return if (isSandbox) {
            val url = "$baseUrl/rest/$SERVICE_PREFIX.SandboxService/GetSandboxPortfolio"
            logger("$TAG POST $url (account=$accountId)")
            val request = RestGetPortfolioRequest(accountId)
            val response: RestPortfolioResponse =
                client
                    .post(url) {
                        header("Authorization", "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.body()
            val totalRub = (response.totalAmountCurrencies?.units?.toLongOrNull() ?: 0L) + (response.totalAmountCurrencies?.nano ?: 0) / 1_000_000_000.0
            val positionsValue = response.positions.filterNot { it.instrumentType == "currency" }.sumOf { pos ->
                val price = pos.currentPrice?.let { it.units.toLongOrNull()?.toDouble()?.plus(it.nano / 1_000_000_000.0) } ?: 0.0
                val qty = pos.quantity?.let { it.units.toLongOrNull()?.toDouble()?.plus(it.nano / 1_000_000_000.0) } ?: 0.0
                price * qty
            }
            totalRub - positionsValue
        } else {
            val url = "$baseUrl/rest/$SERVICE_PREFIX.UsersService/GetMarginAttributes"
            logger("$TAG POST $url (account=$accountId)")
            val request = RestGetMarginAttributesRequest(accountId)
            val response: RestMarginAttributesResponse =
                client
                    .post(url) {
                        header("Authorization", "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.body()
            (response.liquidPortfolio?.units?.toLongOrNull() ?: 0L) + (response.liquidPortfolio?.nano ?: 0) / 1_000_000_000.0
        }
    }
}

@Serializable
data class RestGetLastPricesRequest(
    @SerialName("instrumentId") val instrumentId: List<String>,
)

@Serializable
data class RestGetLastPricesResponse(
    @SerialName("lastPrices") val lastPrices: List<RestLastPrice> = emptyList(),
)

@Serializable
data class RestLastPrice(
    @SerialName("instrumentUid") val instrumentUid: String = "",
    @SerialName("price") val price: RestQuotation? = null,
)

@Serializable
data class RestQuotation(
    @SerialName("units") val units: String = "0",
    @SerialName("nano") val nano: Int = 0,
)

@Serializable
data class RestGetOrdersRequest(@SerialName("accountId") val accountId: String)

@Serializable
data class RestGetPortfolioRequest(@SerialName("accountId") val accountId: String)

@Serializable
data class RestOrdersResponse(@SerialName("orders") val orders: List<RestOrder> = emptyList())

@Serializable
data class RestOrder(
    @SerialName("orderId") val orderId: String = "",
    @SerialName("instrumentUid") val instrumentUid: String = "",
    @SerialName("ticker") val ticker: String = "",
    @SerialName("classCode") val classCode: String = "",
    @SerialName("direction") val direction: String = "",
    @SerialName("lotsRequested") val lotsRequested: Long = 0,
    @SerialName("lotsExecuted") val lotsExecuted: Long = 0,
    @SerialName("initialOrderPrice") val initialOrderPrice: RestQuotation? = null,
    @SerialName("executedOrderPrice") val executedOrderPrice: RestQuotation? = null,
    @SerialName("orderType") val orderType: String = "",
    @SerialName("executionReportStatus") val executionReportStatus: String = "",
    @SerialName("orderDate") val orderDate: String? = null,
)

@Serializable
data class RestStopOrdersResponse(@SerialName("stopOrders") val stopOrders: List<RestStopOrder> = emptyList())

@Serializable
data class RestStopOrder(
    @SerialName("stopOrderId") val stopOrderId: String = "",
    @SerialName("instrumentUid") val instrumentUid: String = "",
    @SerialName("ticker") val ticker: String = "",
    @SerialName("classCode") val classCode: String = "",
    @SerialName("direction") val direction: String = "",
    @SerialName("lotsRequested") val lotsRequested: Long = 0,
    @SerialName("stopPrice") val stopPrice: RestQuotation? = null,
    @SerialName("limitPrice") val limitPrice: RestQuotation? = null,
    @SerialName("orderType") val orderType: String = "",
    @SerialName("executionReportStatus") val executionReportStatus: String = "",
    @SerialName("createDate") val createDate: String? = null,
)

@Serializable
data class RestPortfolioResponse(
    @SerialName("positions") val positions: List<RestPosition> = emptyList(),
    @SerialName("totalAmountCurrencies") val totalAmountCurrencies: RestQuotation? = null,
)

@Serializable
data class RestPosition(
    @SerialName("instrumentUid") val instrumentUid: String = "",
    @SerialName("quantity") val quantity: RestQuotation? = null,
    @SerialName("currentPrice") val currentPrice: RestQuotation? = null,
    @SerialName("averagePositionPrice") val averagePositionPrice: RestQuotation? = null,
    @SerialName("expectedYield") val expectedYield: RestQuotation? = null,
    @SerialName("instrumentType") val instrumentType: String = "",
)

@Serializable
data class RestGetMarginAttributesRequest(@SerialName("accountId") val accountId: String)

@Serializable
data class RestMarginAttributesResponse(
    @SerialName("liquidPortfolio") val liquidPortfolio: RestQuotation? = null,
)
