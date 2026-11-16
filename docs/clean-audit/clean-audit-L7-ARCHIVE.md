<!-- SECTION_BEGIN: clean-audit-L7-ARCHIVE -->
## L7 — di (архив)










<!-- SUBSECTION_BEGIN: clean-audit-93 -->
___
### Исправленные (L7)
<!-- SUBSECTION_END: clean-audit-93 -->





<!-- SUBSECTION_BEGIN: Н37 -->
___
### Н37 ✅ Н37. Конвенции DI-файлов

**Статус:** исправлено (2026-11-11, PR #129, merge `bef308e2`). Rename
`RepositoryModule.kt` → `UseCaseModule.kt` (file-convention aspect);
`@Provides` для stateless usecases — по 2e → KMP → DI. Остаток
(24 usecases с `@Inject`/`@Singleton`) → задача
`### Н37b: usecases без DI-аннотаций`.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `RepositoryModule.kt` содержит **два** `@Module` (`RepositoryModule` + `UseCaseModule`). По конвенции — отдельные файлы. `@Provides` для stateless-классов (`CalculateTradeDetailsUseCase`, `PrepareOrderRequestUseCase`) — корректен по 2e → KMP → DI (domain без DI-аннотаций); `@Inject constructor()` на usecases — не применять (шаг назад от KMP). `BcsBrokerApi` / `FinamBrokerApi` — отдельная задача (`data/`, KMP не блокирует).

**Нарушенное правило Clean:** конвенция DI-файлов (не Clean-инвариант).

**Clean-альтернатива (чисто):** `UseCaseModule.kt` отдельным файлом; `@Provides` для usecases (без `@Inject constructor()` — по 2e → KMP → DI). Плюсы: конвенция; domain без DI-аннотаций (KMP-ready). Минусы: boilerplate в `@Provides`.

**Выгоды:** —.

**Что сделано:** `di/RepositoryModule.kt` → `di/UseCaseModule.kt` (git mv, 100% rename, content 1:1). `@Provides` для `CalculateTradeDetailsUseCase` / `PrepareOrderRequestUseCase` в `UseCaseModule.kt` — сохранены (2e → KMP → DI). `RepositoryModule` object (H21b) уже был удалён ранее. CI green `9e6d37ee`.

**Ссылка на задачу:** PR #129 (merge `bef308e2`). 24 usecases с `@Inject` — задача `### ✅ Н37b: usecases без DI-аннотаций (2e → KMP → DI)` (`docs/roadmap/ROADMAP-ARCHIVE.md`, PR #134, merge `1fe8a869`).

<!-- SUBSECTION_END: Н37 -->

<!-- SUBSECTION_BEGIN: Н36 -->
___
### Н36 ✅ Н36. provideTInvestWsHttpClient — транспортная конфигурация в di

**Статус:** исправлено (merge `9f352bbe`, PR #128).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `DataModule.provideTInvestWsHttpClient` содержит конфигурацию `HttpClient` (CIO engine, WebSockets, ContentNegotiation, trustManager). Транспортная конфигурация в сборочном слое. При появлении второго клиента (например, для REST) — дублирование конфига.

**Нарушенное правило Clean:** транспортная конфигурация в di-слое.

**Clean-альтернатива (чисто):** `data/transport/TInvestHttpClientFactory` (data) — `fun create(context: Context): HttpClient`. `DataModule.provideHttpClient(factory: TInvestHttpClientFactory): HttpClient = factory.create(context)` — тонкий DI. Плюсы: конфиг в data, DI чистый. Минусы: переезд.

**Выгоды:** —.

**Что сделано:** `data/transport/TInvestHttpClientFactory.kt` (новый) — top-level `createTInvestWsHttpClient(context, logger): HttpClient` с CIO engine, WebSockets, ContentNegotiation, `CustomTrustManager`. `DataModule.provideTInvestWsHttpClient` → 1-line delegate; удалены неиспользуемые импорты (`CIO`, `WebSockets`, `ContentNegotiation`, `json`, `Json`, `CustomTrustManager`). `HttpClient`-импорт сохранён (return type + `provideTInvestBrokerAPI`). CI green `bc28cf8a`.

**Ссылка на задачу:** PR #128 (merge `9f352bbe`). Закрывает clean-audit H36 (когда-то «не приоритет», взято в работу 2026-11-11).

---

<!-- SUBSECTION_END: Н36 -->

<!-- SUBSECTION_BEGIN: Н29 -->
___
### Н29 ✅ Н29. BrokerManager — domain-сервис в di/

**Статус:** исправлено (PR #60, `dev`, 2026-10-28).

**Серьёзность:** критично.

**Live тест:** -

**Описание:** `di/BrokerManager.kt` содержит domain-логику: `computeAnyInitialized()` (вычисление), `refreshInitializationState()` (публичный side-effect), `getDefaultBroker(): BrokerApi` throws `IllegalStateException("Брокер TInvest не зарегистрирован")` (знает «TInvest = default»), `anyInitialized: StateFlow<Boolean>` (реактивный контракт). Все потребители: `data/repository/*` (3), `domain/api/managers/*` (3), `TScalpApplication` (1), `SettingsViewModel` (4 callsites) инжектят `di.BrokerManager`.

**Нарушенное правило Clean:** dependency rule — domain (managers), presentation, data зависят от `di` (сборочный слой). `di/` не должен содержать доменную логику.

**Clean-альтернатива:** `domain.registry.BrokerRegistry` — domain-сервис; implementation в `data/registry/`; `@Binds` в `di/`. Плюсы: domain-сервис в domain, `di/` содержит только DI. Минусы: переезд ~10 файлов-потребителей. `refreshInitializationState` — внутренний, не в интерфейсе (см. `**Анализ L1-Н22:**`).

**Выгоды:** —.

**Анализ L7-part1:** подтверждается — `BrokerManager` — единственная утечка `di/` наружу (10 файлов, grep 2026-10-27). Плюс: `provideBrokerManager` содержит domain-конфигурацию (список брокеров, порядок вкладок) — расширяет Н29 до «`BrokerManager` + его конфигурация в `di/`». См. Н35.

**Открытый вопрос (H22):** `refreshInitializationState` — `internal` в `BrokerRegistryImpl` (concrete type протекает наружу) или `fun refreshInitializationState()` в интерфейсе `BrokerRegistry` (side-effect в domain-интерфейсе) или автоматически в `BrokerLifecycleApi.initialize` (циклическая зависимость). Решение — при выполнении H22.

**Анализ L5-Н40:** подтверждается — 3 менеджера (`PositionFlowManager`, `PriceFlowManager`, `OrderFlowManager`) — часть списка потребителей `di.BrokerManager` (10 файлов). См. L5-Н40.

**Анализ L6-Н44:** подтверждается — потребители `di.BrokerManager` включают 3 data/repository (`SearchCache`, `InstrumentRepository`, `InvestRepository`). Итого 10: 3 VM (L1) + 3 менеджера (L5) + 3 репозитория (L6) + 1 app (L8).

**Ссылка на задачу:** `### H22. BrokerRegistry` (PR #60, `dev`).

<!-- SUBSECTION_END: Н29 -->

<!-- SUBSECTION_BEGIN: Н35 -->
___
### Н35 ✅ Н35. provideBrokerManager — domain-конфигурация в di

**Статус:** исправлено (PR #80, `dev`, 2026-10-31).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `DataModule.provideBrokerManager` содержит domain-конфигурацию: список брокеров (TInvest, BCS, Finam) и **порядок** в `mapOf`, который определяет порядок вкладок в UI (`getAvailableBrokerNames()`) и порядок в `SearchCache`/`InvestRepository`. При добавлении 4-го брокера — правка в `di/`.

**Нарушенное правило Clean:** domain-knowledge (список зарегистрированных брокеров + порядок) в di-слое.

**Clean-альтернатива (чисто):** `BrokerRegistryImpl(brokers: Set<BrokerApi>)` — строит `Map<BrokerName, BrokerApi>` через `BrokerName.entries` (порядок из enum). `defaultBroker: BrokerName = BrokerName.TINVEST` — константа в domain. `DataModule.provideBrokerRegistry(brokers: Set<BrokerApi>)` — тонкий DI. Плюсы: domain-knowledge в domain, порядок вкладок из `BrokerName.entries`. Минусы: переезд, входит в дизайн H22.

**Выгоды:** —.

**Что сделано:** `BrokerRegistry` companion — `val defaultBroker: BrokerName = TINVEST`; `BrokerRegistryImpl(brokers: Set<BrokerApi>)` — map по `broker.name`, порядок `getAvailableBrokerNames()`/`getAllBrokers()` из `BrokerName.entries`; `DataModule.provideBrokerRegistry` — одна строка `BrokerRegistryImpl(setOf(...))`; 18 тестовых конструирований обновлены + `broker.name` stub; новый `BrokerRegistryImplTest` (5 PASSED). Коммит `7494262`.

**Ссылка на задачу:** PR #80 (`dev`, 2026-10-31). Входит в дизайн H22 (`BrokerRegistry`).

<!-- SUBSECTION_END: Н35 -->

<!-- SECTION_END: clean-audit-L7-ARCHIVE -->
