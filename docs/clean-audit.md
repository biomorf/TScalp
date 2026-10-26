# Clean-audit

Справочник отклонений от Clean-архитектуры в проекте TScalp.
Фиксирует: что соответствует Clean, что отклоняется, почему.
Документ — справочник. Задачи реализации — в `docs/ROADMAP.md`.

## Шаблон записи

~~~markdown
### <ID>. <Краткое название>

**Статус:** открыто / в работе / принято (по 2c) / исправлено.

**Серьёзность:** критично / высоко / средне / низко.

**Описание:** 1–3 предложения — что именно нарушено.

**Анализ L<N>-<ID>:** подтверждается / опровергается — <что именно>. (опциональное поле, только если запись уточняется анализом другого уровня)

**Нарушенное правило:** одна строка — какое правило Clean.

**Clean-альтернатива:** как правильно — с плюсами и минусами.

**Выгоды (обязательно для «принято»):** что даёт текущий вариант.

**Ссылка на задачу:** название задачи в `docs/ROADMAP.md` / `docs/ci-roadmap.md` или `—`.
~~~

## Уровни (по приоритету)

| Уровень | Слой | Приоритет |
|---|---|---|
| L1 | presentation | 0 (первый) |
| L2 | util (grab-bag) | 1 |
| L3 | domain/models | 2 |
| L4 | domain/api | 3 |
| L5 | domain/managers | 4 |
| L6 | data | 5 |
| L7 | di | 6 |
| L8 | app | 7 |

L4–L8 — не проверены, заполняются по мере инспекции.

### Шкала серьёзности

| Значение | Значение |
|---|---|
| критично | Нарушение dependency rule, но код работает; блокирует полную Clean-миграцию |
| высоко | Архитектурное нарушение, работает; заметно усложняет поддержку |
| средне | DRY / конвенция / временный долг |
| низко | Стилистика, KDoc, мелочь |

---

## L1 — presentation

**Краткая таблица:**

| ID | Серьёзность | Затрагиваемые уровни | Локализация |
|---|---|---|---|
| Н21. ViewModel → data.repository.* | критично | L1, L6 | `OrdersViewModel`, `PortfolioViewModel`, `SettingsViewModel` |
| Н22. ViewModel → di.BrokerManager | критично | L1, L7 | те же 3 VM |
| Н1. Presentation конструирует PortfolioPosition | высоко | L1, L3 | `OrdersScreen.kt` (~353, ~580) |
| Н23. ViewModel → data.orchestrator.DebugConfig | средне | L1, L6 | `SettingsViewModel` |
| Н4. UI конвертирует String → domain-модель | средне | L1, L3 | `OrdersListDialog.kt` |
| Н24. Дублирование обогащения в двух VM | средне | L1, L3 | `OrdersViewModel.updatePositionPnl`, `PortfolioViewModel.updatePortfolioItem` |
| Н3. OrdersListViewModel в `ui.components` | низко | L1 | `ui/components/OrdersListViewModel.kt` |

### Н21. ViewModel → data.repository.*

**Статус:** открыто.

**Серьёзность:** критично.

**Описание:** три ViewModel (`OrdersViewModel`, `PortfolioViewModel`, `SettingsViewModel`) инжектят `InvestRepository`, `InstrumentRepository`, `SearchCache`, `SettingsRepository`, `TradingStateRepository` из `data.repository.*` напрямую. По Clean presentation должна зависеть от domain-интерфейса, а data — реализовывать его.

**Нарушенное правило:** Dependency rule — presentation не должна знать про data (зависимость наружу).

**Clean-альтернатива:** `domain.api.repository.*` — по одному интерфейсу на репозиторий (для `InvestRepository` частично сделано в `### Разбивка InvestRepository по доменным областям`, но не для остальных 4). VM инжектит интерфейс, data реализует через `@Binds`. Плюсы: Clean-дисциплина, KMP-совместимость, фейки в тестах. Минусы: переезд 4 репозиториев (~2–3 ч) + правка DI.

**Выгоды:** —

**Ссылка на задачу:** `### Разбивка InvestRepository по доменным областям` (частично; 4 остальных репозитория — вне scope, задача не заведена).

