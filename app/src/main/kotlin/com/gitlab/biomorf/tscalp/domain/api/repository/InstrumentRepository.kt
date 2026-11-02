package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.Instrument

/**
 * Domain-контракт доступа к доменным моделям инструментов.
 *
 * Реализация — `data/repository/InstrumentRepositoryImpl.kt` (H21b).
 * Создан в рамках H21b, поведение перенесено из
 * `data.repository.InstrumentRepository` без изменений.
 */
interface InstrumentRepository {
    /**
     * Возвращает актуальный `Instrument` по `tscalpInstrumentId`.
     * Если в кэше нет — загружает через брокера и кэширует.
     */
    suspend fun getInstrument(tscalpInstrumentId: String): Instrument?
}
