package com.gitlab.biomorf.tscalp.data.registry

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BrokerRegistryImpl(
    private val brokers: Map<BrokerName, BrokerApi>,
) : BrokerRegistry {
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
    override fun getBroker(name: BrokerName): BrokerApi? = brokers[name]

    /**
     * Брокер по умолчанию (Т‑Инвестиции).
     */
    override fun getDefaultBroker(): BrokerApi =
        brokers[BrokerName.TINVEST]
            ?: throw IllegalStateException("Брокер TInvest не зарегистрирован")

    /**
     * Возвращает список ключей брокеров в типизированном виде — для UI и pager'а.
     */
    override fun getAvailableBrokerNames(): List<BrokerName> = brokers.keys.toList()

    /**
     * Возвращает список всех брокеров (для массовых операций).
     */
    override fun getAllBrokers(): List<BrokerApi> = brokers.values.toList()

    /**
     * Пересчитывает состояние инициализации.
     * Вызывается после успешной инициализации любого брокера,
     * чтобы ViewModel получили актуальное значение через StateFlow.
     */
    override fun refreshInitializationState() {
        _anyInitialized.value = computeAnyInitialized()
    }

    private fun computeAnyInitialized(): Boolean = brokers.values.any { it.isInitialized }
}
