package com.gitlab.biomorf.tscalp.domain.api.roles

/**
 * Типизированные credentials для lifecycle-интерфейсов
 * ([TInvestLifecycleApi], [BcsLifecycleApi], [FinamLifecycleApi]).
 *
 * Каждый интерфейс принимает только свой тип
 * (`initialize(credentials: BrokerCredentials.<X>)`) — compile-time
 * проверка на callsite, без runtime `when` / `IllegalArgumentException`.
 * Generic-параметризация интерфейса роли не даёт compile-time
 * гарантии: JVM erasure стирает type-параметр, проверка `as?`
 * видит только сам интерфейс.
 *
 * См. `docs/ROADMAP.md` → `### Пакет 3: Н26 + Н50` (clean-audit Н26).
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
