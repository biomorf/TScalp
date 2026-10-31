package com.gitlab.biomorf.tscalp.data.orchestrator

import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestAccountsService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestMarketDataService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestOrdersService
import com.gitlab.biomorf.tscalp.domain.log.TestLogger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Order
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import io.grpc.Status
import io.grpc.StatusException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class TInvestOrchestratorFallbackTest {
    private val marketData = mockk<TInvestMarketDataService>()
    private val accounts = mockk<TInvestAccountsService>()
    private val orders = mockk<TInvestOrdersService>()

    @Test
    fun `pricesSnapshot falls back to REST on UNAVAILABLE`() =
        runBlocking {
            val restData = mapOf("uid1" to 100.0)
            coEvery { marketData.getLastPricesByTscalpInstrumentId(any()) } throws StatusException(Status.UNAVAILABLE)

            val orchestrator =
                TInvestOrchestrator(
                    marketData = marketData,
                    orders = orders,
                    accounts = accounts,
                    debugFlagsRepository = mockk(relaxed = true),
                    logger = TestLogger,
                    pricesRestFallback = { restData },
                )

            val result = orchestrator.pricesSnapshot(listOf("uid1"))
            assertEquals(restData, result)
            coVerify(exactly = 1) { marketData.getLastPricesByTscalpInstrumentId(any()) }
        }

    @Test
    fun `pricesSnapshot does not fall back on business error`() =
        runBlocking {
            coEvery { marketData.getLastPricesByTscalpInstrumentId(any()) } throws StatusException(Status.INVALID_ARGUMENT)

            var fallbackCalled = false
            val orchestrator =
                TInvestOrchestrator(
                    marketData = marketData,
                    orders = orders,
                    accounts = accounts,
                    debugFlagsRepository = mockk(relaxed = true),
                    logger = TestLogger,
                    pricesRestFallback = {
                        fallbackCalled = true
                        emptyMap()
                    },
                )

            try {
                orchestrator.pricesSnapshot(listOf("uid1"))
                fail("Expected StatusException")
            } catch (e: StatusException) {
                assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
            }
            assertFalse(fallbackCalled)
        }

    @Test
    fun `ordersSnapshot falls back to REST on UNAVAILABLE`() =
        runBlocking {
            val restOrders = listOf<Order>(mockk())
            coEvery { orders.getOrders(any()) } throws StatusException(Status.UNAVAILABLE)

            val orchestrator =
                TInvestOrchestrator(
                    marketData = marketData,
                    orders = orders,
                    accounts = accounts,
                    debugFlagsRepository = mockk(relaxed = true),
                    logger = TestLogger,
                    ordersRestFallback = { restOrders },
                )

            val result = orchestrator.ordersSnapshot("acc1")
            assertEquals(restOrders, result)
            coVerify(exactly = 1) { orders.getOrders(any()) }
        }

    @Test
    fun `balanceSnapshot falls back to REST on UNAVAILABLE`() =
        runBlocking {
            val restBalance = 5000.0
            coEvery { accounts.getBalance(any()) } throws StatusException(Status.UNAVAILABLE)

            val orchestrator =
                TInvestOrchestrator(
                    marketData = marketData,
                    orders = orders,
                    accounts = accounts,
                    debugFlagsRepository = mockk(relaxed = true),
                    logger = TestLogger,
                    balanceRestFallback = { restBalance },
                )

            val result = orchestrator.balanceSnapshot("acc1")
            assertEquals(restBalance, result, 0.0001)
            coVerify(exactly = 1) { accounts.getBalance(any()) }
        }

    @Test
    fun `positionsSnapshot falls back to REST on UNAVAILABLE`() =
        runBlocking {
            val restPositions =
                listOf(
                    PositionStreamItem(
                        tscalpInstrumentId = "uid1",
                        brokerName = BrokerName.TINVEST,
                        ticker = "TEST",
                        quantity = 1L,
                    ),
                )
            coEvery { marketData.fetchPortfolioPositions(any(), any()) } throws StatusException(Status.UNAVAILABLE)

            val orchestrator =
                TInvestOrchestrator(
                    marketData = marketData,
                    orders = orders,
                    accounts = accounts,
                    debugFlagsRepository = mockk(relaxed = true),
                    logger = TestLogger,
                    positionsRestFallback = { _, _ -> restPositions },
                )

            val result = orchestrator.positionsSnapshot("acc1", false)
            assertEquals(restPositions, result)
            coVerify(exactly = 1) { marketData.fetchPortfolioPositions(any(), any()) }
        }
}
