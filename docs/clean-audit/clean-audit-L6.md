<!-- SECTION_BEGIN: clean-audit-L6 -->
## L6 — data/repository

<!-- SUBSECTION_BEGIN: Н66 -->
___
### Н66 Н66. getTradingStatuses — N параллельных unary

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** ✓

**Описание:** `TInvestMarketDataService.getTradingStatuses`: `coroutineScope { ids.map { async { getTradingStatus(uid) } }.awaitAll() }` — N параллельных unary-запросов. При 100 инструментах — 100 параллельных запросов. При превышении лимита T-Invest (`RESOURCE_EXHAUSTED`) — часть падает в `UNKNOWN`.

**Нарушенное правило Clean:** не строго Clean — не учитывает rate-limit внешнего API.

**Clean-альтернатива:** через rate-limiter (ROADMAP `### Rate-limiter для unary-запросов T-Invest`) или батчинг (если API даст batch-метод). Плюсы: нет `RESOURCE_EXHAUSTED`. Минусы: —.

**Выгоды:** быстрее, чем последовательный вызов.

**Ссылка на задачу:** `docs/ROADMAP.md` → `### Rate-limiter для unary-запросов T-Invest`.

<!-- SUBSECTION_END: Н66 -->

<!-- SUBSECTION_BEGIN: Н67 -->
___
### Н67 Н67. postStopOrder — закомментированный expireDate

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** ✓

**Описание:** `TInvestOrdersService.postStopOrder`: `// if (request.expireDate != null) builder.setExpireDate(parseDate(request.expireDate))`. `parseDate` закомментирован. Цепочка `expireDate` рвётся в 4+ местах (UI, ViewModel, UseCase, Service, parseDate). UI физически не может выбрать `GOOD_TILL_DATE` — всегда уходит `GOOD_TILL_CANCEL`.

**Нарушенное правило Clean:** не строго Clean — нереализованная фича, размазанная по слоям.

**Clean-альтернатива:** восстановить цепочку: UI (DatePicker) → ViewModel (`expireDate`) → UseCase (`StopOrderRequest.expireDate`) → Service (`parseDate` + `builder.setExpireDate`). Плюсы: работает `GOOD_TILL_DATE`. Минусы: ~2–3 ч.

**Выгоды:** работает `GOOD_TILL_CANCEL` (default), пользователь не может выбрать дату.

**Ссылка на задачу:** `docs/ROADMAP.md` → `### UI выбора даты истечения стоп-заявки (GOOD_TILL_DATE)`.

<!-- SUBSECTION_END: Н67 -->

<!-- SUBSECTION_BEGIN: Н72 -->
___
### Н72 Н72. BcsBrokerApi.orders = null при работающем postOrder

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** ✓

**Описание:** `BcsBrokerApi.orders = null`, но `postOrder` реализован (HTTP POST). `InvestRepository.postOrderResult` использует `broker.orders?.postOrder` → для BCS всегда `Failure`, хотя `postOrder` работает. `postOrder` доступен только через fat-метод `BrokerApi.postOrder`.

**Нарушенное правило Clean:** не строго Clean — nullable-val supertype «все методы»; BCS честно не ставит supertype, но fat-метод работает в обход роли.

