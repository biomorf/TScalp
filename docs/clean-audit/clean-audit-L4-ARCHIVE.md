<!-- SECTION_BEGIN: clean-audit-L4-ARCHIVE -->
## L4 — domain/api (архив)










<!-- SUBSECTION_BEGIN: clean-audit-50 -->
___
### Исправленные (L4)
<!-- SUBSECTION_END: clean-audit-50 -->









<!-- SUBSECTION_BEGIN: Н30 -->
___
### Н30 ✅ Н30. BrokerSnapshotApi KDoc — устарел после #9

**Статус:** исправлено (2026-11-11, commit `decc4ea9`).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `BrokerSnapshotApi.positionsSnapshot` KDoc: «**Не путать** с `BrokerApi.fetchPortfolioPositions` — тот возвращает полный `PortfolioPosition` для UI (`totalValue`, `profitPercent`, `priceChangePercent`)». После #9 `fetchPortfolioPositions` возвращает `PositionStreamItem` (domain, без presentation-полей), KDoc устарел.

**Нарушенное правило Clean:** актуальность документации (не строго Clean).

**Clean-альтернатива:** переформулировать: «Не путать с `fetchPortfolioPositions`: тот возвращает full snapshot (с `currentPrice`) — источник P&L в `InvestRepository.fetchPositionsResult`; здесь — partial для merge со stream в `PositionFlowManager`». Плюсы: актуально. Минусы: —.

**Выгоды:** —.

**Что сделано:** KDoc `positionsSnapshot` переформулирован: `BrokerApi.fetchPortfolioPositions` → `PortfolioApi.fetchPortfolioPositions` (фат-часть удалена в H25), `PortfolioPosition` → `AccountPortfolio` (легаси-модель удалена в H5, merge `d04ecc4`), добавлено пояснение «partial для merge со stream в `PositionFlowManager`». Commit `decc4ea9`.

**Ссылка на задачу:** commit `decc4ea9` (2026-11-11). Правилась как п.5 ревизии `### Ревизия ссылок на PortfolioPosition после H5`.

<!-- SUBSECTION_END: Н30 -->

<!-- SUBSECTION_BEGIN: Н25 -->
___
### Н25 ✅ Н25. BrokerApi fat дублирует nullable-val роли

