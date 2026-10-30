package com.gitlab.biomorf.tscalp.domain.api.managers

import com.gitlab.biomorf.tscalp.domain.log.TestLogger
import com.gitlab.biomorf.tscalp.domain.models.StreamState
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class StreamStateTrackerTest {
    private val t0 = Instant.fromEpochMilliseconds(1_000_000)

    private fun newTracker(): StreamStateTracker =
        StreamStateTracker(
            logger = TestLogger,
            tag = "test",
            streamSilence = 100.milliseconds,
            stale = 200.milliseconds,
        )

    @Test
    fun `initial state is STOPPED`() {
        assertEquals(StreamState.STOPPED, newTracker().state.value)
    }

    @Test
    fun `onStart transitions to INITIALIZING`() {
        val tracker = newTracker()
        tracker.onStart(t0)
        assertEquals(StreamState.INITIALIZING, tracker.state.value)
    }

    @Test
    fun `stream emit transitions to LIVE`() {
        val tracker = newTracker()
        tracker.onStart(t0)
        tracker.onStreamEmit(t0 + 1.milliseconds)
        assertEquals(StreamState.LIVE, tracker.state.value)
    }

    @Test
    fun `polling success from INITIALIZING transitions to POLLING`() {
        val tracker = newTracker()
        tracker.onStart(t0)
        tracker.onPollingSuccess(t0 + 1.milliseconds)
        assertEquals(StreamState.POLLING, tracker.state.value)
    }

    @Test
    fun `polling success from LIVE does not downgrade`() {
        val tracker = newTracker()
        tracker.onStart(t0)
        tracker.onStreamEmit(t0 + 1.milliseconds)
        tracker.onPollingSuccess(t0 + 2.milliseconds)
        assertEquals(StreamState.LIVE, tracker.state.value)
    }

    @Test
    fun `LIVE with silent stream and live polling transitions to POLLING`() {
        val tracker = newTracker()
        tracker.onStart(t0)
        tracker.onStreamEmit(t0 + 1.milliseconds)
        tracker.onPollingSuccess(t0 + 50.milliseconds)
        tracker.tick(t0 + 150.milliseconds)
        assertEquals(StreamState.POLLING, tracker.state.value)
    }

    @Test
    fun `LIVE stays LIVE when stream alive and polling silent`() {
        val tracker = newTracker()
        tracker.onStart(t0)
        tracker.onStreamEmit(t0 + 1.milliseconds)
        tracker.onStreamEmit(t0 + 500.milliseconds)
        tracker.tick(t0 + 550.milliseconds)
        assertEquals(StreamState.LIVE, tracker.state.value)
    }

    @Test
    fun `LIVE with both sources silent transitions to STALE`() {
        val tracker = newTracker()
        tracker.onStart(t0)
        tracker.onStreamEmit(t0 + 1.milliseconds)
        tracker.tick(t0 + 250.milliseconds)
        assertEquals(StreamState.STALE, tracker.state.value)
    }

    @Test
    fun `POLLING with silent polling transitions to STALE`() {
        val tracker = newTracker()
        tracker.onStart(t0)
        tracker.onPollingSuccess(t0 + 1.milliseconds)
        tracker.tick(t0 + 250.milliseconds)
        assertEquals(StreamState.STALE, tracker.state.value)
    }

    @Test
    fun `STALE recovers to POLLING on polling success`() {
        val tracker = newTracker()
        tracker.onStart(t0)
        tracker.tick(t0 + 250.milliseconds)
        assertEquals(StreamState.STALE, tracker.state.value)
        tracker.onPollingSuccess(t0 + 251.milliseconds)
        assertEquals(StreamState.POLLING, tracker.state.value)
    }

    @Test
    fun `STALE recovers to LIVE on stream emit`() {
        val tracker = newTracker()
        tracker.onStart(t0)
        tracker.tick(t0 + 250.milliseconds)
        assertEquals(StreamState.STALE, tracker.state.value)
        tracker.onStreamEmit(t0 + 251.milliseconds)
        assertEquals(StreamState.LIVE, tracker.state.value)
    }

    @Test
    fun `onStop transitions to STOPPED`() {
        val tracker = newTracker()
        tracker.onStart(t0)
        tracker.onStreamEmit(t0 + 1.milliseconds)
        tracker.onStop()
        assertEquals(StreamState.STOPPED, tracker.state.value)
    }

    @Test
    fun `tick from STOPPED is no-op`() {
        val tracker = newTracker()
        tracker.tick(t0 + 1000.milliseconds)
        assertEquals(StreamState.STOPPED, tracker.state.value)
    }

    @Test
    fun `onStart after onStop resets to INITIALIZING`() {
        val tracker = newTracker()
        tracker.onStart(t0)
        tracker.onStreamEmit(t0 + 1.milliseconds)
        tracker.onStop()
        tracker.onStart(t0 + 1000.milliseconds)
        assertEquals(StreamState.INITIALIZING, tracker.state.value)
    }
}
