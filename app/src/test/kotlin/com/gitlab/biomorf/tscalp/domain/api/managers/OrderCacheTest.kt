package com.gitlab.biomorf.tscalp.domain.api.managers

import com.gitlab.biomorf.tscalp.domain.models.Order
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderCacheTest {
    private fun order(
        orderId: String,
        orderDate: Long? = 0L,
        price: Double = 100.0,
    ) = Order.Regular(
        orderId = orderId,
        ticker = "SBER",
        tscalpInstrumentId = "uid-sber",
        instrumentType = "share",
        direction = "BUY",
        price = price,
        quantity = 1L,
        orderType = OrderTypeSelection.Limit,
        status = "NEW",
        orderDate = orderDate,
    )

    // ---------- putFromPolling / putFromStream / remove ----------

    @Test
    fun `put from polling stores entry`() {
        val cache = OrderCache(ttlMs = 100L)
        cache.putFromPolling(order("o1"), now = 10L)

        assertEquals(1, cache.snapshot().size)
        assertEquals("o1", cache.snapshot().first().orderId)
    }

    @Test
    fun `put from stream stores entry`() {
        val cache = OrderCache(ttlMs = 100L)
        cache.putFromStream(order("o1"), now = 10L)

        assertEquals(1, cache.snapshot().size)
    }

    @Test
    fun `remove drops entry`() {
        val cache = OrderCache(ttlMs = 100L)
        cache.putFromPolling(order("o1"), now = 10L)
        cache.remove("o1")

        assertTrue(cache.snapshot().isEmpty())
    }

    // ---------- evictStale: TTL ----------

    @Test
    fun `polling entry is kept before ttl`() {
        val cache = OrderCache(ttlMs = 100L)
        cache.putFromPolling(order("o1"), now = 0L)
        cache.evictStale(now = 50L)

        assertEquals(1, cache.snapshot().size)
    }

    @Test
    fun `polling entry is evicted after ttl`() {
        val cache = OrderCache(ttlMs = 100L)
        cache.putFromPolling(order("o1"), now = 0L)
        cache.evictStale(now = 150L)

        assertTrue(cache.snapshot().isEmpty())
    }

    @Test
    fun `stream-only entry is evicted after ttl`() {
        val cache = OrderCache(ttlMs = 100L)
        cache.putFromStream(order("o1"), now = 0L)
        cache.evictStale(now = 150L)

        assertTrue(cache.snapshot().isEmpty())
    }

    // ---------- evictStale: совмещение двух источников ----------

    @Test
    fun `stale polling plus fresh stream is kept`() {
        val cache = OrderCache(ttlMs = 100L)
        cache.putFromPolling(order("o1"), now = 0L)
        cache.putFromStream(order("o1"), now = 80L)
        // cutoff = 150 - 100 = 50; lastSeenAt=0 < 50, lastStreamAt=80 > 50 → keep.
        cache.evictStale(now = 150L)

        assertEquals(1, cache.snapshot().size)
    }

    @Test
    fun `fresh polling plus stale stream is kept`() {
        val cache = OrderCache(ttlMs = 100L)
        cache.putFromStream(order("o1"), now = 0L)
        cache.putFromPolling(order("o1"), now = 80L)
        // cutoff = 150 - 100 = 50; lastSeenAt=80 > 50, lastStreamAt=0 < 50 → keep.
        cache.evictStale(now = 150L)

        assertEquals(1, cache.snapshot().size)
    }

    @Test
    fun `putFromPolling preserves lastStreamAt from earlier stream event`() {
        val cache = OrderCache(ttlMs = 100L)
        // Свежий stream-событие, затем «устаревший» polling-снимок.
        cache.putFromStream(order("o1"), now = 90L)
        cache.putFromPolling(order("o1"), now = 10L)
        // Если putFromPolling сбросил бы lastStreamAt в 0, то max(10, 0)=10,
        // cutoff = 120-100=20 → 10 < 20 → удалит. Ожидаем: keep.
        cache.evictStale(now = 120L)

        assertEquals(1, cache.snapshot().size)
    }

    @Test
    fun `putFromStream preserves lastSeenAt from earlier polling event`() {
        val cache = OrderCache(ttlMs = 100L)
        cache.putFromPolling(order("o1"), now = 90L)
        cache.putFromStream(order("o1"), now = 10L)
        // Если putFromStream сбросил бы lastSeenAt в 0, то max(0, 10)=10,
        // cutoff = 120-100=20 → 10 < 20 → удалит. Ожидаем: keep.
        cache.evictStale(now = 120L)

        assertEquals(1, cache.snapshot().size)
    }

    // ---------- snapshot / clear ----------

    @Test
    fun `snapshot sorts by orderDate then price`() {
        val cache = OrderCache(ttlMs = 100L)
        cache.putFromPolling(order("c", orderDate = 30L, price = 100.0), now = 1L)
        cache.putFromPolling(order("a", orderDate = 10L, price = 100.0), now = 1L)
        cache.putFromPolling(order("b", orderDate = 20L, price = 50.0), now = 1L)

        val ids = cache.snapshot().map { it.orderId }
        assertEquals(listOf("a", "b", "c"), ids)
    }

    @Test
    fun `snapshot places null orderDate at the end`() {
        val cache = OrderCache(ttlMs = 100L)
        cache.putFromPolling(order("a", orderDate = 10L), now = 1L)
        cache.putFromPolling(order("b", orderDate = null), now = 1L)

        val ids = cache.snapshot().map { it.orderId }
        assertEquals(listOf("a", "b"), ids)
    }

    @Test
    fun `clear wipes everything`() {
        val cache = OrderCache(ttlMs = 100L)
        cache.putFromPolling(order("o1"), now = 1L)
        cache.putFromStream(order("o2"), now = 2L)
        cache.clear()

        assertTrue(cache.snapshot().isEmpty())
    }
}