**Clean-альтернатива (по комментарию в закрытой #4):** разделить `OrdersApi` на `OrdersQueryApi` + `OrdersCommandApi`; BCS реализует `OrdersCommandApi` (postOrder), `OrdersQueryApi = null`. Плюсы: BCS-торговля доступна через роль. Минусы: +1 роль.

**Выгоды:** —.

**Ссылка на задачу:** `### Реализация BCS Broker API` (ROADMAP — не заведена).

<!-- SUBSECTION_END: Н72 -->

<!-- SUBSECTION_BEGIN: Н73 -->
___
### Н73 Н73. FinamBrokerApi.checkTradeAvailability — всегда Success

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** ✓

**Описание:** `FinamBrokerApi.checkTradeAvailability(...) = TradeCheckResult.Success` — фиктивный контракт. Finam — заглушка, все данные пустые. UI (через `InvestRepository.checkTradeAvailabilityResult`) думает, что сделка возможна → отправляет заявку → `postOrder` возвращает фиктивный `OrderResult("", 0L, 0L, OrderStatus.NEW)`.

**Нарушенное правило Clean:** не строго Clean — фиктивный контракт вместо честного «не поддерживается».

**Clean-альтернатива:** `TradeCheckResult.Error("Finam API не поддерживается")` или `throw NotImplementedError`. Плюсы: UI видит реальное состояние. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `### Реализация Finam Broker API` (ROADMAP).

<!-- SUBSECTION_END: Н73 -->

<!-- SUBSECTION_BEGIN: Н74 -->
___
### Н74 Н74. FinamBrokerApi.subscribePositions — dead callbackFlow

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `FinamBrokerApi.subscribePositions` — `callbackFlow` с polling-loop раз в 10 секунд, но `fetchPortfolioPositions` возвращает `emptyList()` (заглушка). Loop крутится вхолостую. При этом `streams = null` — менеджеры используют `broker.streams?.subscribePositions ?: skip`, значит fat-метод — dead code.

**Нарушенное правило Clean:** не строго Clean — dead code, вхолостую крутящий ресурсы.

**Clean-альтернатива:** `return emptyFlow()` (как у BCS) или `flowOf()`. Плюсы: без вхолостую крутящегося loop. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `### Реализация Finam Broker API` (ROADMAP).

<!-- SUBSECTION_END: Н74 -->

<!-- SUBSECTION_BEGIN: Н75 -->
___
### Н75 Н75. FinamBrokerApi — 6 TODO("Not yet implemented")

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `FinamBrokerApi`: `openSandboxAccount`, `closeSandboxAccount`, `sandboxPayIn`, `cancelOrder`, `postStopOrder`, `cancelStopOrder` — `TODO("Not yet implemented")`. По AGENTS «Запрещено оставлять `TODO` без согласования». Безопасно благодаря nullable-val (`sandbox = null`, `orders = null`), но при появлении роли — упадёт.

**Нарушенное правило Clean:** не строго Clean — `NotImplementedError` вместо честной заглушки.

**Clean-альтернатива:** `emptyList()` / `throw UnsupportedOperationException("...")` (как BCS) или `error("Not implemented: <метод>")` с KDoc. Плюсы: явная семантика. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `### Реализация Finam Broker API` (ROADMAP).

<!-- SUBSECTION_END: Н75 -->

<!-- SUBSECTION_BEGIN: Н76 -->
___
### Н76 Н76. KDoc ссылается на несуществующую ### Реализация BCS Broker API

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `BcsBrokerApi.kt` KDoc: «Восстановление — задача `### Реализация BCS Broker API` в ROADMAP». Такой задачи в `docs/ROADMAP.md` **нет** (есть только `### Реализация Finam Broker API`). Комментарии в `BcsBrokerApi` и `docs/ISSUES.md` тоже упоминают её как существующую.

**Нарушенное правило Clean:** AGENTS 5e (self-sufficiency) — ссылка на несуществующую задачу. Плюс — дырка в планировании.

**Clean-альтернатива:** завести `### Реализация BCS Broker API` в `docs/ROADMAP.md` (план по 5e) **или** переформулировать KDoc «задача не заведена». Плюсы: ссылка работает. Минусы: новая задача в ROADMAP. **Решение:** оставлено как находка (пользователь: «оставить как находку»).

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена, KDoc висит).

<!-- SUBSECTION_END: Н76 -->

<!-- SUBSECTION_BEGIN: Н77 -->
___
### Н77 Н77. BcsBrokerApi.subscribePositions → emptyFlow(), polling-loop удалён

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `BcsBrokerApi.subscribePositions = emptyFlow()`. KDoc: «Polling-loop был сломан (транспорт BCS не отлажен), удалён. Восстановление — задача `### Реализация BCS Broker API`». `emptyFlow()` = «поток сразу завершился», не «нет данных». Плюс — покрытие polling через REST потеряно.

**Нарушенное правило Clean:** не строго Clean — заглушка маскирует «не поддерживаю» под «стрим есть, но пустой».

**Clean-альтернатива:** убрать fat-метод из BCS-реализации (Kotlin требует заглушку из `BrokerApi`) либо `throw UnsupportedOperationException`. Плюсы: честно. Минусы: ломает fat-контракт `BrokerApi` (до Н25).

**Выгоды:** —.

**Ссылка на задачу:** `### Реализация BCS Broker API` (ROADMAP — не заведена).

<!-- SUBSECTION_END: Н77 -->

<!-- SUBSECTION_BEGIN: Н78 -->
___
### Н78 Н78. BcsBrokerApi использует OkHttpClient() без CustomTrustManager

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `BcsBrokerApi.kt`: `private val client = OkHttpClient()` — отдельный клиент без российских CA. Сейчас работает (BCS на стандартных CA), но мина: если BCS введёт российские CA — TLS сломается тихо. Инстанс не переиспользуется с DI.

