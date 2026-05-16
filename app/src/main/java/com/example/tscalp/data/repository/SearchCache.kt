package com.example.tscalp.data.repository

import com.example.tscalp.domain.models.FutureUi
import com.example.tscalp.di.BrokerManager
import com.example.tscalp.domain.api.BrokerApi
import com.example.tscalp.domain.models.InstrumentUi
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
    suspend fun search(brokerName: String, query: String): List<InstrumentUi> {
        val normalizedQuery = query.trim().lowercase()
        val key = "$brokerName:$normalizedQuery"

        cache[key]?.let { return it }

        val broker = brokerManager.getBroker(brokerName)
            ?: throw IllegalArgumentException("Брокер $brokerName не найден")

        val results = withContext(Dispatchers.IO) {
            broker.findInstruments(query)
        }

        // Сортировка: сначала обычные инструменты, затем фьючерсы по дате
        val sorted = results.sortedWith(
            compareBy<InstrumentUi> { it !is FutureUi }       // обычные выше
                .thenBy { (it as? FutureUi)?.expirationDate } // фьючерсы по дате
        )

        cache[key] = sorted
        return sorted
    }

    /**
     * Инвалидирует кеш для конкретного запроса и брокера.
     * Используется при нажатии кнопки "Обновить".
     */
    fun invalidate(brokerName: String, query: String) {
        val normalizedQuery = query.trim().lowercase()
        val key = "$brokerName:$normalizedQuery"
        cache.remove(key)
    }
}