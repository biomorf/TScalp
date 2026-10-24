package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.data.api.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PriceFlowManagerTest {
    @Test
    fun `concurrent setInterest creates single active stream`() {
        runBlocking {
            val activeCount = AtomicInteger(0)
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            coEvery { mockBroker.pricesSnapshot(any()) } returns emptyMap()
            val mockStreams = mockk<StreamsApi>(relaxed = true)
            every { mockBroker.streams } returns mockStreams
            every { mockStreams.subscribeLastPrices(any()) } returns
                flow {
                    activeCount.incrementAndGet()
                    try {
                        awaitCancellation()
                    } finally {
                        activeCount.decrementAndGet()
                    }
                }

            val manager =
                PriceFlowManager(
                    BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)),
                )

            // 50 параллельных setInterest с разными union.
            // Каждый вызов даёт новый union → restart.
            // С synchronized(startLock): restart'ы последовательны,
            // каждый cancel'ит предыдущий, активен 1.
            // Без synchronized: cancel'ы теряются из-за перезаписи ссылки
            // streamJob, активны несколько (утечка job'ов).
            coroutineScope {
                (1..50)
                    .map { i ->
                        async(Dispatchers.Default) {
                            manager.setInterest(PriceConsumer.ORDERS, setOf("uid-$i"))
                        }
                    }.awaitAll()
            }
            delay(500)

            assertEquals(1, activeCount.get())

            manager.clearInterest(PriceConsumer.ORDERS)
        }
    }

    @Test
    fun `sequential setInterest with same union creates single stream`() {
        runBlocking {
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            coEvery { mockBroker.pricesSnapshot(any()) } returns emptyMap()
            val mockStreams = mockk<StreamsApi>(relaxed = true)
            every { mockBroker.streams } returns mockStreams
            every { mockStreams.subscribeLastPrices(any()) } returns
                flow { awaitCancellation() }

            val manager =
                PriceFlowManager(
                    BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)),
                )

            manager.setInterest(PriceConsumer.ORDERS, setOf("uid-1"))
            delay(100)
            // Тот же union → restart не должен вызываться.
            manager.setInterest(PriceConsumer.ORDERS, setOf("uid-1"))
            delay(100)

            verify(exactly = 1) { mockStreams.subscribeLastPrices(any()) }

            manager.clearInterest(PriceConsumer.ORDERS)
        }
    }

    @Test
    fun `concurrent reapplyDebugFlags creates single active stream`() =
        runBlocking {
            val activeCount = AtomicInteger(0)
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            coEvery { mockBroker.pricesSnapshot(any()) } returns emptyMap()
            val mockStreams = mockk<StreamsApi>(relaxed = true)
            every { mockBroker.streams } returns mockStreams
            every { mockStreams.subscribeLastPrices(any()) } returns
                flow {
                    activeCount.incrementAndGet()
                    try {
                        awaitCancellation()
                    } finally {
                        activeCount.decrementAndGet()
                    }
                }

            val manager =
                PriceFlowManager(
                    BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)),
                )

            // Дать первому стриму стартовать — иначе union пуст
            // и reapplyDebugFlags увидит restart(emptySet()) = no-op.
            manager.setInterest(PriceConsumer.ORDERS, setOf("uid-1"))
            delay(100)

            // 50 параллельных reapplyDebugFlags — каждый restart'ит
            // текущий union. С synchronized(startLock) cancel'ы
            // последовательны, к концу активен 1. Без synchronized —
            // несколько streamJob выживают, activeCount > 1.
            coroutineScope {
                (1..50)
                    .map { async(Dispatchers.Default) { manager.reapplyDebugFlags() } }
                    .awaitAll()
            }
            delay(500)

            assertEquals(1, activeCount.get())

            manager.clearInterest(PriceConsumer.ORDERS)
        }

    @Test
    fun `connectionState transitions to LIVE on stream emit and STOPPED on clearInterest`() =
        runBlocking {
            val mockBroker = mockk<TInvestBrokerAPI>(relaxed = true)
            every { mockBroker.isInitialized } returns true
            every { mockBroker.snapshot } returns mockBroker
            coEvery { mockBroker.pricesSnapshot(any()) } returns emptyMap()
            val mockStreams = mockk<StreamsApi>(relaxed = true)
            every { mockBroker.streams } returns mockStreams
            every { mockStreams.subscribeLastPrices(any()) } returns
                flow {
                    emit("uid-1" to 100.0)
                    awaitCancellation()
                }

            val manager =
                PriceFlowManager(
                    BrokerManager(mapOf(BrokerName.TINVEST to mockBroker)),
                )
            manager.setInterest(PriceConsumer.ORDERS, setOf("uid-1"))
            delay(200)
            assertEquals(StreamState.LIVE, manager.connectionState.value)

            manager.clearInterest(PriceConsumer.ORDERS)
            assertEquals(StreamState.STOPPED, manager.connectionState.value)
        }
}
