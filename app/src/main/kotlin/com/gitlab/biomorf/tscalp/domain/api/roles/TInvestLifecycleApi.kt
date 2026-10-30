package com.gitlab.biomorf.tscalp.domain.api.roles

/**
 * Lifecycle T-Invest: login по [BrokerCredentials.TInvest], logout.
 *
 * Compile-time типизация: `initialize` принимает только
 * [BrokerCredentials.TInvest]; неверный тип не компилируется
 * (generic-альтернатива не работает — JVM erasure).
 *
 * Доступ через `BrokerApi.lifecycleTInvest` (`null` = не поддерживает).
 */
interface TInvestLifecycleApi {
    /** @return `true` — успех, `false` — ошибка (сеть, отказ сервера). */
    suspend fun initialize(credentials: BrokerCredentials.TInvest): Boolean

    /** Деинициализация: закрыть каналы, обнулить состояние. */
    fun deinitialize()
}
