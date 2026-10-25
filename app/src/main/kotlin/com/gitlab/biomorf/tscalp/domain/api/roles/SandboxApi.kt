package com.gitlab.biomorf.tscalp.domain.api.roles

import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney

/**
 * Опциональный контракт: операции со счётом песочницы.
 *
 * **T-Invest-специфичная роль.** У BCS и Finam песочницы нет
 * и не будет — это свойство брокера, а не «пока не
 * реализовали». Поэтому сигнатура без параметра `sandboxMode`:
 * у TInvest все 3 метода работают всегда в sandbox, у
 * BCS/Finam — не применимо. Доступ через `BrokerApi.sandbox`
 * (`null` = не поддерживает; у BCS/Finam — постоянно).
 *
 * **Nullable-val и supertype.** По правилу AGENTS.md
 * «Nullable-val роли»: supertype = «реализую все методы».
 * По grep 2026-10-25:
 * - TInvest — 3/3 реальные (через `marketData.*`), supertype
 *   + `sandbox = this`;
 * - BCS — `UnsupportedOperationException` / no-op,
 *   `sandbox = null`, без supertype;
 * - Finam — `TODO("Not yet implemented")`, `sandbox = null`,
 *   без supertype.
 *
 * См. `docs/ROADMAP.md` → `### SandboxApi` (шаг #6) и
 * `### Рефакторинг ролей BrokerApi (nullable-val, Scenario2)`.
 */
interface SandboxApi {
    /**
     * Открывает новый счёт в песочнице.
     *
     * @return `accountId` нового счёта.
     */
    suspend fun openSandboxAccount(): String

    /**
     * Закрывает счёт в песочнице.
     */
    suspend fun closeSandboxAccount(accountId: String)

    /**
     * Пополняет счёт в песочнице.
     *
     * @param amount сумма (`SandboxMoney`: валюта, units,
     *   nano).
     */
    suspend fun sandboxPayIn(
        accountId: String,
        amount: SandboxMoney,
    )
}