### Н22. ViewModel → di.BrokerManager

**Статус:** открыто.

**Серьёзность:** критично.

**Описание:** все потребители (`OrdersViewModel`, `PortfolioViewModel`, `SettingsViewModel`, `TScalpApplication`, `domain/api/managers/*`, `data/repository/*`) инжектят `com.gitlab.biomorf.tscalp.di.BrokerManager` напрямую. Вызывают `getAllBrokers()`, `getBroker(name)?.lifecycle?.initialize(...)`, `getAvailableBrokerNames()`, `refreshInitializationState()`, `getDefaultBroker()`. `BrokerManager` — точка сборки брокеров, живёт в `di/`.

**Нарушенное правило:** Dependency rule — presentation зависит от `di` (сборочный слой).

**Clean-альтернатива:** `domain.api.BrokerRegistry` — интерфейс с теми же методами (`getAllBrokers`, `getBroker`, `getAvailableBrokerNames`, `getDefaultBroker`, `refreshInitializationState`). Реализация — `data/registry/BrokerRegistryImpl` или `di/BrokerRegistryImpl`, `@Binds` в Hilt. VM инжектит `BrokerRegistry`. Плюсы: presentation → domain.api; `BrokerRegistry` в KMP-модуле. Минусы: переезд + переименование `BrokerManager` (~1–2 ч) + правка 3 VM + тестов.

**Выгоды:** —


**Анализ L7-Н29:** подтверждается — анализ L7 показал, что `BrokerManager` — domain-сервис в `di/`, нарушение шире, чем только VM: потребители включают `domain/api/managers/*` и `data/repository/*`. См. L7-Н29.

**Ссылка на задачу:** — (не заведена).

### Н1. Presentation конструирует PortfolioPosition

**Статус:** открыто.

**Серьёзность:** высоко.

**Описание:** `OrdersScreen.kt` (два места: основной инструмент ~353, парный ~580) вручную вызывает `PortfolioPosition(...)` с полями `profit = null`, `profitPercent = null` для инструмента, которого нет в портфеле. По Clean presentation имеет право читать domain-модели, но не создавать их — правила конструирования принадлежат domain. После #9 `PortfolioPosition` — именно domain-модель.

**Нарушенное правило:** Dependency rule — presentation не конструирует domain-модели в обход domain-логики.

**Clean-альтернатива:** фабрика во ViewModel (или domain-функция `emptyPortfolioPosition(instrument, currentPrice)`) — UI получает готовую модель. Плюсы: правила конструирования в domain/presentation-фабрике. Минусы: ~30 мин правок + правка `OrdersUiState`.

**Выгоды:** —

**Ссылка на задачу:** — (не заведена).

### Н23. ViewModel → data.orchestrator.DebugConfig

**Статус:** открыто.

**Серьёзность:** средне.

**Описание:** `SettingsViewModel` импортирует `data.orchestrator.DebugConfig`, читает/пишет `disableGrpc`, `disableWs`, `forceRest` через `toggleGrpc/toggleWs/toggleRest`. Debug-флаги транспорта в presentation.

**Нарушенное правило:** Dependency rule — presentation не должна знать про `data.orchestrator`.

**Clean-альтернатива:** `domain.api.DebugFlagsRepository` с геттерами/сеттерами; реализация — `data.orchestrator.DebugConfig` за интерфейсом. Плюсы: presentation → domain.api. Минусы: ~30 мин правок.

**Выгоды:** —

**Ссылка на задачу:** — (не заведена). Связано с закрытыми `### Debug-тумблеры` и `### DebugConfig: только для debug-сборок` (функционал работает; переезд API — отдельная задача).

### Н4. UI конвертирует String → domain-модель

**Статус:** открыто.

**Серьёзность:** средне.

**Описание:** `OrdersListDialog.kt` в `OrderCard` передаёт `orderType = TradePhrases.stringToOrderType(order.type)`. Конверсия «сырое значение из data → domain-модель» выполняется в presentation.

**Нарушенное правило:** Presentation не должна маппить сырые данные в domain-модели — это забота data-слоя (маппер).

