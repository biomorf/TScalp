package com.gitlab.biomorf.tscalp.domain.api.managers

import com.gitlab.biomorf.tscalp.data.api.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.data.registry.BrokerRegistryImpl
import com.gitlab.biomorf.tscalp.domain.log.TestLogger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Order
import com.gitlab.biomorf.tscalp.domain.models.OrderState
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.StreamState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class OrderFlowManagerTest {
    @Test
    fun `concurrent start creates single job`() {
        runBlocking {
            // Мок брокера: subscribeOrderState возвращает бесконечный flow,
            // чтобы job внутри start() не завершился сам и race не маскировался.
            // ordersSnapshot → emptyList(), polling-цикл после первого снимка
            // уходит в delay(POLLING_INTERVAL_MS).
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.name } returns BrokerName.TINVEST
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            every { mockBroker.streams } returns mockBroker
            coEvery { mockBroker.ordersSnapshot(any()) } returns emptyList()
            coEvery { mockBroker.subscribeOrderState(any()) } returns
                flow { awaitCancellation() }

            val manager = OrderFlowManager(BrokerRegistryImpl(setOf(mockBroker)), logger = TestLogger, clock = Clock.System, managerDispatcher = Dispatchers.Default)

            // 50 параллельных вызовов start() — race между check-then-set
            // в synchronized(startLock). Без synchronized создавались бы
            // несколько job'ов, и subscribeOrderState / ordersSnapshot
            // вызывались бы >1 раза.
            coroutineScope {
                (1..50)
                    .map { async(Dispatchers.Default) { manager.start("acc-1") } }
                    .awaitAll()
            }
            // Дать scope.launch внутри start() успеть выполниться
            // (вложенный coroutineScope { launch { polling } launch { stream } }).
            delay(300)

            coVerify(exactly = 1) { mockBroker.subscribeOrderState("acc-1") }
            coVerify(exactly = 1) { mockBroker.ordersSnapshot("acc-1") }

            manager.stop()
        }
    }

    @Test
    fun `connectionState transitions to LIVE on stream emit and STOPPED on stop`() =
        runBlocking {
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.name } returns BrokerName.TINVEST
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            every { mockBroker.streams } returns mockBroker
            coEvery { mockBroker.ordersSnapshot(any()) } returns emptyList()
            val orderState =
                OrderState(
                    orderId = "order-1",
                    orderRequestId = null,
                    ticker = "SBER",
                    direction = "BUY",
                    limitPrice = 100.0,
                    executedPrice = null,
                    quantity = 1L,
                    executedQuantity = 0L,
                    status = "NEW",
                    updateTime = null,
                    orderType = "LIMIT",
                    tscalpInstrumentId = "uid-sber",
                )
            coEvery { mockBroker.subscribeOrderState(any()) } returns
                flow {
                    emit(orderState)
                    awaitCancellation()
                }

            val manager = OrderFlowManager(BrokerRegistryImpl(setOf(mockBroker)), logger = TestLogger, clock = Clock.System, managerDispatcher = Dispatchers.Default)
            manager.start("acc-1")
            delay(200)
            assertEquals(StreamState.LIVE, manager.connectionState.value)

            manager.stop()
            assertEquals(StreamState.STOPPED, manager.connectionState.value)
        }

    @Test
    fun `connectionState transitions to POLLING on polling-only`() =
        runBlocking {
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.name } returns BrokerName.TINVEST
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            every { mockBroker.streams } returns mockBroker
            coEvery { mockBroker.ordersSnapshot(any()) } returns emptyList()
            coEvery { mockBroker.subscribeOrderState(any()) } returns
                flow { awaitCancellation() }

            val manager = OrderFlowManager(BrokerRegistryImpl(setOf(mockBroker)), logger = TestLogger, clock = Clock.System, managerDispatcher = Dispatchers.Default)
            manager.start("acc-1")
            delay(300)

            assertEquals(StreamState.POLLING, manager.connectionState.value)

            manager.stop()
            assertEquals(StreamState.STOPPED, manager.connectionState.value)
        }

    @Test
    fun `concurrent reapplyDebugFlags creates single active stream`() =
        runBlocking {
            val activeCount = AtomicInteger(0)
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.name } returns BrokerName.TINVEST
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            every { mockBroker.streams } returns mockBroker
            coEvery { mockBroker.ordersSnapshot(any()) } returns emptyList()
            coEvery { mockBroker.subscribeOrderState(any()) } returns
                flow {
                    activeCount.incrementAndGet()
                    try {
                        awaitCancellation()
                    } finally {
                        activeCount.decrementAndGet()
                    }
                }

            val manager = OrderFlowManager(BrokerRegistryImpl(setOf(mockBroker)), logger = TestLogger, clock = Clock.System, managerDispatcher = Dispatchers.Default)

            manager.start("acc-1")
            delay(100)

            coroutineScope {
                (1..50)
                    .map { async(Dispatchers.Default) { manager.reapplyDebugFlags() } }
                    .awaitAll()
            }
            delay(500)

            assertEquals(1, activeCount.get())

            manager.stop()
            assertEquals(StreamState.STOPPED, manager.connectionState.value)
        }

    @Test
    fun `reapplyDebugFlags before start is no-op`() =
        runBlocking {
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.name } returns BrokerName.TINVEST
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            every { mockBroker.streams } returns mockBroker
            coEvery { mockBroker.ordersSnapshot(any()) } returns emptyList()
            coEvery { mockBroker.subscribeOrderState(any()) } returns
                flow { awaitCancellation() }

            val manager = OrderFlowManager(BrokerRegistryImpl(setOf(mockBroker)), logger = TestLogger, clock = Clock.System, managerDispatcher = Dispatchers.Default)

            manager.reapplyDebugFlags()
            delay(200)

            coVerify(exactly = 0) { mockBroker.subscribeOrderState(any()) }
            coVerify(exactly = 0) { mockBroker.ordersSnapshot(any()) }
        }

    @Test
    fun `evictStale uses injected clock, not system time (fake-time regression)`() {
        runBlocking {
            // t0 — далёкое будущее (2070). Если код использует
            // System.currentTimeMillis() вместо clock.now():
            //   putFromPolling → lastSeenAt = 2026 (System)
            //   evictStale(2026) → cutoff = 2025 → lastSeenAt > cutoff
            //   → запись НЕ эвиктится, тест падает.
            // С clock.now():
            //   putFromPolling → lastSeenAt = 2070
            //   advance(31s) → evictStale(2070+31s) → cutoff = 2070+1s
            //   → lastSeenAt < cutoff → запись эвиктится.
            val t0 = Instant.parse("2070-01-01T00:00:00Z")
            val fakeClock = FakeClock(t0)
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.name } returns BrokerName.TINVEST
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            every { mockBroker.streams } returns mockBroker
            coEvery { mockBroker.ordersSnapshot(any()) } returns listOf(sampleOrder("o1"))
            coEvery { mockBroker.subscribeOrderState(any()) } returns
                flow { awaitCancellation() }

            val manager =
                OrderFlowManager(
                    BrokerRegistryImpl(setOf(mockBroker)),
                    logger = TestLogger,
                    clock = fakeClock,
                    managerDispatcher = Dispatchers.Default,
                )
            manager.start("acc-1")
            delay(300)
            assertEquals(listOf("o1"), manager.orders.value.map { it.orderId })

            // Следующий polling: пустой snapshot, fakeClock за ttl (30s).
            coEvery { mockBroker.ordersSnapshot(any()) } returns emptyList()
            fakeClock.advance(31.seconds)
            manager.reapplyDebugFlags()
            delay(300)

            assertTrue(manager.orders.value.isEmpty())
            manager.stop()
        }
    }

    private fun sampleOrder(id: String): Order =
        Order.Regular(
            orderId = id,
            ticker = "SBER",
            tscalpInstrumentId = "uid-sber",
            instrumentType = "share",
            direction = "BUY",
            quantity = 1L,
            orderType = OrderTypeSelection.Limit,
            status = "NEW",
            orderDate = 0L,
            price = 100.0,
        )
}
