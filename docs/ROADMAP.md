# ROADMAP

План задач проекта TScalp (разработка): что в работе, что
запланировано, что отложено. Формат — секция на задачу,
со статусом и оценкой.

CI/CD-планирование → `docs/ci-roadmap.md`.




## Шаблон задачи

Все поля обязательные. Задачи без заполненных полей не
принимаются. Структура — по AGENTS 5e.

~~~markdown
### <Название>

**Мобильная работа:** полное закрытие | частичное | невозможно.

**Статус:** запланировано.

**Контекст:** зачем задача, какую проблему решает. Для задач
из `clean-audit` — с ID: «(clean-audit Н21) …».

**Соответствие Clean:** соответствует | отклонение по 2c
(принято) | отклонение (открыто).

**Нарушенное правило Clean:** одна строка. Для
«соответствует» — «—».

**Clean-альтернатива:** как правильно, с плюсами и минусами.
Для «соответствует» — «—».

**Выгоды (для «отклонение по 2c»):** что даёт текущий
вариант — конкретно. Для остальных статусов — «—».

**Предусловия:** что должно быть сделано до начала.

**Файлы:** конкретные пути и классы. Если файл ещё не
создан — где создать.

**Шаги:** пронумерованная последовательность. Один шаг —
одно логически завершённое изменение.

**Тесты:** какие меняются / добавляются; что не покрывается
и почему.

**Референсы:** ссылки на SDK, документацию, образцы в
репозитории.

**Ограничения:** что нельзя делать, известные грабли.

**Критерий готовности:** наблюдаемое поведение, тесты,
гейт.

**Оценка:** часы/минуты.

**Зависимости:** задачи-блокеры.

**Связано:** related-задачи в ROADMAP / ISSUES /
clean-audit.

**Открытые вопросы:** факты, которых не хватает; где искать
ответ.
~~~




---




## Планируемые задачи



### Архитектурный документ (docs/architecture.md)

**Мобильная работа:** полное закрытие (правки .md — через proot;
mermaid-рендер — визуальная проверка, не блокирует).

**Статус:** запланировано.

**Контекст:** архитектурные решения разбросаны по AGENTS.md,
ROADMAP (закрытые задачи), docs/streams.md, docs/live-test.md,
KDoc. Нет единой точки входа. Нужен `docs/architecture.md` —
справочник с mermaid-диаграммами.

**Цель:** новый читатель за одно чтение понимает: слои, где
живёт fallback, как managers агрегируют данные, какие
идентификаторы, какие долги известны.

**Известные архитектурные особенности (собраны 2026-10-23):**

1. **Слои и границы.**
    - presentation → domain → data. ViewModel не ходит к брокеру —
      только через `InvestRepository` или `*FlowManager`.
    - Orchestrator (`TInvestOrchestrator`) — транспорты.
    - Managers (`PriceFlowManager`, `PositionFlowManager`,
      `OrderFlowManager`) — merge stream + polling + домен.
    - Repository (`InvestRepository`) — навигация по брокерам.

2. **Транспорты и fallback.**
    - Stream-уровень (orchestrator): gRPC stream ↔ WS stream через
      `streamWithFallback(primary, fallback, polling = null)`.
      Polling — `null` с StreamState (перенесён в managers).
    - Unary-уровень (orchestrator): gRPC unary → REST unary на
      транспортные ошибки (`UNAVAILABLE` / `DEADLINE_EXCEEDED` /
      `UNIMPLEMENTED`); бизнес-ошибки не fallback.
    - Merge-уровень (managers): stream ↔ polling. Обогащение
      (P&L, money/blocked, TTL-эвикция) — в managers.
    - Fallback-логика принадлежит оркестратору (transport-level);
      managers видят только `Flow` + `suspend fun`.

