package com.gitlab.biomorf.tscalp.domain.api.roles

/**
 * Опциональный контракт: свободный остаток по счёту.
 *
 * Реализуется брокером, если он умеет отдавать баланс.
 * Доступ через `BrokerApi.balance` (`null` = не поддерживает).
 *
 * **Nullable-val и supertype.** По правилу AGENTS.md
 * «Nullable-val роли»: supertype = «реализую все методы».
 * По grep 2026-10-25:
 * - TInvest — реальный (`orchestrator.balanceSnapshot`, gRPC
 *   unary + REST fallback), supertype + `balance = this`;
 * - BCS — заглушка (`return 0.0`), `balance = null`, без
 *   supertype;
 * - Finam — заглушка (`return 0.0`), `balance = null`, без
 *   supertype.
 *
 * См. `docs/ROADMAP.md` → `### BalanceApi` (шаг #8) и
 * `### Рефакторинг ролей BrokerApi (nullable-val, Scenario2)`.
 */
interface BalanceApi {
    /**
     * Свободный остаток по счёту.
     *
     * У T-Invest: prod — `getMarginAttributes`
     * (`liquidPortfolio`), sandbox — расчёт из портфеля
     * (totalAmountCurrencies минус стоимость позиций).
     *
     * Единица — валюта счёта (обычно RUB).
     */
    suspend fun getBalance(accountId: String): Double
}