**Статус:** исправлено (PR #61, `dev`, 2026-10-28).

**Серьёзность:** критично.

**Live тест:** -

**Описание:** 19 методов fat-части `BrokerApi` почти все дублируют nullable-val роли: `getAccounts` -> `accounts?.getAccounts`, `openSandboxAccount`/`closeSandboxAccount`/`sandboxPayIn` -> `sandbox?.<метод>`, `getBalance` -> `balance?.getBalance`, `subscribePositions`/`subscribeLastPrices`/`subscribeOrderState` -> `streams?.<метод>`, `postOrder`/`postStopOrder`/`getStopOrders`/`cancelStopOrder`/`getOrders`/`cancelOrder` -> `orders?.<метод>`, `findInstruments`/`getTradingStatuses` -> `userInstruments?.<метод>`. Не дублируют 2 метода: `fetchPortfolioPositions`, `checkTradeAvailability`. `getLastPricesByTscalpInstrumentId` — частичный дубль `pricesSnapshot` (та же семантика, разница только в fallback gRPC->REST). Два пути к одному контракту (`broker.postOrder` и `broker.orders?.postOrder`) — разное поведение при `null`.

**Нарушенное правило Clean:** Interface Segregation Principle — `BrokerApi` остаётся fat с дублированием ролей; nullable-val решение не доведено до конца.

**Clean-альтернатива:** выделить оставшиеся 3 метода в роль (`PortfolioApi` + `TradingStatusApi`), мигрировать `InvestRepository` на `broker.<role>?`, удалить fat-часть. `BrokerApi` — тонкий фасад с 9 nullable-val ролями и `name`/`isInitialized`. Плюсы: полный ISP, `@Suppress("TooManyFunctions")` снимается, один путь к каждому контракту. Минусы: `InvestRepository` — 29 callsites, ~2–3 ч.

**Выгоды (для «отклонение по 2c»):** KDoc в `BrokerApi` фиксирует fat как осознанно (мультиброкерный фасад, `InvestRepository` — 29 callsites). По факту fat-часть — legacy переходного состояния. Пересмотр — после стабилизации остальных задач clean-audit, новой задачи пока нет.

**Анализ L4-part2:** подтверждается — анализ L4-part2 (6 ролей) уточнил: 16 из 19 fat-методов полностью дублируют nullable-val роли, 1 (`getLastPricesByTscalpInstrumentId`) — частичный дубль `pricesSnapshot`, 2 (`fetchPortfolioPositions`, `checkTradeAvailability`) не дублируют.

**Анализ L6-Н45:** подтверждается — `InvestRepository` использует 4 fat-метода (не 19): `getLastPricesByTscalpInstrumentId`, `fetchPortfolioPositions`, `checkTradeAvailability`, `subscribePositions` (дубль `streams?.subscribePositions`). Остальные 12 — через nullable-val роли.

**Анализ L6-part2-Н25:** подтверждается — BCS подтверждает проблему: fat-метод `postOrder` работает, но `orders = null` делает его недоступным через роль; `subscribePositions` → `emptyFlow()` (мёртвая заглушка). Нужен расщеп `OrdersApi` на `OrdersQueryApi` / `OrdersCommandApi`.

**Что сделано:** 3 живых вынесены в роли `PortfolioApi`, `MarketDataApi`, `TradeCheckApi`. 16 мёртвых fat-методов удалены. `BrokerApi` — 12 nullable-val ролей, 0 `fun`. `@Suppress` снят. `InvestRepository` уже удалён (PR #56), `*RepositoryImpl` инжектят роли.

**Ссылка на задачу:** — (не заведена). Пересмотр — в рамках `### Рефакторинг ролей BrokerApi` (закрыта), новая задача не заведена.

---

<!-- SUBSECTION_END: Н25 -->

<!-- SUBSECTION_BEGIN: Н70 -->
___
### Н70 ✅ Н70. BrokerApi — @Suppress("TooManyFunctions")

**Статус:** исправлено (PR #61, `dev`, 2026-10-28).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `BrokerApi` (fat-интерфейс, 19 методов + 9 nullable-val ролей) несёт `@Suppress("TooManyFunctions")` — маркер осознанного долга, зафиксированного в KDoc. 16 из 19 fat-методов дублируют nullable-val роли (Н25).

**Нарушенное правило Clean:** Interface Segregation — `@Suppress` как декларация «не будем чинить».

**Clean-альтернатива:** см. Н25 — полный ISP (выделить оставшиеся 3 метода в роль, мигрировать `InvestRepository`, снять `@Suppress`). Плюсы: чистый фасад. Минусы: 29 callsites в `InvestRepository`.

**Выгоды:** сохранение `InvestRepository` без миграции; работающий код.

**Анализ L4-Н25:** этот `@Suppress` — маркер, подтверждающий долг из Н25 (fat-интерфейс осознанно не доведён до полного ISP).

**Что сделано:** `@Suppress("TooManyFunctions")` снят: в `BrokerApi` 0 `fun`, только `val` (12 ролей). detekt не ругается.

**Ссылка на задачу:** `docs/clean-audit.md` → L4-Н25; `### Рефакторинг ролей BrokerApi` (закрыта); `### Рефакторинг BrokerApi-реализаций (D)` (закрыта).

<!-- SUBSECTION_END: Н70 -->

<!-- SUBSECTION_BEGIN: Н31 -->
___
### Н31 ✅ Н31. StreamsApi.subscribeLastPrices(uids) — T-Invest-специфика

**Статус:** исправлено (PR #71, `dev`, 2026-10-30).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `StreamsApi.subscribeLastPrices(uids: List<String>)` — параметр `uids`. `BrokerSnapshotApi.pricesSnapshot(ids: List<String>)` — та же семантика, но `ids`. По AGENTS: «Domain-слой оперирует `tscalpInstrumentId`… ключ инструмента называется `tscalpInstrumentId`, а не `uid` и не `figi`». T-Invest-специфика (uid) протекает в domain-интерфейс.

**Нарушенное правило Clean:** соглашение AGENTS «domain оперирует `tscalpInstrumentId`».

**Clean-альтернатива:** переименовать в `tscalpInstrumentIds: List<String>` (или `ids`). Плюсы: консистентность, T-Invest-специфика не протекает. Минусы: правка `TInvestBrokerAPI` (2 метода — `subscribeLastPrices` в fat + role) + `PriceFlowManager` (1 callsite).

**Выгоды:** —.

**Что сделано:** scope расширен (2026-10-30, по решению пользователя):

- domain (4 роли): `ids` → `tscalpInstrumentIds` — `BrokerSnapshotApi.pricesSnapshot`, `MarketDataApi.getLastPricesByTscalpInstrumentId`, `UserInstrumentsApi.getTradingStatuses`, `StreamsApi.subscribeLastPrices`;
- data T-Invest (5 сигнатур/override): `ids` → `uids` — `TInvestBrokerApi`, `TInvestMarketDataService` (data честно отражает формат брокера);
- KDoc на 4 domain-роли — граница слоёв (T-Invest `uids`, BCS `figi`, Finam `ticker`; domain `tscalpInstrumentIds`);
- `TInvestBrokerApi`: `PARAMETER_NAME_CHANGED_ON_OVERRIDE` в class-level `@Suppress` (осознанно, all callsites позиционные; объяснение в KDoc — ktlint: EOL comment после KDoc запрещён).

**Ссылка на задачу:** PR #71 (`feature/instrument-ids`), коммиты `6f74454`, `86ab1c4`.

<!-- SUBSECTION_END: Н31 -->

<!-- SUBSECTION_BEGIN: Н32 -->
___
### Н32 ✅ Н32. StreamCombinators — транспорт в domain/api

**Статус:** исправлено (PR #76, `dev`, 2026-10-30).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `StreamCombinators.streamWithFallback` (merge источников — gRPC + WS параллельно) + `retryWithBackoff` (переподключение с backoff и rate-limit при обрыве) — транспортные утилиты. Используются в `data/orchestrator/TInvestOrchestrator.kt` для merge gRPC + WS на транспортном уровне и переподключения при обрыве с учётом rate-limits. Живут в `domain/api/` — неверное место. Менеджеры (`domain/api/managers/*`) **не используют** `streamWithFallback` — осознанное архитектурное решение, **не долг**: `StreamStateTracker` должен различать источник (stream vs polling) для корректного `LIVE`/`POLLING`/`STALE`; `streamWithFallback` этого знания не даёт.

**Нарушенное правило Clean:** dependency rule — transport-специфика (retry, backoff, rate-limit) в `domain/api/`.

**Clean-альтернатива:** `data/orchestrator/StreamCombinators.kt` (рядом с `TInvestOrchestrator`) или `data/transport/StreamCombinators.kt`. Плюсы: `domain/api/` содержит только роли и `BrokerApi`; domain не знает про transport-специфику. Минусы: переезд 1 файла + правка импортов в `TInvestOrchestrator` (единственный потребитель — grep 2026-10-27).

**Выгоды:** —.

**Что сделано:** `git mv domain/api/StreamCombinators.kt → data/orchestrator/StreamCombinators.kt`; `package` → `data.orchestrator`; `TInvestOrchestrator` — импорты обновлены (единственный потребитель); порядок импортов — `ktlintFormat`. Поведение не изменено.

**Ссылка на задачу:** PR #76 (`dev`, 2026-10-30), коммит `a636699`.

<!-- SUBSECTION_END: Н32 -->

<!-- SUBSECTION_BEGIN: Н32a -->
___
### Н32a ✅ Н32a. streamWithFallback — семантически неверное имя

**Статус:** исправлено (PR #76, `dev`, 2026-10-30).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `streamWithFallback` делает `merge` (параллельная эмиссия из всех источников), а не fallback (переключение при отказе). В T-Invest-контексте WS — резервный транспорт для gRPC, но merge **параллельный**, осознанно (оба идут одновременно, WS не включается по отказу). Имя вводит в заблуждение.

**Нарушенное правило Clean:** не строго Clean — семантическая честность.

**Clean-альтернатива:** переименовать в `mergeStreamSources` / `combineStreams` (с KDoc «WS — резервный транспорт, merge параллельный»). Плюсы: честное имя. Минусы: правка ~3 callsites (`TInvestOrchestrator`, единственный потребитель).

**Выгоды:** «fallback» отражает бизнес-логику «WS — резерв». Компромисс.

**Что сделано:** `streamWithFallback` → `mergeStreamSources`; KDoc — «merge, не fallback»; `TInvestOrchestrator` — импорт + 3 callsites; поведение не изменено.

**Ссылка на задачу:** PR #76 (`dev`, 2026-10-30), коммит `986c330`. Объединено с Н32 в одном PR, но отдельным коммитом.

<!-- SUBSECTION_END: Н32a -->

<!-- SUBSECTION_BEGIN: Н26 -->
___
### Н26 ✅ Н26. BrokerLifecycleApi — не 2c-фиксация

**Статус:** исправлено (PR #77, `dev`, 2026-10-31).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `BrokerLifecycleApi.initialize(credentials: BrokerCredentials): Boolean` — runtime `IllegalArgumentException` при неверном типе credentials. KDoc: «Сообщение об ошибке — через `AppLogger` и `AppResult.Failure`» — domain-интерфейс ссылается на `util.AppLogger` (domain -> util).

**Нарушенное правило Clean:** runtime-проверка типов вместо compile-time; KDoc связывает domain с `util`.

**Clean-альтернатива:** вариант B — 3 отдельных интерфейса в `domain/api/roles/`: `TInvestLifecycleApi`, `BcsLifecycleApi`, `FinamLifecycleApi` (каждый — `initialize(credentials: BrokerCredentials.<X>): Boolean` + `deinitialize()`). `BrokerApi` — 3 nullable-val (`lifecycleTInvest`, `lifecycleBcs`, `lifecycleFinam`) вместо одного `lifecycle`. Реализации — `= this` + supertype, без `when`, без `else -> throw`. `clearBrokerCredentials` (SettingsViewModel) — явный `when (brokerName)` на 3 ветки (вариант A; `BrokerRegistry` уже в `domain.api`). Плюсы: compile-time (generic не работает — JVM erasure: `as? BrokerLifecycleApi<TInvestCredentials>` проверяет только `BrokerLifecycleApi`, не `C`), runtime-throw исчезает. Минусы: +3 интерфейса по 1 методу, `when` в `clearBrokerCredentials`.

**Выгоды:** —.

**Что сделано:** 3 интерфейса в `domain/api/roles/` (`TInvestLifecycleApi`, `BcsLifecycleApi`, `FinamLifecycleApi`); `BrokerApi` — `lifecycle` → 3 nullable-val (`lifecycleTInvest/Bcs/Finam`); реализации — supertype + `= this` / `null`, `initialize(credentials: BrokerCredentials.<X>)` без `when`; `BrokerLifecycleApi.kt` удалён; 6 callsites (`TScalpApplication` 2, `SettingsViewModel` 4), `clearBrokerCredentials` — `when (brokerName)` на 3 ветки.

**Ссылка на задачу:** PR #77 (`dev`, 2026-10-31), коммит `63697ae`.

<!-- SUBSECTION_END: Н26 -->

<!-- SUBSECTION_BEGIN: Н60 -->
___
### Н60 ✅ Н60. retryWithBackoff — non-retryable не классифицирует

**Статус:** исправлено (PR #79, `dev`, 2026-10-31).

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `StreamCombinators.retryWithBackoff` переподключает любой источник при любой ошибке с backoff. `UNAUTHENTICATED`, `NOT_FOUND`, `PERMISSION_DENIED`, `INVALID_ARGUMENT` — вечный retry-loop (до остановки вручную). `CancellationException` пробрасывается, но не-recoverable gRPC-коды не различаются.

**Нарушенное правило Clean:** не строго Clean — resilience-логика не различает recoverable/non-recoverable.

**Clean-альтернатива:** классификация по `Status.fromThrowable(e).code`: retry — `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `INTERNAL`, `UNKNOWN`; non-retry — `NOT_FOUND`, `UNAUTHENTICATED`, `PERMISSION_DENIED`, `INVALID_ARGUMENT`, `FAILED_PRECONDITION`. Плюсы: батарея, трафик, rate-limit не тратятся на безнадёжный retry. Минусы: —.

**Выгоды:** —.

**Что сделано:** `private isRetryable(e)` — non-retry (`NOT_FOUND`, `UNAUTHENTICATED`, `PERMISSION_DENIED`, `INVALID_ARGUMENT`, `FAILED_PRECONDITION`) → `throw`; retry (`UNAVAILABLE`, `DEADLINE_EXCEEDED`, `INTERNAL`, `UNKNOWN`) → backoff; не-gRPC → retry. `private retryDelay(e, attempt, delays)` — `RESOURCE_EXHAUSTED` → `delays.max()` (60 с), иначе стандартный шаг. Коммит `fe58413`, тесты `181de0f` (5 PASSED).

**Ссылка на задачу:** PR #79 (`dev`, 2026-10-31). `docs/ISSUES.md` → «gRPC retry: бесконечный retry на не-recoverable ошибках».

<!-- SUBSECTION_END: Н60 -->

<!-- SUBSECTION_BEGIN: Н61 -->
___
### Н61 ✅ Н61. retryWithBackoff — busy loop на normal completion

**Статус:** исправлено (PR #79, `dev`, 2026-10-31).

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `StreamCombinators.retryWithBackoff`: при normal completion (`source().collect { emit(it) }` вернулся без exception) — `attempt = 0` и новый цикл **без delay**. Если source завершается мгновенно-пустым (сервер закрыл стрим, gRPC завершился нормально) — busy loop, CPU 100%, батарея в ноль.

**Нарушенное правило Clean:** не строго Clean — resilience-логика не защищена от мгновенного нормального завершения.

**Clean-альтернатива:** при normal completion — `delay(1_000)` перед новой попыткой (как в `PriceFlowManager.collectStream`). Плюсы: защита от busy loop. Минусы: —.

**Выгоды:** —.

**Что сделано:** `private const NORMAL_COMPLETION_DELAY_MS = 1_000L`; `delay(NORMAL_COMPLETION_DELAY_MS)` после `source().collect { }` без исключения (перед новой итерацией). Коммит `6bf349d`, тесты `181de0f` (virtual time ≥ 2000 мс).

**Ссылка на задачу:** PR #79 (`dev`, 2026-10-31). `docs/ISSUES.md` → «PriceFlowManager.collectStream: потенциальный busy loop».

<!-- SUBSECTION_END: Н61 -->

<!-- SECTION_END: clean-audit-L4-ARCHIVE -->
