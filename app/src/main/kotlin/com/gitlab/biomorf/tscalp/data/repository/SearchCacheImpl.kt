package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.SearchCacheRepository
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.SearchItem
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
        private val cache = ConcurrentHashMap<String, List<SearchItem>>()

        /**
         * Выполняет поиск инструментов через указанного брокера, кеширует результат
         * и сортирует фьючерсы по дате экспирации (ближайшие сверху).
         */
        override suspend fun search(
            brokerName: BrokerName,
            query: String,
        ): AppResult<List<SearchItem>> {
            val normalizedQuery = query.trim().lowercase()
            val key = "${brokerName.key}:$normalizedQuery"

            cache[key]?.let { return AppResult.Success(it) }

            val broker =
                brokerManager.getBroker(brokerName)
                    ?: return AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не найден"),
                    )

            val results = broker.userInstruments?.findInstruments(query) ?: emptyList()
            cache[key] = results
            return AppResult.Success(results)
        }

        override fun isCached(
            brokerName: BrokerName,
            query: String,
        ): Boolean {
            val normalizedQuery = query.trim().lowercase()
            val key = "${brokerName.key}:$normalizedQuery"
            return cache.containsKey(key)
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
