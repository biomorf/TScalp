package com.gitlab.biomorf.tscalp.domain.api.roles

/**
 * Типизированные credentials для [BrokerLifecycleApi.initialize].
 *
 * Закрытая иерархия: каждая реализация [BrokerLifecycleApi]
 * принимает свой тип через `when` в `initialize(credentials)`,
 * неверный тип — `IllegalArgumentException`. Compile-time
 * проверка гарантирует, что все ветки покрыты, и что
 * потребитель передаёт credentials ровно тому брокеру, для
 * которого они предназначены.
 *
 * **Почему sealed interface, а не three-method API.**
 * Альтернатива — три метода в `BrokerLifecycleApi`
 * (`initializeTInvest`, `initializeBcs`, `initializeFinam`)
 * с default-реализациями no-op. Отклонено по 2c: интерфейс
 * знает про всех брокеров (протекание data в domain), у
 * BCS/Finam — no-op заглушки вместо честной типизации.
 * Sealed credentials убирают протекание: интерфейс не знает,
 * какие брокеры существуют, знает только про
 * `BrokerCredentials`.
 *
 * См. `docs/ROADMAP.md` → `### Broker lifecycle API` (#2 в
 * umbrella `### Рефакторинг ролей BrokerApi (nullable-val,
 * Scenario2)`).
 */
sealed interface BrokerCredentials {
    /**
     * T-Invest: Bearer-токен + режим (sandbox / prod).
     *
     * @property token токен из личного кабинета T-Invest.
     * @property sandbox `true` — sandbox, `false` — prod.
     *   Управляет `TInvestChannelFactory.create` и
     *   выбором URL (WS / REST).
     */
    data class TInvest(
        val token: String,
        val sandbox: Boolean,
    ) : BrokerCredentials

    /**
     * BCS: refresh-токен + режим доступа (read / write).
     *
     * `clientId` (`trade-api-read` / `trade-api-write`) —
     * derived внутри `BcsBrokerApi.initialize`, не снаружи:
     * `isWriteMode` — единственный источник истины.
     *
     * @property refreshToken refresh-токен (90 дней, выдаётся
     *   в личном кабинете BCS).
     * @property isWriteMode `true` — торговля (write),
     *   `false` — только чтение (read).
     */
    data class Bcs(
        val refreshToken: String,
        val isWriteMode: Boolean,
    ) : BrokerCredentials

    /**
     * Finam: Bearer-токен.
     *
     * @property token токен Finam API.
     */
    data class Finam(
        val token: String,
    ) : BrokerCredentials
}