3. **Managers lifecycle.**
    - `start` / `stop` / `reapplyDebugFlags` — единая точка.
    - `synchronized(startLock)` — защита check-then-set
      (race-фикс PR #34; регрессия покрыта тестами).
    - `job: Job?` + `coroutineScope { launch × 3 }` (stream,
      polling, `stateWatchdog`).
    - `Dispatchers.IO.limitedParallelism(1)` — сериализация
      мутабельного состояния; `synchronized` не нужен.
    - `stateTracker: StreamStateTracker` — `LIVE` / `POLLING` /
      `STALE` / `STOPPED`. Watchdog tick 1s.
    - `reapplyDebugFlags` — no-op, если менеджер не запущен.

4. **Идентификаторы.**
    - `tscalpInstrumentId` (domain) ↔ `uid` (T-Invest) / `figi`
      (BCS) / `ticker+exchange` (Finam).
    - Трансляция — в data-слое (репозиторий / брокер).
    - `InstrumentUi.ttech_uid` — специфика T-Invest (исключение).

5. **Потоки данных (domain).**
    - `PriceFlowManager._prices: SharedFlow(replay = 0)` — one-shot
      updates; подписчик подтягивает через `pricesSnapshot`.
    - `PositionFlowManager._flow: SharedFlow(replay = 1)` —
      stateful snapshot; сброс при смене счёта.
    - `OrderFlowManager._orders: StateFlow` + `_hasLoaded`.

6. **Domain-модели.**
    - `PositionStreamItem` — full / partial; маркер
      `currentPrice != null`.
    - `PortfolioPosition` — always full, для UI.
    - `OrderState` — transport event; `OrderListItem` — sum-type
      через `isStopOrder` (антипаттерн, план: `sealed interface
     Order { Regular, Stop }`).
    - `InstrumentUi` — open class, `FutureUi` / `ShareUi`,
      `pointValue` только у futures.

7. **BrokerApi.**
    - 19 функций + `snapshot: BrokerSnapshotApi?`,
      `@Suppress("TooManyFunctions")`.
    - `fetchPortfolioPositions` (was `fetchPositionsSnapshot`) —
      unary с fallback на уровне брокера; полный `PortfolioPosition`
      (UI + база для оркестратора, конвертируется в
      `PositionStreamItem` в `positionsSnapshot`).
    - `pricesSnapshot` / `ordersSnapshot` / `positionsSnapshot` —
      `BrokerSnapshotApi` (композиция через `BrokerApi.snapshot`).
      `TInvestBrokerAPI`: `snapshot = this`. `BcsBrokerApi` /
      `FinamBrokerApi`: `snapshot = null` → в менеджере `return`
        + `AppLogger.e` (STALE, не POLLING).
    - Ролевые интерфейсы (`PortfolioApi`, `OrdersApi`,
      `MarketDataApi`, `StreamsApi`, `SnapshotApi`) — отдельная
      задача `### Рефакторинг BrokerApi-реализаций` (D).

8. **Обработка ошибок.**
    - `AppResult<T>` (Success/Failure) на границах слоёв.
    - `BrokerErrorMessages` — расшифровка кодов брокеров.
    - `GrpcExceptionGuard` — глобальный
      `UncaughtExceptionHandler` для `io.grpc.StatusException`
      (late callback); non-fatal.

9. **Debug-инфраструктура.**
    - `DebugConfig` gated `BuildConfig.DEBUG` (release — `false`).
    - `AppLogger` — `Log.d` только в debug.
    - WS `logFrames` — `BuildConfig.DEBUG`.
    - Матрица 8 комбинаций флагов — `### Debug-тумблеры`.

**Ссылки на существующие docs (не дублировать):**
`docs/streams.md`, `docs/live-test.md`, `docs/brokers.md`,
`docs/rate-limits.md`, `docs/git-flow.md`, `AGENTS.md`.

**Файлы:**
- `docs/architecture.md` (новый)
- `AGENTS.md` → «Документы проекта» — добавить ссылку
- `README.md` — ссылка (опционально)

**Шаги:**

1. Создать `docs/architecture.md`: шапка, обзор, ссылки.
2. Разделы 1–3 с mermaid: `flowchart` (слои), `sequenceDiagram`
   (fallback), `stateDiagram-v2` (StreamState).
3. Разделы 4–6: `flowchart` (идентификаторы), таблица (replay
   semantics).
4. Разделы 7–9: таблицы + ссылки.
5. Раздел «Ссылки на docs».
6. Обновить `AGENTS.md` → «Документы проекта».
7. Проверка mermaid — визуальная (десктоп / GitLab UI).

**Референсы:**
- `docs/streams.md` — T-Invest стримы, WS.
- `docs/live-test.md` — процедура, сценарии.
- `docs/git-flow.md` — образец mermaid.
- ROADMAP → `### Ktor REST для позиций/заявок/баланса` —
  образец `flowchart` слоёв.

**Ограничения:**
- Не дублировать `docs/streams.md`, `docs/live-test.md`,
  `docs/brokers.md`, `docs/rate-limits.md` — только ссылки.
- Не переносить рабочие правила из AGENTS.md.
- Mermaid: не дублировать текст дословно.
- Не менять код в этой задаче.
- Язык документа — русский (как весь проект).

**Критерий готовности:**
- `docs/architecture.md` создан, все 9 разделов + ссылки.
- Mermaid-диаграммы: 3+ (слои, fallback, StreamState).
- Cross-references на 5+ существующих docs.
- `AGENTS.md` → «Документы проекта» содержит ссылку.
- Нет дублирования с `docs/streams.md` / `docs/live-test.md`.

**Оценка:** ~2–3 часа (разделы 1–3 — 1 ч, 4–6 — 40 мин,
7–9 — 40 мин, ссылки + AGENTS — 20 мин).

**Зависимости:** `### TInvest-specific` — решение принято
(C-вариант, `BrokerApi.snapshot: BrokerSnapshotApi?`), раздел 7
обновлён (2026-10-23). Задача больше не блокирована.

**Открытые вопросы:**
- Раздел 7 (BrokerApi) — **решено 2026-10-23**: C-вариант
  (`BrokerApi.snapshot: BrokerSnapshotApi?`), см. обновлённый
  раздел 7 и `### TInvest-specific`.
- `InstrumentUi.ttech_uid` — в раздел 4 (идентификаторы) или
  отдельный пункт в 6 (domain-модели)? По смыслу — в 4
  (исключение), ссылка из 6.
- Clean архитектура (umbrella-задача) — создаётся после
  `docs/architecture.md`, из раздела «Известные долги».
  Не входит в эту задачу.




___




### Compose UI: test infra + 3 экрана (3 проходки)

**Мобильная работа:** полное закрытие (proot + PR, гейт на CI). Проходка 2 (state hoisting) верифицируется behavioral Compose-тестами на Robolectric; визуальная проверка на desktop — опционально, не блокер.

**Статус:** в работе (U0 done; U1 done (PR #116, #117, #118); U2–U3 planned).

**Контекст:** `### Рефакторинг архитектурного долга (detekt-группа 3)` → `#### Compose-экраны` (OrdersScreen, PortfolioScreen, SettingsScreen) требует декомпозиции god-composables под `@Suppress("LongMethod", "CyclomaticComplexMethod")`. Риск: перенос `remember` / `LaunchedEffect` / `derivedStateOf` / `Modifier`-порядка без behavioral-тестов = «только глазами» (live-тест с телефона не отработан, см. `docs/live-test.md`). Umbrella вводит **Compose UI test infra (Robolectric, JVM, без эмулятора)** и разбивает каждый экран на **3 проходки**: extract (mobile-safe) → state hoisting + behavioral tests → cleanup.

**Соответствие Clean:** соответствует (presentation-only; тесты — presentation-test; infra — test-layer).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** нет.

**Файлы (сводно):**
- `app/build.gradle.kts` — testImplementation для U0.
- `app/src/test/kotlin/.../presentation/` — Compose UI тесты (Robolectric).
- `presentation/screens/orders/OrdersScreen.kt` (+ вынесенные `OrdersHeader.kt` / `Orders*Section.kt`).
- `presentation/screens/portfolio/PortfolioScreen.kt`.
- `presentation/screens/settings/SettingsScreen.kt`.
- `docs/gradle-setup.md` (секция «Compose UI tests»).

**Шаги:**

#### ✅ U0 — Compose UI test infra (Robolectric-hosted, PR #115, merge 16bc3c99)

**Дата закрытия:** 2026-11-09

**Подтверждение:** выполнено (PR #115, merge `16bc3c99`; `OrderCardTest` 2 smoke-кейса в `fast-check` (Robolectric-hosted, JVM, без эмулятора); CI green).

1. `app/build.gradle.kts`:
   - `testImplementation`: `androidx.compose.ui:ui-test-junit4`, `org.robolectric:robolectric`, `androidx.test:runner`, `androidx.test.ext:junit`.
   - `debugImplementation`: `androidx.compose.ui:ui-test-manifest`.
   - `android { testOptions { unitTests.isIncludeAndroidResources = true } }`.
2. Smoke-тест `OrderCardTest` (`@RunWith(AndroidJUnit4::class)`, `@Config(sdk = [34])`, `createAndroidComposeRule<ComponentActivity>()`): отображает текст, вызывает `onClick` на клик.
3. CI: `fast-check` уже прогоняет `testDebugUnitTest` — smoke зелёный = инфра рабочая.
4. Если JDK 17 в CI не хватает для Robolectric SDK 34/36 — `@Config(sdk = [34])`, либо bump JDK в `ci.yml` отдельным fix-коммитом.
5. `docs/gradle-setup.md` — секция «Compose UI tests (Robolectric)»: как запустить локально, ограничения (нет RenderThread / accessibility / IME).

**Оценка U0:** ~2 ч.

#### ✅ U1 — OrdersScreen (3 проходки, PR #116, #117, #118)

**Дата закрытия:** 2026-11-10

**Подтверждение:** выполнено (Pass-1 PR #116 `6140b66f`; Pass-2 PR #117 `54b97521`; Pass-3 PR #118 `fec1c670`; CI green).

**Pass-1:** ✅ (PR #116, merge `6140b66f`).
- Вынесен `OrdersDialogsSection` (4 диалога: TradingBrokerDialog, OrdersListDialog, SearchBrokerDialog, PairSearchBrokerDialog): 49 строк → 1 вызов; `OrdersScreen` 332 → 304.
- Mobile-safe extract: state/effects (`remember`, `LaunchedEffect`, `derivedStateOf`) **не** переносились; child принимает VMs + `showOrdersListDialog` + callback.
- `OrdersHeader` / `OrdersBottomButtons` / `OrdersScreenSnackbar` уже были вынесены в PR #92/#93 — Pass-1 их не трогал.
- `@Suppress("LongMethod")` остаётся (304 > 60); `@Suppress("CyclomaticComplexMethod")` уже снят в PR #92.
- CI: fast-ktlint + fast-check зелёные.

**Pass-2:** ✅ (PR #117, merge `54b97521`).
- **Extract `OrdersScrollableContent`:** `rememberScrollState` + `rememberCoroutineScope` + `remember(derivedStateOf)` × 2 + ~15 callbacks — внутрь child (им там место, state наружу не hoist-ится).
- **`OrdersScreenBehaviorTest` невозможен:** `OrdersScreen` инжектит 4 VM через `hiltViewModel()`, `createComposeRule()` без Hilt-test-infra (`@HiltAndroidRule`, `TestApplication`) не запустится. Вынесено в `### Compose UI: Hilt-test-infra для OrdersScreen` (2d).
- **Behavioral тесты — на чистые извлечённые composables:** `OrdersDialogsSection` (showOrdersListDialog true/false), `InstrumentSearchField` (`inputText` / `expanded` / `onQueryChanged`), `OrdersTypeChips`, `OrdersQuantityRow`. Рендерятся через `createComposeRule()` (U0 infra), без Hilt.
- `LaunchedEffect(Unit) { checkApiInitialization() }` — остаётся в `OrdersScreen` (side-effect VM, не UI-state).
- CI: fast-check (behavioral).

**Pass-3:** ✅ (PR #118, merge `fec1c670`).
- `@Preview` добавлены для `OrdersQuantityRow` (enabled/disabled), `OrdersTypeChips` (Market/Limit), `SearchResultItem` (Share stub).
- `OrdersScrollableContent` — skip (40-line stub anti-pattern в main-source); KDoc `Pass-3 skip` в `OrdersScrollableContent.kt`.
- `@Suppress("LongMethod")` остаётся на `OrdersScreen` (140 > 60) и `OrdersScrollableContent` (82 > 60, 2c-fixation Pass-2a).
- CI: fast-ktlint + fast-check зелёные (после fix-коммита `f8272a19` → blank lines).

**Оценка U1:** ~4–6 ч.

#### U2 — PortfolioScreen (3 проходки)

**Pass-1:** extract `AccountHeaderCard` / `PortfolioAccountBlock` / `EmptyPortfolioCard` (частично уже есть).
**Pass-2:** behavioral test (load / error / empty / sandbox pay-in) до/после; перенос `remember`/`LaunchedEffect` в child.
**Pass-3:** снять `@Suppress` на `PortfolioScreen` (`LongMethod` + `CyclomaticComplexMethod`).

**Оценка U2:** ~3–4 ч.

#### U3 — SettingsScreen (3 проходки)

**Pass-1:** extract внутри `TInvestSettingsPanel` (324 строки) / `BcsSettingsPanel` (182) / `FinamSettingsPanel` (149) — подсекции (token, sandbox-accounts, actions).
**Pass-2:** behavioral test на «подключиться / отключиться / ошибка» (после `### Settings: inline status card vs snackbar`, если выбран snackbar — статус проверяется через snackbar; иначе inline Card).
**Pass-3:** снять `@Suppress` на 4 панелях (`SettingsScreen` 65, `TInvestSettingsPanel` 356, `BcsSettingsPanel` 201, `FinamSettingsPanel` 164).

**Оценка U3:** ~5–6 ч.

**Тесты:** U0 smoke; U1–U3 pass-2 behavioral (Robolectric); unit (presentation VM) — как раньше.

**Референсы:** Compose UI Test (developer.android.com), Robolectric, `docs/live-test.md`, `docs/gradle-setup.md`.

**Ограничения:**
- Pass-1 **не** переносит `remember` / `LaunchedEffect` / `derivedStateOf` (только extract).
- Pass-2 — обязательный behavioral test **до** и **после** переноса.
- Pass-3 — `@Suppress` снимается только при detekt-зелёном.
- UI визуал не меняется (правило 1: только по санкции).
- U0 отдельным PR; U1/U2/U3 — по одному PR каждый (один CI-цикл на экран).

**Критерий готовности:** U0 smoke зелёный на CI; U1–U3 (Pass-1 + Pass-2 + Pass-3), behavioral-тесты зелёные, `@Suppress` сняты (где возможно), detekt зелёный.

**Оценка:** ~14–18 ч.

**Зависимости:** нет.

**Связано:** `### Рефакторинг архитектурного долга (detekt-группа 3)` → `#### Compose-экраны` (поглощено этой umbrella); `#### Compose-компоненты` (AssetPositionCard, BrokerAccountDialog) — отдельная задача; `### Settings: inline status card vs snackbar` (U3 Pass-2 зависит от выбора A/B).

**Открытые вопросы:** JDK 17 vs Robolectric SDK 34/36 в CI — проверить первым PR U0. Paparazzi/Roborazzi (screenshot) — опционально, отдельная задача (не блокер).

___

### Orders: P&L в песочнице (expectedYield)

**Мобильная работа:** частичное (live-тест в песочнице: 2 счёта с SBER).

**Статус:** запланировано.

**Контекст:** Pre-existing bug (воспроизведён на `dev` при live-тесте 2026-11-03 после PR #89). На **песочном** счёте `profit` / `profitPercent` на карточке заявки показывают **нули**. На боевом счёте — корректно. Причина по коду:
- `TInvestPortfolioService.fetchPortfolioPositions`: `expectedYield = pos.expectedYield?.let { it.units + it.nano / 1_000_000_000.0 }` — в sandbox `GetPortfolio.expectedYield` может быть `null` (серверное ограничение / sandbox не считает P&L). `Position.profit = null` → `profitPercent = null`.
- `refreshLastSelectedInstruments`: `.profit = pos?.profit ?: 0.0`, `.profitPercent = pos?.profitPercent ?: 0.0` — **null превращается в 0.0**. UI показывает «0», не «—».

**Соответствие Clean:** соответствует (баг-фикс).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды:** —.

**Предусловия:** нет. (PR #89 не виноват — идентично `dev` до миграции.)

**Файлы (ожидаемые):**
- `presentation/screens/orders/OrdersViewModel.kt` — `refreshLastSelectedInstruments` (`?: 0.0`).
- `presentation/screens/orders/OrdersUiState.kt` — `SelectedInstrumentInfo.profit: Double?` / `profitPercent: Double?` (сейчас `Double`, не `Double?` — проверить).
- `presentation/screens/orders/OrdersScreen.kt` — отображение P&L (`if (profit == null) "—" else format(...)`).
- `data/api/tinvest/TInvestPortfolioService.kt` — только диагностика (`expectedYield` в sandbox).
- `domain/models/Portfolio.kt` — `Position.profitPercent` getter (уже `null`-safe).

**Шаги:**

0. **Диагностика (обязательно до фикса).** В песочнице: 2 счёта, SBER, купить, `logger.d` в `TInvestPortfolioService` (raw `pos.expectedYield` от `GetPortfolio`), `PositionFlowManager.mergePartial` (profit), `updatePositionsFromSheet` (profit/profitPercent), `refreshLastSelectedInstruments` (pos?.profit). Собрать лог. **Гипотеза для проверки:** `GetPortfolio.expectedYield == null` в sandbox.
1. **По результатам шага 0 — выбрать вариант:**
   - **A (минимальный, если `expectedYield = null`).** `refreshLastSelectedInstruments`: убрать `?: 0.0` для `profit` / `profitPercent` (сохранять `null`). UI показывает «—» вместо «0». `profitPercent` в `SelectedInstrumentInfo` — `Double?` (если сейчас `Double` — миграция типа). Плюс: P&L не врёт (0 ≠ «неизвестно»). Минус: правка `SelectedInstrumentInfo` (тип) + UI.
   - **B (client-side fallback).** При `expectedYield == null` и `averagePrice > 0`: `profit = (currentPrice - avgPrice) * quantity * moneyPerPointOfPrice`. Это пересекается с `### P&L: client-side fallback для PortfolioPosition` (низкий приоритет в ROADMAP). Возможно, объединить — «sandbox P&L» = частный случай client-side fallback.
   - **C (не чинить).** Признать sandbox-ограничение, документировать в `docs/ISSUES.md` («P&L в песочнице: expectedYield = null, UI показывает 0»). Боевой счёт работает — не блокер.

   **Рекомендация:** **A** + запись в `docs/ISSUES.md` про sandbox-ограничение. B — отдельная задача (`P&L: client-side fallback`).
2. Если A: `SelectedInstrumentInfo.profit: Double?` / `profitPercent: Double?`; `refreshLastSelectedInstruments` — без `?: 0.0`; `OrdersScreen` — `profit?.let { format(...) } ?: "—"`.
3. UI-санкция (правило 1): отображение «—» вместо «0» на карточке заявки — согласовать.
4. Тесты: `refreshLastSelectedInstruments` — unit (если появится mock-инфра). Live: sandbox — «—», prod — корректный P&L.
5. Гейт.

**Тесты:** live-тест sandbox + prod.

**Референсы:** `### P&L: client-side fallback для PortfolioPosition` (B-вариант, low priority); `### Position: источники priceChangePercent и lockedQuantity` (`GetPositions` — альтернативный источник P&L); `docs/ISSUES.md`.

**Ограничения:**
- Не чинить B в этой задаче (отдельная `P&L: client-side fallback`).
- UI-санкция (правило 1): «—» вместо «0».
- Не менять `TInvestPortfolioService` без подтверждения диагностики.

**Критерий готовности:**
- В песочнице: `profit` / `profitPercent` на карточке — «—» (не «0»); в `docs/ISSUES.md` — запись про `expectedYield = null` в sandbox.
- На боевом счёте — P&L корректный (без регресса).
- Гейт зелёный; live-тест PASS.

**Оценка:** ~1.5–2 ч (диагностика 30 мин, A-фикс 30 мин, live-тест 30 мин, гейт 15 мин, резерв).

**Зависимости:** нет.

**Связано:** `### P&L: client-side fallback для PortfolioPosition` (B-вариант, отдельная задача); `### Position: источники priceChangePercent и lockedQuantity`.

**Открытые вопросы:** `expectedYield` в sandbox — `null` или `0`? Определить шагом 0. Если `0` — возможно, UI показывает 0 корректно (нет P&L), но это не отличимо от «реальный ноль»; тогда — отдельный `ISSUES.md` без фикса.




___




### Рефакторинг архитектурного долга (detekt-группа 3)

**Статус:** запланировано.

**Контекст:** при внедрении detekt 2.0.0-alpha.6 (см. задачу
`detekt` в разделе выше) выявлены 10 групп архитектурного
долга. Правила `LongMethod`, `CyclomaticComplexMethod`,
`TooManyFunctions`, `LargeClass` подавлены в коде через
`@Suppress` со ссылкой на эту секцию. `MagicNumber` (45
записей) зафиксирован в `app/detekt-baseline.xml`.

**Общие предусловия:**
- Перед рефакторингом каждой группы — прогнать тесты,
  зафиксировать текущее поведение.

**Правила active по дефолту (уточнение 2026-10-31).** Задача
`### detekt: включить архитектурные правила` закрыта слиянием
с этой: `config/detekt/detekt.yml` использует
`buildUponDefaultConfig = true`, 4 правила
(`LongMethod`, `CyclomaticComplexMethod`, `TooManyFunctions`,
`LargeClass`) не переопределены в `config/detekt/detekt.yml` и
наследуют дефолт `active: true`. `@Suppress` в коде — реальные
маркеры долга, а не декоративные (см. `grep "@Suppress\("
app/src/main/kotlin`). Отдельная задача не нужна.
- Разбивать на подзадачи: один класс / одна функция за раз,
  коммит после каждого шага (правило 4b AGENTS.md).
- После рефакторинга — снять `@Suppress`, убедиться что
  detekt проходит без него.

**Общие ограничения:**
- Не менять UI без санкции (правило 1 AGENTS.md).
- Не ломать публичные контракты без миграции вызывающих.
- Тесты (63 unit) должны оставаться зелёными.

**Общий критерий готовности:** все `@Suppress` из этой секции
сняты; `:app:detekt` проходит без новых подавлений; тесты
зелёные.

**Оценка:** ~15–20 часов суммарно.

**Зависимости:** нет.




___




#### Compose-экраны

**Перенесено в umbrella** `### Compose UI: test infra + 3 экрана (3 проходки)` (см. выше по приоритету; подсекции U0–U3). Здесь оставлен только маркер — не дублировать. `#### Compose-компоненты` (AssetPositionCard, BrokerAccountDialog) остаются в этой секции как отдельная работа.

#### Compose-компоненты

**Файлы:**
- `ui/components/AssetPositionCard.kt` — `AssetPositionCard`
  (109 строк), `AssetCardContent` (136 строк, complexity 20).
- `ui/components/BrokerAccountDialog.kt` (105 строк).
- `ui/components/OrdersListViewModel.kt` — `loadOrders`
  (62 строки).

**Оценка:** ~2 часа.




___




#### ViewModel-слой

**Статус:** выполнено (13d split, 2026-11-07; M006, 2026-11-06).

**Что сделано:**
- `OrdersViewModel` → 4 VM (Coord / Form / Search / Dialogs), 13d split.
  `postOrder` orchestration → `PlaceOrderUseCase` (13d-C, `a793c38`).
  Было: 49 функций / LargeClass / `postOrder` 172 строки.
  Факт: `OrdersViewModel` — 8 функций.
- `SettingsViewModel` → 5 panel-VM + 6 usecase (M006, PR #103).
  Было: 20 функций. Факт: 1 функция.
- `PortfolioViewModel`: 12 → 10 функций.

**Остаток (отдельные задачи, не здесь):**
- `OrderFormViewModel` — 29 функций, `@Suppress("TooManyFunctions")` →
  `### OrderFormViewModel: reduce functions`.
- `OrderSearchViewModel` — 17 функций, `@Suppress("TooManyFunctions")` →
  `### OrderSearchViewModel: reduce functions` (см. ниже).
- `OrdersViewModel` — 8 функций, `@Suppress("TooManyFunctions")` остаточный
  (over-suppress после split; 8 ≤ 11) → chore-коммит без задачи.

**Оценка:** ~5 часов (было; выполнено в 13d + M006).



___



#### BrokerApi-реализации

**Архитектурный долг, вынесено в отдельную задачу.**
`### Рефакторинг BrokerApi-реализаций (D)` — см. «Планируемые
задачи». Полный план (TInvest WS/REST split, BCS/Finam
отложены) — там.




___




#### Репозитории

**Файлы:** `data/repository/InvestRepository.kt` (16 функций),
`data/repository/SettingsRepository.kt` (15).

**Пути решения:** разбить по доменным областям (аккаунты,
заявки, портфель, стримы — для `InvestRepository`; токены,
настройки UI, режим sandbox — для `SettingsRepository`).

**Архитектурный долг, вынесено в отдельную задачу.**
`InvestRepository` — `### Разбивка InvestRepository по
доменным областям` (см. «Планируемые задачи»). Полная
оценка, scope и Clean-граница (`domain.api.repository.*`
интерфейсы + миграция ViewModel) — там.

`SettingsRepository` — **остаётся здесь** (не в scope той
задачи, отдельная работа).

**Оценка:** ~2 часа (устаревшая оценка для `InvestRepository`;
актуально — см. новую задачу, ~5–6 ч с Clean-границей.
Для `SettingsRepository` — ~2 ч, оценка актуальна).




___




#### UseCase-слой

**Файлы:**
- `domain/usecases/CalculateTradeDetailsUseCase.kt` —
  `calculate` (complexity 25).
- `domain/usecases/PrepareOrderRequestUseCase.kt` —
  `prepare` (92 строки, complexity 20).

**Пути решения:** разбить `calculate` на отдельные функции
по типу заявки (market/limit/stop). `prepare` — вынести
валидацию, сборку request, разрешение uid в отдельные шаги.

**Оценка:** ~2 часа.




___




#### Data-слой T-Invest

**Файлы:**
- `data/api/tinvest/TInvestPortfolioService.kt` —
  `fetchPortfolioPositions` (was `fetchPositionsSnapshot`; complexity 26).
- `data/api/tinvest/TInvestInstrumentMapper.kt` — `mapProtoToDomain`
  (118 строк).

**Пути решения:** `fetchPortfolioPositions` (was `fetchPositionsSnapshot`)
разбить на `fetchPortfolio` + `enrichWithInstrumentData` +
`enrichWithMarginData`. `mapProtoToDomain` — на
`mapFutures` / `mapShares` / `mapDefault`.

**Оценка:** ~2 часа.




___



### CustomTrustManager: fail-fast + 5 CA (Н79+Н80)

**Мобильная работа:** полное закрытие (proot + PR, гейт на CI). **Live-тест не требуется** (fail-fast виден в логах; unit-тест покрывает).

**Статус:** запланировано.

**Контекст:** (clean-audit Н79 + Н80) `CustomTrustManager.loadCertificateToKeyStore` — `catch (e: Exception) { logger.w(...) }`. Если сертификат не загрузился — тихо пропускаем, TLS работает с усечённым набором, диагностика потеряна. Нет проверки `customTrustManager.acceptedIssuers.size == 5`. Риск: молчаливая деградация. **Security, латентный.**

**Соответствие Clean:** соответствует (resilience, fail-fast).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** нет.

**Файлы:**
- `data/api/common/CustomTrustManager.kt` — `catch` → `throw`, проверка `acceptedIssuers.size`.
- `data/api/common/CustomTrustManagerTest.kt` (новый) — unit-тест.

**Шаги:**
1. `loadCertificateToKeyStore`: `catch (e: CertificateException | IOException)` → `throw IllegalStateException("CA load failed: res=$resId", e)`. Fail-fast.
2. После init `customTrustManager` — `check(customTrustManager.acceptedIssuers.size >= 5) { "Expected 5 Russian CAs, got ${...}" }`.
3. Unit-тест `CustomTrustManagerTest`: mock `Context` → `IllegalStateException`; happy path — 5 CA.
4. Гейт.

**Тесты:** `CustomTrustManagerTest` — 2 теста. Если mock-инфраструктуры нет — fail-fast тест с `mockk<Context>` (у `Context.resources` mock возвращает `null`/пусто → `IllegalStateException`). Решить при старте.

**Референсы:** `docs/clean-audit.md` → Н79, Н80. `docs/ISSUES.md` → «BcsBrokerApi создаёт OkHttpClient без CustomTrustManager».

**Ограничения:** не менять `checkServerTrusted`; не менять 5 raw-ресурсов; не смешивать с BCS-okhttp-без-CA (Н78, отдельно).

**Критерий готовности:** `catch` → `throw`; `check(acceptedIssuers.size >= 5)`; unit-тест 2 кейса; гейт зелёный.

**Оценка:** ~40–50 мин.

**Зависимости:** нет.

**Связано:** `docs/clean-audit.md` → Н79, Н80; `docs/ISSUES.md` → BCS OkHttp.

**Открытые вопросы:** mock-инфраструктура для `Context` — Robolectric или mockk? **Дефолт:** fail-fast тест с `mockk<Context>`. Решить при старте.




___



### Ревизия ссылок на PortfolioPosition после H5

**Мобильная работа:** полное закрытие (docs-only, proot).

**Статус:** запланировано.

**Контекст:** после закрытия `### H5. PortfolioPosition: разделение domain/presentation` (удаление legacy `PortfolioPosition`) несколько записей в `docs/` содержат устаревшие ссылки / формулировки. Их нужно актуализировать или закрыть — иначе следующая сессия будет введена в заблуждение.

**Что нужно отревизовать:**

1. **`### UI: отображение blocked на валютной карточке`** (`docs/ROADMAP.md`) — после `Portfolio: модель портфеля` деньги (`money` / `blocked`) переехали в `AccountBalance`, а не в `PortfolioPosition.money` / `.blocked`. Валютная карточка `RUB000UTSTOM` удалена с `PortfolioScreen`, баланс — в `AccountHeaderCard` (шапка счёта). Задача **пересматривается**: либо закрыть как выполненную в `Portfolio: модель портфеля` (шапка счёта с `AccountBalance.total`), либо переформулировать под остаток (если есть).

2. **`### P&L: client-side fallback для PortfolioPosition`** (`docs/ROADMAP.md`) — все упоминания `PortfolioPosition` в KDoc / формулировке устареют после H5. Fallback (`clientProfit()`) теперь применяется к `Position` (domain) / `PositionStreamItem` (в менеджере), а не к `PortfolioPosition`. Требуется переименовать задачу (`### P&L: client-side fallback для Position`), обновить ссылки в теле.

3. **`docs/architecture.md` → «Data flow: позиции»** — упоминает `PortfolioPosition` как legacy (актуально сейчас). После H5 — устареет: `Position` — единственная модель, `PortfolioPosition` нет. Обновить раздел «Модели» / «Legacy `flow`» / «ViewModel».

4. **`docs/clean-audit.md` → Н5** — после H5 статус → `исправлено`, запись переносится в `### Исправленные (L3)`. Проверить связанные упоминания (`toPortfolioPosition`, `mergeWith` в других записях — уже удалены, ссылки устарели).

5. **KDoc в коде** — после H5 `grep -rn "PortfolioPosition" app/src` = 0 (это критерий H5), но KDoc-ссылки на удалённые функции (`toPortfolioPosition` и т.п.) в других файлах могут остаться. Проверить `grep -rn "toPortfolioPosition\|mergeWith\|PortfolioPosition" app/src` и почистить.

**Соответствие Clean:** соответствует.

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** `### H5. PortfolioPosition: разделение domain/presentation` — закрыта (merge в `dev`).

**Файлы:**
- `docs/ROADMAP.md` — `### UI: отображение blocked...`, `### P&L: client-side fallback...`.
- `docs/architecture.md` — «Data flow: позиции».
- `docs/clean-audit.md` — Н5 (закрытие), связанные упоминания.
- KDoc в `app/src/**/*.kt` — если есть ссылки на удалённые `PortfolioPosition`-функции.

**Шаги:**
1. `grep -rn "PortfolioPosition\|toPortfolioPosition\|mergeWith" app/src docs/` — собрать фактический список ссылок.
2. Пройти по 5 пунктам выше, для каждого — решить: закрыть / переформулировать / обновить.
3. Правки — docs-only в `dev` (категория 2 по 5g), каждый документ — отдельный коммит (5b).
4. KDoc — правка в feature-ветке (если есть) или отдельным `chore(docs)` в `dev`.
5. Проверка: `grep -rn "PortfolioPosition" app/src docs/ROADMAP.md docs/architecture.md` — пусто (кроме исторических KDoc в `docs/clean-audit.md` → `### Исправленные`).

**Тесты:** не требуются (docs-only + KDoc).

**Референсы:**
- `### H5. PortfolioPosition: разделение domain/presentation` — источник.
- История H5: merge `d04ecc4`, PR #90.
- `docs/clean-audit.md` → Н5.

**Ограничения:**
- Не менять код (только docs + KDoc).
- Не создавать дубли задач — обновлять существующие.
- Каждый документ — отдельным коммитом (5b, атомарная пара).

**Критерий готовности:**
- Все 5 пунктов выше обработаны (закрыто / переформулировано / обновлено).
- `grep -rn "PortfolioPosition" docs/ROADMAP.md docs/architecture.md` — пусто (кроме записей в `### Исправленные` / исторических).
- `grep -rn "PortfolioPosition" app/src` — пусто (критерий H5).
- `docs/clean-audit.md` Н5 → `исправлено`.

**Оценка:** ~30–45 мин (docs-only, 3–4 коммита).

**Зависимости:** `### H5. PortfolioPosition: разделение domain/presentation` (blocker).

**Связано:** `### H5. PortfolioPosition...`, `### ✅ Portfolio: модель портфеля`, `### UI: отображение blocked...`, `### P&L: client-side fallback...`. История: merge `d04ecc4`, PR #90.

**Открытые вопросы:**
- `### UI: отображение blocked...` — закрыть как выполненную в Portfolio (шапка счёта) или переформулировать под остаток (например, детализация по валютам)? Решить при ревизии, по факту UI.
- `### P&L: client-side fallback...` — переименовать в `... для Position`? Решить при ревизии, зависит от того, где сейчас применяется fallback (PositionStreamItem? Position?).




___




### 2c-фиксация: Н28 (AccountsApi sandboxMode) + Н15 (TradingStateSnapshot)

**Мобильная работа:** полное закрытие (proot + PR, гейт на CI).

**Статус:** запланировано.

**Контекст:** (clean-audit Н28 + Н15) два отклонения от Clean, оба **приняты по 2c** (сознательное решение «не переделывать») без формальной фиксации.

- **Н28:** `AccountsApi.getAccounts(sandboxMode)` — domain-контракт несёт T-Invest-специфику. BCS/Finam игнорируют. 1 callsite (`AccountsRepositoryImpl:49`), 2 impl. Refactor (`TInvestAccountsApi : AccountsApi`) не окупается.
- **Н15:** `TradingStateSnapshot` (`quantity: String`, `pairedMultiplier: String`, `orderType: OrderTypeSelection`, `pairTradingEnabled: Boolean`) — presentation-снимок в domain. Simple DTO для DataStore, 4 поля. Refactor — ~1–1.5 ч + миграция формата. Не окупается.

**Соответствие Clean:** отклонение по 2c (принято).

**Нарушенное правило Clean:** — (фиксация уже принятых отклонений).

**Clean-альтернатива (Н28):** `TInvestAccountsApi : AccountsApi` с `getAccounts(sandboxMode)`, `AccountsApi.getAccounts()` без параметра. Плюсы: чистый domain. Минусы: +1 интерфейс, правка 2 impl, 1 callsite — overhead > выгоды.

**Clean-альтернатива (Н15):** `TradingStateSnapshot` — domain-валидированный (`quantity: Long?`, `orderType` в presentation). Плюсы: domain чист. Минусы: ~1–1.5 ч + миграция DataStore-формата (бэкап-совместимость), 4 поля.

**Выгоды (для «отклонение по 2c»):**
- **Н28:** 1 callsite, 2 impl, готово. Refactor — overhead без текущей выгоды.
- **Н15:** 4 поля, простой DTO, DataStore-ключи 1:1. Refactor — миграция ради 4 полей.

**Предусловия:** нет.

**Файлы:**
- `docs/clean-audit.md` — Н28 и Н15 → `принято (2c)` + `**Выгоды (по 2c):**` (конкретно) + перенос в `### Принятые (2c)`.

**Шаги:**
1. `docs/clean-audit.md`: Н28 → `**Статус:** принято (2c).` + `**Выгоды (по 2c):** <текст>`. Перенос в `### Принятые (2c)` в L4 (если нет — создать).
2. То же для Н15, L3.
3. `git diff --check`.
4. Коммит `docs(clean-audit): accept 2c for Н28 + Н15`.

**Тесты:** не требуются.

**Референсы:** `docs/clean-audit.md` → Н28, Н15. `AGENTS.md` → 2c.

**Ограничения:** не менять код. Не создавать задачи в ROADMAP (эти два — окончательно приняты). Каждый — отдельным блоком в clean-audit.

**Критерий готовности:** Н28 и Н15 в `### Принятые (2c)` с тремя элементами 2c; `git diff --check` чистый.

**Оценка:** ~10–15 мин.

**Зависимости:** нет.

**Связано:** `AGENTS.md` → 2c.

**Открытые вопросы:** раздел `### Принятые (2c)` — общий или в каждом уровне? **Дефолт:** в каждом уровне (L3 / L4), по образцу `### Исправленные (Lx)`.




___




### Margin/risk → TradeCheckResult

**Мобильная работа:** полное закрытие (proot + PR, гейт на CI).

**Статус:** запланировано.

**Контекст:** margin/risk-поля (`initialMarginOnBuy/Sell`, `dlong/kshort`, `dlongMin/dshortMin`, `klong/kshort`) — account-specific. У T-Invest — свойства инструмента в `Future`/`Option`. У Finam — `AssetsService/GetAssetParams` с `account_id` (per-account). Это **не свойство инструмента**, а «доступность в этом счёте». Сейчас лежат в `FutureUi`, что мешает и чистоте (не property of instrument), и мультиброкерности (у BCS/Finam другой источник).

**Соответствие Clean:** соответствует (domain-модель разделяется по ответственности).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** `### Instrument: C-min → sealed interface (две фазы)` — Phase 2 закрыта (в Phase 2 однозначно видно, что margin — Future-specific и account-specific).

**Файлы:**
- `app/src/main/kotlin/.../domain/models/Instruments.kt` — удалить margin/risk из `Future`, `Option`.
- `app/src/main/kotlin/.../domain/models/TradingStatus.kt` (или новый `MarginInfo.kt`) — `MarginInfo` (или расширить `TradeCheckResult`).
- `app/src/main/kotlin/.../data/api/tinvest/` — мапперы T-Invest: margin из `Future`/`GetFuturesMargin`.
- Finam — при реализации `GetAssetParams`.
- `app/src/main/kotlin/.../domain/usecases/CalculateTradeDetailsUseCase.kt` — использовать `MarginInfo` (если нужно).

**Шаги:**
1. Решить форму: новый `MarginInfo` (отдельная модель) или расширение `TradeCheckResult` (`margin: MarginInfo?`).
2. Удалить margin/risk из `Future`/`Option` (после Phase 2).
3. Добавить `MarginInfo` (buy/sell margin, klong/kshort, dlong/dshort, dlongMin/dshortMin, dlongClient/dshortClient — nullable).
4. T-Invest: маппер — из `Future`/`GetFuturesMargin` в `MarginInfo` (через `TradeCheckResult`).
5. Finam: при реализации `GetAssetParams` — маппинг.
6. Тесты: `MarginInfo` (T-Invest futures), `TradeCheckResult.margin` (success/failure).
7. Гейт.

**Тесты:** `MarginInfo` (T-Invest futures), `TradeCheckResult.margin` (success/failure), Finam — при реализации.

**Референсы:** proto T-Invest `Future` (`initial_margin_on_buy/sell`, `dlong*`, `klong/kshort` — deprecated), `GetFuturesMarginResponse`. Finam `GetAssetParams` (`long_initial_margin`, `short_initial_margin`, `long_risk_rate`, `short_risk_rate`, `longable`, `shortable`).

**Ограничения:**
- Не смешивать с `### Instrument: C-min → sealed interface` — отдельная задача после Phase 2.
- `klong`/`kshort` — `[deprecated = true]` в T-Invest proto; в `MarginInfo` можно не включать, зафиксировать в KDoc.
- Не менять UI без санкции.

**Критерий готовности:**
- `MarginInfo` (или `TradeCheckResult.margin`) создан.
- Margin/risk не в `Instrument`/`Future`/`Option`.
- T-Invest маппинг работает (unit-тесты).
- Finam — заглушка (при реализации).
- Гейт зелёный.

**Оценка:** ~2 ч.

**Зависимости:** `### Instrument: C-min → sealed interface (две фазы)` — Phase 2.

**Связано:** `docs/domain_models.md` §6 «Вне модели»; `docs/clean-audit.md` → Н5 (концептуальное разделение модели).

**Открытые вопросы:**
- Форма: отдельный `MarginInfo` или поле в `TradeCheckResult` — решить при старте.
- `klong`/`kshort` — deprecated в T-Invest; включать в модель или нет — решить при старте.




___





### T-Invest: subtype-specific поля в mapShare / mapFuture / mapBond / mapOption / mapEtf / mapCurrency

**Мобильная работа:** полное закрытие (proot + PR, гейт на CI).

**Статус:** запланировано.

**Контекст:** в Phase 2 (§8 шаг 3) `mapShare` / `mapFuture` / `mapBond` / `mapOption` / `mapEtf` / `mapCurrency` принимают `ProtoInstrument` — bulk-тип, в котором **нет** subtype-specific полей. Поэтому `mapShare` возвращает `Share` с `ipoDate = null`, `issueSize = null`, ...; `mapFuture` — `Future` с `firstTradeDate = null`, ...; и т.д. Поля из §5 (`ipoDate`, `issueSize`, `futureType`, `firstTradeDate`, `faceValue`, `couponRate`, `direction`, `strikePrice`, ...) **не заполняются**. Это `0.2`/`0.3` для T-Invest proto — §8 шаг 0.1 говорит «`Share`/`Future`/`Option`/`Instrument` уже получены 2026-11-01», но маппинг не сделан.

**Соответствие Clean:** соответствует.

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** нет.

**Файлы:**
- `data/api/tinvest/TInvestInstrumentMapper.kt` — переписать `mapShare` / `mapFuture` / `mapBond` / `mapOption` / `mapEtf` / `mapCurrency` на proto `Share` / `Future` / `Bond` / `Option` / `Etf` / `Currency`.
- `data/api/tinvest/TInvestInstrumentsService.kt` — `fetchProtoInstrument` возвращает `Instrument` (bulk); для каждого типа — отдельный вызов SDK (`Shares().shareBy` / `Futures().futureBy` / ...) или `Instrument` + `GetInstrumentBy` c `instrumentKind`.
- `data/api/tinvest/TInvestRestMappers.kt` / `TInvestRestInstrumentMappers.kt` — то же для REST (если REST отдаёт `Share`/`Future`/... — сейчас `RestInstrument` единый).
- `data/repository/InstrumentRepositoryImpl.kt` — если нужен кэш по типу.

**Шаги:**
1. Проверить, какие proto-типы T-Invest SDK доступны (`Share` / `Future` / `Bond` / `Option` / `Etf` / `Currency`).
2. Переписать 6 мапперов на proto-subtype (T-Invest API: `InstrumentsService.Shares` / `Futures` / `Bonds` / `Options` / `Etfs` / `Currencies`).
3. `fetchProtoInstrument` → `fetchProtoInstrumentByType` (dispatcher по `instrumentType`).
4. Заполнить subtype-specific в 6 data class.
5. `Bond` / `Option` / `Etf` / `Currency` — тоже заполнить (не только `Future` / `Share`).
6. Тесты: 6 мапперов (unit).

**Тесты:** `TInvestInstrumentMapperTest` — 6 функций, каждый subtype-specific-поле проверено.

**Референсы:** proto T-Invest: `ru.tinkoff.piapi.contract.v1.{Share, Future, Bond, Option, Etf, Currency}`; SDK: `InstrumentsService` (`shares` / `futures` / `bonds` / `options` / `etfs` / `currencies`).

**Ограничения:**
- Не менять `Instrument` интерфейс — только заполнение subtype-specific.
- `mapOther` — остаётся `Other` без subtype-specific (по §5).
- REST fallback: `RestInstrument` — единый DTO; если REST не даёт subtype-specific — оставить `null` (см. §8 «при реализации BCS/Finam»).

**Критерий готовности:**
- `mapShare` / `mapFuture` / `mapBond` / `mapOption` / `mapEtf` / `mapCurrency` заполняют subtype-specific поля (не `null` по умолчанию).
- Тесты мапперов — 6 функций, subtype-specific поля проверены.
- Гейт зелёный.

**Оценка:** ~4–6 ч.

**Зависимости:** нет.

**Связано:** `docs/domain_models.md` §5, §8 шаг 3; `### Пакет C: TInvestMarketDataService split` (образец).

**Открытые вопросы:** какие proto-поля `Bond` / `Etf` / `Currency` точно доступны — при реализации подтвердить.




___




### Position: источники priceChangePercent и lockedQuantity

**Мобильная работа:** полное закрытие (proot + PR, гейт на CI).

**Статус:** запланировано.

**Контекст:** `Position` (sealed, 7 subtypes) имеет computed-поля `priceChangePercent: Double?` и `lockedQuantity: Double?`, но `PositionStreamItem` их не содержит (коммит «Portfolio: модель портфеля» добавляет конвертер `toPosition` с этими полями = `null`). Нужны источники в мапперах брокеров:
- **`priceChangePercent`** — изменение цены за торговый день, % от цены открытия. **Не** `(currentPrice - averagePrice) / averagePrice` (это P&L, отдельно). Источники: T-Invest `dailyYieldRelative` (`PositionData`), BCS `dailyPercentPL`, Finam `daily_pnl`.
- **`lockedQuantity`** — заблокированное количество бумаг. T-Invest `blocked` в позициях `securities`/`futures`/`options`, BCS `locked`, Finam — `null` (у Finam `maintenance_margin` — деньги ГО, не бумаги).

Сейчас в `PositionStreamItem` эти поля отсутствуют, конвертер отдаёт `null` (правило 2d — временное, задача = полное).

**Соответствие Clean:** соответствует (расширение domain-модели + мапперов).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** `### Portfolio: модель портфеля` — закрыта (конвертер `toPosition` реализован).

**Файлы:**
- `domain/models/Portfolio.kt` — `PositionStreamItem + priceChangePercent: Double? = null + lockedQuantity: Double? = null`.
- `data/api/tinvest/TInvestPortfolioService.kt` — из `PositionData.dailyYieldRelative`, `PositionsSecurities.blocked`.
- `data/api/tinvest/TInvestPositionsWsMapper.kt` — из `PositionsStreamResponseDto` (`dailyYieldRelative`, `blocked`).
- `data/api/tinvest/TInvestRestMappers.kt` / `TInvestRestInstrumentMappers.kt` — из `RestPosition` (если DTO содержит).
- `data/api/bcs/BcsBrokerApi.kt` — `dailyPercentPL`, `locked`.
- `data/api/finam/FinamBrokerApi.kt` — `daily_pnl`, `locked = null`.
- `presentation/screens/portfolio/PortfolioViewModel.kt` — убрать локальный пересчёт `priceChangePercent` из tick-to-tick (`updatePriceFromStream`); брать из `Position`.
- `presentation/components/AssetPositionCard.kt` — tick-delta для анимации цвета считать локально (из `previousPrice`), не из VM.

**Шаги:**
1. `PositionStreamItem` — 2 новых поля.
2. Мапперы T-Invest — заполнить из `dailyYieldRelative` / `blocked`.
3. Мапперы BCS / Finam — при реализации брокеров (эта задача — только T-Invest; BCS/Finam — заглушки `null`).
4. `toPosition` — передавать поля в `Position` (без хардкода `null`).
5. `PortfolioViewModel.updatePriceFromStream` — убрать пересчёт `changePercent`; тик-дельту для цвета считать в `AssetPositionCard` локально.
6. Тесты мапперов (T-Invest) + `AssetPositionCard` превью с `priceChangePercent != null`.
7. Гейт.

**Тесты:** `TInvestPositionsWsMapperTest` / `TInvestRestMappersTest` — `priceChangePercent`, `lockedQuantity` заполнены. `PositionTest` (если есть) — `toPosition` передаёт поля.

**Референсы:** proto T-Invest `PositionData.daily_yield_relative`, `PositionsSecurities.blocked`. BCS `https://trade-api.bcs.ru/http/portfolio`. Master-plan `docs/domain_models.md` §4.

**Ограничения:**
- **UI-санкция** (правило 1): `PortfolioViewModel` перестаёт считать tick-to-tick `priceChangePercent`; `AssetPositionCard` считает локально. Визуально — оба элемента (карточка и P&L-секция) сохраняют цветовую индикацию, но карточка — от `dailyYieldRelative` (стабильно в течение дня, без мигания при каждом тике), P&L-секция — от `profitPercent`.
- `lockedQuantity` — `Double?`, как и в модели.
- Не смешивать с `### UI: отображение blocked на валютной карточке` (там `money`/`blocked` валюты) — это про позиции бумаг.
- Не трогать `PortfolioPosition` (legacy) — миграция в `### OrdersViewModel: миграция на PortfolioSheet`.

**Критерий готовности:**
- `PositionStreamItem.priceChangePercent` / `lockedQuantity` заполняются в T-Invest мапперах.
- `Position` (из `toPosition`) содержит значения.
- `PortfolioViewModel` не пересчитывает `priceChangePercent` через тик-дельту.
- `AssetPositionCard` анимация цвета — на локальной тик-дельте, цвет — на `position.priceChangePercent`.
- Гейт зелёный.

**Оценка:** ~2–3 ч (поля 10 мин, T-Invest мапперы 45 мин, `toPosition` 10 мин, VM+Card 1 ч, тесты 30 мин, гейт 15 мин).

**Зависимости:** `### Portfolio: модель портфеля` (blocker).

**Связано:** `### Portfolio: модель портфеля`; `### OrdersViewModel: миграция на PortfolioSheet`; `### UI: отображение blocked на валютной карточке` (разные `blocked`); `docs/domain_models.md` §4.

**Открытые вопросы:**
- `AssetPositionCard` сейчас принимает `priceChangePercent: Double?` параметром. После задачи — брать из `position.priceChangePercent`? Или оставить параметром (гибче для тестов)? **Рекомендация:** из `position` (модель знает, UI читает).




___




### Rate-limiter для unary-запросов T-Invest

**Мобильная работа:** полное закрытие (proot + PR, гейт на CI).

**Статус:** запланировано.

**Контекст:** в проекте **нет rate-limiter для unary-запросов**
к T-Invest. Единственные механизмы ограничения частоты —
периодичность polling (`RETRY_DELAY_MS = 10_000L` для позиций,
`5_000L` для цен) и backoff переподключения стримов
(`GRPC_RETRY_DELAYS_MS`, `RETRY_DELAYS_MS`). Это не ограничители
частоты API, а расписания. При параллельных запросах
(например, `getTradingStatuses` через
`coroutineScope { awaitAll() }` — N параллельных unary на
N инструментов) лимит легко превышается.

Официальные лимиты T-Invest записаны в `docs/rate-limits.md`:
не более 50 запросов в секунду суммарно, плюс по-сервисные
лимиты в минуту (Instruments 200, Users 100, MarketData 300,
Orders 100 — из них `getOrders` 200, `postOrder` 15/сек,
и т.д.).

**Зачем:** без rate-limiter'а возможны `RESOURCE_EXHAUSTED`
от API, троттлинг на стороне сервера, деградация UX
(запросы уходят в отказ, UI показывает ошибки). Особенно
критично для `postOrder` (15/сек) и `getTradingStatuses`
(N параллельных).

**Предусловия:** нет.

**Файлы:**

- `app/src/main/kotlin/com/gitlab/biomorf/tscalp/data/api/tinvest/TInvestRateLimiter.kt` (новый) —
  rate-limiter (токен-бакет) с учётом лимитов по сервисам и
  общего лимита 50/сек.
- `app/src/main/kotlin/com/gitlab/biomorf/tscalp/data/api/tinvest/` —
  unary-вызовы в `TInvestPricesService` (`getLastPricesByTscalpInstrumentId`),
  `TInvestTradingService` (`getTradingStatuses`, `checkTradeAvailability`),
  `TInvestPortfolioService` (`fetchPortfolioPositions` — was `fetchPositionsSnapshot`),
  `TInvestInstrumentsService` (`fetchFullInstrument`, `findInstruments`),
  `TInvestAccountsService` (`getAccounts`, `getBalance`) — обернуть в rate-limiter.
- `app/src/main/kotlin/com/gitlab/biomorf/tscalp/data/api/tinvest/TInvestOrdersService.kt` —
  обернуть `postOrder` (15/сек), `postStopOrder`,
  `getOrders`/`getStopOrders`, `cancel*`.
- `app/src/main/kotlin/com/gitlab/biomorf/tscalp/di/DataModule.kt` —
  Hilt-провайдер `TInvestRateLimiter` как `@Singleton`.

**Шаги:**

1. **Вынести лимиты в константы** (`TInvestRateLimits` в
   `data/api/`): таблицы из `docs/rate-limits.md` — по каждому
   сервису, отдельные строки для `getOrders` и `postOrder`,
   общий лимит 50/сек.
2. **Реализовать токен-бакет** (`TInvestRateLimiter`): метод
   `suspend fun <T> withPermission(service: TInvestService, block: suspend () -> T): T`.
   Внутри — `Semaphore` или `Channel`-based limiter с
   двумя уровнями: per-service (N/мин) и глобальный (50/сек).
3. **Интегрировать** в 5 data-сервисов (`TInvestPricesService`,
   `TInvestTradingService`, `TInvestPortfolioService`,
   `TInvestInstrumentsService`, `TInvestAccountsService`) и
   `TInvestOrdersService`: обернуть каждый unary-вызов в
   `rateLimiter.withPermission(...) { broker.method() }`.
4. **Особый случай `getTradingStatuses`:** сейчас
   `coroutineScope { ids.map { async { ... } }.awaitAll() }` —
   N параллельных. С rate-limiter'ом либо (а) последовательно
   через limiter, либо (б) батчинг (если API даст метод
   batch-запроса), либо (в) ограничить concurrency — например,
   `Semaphore(10)` поверх rate-limiter'а.
5. **Опционально: `GetUserTariff`** — unary-метод, возвращает
   актуальные лимиты для пользователя. Вызывать при старте
   приложения (после `initialize`), кэшировать, использовать
   в rate-limiter вместо статических констант. Для грейдов
   4–5 лимиты выше, чем в таблице.
6. **Тесты:** unit-тест на `TInvestRateLimiter` — что N
   запросов не превышают лимит, что `postOrder` не чаще
   15/сек, что общий лимит 50/сек соблюдается.

**Референсы:**

- `docs/rate-limits.md` — официальные лимиты (полные таблицы).
- Официальная документация T-Invest:
  `https://developer.tbank.ru/invest/intro/developer/limits` —
  раздел «Лимиты запросов».
- `GetUserTariff` в proto-контракте:
  `ru.tinkoff.piapi.contract.v1.UsersService/GetUserTariff`.

**Ограничения:**

- Не менять публичные контракты `BrokerApi`. Rate-limiter —
  внутренняя деталь `TInvestBrokerAPI` и его сервисов.
- Не смешивать с задачей `stream-orchestrator` — это отдельная
  работа по data-слою, не про WS.
- Не трогать BCS / Finam — у них свои лимиты, отдельные задачи
  при интеграции.
- Держать поведение `getTradingStatuses` прежним по контракту
  (тот же `Map<uid, TradingAvailability>`), меняется только
  внутренняя реализация.
- Если rate-limiter не может дать разрешение (запрос ждёт
  слишком долго) — вернуть `AppResult.Failure` с
  `AppError.Unknown("rate limit")` или дождаться с таймаутом.
  Решение зафиксировать в KDoc.

**Критерий готовности:**

- `TInvestRateLimiter` реализован, покрыт unit-тестами.
- Все unary-вызовы в 5 data-сервисах (`TInvestPricesService`,
  `TInvestTradingService`, `TInvestPortfolioService`,
  `TInvestInstrumentsService`, `TInvestAccountsService`) и
  `TInvestOrdersService` проходят через rate-limiter.
- `getTradingStatuses` не превышает лимит `MarketData`
  (300/мин для gRPC) даже при 100 инструментах за раз.
- `postOrder` не чаще 15/сек.
- Общий лимит 50/сек с одного IP соблюдается (замер в unit-тесте).
- 63 теста зелёные, ktlint и detekt чистые.

**Оценка:** ~3–5 часов (токен-бакет 1–1.5 ч, интеграция
1–1.5 ч, `GetUserTariff` 0.5–1 ч, тесты 0.5–1 ч).

**Зависимости:** нет. **Разблокирует:** безопасный переход на
более агрессивный polling, добавление батч-операций по
инструментам, торговлю без риска `RESOURCE_EXHAUSTED`.

**Открытые вопросы:**

- Использовать `GetUserTariff` сразу или начать со статических
  лимитов и добавить динамические позже. Решить при старте.
- Как вести себя при долгом ожидании (timeout vs fail vs
  ждать) — унифицировать через `AppResult`.
- Нужен ли отдельный rate-limiter для REST-эндпоинтов T-Invest
  (REST-прокси), если они будут использоваться — у REST
  лимиты ниже (см. таблицу). Пока REST не используется,
  не актуально.




___




### CardIdentity: persist broker + account в TradingStateSnapshot

**Мобильная работа:** полное закрытие (proot + PR, гейт на CI).

**Статус:** запланировано.

**Контекст:** Фаза 1b вводит `selectedBroker` / `selectedAccountId` /
`pairedBroker` / `pairedAccountId` в `OrderFormState`. `saveState()` их
**не сохраняет**: `TradingStateSnapshot` содержит `selectedTscalpInstrumentId`
/ `pairedTscalpInstrumentId` / `pairTradingEnabled` / `quantity` /
`pairedMultiplier` / `orderType` — без broker/account. После перезапуска
слоты восстановятся с `TINVEST` / `null` — потеря счёта. Обойдённая
альтернатива Фазы 1b (см. «Обойдённые альтернативы» п.5).

**Соответствие Clean:** соответствует (domain-DTO расширяется; DataStore —
data-слой).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** Фаза 1b закрыта.

**Файлы:**
- `domain/models/TradingState.kt` — `TradingStateSnapshot` + 4 nullable
  поля (`= null`).
- `presentation/screens/orders/OrderFormViewModel.kt` — `saveState` пишет
  новые поля; `restoreOrderForm` читает.
- `domain/usecases/RestoreOrderFormUseCase.kt` — читает новые поля.
- `data/.../TradingStateRepositoryImpl` — encode/decode; новые
  DataStore-ключи (`selected_broker`, `selected_account_id`,
  `paired_broker`, `paired_account_id`).

**Шаги:**
1. `TradingStateSnapshot` +4 поля (nullable).
2. `TradingStateRepositoryImpl` — encode/decode.
3. `OrderFormViewModel.saveState` / `restoreOrderForm`.
4. Тесты: `TradingStateRepositoryImplTest` (roundtrip с broker/account);
   `RestoreOrderFormUseCaseTest` (+4 кейса).
5. Live-test: primary SBER(B) + pair SBER(A) → force-stop → запуск →
   слоты восстановлены с правильными broker/account.

**Тесты:** `TradingStateRepositoryImplTest`, `RestoreOrderFormUseCaseTest`.

**Референсы:** Фаза 1b; `docs/architecture.md` → «CardIdentity и слоты».

**Ограничения:** не смешивать с Фазой 1b (отдельный PR). DataStore — новые
ключи, legacy не трогаем.

**Критерий готовности:** broker/account слотов переживают перезапуск;
гейт зелёный; live-test PASS.

**Оценка:** ~2–3 ч.

**Зависимости:** Фаза 1b.

**Связано:** Фаза 1b; `### UI: кнопка swap слотов`.

---

### UI: кнопка swap слотов (primary ↔ pair)

**Мобильная работа:** частичное (live-тест UI).

**Статус:** запланировано. **UI — требует санкции.**

**Контекст:** по `docs/architecture.md` → «CardIdentity и слоты» α-вариант:
swap карточек между слотами возможен через временный счёт или через
удаление + пересоздание. UX-неудобно (3–4 действия). Нужна явная кнопка
swap рядом со slider «Парная торговля».

**Соответствие Clean:** соответствует.

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** Фаза 1b закрыта (composite key + слоты работают).

**Файлы:**
- `presentation/screens/orders/OrdersPairTrading.kt` — новый IconButton
  рядом со slider «Парная торговля».
- `presentation/screens/orders/OrderFormViewModel.kt` — `swapSlots()`:
  exchange `selectedInstrument` / `pairedInstrument`, `selectedBroker` /
  `pairedBroker`, `selectedAccountId` / `pairedAccountId`; `saveState()`.
- `presentation/screens/orders/OrdersScreen.kt` — передача `onSwapSlots`.

**Шаги:**
1. **UI-санкция** (правило 1): согласовать иконку, текст, положение
   относительно slider.
2. `OrderFormViewModel.swapSlots()` — атомарный swap полей слота.
3. UI-кнопка.
4. Live-test: primary SBER(A) + pair SBER(B) → swap → primary SBER(B) +
   pair SBER(A); сохранить в DataStore; после перезапуска — то же.

**Тесты:** `OrderFormViewModelTest.swapSlots` (unit).

**Референсы:** `docs/architecture.md` → «CardIdentity и слоты»; Фаза 1b.

**Ограничения:** не смешивать с Фазой 1b (отдельный PR). UI-санкция.

**Критерий готовности:** кнопка swap работает; слоты меняются местами;
переживает перезапуск; гейт зелёный; live-test PASS.

**Оценка:** ~2–3 ч (с UI-санкцией).

**Зависимости:** Фаза 1b.

**Связано:** `### Рефакторинг: tscalpInstrumentId в CardIdentity + весь domain`.




---




### KMP: domain → commonMain

**Мобильная работа:** невозможно (сборка KMP-модуля, desktop).

**Статус:** запланировано.

**Контекст:** цель — перевести `domain` в `:shared/commonMain` для KMP. Аудит 2026-10-29 (`docs/architecture.md` → «KMP readiness»): 4 блокера. Domain почти готов (repository/api/roles/log — чисты); #1 (`Instant`) и #3 (`currentTimeMillis`) закрыты в PR #73.

**Соответствие Clean:** соответствует.

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** Koin (снимает `javax.inject`), фикс #4 (`Dispatchers.IO` → инжект `CoroutineDispatcher`) — задачи ниже. #1 (`Instant`) и #3 (`currentTimeMillis`) закрыты в PR #73.

**Файлы:**
- `settings.gradle.kts` — новый модуль `:shared` (KMP).
- `shared/build.gradle.kts` — `commonMain` + `androidMain`.
- `app/build.gradle.kts` — `:app` depends on `:shared`.
- Переезд `domain/` → `shared/src/commonMain/kotlin/...`.

**Шаги:**
1. ✅ Фикс #1 (`Instant`) + #3 (`currentTimeMillis`) — закрыто (PR #73).
2. Фикс #4 (`Dispatchers.IO` → инжект `CoroutineDispatcher`).
3. Koin.
4. Создать `:shared` (KMP module): `commonMain` + `androidMain`.
5. `git mv` domain → `shared/src/commonMain/kotlin/...`.
6. `:app` depends on `:shared`; data импортирует domain из `:shared`.
7. Тесты domain → `shared/src/commonTest`.
8. Гейт.

**Тесты:** domain-тесты → `commonTest`.

**Референсы:** `docs/architecture.md` → «KMP readiness».

**Ограничения:** `data` — не в этой задаче (`androidMain` split — отдельная фаза).

**Критерий готовности:** `domain` в `:shared/commonMain`; `:app:compileDebugKotlin` зелёный.

**Оценка:** ~3–4 ч (после #4, Koin).

**Зависимости:** Koin, `### KMP: инжект CoroutineDispatcher вместо Dispatchers.IO`. #1 (`Instant`) и #3 (`currentTimeMillis`) закрыты в PR #73.

**Связано:** `### Плавный переход Hilt → Koin`, `docs/architecture.md`.

**Открытые вопросы:**
- `:shared` с Android target — `androidLibrary` или `com.android.kotlin.multiplatform.library`? Уточнить при старте.
- `domain/log/Logger` — остаётся интерфейсом (`AndroidAppLogger` в `:app/data/log`).




___




### Плавный переход Hilt → Koin

**Мобильная работа:** полное закрытие (proot + PR, гейт на CI).

**Статус:** отложено.
**Ветка:** feature/hilt-koin-inventory (последний коммит
2026-10-17 18:47, `e6b093a` — inventory-фаза).

**Причина приостановки:** приоритет отдан задачам,
разблокирующим BCS/Finam (race, REST polling). Миграция не
блокирует текущую работу; в KMP-фазу не идёт.

**Когда возобновить:** перед стартом iOS/KMP-фазы
(эта задача — предусловие).

**Контекст:** Hilt не поддерживает Kotlin Multiplatform —
Android/JVM only. Koin — основной KMP-совместимый DI,
работает на Android, iOS, JVM из одного кода. При появлении
iOS-задачи миграция станет обязательной. Сейчас — удобный
момент для плавного перехода: сосуществование Hilt и Koin
возможно через мост `koin-android-dagger`, миграция — по
модулям без остановки проекта.

**Зачем заранее:** переписывание всего DI-графа за один
раз — большая остановка. Поэтапный переход растворяется в
текущей работе, не блокирует другие задачи.

**Предусловия:**

- Задачи, затрагивающие DI-модули (`RepositoryModule`,
  `UseCaseModule`, `DataModule`), выполнены или
  остановлены.
- Koin Compiler Plugin доступен и совместим с Kotlin 2.4.20
  — проверить при старте, нужно для compile-time проверки
  графа.
- Koin 4.x или новее — уточнить актуальную версию на
  Maven Central.

**Ключевые принципы:**

- **Сосуществование до полного перехода.** Hilt и Koin
  работают параллельно через `koin-android-dagger`. Каждый
  модуль переводится отдельно.
- **Порядок «от листьев к корню».** Сначала модули без
  Hilt-специфичных зависимостей (`RepositoryModule`,
  `UseCaseModule`), потом `DataModule` (там
  `@ApplicationContext`), потом ViewModel и Activity.
- **Compile-time проверка графа.** Koin Compiler Plugin
  (требует Kotlin 2.3.20+) сохраняет проверку графа на
  этапе компиляции — как у Hilt. Рантайм-резолвинг без
  плагина — регресс, не использовать.
- **После каждого модуля — сборка и тесты.** Коммит после
  каждого переведённого модуля (правило 4b AGENTS.md).

**Фазы:**

**Фаза 1 — Инвентаризация и инфраструктура.**
- Составить список всех Hilt-модулей (`@Module`,
  `@InstallIn`), точек инъекции (`@Inject`,
  `@AndroidEntryPoint`, `@HiltViewModel`), их зависимостей.
- Добавить Koin-зависимости: `koin-android`,
  `koin-androidx-compose`, `koin-android-dagger`,
  Koin Compiler Plugin.
- В `TScalpApplication.onCreate` вызвать `startKoin` **до**
  инициализации Hilt-компонентов (иначе bridge упадёт).
- Проверка: приложение запускается, все зависимости
  разрешаются. Hilt пока управляет всем.

**Фаза 2 — Пилотный модуль (`RepositoryModule`).**
- Создать `app/di/koin/RepositoryKoinModule.kt` с
  провайдерами репозиториев.
- Для получения зависимостей из Hilt использовать
  `scope.dagger<HiltEntryPoint>().someDependency()`.
- Удалить соответствующие `@Module @InstallIn` из
  `RepositoryModule.kt`.
- Проверка: сборка, 63 теста, smoke-тест приложения.

**Фаза 3 — Постепенный перенос модулей.**
- Порядок: `UseCaseModule` → `DataModule`
  (`@ApplicationContext` → `androidContext(...)`) →
  `BrokerModule` → ViewModels → Activity.
- ViewModel: `@HiltViewModel` → `koinViewModel()` в
  Compose.
- Activity: `@AndroidEntryPoint` → `KoinComponent` или
  ручная инициализация.
- После каждого модуля: сборка + тесты + коммит.

**Фаза 4 — Удаление Hilt.**
- Проверка: `grep -r "@Hilt\|@Inject\|@Module\|@InstallIn"`
  не находит совпадений.
- Удалить зависимости: `hilt-android`,
  `hilt-android-compiler`, `hilt-navigation-compose`.
- Удалить плагин `dagger.hilt.android.plugin` из
  корневого `build.gradle.kts`.
- Финальная проверка: полная сборка, тесты, ручной прогон.

**Файлы:**

- `gradle/libs.versions.toml` или `build.gradle.kts` —
  версии Koin.
- `app/build.gradle.kts` — зависимости Koin и плагин
  compiler.
- `TScalpApplication.kt` — `startKoin` в `onCreate`.
- `app/di/koin/*.kt` (новые) — Koin-модули по мере
  перевода.
- `app/di/*.kt` (существующие) — удаление Hilt-модулей по
  мере перевода.
- ViewModel-файлы — `@HiltViewModel` → `koinViewModel`.
- `MainActivity.kt` — замена `@AndroidEntryPoint`.

**Референсы:**

- Koin Android: `https://insert-koin.io/docs/reference/koin-android/`
- Koin Compiler Plugin:
  `https://insert-koin.io/docs/reference/koin-compiler-plugin/`
- `koin-android-dagger` bridge:
  `https://insert-koin.io/docs/reference/koin-android/koin-dagger/`
- Koin для Compose Multiplatform:
  `https://insert-koin.io/docs/reference/koin-compose/`

**Ограничения:**

- Не откусывать сразу всё: один модуль — один коммит.
- Не переводить ViewModel и Activity до модулей нижнего
  уровня — они зависят от всего графа.
- Не использовать Koin без Compiler Plugin — теряется
  compile-time safety.
- Не смешивать миграцию с другими изменениями в тех же
  модулях — отдельные коммиты.
- `:transport-ktor` (если уже есть) не знает про DI —
  провайдеры живут в `:app`. Koin-модуль для него
  создаётся на фазе 2 или 3.

**Критерий готовности:**

- Hilt полностью удалён из проекта.
- Все зависимости разрешаются через Koin.
- Compile-time проверка графа работает (Compiler Plugin).
- 63 unit-теста зелёные, ktlint и detekt чистые.
- Приложение собирается и запускается на устройстве.

**Оценка:** ~8–12 часов суммарно (фаза 1: 2 ч;
фаза 2: 1–2 ч; фаза 3: 4–6 ч; фаза 4: 1–2 ч).

**Зависимости:** нет. **Разблокирует** iOS-фазу
(перенос кода в KMP-модуль без Hilt).

**Открытые вопросы:**

- Точная совместимость Koin Compiler Plugin с Kotlin
  2.4.20 и AGP 9.4.0. Проверить при старте фазы 1.
- Актуальная версия Koin на Maven Central.
- Нужен ли `koin-androidx-compose` отдельно или хватает
  `koin-compose` в текущей версии. Уточнить по
  документации.
- `startKoin` до Hilt — что именно «до»: в `onCreate`
  перед `super.onCreate()` или после? Проверить по
  документации bridge.




___




### UI: показать executedPrice / executedQuantity на карточке

**Мобильная работа:** частичное (live-тест UI).

**Статус:** запланировано. **UI — требует санкции.**

**Контекст:** после Split order (коммит `ec9c7d5`) в `Order.Regular` появились поля `executedPrice: Double?` (фактическая цена исполнения) и `executedQuantity: Long` (фактически исполненное количество, может быть меньше `quantity` при частичном исполнении). Данные приходят из мапперов (gRPC: `lotsExecuted` / `executedOrderPrice`, REST: `lotsExecuted` / `executedOrderPrice`), но `OrderCard` их не отображает. Пользователь пока не определил визуальное размещение.

**Соответствие Clean:** соответствует.

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** `### Split order domain into sealed Regular/Stop` (закрыта, `ec9c7d5`).

**Файлы:**
- `app/src/main/kotlin/com/gitlab/biomorf/tscalp/ui/components/OrderCard.kt` — новые параметры, отображение, `@Preview`.
- `app/src/main/kotlin/com/gitlab/biomorf/tscalp/ui/components/OrdersListDialog.kt` — передача полей из `Order.Regular`.

**Шаги:**
1. **Санкция на UI** (правило 1): согласовать вариант размещения (A/B/C ниже), тексты, форматирование.
2. `OrderCard`: добавить `executedPrice: Double? = null`, `executedQuantity: Long = 0L`.
3. `OrdersListDialog`: для `Order.Regular` передавать `order.executedPrice` / `order.executedQuantity`; для `Order.Stop` — `null` / `0`.
4. Реализовать отображение по выбранному варианту.
5. Обновить `@Preview` в `OrderCard`: добавить кейс частичного исполнения.
6. Гейт: полный.

**Варианты размещения (требуют санкции):**
- **A. Дополнить правую колонку.** Вместо `"$quantity лотов"` — `"$executedQuantity / $quantity лотов"` (если `executedQuantity > 0`). Под ценой — `formatPrice(executedPrice)` мелким шрифтом. Минимальное изменение, карточка не растёт.
- **B. Строка в левой колонке.** Под `status` добавить строку `"Исполнено: N лотов по X"` (только при `executedQuantity > 0`). Явно, но удлиняет карточку.
- **C. Замена при полном исполнении.** При `status == "FILL"` показывать только `executedPrice` / `executedQuantity`, скрывая исходные `price` / `quantity`. Чище визуально, но скрывает информацию о заявке.

Рекомендация: **A**.

**Тесты:** обновить `@Preview` (добавить `Regular` с `executedPrice = 151.0, executedQuantity = 5`). Unit-тестов нет (UI).

**Референсы:** `docs/ROADMAP.md` → `### Split order domain into sealed Regular/Stop` (закрыта). `docs/clean-audit.md` → Н4.

**Ограничения:** не менять модель `Order`. Не менять `OrderCard` без санкции (правило 1). Не смешивать с другими UI-задачами.

**Критерий готовности:** на карточке отображается исполненная цена и количество (для частично/полностью исполненных). Визуальное изменение согласовано. Гейт зелёный.

**Оценка:** ~1–1.5 ч (включая согласование UI).

**Зависимости:** нет.

**Связано:** `### Split order domain into sealed Regular/Stop`; `docs/clean-audit.md` → Н4; `docs/ROADMAP.md` → `### Split order domain into sealed Regular/Stop`.

**Открытые вопросы:** какой вариант размещения (A/B/C)? Показывать ли при `executedQuantity == 0`? Нужно ли отображать для `Order.Stop` (сейчас полей нет — только `null`/`0`)?




___




### UI-индикатор состояния стримов

**Статус:** запланировано. **UI — требует санкции.**

**Контекст:** пользователь не может оценить на глаз,
актуальны ли данные. Индикатор снимает вопрос
«это задержка или данные устарели».

**Предусловия:** `StreamState` реализован во всех трёх
менеджерах (факт), но задача StreamState **не закрыта** —
deferred closure, ждёт фикса debug-тумблеров и повторного
live-теста. UI-индикатор блокируется **закрытием** StreamState,
не реализацией.

**Файлы:**
- `presentation/.../MainScreen.kt` (или где определён NavBar) —
  бейдж-точка на иконке вкладки.
- `presentation/.../SettingsScreen.kt` — раздел «Диагностика».
- `presentation/.../MainViewModel.kt` (или соответствующие
  ViewModels) — проброс `StreamState` в UI.

**UI-описание:**

- **Бейдж-точка** на иконке вкладки:
  - Заявки → `PriceFlowManager.connectionState`;
  - Портфель → `PositionFlowManager.connectionState`;
  - POLLING → 🟡 (`MaterialTheme.colorScheme.tertiary`);
  - STALE → 🔴 (`MaterialTheme.colorScheme.error`);
  - LIVE / INITIALIZING / STOPPED → **бейджа нет** (тишина = норма).
- **Snackbar** при переходе в деградацию:
  - debounce 3 секунды;
  - тексты: см. п. «Тексты» ниже;
  - восстановление в LIVE — без Snackbar.
- **Раздел «Диагностика»** в Настройках:
  - текущее состояние обоих стримов;
  - время последнего обновления;
  - источник (gRPC/REST/WS);
  - расшифровка цветов бейджа.

**Тексты:**
- POLLING (цены): «Стрим цен: резервный режим. Обновление раз в 5 секунд.»
- POLLING (позиции): «Стрим позиций: резервный режим. Обновление раз в 10 секунд.»
- STALE (цены): «Стрим цен: нет данных. Проверьте соединение.»
- STALE (позиции): «Стрим позиций: нет данных. Проверьте соединение.»

**Ограничения:**
- **Не менять** цвет текста NavBar — конфликт с семантикой
  selected/unselected.
- **Не делать** long-press на вкладке — не discoverable,
  конфликт с системным long-press. Подсказка доступна через
  раздел «Диагностика».

**Критерий готовности:**
- При переходе стрима в POLLING появляется жёлтая точка
  и один Snackbar (не повторяется каждые N секунд).
- При STALE — красная точка и Snackbar.
- Раздел «Диагностика» показывает актуальное состояние.

**Оценка:** ~2 часа.

**Зависимости:** блокируется StreamState.




___




### UI: отображение blocked на валютной карточке

**Мобильная работа:** частичное (live-тест UI).

**Статус:** запланировано. **UI — требует санкции.**
Сейчас задача — план, не реализация: как именно показывать
`blocked` на карточке, не решено.

**Контекст:** валютная позиция (ticker `RUB000UTSTOM`)
отображается обычной `AssetPositionCard`. После WS-интеграции
в `PositionStreamItem` появились `money` (свободные) и
`blocked` (заблокированные) — но до UI они не доходят,
`PortfolioPosition` этих полей не имеет, а `toPortfolioPosition()`
их отбрасывает. Пользователь видит только `quantity` (в
polling — рубли), без разбивки на свободные/заблокированные.

**Предусловия:**
- WS-интеграция money/blocked в `PositionFlowManager`
  закрыта (сделано, коммит `0167c03`).
- Формула связи `money`, `blocked`, `quantity` — не
  подтверждена (`docs/ROADMAP.md` → «Оркестратор стримов
  и Ktor WS transport»). До выяснения показывать `money`
  и `blocked` как **отдельные значения**, а не производные.

**Файлы:**
- `app/src/main/kotlin/.../domain/models/Portfolio.kt` —
  добавить `money: Double? = null`, `blocked: Double? = null`
  в `PortfolioPosition`; пробросить в
  `PositionStreamItem.toPortfolioPosition()`.
- `app/src/main/kotlin/.../domain/models/Portfolio.kt` —
  `PortfolioPosition.mergeWith` (копирование полей при обновлении,
  `old.copy(...)` не переносит `money`/`blocked`; вызывается из
  `PortfolioViewModel:242` и `OrdersViewModel:1081`).
  `PortfolioViewModel.updatePortfolioItem` — посредник, правок
  не требует.
- `app/src/main/kotlin/.../ui/components/AssetPositionCard.kt` —
  отображение (варианты ниже).
- `app/src/main/kotlin/.../presentation/screens/portfolio/PortfolioScreen.kt` —
  проброс параметров, если добавляются отдельными аргументами.

**Варианты отображения (требуют санкции):**

- **A. Дополнительная строка в карточке.** Под текущей суммой
  — «Свободно: X ₽ · Заблокировано: Y ₽». Появляется только
  для `instrumentType == "currency"` и при непустом
  `blocked`. Минимум правок, явно.
- **B. Развёрнутая карточка по тапу.** Обычный вид —
  `quantity`; по тапу раскрывается детализация с `money` /
  `blocked`. Не засоряет список, но требует состояния
  «раскрыта» в `AssetPositionCard` (сейчас такого нет).
- **C. Badge с суммой блокировки.** Маленький бейдж в углу
  карточки при `blocked != null && blocked > 0`. Минимум
  текста, но неочевидно без подсказки.

Рекомендация: **A**. Самое прозрачное, без новых состояний
в composable, легко тестируется превью.

**Шаги:**
1. Добавить `money`/`blocked` в `PortfolioPosition` (domain).
2. Пробросить в `PositionStreamItem.toPortfolioPosition()`.
3. Обновить `PortfolioPosition.mergeWith` (domain) — при
   обновлении переносить `money`/`blocked` (по аналогии с
   `expectedYield`). `updatePortfolioItem` не трогать: он
   делегирует в `mergeWith` / `toPortfolioPosition`.
4. **Санкция на UI** — согласовать вариант (A/B/C) и тексты.
5. Реализовать отображение в `AssetPositionCard`.
6. Compose Preview для трёх состояний: `blocked == null`,
   `blocked == 0.0`, `blocked > 0.0`.
7. Unit-тест на `toPortfolioPosition()` (money/blocked
   доходят).
8. Сборка, 84+ теста зелёные, ручная проверка на устройстве.

**Референсы:**
- `docs/ROADMAP.md` → «Оркестратор стримов и Ktor WS transport»
  (фаза 2, money/blocked merge).
- `docs/ISSUES.md` → «PositionsStream over WS не отдаёт
  данные в песочнице» (prod-only поведение).
- `TInvestPositionsWsMapperTest`, `PositionFlowManagerTest` —
  образец тестов на доменный merge.

**Ограничения:**
- Не менять UI без санкции (правило 1 AGENTS.md) — вариант
  и тексты согласуются до правки.
- Не показывать `blocked` для не-валютных инструментов.
- Не выводить `money + blocked` как «итог», пока формула
  не подтверждена — только отдельные значения.
- Не трогать `OrdersScreen` — карточка там используется в
  контексте заявок, к заблокированным средствам отношения
  не имеет.

**Критерий готовности:**
- `PortfolioPosition.money` / `.blocked` заполняются из
  WS-события.
- На карточке `RUB000UTSTOM` видно `blocked` (по варианту,
  согласованному с пользователем).
- Для не-валютных инструментов внешний вид не изменился.
- Тесты на `toPortfolioPosition` добавлены.
- Сборка и тесты зелёные.

**Оценка:** ~2–2.5 часа (domain: `PortfolioPosition` + 2 функции
в `Portfolio.kt` — 30 мин; UI — 1 ч; превью+тесты — 1 ч).

**Зависимости:** нет. **Блокирует** отображение полной
картины валютной позиции (свободные + заблокированные).

**Открытые вопросы:**
- Как именно пользователь хочет видеть `blocked`? Вариант
  A/B/C — нужно решение.
- Формула `money` ↔ `blocked` ↔ `quantity` — проверить на
  реальном счёте с ненулевым `blocked`. До этого показывать
  раздельно.
- Показывать ли `money` отдельно от `quantity`, или
  заменить текущее значение? Зависит от формулы.
- Нужно ли отображать `blocked` в `OrdersScreen` (карточка
  позиции в контексте заявки) — сейчас нет, но при
  блокировке инструмента биржей может быть полезно.




___




### UI выбора даты истечения стоп-заявки (GOOD_TILL_DATE)

**Мобильная работа:** частичное (live-тест UI).

**Статус:** запланировано. **UI — требует санкции.**

**Контекст:** `GOOD_TILL_DATE` в домене и proto есть, но
пользователь физически не может его выбрать — UI всегда
отправляет `GOOD_TILL_CANCEL` (`OrdersUiState.expirationType`
дефолт, DatePicker'а нет).

Диагностика 2026-10-22 показала: цепочка `expireDate` рвётся
в **четырёх** местах, а не в одном `parseDate`.
Восстановление только backend'а (`parseDate` + `setExpireDate`)
эффекта не даст — `expireDate` из UI не приходит. Это не
«потерянная логика», а **нереализованная UI-фича**.

**Цепочка (что сломано):**
1. **UI (`OrdersScreen.kt`)** — нет поля выбора даты
   (ни DatePicker, ни текстового ввода). `expirationType`
   не переключается с `GOOD_TILL_CANCEL`.
2. **`OrdersUiState`** — поле `expireDate: String?` есть,
   но UI его не заполняет. `expirationType` фиксирован.
3. **`OrdersViewModel.postOrder`** — не передаёт
   `state.expireDate` в `PrepareOrderRequestUseCase.prepare`.
4. **`PrepareOrderRequestUseCase.prepare`** — нет параметра
   `expireDate`, `StopOrderRequest.expireDate` всегда `null`.
5. **`TInvestOrdersService.postStopOrder`** — вызов
   `builder.setExpireDate(parseDate(...))` закомментирован.
6. **`parseDate`** — закомментирован, в исходной версии
   возвращал пустой `Timestamp` (ничего не парсил).

**Что уже есть:** домен `StopOrderRequest.expireDate: String?`,
enum `StopOrderExpirationType { GOOD_TILL_CANCEL,
GOOD_TILL_DATE }`, proto-поле `PostStopOrderRequest.expire_date`
(`com.google.protobuf.Timestamp`).

**Файлы:**
- `presentation/screens/orders/OrdersScreen.kt` — UI:
  DatePicker или текстовое поле.
- `presentation/screens/orders/OrdersUiState.kt` — модель
  (уже есть, не трогать).
- `presentation/screens/orders/OrdersViewModel.kt` — проброс
  `expireDate` в use case.
- `domain/usecases/PrepareOrderRequestUseCase.kt` — параметр
  `expireDate`, проброс в `StopOrderRequest`.
- `data/api/tinvest/TInvestOrdersService.kt` — восстановить
  `parseDate` + `builder.setExpireDate(...)`.

**Шаги:**
1. **Санкция на UI** (правило 1 AGENTS.md): согласовать
   формат выбора даты (DatePicker vs текстовое поле) и
   тексты.
2. UI: добавить поле выбора даты + переключатель
   `expirationType`. Показывать дату только при
   `GOOD_TILL_DATE`.
3. ViewModel: прокинуть `state.expireDate` в
   `prepareOrderRequest.prepare(...)`.
4. UseCase: добавить параметр `expireDate: String?`,
   пробросить в `StopOrderRequest`.
5. Service: реализовать `parseDate` — формат из UI →
   `com.google.protobuf.Timestamp` (seconds + nanos).
   Восстановить `builder.setExpireDate(...)` при
   `GOOD_TILL_DATE`. Ошибка парсинга → `AppResult.Failure`.
6. Тесты: `parseDate` (валидный формат, невалидный → null),
   `prepare` (expireDate пробрасывается).
7. Live-тест: stop-заявка с `GOOD_TILL_DATE` создаётся
   и принимается биржей.

**Референсы:**
- Документация T-Invest API → PostStopOrder → `expire_date`.
- `com.google.protobuf.Timestamp` (seconds, nanos).
- Текущее состояние секции — в git-истории (первоначальная
  неверная постановка «потерянная логика»).

**Ограничения:**
- UI-изменение требует санкции (правило 1 AGENTS.md).
- Не менять публичный контракт `StopOrderRequest` без
  миграции вызывающих.
- Не смешивать с «Split order domain into sealed
  Regular/Stop» — `StopOrderRequest` там не трогается.
- Формат `expireDate` в домене — `String?` (ISO-8601 или
  дата без времени — решить при санкции). После парсинга
  в `Timestamp` — UTC.

**Критерий готовности:**
- В UI можно выбрать `GOOD_TILL_DATE` и дату.
- Stop-заявка с датой создаётся и принимается биржей.
- `expireDate` в `PostStopOrderRequest` совпадает с UI.
- Ошибка парсинга → `AppResult.Failure` с понятным
  сообщением (не падение).
- Покрыто тестами.

**Оценка:** ~2–3 часа (санкция на UI 10 мин, UI 1 ч,
ViewModel+UseCase 30 мин, Service+parseDate 30 мин,
тесты 30 мин, live-тест 30 мин).

**Зависимости:** нет. **Разблокирует:** пользовательский
сценарий `GOOD_TILL_DATE` (сейчас недоступен).

**Открытые вопросы:**
- Формат выбора: DatePicker (с ограничениями диапазона?) или
  текстовое поле (ISO-8601)? Решить на санкции.
- Ограничения T-Invest на `expire_date` — минимальная /
  максимальная дата? Проверить в документации.
- Смена типа с `GOOD_TILL_DATE` на `GOOD_TILL_CANCEL` —
  очищать `expireDate` или оставлять?




___




### Индикатор доступности инструмента в карточке позиции

**Файлы:**
- `ui/components/AssetPositionCard.kt` — параметр
  `tradingAvailability` объявлен, но не используется.

**Контекст:** данные передаются из UI (`PortfolioScreen`,
`OrdersScreen`), но визуальный индикатор не реализован.
UI-изменение требует санкции (правило 1 AGENTS.md).

**Пути решения:** добавить в карточку бейдж или цветовой
индикатор статуса. Тексты и иконки согласовать заранее.

**Оценка:** ~1 час (после санкции на UI).

**Когда вернуться:** при следующей итерации по UI портфеля.




___




### P&L: client-side fallback для PortfolioPosition (низкий приоритет)

**Мобильная работа:** частичное (live-тест данных).

**Статус:** запланировано.

**Контекст:** сейчас `PortfolioPosition.profit = expectedYield` (серверный P&L от брокера). Если сервер не прислал `expectedYield` (изменение API, bug, первый partial-event до polling), `profit = null`, UI показывает прочерк. Клиентский расчёт `(currentPrice - averagePrice) * quantity * pointValue` мог бы дать ненулевую цифру.

`PositionFlowManager.enrichAndCache` уже подставляет `expectedYield` из кеша для partial-событий, когда polling пришёл. Но если сервер не прислал его в самом polling — fallback отсутствует.

**Соответствие Clean:** соответствует.

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** нет.

**Файлы:**
- `domain/models/Portfolio.kt` — новая `fun PositionStreamItem.clientProfit(): Double?` (pure); использовать в `toPortfolioPosition()` и `mergeWith()` как fallback.
- `app/src/test/kotlin/com/gitlab/biomorf/tscalp/domain/models/PortfolioTest.kt` (новый) — unit-тесты.

**Шаги:**
1. Реализовать `fun PositionStreamItem.clientProfit(): Double?`: `val avg = averagePositionPrice ?: return null`; `val price = currentPrice ?: return null`; `if (avg <= 0.0 || quantity == 0L) return null`; `val point = pointValue?.takeIf { it > 0.0 } ?: 1.0`; `return (price - avg) * quantity * point`.
2. `toPortfolioPosition()`: `val profitValue = expectedYield ?: clientProfit()`; `profit = profitValue`, `profitPercent = calculateProfitPercent(profitValue, ...)`.
3. `mergeWith()`: то же (не затирать `profit` в null, если `expectedYield == null`, но `clientProfit()` возвращает значение).
4. Тесты (5–6 кейсов): server (non-null), client (expectedYield == null), futures (pointValue), avgPrice == null -> null, quantity == 0 -> null, убыток (negative client).
5. Гейт: `testDebugUnitTest / lintDebug / ktlintCheck / detekt`.

**Тесты:** 5–6 unit-кейсов.

**Референсы:**
- Закрытая `### ✅ Рефакторинг расчёта P&L для PortfolioPosition`.
- `data/api/tinvest/TInvestPortfolioService.kt` — passthrough
  `expectedYield` в `fetchPortfolioPositions`.
- `domain/api/managers/PositionFlowManager.enrichAndCache` — заполнение из кеша.

**Ограничения:**
- Не менять поведение, когда `expectedYield != null` — серверный приоритет.
- Не менять модель `PortfolioPosition` (только вычисление `profit`).
- Не трогать `profitPercent` — унифицирован (`calculateProfitPercent`).
- Fallback только при `expectedYield == null` (не при `0.0`).

**Критерий готовности:**
- `expectedYield == null && avgPrice != null && currentPrice != null` -> profit клиентски.
- `expectedYield != null` -> server (поведение 1:1).
- Тесты 5–6, гейт зелёный.

**Оценка:** ~1.5 ч.

**Зависимости:** нет.

**Связано:** закрытая `### ✅ Рефакторинг расчёта P&L для PortfolioPosition`.

**Открытые вопросы:**
- Если `expectedYield != null`, но клиентский расчёт расходится > 5% — логировать для аналитики? Отложено.
- Если `averagePrice` устарел — проверка «свежести» отдельная задача.




___




### Currency format: normalize ISO 4217 case

**Мобильная работа:** полное закрытие (proot + PR, гейт на CI).

**Статус:** запланировано.

**Контекст:** `formatCurrency(value, currency)` в `presentation/util/CurrencyFormat.kt` использует `Currency.getInstance(currency)` без нормализации регистра. ISO 4217 требует uppercase (`"RUB"`, `"USD"`), `Currency.getInstance("rub")` бросает `IllegalArgumentException`. `DOMESTIC_CURRENCY = "RUB"` — uppercase, поэтому fallback работает; но `Instrument.currency` приходит от брокера «как есть» (T-Invest/BCS/Finam могут отдавать lowercase — не проверено live). Три callsite в presentation передают `Instrument.currency` напрямую: `OrdersViewModel:1101` (`selectedInstrument.currency`), `OrdersViewModel:1115` (`pairedInstrument.currency`), `AssetPositionCard:215` (`position.instrument.currency`). Дефект латентный: на T-Invest RUB — uppercase, но для USD/EUR и для BCS/Finam может проявиться. Pre-existing (старый код тоже передавал `position.currency` как есть).

**Соответствие Clean:** соответствует (правка в presentation-слое, dependency rule не затронута).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** нет.

**Файлы:**
- `presentation/util/CurrencyFormat.kt` — нормализация в `formatCurrency` (или приватный helper).
- `presentation/util/CurrencyFormatTest.kt` (новый) — unit-тесты.

**Шаги:**
1. `formatCurrency` — нормализовать `currency` перед `Currency.getInstance(...)`: `currency.uppercase(Locale.ROOT)` (или `takeIf { it.isNotBlank() }?.uppercase(...) ?: DOMESTIC_CURRENCY` при `null`/пусто — сейчас `formatCurrency` принимает non-null `String`, так что fallback не нужен, только uppercase).
2. `formatPrice` — проброс `currency` в `formatCurrency` без изменений (uppercase делается внутри `formatCurrency`).
3. Тест `CurrencyFormatTest`: `formatCurrency(1234.5, "rub")` не падает; результат совпадает с `formatCurrency(1234.5, "RUB")`; `formatCurrency(1234.5, "usd")` — совпадает с `"USD"`.
4. Гейт.

**Тесты:**
- `CurrencyFormatTest` (новый, ~4 теста): lowercase → не падает; mixed case → не падает; uppercase → как раньше; сравнение результатов `"rub"` vs `"RUB"`.
- Существующие `PositionFormatterTest` — не меняются (`formatAverage` / `formatProfit` / `formatCurrentPrice` передают `currency` дальше).
- Live — не требуется (unit-тест достаточен, поведение детерминировано).

**Референсы:**
- `java.util.Currency.getInstance(String)` — ISO 4217, uppercase.
- `java.util.Locale.ROOT` — для `uppercase` без locale-зависимости (турецкая `i` → `İ` — не наш кейс, но `Locale.ROOT` — правило).
- Pre-existing контекст: PR #90 (H5) — при миграции `AssetPositionCard` / `PositionFormatter` на `Position` callsite не изменился.

**Ограничения:**
- Не менять сигнатуру `formatCurrency(value, currency)` — это публичный API presentation-util, используется в `OrdersViewModel`, `AssetPositionCard`, `PositionFormatter`.
- Не менять `DOMESTIC_CURRENCY`.
- Не нормализовать `Instrument.currency` в domain — там значение от брокера (честно отражает формат), нормализация — presentation-забота.
- UI-изменение: результаты форматирования не меняются для uppercase (`"RUB"` / `"USD"`), меняются только для lowercase-входа (раньше — crash, теперь — корректный формат). UI-санкция не нужна (это баг-фикс, не изменение внешнего вида).

**Критерий готовности:**
- `formatCurrency(1234.5, "rub")` — не бросает `IllegalArgumentException`.
- `formatCurrency(1234.5, "rub") == formatCurrency(1234.5, "RUB")`.
- `formatCurrency(1234.5, "usd") == formatCurrency(1234.5, "USD")`.
- Гейт зелёный (`testDebugUnitTest / lintDebug / ktlintCheck / detekt`).

**Оценка:** ~30–45 мин (правка 5 мин, тесты 15 мин, гейт 10 мин).

**Зависимости:** нет.

**Связано:** `docs/ISSUES.md` → «Currency format: lowercase iso-code в `Currency.getInstance`»; `docs/clean-audit.md` → Н5 (не пересекается, контекст PR #90, где вскрылось).

**Открытые вопросы:**
- Какие регистры `Instrument.currency` реально приходят от T-Invest / BCS / Finam — не проверено live. Unit-тест покрывает оба варианта; live — при следующей сессии с реальным USD-инструментом.
- Если `currency` придёт пустой `""` — `Currency.getInstance("")` тоже бросает IAE. Стоит ли fallback на `DOMESTIC_CURRENCY` при `isBlank()`? **Дефолт:** нет (пустая строка — невалидный вход, лучше упасть громко). Пересмотреть при появлении реального кейса.




___




#### Классификатор ошибок

**Файлы:**
- `util/AppResultExtensions.kt` — `Throwable.toAppError`
  (66 строк, complexity 26).

**Пути решения:** разбить на per-broker обработчики
(`toTInvestError`, `toBcsError`, `toFinamError`) с
диспетчеризацией по `BrokerName`.

**Оценка:** ~1 час.




___




### Полировка debug-переключателей

**Мобильная работа:** частичное (live-тест 8 комбинаций тумблеров).

**Статус:** запланировано.

**Контекст:** в рамках задачи «Ktor REST transport для polling»
добавлены три независимых тумблера (gRPC, WS, REST) для
динамической проверки транспортов. Проверены базовые сценарии
(bypass REST, отключение gRPC/WS). Однако не проверены все 8
возможных комбинаций, особенно при резких переключениях
(race conditions, корректность жизненного цикла корутин,
залипание состояний).

**Триггер:** обнаружение аномалий (зависания, лишние
переподключения, дубли логов) в следующих задачах (например,
`GrpcExceptionGuard`). **Триггер сработал (2026-10-22):** дубли
подключений (gRPC и WS) в logcat при старте приложения — см.
`docs/ISSUES.md` → «Race в start/stop *FlowManager —
дублирование стримов».

**Файлы:**
- `presentation/screens/settings/SettingsViewModel.kt`
- `presentation/screens/settings/SettingsScreen.kt`
- `domain/api/PriceFlowManager.kt`
- `domain/api/PositionFlowManager.kt`

**Шаги:**
1. Прогнать все 8 комбинаций тумблеров.
2. Исправить найденные race conditions.
3. Добавить сохранение состояния тумблеров в DataStore
   (опционально).

**Находка из live-теста 2026-10-22 (PR #35,
`settingsvm-duplicate-init`):** проверка `forceRest` требует
**непустого union** цен — быть на экране «Заявки» или
«Портфель», не в «Настройках». Иначе
`PriceFlowManager.restart(emptySet())` ничего не запускает,
ветка `forceRest` не проверяется (в логе — `Union empty,
gRPC stream stopped`). Покрыто отдельно в live-тесте PR #34
(`pricesSnapshot: forceRest=true` → `TInvestRest: POST
.../GetLastPrices` → `[manager/polling]`). При прогоне
8 комбинаций — учитывать: сначала открыть экран с
активными ценами, потом переключать.

**Оценка:** ~1–2 часа.

**Зависимости:** нет. Разблокирована — фикс race в
`reapplyDebugFlags` в `dev` (PR #34). См.
`docs/ROADMAP.md` → закрытая «Race в start/stop `*FlowManager`».




___




### Реализация BCS Broker API

**Мобильная работа:** частичное (live-тест BCS API).

**Статус:** запланировано.

**Контекст:** `BcsBrokerApi` — заглушка. Из 25 методов работают 3-4: `getAccounts` (HTTP GET к портфелю), `postOrder` (HTTP POST). Остальные — `emptyList`, `UnsupportedOperationException`, no-op. `userInstruments = null`, `streams = null`, `sandbox = null`, `lifecycle` — есть. BCS REST + WebSocket — отдельная история (docs `https://trade-api.bcs.ru/`). Закрывает §8 шаг 0.2 (BCS mapping subtype-полей: `emissionDate` ↔ `ipoDate`, `subType` ↔ `shareType`, `baseAssetFuture` ↔ `futuresType`, `faceValue` ↔ `nominal`, `dividendYield` vs `divYieldFlag`, `liquidityRating` vs `liquidityFlag` — 6 `(?)` в `docs/domain_models.md` §9).

**Соответствие Clean:** соответствует.

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** нет.

**Файлы:**
- `data/api/bcs/BcsBrokerApi.kt` — реализация.
- `data/api/bcs/BcsModels.kt` (новый) — DTO для JSON-ответов BCS.
- `data/api/bcs/BcsMappers.kt` (новый) — конвертеры JSON ↔ domain.
- `docs/domain_models.md` §9 — снять `(?)` для BCS-колонки.
- `docs/domain_models.md` §10 — закрыть или обновить открытые вопросы.

**Шаги:**
1. Изучить BCS API: базовый URL (`https://trade-api.bcs.ru/`), авторизация (OAuth2 через Keycloak, refresh-token 90 дней), REST-эндпоинты, WS (`wss://ws.broker.ru/trade-api-*/api/v1/.../ws`).
2. Реализовать `getAccounts`, `fetchPortfolioPositions`, `getBalance`, `findInstruments`, `getTradingStatuses`.
3. Реализовать `postOrder` (уже есть), `postStopOrder`, `getOrders`, `getStopOrders`, `cancelOrder`, `cancelStopOrder` — реально или `null` (по nullable-val).
4. Реализовать `userInstruments` (если BCS даёт поиск — `findInstruments` + `getTradingStatuses`).
5. Реализовать `streams` (BCS WS: котировки, статус заявок, лимиты). BCS WS — `type` + payload.
6. BCS mapping subtype-полей (6 `(?)` в §9).
7. Закрыть §8 шаг 0.2.

**Тесты:** unit-тесты мапперов (моки OkHttp/JSON). Live-тест на реальном BCS-счёте (согласование с руководителем).

**Референсы:** `docs/brokers.md` → BCS; `https://trade-api.bcs.ru/`; `https://trade-api.bcs.ru/http/information`; `### Реализация Finam Broker API` (образец).

**Ограничения:**
- Не менять `BcsBrokerApi` публичный контракт — только реализация.
- `BcsBrokerApi.orders = null` → **не** ставить supertype `OrdersApi`, если не все 6 методов работают (nullable-val правило).
- `sandbox = null` у BCS не будет по природе брокера (нет песочницы).

**Критерий готовности:**
- `BcsBrokerApi` реализует `AccountsApi`, `PortfolioApi`, `MarketDataApi`, `UserInstrumentsApi`, `StreamsApi`, `OrdersApi` (кроме неработающих — `null`).
- BCS-mapping subtype-полей: 0 `(?)` в BCS-колонке §9.
- §8 шаг 0.2 — `[x]`.
- Live-тест PASS.

**Оценка:** ~15–20 ч (зависит от BCS API и сложности WS).

**Зависимости:** нет.

**Связано:** `### Реализация Finam Broker API` (образец); `docs/domain_models.md` §8 шаг 0.2; `### Пакет C: TInvestMarketDataService split` (образец data-слоя).

**Открытые вопросы:** BCS WS `type` + payload формат — по `https://trade-api.bcs.ru/websocket/portfolio/`; при реализации подтвердить.




___




### Реализация Finam Broker API

**Статус:** запланировано.

**Контекст:** `FinamBrokerApi` — заглушка. Все методы
`BrokerApi` либо возвращают пустые значения (`emptyList`,
`emptyMap`, `0.0`), либо бросают `NotImplementedError` через
`TODO("Not yet implemented")`. Брокер зарегистрирован
в `DataModule` наравне с T-Invest и BCS, но фактически
не работает.

**Предусловия:**
- Доступ к Finam API (документация, тестовый токен).
- Завершена задача «Оркестратор стримов и Ktor WS transport» —
  `:transport-ktor` даёт HTTP-клиент для REST и WS.
  У Finam есть gRPC, WS и REST — все три транспорта,
  как у T-Invest.

**Файлы:**
- `data/api/finam/FinamBrokerApi.kt` — основная реализация.
- `util/FinamErrorMessages.kt` — расшифровка кодов ошибок
  (может не существовать, см. ROADMAP → «Расшифровка
  кодов ошибок брокеров»).
- `data/api/finam/FinamModels.kt` (новый) — data-классы для
  JSON-ответов API.
- `data/api/finam/FinamConverters.kt` (новый) — конвертеры
  JSON ↔ доменные модели.

**Шаги:**

1. Изучить документацию Finam API: базовый URL
   (боевой и sandbox), авторизация, формат запросов
   и ответов.
2. Реализовать `getAccounts(sandboxMode)`.
3. Реализовать `fetchPortfolioPositions(accountId, sandboxMode)`
   (was `fetchPositionsSnapshot`).
4. Реализовать `getBalance(accountId)`.
5. Реализовать `findInstruments(query)`.
6. Реализовать `postOrder` / `postStopOrder` /
   `cancelOrder` / `cancelStopOrder`.
7. Реализовать `getOrders` / `getStopOrders`.
8. Реализовать `getLastPricesByTscalpInstrumentId`.
9. Реализовать `getTradingStatuses`.
10. Реализовать стримы (`subscribePositions`,
    `subscribeOrderState`, `subscribeLastPrices`):
    gRPC-stream — primary, WS — fallback, REST — polling.
    Композиция через `FinamOrchestrator` по образцу
    `TInvestOrchestrator`.
11. Заполнить `FinamErrorMessages.kt` по мере
    появления кодов ошибок.
12. Покрыть unit-тестами (моки OkHttp).

**Референсы:**
- Официальная документация Finam API:
    - gRPC: https://api.finam.ru/docs/grpc/#introduction
    - WebSocket (AsyncAPI): https://api.finam.ru/docs/async-api/
    - REST: https://api.finam.ru/docs/rest/
- Существующая реализация `BcsBrokerApi` — образец
  REST-клиента.
- `TInvestOrchestrator` — образец композиции транспортов
  с fallback'ами.

**Ограничения:**
- **Не оставлять `TODO("Not yet implemented")`** в коде,
  который может быть вызван: заменять на явный
  `AppResult.Failure` или `emptyList` с KDoc, если операция
  не поддерживается.
- Логирование — через `AppLogger`, не `printStackTrace`.
- Идентификаторы инструментов — через `tscalpInstrumentId`
  в domain-слое (см. AGENTS.md).

**Критерий готовности:**
- Все методы `BrokerApi` реализованы через REST-запросы
  к Finam API.
- `TODO("Not yet implemented")` в `FinamBrokerApi.kt`
  отсутствуют.
- В UI Finam выбирается наравне с T-Invest и BCS,
  выполняет базовые операции (счёт, портфель, заявка).

**Оценка:** ~8–12 часов (зависит от документации
и наличия тестового доступа).

**Зависимости:** нет. Блокирует полноценное использование
Finam в UI.




___



### Live-test Broker lifecycle API: BCS/Finam

**Мобильная работа:** невозможно (нужен девайс/эмулятор + adb).

**Статус:** отложено.

**Контекст:** `### Broker lifecycle API` (#2 umbrella, закрыт
2026-10-25) прошёл live-тест только по TInvest. BCS/Finam lifecycle
не проверены: оба брокера — заглушки (`getAccounts`/`getOrders`/
etc.), стримы не реализованы. Тест сейчас вернул бы «initialize:
success, стримы не идут» — не проверка, а констатация известного
состояния. Defer по 2d — временное решение с задачей на полное
закрытие.

**Предусловия:**
- `### Реализация BCS Broker API` закрыта.
- `### Реализация Finam Broker API` закрыта.

**Что делать:**

Повторить процедуру live-теста из `### Broker lifecycle API`
(`docs/live-test.md`, режим B) для BCS/Finam:

1. **Login BCS** через `SettingsViewModel.initializeBcs` —
   `lifecycle.initialize(BrokerCredentials.Bcs)` →
   `isInitialized = true`, стримы поднимаются, `initialize:
   success` в логе.
2. **Login Finam** через `SettingsViewModel.initializeFinam` —
   `lifecycle.initialize(BrokerCredentials.Finam)` →
   `isInitialized = true`, стримы поднимаются.
3. **Logout BCS / Finam** —
   `clearBrokerCredentials: <broker> deinitialized`,
   `isInitialized = false`.
4. **Restart приложения** с сохранёнными credentials —
   `restoreBcs` восстанавливает BCS, Finam — через UI (Finam
   сейчас в `restoreBrokerConnection` — `Unit`, требует
   `initialize` из UI, см. `TScalpApplication.restoreBrokerConnection`).

**Файлы:** нет правок в коде. Только лог (`~/lifecycle-live-bcs-finam.log`).

**Шаги:** без правок кода — только запуск и анализ лога по
процедуре `docs/live-test.md`.

**Референсы:**
- `docs/live-test.md` — процедура, фильтры logcat, режим B.
- `### Broker lifecycle API` (закрыт) — TInvest-only live-тест,
  образец.

**Ограничения:**
- Не менять код — только тест. Если при тесте обнаружится
  регрессия в lifecycle — отдельная задача/фикс.
- Не смешивать с задачей `### Реализация Finam Broker API` —
  там реализация, здесь верификация lifecycle.

**Критерий готовности:**
- Login / logout / restart для BCS и Finam проходят по логу
  (сценарии 1–4 выше).
- `isInitialized` переключается корректно.
- Красных флагов в логе нет.

**Оценка:** ~30 мин.

**Зависимости:**
- `### Реализация BCS Broker API`
- `### Реализация Finam Broker API`

**Связано:**
- `### Broker lifecycle API` (закрыт 2026-10-25, TInvest-only).
- `### Реализация BCS Broker API`, `### Реализация Finam Broker API`.

**Открытые вопросы:** нет.




___




### Установка GrpcExceptionGuard

**Мобильная работа:** частичное (live-тест на устройстве).

**Статус:** в работе.
**Подтверждение:** ожидает (PR #33 merged, ветка удалена;
AppMetrica, 2–3 дня после релиза; критерий: нет новых fatal
`io.grpc.StatusException`, появились non-fatal
`"Unhandled gRPC"`; детали — «Критерий закрытия» ниже).
**Триггер сработал (2026-10-21):** AppMetrica зафиксировала
краш `io.grpc.StatusException`.

**Контекст:** `io.grpc.kotlin.ClientCalls` при ошибке канала
доставляет `StatusException` через поздний callback в уже
завершённую suspend-корутину. Доставить некому → исключение
уходит в `Thread.getDefaultUncaughtExceptionHandler()` →
kill process. `CoroutineExceptionHandler` и `.catch { }` не
помогают.

**Файлы:**
- `app/src/main/kotlin/.../TScalpApplication.kt` — установка
  глобального `UncaughtExceptionHandler`.

**Шаги:**
1. В `TScalpApplication.onCreate` обернуть оригинальный
   `Thread.getDefaultUncaughtExceptionHandler()`.
2. Если `throwable is StatusException || throwable is StatusRuntimeException`:
    - логировать через `AppLogger.e` (с расшифровкой из
      `TInvestErrorMessages`);
    - отправить в AppMetrica как **нефатальную** ошибку
      (`AppMetrica.reportError("Unhandled gRPC", throwable)`);
    - **не пробрасывать** дальше в оригинальный handler
      (приложение выживает).
3. Иначе — пробросить в оригинальный handler (пусть падает
   как обычно).
4. Проверка: включить авиарежим на эмуляторе, дождаться
   gRPC-ошибки, убедиться, что приложение не падает, а в
   логах есть запись об ошибке.

**Ограничения:**
- Не глотать ошибку молча — обязательно логировать и
  отправлять в AppMetrica. Иначе скроем реальные сетевые
  проблемы (см. `docs/ISSUES.md`).
- Не перехватывать `CancellationException` — это штатная
  отмена, она не должна попадать в handler.

**Критерий готовности:**
- Приложение не падает при gRPC-ошибках в фоне.
- Ошибки видны в AppMetrica (как non-fatal) и в логах.
- Остальные краши (не gRPC) по-прежнему валят приложение и
  видны в AppMetrica.

**Live-смоук (2026-10-22, эмулятор Pixel 8a, Android 14):**
- Guard установлен, старт приложения не сломан
  (`initAppMetrica` → `installGrpcExceptionGuard`, порядок
  сохраняет fatal-reпортинг AppMetrica).
- Авиарежим → `UNAVAILABLE` во всех стримах; guard молчит
  (штатные ошибки ловятся в своих корутинах — `try/catch`,
  `.catch` — до глобального handler не доходят). Ожидаемое
  поведение.
- Возврат сети → gRPC-reconnect и WS-reconnect, данные
  пошли, приложение живо.
- `AndroidRuntime: FATAL EXCEPTION` — нет.
- Исходный race (`ClientCalls` доставляет `StatusException`
  в уже завершённую корутину) по команде не воспроизводится —
  этим смоуком не проверялся. Регрессий не внёс, но и
  доказательством работы guard не является.

**Критерий закрытия:** наблюдение AppMetrica 2–3 дня после
релиза. Задача подтверждена, если одновременно:
- не появляются новые **fatal** `io.grpc.StatusException`;
- появляются **non-fatal** `"Unhandled gRPC"` (из
  `AppMetrica.reportError` в guard).

Если оба условия выполнены — `docs(roadmap): mark
grpc-exception-guard done`. Если появились новые fatal —
guard не срабатывает, разбираться отдельно.

**Оценка:** ~1 час.




___




### Расшифровка кодов ошибок брокеров

**Статус:** в работе.

**Сделано:**
- `util/BrokerErrorMessages.kt` — центральный реестр
  `Map<BrokerName, Map<code, msg>>`.
- `util/TInvestErrorMessages.kt` — ~130 кодов T-Invest по
  официальной документации.
- `AppError.Api` расширен полями `brokerName`, `brokerCode`.
- `toAppError(brokerName)` и `runCatchingAppResult(brokerName, block)`.
- `InvestRepository` передаёт `brokerName` во все вызовы.

**Осталось:**
- `BcsErrorMessages.kt` — заполнить по мере нахождения кодов BCS.
- `FinamErrorMessages.kt` — то же.
- Дополнять T-Invest таблицу новыми кодами по мере появления в логах.

**Оценка:** 15 минут на нового брокера + 5 минут на новый код.



___



### OrderSearchViewModel: reduce functions

**Мобильная работа:** полное закрытие (proot + PR, гейт на CI).

**Статус:** запланировано.

**Контекст:** `OrderSearchViewModel` после 13d (11a) — 17 функций, `@Suppress("TooManyFunctions")` (2c, KDoc «17 функций = 2 симметричных search-флоу × 8. Split на 2 VM = +1 VM + cross-VM через selectedBroker (M5). Группировка (sealed SearchDialogAction) — хуже читаемость»). Снятие `@Suppress` — снизить число методов до ≤ 11 или разбить на 2 VM (`OrderSearchViewModel` + `OrderPairSearchViewModel`).

**Соответствие Clean:** соответствует (SRP / `TooManyFunctions` — долг по 2c, не нарушение).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** «OrdersViewModel: разбить» закрыто (13d/14b/15/17 — сделаны).

**Файлы:**
- `presentation/screens/orders/OrderSearchViewModel.kt` — 17 функций, `@Suppress("TooManyFunctions")`.
- `presentation/screens/orders/OrderSearchState.kt` — 15 полей (13 плана + `statusMessage` / `isError`, M6 pre-existing).
- (опционально) `OrderPairSearchViewModel.kt` + `OrderPairSearchState.kt` — split pair-search.
- (опционально) `presentation/screens/orders/OrdersSearch.kt` / `OrdersPairTrading.kt` — callsites при split.

**Шаги:**
1. Решить форму: (a) снижение числа функций через группировку (sealed `SearchAction`) — без split; (b) split на 2 VM (основной / парный search).
2. Диагностика: `grep -nE "^\s+fun " OrderSearchViewModel.kt` — 17 функций, какие симметричны.
3. Реализация выбранного варианта + миграция UI-callsites.
4. Снять `@Suppress("TooManyFunctions")` (если функция ≤ 11) или пересчитать порог.
5. Гейт: `testDebugUnitTest / lintDebug / ktlintCheck / detekt`.
6. `OrderSearchViewModelTest` — обновить/расширить (текущие 6 кейсов).

**Тесты:** `OrderSearchViewModelTest` — 6 кейсов (было), + регрессия на split (если B).

**Референсы:** 13d KDoc `@Suppress("TooManyFunctions")` (2c); `OrderFormViewModel` (13d-B) — аналогичный `@Suppress`, задача ниже; closed `### ✅ OrderSearchViewModel` (ROADMAP-ARCHIVE, `7c4b5d9`/`7e1196c`/`eea0ec5`).

**Ограничения:**
- `statusMessage` / `isError` (M6 pre-existing) — **не трогать** в этой задаче (M6 — отдельная).
- UI-санкция не требуется (только внутренняя структура VM).
- **Связанная M001** (`### M001: formatting в presentation`) — если правка
  не усложняет, можно заодно: `formatCostOverlay` /
  `formatMultiplierOverlay` / `formatPostOrderSuccess` из Form-VM в
  `presentation/util/`. Формально не часть этой задачи — приоритет M001
  отдельно, но по 2c-долгу — можно.

**Критерий готовности:**
- `@Suppress("TooManyFunctions")` снят или функция ≤ 11.
- `OrderSearchViewModelTest` зелёный.
- Гейт зелёный.

**Оценка:** ~2–3 ч.

**Зависимости:** «OrdersViewModel: разбить» (закрыто).

**Связано:** `### OrderFormViewModel: reduce functions`, `### M6: one-shot events (StatusEvent)`.

**Открытые вопросы:** вариант A (группировка) vs B (split) — решить при старте.



___



### 2. OrderFormViewModel: reduce functions

**Мобильная работа:** полное закрытие (proot + PR, гейт на CI).

**Статус:** запланировано.

**Контекст:** `OrderFormViewModel` после 13d (A–D) — 30 функций, `@Suppress("TooManyFunctions")` (2c, KDoc 13d-B «12 функций в 13d-B, 26+ в 13d-C/D. Split Form на sub-VM = +N VM + cross-VM (форма — единый сценарий: instrument + quantity + orderType + pair + confirm). Группировка — хуже читаемость»). Плюс P7 (M001): `formatCostOverlay` / `formatMultiplierOverlay` / `formatPostOrderSuccess` — presentation в VM. Снятие `@Suppress` — extract helpers / presentation-format в `presentation/util/`.

**Соответствие Clean:** соответствует (SRP / `TooManyFunctions` — долг по 2c, не нарушение).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** «OrdersViewModel: разбить» закрыто (13d/14b/15/17 — сделаны).

**Файлы:**
- `presentation/screens/orders/OrderFormViewModel.kt` — 30 функций, `@Suppress("TooManyFunctions")`, private `formatPostOrderSuccess` / `formatCostOverlay` / `formatMultiplierOverlay` (P7).
- `presentation/util/` — новые helper'ы (format).
- `presentation/screens/orders/OrderFormState.kt` — 29 полей.

**Шаги:**
1. Extract presentation-format (`formatCostOverlay` / `formatMultiplierOverlay` / `formatPostOrderSuccess`) в `presentation/util/` (M001, P7) — это убирает 3 функции из VM.
2. Пересчитать: `grep -cE "^\s+(fun|private fun|private suspend fun|override fun)" OrderFormViewModel.kt` — сколько после extract.
3. Если всё ещё > 11 — extract private helpers в top-level `private fun OrderFormState.X(...)` (в `presentation/screens/orders/`, отдельный файл).
4. Снять `@Suppress("TooManyFunctions")`.
5. Гейт: `testDebugUnitTest / lintDebug / ktlintCheck / detekt`.
6. `OrderFormViewModelTest` (новый) — если ещё нет.

**Тесты:** `OrderFormViewModelTest` — success / failure / exception / cancellation на `postOrder` + `onInstrumentSelected` / `onPairedInstrumentSelected` (или использовать существующие).

**Референсы:** 13d-B KDoc (`@Suppress("TooManyFunctions")`, 2c); `### M001: formatting в presentation` (связана, P7); `OrderSearchViewModel: reduce functions` (аналог).

**Ограничения:**
- **`formatPostOrderSuccess`** — оставить как presentation (M001-задача, extract).
- **`postOrder` / `buildPlaceOrderRequest`** — orchestration (форма), не presentation (не в M001).
- UI-санкция не требуется (только внутренняя структура VM).

**Критерий готовности:**
- `@Suppress("TooManyFunctions")` снят или функция ≤ 11.
- `formatCostOverlay` / `formatMultiplierOverlay` / `formatPostOrderSuccess` — в `presentation/util/` (M001).
- `OrderFormViewModelTest` зелёный.
- Гейт зелёный.

**Оценка:** ~4–6 ч.

**Зависимости:** «OrdersViewModel: разбить» (закрыто).

**Связано:** `### OrderSearchViewModel: reduce functions`, `### M001: formatting в presentation`, `### M6: one-shot events (StatusEvent)`.

**Открытые вопросы:** какие функции extract'ить (формат / helpers) — решить при старте.



___



___



### OrdersListDialog: empty-state

**Мобильная работа:** частичное (live-тест UI).

**Статус:** запланировано. **UI — требует санкции.**

**Контекст:** в `OrdersListViewModel` до M016 было сообщение `"Нет активных заявок"`, эмитившееся через `statusMessage` когда `orders.isEmpty() && hasLoaded`. При этом **никто это сообщение не читал**: `OrdersListDialog` подписан на `uiState.orders` и `uiState.isLoading`, но не на `statusMessage`. В рамках M016 (`### ✅ 4. M6: one-shot events (StatusEvent)`, PR #112) `statusMessage` из `OrdersListState` удалён как dead code (2d — задача = этот follow-up). Теперь empty-state надо реализовать явно, если он нужен.

**Соответствие Clean:** соответствует (UI-фича; empty-state — presentation).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** M016 закрыт (PR #112).

**Файлы:**
- `presentation/components/OrdersListDialog.kt` — empty-state.

**Шаги:**
1. **UI-санкция** (правило 1): согласовать текст («Нет активных заявок» / «Заявок нет» / иконка), размещение.
2. UI: `if (orders.isEmpty() && !isLoading) { EmptyState() } else { LazyColumn { … } }` в `OrdersListDialog`.
3. `@Preview` для empty-state (если превью уже есть — иначе отдельная задача).

**Тесты:** UI-превью (по согласованию), unit-тестов нет.

**Референсы:** `OrdersListDialog.kt`, M016 (PR #112) — где удалён dead-code `"Нет активных заявок"`.

**Ограничения:**
- Не менять `OrdersListState` (только UI-check `orders.isEmpty()`).
- Не смешивать с другими UI-задачами.

**Критерий готовности:**
- В `OrdersListDialog` отображается пустое состояние, когда `orders.isEmpty() && !isLoading`.
- Текст и стиль согласованы (UI-санкция).
- Гейт зелёный; live-test UI PASS.

**Оценка:** ~30–45 мин (с санкцией).

**Зависимости:** нет.

**Связано:** M016 (PR #112), `### M6: Portfolio + Settings panels`.

**Открытые вопросы:** нужен ли empty-state в UI вообще? Решить на санкции.



___



### Settings: inline status card vs snackbar

**Мобильная работа:** частичное (live-тест UI).

**Статус:** запланировано. **UI — требует санкции.**

**Контекст:** (2d) в M6 umbrella (`### ✅ M6: finish one-shot events`, PR #114) три Settings panel-VM (`TInvestSettingsViewModel`, `BcsSettingsViewModel`, `FinamSettingsViewModel`) **не** переведены на `Channel<StatusEvent>`. Причина: `SettingsScreen` рендерит `statusMessage` / `isError` как **inline `Card`** прямо в layout (`uiState.statusMessage?.let { Card { Text(message) } }`), а не как snackbar. У `SettingsScreen` нет `SnackbarHostState`. Это не M6-баг, а другой UI-паттерн. Umbrella закрыта по U0+U1+U2; U3/Settings вынесен отдельно.

**Соответствие Clean:** отклонение по 2c (принято).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** оставить как есть (inline Card — нормальный state-паттерн для Settings: статус подключения — не transient event). Плюсы: просто, без Channel; Card видна, пока не закроют (в отличие от snackbar, который auto-dismiss). Минусы: UX отличается от Orders/Portfolio (там snackbar).

**Выгоды (для «отклонение по 2c»):** Settings — «состояние подключения» (persistent, а не one-shot). Snackbar скроется через N секунд, пользователь может не успеть прочитать. Inline Card лучше подходит для такого контента. Смена на snackbar — **UX-регресс** для Settings.

**Предусловия:** нет. `StatusEvent` + `StatusEventEffect` + `StatusSnackbarHost` — уже общие (`presentation/util|components`, PR #114).

**Файлы:**
- (вариант A: «оставить inline Card», 2c-принято) — ничего, только явная запись в clean-audit `### M6: Settings — inline Card, не M6`.
- (вариант B: snackbar) — `presentation/screens/settings/{TInvest,Bcs,Finam}SettingsViewModel.kt` + `SettingsScreen.kt` + `StatusEventEffect` (общий).

**Шаги (вариант B, если санкция «snackbar»):**
1. UI-санкция (правило 1): согласовать «snackbar вместо inline Card» для 3 панелей — визуал меняется.
2. Каждая panel-VM: `Channel<StatusEvent>` + `val status: Flow<StatusEvent>`.
3. State (`TInvestUiState` / `BcsUiState` / `FinamUiState`): −`statusMessage`/`isError`.
4. `SettingsScreen`: 3× `StatusEventEffect(viewModel.status)` + `StatusSnackbarHost`.
5. Тесты: 1 кейс на панель (status Flow), если mock-инфра.
6. Live-test UI (3 панели: подключение / ошибка / отключение).

**Тесты:** 3 VM (1 кейс на панель), live-test UI 3 сценария.

**Референсы:** M6 umbrella (PR #114), `OrderSearchViewModelTest` / `OrderFormViewModelTest` (pattern), `StatusEventEffect` / `StatusSnackbarHost` (`presentation/components/`).

**Ограничения:**
- UI-санкция обязательна (правило 1): меняется визуал (Card → snackbar).
- Не смешивать с `### OrdersListDialog: empty-state` (тоже UI, отдельно).
- Если «оставить inline Card» — задача закрывается как `accepted (2c)` с записью в clean-audit, без правок.

**Критерий готовности:**
- (вариант A: «оставить») — clean-audit `### M6: Settings — inline Card, не M6` (2c), задача → ARCHIVE.
- (вариант B: snackbar) — 3 panel-VM на `Channel<StatusEvent>`, state −2 поля, `SettingsScreen` — 3× `StatusEventEffect`, live-test UI PASS, gate green.

**Оценка:** вариант B — ~2 ч; вариант A — ~10 мин (запись в clean-audit).

**Зависимости:** нет.

**Связано:** M6 umbrella (`### ✅ M6: finish one-shot events`, PR #114).

**Открытые вопросы:** нужен ли snackbar в Settings (единый UX с Orders/Portfolio) или inline Card — окончательный выбор? Решить на санкции.






___



### Compose UI: Hilt-test-infra для OrdersScreen

**Мобильная работа:** полное закрытие (proot + PR, гейт на CI).

**Статус:** запланировано.

**Контекст:** (2d) U1 Pass-2 показал: `OrdersScreen` инжектит 4 VM через `hiltViewModel()` (`OrdersViewModel`, `OrderSearchViewModel`, `OrderDialogsViewModel`, `OrderFormViewModel`). `createComposeRule()` без Hilt-test-infra (`@HiltAndroidRule`, `TestApplication`, `@UninstallModules`) на таком composable не запускается — `OrdersScreenBehaviorTest` (search → select → form, confirm, pair slider, snackbar) **невозможен**. Вынесено из U1 Pass-2 как отдельная инфраструктура.

**Соответствие Clean:** соответствует (test-layer, presentation-test).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** U0 Compose UI infra закрыт (PR #115, Robolectric + `@GraphicsMode` + `@ConscryptMode`). `OrderCardTest` — образец.

**Файлы:**
- `app/build.gradle.kts` — `testImplementation("com.google.dagger:hilt-android-testing:2.60.1")`, `kspTest("com.google.dagger:hilt-android-compiler:2.60.1")`.
- `app/src/test/kotlin/.../testutil/TestApplication.kt` (Hilt).
- `app/src/test/resources/robolectric.properties` — `application=...TestApplication`.
- Новый `OrdersScreenBehaviorTest.kt`.

**Шаги:**
1. `app/build.gradle.kts`: + `hilt-android-testing` (test), `kspTest hilt-android-compiler`.
2. `TestApplication` (Hilt): `@HiltAndroidApp` в test-source set; `robolectric.properties: application=...TestApplication`.
3. Пилотный test с `@HiltAndroidRule` + `@Config(sdk = [34])` + `@GraphicsMode(NATIVE)` + `@ConscryptMode(OFF)` + `createAndroidComposeRule<ComponentActivity>()` (Activity обязательна для `@AndroidEntryPoint`).
4. `OrdersScreenBehaviorTest`: search → select → form, confirm dialog, pair slider, snackbar (5–7 сценариев).
5. Гейт: fast-check (JVM, Robolectric, без эмулятора).

**Тесты:** 5–7 сценариев OrdersScreen (behavioral, JVM).

**Референсы:** U0 (PR #115), `OrderCardTest` (робо-образец), Dagger Hilt testing docs, `androidx.hilt:hilt-navigation-compose`.

**Ограничения:**
- Robolectric-hosted (JVM), **не** instrumented (эмулятор) — как U0.
- Не смешивать с U1 Pass-2 (это отдельная инфраструктура, без неё Pass-2 behavioural идёт на чистых composables).
- `@AndroidEntryPoint` на `ComponentActivity` + Hilt rule — порядок rule важен (`@HiltAndroidRule` first).

**Критерий готовности:** `OrdersScreenBehaviorTest` 5–7 сценариев зелёные на CI (fast-check), без эмулятора.

**Оценка:** ~2–3 ч.

**Зависимости:** U0 (PR #115) — done.

**Связано:** `### Compose UI: test infra + 3 экрана (3 проходки)` (umbrella U0 done, U1 Pass-2 planned).

**Открытые вопросы:** `createAndroidComposeRule<ComponentActivity>()` + `@AndroidEntryPoint` в Robolectric — совместимость проверить первым PR (если Activity-инъекция не поднимется, fallback — `createComposeRule()` + `hiltViewModel()` заменить на прямую инжекцию через `@Inject` в тесте).

___

### docs/live-test.md: mobile-ограничения (Robolectric vs device)

**Мобильная работа:** полное закрытие (docs-only, proot).

**Статус:** запланировано.

**Контекст:** (2d) диагностика U1 Pass-2: `docs/live-test.md` не содержит секции о **mobile (proot)** ограничениях. Сейчас `Robolectric` + Compose UI tests дают JVM-поведение (semantics, callbacks, snapshot), но **не** заменяют live-test на устройстве (RenderThread, accessibility, IME, insets, edge-to-edge, WebView, камера/биометрия). Нужно явно зафиксировать границу, чтобы не путать «JVM behavioral PASS» с «device smoke PASS».

**Соответствие Clean:** соответствует (docs-only).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** U0 Compose UI infra (PR #115) — done.

**Файлы:**
- `docs/live-test.md` — новая секция `## Mobile (proot): Robolectric vs device`.

**Шаги:**
1. Добавить секцию: что **покрывается** Robolectric (semantics, callbacks, `remember`/`LaunchedEffect` behaviour, mainClock), что **нет** (RenderThread/bitmap, accessibility, IME/insets, edge-to-edge, WebView, камера/биометрия).
2. Явно: «JVM behavioral PASS ≠ device smoke PASS»; для последнего — `docs/live-test.md` режим A/B (desktop+adb).
3. Обновить related `docs/gradle-setup.md` → «Compose UI tests (Robolectric)» (ссылка на границу).
4. `git diff --check`.

**Тесты:** нет (docs-only).

**Референсы:** U0 (PR #115), `docs/gradle-setup.md` (Compose UI section), Robolectric limitations.

**Ограничения:**
- docs-only; не менять `Robolectric`-тесты.
- Не дублировать `docs/gradle-setup.md` — там раздел про зависимости и setup, здесь — про границу JVM vs device.

**Критерий готовности:** секция в `docs/live-test.md`; ссылка из `docs/gradle-setup.md`; `git diff --check` чистый.

**Оценка:** ~20 мин.

**Зависимости:** нет.

**Связано:** U0 (PR #115).

**Открытые вопросы:** нет.









___



## Закрытые

Задачи вынесены в [`ROADMAP-ARCHIVE.md`](ROADMAP-ARCHIVE.md).
