package com.gitlab.biomorf.tscalp.domain.api.roles

import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount

/**
 * Опциональный контракт: список счетов брокера.
 *
 * Реализуется брокером, если он умеет отдавать счета. Доступ
 * через `BrokerApi.accounts` (`null` = не поддерживает).
 *
 * **Nullable-val и supertype.** По правилу AGENTS.md
 * «Nullable-val роли»: supertype = «реализую все методы».
 * По grep 2026-10-25:
 * - TInvest — реальный (`marketData.getAccounts`), supertype +
 *   `accounts = this`;
 * - BCS — реальный (HTTP GET к портфелю), supertype +
 *   `accounts = this`;
 * - Finam — заглушка (`emptyList()`), `accounts = null`, без
 *   supertype.
 *
 * См. `docs/ROADMAP.md` → `### AccountsApi` (шаг #5) и
 * `### Рефакторинг ролей BrokerApi (nullable-val, Scenario2)`.
 */
interface AccountsApi {
    /**
     * Список счетов брокера.
     *
     * @param sandboxMode `true` — sandbox-счета (T-Invest
     *   `sandboxServiceSync.getSandboxAccounts`), `false` —
     *   боевые (T-Invest `usersServiceSync.getAccounts`).
     *   У брокеров без sandbox (BCS/Finam) параметр
     *   игнорируется.
     */
    suspend fun getAccounts(sandboxMode: Boolean): List<BrokerAccount>
}
