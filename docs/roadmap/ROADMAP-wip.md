<!-- SECTION_BEGIN: roadmap-wip -->
## В работе










<!-- SUBSECTION_BEGIN: t-3 -->
___
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
| 5 | `PortfolioScreen: retry с backoff` (new; **не rate-limit**, UX) | UX | live-test 6 |

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










<!-- SUBSECTION_BEGIN: t-6 -->
___
### [WIP] T-6 Search: SearchItem (partial) + lazy full on select
**Мобильная работа:** полное закрытие (proot + PR; гейт на CI).

**Статус:** в работе.

**Ветка:** feature/search-n-plus-1

**Контекст (диагностика 2026-11-17).**
- `TInvestInstrumentsService.findInstruments` = 1 `findInstrumentShorts` + N `fetchProtoInstrument` (N+1 unary). Каждый `fetchProtoInstrument` — `getInstrumentBy(uid)`.
- C2 добавил `updateStatuses(ids)` → +N `getTradingStatus`. С T-7 (cache hit skip) и T-8 (batch) — эти N теперь по 1 unary на чанк (после T-8), но N+1 в `findInstruments` остаётся.
- Live-log 2026-11-16: 1252 `getInstrumentBy` за 5 мин (второй по частоте после `getTradingStatus`).
- `InstrumentShort` (proto 1.51) содержит: `figi`, `ticker`, `classCode`, `instrumentType`, `name`, `uid`. **Нет** `isin`, `lot`, `currency`, `settlementCurrency`, `priceStepCost`, `minPriceIncrement`, `expirationDate`.
- `Instrument.isin` — **non-null** в domain-модели → partial без изменения контракта невозможен.
- UI search использует только `ticker`, `name`, `instrumentType` (`SearchResultItem`); после выбора — full (`selectedInstrument` в state, `formatCostOverlay`, `OrderTradeDetailsViewModel`).
- **Механизм lazy-full уже готов:** `SelectInstrumentUseCase.execute` вызывает `instrumentRepository.getInstrument(uid)` (кэш + `fetchFullInstrument`), получает `actualInstrument`, возвращает `card` + `pointValue`. `card.instrument = actualInstrument` — но не выставлен в state напрямую (state = переданный `instrument`).

**Решение (вариант A).** Новая domain-модель `SearchItem` для результатов поиска; full `Instrument` — lazy на select через существующий `SelectInstrumentUseCase`.
1. `domain/models/SearchItem`: `tscalpInstrumentId`, `brokerName`, `ticker`, `name`, `instrumentType`, `figi?`, `classCode?`.
2. `TInvestInstrumentsService.findInstruments` → `List<SearchItem>`: `findInstrumentShorts` + маппинг `InstrumentShort` → `SearchItem` (`short.uid` → `tscalpInstrumentId`). **Без** `fetchProtoInstrument`. 1 unary.
3. `TInvestRestService.findInstrumentsRestFallback` — аналогично (из `InstrumentShort`-DTO).
4. `SearchCacheRepository` / `SearchCacheImpl` — `List<SearchItem>` (кеш).
5. `SearchInstrumentsUseCase.execute` → `AppResult<List<SearchItem>>`. `updateStatuses` — по `SearchItem.tscalpInstrumentId` (как сейчас, но список короче).
6. `SlotSearchState.results: List<SearchItem>`, `OrdersSearch` / `SearchResultItem` — `SearchItem`.
7. `onInstrumentSelected(SearchItem)` → `SelectInstrumentUseCase.execute(item.toIdentity(), accountId)` → `actualInstrument` (кэш или 1 `getInstrumentBy`) → `state.selectedInstrument = actualInstrument`.
8. `SelectInstrumentUseCase`: `card.instrument = actualInstrument` (не переданный); в state — `actualInstrument` (full).

**Результат.**
- Search: **1 unary** (было N+1; после T-8 — N+1 + N/100).
- Select: **0–1** (`getInstrumentBy` только если uid нет в кеше).
- `selectedInstrument` — full, downstream без изменений.

**Отклонённые альтернативы.**
- **B — кэш без SearchItem.** `InstrumentRepository` кэш уже есть; повторный поиск не делает N+1, но **первый — N+1**. T-6 по существу не закрывает. Рассмотреть, если A окажется слишком дорогим по объёму.
- **C — `Instrument` с полями из `InstrumentShort`, `isin = ""`.** Нарушает семантику контракта (`isin` non-null, но пустой); техдолг. Не рекомендую.
- **D — top-K full.** Search: full для первых K (видимых), partial для остальных. Сложнее A, без выгоды.

**Соответствие Clean:** соответствует (новая domain-модель `SearchItem`; `SelectInstrumentUseCase` — существующий orchestration; data-layer без протечки в domain).

**Нарушенное правило Clean:** —.
**Clean-альтернатива:** —.
**Выгоды:** —.
**MVVM:** соответствует (presentation потребляет `SearchItem`; orchestration в usecase).
**KMP:** соответствует (`SearchItem` — data class; `kotlinx.coroutines`; без platform).

**Предусловия:** T-5 (merged), T-7 (merged), T-8 (merged).

