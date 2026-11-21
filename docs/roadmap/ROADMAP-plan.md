<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: roadmap-plan -->
## Запланировано










<!-- SUBSECTION_BEGIN: t-2 -->
<a id="t-2"></a>
### [PLAN] T-2 AGENTS: консолидация скриптов в AGENTS/scripts + инструкции отдельно
**Статус:** запланировано.
**Мобильная работа:** полное закрытие
**Контекст:** скрипты для работы с `AGENTS/` частично в репо
(`AGENTS/scripts/`: `docs_normalize.py`, `docs_add.py`, `docs_cut.py`,
`docs_rebuild_index.py`), частично вне (`$HOME/deepseek/seed_markers.py`
— одноразовый, но не сохранён). Инструкция `4j` упоминает только
`add`/`cut`/`normalize`, не `rebuild_index`. Нужно: (1) все штатные
скрипты — в `AGENTS/scripts/`; (2) `4j` — полный список инструментов;
(3) одноразовые — в `AGENTS/scripts/oneoff/` или задокументированы
как «не штатные».
**Соответствие Clean:** — (не применимо, инструменты документации)
**Нарушенное правило Clean:** —
**Clean-альтернатива:** —
**Выгоды:** —
**MVVM:** — (не применимо)
**KMP:** — (не применимо)
**Предусловия:** `AGENTS/scripts/` с 4 скриптами (есть,
`bc9673e5`/`b814a5fd`/`c99b302b`/`e12aed55`).
**Файлы:**
- `AGENTS/scripts/README.md` — что каждый скрипт делает, аргументы,
  примеры (инструменты).
- `AGENTS/AGENTS-style-docs.md` (r4j) — обновить: полный список
  (`normalize`, `add`, `cut`, `rebuild_index`), ссылка на
  `scripts/README.md`.
- `AGENTS/scripts/oneoff/` — `seed_markers.py` (перенести из
  `$HOME/deepseek/`, пометить «одноразовый, не запускать повторно»).
- `$HOME/deepseek/` — почистить бэкапы/временные.
**Шаги:**
1. `AGENTS/scripts/README.md` — таблица: скрипт, назначение,
   аргументы, пример.
2. `seed_markers.py` — `cp $HOME/deepseek/seed_markers.py
   AGENTS/scripts/oneoff/`, шапка «одноразовый, не запускать
   повторно (уже применён в 2658a54c)».
3. `r4j`: добавить `docs_rebuild_index.py` в список; ссылка на
   `README.md`.
4. `$HOME/deepseek/` — `rm normalize.py.bak*`, `4j-body.md`,
   `smoke-body.md`, `agents-cut-*.md`.
5. Verify: `r4j` содержит все 4 скрипта; `README.md` содержит
   все 4; `--check-index` 0.
**Референсы:** `AGENTS/scripts/*.py`, `r4j`,
`$HOME/deepseek/seed_markers.py`.
**Ограничения:** не менять логику скриптов — только документация
и перенос. `seed_markers.py` — не запускать (уже применён).
**Критерий готовности:** `AGENTS/scripts/README.md` (4 скрипта +
oneoff); `r4j` упоминает все 4; `oneoff/seed_markers.py` в репо;
`$HOME/deepseek/` чист от временных; `--check-index` 0.
**Оценка:** ~30–40 мин.
**Зависимости:** —.
**Открытые вопросы:** `seed_markers.py` — в `oneoff/` или удалить
(он одноразовый, применён)? Рекомендую `oneoff/` — история, как
расставлялись маркеры.

---
<!-- SUBSECTION_END: t-2 -->










<!-- SUBSECTION_BEGIN: t-4 -->
<a id="t-4"></a>
### [PLAN] T-4 Rate-limiter: GetUserTariff (dynamic limits)
**Мобильная работа:** полное закрытие (proot + PR; data-слой, гейт на CI).

**Статус:** запланировано. Follow-up к 1a (D5=A).

**Контекст:** 1a использует статические лимиты из `docs/rate-limits.md` (грейд 1 — консервативно). `GetUserTariff` (unary `UsersService/GetUserTariff`) возвращает актуальные `unaryLimits` / `streamLimits` для текущего пользователя; грейды 4–5 дают лимиты выше табличных. Для live-test C2 статики достаточно, но для high-tier пользователей rate-limiter занижает throughput.

**Соответствие Clean:** соответствует (data-layer infrastructure; domain не знает).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**MVVM:** — (не применимо).

