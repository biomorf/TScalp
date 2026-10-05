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
        val broker = brokerManager.getBroker(BrokerName.TINVEST) as? TInvestBrokerAPI ?: return null
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