**Clean-альтернатива:** `OrderListItem.type` → `OrderTypeSelection` в маппере `TInvestOrdersMapper` (data). UI получает готовый `OrderTypeSelection`. Плюсы: presentation без маппинга. Минусы: зависит от `### Split order domain into sealed Regular/Stop` (там переделка модели).

**Выгоды:** —

**Ссылка на задачу:** `### Split order domain into sealed Regular/Stop` (в его рамках решается).

### Н24. Дублирование обогащения в двух VM

**Статус:** открыто.

**Серьёзность:** средне.

**Описание:** `OrdersViewModel.updatePositionPnl` и `PortfolioViewModel.updatePortfolioItem` реализуют одну логику (обогащение `PositionStreamItem` → `PortfolioPosition` через `toPortfolioPosition` / `calculateProfitPercent`), но с разной реализацией (в одной — `index == -1` → добавить, в другой — аналогично, но с доп. полями `isin`/`classCode`). Дублирование, рассинхронизация.

**Нарушенное правило:** Не строго Clean — DRY / Single Source of Truth для конверсии.

**Clean-альтернатива:** вынести обогащение в `PositionFlowManager` (domain.api.managers) — эмитит готовые `PortfolioPosition`, VM не парсит `PositionStreamItem`. Плюсы: одна точка правды, VM — тонкие. Минусы: ~2 ч, переписать 2 VM + менеджер.

**Выгоды:** —

**Ссылка на задачу:** — (не заведена). Связано с `### Рефакторинг ViewModel-слоя`.

### Н3. OrdersListViewModel в ui.components

**Статус:** открыто.

**Серьёзность:** низко.

**Описание:** `OrdersListViewModel` живёт в `ui.components` (рядом с `OrdersListDialog.kt`, импортируется оттуда). Остальные ViewModel — в `presentation/screens/*/`. Нарушение конвенции проекта.

**Нарушенное правило:** Не Clean в строгом смысле — архитектурная конвенция пакетов.

**Clean-альтернатива:** `presentation/screens/orders/OrdersListViewModel.kt` (рядом со своим экраном). Плюсы: консистентность. Минусы: `git mv` + правка импорта в 1 файле (~10 мин).

**Выгоды:** —

**Ссылка на задачу:** — (не заведена).

### Исправленные (L1)

[пусто]

---

## L2 — util

**Краткая таблица:**

| ID | Серьёзность | Затрагиваемые уровни | Локализация |
|---|---|---|---|
| Н19. AppLogger — android.util.Log в util | высоко | L2, все | `util/AppLogger.kt` |
| Н20. AppResultExtensions — io.grpc в util | высоко | L2, L6 | `util/AppResultExtensions.kt` |
| Н17. TradePhrases — Compose в util | средне | L2, L1 | `util/TradePhrases.kt` |
| Н18. CurrencyUtils — presentation-формат в util | низко | L2, L1 | `util/CurrencyUtils.kt` |

### Н19. AppLogger — android.util.Log в util

**Статус:** открыто.

**Серьёзность:** высоко.

**Описание:** `util/AppLogger.kt` импортирует `android.util.Log` + `BuildConfig`. Используется всеми слоями (AGENTS: «`AppLogger`, не `android.util.Log`»). Формально domain видит платформу через `util`, даже транзитивно.

**Нарушенное правило:** Dependency rule — domain не должен видеть платформу (даже через shared-слой).

**Clean-альтернатива:** `domain.log.Logger` — интерфейс (`fun d/w/e(tag, msg, throwable?)`); `data.log.AndroidAppLogger` — реализация через `android.util.Log`; Hilt `@Binds`. Domain инжектит `Logger`. Плюсы: KMP-совместимо, тестируемо, чисто. Минусы: инъекция в каждый класс (~30+ callsites), замена статического `AppLogger.d(...)` на `logger.d(...)`.

**Выгоды:** zero-ceremony, глобальный доступ, не требует инжекта. Правило «все слои через `AppLogger`» уже в AGENTS — компромисс, но **не зафиксирован** по 2c (нет правила/альтернативы/выгод в KDoc/ROADMAP).

