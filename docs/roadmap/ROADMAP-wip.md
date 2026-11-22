<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: roadmap-wip -->
## В работе










<!-- SUBSECTION_BEGIN: t-3 -->
<a id="t-3"></a>
### [WIP] T-3 Rate-limit umbrella: cold start + search RESOURCE_EXHAUSTED
**Мобильная работа:** полное закрытие (proot + PR; гейт на CI).

**Статус:** в работе. Блокирует live-test C2 (scenarios 2–7).

**Ветка:** — (umbrella; ветки подзадач — отдельные)

**Умбрелла.** Группировка задач по rate-limit и сокращению unary-запросов. Цель: устранить `RESOURCE_EXHAUSTED` на cold start и search; заложить broker-agnostic architecture для BCS/Finam.

**Контекст (live-log C2, 2026-11-15, emulator, sandbox T-Invest):**
- **Search burst:** 6 × `TInvestInstruments: RESOURCE_EXHAUSTED` за 300ms (17:50:40.024–.314) на pair-search.
- **Причина:** `findInstruments` = N+1 unary (1 + N `getInstrumentBy`); C2 добавил `SearchInstrumentsUseCase:49` `updateStatuses(ids)` = +N `getTradingStatuses` → **2N+1 unary** на поиск. Sandbox 6/сек → burst.
- **Cold start:** 5s×N accounts polling + 10s position polling + 5 min periodic statuses + `restore` (broker init + instrument ×2 + load) → суммарная нагрузка поверх search.
- **НЕ rate-limit:** `PortfolioScreen` `LaunchedEffect(isLoading)` infinite retry (UX-баг, блокирует live-test 6).

**Подзадачи (все — отдельные задачи 5e, ниже в этом разделе):**

| # | Подзадача | Тип | Блокирует |
|---|---|---|---|
| 1a | `Rate-limiter для unary-запросов T-Invest` (existing) | защита (P0) | live-test 2–7 |
| 1b | `Stream connection limiter: WS + gRPC-streaming` (new) | защита (P0) | WS-reconnect шторм, BCS/Finam streaming |
| 2 | `Search: сократить N+1 unary в findInstruments` (new) | оптимизация | UX search |
| 3 | `updateStatuses: skip on cache hit / lazy on select` (new) | оптимизация | rate-budget |
| 4 | `getTradingStatuses: батчинг / concurrency` (new; поглощает `clean-audit` Н66) | оптимизация | rate-budget |
| 5 | `PortfolioScreen: double-load + guard` (поглощена T-9; **не rate-limit**, UX) | UX | live-test 6 |

**Порядок:** **1a → 1b → (2, 3, 4 параллельно) → 5.**
**Обоснование:** (1a) — фундамент unary: без защиты любая новая фича / фоновый запрос / periodic может снова выбить unary-лимит. (1b) — фундамент stream: WS-реконнект-шторм может выбить stream-лимит (32/16/11) молча; сокращение без защиты — «бег на месте». После (1) каждая оптимизация — безопасный refactor: «стало 2N+1 → N+1 → 1» на фоне гарантии ≤ 50/sec. (5) независима от rate-limit, но блокирует live-test 6 (restore).

**Multi-broker (2a, 2e):** rate-limits у брокеров разные:
- T-Invest: `docs/rate-limits.md` (50/sec global, 200/min Instruments gRPC, 600/min MarketData, `postOrder` 15/sec, `GetUserTariff` для динамических лимитов).
- BCS: `429 Too Many Requests`; конкретные лимиты — TBD (проверить при `### Реализация BCS Broker API`).
- Finam: REST 200 req/min (`docs/brokers.md`); gRPC — TBD.

