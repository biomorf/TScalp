package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.data.api.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.OrderState
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
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class OrderFlowManagerTest {
    @Test
    fun `concurrent start creates single job`() {
        runBlocking {
            // Мок брокера: subscribeOrderState возвращает бесконечный flow,
            // чтобы job внутри start() не завершился сам и race не маскировался.
            // ordersSnapshot → emptyList(), polling-цикл после первого снимка
            // уходит в delay(POLLING_INTERVAL_MS).
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.isInitialized } returns true
            coEvery { mockBroker.ordersSnapshot(any()) } returns emptyList()
            coEvery { mockBroker.subscribeOrderState(any()) } returns
                flow { awaitCancellation() }

            val manager = OrderFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))

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
            every { mockBroker.isInitialized } returns true
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

            val manager = OrderFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))
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
            every { mockBroker.isInitialized } returns true
            coEvery { mockBroker.ordersSnapshot(any()) } returns emptyList()
            coEvery { mockBroker.subscribeOrderState(any()) } returns
                flow { awaitCancellation() }

            val manager = OrderFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))
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
            every { mockBroker.isInitialized } returns true
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

            val manager = OrderFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))

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
            every { mockBroker.isInitialized } returns true
            coEvery { mockBroker.ordersSnapshot(any()) } returns emptyList()
            coEvery { mockBroker.subscribeOrderState(any()) } returns
                flow { awaitCancellation() }

            val manager = OrderFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))

            manager.reapplyDebugFlags()
            delay(200)

            coVerify(exactly = 0) { mockBroker.subscribeOrderState(any()) }
            coVerify(exactly = 0) { mockBroker.ordersSnapshot(any()) }
        }
}
