package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.data.api.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi

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
    // Ключ — tscalpInstrumentId
    private val cache = ConcurrentHashMap<String, InstrumentUi>()
    private val mutex = Mutex()

    /**
     * Возвращает актуальный InstrumentUi по tscalpInstrumentId.
     * Если в кэше нет, загружает через брокера и кэширует.
     */
    suspend fun getInstrument(tscalpInstrumentId: String): InstrumentUi? {
        cache[tscalpInstrumentId]?.let { return it }
        return mutex.withLock {
            cache[tscalpInstrumentId] ?: loadAndCache(tscalpInstrumentId)
        }
    }

    private suspend fun loadAndCache(tscalpInstrumentId: String): InstrumentUi? {
        val broker = brokerManager.getBroker(BrokerName.TINVEST) as? TInvestBrokerAPI ?: return null
        val instrument = broker.fetchFullInstrument(tscalpInstrumentId)
        if (instrument != null) {
            cache[tscalpInstrumentId] = instrument
        }
        return instrument
    }

    /**
     * Принудительно обновляет кэш для указанного tscalpInstrumentId.
     */
    suspend fun refreshInstrument(tscalpInstrumentId: String) {
        mutex.withLock {
            cache.remove(tscalpInstrumentId)
            loadAndCache(tscalpInstrumentId)
        }
    }
}