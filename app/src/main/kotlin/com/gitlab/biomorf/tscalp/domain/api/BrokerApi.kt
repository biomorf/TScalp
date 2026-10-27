package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.domain.api.roles.AccountsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BalanceApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerInstrumentApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerSnapshotApi
import com.gitlab.biomorf.tscalp.domain.api.roles.MarketDataApi
import com.gitlab.biomorf.tscalp.domain.api.roles.OrdersApi
import com.gitlab.biomorf.tscalp.domain.api.roles.PortfolioApi
import com.gitlab.biomorf.tscalp.domain.api.roles.SandboxApi
import com.gitlab.biomorf.tscalp.domain.api.roles.StreamsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.TradeCheckApi
import com.gitlab.biomorf.tscalp.domain.api.roles.UserInstrumentsApi
import com.gitlab.biomorf.tscalp.domain.models.BrokerName

/**
 * Фасад брокера: 12 nullable-val ролей + `name` / `isInitialized`.
 *
 * **Nullable-val паттерн.** Каждая роль — опциональный контракт:
 * `null` = не поддерживает, брокер реализует все методы роли (supertype
 * + `= this`) или не реализует (`= null`, без supertype). Компилятор
 * заставляет проверить `?.` / `?:` на callsite.
 *
 * **Fat-часть удалена** (ревизия 2026-10-28). 16 fat-методов дублировали
 * роли и не имели потребителей (grep: 0 callsites). 3 живых вынесены
 * в роли: [PortfolioApi] (`fetchPortfolioPositions`), [MarketDataApi]
 * (`getLastPricesByTscalpInstrumentId`), [TradeCheckApi]
 * (`checkTradeAvailability`). `@Suppress("TooManyFunctions")` снят —
 * в интерфейсе 0 `fun`, только `val`.
 *
 * **Роли и потребители:**
 * - [BrokerSnapshotApi] (`snapshot`) → менеджеры (polling);
 * - [StreamsApi] (`streams`) → менеджеры (gRPC / WS);
 * - [BrokerLifecycleApi] (`lifecycle`) → `TScalpApplication`, `SettingsViewModel`;
 * - [BrokerInstrumentApi] (`instrument`) → `InstrumentRepository`;
 * - [OrdersApi] (`orders`) → `OrdersRepository`;
 * - [AccountsApi] (`accounts`) → `AccountsRepository`, `TScalpApplication`;
 * - [SandboxApi] (`sandbox`) → `AccountsRepository`, `TScalpApplication`;
 * - [UserInstrumentsApi] (`userInstruments`) → `SearchCache`,
 *   `MarketDataRepository`;
 * - [BalanceApi] (`balance`) → `PortfolioRepository`;
 * - [PortfolioApi] (`portfolio`) → `PortfolioRepository`;
 * - [MarketDataApi] (`marketData`) → `MarketDataRepository`;
 * - [TradeCheckApi] (`tradeCheck`) → `OrdersRepository`.
 *
 * См. `docs/ROADMAP.md` → `### BrokerApi: ревизия fat-части после
 * InvestRepository split`, `### Рефакторинг ролей BrokerApi
 * (nullable-val, Scenario2)` (umbrella, #1–#9 закрыты).
 */
interface BrokerApi {
    val name: BrokerName
    val isInitialized: Boolean

    /** Опциональный контракт unary-снимков (polling в менеджерах). */
    val snapshot: BrokerSnapshotApi?

    /** Опциональный контракт стрим-методов (gRPC / WS). */
    val streams: StreamsApi?

    /** Опциональный контракт lifecycle (login / logout). */
    val lifecycle: BrokerLifecycleApi?

    /** Опциональный контракт: полный доменный инструмент по uid. */
    val instrument: BrokerInstrumentApi?

    /** Опциональный контракт заявок брокера. */
    val orders: OrdersApi?

    /** Опциональный контракт счетов брокера. */
    val accounts: AccountsApi?

    /** Опциональный контракт: операции со счётом песочницы. */
    val sandbox: SandboxApi?

    /**
     * Опциональный контракт: инструменты для пользовательского пути
     * (поиск, статусы торговли).
     *
     * **Не путать** с [instrument] — тот возвращает один полный
     * `InstrumentUi` по uid (внутренняя инфраструктура).
     */
    val userInstruments: UserInstrumentsApi?

    /** Опциональный контракт: свободный остаток по счёту. */
    val balance: BalanceApi?

    /** Опциональный контракт: снимок позиций портфеля (full snapshot). */
    val portfolio: PortfolioApi?

    /** Опциональный контракт: последние цены (unary). */
    val marketData: MarketDataApi?

    /** Опциональный контракт: проверка возможности сделки. */
    val tradeCheck: TradeCheckApi?
}