**Ссылка на задачу:** — (не заведена). Требует либо 2c-фиксации в AGENTS, либо переезда.

### Н20. AppResultExtensions — io.grpc в util

**Статус:** открыто.

**Серьёзность:** высоко.

**Описание:** `util/AppResultExtensions.kt` импортирует `io.grpc.StatusException` / `StatusRuntimeException` / `Status.Code`. `toAppError` (66 строк) — классификатор ошибок с gRPC-спецификой. Если domain импортирует `toAppError`, domain видит `io.grpc` (data-слой).

**Нарушенное правило:** Dependency rule — transport-зависимости в shared-слое.

**Clean-альтернатива:** `data/error/AppErrorMapper.kt` (или per-broker: `data/api/TInvestErrorMapper.kt`, `BcsErrorMapper.kt`, `FinamErrorMapper.kt` — по образцу `### Классификатор ошибок` в ROADMAP). Плюсы: transport-зависимости в data. Минусы: переезд + правка импортов в data + domain (если domain использует `toAppError`).

**Выгоды:** одна функция для всех слоёв, единая точка логирования ошибок. Долг частично зафиксирован в KDoc (`@Suppress("LongMethod", "CyclomaticComplexMethod")` + ссылка на `### Рефакторинг классификатора ошибок`). Но **архитектурное** нарушение (gRPC в `util`) в 2c **не зафиксировано**.

**Ссылка на задачу:** `#### Классификатор ошибок` в `### Рефакторинг архитектурного долга (detekt-группа 3)` (про декомпозицию; про переезд — задача не заведена).

### Н17. TradePhrases — Compose в util

**Статус:** открыто.

**Серьёзность:** средне.

**Описание:** `util/TradePhrases.kt` импортирует `androidx.compose.ui.graphics.Color`, содержит UI-тексты (`directionText`, `orderTypeDescription`, `statusLabel`, `confirmOrderText`), цвета (`orderTypeColor`), маппинг строк в `OrderTypeSelection` (`stringToOrderType`).

**Нарушенное правило:** Dependency rule — presentation-код в shared-слое.

**Clean-альтернатива:** `presentation/util/TradePhrases.kt` (Compose + UI-тексты) + `presentation/theme/TradeColors.kt` (цвета). Маппинг `stringToOrderType` — в data-маппер (см. Н4). Плюсы: `util` становится Compose-free, KMP-совместимым. Минусы: переезд ~7 методов + правка импортов в 4 файлах presentation (~30 мин).

**Выгоды:** SSOT для текстов и цветов в одном файле. Но это presentation-выгода.

**Ссылка на задачу:** — (не заведена).

### Н18. CurrencyUtils — presentation-формат в util

**Статус:** открыто.

**Серьёзность:** низко.

**Описание:** `util/CurrencyUtils.kt` содержит `formatCurrency` (`NumberFormat.getCurrencyInstance(ru-RU)` + `Currency.getInstance("RUB")`) и `formatPrice` («%.2f пт» для futures). Presentation-форматирование (локаль, «₽», «пт»).

**Нарушенное правило:** Presentation-форматирование принадлежит presentation, не shared-слою.

**Clean-альтернатива:** `presentation/util/Format.kt`. Плюсы: presentation-зависимости (Locale, Currency) в presentation. Минусы: переезд + правка 2 импортов.

**Выгоды:** `formatCurrency` — общая утилита, без Compose, без платформы (только `java.text`/`java.util`). Технически допустима в `util`, но presentation-выбор локали и «пт» — presentation-специфика.

**Ссылка на задачу:** — (не заведена).

### Исправленные (L2)

[пусто]

---

## L3 — domain/models

**Краткая таблица:**