**KMP:** соответствует (`TransportRateLimitsProvider` интерфейс, без platform).

**Предусловия:** 1a закрыт (`TInvestRateLimiter` + `TInvestRateLimits` есть).

**Файлы:**
- `data/api/common/TransportRateLimitsProvider.kt` (новый, KMP-safe) — `suspend fun fetch(): RateLimitsSnapshot`.
- `data/api/tinvest/TInvestRateLimitsProvider.kt` (новый) — `GetUserTariff` → snapshot.
- `data/api/tinvest/TInvestRateLimiter.kt` — принять `RateLimitsProvider?`; `null` → статика.
- `data/api/tinvest/TInvestBrokerApi.kt` — initialize: fetch + передать snapshot.
- Тесты.

**Шаги:**
1. `TransportRateLimitsProvider` (interface) — KMP-safe.
2. `TInvestRateLimitsProvider` — `GetUserTariff` через rate-limiter (bootstrap-проблема: сам идёт под глобальным лимитом).
3. `TInvestRateLimiter` — опциональный provider; до первого fetch — статика.
4. `TInvestBrokerApi` initialize — fetch после `initialize`, кэш в памяти.
5. **Логирование:** `[rate-limiter/tariff] fetched: unary=..., stream=...`; при ошибке — fallback на статику + `Logger.w`.
6. Тесты: provider (мок SDK), limiter с/без provider.

**Тесты:** 3 unit-теста.

**Референсы:** `docs/rate-limits.md` → «`GetUserTariff` — динамические лимиты»; proto `ru.tinkoff.piapi.contract.v1.UsersService/GetUserTariff`; 1a шаг 5.

**Ограничения:**
- Не менять `TInvestRateLimits` (статика остаётся fallback).
- Не блокировать `initialize` долгим tariff-fetch: timeout 3s + fallback.
- Bootstrap: `GetUserTariff` сам под `rateLimiter` (без цикла).

**Критерий готовности:**
- Provider + limiter работают с динамическими лимитами.
- Live-test: high-tier пользователь видит throughput > статики; low-tier — не ломается.
- Fallback на статику при ошибке/tail.
- Гейт зелёный.

**Оценка:** ~1.5–2 ч (provider 40 мин, integration 40 мин, tests 20 мин, live 20 мин).

**Зависимости:** 1a (закрыт).

**Связано:** umbrella 1a (D5=A, шаг 5); `docs/rate-limits.md`.

**Открытые вопросы:**
- Bootstrap: `GetUserTariff` под глобальным rate-limiter? — да, но с повышенным таймаутом.
- Кэшировать между запусками (DataStore) или только в памяти? — в памяти для 1-й итерации.

---
<!-- SUBSECTION_END: t-4 -->










<!-- SUBSECTION_BEGIN: t-40 -->
<a id="t-40"></a>
### [PLAN] T-40 PortfolioUiState: passthrough tradingStatuses StateFlow
**Мобильная работа:** полное закрытие (proot + PR; presentation; гейт на CI).

**Статус:** запланировано.

**Контекст:** после T-9 (5b) `PortfolioViewModel` подписан на `tradingStatusRepository.statuses` и кладет результат в `_uiState.tradingStatuses` через `collect`. Orders работает иначе - passthrough: `val tradingStatuses: StateFlow<...> = tradingStatusRepository.statuses` (SSoT напрямую). T-40 - унифицировать Portfolio под паттерн Orders.

**Соответствие Clean:** соответствует - presentation читает domain-контракт (StateFlow из repository), не копирует.

**Нарушенное правило Clean:** -.

**Clean-альтернатива:** -.

**Выгоды (для «отклонение по 2c»):** -.

**MVVM:** соответствует - `M5` предотвращен (нет двух полей на одно значение).

**KMP:** - (не применимо, presentation).

**Предусловия:** T-9 закрыт.

**Файлы:** `presentation/screens/portfolio/PortfolioViewModel.kt`, `PortfolioUiState.kt`, `PortfolioScreen.kt`.

**Шаги:**
1. В VM: `val tradingStatuses = tradingStatusRepository.statuses` (passthrough).
2. В `PortfolioUiState`: удалить `val tradingStatuses`.
3. В Screen: `val tradingStatuses by viewModel.tradingStatuses.collectAsState()`; передавать в `PortfolioAccountBlock`.
4. Гейт.

**Тесты:** `PortfolioScreenBehaviourTest` - обновить mock если есть.

**Референсы:** `OrdersViewModel.kt:70` (образец passthrough).

