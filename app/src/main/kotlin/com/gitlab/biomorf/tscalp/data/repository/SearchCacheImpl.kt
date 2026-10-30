package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.SearchCacheRepository
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.FutureUi
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Кеш результатов поиска инструментов.
 * Хранит результаты по составному ключу "brokerName:query".
 * Выполняет сортировку фьючерсов по дате экспирации.
 */
@Singleton
class SearchCacheImpl
    @Inject
    constructor(
        private val brokerManager: BrokerRegistry,
    ) : SearchCacheRepository {
        // Ключ: "brokerName:нормализованныйЗапрос"
        private val cache = ConcurrentHashMap<String, List<InstrumentUi>>()

        /**
         * Выполняет поиск инструментов через указанного брокера, кеширует результат
         * и сортирует фьючерсы по дате экспирации (ближайшие сверху).
         */
        override suspend fun search(
            brokerName: BrokerName,
            query: String,
        ): AppResult<List<InstrumentUi>> {
            val normalizedQuery = query.trim().lowercase()
            val key = "${brokerName.key}:$normalizedQuery"

            cache[key]?.let { return AppResult.Success(it) }

            val broker =
                brokerManager.getBroker(brokerName)
                    ?: return AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не найден"),
                    )

            val results = broker.userInstruments?.findInstruments(query) ?: emptyList()

            val sorted =
                results.sortedWith(
                    compareBy<InstrumentUi> { it !is FutureUi }
                        .thenBy { (it as? FutureUi)?.expirationDate },
                )

            cache[key] = sorted
            return AppResult.Success(sorted)
        }

        /**
         * Инвалидирует кеш для конкретного запроса и брокера.
         * Используется при нажатии кнопки "Обновить".
         */
        override fun invalidate(
            brokerName: BrokerName,
            query: String,
        ) {
            val normalizedQuery = query.trim().lowercase()
            val key = "${brokerName.key}:$normalizedQuery"
            cache.remove(key)
        }
    }
