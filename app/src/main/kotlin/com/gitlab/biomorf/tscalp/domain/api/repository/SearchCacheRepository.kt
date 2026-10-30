package com.gitlab.biomorf.tscalp.domain.api.repository

import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi

/**
 * Domain-контракт кеша результатов поиска инструментов.
 *
 * Реализация — `data/repository/SearchCacheRepositoryImpl.kt` (H21b).
 * Создан в рамках H21b, поведение перенесено из
 * `data.repository.SearchCache` без изменений.
 *
 * Контракт ошибок: `search` возвращает [AppResult.Failure] с
 * [com.gitlab.biomorf.tscalp.domain.models.AppError.Unknown] при
 * ненайденном брокере (clean-audit Н46). Исключения на границе
 * слоя не бросаются.
 */
interface SearchCacheRepository {
    /**
     * Поиск инструментов через указанного брокера с кешированием.
     * Результат отсортирован: фьючерсы по дате экспирации, ближайшие сверху.
     */
    suspend fun search(
        brokerName: BrokerName,
        query: String,
    ): AppResult<List<InstrumentUi>>

    /** Инвалидирует кеш для конкретного запроса и брокера. */
    fun invalidate(
        brokerName: BrokerName,
        query: String,
    )
}