**Ограничения:** не менять поведение UI; перенос только источника данных.

**Критерий готовности:** UI получает `tradingStatuses` через passthrough; SSoT - `TradingStatusRepository`; гейт.

**Оценка:** ~20-30 мин.

**Зависимости:** T-9 (5b).

**Связано:** T-9; T-41.

**Открытые вопросы:** нет.

---
<!-- SUBSECTION_END: t-40 -->










<!-- SUBSECTION_BEGIN: t-41 -->
<a id="t-41"></a>
### [PLAN] T-41 Orders: миграция tradingStatuses на TradingStatusFlowManager
**Мобильная работа:** полное закрытие (proot + PR; presentation; гейт на CI).

**Статус:** запланировано.

**Контекст:** после T-9 (5b) `TradingStatusFlowManager` есть в domain. `OrdersViewModel` сейчас обновляет статусы on-demand: при смене списка инструментов - `updateTradingStatuses(idsToUpdate)` -> `UpdateTradingStatusesUseCase`. Работает, но: (а) между изменениями списка статусы устаревают (нет polling); (б) ids Portfolio и Orders могут пересекаться - два независимых fetch. Миграция на `TradingStatusFlowManager` даст единый polling + union ids.

**Соответствие Clean:** соответствует - policy в domain-менеджере.

**Нарушенное правило Clean:** -.

**Clean-альтернатива:** -.

**Выгоды (для «отклонение по 2c»):** -.

**MVVM:** соответствует - VM триггерит `start/updateIds`, не оркестрирует polling.

**KMP:** соответствует - manager в domain.

**Предусловия:** T-9 закрыт (manager в проде, обкатан на Portfolio).

**Файлы:** `presentation/screens/orders/OrdersViewModel.kt`, `OrdersCoordViewModel.kt` (если триггерит), `domain/api/managers/TradingStatusFlowManager.kt` (union-id логика).

**Шаги:**
1. Проверить текущие callsites `updateTradingStatuses` в Orders.
2. Inject `TradingStatusFlowManager` в `OrdersViewModel`.
3. `updateTradingStatuses(ids)` -> `manager.updateIds(ids)`.
4. `onCleared` -> `manager.stop()`.
5. Проверить union с Portfolio.
6. Гейт.

**Тесты:** `OrdersViewModelTest` - обновить mock (manager вместо UseCase).

**Референсы:** T-9; `OrdersViewModel.kt:102`.

**Ограничения:** не менять UI; миграция 1:1 по поведению.

**Критерий готовности:** статусы в Orders обновляются через manager; union с Portfolio; гейт.

**Оценка:** ~1-1.5 ч.

**Зависимости:** T-9.

**Связано:** T-9; T-40.

**Открытые вопросы:** как `OrdersViewModel` триггерит `updateTradingStatuses` - на каждом изменении списка или на init? Уточнить при старте.

---
<!-- SUBSECTION_END: t-41 -->










<!-- SUBSECTION_BEGIN: t-42 -->
<a id="t-42"></a>
### [PLAN] T-42 TradingStatus: stream API (subscribeTradingStatuses)
**Мобильная работа:** полное закрытие (proot + PR; data + domain; гейт на CI).

**Статус:** запланировано.

**Контекст:** T-9 реализует `TradingStatusFlowManager` polling-only (1 мин), потому что в `TInvest*Service` нет обертки над `MarketDataStreamService.subscribeTradingStatuses`. По `docs/rate-limits.md`: «На торговые статусы лимита подписок нет» - stream возможен. Полная аналогия с `PriceFlowManager` (stream primary + polling fallback) устранит polling в steady-state.

**Соответствие Clean:** соответствует - stream-обертка в `data/api/tinvest/`, manager в `domain/api/managers/`.

**Нарушенное правило Clean:** -.

**Clean-альтернатива:** -.

**Выгоды (для «отклонение по 2c»):** -.

**MVVM:** - (не применимо, data+domain).

**KMP:** соответствует - manager в domain, stream в data.

**Предусловия:** T-9 закрыт.

**Файлы:** `data/api/tinvest/TInvestTradingService.kt`, `TInvestBrokerApi.kt`, `domain/api/roles/TradingStatusStreamApi.kt` (новый), `domain/api/managers/TradingStatusFlowManager.kt`, `data/orchestrator/TInvestOrchestrator.kt` (если нужен stream-комбинатор).

