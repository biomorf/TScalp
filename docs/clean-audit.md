# Clean-audit

Справочник отклонений от Clean-архитектуры в проекте TScalp.
Фиксирует: что соответствует Clean, что отклоняется, почему.
Документ — справочник. Задачи реализации — в `docs/ROADMAP.md`.

## Шаблон записи

~~~markdown
### <ID>. <Краткое название>

**Статус:** открыто / в работе / принято (по 2c) / исправлено.

**Серьёзность:** критично / высоко / средне / низко.

**Live тест:** ✓ (требуется) / - (не требуется).

**Описание:** 1–3 предложения — что именно нарушено.

**Анализ L<N>-<ID>:** подтверждается / опровергается — <что именно>. (опциональное поле, только если запись уточняется анализом другого уровня)

**Нарушенное правило:** одна строка — какое правило Clean.

**Clean-альтернатива:** как правильно — с плюсами и минусами.

**Выгоды (обязательно для «принято»):** что даёт текущий вариант.

**Ссылка на задачу:** название задачи в `docs/ROADMAP.md` / `docs/ci-roadmap.md` или `—`.

**Сводный отчёт, <дата>:** — (не включена) / да (включена в отчёт от <дата>)
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
| L9 | transport-ktor (отдельный модуль) | 8 |

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

| ID | Серьёзность | Затрагиваемые уровни | Локализация | live тест |
|---|---|---|---|---|
| Н22. ViewModel → di.BrokerManager | критично | L1, L7 | те же 3 VM | - |
| Н1. Presentation конструирует PortfolioPosition | высоко | L1, L3 | `OrdersScreen.kt` (~353, ~580) | - |
| Н23. ViewModel → data.orchestrator.DebugConfig | средне | L1, L6 | `SettingsViewModel` | ✓ |
| Н4. UI конвертирует String → domain-модель | средне | L1, L3 | `OrdersListDialog.kt` | - |
| Н24. Дублирование обогащения в двух VM | средне | L1, L3 | `OrdersViewModel.updatePositionPnl`, `PortfolioViewModel.updatePortfolioItem` | - |
| Н3. OrdersListViewModel в `ui.components` | низко | L1 | `ui/components/OrdersListViewModel.kt` | - |

### Исправленные (L1)

### ✅ Н24. Дублирование обогащения в двух VM

