package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.domain.api.roles.AccountsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BalanceApi
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
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import com.gitlab.biomorf.tscalp.domain.models.OrderState
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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
@Suppress("TooManyFunctions")
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

    suspend fun getAccounts(sandboxMode: Boolean): List<BrokerAccount>

    suspend fun openSandboxAccount(): String // возвращает accountId нового счёта

    suspend fun closeSandboxAccount(accountId: String)

    /**
     * Возвращает одноразовый снимок позиций портфеля
     * (в отличие от [subscribePositions], который отдаёт поток).
     *
     * Транспорт зависит от реализации брокера: у T-Invest —
     * gRPC unary ([getPortfolio][ru.tinkoff.piapi.contract.v1.PortfolioResponse]),
     * у BCS / Finam — REST (парсинг JSON).
     *
     * Возвращает [PositionStreamItem] (domain-модель, без
     * presentation-полей `totalValue` / `profitPercent`).
     * UI-конвертация в `PortfolioPosition` — в
     * `InvestRepository.fetchPositionsResult` через
     * `toPortfolioPosition()`.
     *
     * **Не путать** с [BrokerSnapshotApi.positionsSnapshot]:
     * тот partial `PositionStreamItem` для merge со stream в
     * `PositionFlowManager` (без `currentPrice`). Здесь —
     * full snapshot с `currentPrice` (источник P&L в
     * domain-конвертации).
     */
    suspend fun fetchPortfolioPositions(
        accountId: String,
        sandboxMode: Boolean,
    ): List<PositionStreamItem>

    // Единый стрим позиций с автоматическим восстановлением (gRPC → WebSocket → Polling)
    // Если брокер не поддерживает стриминг, реализация может сразу делегировать в polling

    /**
     * Единый поток обновлений позиций.
     * Скрывает внутри себя каскад fallback (gRPC → WebSocket → Polling),
     * а также при подписке сразу эмитит текущий снапшот портфеля.
     */
    fun subscribePositions(accountId: String): Flow<PositionStreamItem>

    /**
     * Стрим последних цен по списку идентификаторов инструментов
     * в формате брокера (для T-Invest — это uid).
     * Реализация по умолчанию возвращает пустой поток — брокеры, не
     * поддерживающие стрим (BCS, Finam), используют её без изменений.
     * TInvest переопределяет.
     */
    fun subscribeLastPrices(uids: List<String>): Flow<Pair<String, Double>> = emptyFlow()

    suspend fun getBalance(accountId: String): Double

    suspend fun sandboxPayIn(
        accountId: String,
        amount: SandboxMoney,
    )

    /**
     * Возвращает специфичный для брокера идентификатор инструмента по тикеру.
     * Для Т-Инвестиций это figi, для БКС — bscticker (пока просто ticker).
     * Может вернуть null, если инструмент не найден.
     */
    suspend fun findInstruments(query: String): List<InstrumentUi>

    /**
     * Выставляет заявку (рыночную или лимитную) через брокера.
     * @param orderType тип заявки (ORDER_TYPE_MARKET или ORDER_TYPE_LIMIT)
     * @param price цена (для рыночной игнорируется, можно передать Quotation.getDefaultInstance())
     */
    suspend fun postOrder(request: BrokerOrderRequest): OrderResult

    /**
     * Выставляет стоп-заявку (take-profit, stop-loss, stop-limit).
     * @return StopOrderResponse с информацией о созданной заявке.
     */
    suspend fun postStopOrder(request: StopOrderRequest): String // возвращает stopOrderId

    /**
     * Возвращает список активных стоп-заявок для указанного счёта.
     */
    suspend fun getStopOrders(accountId: String): List<OrderListItem>

    /**
     * Отменяет стоп-заявку по её идентификатору.
     */
    suspend fun cancelStopOrder(
        accountId: String,
        stopOrderId: String,
    )

    suspend fun getOrders(accountId: String): List<OrderListItem>

    suspend fun cancelOrder(
        accountId: String,
        orderId: String,
    )

    /**
     * Получает последние цены для списка тикеров.
     * Возвращает карту ticker -> цена (или null, если цена недоступна).
     */
    suspend fun getLastPricesByTscalpInstrumentId(ids: List<String>): Map<String, Double?>

    /**
     * Возвращает статусы доступности для торговли для списка идентификаторов инструментов.
     * Ключ — идентификатор инструмента (tscalpInstrumentId), значение — статус доступности.
     */
    suspend fun getTradingStatuses(ids: List<String>): Map<String, TradingAvailability>

    suspend fun subscribeOrderState(accountId: String): Flow<OrderState>

    /**
     * @param tscalpInstrumentId универсальный идентификатор инструмента.
     * @param brokerUid идентификатор инструмента в формате брокера
     *        (для T-Invest равен tscalpInstrumentId; для других брокеров
     *        может отличаться, например figi у BCS).
     */
    suspend fun checkTradeAvailability(
        accountId: String,
        tscalpInstrumentId: String,
        brokerUid: String?,
        direction: OrderDirection,
        quantity: Long,
    ): TradeCheckResult
}