**Шаги:**
1. Проверить T-Invest API `MarketDataStreamService.subscribeTradingStatuses` - сигнатура, формат.
2. Обертка в `TInvestTradingService`: `Flow<TradingStatusStreamItem>`, reconnect через `StreamCombinators.retryWithBackoff`.
3. Role `TradingStatusStreamApi` в domain.
4. `TradingStatusFlowManager`: stream primary + polling fallback.
5. `StreamStateTracker` - LIVE/POLLING/STALE.
6. Гейт.

**Тесты:** unit на `subscribeTradingStatuses` (mock stream); manager - stream primary vs polling fallback.

**Референсы:** `PriceFlowManager.kt`, `TInvestPricesService.kt:54`, `docs/rate-limits.md:85`, `docs/streams.md`.

**Ограничения:** polling fallback при потере stream; stream-лимит не считать.

**Критерий готовности:** manager stream primary; polling только fallback; `StreamStateTracker` LIVE/POLLING; гейт.

**Оценка:** ~3-4 ч.

**Зависимости:** T-9.

**Связано:** T-9; umbrella T-3 sub-1b (stream connection limiter).

**Открытые вопросы:** есть ли у T-Invest SDK `MarketDataStreamService.subscribeTradingStatuses` в текущей версии - `grep 'ru.tinkoff.piapi.*TradingStatus'` при старте.

---
<!-- SUBSECTION_END: t-42 -->










<!-- SUBSECTION_BEGIN: t-9 -->
<a id="t-9"></a>
### [PLAN] T-9 PortfolioScreen: double-load + TradingStatusFlowManager
**Мобильная работа:** полное закрытие (proot + PR; presentation + domain; гейт на CI).

**Статус:** запланировано.

**Контекст:** `PortfolioScreen:38-42` - `repeatOnLifecycle(RESUMED) { viewModel.refresh() }` дергает полный `loadPortfolio()` при каждом входе в RESUMED (back-stack, foreground, поворот). Плюс `init { brokerManager.anyInitialized.collect { checkApiInitialization() } }` в VM - еще один `loadPortfolio()` при первом `anyInitialized`. Итог: 2+ полных загрузки на cold start, N+1 на resume - burst, блокирующий live-test 6 (restore). Umbrella-диагноз «LaunchedEffect(isLoading) infinite retry» устарел: стр. 44 - `isRefreshing = uiState.isLoading` (синхронизация, не retry); `refresh()` = `loadPortfolio()` без retry/backoff. Дополнительно: `tradingStatuses` в `PortfolioUiState` - свое поле (не SSoT), VM поллит `while (isActive) { delay(5min); updateTradingStatuses() }` - дублирует `UpdateTradingStatusesUseCase` + `TradingStatusRepository` из Orders, M2-нарушение.

**Соответствие Clean:** отклонение по 2c (принято) - менеджер поллинга в `domain/api/managers/` (по образцу PositionFlowManager/PriceFlowManager), не в VM/presentation.

**Нарушенное правило Clean:** dependency rule - периодический polling статусов (domain-policy) сейчас в VM (`while (isActive)`), при этом `TradingStatusRepository` (SSoT) - правильное место, но не используется.

**Clean-альтернатива:** (а) polling в `TradingStatusRepositoryImpl` - концептуально неверно (repository пассивный); (б) polling в UseCase - тоже policy, не domain-UseCase; (в) polling в VM - оставляет M2. Правильно - `TradingStatusFlowManager` в `domain/api/managers/`.

**Выгоды (для «отклонение по 2c»):** полная аналогия с существующими менеджерами (PositionFlowManager, PriceFlowManager) - паттерн проекта, не изобретение; единый SSoT (`TradingStatusRepository`) для Portfolio+Orders; rate-limit safe при 1 мин (batch primary + singular fallback - T-8).

**MVVM:** `M1` + `M2` - закрываются в рамках T-9: UI (`repeatOnLifecycle`) не командует загрузкой -> `LaunchedEffect(Unit)`; VM не поллит domain -> `TradingStatusFlowManager.start/updateIds/stop`; `tradingStatuses` -> `collect` из `TradingStatusRepository`.

**KMP:** соответствует - менеджер в `domain/api/managers/`, `CoroutineDispatcher` инжектится; `MarketDataRepository`/`TradingStatusRepository` - domain-интерфейсы; platform не протекает.

**Предусловия:** нет (T-8 закрыт - batch primary + singular fallback есть).

