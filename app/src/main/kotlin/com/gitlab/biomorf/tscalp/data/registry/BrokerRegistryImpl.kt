package com.gitlab.biomorf.tscalp.data.registry

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BrokerRegistryImpl(
    brokers: Set<BrokerApi>,
) : BrokerRegistry {
    /** Map по имени; порядок не используется — порядок задаёт [BrokerName.entries]. */
    private val brokersByName: Map<BrokerName, BrokerApi> = brokers.associateBy { it.name }

    /**
     * Есть ли хотя бы один инициализированный брокер.
     * Реактивный источник для ViewModel вместо синхронного опроса.
     * Обновляется через [refreshInitializationState].
     */
    private val _anyInitialized = MutableStateFlow(computeAnyInitialized())
    override val anyInitialized: StateFlow<Boolean> = _anyInitialized.asStateFlow()

    /**
     * Возвращает брокера по имени. Типобезопасный вариант.
     */
    override fun getBroker(name: BrokerName): BrokerApi? = brokersByName[name]

    /**
     * Брокер по умолчанию (Т‑Инвестиции).
     */
    override fun getDefaultBroker(): BrokerApi =
        brokersByName[BrokerRegistry.defaultBroker]
            ?: throw IllegalStateException("Брокер ${BrokerRegistry.defaultBroker} не зарегистрирован")

    /**
     * Возвращает список ключей брокеров в типизированном виде — для UI и pager'а.
     */
    override fun getAvailableBrokerNames(): List<BrokerName> = BrokerName.entries.filter { brokersByName.containsKey(it) }

    /**
     * Возвращает список всех брокеров (для массовых операций).
     */
    override fun getAllBrokers(): List<BrokerApi> = getAvailableBrokerNames().mapNotNull { brokersByName[it] }

    /**
     * Пересчитывает состояние инициализации.
     * Вызывается после успешной инициализации любого брокера,
     * чтобы ViewModel получили актуальное значение через StateFlow.
     */
    override fun refreshInitializationState() {
        _anyInitialized.value = computeAnyInitialized()
    }

    private fun computeAnyInitialized(): Boolean = brokersByName.values.any { it.isInitialized }
}
