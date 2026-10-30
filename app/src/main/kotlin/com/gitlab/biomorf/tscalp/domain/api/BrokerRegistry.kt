package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.domain.api.roles.BcsLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.FinamLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.TInvestLifecycleApi
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-контракт реестра брокеров.
 *
 * Точка сборки брокеров: список зарегистрированных, доступ к
 * конкретному по [BrokerName], брокер по умолчанию, реактивное
 * состояние «есть ли хотя бы один инициализированный».
 *
 * Реализация — `data/registry/BrokerRegistryImpl.kt` (H22).
 * Потребители: presentation (3 VM), domain (3 менеджера), data
 * (6 репозиториев), app (`TScalpApplication`). Инжектят
 * интерфейс, не реализацию.
 */
interface BrokerRegistry {
    /**
     * Есть ли хотя бы один инициализированный брокер.
     * Реактивный источник для ViewModel вместо синхронного опроса.
     * Обновляется через [refreshInitializationState].
     */
    val anyInitialized: StateFlow<Boolean>

    /** Брокер по имени. `null`, если не зарегистрирован. */
    fun getBroker(name: BrokerName): BrokerApi?

    /**
     * Брокер по умолчанию (T-Invest).
     * @throws IllegalStateException если T-Invest не зарегистрирован.
     */
    fun getDefaultBroker(): BrokerApi

    /** Ключи брокеров в типизированном виде — для UI и pager'а. */
    fun getAvailableBrokerNames(): List<BrokerName>

    /** Все брокеры (для массовых операций). */
    fun getAllBrokers(): List<BrokerApi>

    /**
     * Пересчитывает состояние инициализации.
     *
     * **Временный side-effect.** Должен вызываться после
     * успешной инициализации любого брокера, чтобы VM получили
     * актуальное [anyInitialized]. В перспективе — автообновление
     * через [TInvestLifecycleApi.initialize] / [BcsLifecycleApi.initialize] /
     * [FinamLifecycleApi.initialize] (отдельная задача,
     * clean-audit Н50/Н51).
     */
    fun refreshInitializationState()
}