**Файлы:**
- `presentation/screens/portfolio/PortfolioScreen.kt` (убрать `repeatOnLifecycle`).
- `presentation/screens/portfolio/PortfolioViewModel.kt` (убрать `while (isActive)`, `updateTradingStatuses`; `refresh()` -> прямой `loadPortfolio()`; `collect` из `TradingStatusRepository`).
- `domain/api/managers/TradingStatusFlowManager.kt` (новый: interface + impl в одном файле по образцу PositionFlowManager).
- DI-binding для менеджера (файл - см. «Открытые вопросы»).

**Шаги (S2 - два PR):**

**PR 5a (~15-20 мин):**
1. `PortfolioScreen.kt`: убрать `LaunchedEffect(lifecycleOwner) { repeatOnLifecycle(RESUMED) { refresh() } }` -> `LaunchedEffect(Unit) { viewModel.refresh() }`.
2. `PortfolioViewModel.refresh()`: `fun refresh() { loadPortfolio() }` (убрать двойной `launch`).
3. `while (isActive)` polling в VM - оставить (регрессии нет, удаляется в 5b).
4. Гейт на CI.

**PR 5b (~1.5-2 ч):**
1. Создать `TradingStatusFlowManager` (interface + impl):
   - `start(ids)`: immediate fetch + `while (isActive) { delay(60_000L); fetchAndUpdate() }`.
   - `updateIds(ids)`: заменить tracked set без рестарта.
   - `stop()`: cancel scope.
   - `fetchAndUpdate()`: `marketDataRepository.getTradingStatusesResult` -> `tradingStatusRepository.update(...)`.
   - `INTERVAL_MS = 60_000L`.
2. DI-binding.
3. `PortfolioViewModel`:
   - Убрать `while (isActive)` polling.
   - Убрать свой `updateTradingStatuses`.
   - Inject `TradingStatusFlowManager` + `TradingStatusRepository`.
   - `init { tradingStatusRepository.statuses.collect { s -> _uiState.update { it.copy(tradingStatuses = s) } } }`.
   - После `loadPortfolio` success: `tradingStatusFlowManager.start(ids)`.
   - В `stopPositionUpdates` и `onCleared`: `tradingStatusFlowManager.stop()`.
4. Гейт на CI.

**Тесты:** `PortfolioScreenBehaviourTest` (renders refresh button) - не сломать. Юнит-тест `TradingStatusFlowManager` (start/updateIds/stop, интервал 60с) - добавить в 5b.

**Референсы:** `PositionFlowManager.kt` (образец polling), `PriceFlowManager.kt` (образец scope + retry), `TradingStatusRepositoryImpl.kt` (SSoT), `UpdateTradingStatusesUseCase.kt` (логика fetch+update для cold start), `TInvestTradingService.kt:52-160` (batch primary + singular fallback), live-log C2 (2026-11-15).

**Ограничения:**
- Не трогать `TradingStatusRepositoryImpl` (SSoT остается).
- Не трогать `UpdateTradingStatusesUseCase` (используется Search и Orders).
- Не менять UI (кроме замены `LaunchedEffect`-ключа - lifecycle, не дизайн).
- Не переносить polling в repository (концептуально неверно).
- Не удалять periodic polling совсем (биржи открываются/закрываются несколько раз в день, статусы меняются - UX-функция).

**Критерий готовности:**
- 1 полная загрузка на cold start (не 2+).
- 0 загрузок на back-stack (только холодный старт или явный pull/refresh).
- `tradingStatuses` в UI обновляются из `TradingStatusRepository` (SSoT).
- Polling 1 мин - в `TradingStatusFlowManager`, не в VM.
- `RESOURCE_EXHAUSTED` в live-test 6 - 0.
- Гейт зеленый.

**Оценка:** ~2-2.5 ч (5a ~15-20 мин + 5b ~1.5-2 ч).

**Зависимости:** нет. Блокирует live-test 6.

**Связано:** T-3 umbrella sub-5; T-8 (закрыт - batch+singular); T-40; T-41; T-42; `docs/rate-limits.md`.

**Открытые вопросы:** как называется DI-модуль для manager'ов (`ManagerModule.kt` есть? или binding в `RepositoryBindingModule`?) - `grep` перед PR 5b.

---
<!-- SUBSECTION_END: t-9 -->










<!-- SUBSECTION_BEGIN: t-10 -->
<a id="t-10"></a>
### [PLAN] T-10 TInvest*Service + *Api: unary → AppResult
**Мобильная работа:** полное закрытие (proot + PR; data + domain + presentation, гейт на CI).

**Статус:** запланировано. Follow-up к 1a.

