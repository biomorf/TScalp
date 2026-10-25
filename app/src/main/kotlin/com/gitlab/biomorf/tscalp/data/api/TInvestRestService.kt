package com.gitlab.biomorf.tscalp.data.api

import com.gitlab.biomorf.tscalp.domain.models.OrderListItem
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.transport.ktor.TInvestRestTransport
import com.gitlab.biomorf.tscalp.util.AppLogger
import io.ktor.client.HttpClient

/**
 * REST-транспорт T-Invest: unary-запросы с fallback-замыканиями
 * для [TInvestOrchestrator]. Вынесен из фасада [TInvestBrokerAPI]
 * (см. ROADMAP -> "### Рефакторинг BrokerApi-реализаций (D)").
 *
 * Требует [state] для `sandboxMode` в [balanceRestFallback]:
 * REST-вызов баланса различается для prod и sandbox.
 */
class TInvestRestService(
    private val state: TInvestApiState,
    private val httpClient: HttpClient,
) {
    private companion object {
        const val REST_BASE_URL_PROD = "https://invest-public-api.tbank.ru"
        const val REST_BASE_URL_SANDBOX = "https://sandbox-invest-public-api.tbank.ru"

        fun buildRestUrl(sandbox: Boolean): String = if (sandbox) REST_BASE_URL_SANDBOX else REST_BASE_URL_PROD
    }

    private var restTransport: TInvestRestTransport? = null

    fun initialize(
        token: String,
        sandbox: Boolean,
    ) {
        restTransport =
            TInvestRestTransport(
                client = httpClient,
                baseUrl = buildRestUrl(sandbox),
                token = token,
                logger = { msg -> AppLogger.d("TInvestRest", msg) },
            )
    }

    fun stop() {
        restTransport = null
    }

    suspend fun pricesRestFallback(uids: List<String>): Map<String, Double?> {
        val rest = restTransport ?: throw IllegalStateException("REST transport not initialized")
        return rest.getLastPrices(uids)
    }

    suspend fun positionsRestFallback(accountId: String): List<PositionStreamItem> {
        val rest = restTransport ?: throw IllegalStateException("REST transport not initialized")
        return rest.fetchPositionsSnapshot(accountId).positions.map { it.toStreamItem() }
    }

    suspend fun ordersRestFallback(accountId: String): List<OrderListItem> {
        val rest = restTransport ?: throw IllegalStateException("REST transport not initialized")
        val orders = rest.getOrders(accountId).map { it.toOrderListItem() }
        val stopOrders = rest.getStopOrders(accountId).map { it.toOrderListItem() }
        return orders + stopOrders
    }

    suspend fun balanceRestFallback(accountId: String): Double {
        val rest = restTransport ?: throw IllegalStateException("REST transport not initialized")
        return rest.getBalance(accountId, state.sandboxMode)
    }
}
