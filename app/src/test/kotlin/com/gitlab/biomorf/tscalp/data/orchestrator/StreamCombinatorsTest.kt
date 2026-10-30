package com.gitlab.biomorf.tscalp.data.orchestrator

import io.grpc.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StreamCombinatorsTest {
    @Test
    fun `non-retryable UNAUTHENTICATED is thrown, not retried`() =
        runTest {
            var calls = 0
            val failure = Status.UNAUTHENTICATED.asException()

            try {
                retryWithBackoff(delays = longArrayOf(0L)) {
                    calls++
                    flow<Unit> { throw failure }
                }.first()
                fail("expected StatusException")
            } catch (e: Throwable) {
                assertTrue("expected StatusException, got $e", e is io.grpc.StatusException)
            }

            assertEquals(1, calls)
        }

    @Test
    fun `non-retryable NOT_FOUND is thrown, not retried`() =
        runTest {
            var calls = 0
            val failure = Status.NOT_FOUND.asException()

            try {
                retryWithBackoff(delays = longArrayOf(0L)) {
                    calls++
                    flow<Unit> { throw failure }
                }.first()
                fail("expected StatusException")
            } catch (e: Throwable) {
                assertTrue("expected StatusException, got $e", e is io.grpc.StatusException)
            }

            assertEquals(1, calls)
        }

    @Test
    fun `retryable UNAVAILABLE is retried until source emits`() =
        runTest {
            var calls = 0
            val result =
                retryWithBackoff(delays = longArrayOf(0L)) {
                    calls++
                    flow {
                        if (calls >= 3) emit(42) else throw Status.UNAVAILABLE.asException()
                    }
                }.first()

            assertEquals(42, result)
            assertEquals(3, calls)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `normal completion delays before reconnect`() =
        runTest {
            var calls = 0

            val collected =
                retryWithBackoff(delays = longArrayOf(0L)) {
                    calls++
                    flow { emit(calls) }
                }.take(3).toList()

            assertEquals(listOf(1, 2, 3), collected)
            assertEquals(3, calls)
            assertTrue(
                "expected virtual time >= 2000, got ${'$'}{testScheduler.currentTime}",
                testScheduler.currentTime >= 2_000L,
            )
        }

    @Test
    fun `CancellationException is rethrown, not retried`() =
        runTest {
            var calls = 0

            try {
                retryWithBackoff(delays = longArrayOf(0L)) {
                    calls++
                    flow<Unit> { throw CancellationException("cancelled") }
                }.first()
                fail("expected CancellationException")
            } catch (e: Throwable) {
                assertTrue("expected CancellationException, got $e", e is CancellationException)
            }

            assertEquals(1, calls)
        }
}
