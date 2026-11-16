<!-- SECTION_BEGIN: clean-audit-L2-ARCHIVE -->
## L2 — util (архив)










<!-- SUBSECTION_BEGIN: clean-audit-33 -->
___
### Исправленные (L2)
<!-- SUBSECTION_END: clean-audit-33 -->









<!-- SUBSECTION_BEGIN: Н20 -->
___
### Н20 ✅ Н20. AppResultExtensions — io.grpc в util

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

<!-- SUBSECTION_END: Н20 -->

<!-- SUBSECTION_BEGIN: Н17 -->
___
### Н17 ✅ Н17. TradePhrases — Compose в util

**Статус:** исправлено (2026-10-29, PR #64 — unify presentation).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `util/TradePhrases.kt` импортирует `androidx.compose.ui.graphics.Color`, содержит UI-тексты (`directionText`, `orderTypeDescription`, `statusLabel`, `confirmOrderText`), цвета (`orderTypeColor`), маппинг строк в `OrderTypeSelection` (`stringToOrderType`).

**Нарушенное правило:** Dependency rule — presentation-код в shared-слое.

**Clean-альтернатива:** `presentation/util/TradePhrases.kt` (Compose + UI-тексты) + `presentation/theme/TradeColors.kt` (цвета). Маппинг `stringToOrderType` — в data-маппер (см. Н4). Плюсы: `util` становится Compose-free, KMP-совместимым. Минусы: переезд ~7 методов + правка импортов в 4 файлах presentation (~30 мин).

**Выгоды:** SSOT для текстов и цветов в одном файле. Но это presentation-выгода.

**Что сделано:** `util/TradePhrases.kt` → `presentation/components/TradePhrases.kt` (решение пользователя: только потребитель — `OrderCard`, лежит рядом). `stringToOrderType` удалён в Split order (`ec9c7d5`).

**Ссылка на задачу:** — (не заведена).

<!-- SUBSECTION_END: Н17 -->

<!-- SUBSECTION_BEGIN: Н18 -->
___
### Н18 ✅ Н18. CurrencyUtils — presentation-формат в util

**Статус:** исправлено (2026-10-29, PR #64 — unify presentation).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `util/CurrencyUtils.kt` содержит `formatCurrency` (`NumberFormat.getCurrencyInstance(ru-RU)` + `Currency.getInstance("RUB")`) и `formatPrice` («%.2f пт» для futures). Presentation-форматирование (локаль, «₽», «пт»).

**Нарушенное правило:** Presentation-форматирование принадлежит presentation, не shared-слою.

**Clean-альтернатива:** `presentation/util/Format.kt`. Плюсы: presentation-зависимости (Locale, Currency) в presentation. Минусы: переезд + правка 2 импортов.

**Выгоды:** `formatCurrency` — общая утилита, без Compose, без платформы (только `java.text`/`java.util`). Технически допустима в `util`, но presentation-выбор локали и «пт» — presentation-специфика.

**Что сделано:** `util/CurrencyUtils.kt` → `presentation/util/CurrencyFormat.kt` с параметром `currency` (без привязки к RUB). Domain/data получают числа через `TradeDetails`/`TradeCheckResult`, форматирование — в presentation.

**Ссылка на задачу:** — (не заведена).

<!-- SUBSECTION_END: Н18 -->

<!-- SUBSECTION_BEGIN: Н19 -->
___
### Н19 ✅ Н19. AppLogger — android.util.Log в util

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

<!-- SUBSECTION_END: Н19 -->

<!-- SECTION_END: clean-audit-L2-ARCHIVE -->
