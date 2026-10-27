package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.api.repository.InstrumentRepository
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единый репозиторий для доменных моделей инструментов.
 * Хранит полностью загруженные InstrumentUi (включая pointValue для фьючерсов)
 * в in‑memory кэше и предоставляет их по требованию.
 */
@Singleton
class InstrumentRepositoryImpl
    @Inject
    constructor(
        private val brokerManager: BrokerManager,
    ) : InstrumentRepository {
        // Ключ — tscalpInstrumentId
        private val cache = ConcurrentHashMap<String, InstrumentUi>()
        private val mutex = Mutex()

        /**
         * Возвращает актуальный InstrumentUi по tscalpInstrumentId.
         * Если в кэше нет, загружает через брокера и кэширует.
         */
        override suspend fun getInstrument(tscalpInstrumentId: String): InstrumentUi? {
            cache[tscalpInstrumentId]?.let { return it }
            return mutex.withLock {
                cache[tscalpInstrumentId] ?: loadAndCache(tscalpInstrumentId)
            }
        }

        private suspend fun loadAndCache(tscalpInstrumentId: String): InstrumentUi? {
            val broker = brokerManager.getBroker(BrokerName.TINVEST) ?: return null
            val instrument = broker.instrument?.fetchFullInstrument(tscalpInstrumentId)
            if (instrument != null) {
                cache[tscalpInstrumentId] = instrument
            }
            return instrument
        }
    }