| ID | Серьёзность | Затрагиваемые уровни | Локализация |
|---|---|---|---|
| Н5. PortfolioPosition — presentation-поля (пересматривается) | высоко | L3, L1 | `domain/models/Portfolio.kt` |
| Н15. TradingStateSnapshot — presentation в domain | средне | L3, L1 | `domain/models/TradingState.kt` |
| Н6. InstrumentUi — god-класс | средне | L3 | `domain/models/Instruments.kt` |
| Н7. ttech_figi — временное, без задачи | низко | L3, L6 | `domain/models/Instruments.kt` |
| Н8. OrderTypeSelection — sealed class для sum-type | низко | L3 | `domain/models/OrderModels.kt` |
| Н16. TradeCheckResult — sealed class для sum-type | низко | L3 | `domain/models/TradingStatus.kt` |
| Н13. KDoc StreamState — устаревший пакет | низко | L3 | `domain/models/StreamState.kt` |

### Н5. PortfolioPosition — presentation-поля (пересматривается)

**Статус:** принято (по 2c). **Пересматривается** — концептуальное разделение модели.

**Серьёзность:** высоко.

**Описание:** `PortfolioPosition` содержит presentation-поля `totalValue` (= `currentPrice * quantity`), `profitPercent` (вычисляется через `calculateProfitPercent`), `priceChangePercent`. По Clean domain-модель не должна содержать presentation-атрибуты. Отклонение осознанно принято в #9, зафиксировано в KDoc + `### Обогащение name в PortfolioPosition (вариант C)`.

**Нарушенное правило:** Domain не должен содержать presentation-атрибуты (dependency rule в обратную сторону).

**Clean-альтернатива:** разделить на два типа: `Position` (domain: `tscalpInstrumentId`, `brokerName`, `name`, `ticker`, `quantity`, `currentPrice`, `averagePrice`, `profit`, `pointValue`, `instrumentType`, `classCode`, `isin`, `currency`) и `PortfolioPositionUi` (presentation: `totalValue`, `profitPercent`, `priceChangePercent` + domain-часть). Плюсы: полная Clean-дисциплина. Минусы: переписать `AssetPositionCard`, `PortfolioScreen`, `OrdersScreen`, `PortfolioViewModel`, `OrdersViewModel` + маппер `Position → PortfolioPositionUi`.

**Выгоды (по 2c):** `toPortfolioPosition()` — одна точка конвертации в `InvestRepository`; UI работает с одной моделью, без дублирования `profitPercent`/`totalValue` в presentation. Экономия на маппинге ~1 ч.

**Ссылка на задачу:** `### Рефакторинг расчёта P&L для PortfolioPosition` (про формулу profit). Концептуальный пересмотр (разделение на domain + presentation) — **задача не заведена**.

### Н15. TradingStateSnapshot — presentation в domain

**Статус:** открыто.

**Серьёзность:** средне.

**Описание:** `domain/models/TradingState.kt` содержит `TradingStateSnapshot` с полями `quantity: String`, `pairedMultiplier: String`, `orderType: OrderTypeSelection`, `pairTradingEnabled: Boolean`. `quantity`/`pairedMultiplier` — сырые значения TextField, невалидированные. `orderType` — presentation-выбор. `pairTradingEnabled` — UI-тумблер. Это снимок UI-состояния.

**Нарушенное правило:** Domain не знает про presentation-выбор пользователя и сырые строки TextField.

**Clean-альтернатива:** `presentation/screens/orders/OrdersUiStateSnapshot` для UI + нейтральный domain-снимок с валидированными типами (`quantity: Long?`, `orderType: OrderTypeSelection`). Плюсы: domain чист. Минусы: переезд + правка `TradingStateRepository` (data) + `OrdersViewModel`.

**Выгоды:** простая сериализация в DataStore без маппинга, экономит ~1 ч. Но `quantity: String` — технический долг (можно сохранить «1O» вместо «10»).

**Ссылка на задачу:** — (не заведена).

### Н6. InstrumentUi — god-класс

**Статус:** открыто.

**Серьёзность:** средне.

**Описание:** `domain/models/Instruments.kt` — `InstrumentUi` открытый класс с ~50 полями; `FutureUi` и `ShareUi` дублируют **все** общие поля через `override` + добавляют свои. Неприменимые поля явно `null` (например `ShareUi.initialMarginOnBuy = null`).

**Нарушенное правило:** Формально dependency rule не нарушен (всё в domain). Нарушает Interface Segregation на уровне моделей: `ShareUi` «знает» про `initialMarginOnBuy`.

