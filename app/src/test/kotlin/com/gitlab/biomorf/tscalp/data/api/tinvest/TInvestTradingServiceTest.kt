package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.data.api.common.TransportProtocol
import com.gitlab.biomorf.tscalp.data.api.common.TransportRateLimiter
import com.gitlab.biomorf.tscalp.domain.log.TestLogger
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tinkoff.piapi.contract.v1.GetTradingStatusRequest
import ru.tinkoff.piapi.contract.v1.GetTradingStatusResponse
import ru.tinkoff.piapi.contract.v1.GetTradingStatusesResponse
import ru.ttech.piapi.core.InvestApi
import ru.ttech.piapi.core.MarketDataServiceSync

class TInvestTradingServiceTest {
    private val state = mockk<TInvestApiState>(relaxed = true)
    private val accountsService = mockk<TInvestAccountsService>(relaxed = true)
    private val pricesService = mockk<TInvestPricesService>(relaxed = true)

    // Pass-through limiter: relaxed mockk does not invoke the block
    // for suspend generic T; use a real noop so gRPC mocks are hit.
    private val rateLimiter =
        object : TransportRateLimiter {
            override suspend fun <T> withUnaryPermission(
                protocol: TransportProtocol,
                service: String,
                method: String?,
                block: suspend () -> T,
            ): T = block()

            override suspend fun <T> withStreamSlot(
                protocol: TransportProtocol,
                service: String,
                block: suspend () -> T,
            ): T = block()
        }
    private val marketDataServiceSync = mockk<MarketDataServiceSync>(relaxed = true)
    private val investApi = mockk<InvestApi>(relaxed = true)

    private fun createService(): TInvestTradingService {
        every { state.api } returns investApi
        every { investApi.marketDataServiceSync } returns marketDataServiceSync
        return TInvestTradingService(state, TestLogger, accountsService, pricesService, rateLimiter)
    }

    @Test
    fun `batch path - 1 unary for all uids, 0 singular`() =
        runBlocking {
            val service = createService()
            val uids = listOf("uid-a", "uid-b", "uid-c")
            val batchResponse =
                GetTradingStatusesResponse
                    .newBuilder()
                    .addTradingStatuses(statusResponse("uid-a", true))
                    .addTradingStatuses(statusResponse("uid-b", false))
                    .addTradingStatuses(statusResponse("uid-c", true))
                    .build()
            every { marketDataServiceSync.getTradingStatuses(any()) } returns batchResponse

            val result = service.getTradingStatuses(uids)

            assertEquals(
                mapOf(
                    "uid-a" to TradingAvailability.AVAILABLE,
                    "uid-b" to TradingAvailability.UNAVAILABLE,
                    "uid-c" to TradingAvailability.AVAILABLE,
                ),
                result,
            )
            verify(exactly = 1) { marketDataServiceSync.getTradingStatuses(any()) }
            verify(exactly = 0) { marketDataServiceSync.getTradingStatus(any()) }
        }

    @Test
    fun `batch path - missing uid falls back to UNKNOWN`() =
        runBlocking {
            val service = createService()
            val uids = listOf("uid-a", "uid-b")
            val batchResponse =
                GetTradingStatusesResponse
                    .newBuilder()
                    .addTradingStatuses(statusResponse("uid-a", true))
                    .build()
            every { marketDataServiceSync.getTradingStatuses(any()) } returns batchResponse

            val result = service.getTradingStatuses(uids)

            assertEquals(TradingAvailability.AVAILABLE, result["uid-a"])
            assertEquals(TradingAvailability.UNKNOWN, result["uid-b"])
        }

    @Test
    fun `batch failure - fallback to singular path`() =
        runBlocking {
            val service = createService()
            val uids = listOf("uid-a", "uid-b")
            every { marketDataServiceSync.getTradingStatuses(any()) } throws RuntimeException("batch failed")
            every { marketDataServiceSync.getTradingStatus(any()) } answers {
                val uid = firstArg<GetTradingStatusRequest>().instrumentId
                when (uid) {
                    "uid-a" -> statusResponse("uid-a", true)
                    "uid-b" -> statusResponse("uid-b", false)
                    else -> statusResponse(uid, false)
                }
            }
            every { investApi.marketDataServiceSync } returns marketDataServiceSync

            val result = service.getTradingStatuses(uids)

            assertEquals(TradingAvailability.AVAILABLE, result["uid-a"])
            assertEquals(TradingAvailability.UNAVAILABLE, result["uid-b"])
            verify(exactly = 2) { marketDataServiceSync.getTradingStatus(any()) }
        }

    @Test
    fun `empty uids - no calls`() =
        runBlocking {
            val service = createService()

            val result = service.getTradingStatuses(emptyList())

            assertEquals(emptyMap<String, TradingAvailability>(), result)
            verify(exactly = 0) { marketDataServiceSync.getTradingStatuses(any()) }
            verify(exactly = 0) { marketDataServiceSync.getTradingStatus(any()) }
        }

    private fun statusResponse(
        uid: String,
        available: Boolean,
    ): GetTradingStatusResponse =
        GetTradingStatusResponse
            .newBuilder()
            .setInstrumentUid(uid)
            .setApiTradeAvailableFlag(available)
            .build()
}
