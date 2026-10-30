package com.gitlab.biomorf.tscalp.domain.api.managers

import com.gitlab.biomorf.tscalp.domain.models.Order
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Кэш активных заявок с TTL-эвикцией.
 *
 * Хранит последнее состояние каждой заявки по `orderId` и
 * метки времени последнего наблюдения из двух источников:
 *
 * - [lastSeenAt] — последний polling-snapshot, где заявка была.
 * - [lastStreamAt] — последнее stream-событие по этой заявке.
 *
 * [evictStale] удаляет записи, у которых **оба** источника старше
 * `now - ttl`. TTL задаётся параметром конструктора; в
 * [OrderFlowManager] — `2 × POLLING_INTERVAL` (30 сек при
 * интервале polling 15 сек).
 *
 * Фактическое удержание без подтверждения = `ttl + один
 * polling-цикл` ≈ **3 × интервал polling** (45 сек при интервале
 * 15 сек). Так потому, что эвикция идёт **после** [putFromPolling]
 * в том же цикле. Осознанно: заявка не должна исчезать при
 * единичном пропуске в snapshot — T-Invest иногда возвращает
 * неполный список (баг, задержка индексации).
 *
 * Зачем эвикция: `OrderStateStream` не отдаёт stop-orders
 * (asyncapi) и может терять regular-события при обрыве. Без неё
 * отменённые и исполненные заявки остаются в кэше навсегда.
 *
 * Вызывается **только при успешном polling-цикле**. Если polling
 * упал (gRPC down), `lastSeenAt` не обновляется, и эвикция
 * удалила бы валидные заявки. Поэтому [OrderFlowManager] не
 * вызывает [evictStale] при ошибке.
 *
 * Синхронизация: `OrderFlowManager` использует
 * `managerDispatcher.limitedParallelism(1)`, поэтому polling и stream
 * вызываются последовательно на одном worker'е. Мutex не нужен.
 */
internal class OrderCache(
    private val ttl: Duration,
) {
    private data class Entry(
        val item: Order,
        val lastSeenAt: Instant,
        val lastStreamAt: Instant,
    )

    private val entries = mutableMapOf<String, Entry>()

    /** Добавить/обновить из polling-snapshot. */
    fun putFromPolling(
        item: Order,
        now: Instant,
    ) {
        val existing = entries[item.orderId]
        entries[item.orderId] =
            Entry(
                item = item,
                lastSeenAt = now,
                lastStreamAt = existing?.lastStreamAt ?: Instant.DISTANT_PAST,
            )
    }

    /** Добавить/обновить из stream-события. */
    fun putFromStream(
        item: Order,
        now: Instant,
    ) {
        val existing = entries[item.orderId]
        entries[item.orderId] =
            Entry(
                item = item,
                lastSeenAt = existing?.lastSeenAt ?: Instant.DISTANT_PAST,
                lastStreamAt = now,
            )
    }

    /** Удалить заявку по `orderId` (терминальный статус из stream). */
    fun remove(orderId: String) {
        entries.remove(orderId)
    }

    /**
     * Удалить записи, у которых и `lastSeenAt`, и `lastStreamAt`
     * старше `now - ttl`.
     */
    fun evictStale(now: Instant) {
        if (entries.isEmpty()) return
        val cutoff = now - ttl
        entries.entries.removeAll { (_, e) ->
            val lastTouched = maxOf(e.lastSeenAt, e.lastStreamAt)
            lastTouched < cutoff
        }
    }

    /** Снимок для UI: отсортирован по `orderDate`, затем по `price`. */
    fun snapshot(): List<Order> =
        entries.values
            .map { it.item }
            .sortedWith(
                compareBy<Order> { it.orderDate ?: Long.MAX_VALUE }
                    .thenBy { it.price },
            )

    /** Полная очистка (при смене счёта или stop). */
    fun clear() {
        entries.clear()
    }
}
