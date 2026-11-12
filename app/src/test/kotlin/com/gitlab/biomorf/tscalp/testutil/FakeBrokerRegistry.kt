package com.gitlab.biomorf.tscalp.testutil

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake-реализация [BrokerRegistry] для Hilt-тестов.
 *
 * Заменяет `RegistryModule.provideBrokerRegistry` через
 * `@UninstallModules(RegistryModule::class)` + `@BindValue`.
 * Дефолт — пустой реестр, `anyInitialized = false` (сценарий
 * «API не подключён»). Для сценариев «API инициализирован» —
 * `FakeBrokerRegistry(anyInitialized = true)` либо
 * [setAnyInitialized] во время теста.
 *
 * [getDefaultBroker] бросает `IllegalStateException`, если
 * `brokers` пуст — контракт `BrokerRegistry` не нарушается.
 */
class FakeBrokerRegistry(
    anyInitialized: Boolean = false,
    private val brokers: Map<BrokerName, BrokerApi> = emptyMap(),
) : BrokerRegistry {
    private val _anyInitialized = MutableStateFlow(anyInitialized)
    override val anyInitialized: StateFlow<Boolean> = _anyInitialized.asStateFlow()

    override fun getBroker(name: BrokerName): BrokerApi? = brokers[name]

    override fun getDefaultBroker(): BrokerApi =
        brokers[BrokerRegistry.defaultBroker]
            ?: throw IllegalStateException(
                "FakeBrokerRegistry: ${BrokerRegistry.defaultBroker} not registered",
            )

    override fun getAvailableBrokerNames(): List<BrokerName> = BrokerName.entries.filter { brokers.containsKey(it) }

    override fun getAllBrokers(): List<BrokerApi> = getAvailableBrokerNames().mapNotNull { brokers[it] }

    override fun refreshInitializationState() {
        // no-op: fake state is explicit; tests update via setAnyInitialized.
    }

    fun setAnyInitialized(value: Boolean) {
        _anyInitialized.value = value
    }
}
