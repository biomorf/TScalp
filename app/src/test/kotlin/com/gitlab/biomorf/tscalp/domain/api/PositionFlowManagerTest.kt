package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.data.api.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PositionFlowManagerTest {
    private fun manager(): PositionFlowManager = PositionFlowManager(BrokerManager(emptyMap()))

    @Test
    fun `ws currency without polling cache returns null`() {
        val m = manager()
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

        assertNull(m.enrichAndCache(wsItem))
    }

    @Test
    fun `ws currency merges into cached polling currency item`() {
        val m = manager()
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
        m.enrichAndCache(pollingItem)

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
        val result = m.enrichAndCache(wsItem)

        assertNotNull(result)
        assertEquals("uid-rub", result!!.tscalpInstrumentId)
        assertEquals("RUB000UTSTOM", result.ticker)
        assertEquals(2415.31, result.money!!, 0.0001)
        assertEquals(100.0, result.blocked!!, 0.0001)
    }

    @Test
    fun `ws currency with no matching currency in cache returns null`() {
        val m = manager()
        m.enrichAndCache(
            PositionStreamItem(
                tscalpInstrumentId = "uid-usd",
                brokerName = BrokerName.TINVEST,
                ticker = "USD000UTSTOM",
                quantity = 100L,
                currentPrice = 1.0,
                instrumentType = "currency",
                currency = "usd",
            ),
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

        assertNull(m.enrichAndCache(wsRub))
    }

    @Test
    fun `full share item is returned unchanged`() {
        val m = manager()
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

        val result = m.enrichAndCache(share)

        assertSame(share, result)
    }

    @Test
    fun `concurrent start creates single job`() =
        runBlocking {
            // Мок брокера: subscribePositions возвращает бесконечный flow,
            // чтобы job внутри start() не завершился сам и race не маскировался.
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.isInitialized } returns true
            coEvery { mockBroker.subscribePositions(any()) } returns
                flow { awaitCancellation() }

            val manager = PositionFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))

            // 50 параллельных вызовов start() — race между check-then-set
            // в synchronized(startLock). Без synchronized создавались бы
            // несколько job'ов, и subscribePositions вызывался бы >1 раза.
            coroutineScope {
                (1..50)
                    .map { async(Dispatchers.Default) { manager.start("acc-1") } }
                    .awaitAll()
            }
            // Дать scope.launch внутри start() успеть выполниться.
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
            coEvery { mockBroker.subscribePositions(any()) } returns
                flow {
                    activeCount.incrementAndGet()
                    try {
                        awaitCancellation()
                    } finally {
                        activeCount.decrementAndGet()
                    }
                }

            val manager = PositionFlowManager(BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)))

            // Дать первому стриму (из start) стартовать — иначе
            // reapplyDebugFlags увидит currentAccountId=null и no-op.
            manager.start("acc-1")
            delay(100)

            // 50 параллельных reapplyDebugFlags — каждый cancel'ит
            // предыдущий job и запускает новый. С synchronized(startLock)
            // cancel'ы последовательны, к концу активен 1 job.
            // Без synchronized — несколько job'ов выживают, activeCount > 1.
            coroutineScope {
                (1..50)
                    .map { async(Dispatchers.Default) { manager.reapplyDebugFlags() } }
                    .awaitAll()
            }
            delay(500)

            assertEquals(1, activeCount.get())

            manager.stop()
        }
}
