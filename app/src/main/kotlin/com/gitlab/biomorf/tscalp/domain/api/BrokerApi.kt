package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.domain.api.roles.AccountsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BalanceApi
import com.gitlab.biomorf.tscalp.domain.api.roles.MarketDataApi
import com.gitlab.biomorf.tscalp.domain.api.roles.PortfolioApi
import com.gitlab.biomorf.tscalp.domain.api.roles.TradeCheckApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerInstrumentApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerSnapshotApi
import com.gitlab.biomorf.tscalp.domain.api.roles.OrdersApi
import com.gitlab.biomorf.tscalp.domain.api.roles.SandboxApi
import com.gitlab.biomorf.tscalp.domain.api.roles.StreamsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.UserInstrumentsApi
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.domain.models.BrokerName

/**
 * Фасад брокера: 19 методов + 9 nullable-val ролей.
 *
 * **Fat-интерфейс осознанно.** Interface Segregation Principle
 * нарушен для одного потребителя — `InvestRepository`
 * (мультиброкерный фасад, 29 callsites, использует все методы).
 * Потребители с узкими нуждами инжектят роль напрямую
 * (`StreamsApi`, `BrokerLifecycleApi`, ...) через
 * `broker.<role>?`; `InvestRepository` — на fat `BrokerApi`.
 *
 * **Отклонение по 2c** (три обязательных элемента):
 *
 * 1. **Правило Clean, которое нарушено.** ISP: `InvestRepository`
 *    зависит от fat `BrokerApi` (19 методов). Нет ISP на callsite
 *    для `InvestRepository`.
 * 2. **Clean-альтернатива (B→A).** Супертипы
 *    (`BrokerApi : Roles`) → `BrokerRoleResolver` → удаление
 *    `BrokerApi`; все потребители, включая `InvestRepository`,
 *    мигрируют на resolver. **+** Полный ISP; `BrokerApi`
 *    удалён; единый DI. **−** +5–7 ч и +2 PR; риск переделки
 *    29 callsites; у BCS/Finam супертипы дают фиктивный
 *    контракт (`throw` / `emptyList` — «метод есть, но не
 *    работает»).
 * 3. **Выгоды Scenario2 относительно B→A.** Продолжает
 *    существующий паттерн `snapshot` (nullable-val, уже
 *    работает). 2 из 9 шагов уже были в ROADMAP
 *    (`lifecycle`, `instrument`) — закрытие существующих
 *    задач, не новая работа. Каждый PR независим. `InvestRepository`
 *    (29 callsites) не тронут. Честная опциональность:
 *    `null` = «не поддерживаю», компилятор заставляет
 *    проверить. −5–7 ч и −2 PR по сравнению с B→A. **Цена:**
 *    `BrokerApi` остаётся fat — эта фиксация.
 *
 * **Nullable-val роли (9):** [BrokerSnapshotApi] (`snapshot`),
 * [StreamsApi] (`streams`), [BrokerLifecycleApi] (`lifecycle`),
 * [BrokerInstrumentApi] (`instrument`), [OrdersApi] (`orders`),
 * [AccountsApi] (`accounts`), [SandboxApi] (`sandbox`),
 * [UserInstrumentsApi] (`userInstruments`), [BalanceApi]
 * (`balance`). Паттерн: `null` = не поддерживает; брокер —
 * `override val <role> = this` (реализует все методы) или
 * `= null` (не поддерживает / не реализует; **без** supertype).
 *
 * **Типичные потребители по ролям:**
 * - `StreamsApi` → `PositionFlowManager`, `OrderFlowManager`,
 *   `PriceFlowManager`;
 * - `BrokerSnapshotApi` → те же три менеджера (polling);
 * - `BrokerLifecycleApi` → `TScalpApplication`,
 *   `SettingsViewModel`;
 * - `BrokerInstrumentApi` → `InstrumentRepository`;
 * - `UserInstrumentsApi` → `InvestRepository`, `SearchCache`;
 * - `OrdersApi`, `AccountsApi`, `SandboxApi`, `BalanceApi` →
 *   `InvestRepository` (UI-путь через фасад).
 *
 * **Методы fat-части:** аккаунты / sandbox / позиции / стримы /
 * ордера / цены / статусы — см. ниже. `fetchPortfolioPositions`
 * возвращает `List<PositionStreamItem>` (domain), UI-конвертация
 * в `PortfolioPosition` — в `InvestRepository.fetchPositionsResult`
 * (см. `### Портфельный снимок: ...` в ROADMAP, #9).
 *
 * См. `docs/ROADMAP.md` → `### Рефакторинг ролей BrokerApi
 * (nullable-val, Scenario2)` (umbrella, #1–#9 закрыты).
 */
