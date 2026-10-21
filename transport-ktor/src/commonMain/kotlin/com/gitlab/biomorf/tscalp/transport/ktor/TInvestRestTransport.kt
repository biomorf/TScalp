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
}

@Serializable
internal data class RestGetLastPricesRequest(
    @SerialName("instrumentId") val instrumentId: List<String>,
)

@Serializable
internal data class RestGetLastPricesResponse(
    @SerialName("lastPrices") val lastPrices: List<RestLastPrice> = emptyList(),
)

@Serializable
internal data class RestLastPrice(
    @SerialName("instrumentUid") val instrumentUid: String = "",
    @SerialName("price") val price: RestQuotation? = null,
)

@Serializable
internal data class RestQuotation(
    @SerialName("units") val units: String = "0",
    @SerialName("nano") val nano: Int = 0,
)
