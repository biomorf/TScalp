package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.domain.models.FutureUi
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Кеш результатов поиска инструментов.
 * Хранит результаты по составному ключу "brokerName:query".
 * Выполняет сортировку фьючерсов по дате экспирации.
 */
class SearchCache(private val brokerManager: BrokerManager) {

    // Ключ: "brokerName:нормализованныйЗапрос"
    private val cache = ConcurrentHashMap<String, List<InstrumentUi>>()

    /**
     * Выполняет поиск инструментов через указанного брокера, кеширует результат
     * и сортирует фьючерсы по дате экспирации (ближайшие сверху).
     */
    suspend fun search(brokerName: BrokerName, query: String): List<InstrumentUi> {
        val normalizedQuery = query.trim().lowercase()
        val key = "${brokerName.key}:$normalizedQuery"

        cache[key]?.let { return it }

        val broker = brokerManager.getBroker(brokerName)
            ?: throw IllegalArgumentException("Брокер ${brokerName.displayName} не найден")

        val results = withContext(Dispatchers.IO) {
            broker.findInstruments(query)
        }

        val sorted = results.sortedWith(
            compareBy<InstrumentUi> { it !is FutureUi }
                .thenBy { (it as? FutureUi)?.expirationDate }
        )

        cache[key] = sorted
        return sorted
    }

    /**
     * Инвалидирует кеш для конкретного запроса и брокера.
     * Используется при нажатии кнопки "Обновить".
     */
    fun invalidate(brokerName: BrokerName, query: String) {
        val normalizedQuery = query.trim().lowercase()
        val key = "${brokerName.key}:$normalizedQuery"
        cache.remove(key)
    }
}