**Файлы.**
- `domain/models/SearchItem.kt` (новый).
- `data/api/tinvest/TInvestInstrumentsService.kt` — `findInstruments` → `List<SearchItem>`.
- `data/api/tinvest/TInvestRestService.kt` — `findInstrumentsRestFallback` → `List<SearchItem>`.
- `domain/api/roles/UserInstrumentsApi.kt` — контракт `List<SearchItem>`.
- `domain/api/repository/SearchCacheRepository.kt` — `List<SearchItem>`.
- `data/repository/SearchCacheImpl.kt` — `List<SearchItem>`.
- `domain/usecases/SearchInstrumentsUseCase.kt` — `List<SearchItem>`.
- `presentation/screens/orders/OrderSearchState.kt` — `results: List<SearchItem>`.
- `presentation/screens/orders/OrdersSearch.kt` — `InstrumentSearchField(searchResults: List<SearchItem>)`, `SearchResultItem` — `SearchItem`.
- `presentation/screens/orders/OrderSearchViewModel.kt` — `onResultSelected(SearchItem)`; в `OrderInstrumentViewModel.onInstrumentSelected` — принять `SearchItem`, вызвать `SelectInstrumentUseCase`, обновить state full.
- `domain/usecases/SelectInstrumentUseCase.kt` — `card.instrument = actualInstrument`; `SelectInstrumentResult.instrument: Instrument`.
- `domain/models/BrokerApi.kt` (если есть `instrument` role) — `fetchFullInstrument` уже есть.
- Тесты: `SearchInstrumentsUseCaseTest`, `SearchCacheImplTest`, `TInvestInstrumentsServiceTest` (новый), `OrderSearchViewModelTest`, `SelectInstrumentUseCaseTest`.

**Шаги.**
1. `SearchItem` (domain).
2. `findInstruments` → `List<SearchItem>` (gRPC) + `findInstrumentsRestFallback` → `List<SearchItem>` (REST).
3. `UserInstrumentsApi.findInstruments` контракт + `SearchCacheRepository.search` контракт.
4. `SearchCacheImpl`.
5. `SearchInstrumentsUseCase`.
6. `OrderSearchState.results` + `OrdersSearch`/`SearchResultItem`.
7. `OrderSearchViewModel.onResultSelected` + `OrderInstrumentViewModel.onInstrumentSelected(SearchItem)`.
8. `SelectInstrumentUseCase`: `card.instrument = actualInstrument`.
9. Тесты: обновить контракты + новые кейсы (`findInstruments` → 1 unary).
10. Гейт.

**Тесты.**
- `TInvestInstrumentsServiceTest` (новый): `findInstruments` = 1 `findInstrument`, 0 `getInstrumentBy`.
- `SearchInstrumentsUseCaseTest` — `List<SearchItem>`.
- `SearchCacheImplTest` — `isCached` + `search` с `SearchItem`.
- `SelectInstrumentUseCaseTest` — `card.instrument == actualInstrument`.
- `OrderSearchViewModelTest` — `onResultSelected(SearchItem)`.

**Референсы:** `docs/ISSUES.md` → `## T-Invest REST findInstruments fallback (N+1 unary)`; umbrella T-3; T-5/T-7/T-8; `SelectInstrumentUseCase`; live-log 2026-11-16.

**Ограничения.**
- Не менять контракт `Instrument` (non-null `isin` остаётся).
- `SelectInstrumentUseCase` — единственная точка full-loading; не добавлять second.
- Search UI не показывает `isin`/`lot` — `SearchItem` их не содержит (осознанно).
- Миграция presentation: `searchResults: List<Instrument>` → `List<SearchItem>` — механическая, но в 5+ файлах. grep перед каждым.

**Критерий готовности.**
- `findInstruments` → 1 unary (gRPC), REST fallback — 1 (findInstrumentsRestFallback).
- Select → 0–1 unary (`getInstrumentBy` только на cache miss).
- `selectedInstrument` — full `Instrument`.
- UI — `SearchItem` в результатах.
- Тесты обновлены + `TInvestInstrumentsServiceTest` новый.
- Гейт зелёный.
- Live-test: `getInstrumentBy` — только на select (0 в search).

**Оценка:** ~3–4 ч (контракт-чейндж presentation + domain + data + тесты).

**Зависимости:** T-5, T-7, T-8 — merged.

**Связано:** umbrella T-3; T-7 (cache hit); T-8 (batch); T-39 (drop statuses — deferred); `docs/ISSUES.md` → `## T-Invest REST findInstruments fallback`.

**Открытые вопросы.**
- D3 (из umbrella): partial vs full — **решено: partial (SearchItem) + lazy full на select**.
- `SearchItem.instrumentType` — `InstrumentType` (domain enum) или `String` (proto)? — `InstrumentType`, маппинг в data.
<!-- SUBSECTION_END: t-6 -->










<!-- SUBSECTION_BEGIN: t-33 -->
___
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




___
<!-- SUBSECTION_END: t-33 -->










<!-- SUBSECTION_BEGIN: t-34 -->
___
### [WIP] T-34 Расшифровка кодов ошибок брокеров
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
<!-- SUBSECTION_END: t-34 -->





























<!-- SECTION_END: roadmap-wip -->
