package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


class BrokerManager(private val brokers: Map<BrokerName, BrokerApi>) {

    /**
     * Есть ли хотя бы один инициализированный брокер.
     * Реактивный источник для ViewModel вместо синхронного опроса.
     * Обновляется через [refreshInitializationState].
     */
    private val _anyInitialized = MutableStateFlow(computeAnyInitialized())
    val anyInitialized: StateFlow<Boolean> = _anyInitialized.asStateFlow()

    /**
     * Возвращает брокера по имени. Типобезопасный вариант.
     */
    fun getBroker(name: BrokerName): BrokerApi? = brokers[name]

    /**
     * Брокер по умолчанию (Т‑Инвестиции).
     */
    fun getDefaultBroker(): BrokerApi = brokers[BrokerName.TINVEST]
        ?: throw IllegalStateException("Брокер TInvest не зарегистрирован")

    /**
     * Возвращает список ключей брокеров в типизированном виде — для UI и pager'а.
     */
    fun getAvailableBrokerNames(): List<BrokerName> = brokers.keys.toList()

    /**
     * Возвращает список всех брокеров (для массовых операций).
     */
    fun getAllBrokers(): List<BrokerApi> = brokers.values.toList()

    /**
     * Пересчитывает состояние инициализации.
     * Вызывается после успешной инициализации любого брокера,
     * чтобы ViewModel получили актуальное значение через StateFlow.
     */
    fun refreshInitializationState() {
        _anyInitialized.value = computeAnyInitialized()
    }

    private fun computeAnyInitialized(): Boolean =
        brokers.values.any { it.isInitialized }
}