**Архитектурное решение (2e → data-слой, ближе к транспорту):** `RateLimiter` — интерфейс broker-agnostic (в `data/api/common/`); конфиг лимитов — per-broker (`data/api/<broker>/<Broker>RateLimits.kt`). Обёртка вокруг **каждого unary-вызова** в `data/api/tinvest/TInvest*Service` (`getAccounts`, `findInstruments`, `fetchProtoInstrument`, `getTradingStatuses`, `postOrder`, …). Перед вызовом — `rateLimiter.withPermission(transport, service, method) { ... }`. Domain не знает про лимиты — они свойство транспорта. Подзадача 1 реализует для T-Invest; BCS/Finam — при интеграции.

**Разные протоколы и методы — разные лимиты (D1 = 4 уровня):**
1. **global 50/sec** — по IP, все запросы.
2. **per-(transport, service)** — gRPC и REST **разные бюджеты** (Instruments gRPC 200/min vs REST 100/min; MarketData 600/600; Orders 100/100).
3. **per-(transport, method)** — если у метода свой лимит (`getOrders` 200, `postOrder` 15/sec, `getHistory` 30 REST, `getStopOrders` 60, `cancelOrder`/`replaceOrder` 300, `postOrderAsync` 600).
4. **per-protocol fallback** — при gRPC → REST fallback (orchestrator) второй запрос идёт в **другой** budget (gRPC Instruments 200 vs REST Instruments 100), не в общий «Instruments».

**Соответствие Clean:** соответствует (organizational umbrella; подзадачи — по 2e отдельно).

**MVVM:** — (не применимо, umbrella).

**KMP:** — (не применимо, umbrella).

**Предусловия:** нет.

**Файлы (umbrella):** `docs/ROADMAP.md` — эта umbrella; `docs/ISSUES.md` — umbrella-запись.

**Шаги:** см. подзадачи 1–5. Каждая — отдельный PR.

**Тесты:** см. подзадачи. Umbrella-критерий: live-test C2 2–7 PASS без `RESOURCE_EXHAUSTED`.