**Clean-альтернатива:** `sealed interface Instrument` (минимальный контракт: `tscalpInstrumentId`, `ticker`, `name`, `currency`, `lot`) + `data class Share(...)` + `data class Future(...)` со своими полями. `when (instrument) { is Share -> ..., is Future -> ... }` — компилятор exhaustive. Плюсы: ISP, компилятор-проверка. Минусы: переписать `TInvestInstrumentMapper.mapProtoToDomain` (118 строк, `@Suppress` в detekt-baseline), `BcsBrokerApi` маппинг, все места `InstrumentUi` (карточки, поиск, окна, `InstrumentRepository`).

**Выгоды:** один тип — простая передача через UI, `instrumentType: String` уже есть. Работает.

**Ссылка на задачу:** — (не заведена). Концептуально связано с `### Рефакторинг архитектурного долга (detekt-группа 3)` → `#### Data-слой T-Invest` (`mapProtoToDomain` декомпозиция).

### Н7. ttech_figi — временное, без задачи

**Статус:** открыто.

**Серьёзность:** низко.

**Описание:** `InstrumentUi.ttech_figi` — KDoc говорит «**Временно** — нужен для подписки на `LastPrice` через gRPC-стрим (там figi, не uid). Уйдёт, когда стрим перейдёт на `instrumentUid`». Задачи в `docs/ROADMAP.md` / `docs/ISSUES.md` на удаление `ttech_figi` нет.

**Нарушенное правило:** 2d — временное решение без задачи на полное.

**Clean-альтернатива:** задача в ROADMAP «Перевести gRPC `LastPrice` на `instrumentUid`; удалить `ttech_figi`». Плюсы: 2d соблюдён. Минусы: — (это не альтернатива, а фиксация).

**Выгоды:** работает сейчас; поле нужно для стрима.

**Ссылка на задачу:** — (не заведена). Требует 2d-фиксации.

### Н8. OrderTypeSelection — sealed class для sum-type

**Статус:** открыто.

**Серьёзность:** низко.

**Описание:** `domain/models/OrderModels.kt` — `OrderTypeSelection` объявлен как `sealed class` для чистого sum-type (5 `object`-веток без shared state). По 2a (актуальность): Kotlin 1.5+ рекомендует `sealed interface` для sum-types без shared state. В том же файле для `Order` уже выбрано `sealed interface` (`### Split order domain into sealed Regular/Stop`).

**Нарушенное правило:** Не Clean — стилистическая последовательность (2a).

**Clean-альтернатива:** `sealed interface OrderTypeSelection`. Плюсы: консистентность с `Order`, рекомендация Kotlin. Минусы: правка `when`-конструкций (механически совместимо), ~10 мин.

**Выгоды:** — (нет существенных).

**Ссылка на задачу:** — (не заведена). Можно включить в `### Split order domain into sealed Regular/Stop`.

### Н16. TradeCheckResult — sealed class для sum-type

**Статус:** открыто.

**Серьёзность:** низко.

**Описание:** `domain/models/TradingStatus.kt` — `TradeCheckResult` объявлен как `sealed class` (`data object Success` + `data class Error(message: String)`). Тот же класс проблемы, что Н8.

**Нарушенное правило:** Не Clean — стилистическая последовательность (2a).

**Clean-альтернатива:** `sealed interface TradeCheckResult`. Плюсы: консистентность. Минусы: механическая правка `when`-конструкций (~5 мин).

**Выгоды:** — (нет существенных).

**Ссылка на задачу:** — (не заведена).

### Н13. KDoc StreamState — устаревший пакет

**Статус:** открыто.

**Серьёзность:** низко.

**Описание:** `domain/models/StreamState.kt` — KDoc ссылается на `[com.gitlab.biomorf.tscalp.domain.api.StreamStateTracker]`. После Move `StreamStateTracker` живёт в `com.gitlab.biomorf.tscalp.domain.api.managers.StreamStateTracker` (package `domain.api.managers`). Ссылка битая (в KDoc, не в коде).

**Нарушенное правило:** Не Clean — актуальность документации.