interface BrokerApi {
    val name: BrokerName
    val isInitialized: Boolean

    /**
     * Опциональный контракт unary-снимков (polling в менеджерах).
     *
     * `null` — брокер не поддерживает; менеджер делает `return`
     * с логированием (StreamState остаётся `INITIALIZING` →
     * `STALE`, не `POLLING`).
     */
    val snapshot: BrokerSnapshotApi?

    /**
     * Опциональный контракт стрим-методов (gRPC / WS).
     *
     * `null` — брокер не поддерживает; менеджеры делают
     * `broker.streams?.<метод>` и при `null` полагаются на polling
     * (см. [BrokerSnapshotApi]).
     */
    val streams: StreamsApi?

    /**
     * Опциональный контракт lifecycle (login / logout).
     *
     * `null` — брокер не поддерживает; потребитель (UI, старт приложения)
     * делает `broker.lifecycle?.initialize(credentials)` и при `null`
     * пропускает инициализацию (no-op + `AppLogger.e`).
     *
     * См. [BrokerLifecycleApi] и [BrokerCredentials].
     */
    val lifecycle: BrokerLifecycleApi?

    /**
     * Опциональный контракт: полный доменный инструмент по uid.
     *
     * `null` — брокер не поддерживает; `InstrumentRepository`
     * вернёт `null`.
     *
     * См. [BrokerInstrumentApi].
     */
    val instrument: BrokerInstrumentApi?

    /**
     * Опциональный контракт заявок брокера.
     *
     * `null` — брокер не поддерживает (BCS: 1/6, Finam: 0/6).
     * `InvestRepository` при `null` возвращает `AppResult.Failure`.
     *
     * См. [OrdersApi].
     */
    val orders: OrdersApi?

    /**
     * Опциональный контракт счетов брокера.
     *
     * `null` — брокер не поддерживает (Finam: 0/3).
     * `InvestRepository` при `null` возвращает `AppResult.Failure`;
     * `TScalpApplication` — fallback на `savedAccountId`.
     *
     * См. [AccountsApi].
     */
    val accounts: AccountsApi?

    /**
     * Опциональный контракт: операции со счётом песочницы
     * (T-Invest-specific).
     *
     * `null` — брокер не поддерживает (BCS/Finam — нет
     * песочницы по природе брокера).
     *
     * См. [SandboxApi].
     */
    val sandbox: SandboxApi?

    /**
     * Опциональный контракт: инструменты для пользовательского
     * пути (поиск, статусы торговли).
     *
     * **Не путать** с [instrument] — тот возвращает один полный
     * [InstrumentUi][com.gitlab.biomorf.tscalp.domain.models.InstrumentUi]
     * по uid (внутренняя инфраструктура). Здесь — bulk-поиск
     * без margin и статусы торговли.
     *
     * `null` — брокер не поддерживает (BCS/Finam: 0/2).
     *
     * См. [UserInstrumentsApi].
     */
    val userInstruments: UserInstrumentsApi?

    /**
     * Опциональный контракт: свободный остаток по счёту.
     *
     * `null` — брокер не поддерживает (BCS/Finam: 0/1).
     *
     * См. [BalanceApi].
     */
    val balance: BalanceApi?

    /** Опциональный контракт: снимок позиций портфеля. См. [PortfolioApi]. */
    val portfolio: PortfolioApi?

    /** Опциональный контракт: последние цены (unary). См. [MarketDataApi]. */
    val marketData: MarketDataApi?

    /** Опциональный контракт: проверка возможности сделки. См. [TradeCheckApi]. */
    val tradeCheck: TradeCheckApi?

}
