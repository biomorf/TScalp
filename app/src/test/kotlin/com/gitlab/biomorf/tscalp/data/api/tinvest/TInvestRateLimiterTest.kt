package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.data.api.common.TransportProtocol
import com.gitlab.biomorf.tscalp.domain.api.managers.FakeClock
import com.gitlab.biomorf.tscalp.domain.log.TestLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Rate limiter test (1a-4 B-2).
 *
 * Virtual-time + FakeClock: both must be advanced together, otherwise
 * [TokenBucket.refill] (using [FakeClock]) does not see the time shift.
 * Order: `fakeClock.advance(...)` first, then `testScheduler.advanceTimeBy(...)`.
 *
 * Start instant is 1_000ms (not 0): [TokenBucket.refill] uses
 * `lastRefillNanos == 0L` as a sentinel, and `nowNanos == 0` would collide.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TInvestRateLimiterTest {
    private fun newClock(): FakeClock = FakeClock(Instant.fromEpochMilliseconds(1_000L))

    @Test
    fun `postOrder limited to 15 per second`() =
        runTest {
            val fakeClock = newClock()
            val limiter = TInvestRateLimiter(fakeClock, TestLogger)

            repeat(15) {
                limiter.withUnaryPermission(TransportProtocol.GRPC, "orders", "postOrder") { }
            }

            val job =
                launch {
                    limiter.withUnaryPermission(TransportProtocol.GRPC, "orders", "postOrder") { }
                }
            testScheduler.runCurrent()
            assertFalse("16th must wait for the next second", job.isCompleted)

            fakeClock.advance(67.milliseconds)
            testScheduler.advanceTimeBy(67L)
            testScheduler.runCurrent()

            assertTrue("16th must pass after 67ms", job.isCompleted)
        }

    @Test
    fun `global limited to 50 per second across services`() =
        runTest {
            val fakeClock = newClock()
            val limiter = TInvestRateLimiter(fakeClock, TestLogger)

            repeat(50) {
                limiter.withUnaryPermission(TransportProtocol.GRPC, "users", "getAccounts") { }
            }

            val job =
                launch {
                    limiter.withUnaryPermission(TransportProtocol.GRPC, "operations", "getOperations") { }
                }
            testScheduler.runCurrent()
            assertFalse("51st must wait for global budget", job.isCompleted)

            fakeClock.advance(20.milliseconds)
            testScheduler.advanceTimeBy(20L)
            testScheduler.runCurrent()

            assertTrue("51st must pass after 20ms", job.isCompleted)
        }

    @Test
    fun `timeout throws RateLimitTimeoutException`() =
        runTest {
            val fakeClock = newClock()
            val limiter =
                TInvestRateLimiter(
                    clock = fakeClock,
                    logger = TestLogger,
                    timeoutMs = 50L,
                )

            repeat(15) {
                limiter.withUnaryPermission(TransportProtocol.GRPC, "orders", "postOrder") { }
            }

            var caught: RateLimitTimeoutException? = null
            try {
                limiter.withUnaryPermission(TransportProtocol.GRPC, "orders", "postOrder") { }
            } catch (e: RateLimitTimeoutException) {
                caught = e
            }
            assertNotNull("RateLimitTimeoutException expected", caught)
        }

    @Test
    fun `GRPC and REST have separate budgets`() =
        runTest {
            val fakeClock = newClock()
            val limiter = TInvestRateLimiter(fakeClock, TestLogger)

            repeat(15) {
                limiter.withUnaryPermission(TransportProtocol.GRPC, "orders", "postOrder") { }
            }

            val job =
                launch {
                    limiter.withUnaryPermission(TransportProtocol.REST, "orders", "postOrder") { }
                }
            testScheduler.runCurrent()
            assertTrue("REST budget must not be affected by GRPC exhaustion", job.isCompleted)
        }

    @Test
    fun `waiting does not consume other levels (all-or-nothing)`() =
        runTest {
            val fakeClock = newClock()
            val limiter = TInvestRateLimiter(fakeClock, TestLogger)

            repeat(15) {
                limiter.withUnaryPermission(TransportProtocol.GRPC, "orders", "postOrder") { }
            }

            val waitingJob =
                launch {
                    limiter.withUnaryPermission(TransportProtocol.GRPC, "orders", "postOrder") { }
                }
            testScheduler.runCurrent()
            assertFalse("16th postOrder must wait", waitingJob.isCompleted)

            val otherJob =
                launch {
                    limiter.withUnaryPermission(TransportProtocol.GRPC, "orders", "getOrders") { }
                }
            testScheduler.runCurrent()
            assertTrue(
                "getOrders must pass: per-method postOrder exhausted, per-service/global unaffected",
                otherJob.isCompleted,
            )

            waitingJob.cancel()
            testScheduler.runCurrent()
        }
}