**Контекст:** (clean-audit Н97) Сейчас `TInvest*Service` (6 сервисов) возвращают «сырые» типы (`Map<String, Double?>`, `List<Instrument>`, `List<Order>`), при ошибке — throw. `AppResult` появляется только на границе `*RepositoryImpl` через `runCatchingAppResult`; там **все** исключения мапятся в `AppError.Unknown`, включая `RateLimitTimeoutException` (введён в 1a) — семантика «rate limit vs unknown» теряется: UI не может выбрать между retry и «показать ошибку». Domain-роли (`MarketDataApi`, `PortfolioApi`, …) тоже возвращают «сырые» типы.

**Соответствие Clean:** соответствует (унификация контракта data-слоя).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**MVVM:** соответствует (presentation получает `AppResult<T>` без изменения текущей формы).

**KMP:** соответствует (`AppResult` — domain-модель, KMP-safe).

**Предусловия:** umbrella 1a закрыт (`RateLimitTimeoutException` есть).

**Файлы:** 6 `TInvest*Service`, `TInvestBrokerAPI`, 7 domain-ролей с unary, `*RepositoryImpl` (убрать `runCatchingAppResult` где сервис уже `AppResult`), presentation callsites, `AppError.RateLimited` (новый в `domain/models/`).

**Шаги:**
1. `AppError.RateLimited` — новый подтип; маппинг в presentation («Слишком много запросов. Подождите»).
2. `TInvestPricesService.getLastPricesByTscalpInstrumentId` → `AppResult<Map<String, Double?>>`; `RateLimitTimeoutException` → `AppError.RateLimited`; прочие `Exception` → `AppError.Unknown`.
3. Аналогично 5 остальных сервисов — по коммиту на сервис.
4. Обновить 7 domain-ролей (`MarketDataApi`, `PortfolioApi`, `OrdersApi`, `StreamsApi`, `UserInstrumentsApi`, `AccountsApi`, `TradeCheckApi`) → `AppResult<T>` для unary.
5. Убрать `runCatchingAppResult` из `*RepositoryImpl` для unary-методов, где сервис уже возвращает `AppResult`.
6. Presentation: `when (result)` + `AppError.RateLimited` → отдельное сообщение.
7. Тесты: 6 `TInvest*ServiceTest` + обновить `TInvestOrchestratorFallbackTest` (мок `coEvery { ... } returns AppResult.Failure(...)`).
8. Live-test: search + cold start без `RESOURCE_EXHAUSTED` (с rate-limiter) + UI-сообщение `RateLimited` при таймауте.

**Тесты:** 6 unit-тестов сервисов; правка `TInvestOrchestratorFallbackTest`.

