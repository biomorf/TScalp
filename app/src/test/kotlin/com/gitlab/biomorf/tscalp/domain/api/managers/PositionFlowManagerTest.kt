package com.gitlab.biomorf.tscalp.domain.api.managers

import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.data.registry.BrokerRegistryImpl
import com.gitlab.biomorf.tscalp.domain.log.TestLogger
import com.gitlab.biomorf.tscalp.domain.models.AccountBalance
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Currency
import com.gitlab.biomorf.tscalp.domain.models.CurrencyPosition
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.SharePosition
import com.gitlab.biomorf.tscalp.domain.models.StreamState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PositionFlowManagerTest {
    private val acc1 = AccountKey(BrokerName.TINVEST, "acc-1")

    private fun shareInstrument(uid: String): Share =
        Share(
            tscalpInstrumentId = uid,
            ticker = "T-$uid",
            name = "Name $uid",
            isin = "",
        )

    private fun sharePosition(
        uid: String,
        qty: Double,
        price: Double,
        accountId: String = "acc-1",
    ): SharePosition =
        SharePosition(
            instrument = shareInstrument(uid),
            brokerName = BrokerName.TINVEST,
            accountId = accountId,
            quantity = qty,
            currentPrice = price,
        )

    private fun currencyPosition(
        uid: String,
        currency: String,
        qty: Double,
        price: Double,
        accountId: String = "acc-1",
    ): CurrencyPosition =
        CurrencyPosition(
            instrument =
                Currency(
                    tscalpInstrumentId = uid,
                    ticker = currency.uppercase(),
                    name = currency.uppercase(),
                    isin = "",
                    currency = currency,
                ),
            brokerName = BrokerName.TINVEST,
            accountId = accountId,
            quantity = qty,
            currentPrice = price,
        )

    private fun accountPortfolio(
        positions: List<Position> = emptyList(),
        balances: List<AccountBalance> = emptyList(),
        accountId: String = "acc-1",
    ): AccountPortfolio =
        AccountPortfolio(
            accountId = accountId,
            brokerName = BrokerName.TINVEST,
            positions = positions,
            balances = balances,
        )

    private fun stubBroker(streamFlow: kotlinx.coroutines.flow.Flow<PositionStreamItem>): TInvestBrokerAPI {
        val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
        every { mockBroker.name } returns BrokerName.TINVEST
        every { mockBroker.isInitialized } returns true
        every { mockBroker.portfolio } returns mockBroker
        every { mockBroker.streams } returns mockBroker
        every { mockBroker.subscribePositions(any()) } returns streamFlow
        return mockBroker
    }

    @Test
    fun `sheet is populated from polling account portfolio`() =
        runBlocking {
            val mockBroker = stubBroker(flow { awaitCancellation() })
            coEvery { mockBroker.fetchPortfolioPositions("acc-1", false) } returns
                accountPortfolio(positions = listOf(sharePosition("uid-sber", qty = 2.0, price = 292.6)))

            val manager = PositionFlowManager(BrokerRegistryImpl(setOf(mockBroker)), logger = TestLogger, managerDispatcher = Dispatchers.Default)
            manager.start(setOf(acc1), sandboxMode = false)
            delay(300)

            val account = manager.sheet.value.accounts[acc1]
            assertNotNull(account)
            assertEquals(1, account!!.positions.size)
            assertTrue(account.positions[0] is SharePosition)
            assertEquals(2.0, account.positions[0].quantity, 0.0)
            assertEquals(292.6, account.positions[0].currentPrice, 0.0001)

            manager.stop()
        }

    @Test
    fun `sheet merges partial stream item into existing polling position`() =
        runBlocking {
            val wsItem =
                PositionStreamItem(
                    tscalpInstrumentId = "uid-sber",
                    brokerName = BrokerName.TINVEST,
                    ticker = "T-uid-sber",
                    quantity = 3L,
                    instrumentType = InstrumentType.SHARE,
                    accountId = "acc-1",
                )
            val mockBroker =
                stubBroker(
                    flow {
                        delay(100)
                        emit(wsItem)
                        awaitCancellation()
                    },
                )
            coEvery { mockBroker.fetchPortfolioPositions("acc-1", false) } returns
                accountPortfolio(positions = listOf(sharePosition("uid-sber", qty = 2.0, price = 292.6)))

            val manager = PositionFlowManager(BrokerRegistryImpl(setOf(mockBroker)), logger = TestLogger, managerDispatcher = Dispatchers.Default)
            manager.start(setOf(acc1), sandboxMode = false)
            delay(500)

            val account = manager.sheet.value.accounts[acc1]
            assertNotNull(account)
            assertEquals(1, account!!.positions.size)
            assertEquals(3.0, account.positions[0].quantity, 0.0)
            assertEquals(292.6, account.positions[0].currentPrice, 0.0001)

            manager.stop()
        }

    @Test
    fun `sheet merges domestic currency WS into balances`() =
        runBlocking {
            val wsItem =
                PositionStreamItem(
                    tscalpInstrumentId = "",
                    brokerName = BrokerName.TINVEST,
                    ticker = "",
                    quantity = 0L,
                    instrumentType = InstrumentType.CURRENCY,
                    currency = "rub",
                    money = 2415.31,
                    blocked = 100.0,
                    isDomesticCurrency = true,
                    accountId = "acc-1",
                )
            val mockBroker =
                stubBroker(
                    flow {
                        delay(100)
                        emit(wsItem)
                        awaitCancellation()
                    },
                )
            coEvery { mockBroker.fetchPortfolioPositions("acc-1", false) } returns
                accountPortfolio(positions = listOf(currencyPosition("uid-usd", "usd", qty = 100.0, price = 90.0)))

            val manager = PositionFlowManager(BrokerRegistryImpl(setOf(mockBroker)), logger = TestLogger, managerDispatcher = Dispatchers.Default)
            manager.start(setOf(acc1), sandboxMode = false)
            delay(500)

            val account = manager.sheet.value.accounts[acc1]
            assertNotNull(account)
            val rubBalance = account!!.balances.firstOrNull { it.currency == "rub" }
            assertNotNull(rubBalance)
            assertEquals(2415.31, rubBalance!!.money, 0.0001)
            assertEquals(100.0, rubBalance.blocked, 0.0001)

            manager.stop()
        }

    @Test
    fun `flow emits both polling and stream items`() =
        runBlocking {
            val wsItem =
                PositionStreamItem(
                    tscalpInstrumentId = "uid-sber",
                    brokerName = BrokerName.TINVEST,
                    ticker = "T-uid-sber",
                    quantity = 3L,
                    instrumentType = InstrumentType.SHARE,
                    accountId = "acc-1",
                )
            val mockBroker =
                stubBroker(
                    flow {
                        delay(100)
                        emit(wsItem)
                        awaitCancellation()
                    },
                )
            coEvery { mockBroker.fetchPortfolioPositions("acc-1", false) } returns
                accountPortfolio(positions = listOf(sharePosition("uid-sber", qty = 2.0, price = 292.6)))

            val manager = PositionFlowManager(BrokerRegistryImpl(setOf(mockBroker)), logger = TestLogger, managerDispatcher = Dispatchers.Default)
            val collected = mutableListOf<PositionStreamItem>()
            val collectorJob = launch { manager.flow.collect { collected.add(it) } }

            manager.start(setOf(acc1), sandboxMode = false)
            delay(500)

            assertTrue(collected.isNotEmpty())
            assertTrue(collected.any { it.quantity == 2L })
            assertTrue(collected.any { it.quantity == 3L })

            collectorJob.cancel()
            manager.stop()
        }

    @Test
    fun `concurrent start creates single job`() =
        runBlocking {
            val mockBroker = stubBroker(flow { awaitCancellation() })
            coEvery { mockBroker.fetchPortfolioPositions(any(), any()) } returns accountPortfolio()

            val manager = PositionFlowManager(BrokerRegistryImpl(setOf(mockBroker)), logger = TestLogger, managerDispatcher = Dispatchers.Default)
            coroutineScope {
                (1..50)
                    .map { async(Dispatchers.Default) { manager.start(setOf(acc1), sandboxMode = false) } }
                    .awaitAll()
            }
            delay(200)

            verify(exactly = 1) { mockBroker.subscribePositions("acc-1") }

            manager.stop()
        }

    @Test
    fun `concurrent reapplyDebugFlags creates single active job`() =
        runBlocking {
            val activeCount = AtomicInteger(0)
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.name } returns BrokerName.TINVEST
            every { mockBroker.isInitialized } returns true
            every { mockBroker.portfolio } returns mockBroker
            every { mockBroker.streams } returns mockBroker
            every { mockBroker.subscribePositions(any()) } returns
                flow {
                    activeCount.incrementAndGet()
                    try {
                        awaitCancellation()
                    } finally {
                        activeCount.decrementAndGet()
                    }
                }
            coEvery { mockBroker.fetchPortfolioPositions(any(), any()) } returns accountPortfolio()

            val manager = PositionFlowManager(BrokerRegistryImpl(setOf(mockBroker)), logger = TestLogger, managerDispatcher = Dispatchers.Default)
            manager.start(setOf(acc1), sandboxMode = false)
            delay(100)

            coroutineScope {
                (1..50)
                    .map { async(Dispatchers.Default) { manager.reapplyDebugFlags() } }
                    .awaitAll()
            }
            delay(500)

            assertEquals(1, activeCount.get())

            manager.stop()
        }

    @Test
    fun `connectionState transitions to LIVE on stream emit and STOPPED on stop`() =
        runBlocking {
            val wsItem =
                PositionStreamItem(
                    tscalpInstrumentId = "uid-sber",
                    brokerName = BrokerName.TINVEST,
                    ticker = "T-uid-sber",
                    quantity = 1L,
                    instrumentType = InstrumentType.SHARE,
                    accountId = "acc-1",
                )
            val mockBroker =
                stubBroker(
                    flow {
                        emit(wsItem)
                        awaitCancellation()
                    },
                )
            coEvery { mockBroker.fetchPortfolioPositions(any(), any()) } returns accountPortfolio()

            val manager = PositionFlowManager(BrokerRegistryImpl(setOf(mockBroker)), logger = TestLogger, managerDispatcher = Dispatchers.Default)
            manager.start(setOf(acc1), sandboxMode = false)
            delay(200)
            assertEquals(StreamState.LIVE, manager.connectionState.value)

            manager.stop()
            assertEquals(StreamState.STOPPED, manager.connectionState.value)
        }

    @Test
    fun `connectionState transitions to POLLING on polling-only`() =
        runBlocking {
            val mockBroker = stubBroker(flow { awaitCancellation() })
            coEvery { mockBroker.fetchPortfolioPositions(any(), any()) } returns
                accountPortfolio(positions = listOf(sharePosition("uid-sber", qty = 1.0, price = 292.6)))

            val manager = PositionFlowManager(BrokerRegistryImpl(setOf(mockBroker)), logger = TestLogger, managerDispatcher = Dispatchers.Default)
            manager.start(setOf(acc1), sandboxMode = false)
            delay(300)

            assertEquals(StreamState.POLLING, manager.connectionState.value)

            manager.stop()
        }
}