**Clean-альтернатива:** поправить ссылку в KDoc. Плюсы: актуально. Минусы: — (~2 мин).

**Выгоды:** —

**Ссылка на задачу:** — (не заведена). Мелочь, правится при следующем касании `StreamState.kt`.

### Исправленные (L3)

[пусто]

---

## L4 — domain/api

**Краткая таблица:**

| ID | Серьёзность | Затрагиваемые уровни | Локализация |
|---|---|---|---|
| Н25. BrokerApi fat дублирует nullable-val роли | критично | L4, L6 | `domain/api/BrokerApi.kt` (19 fat-методов) |
| Н26. BrokerLifecycleApi — не 2c-фиксация | средне | L4 | `roles/BrokerLifecycleApi.kt` |
| Н27. StreamsApi.subscribeOrderState — suspend | средне | L4 | `roles/StreamsApi.kt` |
| Н28. AccountsApi.getAccounts(sandboxMode) | средне | L4 | `roles/AccountsApi.kt` |

### Н25. BrokerApi fat дублирует nullable-val роли

**Статус:** принято (по 2c). **Пересматривается:** фактически технический долг переходного состояния, а не осознанный выбор.

**Серьёзность:** критично.

**Описание:** 19 методов fat-части `BrokerApi` почти все дублируют nullable-val роли: `getAccounts` -> `accounts?.getAccounts`, `openSandboxAccount`/`closeSandboxAccount`/`sandboxPayIn` -> `sandbox?.<метод>`, `getBalance` -> `balance?.getBalance`, `subscribePositions`/`subscribeLastPrices`/`subscribeOrderState` -> `streams?.<метод>`, `postOrder`/`postStopOrder`/`getStopOrders`/`cancelStopOrder`/`getOrders`/`cancelOrder` -> `orders?.<метод>`, `findInstruments`/`getTradingStatuses` -> `userInstruments?.<метод>`. Не дублируют 3 метода: `fetchPortfolioPositions`, `getLastPricesByTscalpInstrumentId`, `checkTradeAvailability`. Два пути к одному контракту (`broker.postOrder` и `broker.orders?.postOrder`) — разное поведение при `null`.

**Нарушенное правило Clean:** Interface Segregation Principle — `BrokerApi` остаётся fat с дублированием ролей; nullable-val решение не доведено до конца.

**Clean-альтернатива:** выделить оставшиеся 3 метода в роль (`PortfolioApi` + `TradingStatusApi`), мигрировать `InvestRepository` на `broker.<role>?`, удалить fat-часть. `BrokerApi` — тонкий фасад с 9 nullable-val ролями и `name`/`isInitialized`. Плюсы: полный ISP, `@Suppress("TooManyFunctions")` снимается, один путь к каждому контракту. Минусы: `InvestRepository` — 29 callsites, ~2–3 ч.

**Выгоды (для «отклонение по 2c»):** KDoc в `BrokerApi` фиксирует fat как осознанно (мультиброкерный фасад, `InvestRepository` — 29 callsites). По факту fat-часть — legacy переходного состояния. Пересмотр — после стабилизации остальных задач clean-audit, новой задачи пока нет.

**Ссылка на задачу:** — (не заведена). Пересмотр — в рамках `### Рефакторинг ролей BrokerApi` (закрыта), новая задача не заведена.

### Н26. BrokerLifecycleApi — не 2c-фиксация

**Статус:** открыто.

**Серьёзность:** средне.

**Описание:** `BrokerLifecycleApi.initialize(credentials: BrokerCredentials): Boolean` — runtime `IllegalArgumentException` при неверном типе credentials. KDoc: «Сообщение об ошибке — через `AppLogger` и `AppResult.Failure`» — domain-интерфейс ссылается на `util.AppLogger` (domain -> util).

**Нарушенное правило Clean:** runtime-проверка типов вместо compile-time; KDoc связывает domain с `util`.

**Clean-альтернатива:** `BrokerLifecycleApi<C : BrokerCredentials>` (generic по типу credentials). Плюсы: compile-time гарантия. Минусы: параметризация роли, `BrokerApi.lifecycle: BrokerLifecycleApi<*>?` теряет типизацию.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена).