**Референсы:** `docs/rate-limits.md` (обновлён 2026-11-15, [официальная страница](https://developer.tbank.ru/invest/intro/intro/limits)); `docs/ISSUES.md` → `## T-Invest rate-limit umbrella...`, `## PortfolioScreen: infinite retry...`, `## T-Invest REST findInstruments fallback (N+1 unary)`; `docs/clean-audit.md` → Н66; live-log C2 (2026-11-15); `docs/brokers.md`.

**Ограничения:**
- Не смешивать с `### Rate-limiter...` — это подзадача, umbrella только группирует.
- Multi-broker: не реализовывать BCS/Finam-лимиты сейчас (нет интеграции), только архитектурная готовность (2a).
- Не блокировать PR #160 — он уже merged; umbrella — новая работа.

**Критерий готовности:** live-test C2 2–7 PASS; `RESOURCE_EXHAUSTED` в search — 0; rate-limiter в продакшене; search latency приемлемая (< 2 сек на 10 инструментов); Portfolio retry — bounded (3 попытки).

**Оценка:** umbrella ~6–9 ч суммарно. Подзадачи: (1a) 3–5 ч; (1b) 1–2 ч; (2) 1–1.5 ч; (3) 0.5–1 ч; (4) 1 ч; (5) 0.5 ч.

**Зависимости:** нет. Подзадачи независимы (кроме 2–4 → после 1).

**Связано:** PR #160 (observability + 3 fix; merged `4af3b682`); PR #162 (burst-capacity fix; merged `1a3635fb`); `docs/ISSUES.md` → `## T-Invest rate-limit umbrella...`, `## PortfolioScreen: infinite retry...`; `docs/clean-audit.md` → Н66; `docs/rate-limits.md`; `docs/brokers.md`.

**Открытые вопросы (решить в подзадаче 1):**
- **D1 (решено).** A+ split: 1a — unary (gRPC-unary + REST-unary, token bucket, 4 уровня); 1b — stream (gRPC-streaming + WS, semaphore, флаг `WS_SHARES_STREAM_BUDGET = true`). «gRPC» в конфиге заменён на «streaming» (WS = прокси для gRPC streaming, делят stream-лимит).
- **D2.** `updateStatuses` в search: skip on cache hit / lazy на select / drop?
- **D3.** `findInstruments` N+1: partial (InstrumentShort) vs full контракт? Если presentation требует full — N+1 неизбежен без API-батчинга.
- **D4.** Concurrency: `Semaphore` поверх limiter (N parallel) vs sequential?
- **D5 (решено).** Статика из `docs/rate-limits.md` (A); `GetUserTariff` (динамика, B) — отдельная follow-up задача.
- **D6 (решено).** Per-broker конфиг: `data/api/<broker>/<Broker>RateLimits.kt` (A) + флаг `WS_SHARES_STREAM_BUDGET`. Role-pattern не применим — см. «Не role» в `### Rate-limiter для unary-запросов T-Invest`.

---
<!-- SUBSECTION_END: t-3 -->










<!-- SUBSECTION_BEGIN: t-33 -->
<a id="t-33"></a>
### [WIP] T-33 Установка GrpcExceptionGuard
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

---
<!-- SUBSECTION_END: t-33 -->










<!-- SUBSECTION_BEGIN: t-9 -->
<a id="t-9"></a>
### [WIP] T-9 PortfolioScreen: double-load + TradingStatusFlowManager
**Мобильная работа:** полное закрытие (proot + PR; presentation + domain; гейт на CI).

**Статус:** в работе.
**Ветка:** feature/t-9-portfolio-double-load.

**Контекст:** `PortfolioScreen:38-42` — `LaunchedEffect(lifecycleOwner) { repeatOnLifecycle(RESUMED) { refresh() } }` дергает полный `loadPortfolio()` при каждом входе в RESUMED (back-stack, foreground, поворот). Плюс `init { brokerManager.anyInitialized.collect { checkApiInitialization() } }` — ещё один `loadPortfolio()` при первом `anyInitialized` (`PortfolioViewModel:69-75`, `:95-105`). Итог: 2+ полных загрузки на cold start. `refresh()` = `viewModelScope.launch { loadPortfolio() }`, а `loadPortfolio()` сам делает `viewModelScope.launch` — двойной launch (`:262-264`, `:128-129`); retry/backoff отсутствует (см. «Связано»: T-3 sub-5 поглощается T-9; «infinite retry» — баг LaunchedEffect(isLoading), устраняется PR 5a). Дополнительно: `tradingStatuses` в `PortfolioUiState` — своё поле (`PortfolioUiState:22`), VM поллит `while (isActive) { delay(5 min); updateTradingStatuses() }` (`:83-91`) и сам вызывает `marketDataRepository.getTradingStatusesResult` (`:214-235`) — дублирует `UpdateTradingStatusesUseCase` + `TradingStatusRepository` (M2/M5). Orders уже работает passthrough (`OrdersViewModel:70`), Portfolio — нет: два независимых кэша одного значения.

**Соответствие Clean:** отклонение по 2c (принято) — менеджер поллинга в `domain/api/managers/` (по образцу `PositionFlowManager`/`PriceFlowManager`), не в VM/presentation.

**Нарушенное правило Clean:** dependency rule — периодический polling статусов (domain-policy) в VM (`while (isActive)`), при этом `TradingStatusRepository` (SSoT) существует и не используется.

**Clean-альтернатива:** (а) polling в `TradingStatusRepositoryImpl` — неверно (repository пассивный); (б) polling в `UseCase` — policy, не usecase; (в) polling в VM — оставляет M2. Правильно — менеджер в `domain/api/managers/`.

**Выгоды (для «отклонение по 2c»):** полная аналогия с существующими менеджерами (`PositionFlowManager`, `PriceFlowManager`) — паттерн проекта, не изобретение; единый SSoT (`TradingStatusRepository`) для Portfolio+Orders; rate-limit safe при 60 с (batch primary + singular fallback — T-8 закрыт).

**MVVM:** `M1` + `M2` + `M5` — закрываются в T-9: UI не командует загрузкой на RESUMED (только `LaunchedEffect(Unit)` + guard от повторной загрузки); VM не поллит domain (`setInterest`/`clearInterest`); `tradingStatuses` в UI — passthrough из `TradingStatusRepository`, поле в `PortfolioUiState` удаляется.

**KMP:** соответствует по времени/транспорту; **блокер (унаследованный)** — DI: `@Singleton`/`@Inject` в domain, как в `PositionFlowManager`/`PriceFlowManager`. Не новый; фиксируется тем же отклонением, снятие — T-21.

**Предусловия:** нет (T-8 закрыт — batch primary + singular fallback есть).

**Файлы:**
- `presentation/screens/portfolio/PortfolioScreen.kt` — `repeatOnLifecycle` → `LaunchedEffect(Unit)`; источник `tradingStatuses` (стр. 111).
- `presentation/screens/portfolio/PortfolioViewModel.kt` — убрать `while (isActive)` и свой `updateTradingStatuses`; `refresh()` без двойного launch + guard; passthrough на `TradingStatusRepository.statuses`; `setInterest`/`clearInterest`.
- `presentation/screens/portfolio/PortfolioUiState.kt` — удалить поле `tradingStatuses`.
- `domain/api/managers/TradingStatusFlowManager.kt` (новый) + `TradingStatusConsumer.kt` (новый, enum).
- `test/.../domain/api/managers/TradingStatusFlowManagerTest.kt` (новый).

**Шаги (S2 — два PR):**

**PR 5a (~20-30 мин):**
1. `PortfolioScreen.kt:38-42`: `LaunchedEffect(lifecycleOwner) { lifecycleOwner.lifecycle.repeatOnLifecycle(RESUMED) { refresh() } }` → `LaunchedEffect(Unit) { viewModel.refresh() }`; импорты `Lifecycle`, `repeatOnLifecycle`, `LocalLifecycleOwner` удалить, если больше не используются.
2. `PortfolioViewModel.refresh()`: `fun refresh() { loadPortfolio() }` (убрать внешний `launch`); в `loadPortfolio()` первой строкой тела — `if (_uiState.value.isLoading) return` (дедуп двух триггеров cold start).
3. `while (isActive)` polling оставить (удаляется в 5b, регрессии нет).
4. Гейт на CI.

**PR 5b (~2-2.5 ч):**
1. `TradingStatusFlowManager` — конкретный `@Singleton` с `@Inject constructor` (DI-модуль не нужен, как у `PositionFlowManager`):
   - `setInterest(consumer: TradingStatusConsumer, ids: Set<String>)` — регистрирует/заменяет интерес потребителя; при первом непустом объединении — immediate fetch + `while (isActive) { delay(INTERVAL_MS); fetchAndUpdate() }`;
   - `clearInterest(consumer)` — снимает интерес; при пустом объединении polling останавливается;
   - `fetchAndUpdate()` — группирует объединение ids по `brokerName` (как `PortfolioViewModel:214-229`) и вызывает `UpdateTradingStatusesUseCase.execute(brokerName, ids)` (готовый fetch+update; дублировать нельзя);
   - `INTERVAL_MS = 60_000L`; scope — `CoroutineDispatcher` из `DispatcherModule` + `limitedParallelism(1)` + `SupervisorJob` + `CoroutineExceptionHandler`.
2. `TradingStatusConsumer` (enum рядом с менеджером): `PORTFOLIO`, `ORDERS` — аналог `PriceConsumer`.
3. `PortfolioViewModel`:
   - убрать `while (isActive)` и свой `updateTradingStatuses`;
   - inject `TradingStatusFlowManager` + `TradingStatusRepository`;
   - `val tradingStatuses: StateFlow<Map<String, TradingAvailability>> = tradingStatusRepository.statuses` (passthrough, как `OrdersViewModel:70`);
   - после успешного `loadPortfolio` — `tradingStatusFlowManager.setInterest(TradingStatusConsumer.PORTFOLIO, ids)`;
   - `onCleared` — `clearInterest(PORTFOLIO)` (**не** `stop()`: менеджер общий с Orders, `stop()` убьёт чужой polling);
   - в `checkApiInitialization` ветка `NoneInitialized` — `clearInterest(PORTFOLIO)` вместо `tradingStatuses = emptyMap()`.
4. `PortfolioUiState`: удалить `tradingStatuses`; `PortfolioScreen:111` — `val tradingStatuses by viewModel.tradingStatuses.collectAsState()`.
5. Гейт на CI.

**Тесты:** `TradingStatusFlowManagerTest` — `setInterest` стартует и грузит сразу; два потребителя → union ids; `clearInterest` одного не останавливает polling; `clearInterest` последнего → polling остановлен; интервал 60 с (`advanceTimeBy`); broker-группировка (2 брокера → 2 вызова usecase). `PortfolioScreenBehaviourTest` не ломать: в тесте реальный граф Hilt, новых биндингов не требуется (`TradingStatusRepository` биндится в `RepositoryBindingModule:82`, менеджер — `@Inject`).

**Референсы:** `PositionFlowManager.kt:66-105` (scope, `startLock`, `limitedParallelism`), `PriceFlowManager.kt` + `PriceConsumer.kt` (interest-паттерн), `TradingStatusRepository.kt`, `UpdateTradingStatusesUseCase.kt:37-60`, `OrdersViewModel.kt:70` (passthrough), `TInvestTradingService.kt:52-160` (batch primary + singular fallback), live-log C2 (2026-11-15).

**Ограничения:**
- Не трогать `TradingStatusRepositoryImpl` (SSoT остаётся).
- Не трогать `UpdateTradingStatusesUseCase` (используется Search и Orders).
- Не менять UI (кроме источника `tradingStatuses` — данные, не дизайн).
- Не переносить polling в repository (концептуально неверно).
- Не удалять periodic polling совсем (биржи открываются/закрываются несколько раз в день).
- Не вызывать `stop()` менеджера из VM — менеджер общий (T-41).

**Критерий готовности:**
- 1 полная загрузка на cold start (guard от повторного `loadPortfolio`).
- 0 загрузок на циклах RESUMED (поворот, foreground) — `repeatOnLifecycle` удалён.
- `tradingStatuses` в UI — passthrough из `TradingStatusRepository`; поля в `PortfolioUiState` нет.
- Polling 60 с — в `TradingStatusFlowManager`; остановка при пустом объединении интересов.
- `RESOURCE_EXHAUSTED` в live-test 6 — 0.
- Гейт зелёный.

**Оценка:** ~2.5-3 ч (5a ~20-30 мин + 5b ~2-2.5 ч).

**Зависимости:** нет. Блокирует live-test 6.

**Поглощает:** T-40 `PortfolioUiState: passthrough tradingStatuses StateFlow` — реализуется шагами 3-4 PR 5b; отдельный PR не нужен. Секция T-40 вырезана из `ROADMAP-plan.md` (текст — в истории git).

**Связано:** T-3 umbrella sub-5 (поглощается T-9: double-load + guard + payInSandbox); T-8 (закрыт — batch+singular); T-41 (подключение ORDERS к TradingStatusFlowManager); T-42; `docs/rate-limits.md`.

**Открытые вопросы:** нет. DI-модуль для менеджера не нужен: `PositionFlowManager`/`PriceFlowManager` — конкретные `@Singleton @Inject`; `DispatcherModule:26` уже провайдит `CoroutineDispatcher`.

---
<!-- SUBSECTION_END: t-9 -->










<!-- SECTION_END: roadmap-wip -->










<!-- FILE_END -->
