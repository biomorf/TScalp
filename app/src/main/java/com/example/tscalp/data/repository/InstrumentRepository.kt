package com.example.tscalp.data.repository

import com.example.tscalp.data.api.TInvestInvestService
import com.example.tscalp.di.BrokerManager
import com.example.tscalp.domain.models.InstrumentUi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Единый репозиторий для доменных моделей инструментов.
 * Хранит полностью загруженные InstrumentUi (включая pointValue для фьючерсов)
 * в in‑memory кэше и предоставляет их по требованию.
 */
class InstrumentRepository(
    private val brokerManager: BrokerManager
) {
    // Ключ — tscalpInstrumentId (uid)
    private val cache = ConcurrentHashMap<String, InstrumentUi>()
    private val mutex = Mutex()

    /**
     * Возвращает актуальный InstrumentUi по uid.
     * Если в кэше нет, загружает через TInvest-сервис и кэширует.
     */
    suspend fun getInstrument(uid: String): InstrumentUi? {
        cache[uid]?.let { return it }
        return mutex.withLock {
            cache[uid] ?: loadAndCache(uid)
        }
    }

    private suspend fun loadAndCache(uid: String): InstrumentUi? {
        val broker = brokerManager.getBroker("TInvest") as? TInvestInvestService ?: return null
        val instrument = broker.fetchFullInstrument(uid)
        if (instrument != null) {
            cache[uid] = instrument
        }
        return instrument
    }

    /**
     * Принудительно обновляет кэш для указанного uid.
     */
    suspend fun refreshInstrument(uid: String) {
        mutex.withLock {
            cache.remove(uid)
            loadAndCache(uid)
        }
    }
}