package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.data.api.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
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
    @Test
    fun `ws currency without polling cache is not emitted`() =
        runBlocking {
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            val wsItem =
                PositionStreamItem(
                    tscalpInstrumentId = "",
                    brokerName = BrokerName.TINVEST,
                    ticker = "",
                    quantity = 0L,
                    instrumentType = "currency",
                    currency = "rub",
                    money = 2415.31,
                    blocked = null,
                )
            every { mockBroker.subscribePositions(any()) } returns
                flow {
                    emit(wsItem)
                    awaitCancellation()
                }
            coEvery { mockBroker.positionsSnapshot(any()) } returns emptyList()

            val manager = PositionFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))
            val collected = mutableListOf<PositionStreamItem>()
            val collectorJob = launch { manager.flow.collect { collected.add(it) } }

            manager.start("acc-1")
            delay(300)

            // ws currency без кэша → enrichAndCache вернул null → ничего не эмитится
            assertTrue(collected.isEmpty())

            collectorJob.cancel()
            manager.stop()
        }

    @Test
    fun `ws currency merges into cached polling currency item`() =
        runBlocking {
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            val pollingItem =
                PositionStreamItem(
                    tscalpInstrumentId = "uid-rub",
                    brokerName = BrokerName.TINVEST,
                    ticker = "RUB000UTSTOM",
                    quantity = 2415L,
                    currentPrice = 1.0,
                    instrumentType = "currency",
                    currency = "rub",
                )
            val wsItem =
                PositionStreamItem(
                    tscalpInstrumentId = "",
                    brokerName = BrokerName.TINVEST,
                    ticker = "",
                    quantity = 0L,
                    instrumentType = "currency",
                    currency = "rub",
                    money = 2415.31,
                    blocked = 100.0,
                )
            coEvery { mockBroker.positionsSnapshot(any()) } returns listOf(pollingItem)
            every { mockBroker.subscribePositions(any()) } returns
                flow {
                    delay(100) // дать polling успеть в первый раунд
                    emit(wsItem)
                    awaitCancellation()
                }

            val manager = PositionFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))
            val collected = mutableListOf<PositionStreamItem>()
            val collectorJob = launch { manager.flow.collect { collected.add(it) } }

            manager.start("acc-1")
            delay(500)

            val merged = collected.firstOrNull { it.tscalpInstrumentId == "uid-rub" && it.money == 2415.31 }
            assertNotNull(merged)
            assertEquals("RUB000UTSTOM", merged!!.ticker)
            assertEquals(100.0, merged.blocked!!, 0.0001)

            collectorJob.cancel()
            manager.stop()
        }

    @Test
    fun `ws currency with no matching currency in cache is not emitted`() =
        runBlocking {
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            val usdItem =
                PositionStreamItem(
                    tscalpInstrumentId = "uid-usd",
                    brokerName = BrokerName.TINVEST,
                    ticker = "USD000UTSTOM",
                    quantity = 100L,
                    currentPrice = 1.0,
                    instrumentType = "currency",
                    currency = "usd",
                )
            val wsRub =
                PositionStreamItem(
                    tscalpInstrumentId = "",
                    brokerName = BrokerName.TINVEST,
                    ticker = "",
                    quantity = 0L,
                    instrumentType = "currency",
                    currency = "rub",
                    money = 100.0,
                )
            coEvery { mockBroker.positionsSnapshot(any()) } returns listOf(usdItem)
            every { mockBroker.subscribePositions(any()) } returns
                flow {
                    delay(100)
                    emit(wsRub)
                    awaitCancellation()
                }

            val manager = PositionFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))
            val collected = mutableListOf<PositionStreamItem>()
            val collectorJob = launch { manager.flow.collect { collected.add(it) } }

            manager.start("acc-1")
            delay(500)

            // Только USD из polling, никакого rub (ws без кэша — отброшен)
            assertTrue(collected.none { it.money != null })
            assertTrue(collected.any { it.tscalpInstrumentId == "uid-usd" })

            collectorJob.cancel()
            manager.stop()
        }

    @Test
    fun `full share item is emitted unchanged`() =
        runBlocking {
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            val share =
                PositionStreamItem(
                    tscalpInstrumentId = "uid-sber",
                    brokerName = BrokerName.TINVEST,
                    ticker = "SBER",
                    quantity = 2L,
                    currentPrice = 292.6,
                    instrumentType = "share",
                    expectedYield = 14.73,
                )
            every { mockBroker.subscribePositions(any()) } returns
                flow {
                    emit(share)
                    awaitCancellation()
                }
            coEvery { mockBroker.positionsSnapshot(any()) } returns emptyList()

            val manager = PositionFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))
            val collected = mutableListOf<PositionStreamItem>()
            val collectorJob = launch { manager.flow.collect { collected.add(it) } }

            manager.start("acc-1")
            delay(300)

            val emitted = collected.firstOrNull { it.tscalpInstrumentId == "uid-sber" }
            assertNotNull(emitted)
            assertEquals(292.6, emitted!!.currentPrice!!, 0.0001)
            assertEquals(14.73, emitted.expectedYield!!, 0.0001)

            collectorJob.cancel()
            manager.stop()
        }

    @Test
    fun `concurrent start creates single job`() =
        runBlocking {
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            every { mockBroker.subscribePositions(any()) } returns
                flow { awaitCancellation() }
            coEvery { mockBroker.positionsSnapshot(any()) } returns emptyList()

            val manager = PositionFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))

            coroutineScope {
                (1..50)
                    .map { async(Dispatchers.Default) { manager.start("acc-1") } }
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
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            every { mockBroker.subscribePositions(any()) } returns
                flow {
                    activeCount.incrementAndGet()
                    try {
                        awaitCancellation()
                    } finally {
                        activeCount.decrementAndGet()
                    }
                }
            coEvery { mockBroker.positionsSnapshot(any()) } returns emptyList()

            val manager = PositionFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))

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
        }

    @Test
    fun `connectionState transitions to LIVE on stream emit and STOPPED on stop`() =
        runBlocking {
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            val share =
                PositionStreamItem(
                    tscalpInstrumentId = "uid-sber",
                    brokerName = BrokerName.TINVEST,
                    ticker = "SBER",
                    quantity = 1L,
                    currentPrice = 292.6,
                    instrumentType = "share",
                )
            every { mockBroker.subscribePositions(any()) } returns
                flow {
                    emit(share)
                    awaitCancellation()
                }
            coEvery { mockBroker.positionsSnapshot(any()) } returns emptyList()

            val manager = PositionFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))
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
            every { mockBroker.snapshot } returns mockBroker
            val share =
                PositionStreamItem(
                    tscalpInstrumentId = "uid-sber",
                    brokerName = BrokerName.TINVEST,
                    ticker = "SBER",
                    quantity = 1L,
                    currentPrice = 292.6,
                    instrumentType = "share",
                )
            every { mockBroker.subscribePositions(any()) } returns
                flow { awaitCancellation() }
            coEvery { mockBroker.positionsSnapshot(any()) } returns listOf(share)

            val manager = PositionFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))
            manager.start("acc-1")
            delay(300)

            assertEquals(StreamState.POLLING, manager.connectionState.value)

            manager.stop()
        }
}