**Статус:** исправлено (2026-10-29, PR #69).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `OrdersViewModel.updatePositionPnl` и `PortfolioViewModel.updatePortfolioItem` реализуют одну логику (обогащение `PositionStreamItem` → `PortfolioPosition` через `toPortfolioPosition` / `calculateProfitPercent`), но с разной реализацией (в одной — `index == -1` → добавить, в другой — аналогично, но с доп. полями `isin`/`classCode`). Дублирование, рассинхронизация.

**Нарушенное правило:** Не строго Clean — DRY / Single Source of Truth для конверсии.

**Clean-альтернатива:** вынести обогащение в `PositionFlowManager` (domain.api.managers) — эмитит готовые `PortfolioPosition`, VM не парсит `PositionStreamItem`. Плюсы: одна точка правды, VM — тонкие. Минусы: ~2 ч, переписать 2 VM + менеджер.

**Выгоды:** —

**Что сделано:** SSoT-helper `PortfolioPosition.mergeWith(item: PositionStreamItem)` в `domain/models/Portfolio.kt` (канон по `PortfolioViewModel`): `quantity` из события, `currentPrice`/`averagePrice`/`pointValue` non-null fallback, `totalValue = currentPrice * quantity`, `profit` = `expectedYield`, `profitPercent` через `calculateProfitPercent`, `isin`/`classCode` — `ifBlank { old }`. Оба VM используют `old.mergeWith(item)`. Политики (фильтр partial в Orders, без фильтра в Portfolio) — presentation.

**Отклонение от плана:** вместо `portfolioFlow` в `PositionFlowManager` (в плане) — SSoT-helper в domain-модели. Причина: `PortfolioPosition` содержит presentation-поля (`totalValue`, `profitPercent`), эмитить их из domain-менеджера — углубление clean-audit Н5, не решение Н24. `mergeWith` в domain-модели сохраняет SSoT без нарушения dependency rule.

**Ссылка на задачу:** — (не заведена). Связано с `### Рефакторинг ViewModel-слоя`.

### ✅ Н1. Presentation конструирует PortfolioPosition

**Статус:** исправлено (2026-10-29, PR #68).

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `OrdersScreen.kt` (два места: основной инструмент ~353, парный ~580) вручную вызывает `PortfolioPosition(...)` с полями `profit = null`, `profitPercent = null` для инструмента, которого нет в портфеле. По Clean presentation имеет право читать domain-модели, но не создавать их — правила конструирования принадлежат domain. После #9 `PortfolioPosition` — именно domain-модель.

**Нарушенное правило:** Dependency rule — presentation не конструирует domain-модели в обход domain-логики.

**Clean-альтернатива:** фабрика во ViewModel (или domain-функция `emptyPortfolioPosition(instrument, currentPrice)`) — UI получает готовую модель. Плюсы: правила конструирования в domain/presentation-фабрике. Минусы: ~30 мин правок + правка `OrdersUiState`.

**Выгоды:** —

**Что сделано:** фабрика `emptyPortfolioPosition(instrument, currentPrice, pointValue, instrumentType)` в `domain/models/Portfolio.kt`; `OrdersScreen` — 2 raw `PortfolioPosition(...)` → вызов фабрики. `toPortfolioPosition` (data-конвертер) не тронут.

**Ссылка на задачу:** — (не заведена).

### ✅ Н4. UI конвертирует String → domain-модель

**Статус:** исправлено (2026-10-28, растворён в Split order, коммит `ec9c7d5`).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `OrdersListDialog.kt` в `OrderCard` передаёт `orderType = TradePhrases.stringToOrderType(order.type)`. Конверсия «сырое значение из data → domain-модель» выполняется в presentation.

**Нарушенное правило:** Presentation не должна маппить сырые данные в domain-модели — это забота data-слоя (маппер).

**Clean-альтернатива:** `OrderListItem.type` → `OrderTypeSelection` в маппере `TInvestOrdersMapper` (data). UI получает готовый `OrderTypeSelection`. Плюсы: presentation без маппинга. Минусы: зависит от `### Split order domain into sealed Regular/Stop` (там переделка модели).

**Выгоды:** —

**Ссылка на задачу:** `### Split order domain into sealed Regular/Stop` (в его рамках решается).


### ✅ Н21. ViewModel → data.repository.*

**Статус:** исправлено (PR #57, `dev`, 2026-10-27).

**Серьёзность:** критично.

**Live тест:** -

**Описание:** три ViewModel (`OrdersViewModel`, `PortfolioViewModel`, `SettingsViewModel`) инжектят `InvestRepository`, `InstrumentRepository`, `SearchCache`, `SettingsRepository`, `TradingStateRepository` из `data.repository.*` напрямую. По Clean presentation должна зависеть от domain-интерфейса, а data — реализовывать его.

**Уточнение (2026-10-27):** по факту потребителей 5, не 3 —
добавились `OrdersListViewModel` (`SettingsRepository`) и
`TScalpApplication` (`SettingsRepository` через `@Inject lateinit var`).
Также удалён мёртвый `InstrumentRepository.refreshInstrument` (0 callsites).

**Нарушенное правило:** Dependency rule — presentation не должна знать про data (зависимость наружу).

**Clean-альтернатива:** `domain.api.repository.*` — по одному интерфейсу на репозиторий (для `InvestRepository` частично сделано в `### Разбивка InvestRepository по доменным областям`, но не для остальных 4). VM инжектит интерфейс, data реализует через `@Binds`. Плюсы: Clean-дисциплина, KMP-совместимость, фейки в тестах. Минусы: переезд 4 репозиториев (~2–3 ч) + правка DI.

**Выгоды:** —

**Ссылка на задачу:** `### Разбивка InvestRepository по доменным
областям` (PR #56, `dev`) + `### H21b. Domain-интерфейсы для 4
репозиториев` (PR #57, `dev`).


---

---

### ✅ Н23. ViewModel → data.orchestrator.DebugConfig

**Статус:** исправлено (PR #59, `dev`, 2026-10-28).
**Серьёзность:** средне.

**Live тест:** ✓

**Описание:** `SettingsViewModel` импортирует `data.orchestrator.DebugConfig`, читает/пишет `disableGrpc`, `disableWs`, `forceRest` через `toggleGrpc/toggleWs/toggleRest`. Debug-флаги транспорта в presentation.

**Нарушенное правило:** Dependency rule — presentation не должна знать про `data.orchestrator`.

**Clean-альтернатива:** `domain.api.DebugFlagsRepository` с геттерами/сеттерами; реализация — `data.orchestrator.DebugConfig` за интерфейсом. Плюсы: presentation → domain.api. Минусы: ~30 мин правок.

**Выгоды:** —

**Анализ L5-Н38:** подтверждается — `DebugConfig` протекает в `PriceFlowManager` (L5), не только в `SettingsViewModel` (L1). Нарушение шире, чем presentation.

**Ссылка на задачу:** — (не заведена). Связано с закрытыми `### Debug-тумблеры` и `### DebugConfig: только для debug-сборок` (функционал работает; переезд API — отдельная задача).

---

### ✅ Н22. ViewModel → di.BrokerManager

**Статус:** исправлено (PR #60, `dev`, 2026-10-28).

**Серьёзность:** критично.

**Live тест:** -

**Описание:** все потребители (`OrdersViewModel`, `PortfolioViewModel`, `SettingsViewModel`, `TScalpApplication`, `domain/api/managers/*`, `data/repository/*`) инжектят `com.gitlab.biomorf.tscalp.di.BrokerManager` напрямую. Вызывают `getAllBrokers()`, `getBroker(name)?.lifecycle?.initialize(...)`, `getAvailableBrokerNames()`, `refreshInitializationState()`, `getDefaultBroker()`. `BrokerManager` — точка сборки брокеров, живёт в `di/`.

**Нарушенное правило:** Dependency rule — presentation зависит от `di` (сборочный слой).

**Clean-альтернатива:** `domain.api.BrokerRegistry` — интерфейс с теми же методами (`getAllBrokers`, `getBroker`, `getAvailableBrokerNames`, `getDefaultBroker`, `refreshInitializationState`). Реализация — `data/registry/BrokerRegistryImpl` или `di/BrokerRegistryImpl`, `@Binds` в Hilt. VM инжектит `BrokerRegistry`. Плюсы: presentation → domain.api; `BrokerRegistry` в KMP-модуле. Минусы: переезд + переименование `BrokerManager` (~1–2 ч) + правка 3 VM + тестов.

**Выгоды:** —


**Анализ L7-Н29:** подтверждается — анализ L7 показал, что `BrokerManager` — domain-сервис в `di/`, нарушение шире, чем только VM: потребители включают `domain/api/managers/*` и `data/repository/*`. См. L7-Н29.

**Анализ L7-part1:** подтверждается — полный L7 показал 3 находки (Н35–Н37); `BrokerManager` — единственная утечка `di/` наружу (10 файлов). Дизайн `BrokerRegistry` уточняется: `BrokerRegistryImpl(brokers: Set<BrokerApi>)`, порядок из `BrokerName.entries`, `refreshInitializationState` — открытый вопрос (см. L7-Н29).

**Анализ L5-Н40:** подтверждается — L5 (3 менеджера) показал, что `di.BrokerManager` протекает шире: 3 VM (L1) + 3 менеджера (L5) + 3 репозитория (L6) + 1 app (L8) = 10 потребителей.

**Анализ L8-Н51:** подтверждается — `refreshInitializationState` вызывается из `TScalpApplication` 2 раза (`restoreTInvest`, `restoreBcs`), всего 6 callsites (2 app + 4 SettingsViewModel). Усиливает открытый вопрос H22.

**Ссылка на задачу:** `### H22. BrokerRegistry` (PR #60, `dev`).

### ✅ Н3. OrdersListViewModel в ui.components

**Статус:** исправлено (PR #58, `dev`, 2026-10-27). `OrdersListViewModel`
в `presentation/screens/orders/`, package обновлён.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `OrdersListViewModel` живёт в `ui.components` (рядом с `OrdersListDialog.kt`, импортируется оттуда). Остальные ViewModel — в `presentation/screens/*/`. Нарушение конвенции проекта.

**Нарушенное правило:** Не Clean в строгом смысле — архитектурная конвенция пакетов.

**Clean-альтернатива:** `presentation/screens/orders/OrdersListViewModel.kt` (рядом со своим экраном). Плюсы: консистентность. Минусы: `git mv` + правка импорта в 1 файле (~10 мин).

**Выгоды:** —

**Ссылка на задачу:** `### H3. Move OrdersListViewModel to presentation/screens/orders/` (PR #58, `dev`).
---

## L2 — util

**Краткая таблица:**

| ID | Серьёзность | Затрагиваемые уровни | Локализация | live тест |
|---|---|---|---|---|
| Н19. AppLogger — android.util.Log в util | высоко | L2, все | `util/AppLogger.kt` | - |
| Н20. AppResultExtensions — io.grpc в util | высоко | L2, L6 | `util/AppResultExtensions.kt` | - |
| Н17. TradePhrases — Compose в util | средне | L2, L1 | `util/TradePhrases.kt` | - |
| Н18. CurrencyUtils — presentation-формат в util | низко | L2, L1 | `util/CurrencyUtils.kt` | - |

### Исправленные (L2)

### ✅ Н20. AppResultExtensions — io.grpc в util

**Статус:** исправлено (2026-10-29, PR #70).

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `util/AppResultExtensions.kt` импортирует `io.grpc.StatusException` / `StatusRuntimeException` / `Status.Code`. `toAppError` (66 строк) — классификатор ошибок с gRPC-спецификой. Если domain импортирует `toAppError`, domain видит `io.grpc` (data-слой).

**Нарушенное правило:** Dependency rule — transport-зависимости в shared-слое.

**Clean-альтернатива:** `data/error/AppErrorMapper.kt` (или per-broker: `data/api/TInvestErrorMapper.kt`, `BcsErrorMapper.kt`, `FinamErrorMapper.kt` — по образцу `### Классификатор ошибок` в ROADMAP). Плюсы: transport-зависимости в data. Минусы: переезд + правка импортов в data + domain (если domain использует `toAppError`).

**Выгоды:** одна функция для всех слоёв, единая точка логирования ошибок. Долг частично зафиксирован в KDoc (`@Suppress("LongMethod", "CyclomaticComplexMethod")` + ссылка на `### Рефакторинг классификатора ошибок`). Но **архитектурное** нарушение (gRPC в `util`) в 2c **не зафиксировано**.

**Анализ L5-Н39:** подтверждается — `toAppError` используется в `PriceFlowManager` (L5), не только в data. Domain видит io.grpc через `util` транзитивно.

**Анализ L6-Н20:** подтверждается — `runCatchingAppResult` используется в data (`InvestRepository`, 14 callsites). Переезд `AppResultExtensions` в `data/error/` обоснован: 14 из ~20 callsites уже в data.

**Анализ L8-Н49:** подтверждается — `util.TInvestErrorMessages` (родственен `AppResultExtensions`, оба в util) используется в `TScalpApplication.installGrpcExceptionGuard` для расшифровки gRPC-кодов. App видит util.

**Что сделано:** `util/AppResultExtensions.kt` → `data/error/AppErrorMapper.kt` (package `data.error`); `runCatchingAppResult`, `toAppError`, `extractHttpCode`. Broker-таблицы `BrokerErrorMessages.kt`, `TInvestErrorMessages.kt` → `data/api/error/`. Domain (`PriceFlowManager`) больше не вызывает `toAppError` — логирует `e.message`. Presentation (`OrdersViewModel`) — `AppError.Unknown(e.message)` вместо `toAppError`. `util/` пуст (AppLogger удалён в H19).

**Отклонение от плана:** два пакета вместо одного (`data/error/` — общий ACL, `data/api/error/` — broker-adapter). Причина: семантическое разделение — общий mapper для data vs broker-специфичные таблицы кодов.

**Ссылка на задачу:** `#### Классификатор ошибок` в `### Рефакторинг архитектурного долга (detekt-группа 3)` (про декомпозицию; про переезд — задача не заведена).

### ✅ Н17. TradePhrases — Compose в util

**Статус:** исправлено (2026-10-29, PR #64 — unify presentation).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `util/TradePhrases.kt` импортирует `androidx.compose.ui.graphics.Color`, содержит UI-тексты (`directionText`, `orderTypeDescription`, `statusLabel`, `confirmOrderText`), цвета (`orderTypeColor`), маппинг строк в `OrderTypeSelection` (`stringToOrderType`).

**Нарушенное правило:** Dependency rule — presentation-код в shared-слое.

**Clean-альтернатива:** `presentation/util/TradePhrases.kt` (Compose + UI-тексты) + `presentation/theme/TradeColors.kt` (цвета). Маппинг `stringToOrderType` — в data-маппер (см. Н4). Плюсы: `util` становится Compose-free, KMP-совместимым. Минусы: переезд ~7 методов + правка импортов в 4 файлах presentation (~30 мин).

**Выгоды:** SSOT для текстов и цветов в одном файле. Но это presentation-выгода.

**Что сделано:** `util/TradePhrases.kt` → `presentation/components/TradePhrases.kt` (решение пользователя: только потребитель — `OrderCard`, лежит рядом). `stringToOrderType` удалён в Split order (`ec9c7d5`).

**Ссылка на задачу:** — (не заведена).

### ✅ Н18. CurrencyUtils — presentation-формат в util

**Статус:** исправлено (2026-10-29, PR #64 — unify presentation).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `util/CurrencyUtils.kt` содержит `formatCurrency` (`NumberFormat.getCurrencyInstance(ru-RU)` + `Currency.getInstance("RUB")`) и `formatPrice` («%.2f пт» для futures). Presentation-форматирование (локаль, «₽», «пт»).

**Нарушенное правило:** Presentation-форматирование принадлежит presentation, не shared-слою.

**Clean-альтернатива:** `presentation/util/Format.kt`. Плюсы: presentation-зависимости (Locale, Currency) в presentation. Минусы: переезд + правка 2 импортов.

**Выгоды:** `formatCurrency` — общая утилита, без Compose, без платформы (только `java.text`/`java.util`). Технически допустима в `util`, но presentation-выбор локали и «пт» — presentation-специфика.

**Что сделано:** `util/CurrencyUtils.kt` → `presentation/util/CurrencyFormat.kt` с параметром `currency` (без привязки к RUB). Domain/data получают числа через `TradeDetails`/`TradeCheckResult`, форматирование — в presentation.

**Ссылка на задачу:** — (не заведена).

### ✅ Н19. AppLogger — android.util.Log в util

**Статус:** исправлено (2026-10-29, H19 part 1–3, PR #65–#67).

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `util/AppLogger.kt` импортирует `android.util.Log` + `BuildConfig`. Используется всеми слоями (AGENTS: «`AppLogger`, не `android.util.Log`»). Формально domain видит платформу через `util`, даже транзитивно.

**Нарушенное правило:** Dependency rule — domain не должен видеть платформу (даже через shared-слой).

**Clean-альтернатива:** `domain.log.Logger` — интерфейс (`fun d/w/e(tag, msg, throwable?)`); `data.log.AndroidAppLogger` — реализация через `android.util.Log`; Hilt `@Binds`. Domain инжектит `Logger`. Плюсы: KMP-совместимо, тестируемо, чисто. Минусы: инъекция в каждый класс (~30+ callsites), замена статического `AppLogger.d(...)` на `logger.d(...)`.

**Выгоды:** zero-ceremony, глобальный доступ, не требует инжекта. Правило «все слои через `AppLogger`» уже в AGENTS — компромисс, но **не зафиксирован** по 2c (нет правила/альтернативы/выгод в KDoc/ROADMAP).

**Анализ L8-Н49:** подтверждается — `AppLogger` используется в `TScalpApplication` (`installGrpcExceptionGuard`). App видит util.

**Что сделано:** `domain.log.Logger` (интерфейс) + `data.log.AndroidAppLogger` (реализация) + `di.LogModule` (`@Binds`). 148 callsites мигрированы (domain 36, data 37, presentation 63, app 12). `util/AppLogger.kt` удалён.

**Ссылка на задачу:** — (не заведена). Требует либо 2c-фиксации в AGENTS, либо переезда.


---

## L3 — domain/models

**Краткая таблица:**

| ID | Серьёзность | Затрагиваемые уровни | Локализация | live тест |
|---|---|---|---|---|
| Н5. PortfolioPosition — presentation-поля (пересматривается) | высоко | L3, L1 | `domain/models/Portfolio.kt` | - |
| Н15. TradingStateSnapshot — presentation в domain | средне | L3, L1 | `domain/models/TradingState.kt` | - |
| Н6. InstrumentUi — god-класс | средне | L3 | `domain/models/Instruments.kt` | - |
| Н7. ttech_figi — временное, без задачи | низко | L3, L6 | `domain/models/Instruments.kt` | - |
| Н8. OrderTypeSelection — sealed class для sum-type | низко | L3 | `domain/models/OrderModels.kt` | - |
| Н16. TradeCheckResult — sealed class для sum-type | низко | L3 | `domain/models/TradingStatus.kt` | - |
| Н13. KDoc StreamState — устаревший пакет | низко | L3 | `domain/models/StreamState.kt` | - |

### Н5. PortfolioPosition — presentation-поля (пересматривается)

**Статус:** принято (по 2c). **Пересматривается** — концептуальное разделение модели.

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `PortfolioPosition` содержит presentation-поля `totalValue` (= `currentPrice * quantity`), `profitPercent` (вычисляется через `calculateProfitPercent`), `priceChangePercent`. По Clean domain-модель не должна содержать presentation-атрибуты. Отклонение осознанно принято в #9, зафиксировано в KDoc + `### Обогащение name в PortfolioPosition (вариант C)`.

**Нарушенное правило:** Domain не должен содержать presentation-атрибуты (dependency rule в обратную сторону).

**Clean-альтернатива:** разделить на два типа: `Position` (domain: `tscalpInstrumentId`, `brokerName`, `name`, `ticker`, `quantity`, `currentPrice`, `averagePrice`, `profit`, `pointValue`, `instrumentType`, `classCode`, `isin`, `currency`) и `PortfolioPositionUi` (presentation: `totalValue`, `profitPercent`, `priceChangePercent` + domain-часть). Плюсы: полная Clean-дисциплина. Минусы: переписать `AssetPositionCard`, `PortfolioScreen`, `OrdersScreen`, `PortfolioViewModel`, `OrdersViewModel` + маппер `Position → PortfolioPositionUi`.

**Выгоды (по 2c):** `toPortfolioPosition()` — одна точка конвертации в `InvestRepository`; UI работает с одной моделью, без дублирования `profitPercent`/`totalValue` в presentation. Экономия на маппинге ~1 ч.

**Ссылка на задачу:** `### Рефакторинг расчёта P&L для PortfolioPosition` (про формулу profit). Концептуальный пересмотр (разделение на domain + presentation) — **задача не заведена**.

### Н15. TradingStateSnapshot — presentation в domain

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `domain/models/TradingState.kt` содержит `TradingStateSnapshot` с полями `quantity: String`, `pairedMultiplier: String`, `orderType: OrderTypeSelection`, `pairTradingEnabled: Boolean`. `quantity`/`pairedMultiplier` — сырые значения TextField, невалидированные. `orderType` — presentation-выбор. `pairTradingEnabled` — UI-тумблер. Это снимок UI-состояния.

**Нарушенное правило:** Domain не знает про presentation-выбор пользователя и сырые строки TextField.

**Clean-альтернатива:** `presentation/screens/orders/OrdersUiStateSnapshot` для UI + нейтральный domain-снимок с валидированными типами (`quantity: Long?`, `orderType: OrderTypeSelection`). Плюсы: domain чист. Минусы: переезд + правка `TradingStateRepository` (data) + `OrdersViewModel`.

**Выгоды:** простая сериализация в DataStore без маппинга, экономит ~1 ч. Но `quantity: String` — технический долг (можно сохранить «1O» вместо «10»).

**Ссылка на задачу:** — (не заведена).

### Н6. InstrumentUi — god-класс

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `domain/models/Instruments.kt` — `InstrumentUi` открытый класс с ~50 полями; `FutureUi` и `ShareUi` дублируют **все** общие поля через `override` + добавляют свои. Неприменимые поля явно `null` (например `ShareUi.initialMarginOnBuy = null`).

**Нарушенное правило:** Формально dependency rule не нарушен (всё в domain). Нарушает Interface Segregation на уровне моделей: `ShareUi` «знает» про `initialMarginOnBuy`.

**Clean-альтернатива:** `sealed interface Instrument` (минимальный контракт: `tscalpInstrumentId`, `ticker`, `name`, `currency`, `lot`) + `data class Share(...)` + `data class Future(...)` со своими полями. `when (instrument) { is Share -> ..., is Future -> ... }` — компилятор exhaustive. Плюсы: ISP, компилятор-проверка. Минусы: переписать `TInvestInstrumentMapper.mapProtoToDomain` (118 строк, `@Suppress` в detekt-baseline), `BcsBrokerApi` маппинг, все места `InstrumentUi` (карточки, поиск, окна, `InstrumentRepository`).

**Выгоды:** один тип — простая передача через UI, `instrumentType: String` уже есть. Работает.

**Ссылка на задачу:** — (не заведена). Концептуально связано с `### Рефакторинг архитектурного долга (detekt-группа 3)` → `#### Data-слой T-Invest` (`mapProtoToDomain` декомпозиция).

### Н7. ttech_figi — временное, без задачи

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `InstrumentUi.ttech_figi` — KDoc говорит «**Временно** — нужен для подписки на `LastPrice` через gRPC-стрим (там figi, не uid). Уйдёт, когда стрим перейдёт на `instrumentUid`». Задачи в `docs/ROADMAP.md` / `docs/ISSUES.md` на удаление `ttech_figi` нет.

**Нарушенное правило:** 2d — временное решение без задачи на полное.

**Clean-альтернатива:** задача в ROADMAP «Перевести gRPC `LastPrice` на `instrumentUid`; удалить `ttech_figi`». Плюсы: 2d соблюдён. Минусы: — (это не альтернатива, а фиксация).

**Выгоды:** работает сейчас; поле нужно для стрима.

**Ссылка на задачу:** — (не заведена). Требует 2d-фиксации.

### Н8. OrderTypeSelection — sealed class для sum-type

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `domain/models/OrderModels.kt` — `OrderTypeSelection` объявлен как `sealed class` для чистого sum-type (5 `object`-веток без shared state). По 2a (актуальность): Kotlin 1.5+ рекомендует `sealed interface` для sum-types без shared state. В том же файле для `Order` уже выбрано `sealed interface` (`### Split order domain into sealed Regular/Stop`).

**Нарушенное правило:** Не Clean — стилистическая последовательность (2a).

**Clean-альтернатива:** `sealed interface OrderTypeSelection`. Плюсы: консистентность с `Order`, рекомендация Kotlin. Минусы: правка `when`-конструкций (механически совместимо), ~10 мин.

**Выгоды:** — (нет существенных).

**Ссылка на задачу:** — (не заведена). Можно включить в `### Split order domain into sealed Regular/Stop`.

### Н16. TradeCheckResult — sealed class для sum-type

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `domain/models/TradingStatus.kt` — `TradeCheckResult` объявлен как `sealed class` (`data object Success` + `data class Error(message: String)`). Тот же класс проблемы, что Н8.

**Нарушенное правило:** Не Clean — стилистическая последовательность (2a).

**Clean-альтернатива:** `sealed interface TradeCheckResult`. Плюсы: консистентность. Минусы: механическая правка `when`-конструкций (~5 мин).

**Выгоды:** — (нет существенных).

**Ссылка на задачу:** — (не заведена).

### Н13. KDoc StreamState — устаревший пакет

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

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

| ID | Серьёзность | Затрагиваемые уровни | Локализация | live тест |
|---|---|---|---|---|
| Н25. BrokerApi fat дублирует nullable-val роли | критично | L4, L6 | `domain/api/BrokerApi.kt` (19 fat-методов) | - |
| Н26. BrokerLifecycleApi — не 2c-фиксация | средне | L4 | `roles/BrokerLifecycleApi.kt` | - |
| Н27. StreamsApi.subscribeOrderState — suspend | средне | L4 | `roles/StreamsApi.kt` | - |
| Н28. AccountsApi.getAccounts(sandboxMode) | средне | L4 | `roles/AccountsApi.kt` | - |
| Н30. BrokerSnapshotApi KDoc — устарел после #9 | средне | L4 | `roles/BrokerSnapshotApi.kt` | - |
| Н31. StreamsApi.subscribeLastPrices(uids) — T-Invest-специфика | средне | L4 | `roles/StreamsApi.kt` | - |
| Н32. StreamCombinators — транспорт в domain/api | средне | L4 | `domain/api/StreamCombinators.kt` | - |
| Н32a. streamWithFallback — семантически неверное имя | низко | L4 | `domain/api/StreamCombinators.kt` | - |
| Н60. retryWithBackoff — non-retryable не классифицирует | высоко | L4 | `domain/api/StreamCombinators.kt` | - |
| Н61. retryWithBackoff — busy loop на normal completion | высоко | L4 | `domain/api/StreamCombinators.kt` | - |
| Н70. BrokerApi — @Suppress("TooManyFunctions") | средне | L4 | `domain/api/BrokerApi.kt` | - |

### Н26. BrokerLifecycleApi — не 2c-фиксация

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `BrokerLifecycleApi.initialize(credentials: BrokerCredentials): Boolean` — runtime `IllegalArgumentException` при неверном типе credentials. KDoc: «Сообщение об ошибке — через `AppLogger` и `AppResult.Failure`» — domain-интерфейс ссылается на `util.AppLogger` (domain -> util).

**Нарушенное правило Clean:** runtime-проверка типов вместо compile-time; KDoc связывает domain с `util`.

**Clean-альтернатива:** `BrokerLifecycleApi<C : BrokerCredentials>` (generic по типу credentials). Плюсы: compile-time гарантия. Минусы: параметризация роли, `BrokerApi.lifecycle: BrokerLifecycleApi<*>?` теряет типизацию.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена).

### Н27. StreamsApi.subscribeOrderState — suspend

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `StreamsApi.subscribePositions` / `subscribeLastPrices` — non-suspend; `subscribeOrderState` — suspend. KDoc: «как в `BrokerApi.subscribeOrderState` (контракт совпадает с fat-интерфейсом)».

**Нарушенное правило Clean:** не Clean-инвариант, а консистентность роли — 2 метода non-suspend, 1 suspend, без технической необходимости.

**Clean-альтернатива:** убрать `suspend` у `subscribeOrderState` (обернуть в `flow { }` внутри реализации). Плюсы: консистентность. Минусы: мелочь.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Может быть частью Н25.

### Н28. AccountsApi.getAccounts(sandboxMode)

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** общий `AccountsApi` принимает `sandboxMode: Boolean`. KDoc: «У брокеров без sandbox (BCS/Finam) параметр игнорируется». Domain-контракт роли несёт специфику T-Invest.

**Нарушенное правило Clean:** domain-контракт роли несёт специфику T-Invest (sandbox); BCS/Finam игнорируют — параметр маскирует «не применимо» под «применимо, но неважно».

**Clean-альтернатива:** вынести `sandboxMode` в `TInvestAccountsApi : AccountsApi` (T-Invest-специфика). Или `AccountsApi` без параметра, sandbox-специфика — в реализации. Плюсы: чистый общий контракт. Минусы: переделка TInvest + `InvestRepository`.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена).

### Н30. BrokerSnapshotApi KDoc — устарел после #9

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `BrokerSnapshotApi.positionsSnapshot` KDoc: «**Не путать** с `BrokerApi.fetchPortfolioPositions` — тот возвращает полный `PortfolioPosition` для UI (`totalValue`, `profitPercent`, `priceChangePercent`)». После #9 `fetchPortfolioPositions` возвращает `PositionStreamItem` (domain, без presentation-полей), KDoc устарел.

**Нарушенное правило Clean:** актуальность документации (не строго Clean).

**Clean-альтернатива:** переформулировать: «Не путать с `fetchPortfolioPositions`: тот возвращает full snapshot (с `currentPrice`) — источник P&L в `InvestRepository.fetchPositionsResult`; здесь — partial для merge со stream в `PositionFlowManager`». Плюсы: актуально. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Мелочь, правится при следующем касании `BrokerSnapshotApi.kt`.

### Н32. StreamCombinators — транспорт в domain/api

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `StreamCombinators.streamWithFallback` (merge источников — gRPC + WS параллельно) + `retryWithBackoff` (переподключение с backoff и rate-limit при обрыве) — транспортные утилиты. Используются в `data/orchestrator/TInvestOrchestrator.kt` для merge gRPC + WS на транспортном уровне и переподключения при обрыве с учётом rate-limits. Живут в `domain/api/` — неверное место. Менеджеры (`domain/api/managers/*`) **не используют** `streamWithFallback` — осознанное архитектурное решение, **не долг**: `StreamStateTracker` должен различать источник (stream vs polling) для корректного `LIVE`/`POLLING`/`STALE`; `streamWithFallback` этого знания не даёт.

**Нарушенное правило Clean:** dependency rule — transport-специфика (retry, backoff, rate-limit) в `domain/api/`.

**Clean-альтернатива:** `data/orchestrator/StreamCombinators.kt` (рядом с `TInvestOrchestrator`) или `data/transport/StreamCombinators.kt`. Плюсы: `domain/api/` содержит только роли и `BrokerApi`; domain не знает про transport-специфику. Минусы: переезд 1 файла + правка импортов в `TInvestOrchestrator` (единственный потребитель — grep 2026-10-27).

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Связано: `### StreamState в data-слое` (закрыта — `StreamStateTracker`), `### Оркестратор стримов и Ktor WS transport` (закрыта — `TInvestOrchestrator`).

### Н32a. streamWithFallback — семантически неверное имя

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `streamWithFallback` делает `merge` (параллельная эмиссия из всех источников), а не fallback (переключение при отказе). В T-Invest-контексте WS — резервный транспорт для gRPC, но merge **параллельный**, осознанно (оба идут одновременно, WS не включается по отказу). Имя вводит в заблуждение.

**Нарушенное правило Clean:** не строго Clean — семантическая честность.

**Clean-альтернатива:** переименовать в `mergeStreamSources` / `combineStreams` (с KDoc «WS — резервный транспорт, merge параллельный»). Плюсы: честное имя. Минусы: правка ~3 callsites (`TInvestOrchestrator`, единственный потребитель).

**Выгоды:** «fallback» отражает бизнес-логику «WS — резерв». Компромисс.

**Ссылка на задачу:** — (не заведена). Может быть объединено с Н32 (переезд + переименование одним коммитом).
### Н60. retryWithBackoff — non-retryable не классифицирует

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `StreamCombinators.retryWithBackoff` переподключает любой источник при любой ошибке с backoff. `UNAUTHENTICATED`, `NOT_FOUND`, `PERMISSION_DENIED`, `INVALID_ARGUMENT` — вечный retry-loop (до остановки вручную). `CancellationException` пробрасывается, но не-recoverable gRPC-коды не различаются.

**Нарушенное правило Clean:** не строго Clean — resilience-логика не различает recoverable/non-recoverable.

**Clean-альтернатива:** классификация по `Status.fromThrowable(e).code`: retry — `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `INTERNAL`, `UNKNOWN`; non-retry — `NOT_FOUND`, `UNAUTHENTICATED`, `PERMISSION_DENIED`, `INVALID_ARGUMENT`, `FAILED_PRECONDITION`. Плюсы: батарея, трафик, rate-limit не тратятся на безнадёжный retry. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `docs/ISSUES.md` → «gRPC retry: бесконечный retry на не-recoverable ошибках» (расширено в L5-Н60: «касается также `StreamCombinators.retryWithBackoff`»).

### Н61. retryWithBackoff — busy loop на normal completion

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `StreamCombinators.retryWithBackoff`: при normal completion (`source().collect { emit(it) }` вернулся без exception) — `attempt = 0` и новый цикл **без delay**. Если source завершается мгновенно-пустым (сервер закрыл стрим, gRPC завершился нормально) — busy loop, CPU 100%, батарея в ноль.

**Нарушенное правило Clean:** не строго Clean — resilience-логика не защищена от мгновенного нормального завершения.

**Clean-альтернатива:** при normal completion — `delay(1_000)` перед новой попыткой (как в `PriceFlowManager.collectStream`). Плюсы: защита от busy loop. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `docs/ISSUES.md` → «PriceFlowManager.collectStream: потенциальный busy loop» (расширено в L5-Н61: «касается также `StreamCombinators.retryWithBackoff`»).

### Исправленные (L4)

### ✅ Н25. BrokerApi fat дублирует nullable-val роли

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

### ✅ Н70. BrokerApi — @Suppress("TooManyFunctions")

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

### ✅ Н31. StreamsApi.subscribeLastPrices(uids) — T-Invest-специфика

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

## L5 — domain/managers

**Краткая таблица:**

| ID | Серьёзность | Затрагиваемые уровни | Локализация | live тест |
|---|---|---|---|---|
| Н38. PriceFlowManager → data.orchestrator.DebugConfig | критично | L5, L6 | `PriceFlowManager.kt` | ✓ |
| Н39. PriceFlowManager → util.toAppError | высоко | L5, L2 | `PriceFlowManager.kt` | - |
| Н40. Менеджеры → di.BrokerManager | критично | L5, L7 | 3 менеджера (`PositionFlowManager`, `PriceFlowManager`, `OrderFlowManager`) | - |
| Н41. Хардкод BrokerName.TINVEST в менеджерах | средне | L5 | 3 менеджера | - |
| Н58. PositionFlowManager.runPolling — string matching NOT_FOUND | средне | L5 | `PositionFlowManager.kt` | - |
| Н59. PriceFlowManager.runPolling — swallows CancellationException | средне | L5 | `PriceFlowManager.kt` | - |

### Н39. PriceFlowManager → util.toAppError

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `PriceFlowManager.kt` импортирует `util.toAppError`, использует в `runPolling` (`AppLogger.w(TAG, "REST poll failed: ${e.toAppError(BrokerName.TINVEST).message}", e)`). `toAppError` живёт в `util/AppResultExtensions.kt` и импортирует `io.grpc.StatusException`. Domain видит io.grpc транзитивно.

**Нарушенное правило Clean:** dependency rule — domain видит io.grpc через `util`.

**Clean-альтернатива:** `AppErrorMapper` в data (Н20). `PriceFlowManager` инжектит `AppErrorMapper` (или `Logger` с `AppError`-конвертацией). Плюсы: domain без gRPC. Минусы: инъекция в менеджер.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). **Связано с H20** (`AppErrorMapper` в data).

### Н41. Хардкод BrokerName.TINVEST в менеджерах

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `PositionFlowManager.doStart`, `OrderFlowManager.doStart` — `brokerManager.getBroker(BrokerName.TINVEST)`; `PriceFlowManager.collectStream` + `runPolling` — `BrokerName.TINVEST` (локальные val). «Работаем только с T-Invest» захардкожено в domain/managers. При появлении BCS/Finam позиций/цен/заявок — рефакторинг всех трёх.

**Нарушенное правило Clean:** domain-знание «единственный брокер = T-Invest» в менеджерах, не в конфигурации.

**Clean-альтернатива:** менеджер получает `BrokerName` из состояния (текущий счёт определяет брокера), либо мультиброкерная агрегация (несколько источников). Плюсы: готово к мультиброкерности. Минусы: правка всех трёх менеджеров (~3–4 ч).

**Выгоды:** KISS — до появления BCS/Finam работа только с T-Invest достаточна.

**Ссылка на задачу:** — (не заведена). Связано с `### Реализация BCS Broker API` / `### Реализация Finam Broker API`.

### Н58. PositionFlowManager.runPolling — string matching NOT_FOUND

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `PositionFlowManager.runPolling`: `if (e.message?.contains("NOT_FOUND") == true) return`. `e.message` у `StatusException` — человекочитаемый текст, не код. При изменении формата/локализации — проверка молча перестанет срабатывать, polling продолжит крутиться на удалённом счёте.

**Нарушенное правило Clean:** не строго Clean — string matching вместо structured API.

**Clean-альтернатива:** `Status.fromThrowable(e).code == Status.Code.NOT_FOUND`. Плюсы: не зависит от формата `message`. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `docs/ISSUES.md` → «gRPC retry: string matching NOT_FOUND вместо Status.Code» (расширено в L5-Н58: «касается также `PositionFlowManager.runPolling`»).

### Н59. PriceFlowManager.runPolling — swallows CancellationException

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `PriceFlowManager.runPolling`: единственный `catch (e: Exception)` без предшествующего `catch (e: CancellationException) { throw e }`. `CancellationException` перехватывается и логируется как «REST poll failed», сигнал отмены родителю не пробрасывается — менеджер останавливается «тихо».

**Нарушенное правило Clean:** не строго Clean — нарушен контракт structured concurrency.

**Clean-альтернатива:** `catch (e: CancellationException) { throw e }` перед общим `catch (e: Exception)` — как в `PositionFlowManager.runPolling` / `OrderFlowManager.runPolling`. Плюсы: консистентность, отмена работает. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `docs/ISSUES.md` → «PriceFlowManager.runPolling swallows CancellationException» (отдельная запись).

### Исправленные (L5)

[пусто]

---

### ✅ Н38. PriceFlowManager → data.orchestrator.DebugConfig

**Статус:** исправлено (PR #59, `dev`, 2026-10-28).
**Серьёзность:** критично.

**Live тест:** ✓

**Описание:** `PriceFlowManager.kt` импортирует `data.orchestrator.DebugConfig`, использует в `collectStream` (`if (DebugConfig.disableGrpc && DebugConfig.disableWs) return`). Менеджер (domain) зависит от data.

**Нарушенное правило Clean:** dependency rule — domain зависит от data.

**Clean-альтернатива:** `DebugFlagsRepository` (domain-интерфейс, из Н23). Менеджер инжектит интерфейс, реализация — `DebugConfig` за `@Binds`. Плюсы: domain не видит data. Минусы: правка `PriceFlowManager` (1 callsite) + H23.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). **Связано с H23** (`DebugFlagsRepository`).

---

### ✅ Н40. Менеджеры → di.BrokerManager

**Статус:** исправлено (PR #60, `dev`, 2026-10-28).

**Серьёзность:** критично.

**Live тест:** -

**Описание:** все три менеджера (`PositionFlowManager`, `PriceFlowManager`, `OrderFlowManager`) инжектят `com.gitlab.biomorf.tscalp.di.BrokerManager` напрямую через конструктор. Domain-слой зависит от `di/`.

**Нарушенное правило Clean:** dependency rule — domain зависит от `di`.

**Clean-альтернатива:** `BrokerRegistry` (domain-интерфейс, дизайн в L7-Н22/Н29). Менеджеры инжектят `BrokerRegistry`. Плюсы: domain чист. Минусы: правка 3 менеджеров.

**Выгоды:** —.

**Ссылка на задачу:** `### H22. BrokerRegistry` (PR #60, `dev`).

## L6 — data/repository

**Краткая таблица:**

| ID | Серьёзность | Затрагиваемые уровни | Локализация | live тест |
|---|---|---|---|---|
| Н44. data/repository/* → di.BrokerManager | критично | L6, L7 | 3 файла: `SearchCache.kt`, `InstrumentRepository.kt`, `InvestRepository.kt` | - |
| Н45. InvestRepository использует 4 fat-метода BrokerApi | высоко | L6, L4 | `InvestRepository.kt` | - |
| Н46. SearchCache.search бросает IllegalArgumentException | средне | L6, L1 | `SearchCache.kt` | - |
| Н47. SearchCache.search — избыточный withContext(Dispatchers.IO) | низко | L6 | `SearchCache.kt` | - |
| Н62. TInvestBrokerAPI.initialize не закрывает positionsWebSocket | критично | L6 | `TInvestBrokerApi.kt` | ✓ |
| Н63. TInvestMarketDataService — god-класс 622 строки | критично | L6 | `TInvestMarketDataService.kt` | - |
| Н64. fetchPortfolioPositions — @Suppress("CyclomaticComplexMethod") | средне | L6 | `TInvestMarketDataService.kt` | - |
| Н65. mapProtoToDomain — @Suppress("LongMethod") | средне | L6 | `TInvestInstrumentMapper.kt` | - |
| Н66. getTradingStatuses — N параллельных unary | высоко | L6 | `TInvestMarketDataService.kt` | ✓ |
| Н67. postStopOrder — закомментированный expireDate | средне | L6 | `TInvestOrdersService.kt` | ✓ |
| Н68. SettingsRepository — @Suppress("TooManyFunctions") | средне | L6 | `SettingsRepository.kt` | - |
| Н69. InvestRepository — @Suppress("TooManyFunctions") | средне | L6 | `InvestRepository.kt` | - |
| Н71. BcsBrokerApi.initialize логирует refreshToken | критично | L6 | `BcsBrokerApi.kt` | ✓ |
| Н72. BcsBrokerApi.orders = null при работающем postOrder | высоко | L6, L4 | `BcsBrokerApi.kt` | ✓ |
| Н73. FinamBrokerApi.checkTradeAvailability — всегда Success | высоко | L6 | `FinamBrokerApi.kt` | ✓ |
| Н74. FinamBrokerApi.subscribePositions — dead callbackFlow | средне | L6 | `FinamBrokerApi.kt` | - |
| Н75. FinamBrokerApi — 6 TODO("Not yet implemented") | средне | L6 | `FinamBrokerApi.kt` | - |
| Н76. KDoc ссылается на несуществующую ### Реализация BCS Broker API | высоко | L6 | `BcsBrokerApi.kt` | - |
| Н77. BcsBrokerApi.subscribePositions → emptyFlow(), polling-loop удалён | высоко | L6 | `BcsBrokerApi.kt` | - |
| Н78. BcsBrokerApi OkHttpClient без CustomTrustManager | высоко | L6 | `BcsBrokerApi.kt` | - |
| Н79. CustomTrustManager loadCertificateToKeyStore — swallowed Exception | средне | L6 | `CustomTrustManager.kt` | - |
| Н80. CustomTrustManager — нет проверки загрузки всех 5 CA | низко | L6 | `CustomTrustManager.kt` | - |
| Н81. CustomTrustManager — CustomX509TrustManager lint-warning (принято 2c) | низко | L6 | `CustomTrustManager.kt` | - |

**Соответствуют Clean (data-only, никаких утечек):**
`TradingStateRepository`, `SettingsRepository`.

### Н45. InvestRepository использует 4 fat-метода BrokerApi

**Статус:** исправлено (PR #56, `dev`). `InvestRepository` удалён;
4 fat-метода `BrokerApi` (`getLastPricesByTscalpInstrumentId`,
`fetchPortfolioPositions`, `checkTradeAvailability`,
`subscribePositions`) остались без потребителя — ревизия fat-части
вынесена задачей `### BrokerApi: ревизия fat-части после
InvestRepository split` (ROADMAP).

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `InvestRepository` вызывает 4 fat-метода `BrokerApi`:
- `broker.getLastPricesByTscalpInstrumentId(ids)` — `getLastPricesResult`;
- `broker.fetchPortfolioPositions(accountId, sandboxMode)` — `fetchPositionsResult`;
- `broker.checkTradeAvailability(...)` — `checkTradeAvailabilityResult`;
- `broker.subscribePositions(accountId)` — `subscribePositions` (дубль: есть `streams?.subscribePositions`).

Остальные 12 методов — через nullable-val роли. KDoc `BrokerApi` говорит «`InvestRepository` — фасад на fat `BrokerApi`, 29 callsites, использует все» — фактически использует 4 fat-метода, остальное через роли.

**Нарушенное правило Clean:** Interface Segregation — `InvestRepository` зависит от fat `BrokerApi` ради 4 методов.

**Clean-альтернатива (по H25):** выделить `PortfolioApi` (`fetchPortfolioPositions`), `MarketDataApi` (`getLastPricesByTscalpInstrumentId`), `TradeApi` (`checkTradeAvailability`); `subscribePositions` — на `streams?.subscribePositions`. `InvestRepository` инжектит роли. Плюсы: полный ISP. Минусы: 4 callsite правок в `InvestRepository` + новые роли.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). **Связано с H25**.

### Н46. SearchCache.search бросает IllegalArgumentException

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `SearchCache.search` при ненайденном брокере: `throw IllegalArgumentException("Брокер … не найден")`. Остальные методы `InvestRepository` / data — возвращают `AppResult<T>`, не бросают. Исключение из data в presentation (`OrdersViewModel.onSearchQueryChanged` ловит `Exception`, показывает «Ошибка поиска: …»).

**Нарушенное правило Clean:** контракт data — `AppResult<T>` (по AGENTS: «`AppResult<T>` (Success/Failure), не исключения на границах слоёв»).

**Clean-альтернатива:** `SearchCache.search` возвращает `AppResult<List<InstrumentUi>>` (или `List<InstrumentUi>` без throw — пустой при ненайденном брокере, как `InvestRepository`). Плюсы: консистентность с data-контрактом. Минусы: правка `OrdersViewModel` (2 callsites).

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена).

### Н47. SearchCache.search — избыточный withContext(Dispatchers.IO)

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `SearchCache.search`: `withContext(Dispatchers.IO) { broker.userInstruments?.findInstruments(query) ?: emptyList() }`. `findInstruments` — suspend, внутри data-слоя брокер сам выбирает диспатчер (gRPC/REST). `withContext(Dispatchers.IO)` в data — избыточная диспатчеризация.

**Нарушенное правило Clean:** не строго Clean — data не доверяет слою ниже, дублирует диспатчеризацию.

**Clean-альтернатива:** убрать `withContext(Dispatchers.IO)` — вызывать `findInstruments` напрямую. Плюсы: чисто. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Мелочь, правится при следующем касании `SearchCache`.

### Н62. TInvestBrokerAPI.initialize не закрывает positionsWebSocket

**Статус:** открыто.

**Серьёзность:** критично.

**Live тест:** ✓

**Описание:** `TInvestBrokerAPI.initialize` перезаписывает `positionsWebSocket` без `close()` предыдущего. Ре-логин, смена токена, повторное «Сохранить» в настройках — старая WS-сессия держит сокет, получает ping/frames в никуда. Утечка соединения + нагрузка на сервер (WS-сессии накапливаются). `deinitialize` тоже обнуляет без `close()` — та же утечка при logout.

**Нарушенное правило Clean:** не строго Clean — ресурсный leak в lifecycle.

**Clean-альтернатива:** `positionsWebSocket?.close()` перед перезаписью; добавить `close()` в `TInvestPositionsWebSocket`. Плюсы: нет утечки. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `docs/ISSUES.md` → «TInvestBrokerAPI.initialize() не закрывает предыдущий positionsWebSocket» (ветка `bugfix/ws-lifecycle`).

### Н63. TInvestMarketDataService — god-класс 622 строки

**Статус:** открыто.

**Серьёзность:** критично.

**Live тест:** -

**Описание:** `TInvestMarketDataService` — 622 строки, 19 функций, `@Suppress("TooManyFunctions")`. Отвечает за: счета (`getAccounts`, `openSandboxAccount`, `closeSandboxAccount`), портфель (`getPortfolio`, `fetchPortfolioPositions`), gRPC-стримы позиций (`subscribePositionsGrpc`, `subscribePositionsGrpcWithReconnect`, `convertSecurity`, `convertFuture`), баланс (`getBalance`, `sandboxPayIn`), инструменты (`fetchFullInstrument`, `findInstruments`, `findInstrumentShorts`, `fetchProtoInstrument`, `getFuturesMargin`), цены/статусы (`getLastPricesByTscalpInstrumentId`, `getTradingStatuses`), gRPC-стримы цен/ордеров (`subscribeLastPrices`, `subscribeOrderState`), проверка (`checkTradeAvailability`).

**Нарушенное правило Clean:** SRP — класс отвечает за 8+ доменных областей.

**Clean-альтернатива:** разбить по областям: `TInvestAccountsService`, `TInvestPortfolioService`, `TInvestPositionsService`, `TInvestInstrumentsService`, `TInvestPricesService`, `TInvestTradingService`. Плюсы: один сервис — одна область. Минусы: 6 файлов вместо 1, `TInvestApiState` шарится.

**Выгоды:** все методы работают на общем `TInvestApiState`, единая точка для gRPC-каналов. Разбивка — при рефакторинге.

**Ссылка на задачу:** `docs/ROADMAP.md` → `### Рефакторинг архитектурного долга (detekt-группа 3)` → `#### Data-слой T-Invest` (`fetchPositionsSnapshot` декомпозиция + `mapProtoToDomain`).

### Н64. fetchPortfolioPositions — @Suppress("CyclomaticComplexMethod")

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `TInvestMarketDataService.fetchPortfolioPositions` — `@Suppress("CyclomaticComplexMethod")`. ~40 строк, внутри: mapNotNull по позициям, fetchProtoInstrument для каждой, getFuturesMargin для фьючерсов, маппинг proto → domain, вычисление quantity/currentPrice/expectedYield/averagePrice, сборка `PositionStreamItem`.

**Нарушенное правило Clean:** SRP — функция делает 3 вещи (fetch instrument, fetch margin, map position).

**Clean-альтернатива:** `fetchPortfolioPositions` = `getPortfolio` + `.map { mapPositionToStreamItem(it, fetchInstrumentFn) }`. Плюсы: декомпозиция. Минусы: —.

**Выгоды:** один метод, работает.

**Ссылка на задачу:** `docs/ROADMAP.md` → `### Рефакторинг архитектурного долга (detekt-группа 3)` → `#### Data-слой T-Invest` (упомянут `fetchPositionsSnapshot` декомпозиция).

### Н65. TInvestInstrumentMapper.mapProtoToDomain — @Suppress("LongMethod")

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `TInvestInstrumentMapper.mapProtoToDomain` — 118 строк, `@Suppress("LongMethod")`. Единый `when` по `type` с тремя ветками (futures / share / else), в каждой — полный конструктор модели (~40 аргументов).

**Нарушенное правило Clean:** SRP — функция маппит 3 типа инструмента.

**Clean-альтернатива:** `mapFutures`, `mapShares`, `mapDefault` + диспетчеризация в `mapProtoToDomain`. Плюсы: каждая ветка ~30 строк. Минусы: —.

**Выгоды:** один `when`, вся логика в одном месте.

**Ссылка на задачу:** `docs/ROADMAP.md` → `### Рефакторинг архитектурного долга (detekt-группа 3)` → `#### Data-слой T-Invest`.

### Н66. getTradingStatuses — N параллельных unary

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** ✓

**Описание:** `TInvestMarketDataService.getTradingStatuses`: `coroutineScope { ids.map { async { getTradingStatus(uid) } }.awaitAll() }` — N параллельных unary-запросов. При 100 инструментах — 100 параллельных запросов. При превышении лимита T-Invest (`RESOURCE_EXHAUSTED`) — часть падает в `UNKNOWN`.

**Нарушенное правило Clean:** не строго Clean — не учитывает rate-limit внешнего API.

**Clean-альтернатива:** через rate-limiter (ROADMAP `### Rate-limiter для unary-запросов T-Invest`) или батчинг (если API даст batch-метод). Плюсы: нет `RESOURCE_EXHAUSTED`. Минусы: —.

**Выгоды:** быстрее, чем последовательный вызов.

**Ссылка на задачу:** `docs/ROADMAP.md` → `### Rate-limiter для unary-запросов T-Invest`.

### Н67. postStopOrder — закомментированный expireDate

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** ✓

**Описание:** `TInvestOrdersService.postStopOrder`: `// if (request.expireDate != null) builder.setExpireDate(parseDate(request.expireDate))`. `parseDate` закомментирован. Цепочка `expireDate` рвётся в 4+ местах (UI, ViewModel, UseCase, Service, parseDate). UI физически не может выбрать `GOOD_TILL_DATE` — всегда уходит `GOOD_TILL_CANCEL`.

**Нарушенное правило Clean:** не строго Clean — нереализованная фича, размазанная по слоям.

**Clean-альтернатива:** восстановить цепочку: UI (DatePicker) → ViewModel (`expireDate`) → UseCase (`StopOrderRequest.expireDate`) → Service (`parseDate` + `builder.setExpireDate`). Плюсы: работает `GOOD_TILL_DATE`. Минусы: ~2–3 ч.

**Выгоды:** работает `GOOD_TILL_CANCEL` (default), пользователь не может выбрать дату.

**Ссылка на задачу:** `docs/ROADMAP.md` → `### UI выбора даты истечения стоп-заявки (GOOD_TILL_DATE)`.

### Н68. SettingsRepository — @Suppress("TooManyFunctions")

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `SettingsRepository` — 125 строк, 15 функций, `@Suppress("TooManyFunctions")`. Объединяет: учётные данные брокеров (5 методов), счёт по умолчанию (2), режим песочницы (2), подтверждение заявок (3), ключи (3). KDoc ссылается на `### Рефакторинг репозиториев`.

**Нарушенное правило Clean:** SRP — репозиторий отвечает за 4 области настроек.

**Clean-альтернатива:** разбить на `BrokerCredentialsRepository`, `AccountPreferencesRepository`, `SandboxPreferencesRepository`, `TradingPreferencesRepository`. Плюсы: SRP. Минусы: 4 репозитория вместо 1; `@Binds` для каждого.

**Выгоды:** один репозиторий, простая навигация.


**Уточнение (2026-10-27, H21b):** после `git mv` класс →
`SettingsRepositoryImpl`; 12 функций (было 15: KDoc устарел).
`@Suppress("TooManyFunctions")` остался на `SettingsRepositoryImpl` и
добавлен на интерфейс `domain.api.repository.SettingsRepository`
(13 членов). Декомпозиция (4 области) — не выполнена, не в scope H21b.

**Ссылка на задачу:** `docs/ROADMAP.md` → `### Рефакторинг архитектурного долга (detekt-группа 3)` → `#### Репозитории` (SettingsRepository — 15 функций).

### Н69. InvestRepository — @Suppress("TooManyFunctions")

**Статус:** исправлено (PR #56, `dev`). `InvestRepository` удалён;
`@Suppress` ушёл вместе с ним.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `InvestRepository` — 357 строк, 17 функций, `@Suppress("TooManyFunctions")`. Объединяет 4 доменные области (аккаунты, заявки, портфель, стримы) + фасад над `BrokerApi`.

**Нарушенное правило Clean:** SRP + Clean-граница (presentation → domain, а не presentation → data → domain).

**Clean-альтернатива:** `### Разбивка InvestRepository по доменным областям` — 4 domain-интерфейса + 4 data-реализации.

**Выгоды:** один фасад, простой доступ для UI.

**Ссылка на задачу:** `docs/ROADMAP.md` → `### Разбивка InvestRepository по доменным областям` (в работе).

### Н72. BcsBrokerApi.orders = null при работающем postOrder

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** ✓

**Описание:** `BcsBrokerApi.orders = null`, но `postOrder` реализован (HTTP POST). `InvestRepository.postOrderResult` использует `broker.orders?.postOrder` → для BCS всегда `Failure`, хотя `postOrder` работает. `postOrder` доступен только через fat-метод `BrokerApi.postOrder`.

**Нарушенное правило Clean:** не строго Clean — nullable-val supertype «все методы»; BCS честно не ставит supertype, но fat-метод работает в обход роли.

**Clean-альтернатива (по комментарию в закрытой #4):** разделить `OrdersApi` на `OrdersQueryApi` + `OrdersCommandApi`; BCS реализует `OrdersCommandApi` (postOrder), `OrdersQueryApi = null`. Плюсы: BCS-торговля доступна через роль. Минусы: +1 роль.

**Выгоды:** —.

**Ссылка на задачу:** `### Реализация BCS Broker API` (ROADMAP — не заведена).

### Н73. FinamBrokerApi.checkTradeAvailability — всегда Success

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** ✓

**Описание:** `FinamBrokerApi.checkTradeAvailability(...) = TradeCheckResult.Success` — фиктивный контракт. Finam — заглушка, все данные пустые. UI (через `InvestRepository.checkTradeAvailabilityResult`) думает, что сделка возможна → отправляет заявку → `postOrder` возвращает фиктивный `OrderResult("", 0L, 0L, OrderStatus.NEW)`.

**Нарушенное правило Clean:** не строго Clean — фиктивный контракт вместо честного «не поддерживается».

**Clean-альтернатива:** `TradeCheckResult.Error("Finam API не поддерживается")` или `throw NotImplementedError`. Плюсы: UI видит реальное состояние. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `### Реализация Finam Broker API` (ROADMAP).

### Н74. FinamBrokerApi.subscribePositions — dead callbackFlow

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `FinamBrokerApi.subscribePositions` — `callbackFlow` с polling-loop раз в 10 секунд, но `fetchPortfolioPositions` возвращает `emptyList()` (заглушка). Loop крутится вхолостую. При этом `streams = null` — менеджеры используют `broker.streams?.subscribePositions ?: skip`, значит fat-метод — dead code.

**Нарушенное правило Clean:** не строго Clean — dead code, вхолостую крутящий ресурсы.

**Clean-альтернатива:** `return emptyFlow()` (как у BCS) или `flowOf()`. Плюсы: без вхолостую крутящегося loop. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `### Реализация Finam Broker API` (ROADMAP).

### Н75. FinamBrokerApi — 6 TODO("Not yet implemented")

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `FinamBrokerApi`: `openSandboxAccount`, `closeSandboxAccount`, `sandboxPayIn`, `cancelOrder`, `postStopOrder`, `cancelStopOrder` — `TODO("Not yet implemented")`. По AGENTS «Запрещено оставлять `TODO` без согласования». Безопасно благодаря nullable-val (`sandbox = null`, `orders = null`), но при появлении роли — упадёт.

**Нарушенное правило Clean:** не строго Clean — `NotImplementedError` вместо честной заглушки.

**Clean-альтернатива:** `emptyList()` / `throw UnsupportedOperationException("...")` (как BCS) или `error("Not implemented: <метод>")` с KDoc. Плюсы: явная семантика. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `### Реализация Finam Broker API` (ROADMAP).

### Н76. KDoc ссылается на несуществующую ### Реализация BCS Broker API

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `BcsBrokerApi.kt` KDoc: «Восстановление — задача `### Реализация BCS Broker API` в ROADMAP». Такой задачи в `docs/ROADMAP.md` **нет** (есть только `### Реализация Finam Broker API`). Комментарии в `BcsBrokerApi` и `docs/ISSUES.md` тоже упоминают её как существующую.

**Нарушенное правило Clean:** AGENTS 5e (self-sufficiency) — ссылка на несуществующую задачу. Плюс — дырка в планировании.

**Clean-альтернатива:** завести `### Реализация BCS Broker API` в `docs/ROADMAP.md` (план по 5e) **или** переформулировать KDoc «задача не заведена». Плюсы: ссылка работает. Минусы: новая задача в ROADMAP. **Решение:** оставлено как находка (пользователь: «оставить как находку»).

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена, KDoc висит).

### Н77. BcsBrokerApi.subscribePositions → emptyFlow(), polling-loop удалён

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `BcsBrokerApi.subscribePositions = emptyFlow()`. KDoc: «Polling-loop был сломан (транспорт BCS не отлажен), удалён. Восстановление — задача `### Реализация BCS Broker API`». `emptyFlow()` = «поток сразу завершился», не «нет данных». Плюс — покрытие polling через REST потеряно.

**Нарушенное правило Clean:** не строго Clean — заглушка маскирует «не поддерживаю» под «стрим есть, но пустой».

**Clean-альтернатива:** убрать fat-метод из BCS-реализации (Kotlin требует заглушку из `BrokerApi`) либо `throw UnsupportedOperationException`. Плюсы: честно. Минусы: ломает fat-контракт `BrokerApi` (до Н25).

**Выгоды:** —.

**Ссылка на задачу:** `### Реализация BCS Broker API` (ROADMAP — не заведена).

### Н78. BcsBrokerApi использует OkHttpClient() без CustomTrustManager

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `BcsBrokerApi.kt`: `private val client = OkHttpClient()` — отдельный клиент без российских CA. Сейчас работает (BCS на стандартных CA), но мина: если BCS введёт российские CA — TLS сломается тихо. Инстанс не переиспользуется с DI.

**Нарушенное правило Clean:** не строго Clean — data-слой конструирует ресурс без общей конфигурации.

**Clean-альтернатива:** инжектить `HttpClient`/`OkHttpClient` из DI, единая конфигурация (`CustomTrustManager`). Плюсы: единая точка TLS. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** `docs/ISSUES.md` → «BcsBrokerApi создаёт OkHttpClient без CustomTrustManager».

### Н79. CustomTrustManager loadCertificateToKeyStore — swallowed Exception

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `CustomTrustManager.loadCertificateToKeyStore` — `catch (Exception) { AppLogger.w(TAG, "Не удалось загрузить сертификат из ресурса $resId", e) }`. Если CA-сертификат не загрузился — тихо пропускаем. При подключении к T-Invest: systemTrustManager не знает Russian CA, customTrustManager без сертификата → неинформативная TLS-ошибка. Причина (не загрузился конкретный ресурс) теряется.

**Нарушенное правило Clean:** не строго Clean — resilience-логика молча деградирует.

**Clean-альтернатива:** различать «ресурс отсутствует» (binding-ошибка, не должна случаться) от «сертификат битый» (runtime). При битом — `throw IllegalStateException("CA load failed: res=$resId", e)` (fail-fast), не продолжать с усечённым набором. Плюсы: TLS-проблемы видны сразу. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена).

### Н80. CustomTrustManager — нет проверки загрузки всех 5 CA

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `CustomTrustManager.init` загружает 5 CA (`russian_trusted_root_ca`, `_ca_gost_2025`, `_sub_ca`, `_sub_ca_2024`, `_sub_ca_gost_2025`). Если один упал — `customTrustManager` работает с 4/5. `getAcceptedIssuers` вернёт усечённый набор. Индикации нет.

**Нарушенное правило Clean:** не строго Clean — молчаливая деградация.

**Clean-альтернатива:** после загрузки проверить `customTrustManager.acceptedIssuers.size == 5`; если меньше — `throw IllegalStateException` или `AppLogger.e` (громкий). Плюсы: известно, что все CA активны. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Связано с Н79.

### Н81. CustomTrustManager — CustomX509TrustManager lint-warning (принято 2c)

**Статус:** принято 2c.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `lint-baseline.xml` содержит `CustomX509TrustManager` warning для `data/api/CustomTrustManager.kt:12`. Android lint предупреждает, что custom X509TrustManager может быть insecure. Здесь — осознанно: нужны Russian Trusted Root CA для T-Invest (system trust их не знает).

**Нарушенное правило Clean:** не Clean в строгом смысле — нарушение общего lint-правила.

**Clean-альтернатива:** перейти на системный `Network Security Config` с `trust-anchors` (XML-конфигурация дополнительных CA). Плюсы: без кастомного `X509TrustManager`, lint-warning уходит, конфигурация декларативна. Минусы: требует проверки, что Android принимает все 5 российских CA через `network_security_config.xml` (GOST 2025 может не поддерживаться). Решение — при следующем касании TLS.

**Выгоды (по 2c):** работает сейчас, `CustomX509TrustManager` полностью контролирует fallback (system → custom); NNC может не принять GOST-сертификаты. Baseline зафиксировал предупреждение.

**Ссылка на задачу:** — (не заведена). Связано с `docs/ISSUES.md` → «BcsBrokerApi создаёт OkHttpClient без CustomTrustManager» (потенциальный переезд на NNC затронет всех).

### Исправленные (L6)

### Н71. BcsBrokerApi.initialize логирует refreshToken

**Статус:** исправлено (2026-10-28, ветка `bugfix/security-token-logging`).

**Серьёзность:** критично.

**Live тест:** ✓

**Описание:** `BcsBrokerApi.initialize(refreshToken, clientId)` пишет в лог: `AppLogger.d("BcsBrokerApi", "Инициализация с refreshToken=$refreshToken, clientId=$clientId")`. Refresh-токен — секрет (90 дней, полный доступ к портфелю/торговле). В debug-сборке попадает в logcat в открытом виде; в release `AppLogger.d` отсекается, но `logcat -b all` / устройство может сохранить.

**Нарушенное правило Clean:** AGENTS 4e — секреты не должны попадать в логи.

**Clean-альтернатива:** логировать `refreshToken=***` (redact) или `refreshToken.len=${refreshToken.length}`. Плюсы: секрет не в логах. Минусы: —.

**Выгоды:** —.

**Что сделано:** `$refreshToken` → `${refreshToken.length}`; других утечек нет (grep по `data/api/`).

**Ссылка на задачу:** `docs/ISSUES.md` → «BcsBrokerApi логирует refreshToken» (security-запись).


[пусто]

---

### ✅ Н44. data/repository/* → di.BrokerManager

**Статус:** исправлено (PR #60, `dev`, 2026-10-28).

**Серьёзность:** критично.

**Live тест:** -

**Описание:** `SearchCache`, `InstrumentRepository`, `InvestRepository` инжектят `com.gitlab.biomorf.tscalp.di.BrokerManager` через конструктор. Data-слой зависит от `di/`.

**Нарушенное правило Clean:** dependency rule — data зависит от `di`.

**Clean-альтернатива:** `BrokerRegistry` (domain-интерфейс, дизайн в L7-Н22/Н29). 3 репозитория инжектят `BrokerRegistry`. Плюсы: data → domain. Минусы: правка 3 файлов.

**Выгоды:** —.


**Расширение (2026-10-27, InvestRepository split + H21b):** было 3 файла
(`SearchCache`, `InstrumentRepository`, `InvestRepository`). Стало 8
`*RepositoryImpl`: `AccountsRepositoryImpl`, `InstrumentRepositoryImpl`,
`MarketDataRepositoryImpl`, `OrdersRepositoryImpl`,
`PortfolioRepositoryImpl`, `SearchCacheImpl`, `SettingsRepositoryImpl`,
`TradingStateRepositoryImpl`. Все инжектят `BrokerManager`. Закрывается
в H22 (`BrokerRegistry`), не в H21b.

**Ссылка на задачу:** `### H22. BrokerRegistry` (PR #60, `dev`).

## L7 — di

**Краткая таблица:**

| ID | Серьёзность | Затрагиваемые уровни | Локализация | live тест |
|---|---|---|---|---|
| Н29. BrokerManager — domain-сервис в di/ | критично | L7, L1, L5, L6 | `di/BrokerManager.kt` | - |
| Н35. provideBrokerManager — domain-конфигурация в di | средне | L7, L1 | `di/DataModule.kt` | - |
| Н36. provideTInvestWsHttpClient — транспортная конфигурация в di | средне | L7, L6 | `di/DataModule.kt` | - |
| Н37. Конвенции DI-файлов | низко | L7 | `di/RepositoryModule.kt` | - |

### Н35. provideBrokerManager — domain-конфигурация в di

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `DataModule.provideBrokerManager` содержит domain-конфигурацию: список брокеров (TInvest, BCS, Finam) и **порядок** в `mapOf`, который определяет порядок вкладок в UI (`getAvailableBrokerNames()`) и порядок в `SearchCache`/`InvestRepository`. При добавлении 4-го брокера — правка в `di/`.

**Нарушенное правило Clean:** domain-knowledge (список зарегистрированных брокеров + порядок) в di-слое.

**Clean-альтернатива (чисто):** `BrokerRegistryImpl(brokers: Set<BrokerApi>)` — строит `Map<BrokerName, BrokerApi>` через `BrokerName.entries` (порядок из enum). `defaultBroker: BrokerName = BrokerName.TINVEST` — константа в domain. `DataModule.provideBrokerRegistry(brokers: Set<BrokerApi>)` — тонкий DI. Плюсы: domain-knowledge в domain, порядок вкладок из `BrokerName.entries`. Минусы: переезд, входит в дизайн H22.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Входит в дизайн H22 (`BrokerRegistry`, ROADMAP). См. `**Анализ L7-part1:**` в L1-Н22.

### Н36. provideTInvestWsHttpClient — транспортная конфигурация в di

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `DataModule.provideTInvestWsHttpClient` содержит конфигурацию `HttpClient` (CIO engine, WebSockets, ContentNegotiation, trustManager). Транспортная конфигурация в сборочном слое. При появлении второго клиента (например, для REST) — дублирование конфига.

**Нарушенное правило Clean:** транспортная конфигурация в di-слое.

**Clean-альтернатива (чисто):** `data/transport/TInvestHttpClientFactory` (data) — `fun create(context: Context): HttpClient`. `DataModule.provideHttpClient(factory: TInvestHttpClientFactory): HttpClient = factory.create(context)` — тонкий DI. Плюсы: конфиг в data, DI чистый. Минусы: переезд.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). **Решение — чисто** (архитектурный стиль, не приоритет; когда к месту). Связано с отменённой `### Миграция BCS на Ktor` (удалена из ROADMAP).

### Н37. Конвенции DI-файлов

**Статус:** частично исправлено (H21b, PR #57, `dev`, 2026-10-27). Два `@Module` в
`RepositoryModule.kt` → один (`UseCaseModule`): `RepositoryModule`
object удалён после переноса его `@Provides` в `RepositoryBindingModule`
(H21b). Осталось: `@Provides` для stateless-классов
(`CalculateTradeDetailsUseCase`, `PrepareOrderRequestUseCase`) без
`@Inject constructor()` — отдельной задачей (ROADMAP → «RepositoryModule
→ UseCaseModule.kt», не заведена).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `RepositoryModule.kt` содержит **два** `@Module` (`RepositoryModule` + `UseCaseModule`). По конвенции — отдельные файлы. `@Provides` для stateless-классов без зависимостей (`CalculateTradeDetailsUseCase`, `PrepareOrderRequestUseCase`, `FinamBrokerApi`, `BcsBrokerApi`) — при наличии `@Inject constructor()` на самом классе `@Provides` не нужен (Hilt сам создаст).

**Нарушенное правило Clean:** конвенция DI-файлов (не Clean-инвариант).

**Clean-альтернатива (чисто):** `UseCaseModule.kt` отдельным файлом; `@Inject constructor()` на stateless-классах вместо `@Provides`. Плюсы: конвенция, меньше boilerplate. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Мелочь конвенции, фиксится при следующем касании `RepositoryModule`/`DataModule`.

### Исправленные (L7)

[пусто]

---

### ✅ Н29. BrokerManager — domain-сервис в di/

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

## L8 — app

**Краткая таблица:**

| ID | Серьёзность | Затрагиваемые уровни | Локализация | live тест |
|---|---|---|---|---|
| Н48. TScalpApplication → data.repository.SettingsRepository | низко | L8, L6 | `TScalpApplication.kt` | - |
| Н49. TScalpApplication → util.TInvestErrorMessages + util.AppLogger | средне | L8, L2 | `TScalpApplication.kt` (`installGrpcExceptionGuard`) | - |
| Н50. restoreTInvest содержит domain-логику выбора счёта | средне | L8 | `TScalpApplication.kt` | - |
| Н51. refreshInitializationState — 2 callsites в app | средне | L8, L7 | `TScalpApplication.kt` | ✓ |
| Н52. Finam → Unit в restoreBrokerConnection | низко | L8 | `TScalpApplication.kt` | - |

**MainActivity.kt** — чисто (20 строк, `enableEdgeToEdge` + `TScalpTheme { MainScreen() }`, никаких нарушений).

### Н48. TScalpApplication → data.repository.SettingsRepository

**Статус:** принято 2c.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `TScalpApplication` инжектит `data.repository.SettingsRepository` напрямую. Composition root видит data — норм. Но инжектит конкретный класс, не интерфейс — после H21b (`domain.api.repository.SettingsRepository`) мигрирует.

**Нарушенное правило Clean:** dependency rule — но для composition root это допустимо (2c).

**Clean-альтернатива:** `domain.api.repository.SettingsRepository` (H21b). Плюсы: app → domain. Минусы: ждёт H21b.

**Выгоды (по 2c):** composition root по определению видит все слои; `TScalpApplication` — единственное место, где это необходимо; миграция — после H21b.

**Ссылка на задачу:** `H21b` (ROADMAP).

### Н49. TScalpApplication → util.TInvestErrorMessages + util.AppLogger

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `installGrpcExceptionGuard` использует `util.TInvestErrorMessages.messages[description]` (T-Invest-специфичный маппинг кодов) + `util.AppLogger` (платформа). App видит util.

**Нарушенное правило Clean:** dependency rule — `TInvestErrorMessages` — data-knowledge (T-Invest-специфика), не должен быть в `util` и не должен быть доступен в app напрямую.

**Clean-альтернатива:** `TInvestErrorMessages` → `data/error/TInvestErrorMapper` (часть H20). `AppLogger` → `Logger`-интерфейс (H19). Плюсы: app не видит T-Invest-специфику. Минусы: зависит от H19/H20.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). **Связано с H19, H20**.

### Н50. restoreTInvest содержит domain-логику выбора счёта

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `restoreTInvest` + `resolveTInvestAccountId` + `tryOpenSandboxAccount` — логика выбора счёта при старте (savedAccountId → accounts → sandbox-fallback). Это domain-логика (правила выбора счёта), в `TScalpApplication`.

**Нарушенное правило Clean:** business logic (выбор счёта) в composition root.

**Clean-альтернатива:** `BrokerLifecycleManager` (domain-сервис) с методами `restoreTInvest(broker)`, `resolveAccount(...)`. `TScalpApplication` делегирует. Плюсы: domain-логика в domain. Минусы: новый сервис (~2 ч).

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). **Связано с ISSUES «Асимметрия lifecycle»**.

### Н51. refreshInitializationState — 2 callsites в app

**Статус:** открыто.

**Серьёзность:** средне (в рамках H22).

**Live тест:** ✓

**Описание:** `restoreTInvest` и `restoreBcs` вызывают `brokerManager.refreshInitializationState()`. Side-effect после `lifecycle.initialize`. Всего 6 callsites (`TScalpApplication` 2 + `SettingsViewModel` 4).

**Нарушенное правило Clean:** не строго Clean — side-effect в app после lifecycle-вызова.

**Clean-альтернатива:** обсуждается в H22 (internal / interface / auto через `BrokerLifecycleApi.initialize`).

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). **Обогащает открытый вопрос H22**.

### Н52. Finam → Unit в restoreBrokerConnection

**Статус:** принято 2c.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `restoreBrokerConnection` — `BrokerName.FINAM → Unit` (Finam не восстанавливается из app, только через UI). Осознанное решение — Finam сейчас заглушка.

**Нарушенное правило Clean:** не строго Clean — константа «Finam не восстанавливается» в app.

**Clean-альтернатива:** `BrokerLifecycleApi.supportsRestore: Boolean` в роли. Или отдельный `restoreBrokerConnection` per-broker без `when`. Плюсы: расширяемо. Минусы: доп. метод в роли.

**Выгоды (по 2c):** KISS — Finam — заглушка, при её реализации пересмотреть.

**Ссылка на задачу:** — (не заведена). **Связано с `### Реализация Finam Broker API`**.

### Исправленные (L8)

[пусто]

---

## L9 — transport-ktor (отдельный модуль)

**Краткая таблица:**

| ID | Серьёзность | Затрагиваемые уровни | Локализация | live тест |
|---|---|---|---|---|
| Н88. RestGetPortfolioRequest — без @Serializable | высоко | L9 | `TInvestRestTransport.kt` | - |
| Н87. TInvestPositionsWebSocket — cold flow, риск дублирования соединений | средне | L9 | `TInvestPositionsWebSocket.kt` | ✓ |
| Н89. TInvestRestTransport — DTO в одном файле | низко | L9 | `TInvestRestTransport.kt` | - |
| Н90. RestDto — избыточные @SerialName | низко | L9 | `TInvestRestTransport.kt` | - |
| Н91. createTransportHttpClient — dead code | низко | L9 | `HttpClientFactory.kt`, `HttpClientFactory.android.kt` | - |
| Н92. Общие DTO между WS-каналами — в PositionsStreamDto.kt | низко | L9 | `dto/PositionsStreamDto.kt` | - |
| Н93. PositionsStreamDto — устаревшие комментарии-гипотезы | низко | L9 | `dto/PositionsStreamDto.kt` | - |

### Н88. RestGetPortfolioRequest — без @Serializable

**Статус:** открыто.

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `TInvestRestTransport.kt`: `data class RestGetPortfolioRequest(@SerialName("accountId") val accountId: String)` — единственный DTO из 14 **без** `@Serializable`. Используется в `getBalance(accountId, isSandbox = true)` через `setBody(request)`. При попытке сериализации kotlinx.serialization выбросит `SerializationException` в runtime (сейчас не проявляется, если sandbox-ветка не вызывается).

**Нарушенное правило Clean:** не строго Clean — консистентность DTO + runtime failure.

**Clean-альтернатива:** добавить `@Serializable` к `RestGetPortfolioRequest`. Плюсы: единообразие, работает. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Связано с закрытой `### Ktor REST для позиций/заявок/баланса` — баг в реализации.

### Н87. TInvestPositionsWebSocket — cold flow, риск дублирования соединений

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** ✓

**Описание:** `TInvestPositionsWebSocket.subscribePositions(accountId)` возвращает `flow { ... }` (cold). Каждый подписчик → **новое** WS-соединение. В отличие от `TInvestPricesWebSocket.prices` (hot SharedFlow, один upstream на любое число коллекторов) и `TInvestOrderStateWebSocket.states` (hot SharedFlow). Сейчас один подписчик (через оркестратор), но архитектурный риск: второй подписчик = второе WS к T-Invest (rate-limit, утечка).

**Нарушенное правило Clean:** не строго Clean — консистентность архитектурных решений между тремя WS-клиентами.

**Clean-альтернатива:** hot SharedFlow как в `TInvestPricesWebSocket` (единый upstream) или KDoc с явным предупреждением «один подписчик». Плюсы: консистентность, защита от дублирования. Минусы: переделка модели.

**Выгоды:** cold flow проще; `withInitialPositions = true` не требует дополнительной подписки.

**Ссылка на задачу:** — (не заведена). Связано с закрытой `### Add stop() to TInvestOrderStateWebSocket` (прецедент hot vs cold).

### Н89. TInvestRestTransport — DTO в одном файле

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `TInvestRestTransport.kt` — 220 строк, содержит сам транспорт + 14 DTO. Отдельный файл `dto/TInvestRestDto.kt` был бы чище и совпадал с другими DTO (`dto/PositionsStreamDto.kt`, `dto/MarketDataStreamDto.kt`, `dto/OrderStateStreamDto.kt`).

**Нарушенное правило Clean:** не строго Clean — организация файлов.

**Clean-альтернатива:** `dto/TInvestRestDto.kt`. Плюсы: консистентность. Минусы: `git mv` + правка импортов в `TInvestRestService.kt` и `RestMappers.kt`.

**Выгоды:** —.

**Анализ L9-part2-Н89:** подтверждается — контраст наглядный: в `transport-ktor/dto/` DTO вынесены правильно (`PositionsStreamDto.kt`, `MarketDataStreamDto.kt`, `OrderStateStreamDto.kt`), а в `app/data/api/TInvestRestTransport.kt` — 14 DTO в одном файле с транспортом.

**Ссылка на задачу:** — (не заведена). Связано с Н53 (`data/api/` структура).

### Н90. RestDto — избыточные @SerialName

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `TInvestRestTransport.kt`: большинство `@SerialName("...")` совпадает с именем поля (`@SerialName("orderId") val orderId`, `@SerialName("lotsRequested") val lotsRequested`). Это шум. Исключение — поля, где реально нужно маппинг (нужно свериться с API).

**Нарушенное правило Clean:** не строго Clean — шум.

**Clean-альтернатива:** удалить избыточные `@SerialName`. Плюсы: чисто. Минусы: нужно свериться с API, чтобы не сломать реальный маппинг.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена).

### Н91. createTransportHttpClient — dead code

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `expect fun createTransportHttpClient()` в `commonMain` + `actual` в `androidMain` (OkHttp engine). Grep 2026-10-27: **0 вызовов** в `app/src/` и `transport-ktor/src/`. `app/src/main/kotlin/.../di/DataModule.kt` создаёт `HttpClient` вручную (`provideTInvestWsHttpClient`, CIO engine). `createTransportHttpClient` никогда не вызывается.

**Нарушенное правило Clean:** не строго Clean — dead code.

**Clean-альтернатива:** удалить `HttpClientFactory.kt` + `HttpClientFactory.android.kt`; **или** перевести `provideTInvestWsHttpClient` на `createTransportHttpClient()` (тогда KMP-фабрика используется по назначению, а DI-конфиг сокращается). Плюсы: без dead code, единая фабрика. Минусы: правка `DataModule`.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Связано с Н36 (транспортная конфигурация в di).

---

### Н92. Общие DTO между WS-каналами — в PositionsStreamDto.kt

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** Общие DTO живут в `PositionsStreamDto.kt`, но используются в нескольких WS-каналах:
- `QuotationDto` — `PositionsStreamDto.kt` и `MarketDataStreamDto.kt`;
- `PingDelaySettingsDto` — `PositionsStreamDto.kt` и `MarketDataStreamDto.kt`;
- `PingDto` — `PositionsStreamDto.kt`, `MarketDataStreamDto.kt`, `OrderStateStreamDto.kt`;
- `MoneyValueDto` — `PositionsStreamDto.kt` и `OrderStateStreamDto.kt`.

Кросс-зависимость между файлами каналов.

**Нарушенное правило Clean:** не строго Clean — организация DTO.

**Clean-альтернатива:** вынести общие DTO в `dto/CommonDto.kt`. Плюсы: изоляция каналов, отсутствие кросс-импортов. Минусы: правка импортов в 3 файлах каналов + мапперах.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Связано с Н53 (`data/api/` структура).

### Н93. PositionsStreamDto — устаревшие комментарии-гипотезы

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `PositionsStreamDto.kt` содержит два комментария, устаревших после live-теста 2026-10-19:
1. «ВНИМАНИЕ: точная структура конверта подписки (есть ли внешний ключ "positionsStream" или поля идут плоско) в спецификации не приведена. Гипотеза — обёртка с именем канала или метода. Проверить при первой реализации отправки.»
2. «Гипотеза: protobuf JSON mapping передаёт int64 как string. Если окажется long — поправить на Long?.»

Оба вопроса решены: отправка работает end-to-end (ISSUES: «T-Invest WebSocket: решён — канал должен быть в URL path»), `balance` — строка (`String?`). Комментарии вводят в заблуждение.

**Нарушенное правило Clean:** не строго Clean — актуальность документации.

**Clean-альтернатива:** удалить оба комментария. Плюсы: KDoc соответствует реальности. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Связано с `docs/ISSUES.md` → «T-Invest WebSocket: решён...».

---

## Связанные документы

- `AGENTS.md` → 2c, 2d
- `docs/ROADMAP.md` — задачи реализации
- `docs/architecture.md` — когда появится (раздел «Известные долги»)

## История

- 2026-10-26 — создан. Заполнены L1 (7), L2 (4), L3 (7). L4–L8 — заглушки.
- 2026-10-27 — L1-L8 полная инвентаризация; L9 (transport-ktor): Н87-Н91 (part 1), Н92+ — по мере части 2.

## Сводный отчёт, 2026-10-27

**Зафиксировано в `docs/clean-audit.md`:** 74 находки, ~1500 строк. Проверены все 9 уровней (L1–L9), 3 документа-источника (ROADMAP, ci-roadmap, ISSUES) прошли ревизию.

### Метрики

| Метрика | Значение |
|---|---|
| Всего находок | **74** |
| Открыто | ~70 |
| Принято по 2c | ~4–5 (Н25, Н48, Н52, Н81) |
| Исправлено | 0 |
| Новых в этой сессии | 56 (L4–L9) |
| Файлов кода покрыто | ~55 (app + transport-ktor) |

### Распределение по уровням

| Уровень | Находок | Ключевые |
|---|---|---|
| L1 presentation | 7 | Н21 (VM → data.*), Н22 (VM → `di.BrokerManager`) — критично |
| L2 util | 4 | Н19 (AppLogger/android.util.Log), Н20 (io.grpc в util) — высоко |
| L3 domain/models | 7 | Н5 (`PortfolioPosition`, принято 2c) |
| L4 domain/api | 11 | Н25 (fat `BrokerApi`), Н70 (`@Suppress`) |
| L5 domain/managers | 6 | Н38 (`DebugConfig` в `PriceFlowManager`), Н40 (`di.BrokerManager` × 3) |
| L6 data (repository) | 4 | Н44 (`di.BrokerManager` × 3), Н45 (4 fat-метода) |
| L6 data (api) | 24 | Н53 (структура), Н63 (god-класс 622), Н71 (refreshToken в логах — security) |
| L7 di | 4 | Н29 (`BrokerManager` — domain-сервис в `di/`) |
| L8 app | 5 | Н50 (domain-логика в app), Н51 (`refreshInitializationState` × 2) |
| L9 transport-ktor | 7 | Н88 (missing `@Serializable`), Н87 (cold flow) |

### Severity

- **Критично** ~10: Н21, Н22, Н25, Н29, Н38, Н40, Н44, Н53, Н63, Н71 (security).
- **Высоко** ~15: Н1, Н19, Н20, Н39, Н45, Н62, Н66, Н67, Н72, Н73, Н76, Н77, Н78, Н88, +1.
- **Средне** ~25.
- **Низко** ~24.

### Baseline: до / после

- **До сессии** (initial clean-audit 2026-10-26): 18 находок (L1–L3).
- **После** (2026-10-27): 74 находки (L1–L9).

**Что это даёт:**

- **Полная инвентаризация** — ни одна находка не потеряется, даже если задачи в ROADMAP отменятся (правило 2c, коммит `c9468c3`).
- **Дыры в инвентаризации закрыты** — backfill Н58–Н70 восстановил 13 пропусков.
- **Cross-level связи** — `**Анализ Lx-Нy:**` показывает, что `DebugConfig` протекает в 2 слоя (L1 + L5), `di.BrokerManager` — в 3 (L1 + L5 + L6), `toAppError` — в 3 (L2 + L5 + data).

### Что важно из находок (не очевидное)

1. **`di.BrokerManager` — центральная проблема.** 10 потребителей: 3 VM + 3 менеджера + 3 репозитория + 1 app. Тянет за собой Н22, Н29, Н40, Н44. Решение — `BrokerRegistry` (Н22) + `refreshInitializationState` как открытый вопрос (internal / interface / auto).
2. **Н71 (security) — `refreshToken` BcsBrokerApi логируется.** Уже в ISSUES приоритетно. Ждёт `bugfix/security-token-logging`.
3. **`BrokerApi` fat (Н25)** — не «осознанно», как KDoc утверждает, а **переходное состояние**. BCS подтверждает: `postOrder` работает, но `orders = null` делает его недоступным; `subscribePositions` → `emptyFlow()`. Расщеп `OrdersApi` на `Query/Command` — конкретное решение.
4. **`data/api` структурно критичен** (Н53). 17 файлов в одном пакете. Нужна раскладка `common/`, `tinvest/{services,mappers}`, `bcs/`, `finam/`.
5. **`util` — grab-bag** (Н17–Н20): presentation (`TradePhrases`), платформа (`AppLogger`), transport (`AppResultExtensions`), формат (`CurrencyUtils`). Используется в 3 слоях. Разъезд по слоям — большая задача.

### Рекомендация по приоритету исправления

План в ROADMAP: **H3 → Разбивка InvestRepository → H21b → H23 → util → H22 → H1/Н4/H24 → H5**.

Clean-audit подтверждает этот порядок и уточняет:

- **H3** (тривиально, 10 мин).
- **Разбивка InvestRepository** — разблокирует `domain.api.repository.*` для H21b (Н21, Н45).
- **H21b** — закрывает Н21 (часть) и Н44 (для `SearchCache`, `InstrumentRepository`).
- **H23** (`DebugFlagsRepository`) — закрывает Н23 (L1) **и** Н38 (L5, `PriceFlowManager` → `DebugConfig`). **Одна задача, два уровня.**
- **util-блок (Н17–Н20)** — закрывает `AppLogger` (Н19 + Н49), `AppResultExtensions` (Н20 + Н39), `TradePhrases` (Н17), `CurrencyUtils` (Н18 + Н55). **Одна umbrella, 4+ уточнения.**
- **H22** (`BrokerRegistry`) — закрывает Н22 (VM), Н29 (L7), Н40 (L5), Н44 (L6). **Одна задача — 4 уровня.**
- **H1/Н4/H24** — presentation-мелочи.
- **H5** (`PortfolioPosition`) — концептуальное разделение, последнее.

**Из L6-part2 (вне ROADMAP-плана), но приоритетно:**

- **Н71** (security) — отдельный bugfix. `bugfix/security-token-logging`.
- **Н76** (KDoc ссылается на несуществующую `### Реализация BCS Broker API`) — пробел в планировании.
- **Н62** (`TInvestBrokerAPI.initialize` не закрывает WS) — в ISSUES.
- **Н88** (missing `@Serializable`) — 5 минут.
- **Н53** (структура `data/api/`) — большая, но критичная.