**Нарушенное правило Clean:** не строго Clean — data-слой конструирует ресурс без общей конфигурации.

**Clean-альтернатива:** инжектить `HttpClient`/`OkHttpClient` из DI, единая конфигурация (`CustomTrustManager`). Плюсы: единая точка TLS. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `docs/ISSUES.md` → «BcsBrokerApi создаёт OkHttpClient без CustomTrustManager».

<!-- SUBSECTION_END: Н78 -->

<!-- SUBSECTION_BEGIN: Н81 -->
___
### Н81 Н81. CustomTrustManager — CustomX509TrustManager lint-warning (принято 2c)

**Статус:** принято 2c.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `lint-baseline.xml` содержит `CustomX509TrustManager` warning для `data/api/CustomTrustManager.kt:12`. Android lint предупреждает, что custom X509TrustManager может быть insecure. Здесь — осознанно: нужны Russian Trusted Root CA для T-Invest (system trust их не знает).

**Нарушенное правило Clean:** не Clean в строгом смысле — нарушение общего lint-правила.

**Clean-альтернатива:** перейти на системный `Network Security Config` с `trust-anchors` (XML-конфигурация дополнительных CA). Плюсы: без кастомного `X509TrustManager`, lint-warning уходит, конфигурация декларативна. Минусы: **ban protection** — Google Play / Android OS могут ограничить `<trust-anchors>` для нестандартных CA (policy, targetSdk bump, Play Integrity gates); custom-путь не зависит от NNC policy. **GOST 2025 / sub-CA 2024/2025 могут не парситься в NNC** (Conscrypt XML parser gaps); custom принимает любой валидный X.509.

**Выгоды (по 2c):** **ban protection** — custom-путь не зависит от NNC policy (Play Store / Android OS могут ограничить `<trust-anchors>`); NNC может не принять GOST 2025 / sub-CA 2024/2025 (Conscrypt XML parser gaps); fail-fast Н79/Н80 в коде — degraded state невозможен; baseline зафиксировал generic lint-warning как точку контроля (при изменении `CustomTrustManager` baseline не совпадёт → lint упадёт).

**Ссылка на задачу:** PR #144 (merge `f3d3d916`); `CustomTrustManager.kt` class KDoc — ban-protection rationale; `CustomTrustManagerHappyPathTest` — 5 CA happy path. Связано с `docs/ISSUES.md` → «BcsBrokerApi создаёт OkHttpClient без CustomTrustManager» (потенциальный переезд на NNC затронет всех).

<!-- SUBSECTION_END: Н81 -->

<!-- SUBSECTION_BEGIN: Н97 -->
___
### Н97 Н97. TInvest*Service + *Api — unary: throw вместо AppResult (смешанный контракт)

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** -.

**Описание:** 6 `TInvest*Service` (`Prices`, `Trading`, `Portfolio`, `Instruments`, `Accounts`, `Orders`) возвращают «сырые» типы (`Map<String, Double?>`, `List<Instrument>`, `List<Order>`), при ошибке — throw. `AppResult` появляется только на границе `*RepositoryImpl` через `runCatchingAppResult`; там **все** исключения мапятся в `AppError.Unknown`, включая `RateLimitTimeoutException` (введён в 1a). Семантика «rate limit vs unknown» теряется. Domain-роли (`MarketDataApi`, `PortfolioApi`, …) тоже возвращают «сырые» типы.

**Нарушенное правило Clean:** contract consistency — data-слой на границе слоя уже `AppResult`, но внутри — throw; `runCatchingAppResult` = generic catcher, маскирующий семантику.

**Clean-альтернатива:** все unary-методы `TInvest*Service` и соответствующих domain-ролей — `AppResult<T>`; `AppError.RateLimited` — отдельный подтип; `runCatchingAppResult` не нужен для unary (streaming остаётся throw). Плюсы: самодокументированный контракт, семантика ошибок, единая точка конвертации (в сервисе, не на границе). Минусы: правка 6 сервисов + 7 ролей + presentation callsites (~4–6 ч).

**MVVM-альтернатива:** — (не применимо, data-layer).

**Ссылка на задачу:** `### TInvest*Service + *Api: unary → AppResult` (ROADMAP, follow-up к 1a).

**Связано:** 1a (`RateLimitTimeoutException`); Н46 (закрыт PR #75 — `SearchCache.search` → `AppResult`, тот же паттерн для одного метода).

<!-- SUBSECTION_END: Н97 -->

<!-- SECTION_END: clean-audit-L6 -->
