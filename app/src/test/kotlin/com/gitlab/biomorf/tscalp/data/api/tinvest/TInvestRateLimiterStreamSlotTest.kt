package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.data.api.common.TransportProtocol
import com.gitlab.biomorf.tscalp.domain.api.managers.FakeClock
import com.gitlab.biomorf.tscalp.domain.log.TestLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/**
 * Stream-slot tests for T-5 (`withStreamSlot`).
 *
 * `operations` budget = 11 (TInvestRateLimits.STREAM_GRPC). Cases:
 *  - under limit: 5 occupied, 6th passes immediately.
 *  - at limit: 11 occupied, 12th blocks; release one → 12th passes.
 *  - shared (WS_SHARES_STREAM_BUDGET = true): WS goes into the same
 *    bucket as gRPC — 11 gRPC + 1 WS → WS blocks; release → WS passes.
 *  - split (WS_SHARES_STREAM_BUDGET = false): WS has its own bucket —
 *    11 gRPC + 1 WS → WS passes immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TInvestRateLimiterStreamSlotTest {
    private fun newClock(): FakeClock = FakeClock(Instant.fromEpochMilliseconds(1_000L))

    @Test
    fun `under limit — new slot passes immediately`() =
        runTest {
            val limiter = TInvestRateLimiter(newClock(), TestLogger)
            val latches = List(5) { CompletableDeferred<Unit>() }
            latches.forEach { l ->
                launch { limiter.withStreamSlot(TransportProtocol.GRPC, "operations") { l.await() } }
            }
            testScheduler.runCurrent()

            val extra =
                launch {
                    limiter.withStreamSlot(TransportProtocol.GRPC, "operations") { }
                }
            testScheduler.runCurrent()
            assertTrue("6th under limit (5/11) must pass immediately", extra.isCompleted)

            latches.forEach { it.complete(Unit) }
            testScheduler.advanceUntilIdle()
        }

    @Test
    fun `at limit — next slot blocks until release`() =
        runTest {
            val limiter = TInvestRateLimiter(newClock(), TestLogger)
            val latches = List(11) { CompletableDeferred<Unit>() }
            latches.forEach { l ->
                launch { limiter.withStreamSlot(TransportProtocol.GRPC, "operations") { l.await() } }
            }
            testScheduler.runCurrent()

            val extra =
                launch {
                    limiter.withStreamSlot(TransportProtocol.GRPC, "operations") { }
                }
            testScheduler.runCurrent()
            assertFalse("12th at limit (11/11) must wait", extra.isCompleted)

            latches.first().complete(Unit)
            testScheduler.advanceUntilIdle()
            assertTrue("12th passes after one slot released", extra.isCompleted)

            latches.drop(1).forEach { it.complete(Unit) }
            testScheduler.advanceUntilIdle()
        }

    @Test
    fun `shared — WS shares gRPC bucket (WS_SHARES_STREAM_BUDGET = true)`() =
        runTest {
            val limiter = TInvestRateLimiter(newClock(), TestLogger, wsSharesStreamBudget = true)
            val latches = List(11) { CompletableDeferred<Unit>() }
            latches.forEach { l ->
                launch { limiter.withStreamSlot(TransportProtocol.GRPC, "operations") { l.await() } }
            }
            testScheduler.runCurrent()

            val ws =
                launch {
                    limiter.withStreamSlot(TransportProtocol.WS, "operations") { }
                }
            testScheduler.runCurrent()
            assertFalse("WS must wait: gRPC bucket exhausted (shared)", ws.isCompleted)

            latches.first().complete(Unit)
            testScheduler.advanceUntilIdle()
            assertTrue("WS passes after one shared slot released", ws.isCompleted)

            latches.drop(1).forEach { it.complete(Unit) }
            testScheduler.advanceUntilIdle()
        }

    @Test
    fun `split — WS has own bucket (WS_SHARES_STREAM_BUDGET = false)`() =
        runTest {
            val limiter = TInvestRateLimiter(newClock(), TestLogger, wsSharesStreamBudget = false)
            val latches = List(11) { CompletableDeferred<Unit>() }
            latches.forEach { l ->
                launch { limiter.withStreamSlot(TransportProtocol.GRPC, "operations") { l.await() } }
            }
            testScheduler.runCurrent()

            val ws =
                launch {
                    limiter.withStreamSlot(TransportProtocol.WS, "operations") { }
                }
            testScheduler.runCurrent()
            assertTrue("WS must pass: own bucket, gRPC exhaustion irrelevant (split)", ws.isCompleted)

            latches.forEach { it.complete(Unit) }
            testScheduler.advanceUntilIdle()
        }
}