**Референсы:** clean-audit Н97; закрытый Н46 (PR #75); `docs/architecture.md` → «Data flow».

**Ограничения:**
- Не менять стиль `*RepositoryImpl` (граница остаётся `AppResult`).
- Не смешивать с 1a (это follow-up, не подзадача umbrella).
- Streaming (`Flow`) — не трогать: throw остаётся (структурная ошибка); `AppResult` — только unary.
- `runCatchingAppResult` убирается **только** для unary-методов.

**Критерий готовности:**
- Все unary-методы `TInvest*Service` → `AppResult<T>`.
- 7 domain-ролей → `AppResult<T>` (unary).
- `AppError.RateLimited` — есть, мапится в UI.
- `runCatchingAppResult` не нужен для unary.
- Гейт зелёный; live-test PASS.

**Оценка:** ~4–6 ч (по ~1 ч на сервис + тесты + presentation).

**Зависимости:** 1a (`RateLimitTimeoutException`).

**Связано:** clean-audit Н97; закрытый Н46 (PR #75); umbrella 1a.

**Открытые вопросы:**
- `AppError.RateLimited` — отдельный подтип или флаг `retryable` на `AppError.Unknown`? **Рекомендация:** отдельный подтип.
- Streaming (`Flow`) — throw или тоже `AppResult` в `first()`? **Рекомендация:** throw.

---
<!-- SUBSECTION_END: t-10 -->










<!-- SUBSECTION_BEGIN: t-11 -->
<a id="t-11"></a>
### [PLAN] T-11 C2: SaveFormStateUseCase primitives (M4/M7 fix, pre-existing)
**Мобильная работа:** полное закрытие (proot + PR; domain + presentation, гейт на CI).

**Статус:** запланировано.

**Контекст:** M018 (clean-audit, принято 2c в PR-4): `OrderFormMediator` собирает `TradingStateSnapshot` в presentation (M4/M7). Наследие `OrderFormViewModel.saveState` до PR-3. Fix — `SaveFormStateUseCase` принимает primitives (7 параметров), собирает snapshot **внутри domain**. Устраняет M4/M7 для persist-формы.

**Соответствие Clean:** соответствует (M4/M7 устраняются).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**MVVM:** соответствует (после fix presentation не конструирует domain-модель).

**KMP:** соответствует (usecase в domain, без platform).

**Предусловия:** PR-4 merged (Mediator написан, `OrderFormMediator.kt` собирает snapshot).

**Файлы:**
- `domain/usecases/SaveFormStateUseCase.kt` — signature 7 primitives, snapshot внутри.
- `domain/usecases/SaveFormStateUseCaseTest.kt` — 7 кейсов (null cards, defaults, full).
- `presentation/screens/orders/OrderFormMediator.kt` — передаёт primitives, не импортирует `TradingStateSnapshot`.
- `docs/clean-audit.md` — M018 → `### Исправленные (L1)`.

**Шаги:**
1. `SaveFormStateUseCase.execute(selectedCard, pairedCard, pairTradingEnabled, quantity, pairedMultiplier, orderType)` — собирает snapshot внутри.
2. `SaveFormStateUseCaseTest` — 7 кейсов: null cards, defaults, full, empty, mixed.
3. `OrderFormMediator.updatePrimitives` → `saveFormStateUseCase.execute(...)` (primitives напрямую); remove `TradingStateSnapshot` import.
4. `clean-audit`: M018 → `### Исправленные (L1)`, статус «исправлено».
5. Гейт.

**Тесты:** 7 unit-кейсов `SaveFormStateUseCaseTest`; Mediator-тесты без изменений (если есть).

**Референсы:** M018 (clean-audit), PR-2 (`SaveFormStateUseCase` создан), PR-4 (Mediator). paritytech/layered-architecture «DTO ↔ domain: mandatory, always map at data layer».

**Ограничения:**
- Не менять `TradingStateSnapshot` (domain model без изменений).
- Не менять `TradingStateRepository` (сигнатура `save(snapshot)` — внутри usecase).
- Signature 7 primitives — если при PR-4 добавится поле в snapshot, расширить (это осознанная цена).

**Критерий готовности:**
- `SaveFormStateUseCase.execute(7 primitives)`; snapshot внутри usecase.
- `OrderFormMediator` не импортирует `TradingStateSnapshot`.
- M018 → `### Исправленные (L1)`.
- Гейт зелёный.

**Оценка:** ~1.5 ч.

**Зависимости:** PR-4 merged.

**Связано:** M018 (clean-audit), PR-2 (`SaveFormStateUseCase`), PR-4 (Mediator).

**Открытые вопросы:** нет.

---
<!-- SUBSECTION_END: t-11 -->










<!-- SUBSECTION_BEGIN: t-12 -->
<a id="t-12"></a>
### [PLAN] T-12 Архитектурный документ (docs/architecture.md)
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

---
<!-- SUBSECTION_END: t-12 -->










<!-- SUBSECTION_BEGIN: t-37 -->
<a id="t-37"></a>
### [PLAN] T-37 docs/live-test.md: mobile-ограничения (Robolectric vs device)
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

---
<!-- SUBSECTION_END: t-37 -->










<!-- SUBSECTION_BEGIN: t-38 -->
<a id="t-38"></a>
### [PLAN] T-38 Live-test: C2 Orders form 7 сценариев
**Мобильная работа:** невозможно (live-test на устройстве).

**Статус:** запланировано.

**Контекст:** C2 (split `OrderFormViewModel` на 4 sub-VM + Mediator + UI-orchestration) реализован в feature/orderform-split-c2-p4b. Roborazzi (8 PNG) + property (2+2) + Extended behavioural (20) — зелёные. Но live-test 7 сценариев на устройстве (desktop, Android 14) — не выполнен. Это часть Definition of Done для C2; отложен после PR-4b (5d: отложенные тесты фиксируются явно).

**Соответствие Clean:** соответствует.

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**MVVM:** соответствует.

**KMP:** — (не применимо, presentation).

**Предусловия:** PR-4b merged.

**Файлы:** нет (live-test, без правок).

**Шаги:**
1. Запустить эмулятор Android 14.
2. Проверить 7 сценариев: (1) instrument select primary / pair; (2) quantity / limit / stop / orderType; (3) confirm buy; (4) confirm sell; (5) success snackbar; (6) restore after restart; (7) pair trade.
3. Собрать лог (`adb logcat`, фильтр по `Order*` / `OrdersScreen`).
4. PASS / FAIL по каждому сценарию.

**Тесты:** live-test.

**Референсы:** `docs/live-test.md`; `docs/architecture.md` → «C2 target»; PR-4b (Phase 8).

**Ограничения:** не путать с Roborazzi (JVM-скриншоты ≠ device). Не смешивать с другими live-тестами (Broker lifecycle, etc.).

**Критерий готовности:** 7 сценариев PASS на Android 14.

**Оценка:** ~30 мин.

**Зависимости:** PR-4b merged.

**Связано:** `### ✅ OrderFormViewModel: split на sub-VM (C2, снятие @Suppress) + 2 test infra prereqs` (`docs/roadmap/ROADMAP-ARCHIVE.md`).

**Открытые вопросы:** нет.

---
<!-- SUBSECTION_END: t-38 -->










<!-- SUBSECTION_BEGIN: t-45 -->
<a id="t-45"></a>
### [PLAN] T-45 Миграция docs/*.md под канон
**Мобильная работа:** полное закрытие (proot + PR; docs, гейт на CI).

**Статус:** запланировано.

**Контекст:** канон `docs/docs-structure.md` (§1) требует `FILE_BEGIN`/`FILE_END` от **любого** `.md` без исключений. Двенадцать файлов `docs/` не имеют этих маркеров вообще: часть — вообще без структурных маркеров, часть — со старым `___` вместо `---` и без якорей. Пока они такие, гейт `AGENTS/scripts/docs_lint.py --all` не может быть зелёным по всей области, поэтому в T-44 его область сужена до `AGENTS/` + `docs/roadmap/`. Задача закрывает остаток и снимает сужение.

**Соответствие Clean:** — (не применимо, документация).

**Нарушенное правило Clean:** —

**Clean-альтернатива:** —

**Выгоды (для «отклонение по 2c»):** —

**MVVM:** — (не применимо).

**KMP:** — (не применимо).

**Предусловия:** T-44 закрыт (канон, нормализатор, гейт).

**Файлы:**
- `docs/architecture.md`
- `docs/brokers.md`
- `docs/ci-cd.md`
- `docs/domain_models.md`
- `docs/git-flow.md`
- `docs/gradle-setup.md`
- `docs/ISSUES.md`
- `docs/live-test.md`
- `docs/rate-limits.md`
- `docs/release-process.md`
- `docs/repo-protection.md`
- `docs/streams.md`

**Шаги:**
1. По каждому файлу — `docs_normalize.py --check --file`, зафиксировать отклонения в задаче.
2. Снять сужение области в `AGENTS/scripts/docs_lint.py --all`: вернуть `docs/**` в `SCOPE_DIRS`.
3. Прогон `docs_normalize.py --apply` по файлам от малых к крупным (`repair-process`, `repo-protection`, затем `git-flow`, `gradle-setup`, в конце `streams` и `architecture`), после каждого — `git diff` глазами.
4. `docs/streams.md` — отдельным коммитом: там ожидаемо больше всего расхождений (нет FILE/SECTION-маркеров).
5. `docs/ISSUES.md` — уточнить при старте, нужен ли табличный индекс: формат журнала не табличный, но §1 требует маркеры и секции.

**Тесты:** `docs_normalize.py --check` по каждому файлу → `deviations=0`; `docs_lint.py --all` по всей области → `ALL CHECKS PASSED`.

**Референсы:** `docs/docs-structure.md` §1–§4; T-44 (канон и инструменты); `AGENTS/scripts/README.md`.

**Ограничения:** содержательный текст не менять — только скелет (маркеры, якоря, `---`, отступы). `docs/issues/` и `docs/clean-audit/` вне области (§1). Архивы — только уровень 1.

**Критерий готовности:** все 12 файлов проходят `docs_normalize.py --check` в ноль; гейт зелёный по `docs/**`; сужение области в гейте снято.

**Оценка:** 3–5 ч. (малые файлы по 15–20 мин, `streams.md` 1–1.5 ч).

**Зависимости:** T-44.

**Связано:** T-2 (консолидация скриптов), T-44.

**Открытые вопросы:** нужен ли `docs/ISSUES.md` табличный индекс — или достаточно маркеров и секций; решается на шаге 5.

---
<!-- SUBSECTION_END: t-45 -->










<!-- SECTION_END: roadmap-plan -->










<!-- FILE_END -->