### Н27. StreamsApi.subscribeOrderState — suspend

**Статус:** открыто.

**Серьёзность:** средне.

**Описание:** `StreamsApi.subscribePositions` / `subscribeLastPrices` — non-suspend; `subscribeOrderState` — suspend. KDoc: «как в `BrokerApi.subscribeOrderState` (контракт совпадает с fat-интерфейсом)».

**Нарушенное правило Clean:** не Clean-инвариант, а консистентность роли — 2 метода non-suspend, 1 suspend, без технической необходимости.

**Clean-альтернатива:** убрать `suspend` у `subscribeOrderState` (обернуть в `flow { }` внутри реализации). Плюсы: консистентность. Минусы: мелочь.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Может быть частью Н25.

### Н28. AccountsApi.getAccounts(sandboxMode)

**Статус:** открыто.

**Серьёзность:** средне.

**Описание:** общий `AccountsApi` принимает `sandboxMode: Boolean`. KDoc: «У брокеров без sandbox (BCS/Finam) параметр игнорируется». Domain-контракт роли несёт специфику T-Invest.

**Нарушенное правило Clean:** domain-контракт роли несёт специфику T-Invest (sandbox); BCS/Finam игнорируют — параметр маскирует «не применимо» под «применимо, но неважно».

**Clean-альтернатива:** вынести `sandboxMode` в `TInvestAccountsApi : AccountsApi` (T-Invest-специфика). Или `AccountsApi` без параметра, sandbox-специфика — в реализации. Плюсы: чистый общий контракт. Минусы: переделка TInvest + `InvestRepository`.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена).

### Исправленные (L4)

[пусто]

## L5 — domain/managers

**Статус:** не проверено.

Заполняется по мере инспекции `domain/managers/*.kt`.

## L6 — data

**Статус:** не проверено.

Заполняется по мере инспекции `data/repository/`, `data/api/`, `data/orchestrator/`.

## L7 — di

**Краткая таблица:**

| ID | Серьёзность | Затрагиваемые уровни | Локализация |
|---|---|---|---|
| Н29. BrokerManager — domain-сервис в di/ | критично | L7, L1, L5, L6 | `di/BrokerManager.kt` |

### Н29. BrokerManager — domain-сервис в di/

**Статус:** открыто.

**Серьёзность:** критично.

**Описание:** `di/BrokerManager.kt` содержит domain-логику: `computeAnyInitialized()` (вычисление), `refreshInitializationState()` (публичный side-effect), `getDefaultBroker(): BrokerApi` throws `IllegalStateException("Брокер TInvest не зарегистрирован")` (знает «TInvest = default»), `anyInitialized: StateFlow<Boolean>` (реактивный контракт). Все потребители: `data/repository/*` (3), `domain/api/managers/*` (3), `TScalpApplication` (1), `SettingsViewModel` (4 callsites) инжектят `di.BrokerManager`.

**Нарушенное правило Clean:** dependency rule — domain (managers), presentation, data зависят от `di` (сборочный слой). `di/` не должен содержать доменную логику.

**Clean-альтернатива:** `domain.registry.BrokerRegistry` — domain-сервис; implementation в `data/registry/`; `@Binds` в `di/`. Плюсы: domain-сервис в domain, `di/` содержит только DI. Минусы: переезд ~10 файлов-потребителей. `refreshInitializationState` — внутренний, не в интерфейсе (см. `**Анализ L1-Н22:**`).

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Связано с L1-Н22 (`BrokerRegistry`), частично уточняет.

### Исправленные (L7)

[пусто]

## L8 — app

**Статус:** не проверено.

Заполняется по мере инспекции `TScalpApplication.kt`, `MainActivity.kt`.

---

## Связанные документы

- `AGENTS.md` → 2c, 2d
- `docs/ROADMAP.md` — задачи реализации
- `docs/architecture.md` — когда появится (раздел «Известные долги»)

## История

- 2026-10-26 — создан. Заполнены L1 (7), L2 (4), L3 (7). L4–L8 — заглушки.
