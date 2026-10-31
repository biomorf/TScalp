package com.gitlab.biomorf.tscalp.data.orchestrator

import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestAccountsService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestInstrumentsService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestOrdersService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestPortfolioService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestPositionsService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestPricesService
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestTradingService
import com.gitlab.biomorf.tscalp.domain.log.TestLogger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
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
    private val positions = mockk<TInvestPositionsService>()
    private val accounts = mockk<TInvestAccountsService>()
    private val orders = mockk<TInvestOrdersService>()
    private val portfolio = mockk<TInvestPortfolioService>()
    private val prices = mockk<TInvestPricesService>()
    private val trading = mockk<TInvestTradingService>()
    private val instruments = mockk<TInvestInstrumentsService>()

    @Test
    fun `pricesSnapshot falls back to REST on UNAVAILABLE`() =
        runBlocking {
            val restData = mapOf("uid1" to 100.0)
            coEvery { prices.getLastPricesByTscalpInstrumentId(any()) } throws StatusException(Status.UNAVAILABLE)

            val orchestrator =
                TInvestOrchestrator(
                    positions = positions,
                    orders = orders,
                    accounts = accounts,
                    portfolio = portfolio,
                    prices = prices,
                    trading = trading,
                    instruments = instruments,
                    debugFlagsRepository = mockk(relaxed = true),
                    logger = TestLogger,
                    pricesRestFallback = { restData },
                )

            val result = orchestrator.pricesSnapshot(listOf("uid1"))
            assertEquals(restData, result)
            coVerify(exactly = 1) { prices.getLastPricesByTscalpInstrumentId(any()) }
        }

    @Test
    fun `pricesSnapshot does not fall back on business error`() =
        runBlocking {
            coEvery { prices.getLastPricesByTscalpInstrumentId(any()) } throws StatusException(Status.INVALID_ARGUMENT)

            var fallbackCalled = false
            val orchestrator =
                TInvestOrchestrator(
                    positions = positions,
                    orders = orders,
                    accounts = accounts,
                    portfolio = portfolio,
                    prices = prices,
                    trading = trading,
                    instruments = instruments,
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
                    positions = positions,
                    orders = orders,
                    accounts = accounts,
                    portfolio = portfolio,
                    prices = prices,
                    trading = trading,
                    instruments = instruments,
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
                    positions = positions,
                    orders = orders,
                    accounts = accounts,
                    portfolio = portfolio,
                    prices = prices,
                    trading = trading,
                    instruments = instruments,
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
            coEvery { portfolio.fetchPortfolioPositions(any(), any()) } throws StatusException(Status.UNAVAILABLE)

            val orchestrator =
                TInvestOrchestrator(
                    positions = positions,
                    orders = orders,
                    accounts = accounts,
                    portfolio = portfolio,
                    prices = prices,
                    trading = trading,
                    instruments = instruments,
                    debugFlagsRepository = mockk(relaxed = true),
                    logger = TestLogger,
                    positionsRestFallback = { _, _ -> restPositions },
                )

            val result = orchestrator.positionsSnapshot("acc1", false)
            assertEquals(restPositions, result)
            coVerify(exactly = 1) { portfolio.fetchPortfolioPositions(any(), any()) }
        }

    @Test
    fun `findInstrumentsWithFallback falls back to REST on UNAVAILABLE`() =
        runBlocking {
            val restData = listOf<InstrumentUi>(mockk())
            coEvery { instruments.findInstruments(any()) } throws StatusException(Status.UNAVAILABLE)

            val orchestrator =
                TInvestOrchestrator(
                    positions = positions,
                    orders = orders,
                    accounts = accounts,
                    portfolio = portfolio,
                    prices = prices,
                    trading = trading,
                    instruments = instruments,
                    debugFlagsRepository = mockk(relaxed = true),
                    logger = TestLogger,
                    findInstrumentsRestFallback = { restData },
                )

            val result = orchestrator.findInstrumentsWithFallback("sber")
            assertEquals(restData, result)
            coVerify(exactly = 1) { instruments.findInstruments(any()) }
        }

    @Test
    fun `findInstrumentsWithFallback does not fall back on business error`() =
        runBlocking {
            coEvery { instruments.findInstruments(any()) } throws StatusException(Status.INVALID_ARGUMENT)

            var fallbackCalled = false
            val orchestrator =
                TInvestOrchestrator(
                    positions = positions,
                    orders = orders,
                    accounts = accounts,
                    portfolio = portfolio,
                    prices = prices,
                    trading = trading,
                    instruments = instruments,
                    debugFlagsRepository = mockk(relaxed = true),
                    logger = TestLogger,
                    findInstrumentsRestFallback = {
                        fallbackCalled = true
                        emptyList()
                    },
                )

            try {
                orchestrator.findInstrumentsWithFallback("sber")
                fail("Expected StatusException")
            } catch (e: StatusException) {
                assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
            }
            assertFalse(fallbackCalled)
        }
}
