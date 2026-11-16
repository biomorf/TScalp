<!-- SECTION_BEGIN: clean-audit-L3-ARCHIVE -->
## L3 — domain/models (архив)










<!-- SUBSECTION_BEGIN: clean-audit-38 -->
___
### Исправленные (L3)
<!-- SUBSECTION_END: clean-audit-38 -->









<!-- SUBSECTION_BEGIN: Н15 -->
___
### Н15 ✅ Н15. TradingStateSnapshot — presentation в domain

**Статус:** исправлено (2026-11-13, PR #145, merge `f9dc5596`).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `domain/models/TradingState.kt` содержал `TradingStateSnapshot` с полями `quantity: String`, `pairedMultiplier: String`, `orderType: OrderTypeSelection`, `pairTradingEnabled: Boolean`. `quantity`/`pairedMultiplier` — сырые значения TextField, невалидированные. `orderType` — domain-модель (`sealed interface` в `OrderModels.kt`, не presentation); `pairTradingEnabled` — флаг состояния сессии, не тумблер. Реальное нарушение — 2 сырые строки. Диагностика расширила: `String` протекал через 5 domain-моделей и 3 usecase'а.

**Нарушенное правило:** domain не доверяет своему входу — парсит сырые `String` трижды на разных уровнях. SSoT нарушен.

**Clean-альтернатива:** парсинг один раз на границе слоёв.

**Что сделано:** `quantity: Long = 1L`, `pairedMultiplier: Long = 10L` в `TradingStateSnapshot` и `RestoredFormState`; `PairedOrderInput.multiplier: Long?`; `PlaceOrderRequest.pairedMultiplier: Long`; `CalculateTradeDetailsUseCase` и `PrepareOrderRequestUseCase` принимают `Long?`; `TradingStateRepositoryImpl` encode/decode + fail-fast; presentation — `OrderFormState.pairedQuantity` (computed), `OrderFormStateMapper` (парсинг на границе), `DEFAULT_PAIRED_MULTIPLIER = 10L`; M1 — `ConfirmOrderDialog` больше не парсит (принимает `pairedQuantity: Long?`); G2 — `OrdersQuantityRow` принимает `quantityAsLong: Long`. `"abc"`-тест удалён. PR #145 (merge `f9dc5596`).

**Ссылка на задачу:** PR #145 (merge `f9dc5596`).

**Связано:** `docs/clean-audit.md` → Н96 (D, исправлено в том же PR); `docs/roadmap/ROADMAP-ARCHIVE.md` → `### ✅ Domain: pairedMultiplier/quantity → Long`.

<!-- SUBSECTION_END: Н15 -->

<!-- SUBSECTION_BEGIN: Н96 -->
___
### Н96 ✅ Н96. DataStore-специфика в domain-модели (orderTypeFromStorageKey/toStorageKey)

**Статус:** исправлено (2026-11-13, PR #145, merge `f9dc5596`).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `domain/models/OrderModels.kt` содержал `fun OrderTypeSelection.toStorageKey(): String` и `fun orderTypeFromStorageKey(key: String): OrderTypeSelection` — строковые ключи DataStore (persistence-специфика) как top-level extension в domain-модели. Единственный потребитель — `TradingStateRepositoryImpl` (data). Замечено при диагностике задачи «pairedMultiplier/quantity → Long» (находка D), не в scope исходного текста.

**Нарушенное правило:** persistence-специфика в domain-модели.

**Clean-альтернатива:** private helper в data-слое (`TradingStateRepositoryImpl`).

**Что сделано:** обе функции перенесены в `private companion object` `TradingStateRepositoryImpl` как private. `orderTypeFromStorageKey` теперь fail-fast на неизвестном ключе (`invalidValue("order_type", key)` → `IllegalStateException`), а не fallback `Market`. Roundtrip всех 5 значений `OrderTypeSelection` покрыт в `TradingStateRepositoryImplTest`. PR #145 (merge `f9dc5596`).

**Ссылка на задачу:** PR #145 (merge `f9dc5596`).

**Связано:** `docs/clean-audit.md` → Н15 (исправлено в том же PR).

<!-- SUBSECTION_END: Н96 -->

<!-- SUBSECTION_BEGIN: Н13 -->
___
### Н13 ✅ Н13. KDoc StreamState — устаревший пакет

**Статус:** исправлено (2026-11-11, commit `2f476dc6`).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `domain/models/StreamState.kt` — KDoc ссылается на `[com.gitlab.biomorf.tscalp.domain.api.StreamStateTracker]`. После Move `StreamStateTracker` живёт в `com.gitlab.biomorf.tscalp.domain.api.managers.StreamStateTracker` (package `domain.api.managers`). Ссылка битая (в KDoc, не в коде).

**Нарушенное правило:** Не Clean — актуальность документации.

**Clean-альтернатива:** поправить ссылку в KDoc. Плюсы: актуально. Минусы: — (~2 мин).

**Выгоды:** —

**Что сделано:** KDoc `StreamState.kt` обновлён — ссылка `[com.gitlab.biomorf.tscalp.domain.api.StreamStateTracker]` → `[com.gitlab.biomorf.tscalp.domain.api.managers.StreamStateTracker]` (commit `2f476dc6`). Одна строка, без изменений кода.

**Ссылка на задачу:** commit `2f476dc6` (2026-11-11). Правка в рамках сессии по очереди clean-audit.

<!-- SUBSECTION_END: Н13 -->

<!-- SUBSECTION_BEGIN: Н5 -->
___
### Н5 ✅ Н5. PortfolioPosition — presentation-поля

**Статус:** исправлено (merge `d04ecc4`, PR #90; коммит `4a0e785`).

**Серьёзность:** высоко.

**Live тест:** -.

**Описание:** `PortfolioPosition` (presentation-модель, `domain/models/Portfolio.kt`) содержит presentation-поля `totalValue`, `profitPercent`, `priceChangePercent`. По Clean domain-модель не должна содержать presentation-атрибуты. **Исправлено (feature/portfolio-model):** создана domain-модель `Position` (sealed interface, 7 subtypes) с computed getters `totalValue` / `profitPercent` (не хранятся); `toPortfolioPosition` и `PortfolioPosition.mergeWith` удалены (коммит `a859185`). **Остаток:** `PortfolioPosition` физически лежит в `domain/models/` и возвращается из domain-интерфейса `PortfolioRepository.fetchPositionsResult → AppResult<List<PortfolioPosition>>` (dependency rule наружу); presentation-модель должна исчезнуть, UI должен работать с `Position` напрямую. История: merge `d04ecc4`, PR #90.

**Нарушенное правило:** Domain не должен содержать presentation-атрибуты (dependency rule в обратную сторону).

**Clean-альтернатива:** `Position` (domain) — создана; полностью избавиться от `PortfolioPosition`. `PortfolioRepository.fetchPositionsResult → AppResult<List<Position>>`; `PortfolioUiState.positions` — удалить (UI читает `sheet.accounts`); `AssetPositionCard(position: Position, displayCurrentPrice: Double?, priceChangePercent: Double?)`; `PositionFormatter` на чистых функциях (без модели); `emptyPosition(...)` вместо `emptyPortfolioPosition`; `PortfolioPositionUi` отдельно **не создавать** (лишняя сущность). Плюсы: dependency rule чистая, presentation-модели нет вовсе. Минусы: ~5.5–6 ч, переписать `PositionFormatter` + `PositionFormatterTest` (22 теста). История: merge `d04ecc4`, PR #90.

**Выгоды (по 2c):** `toPortfolioPosition()` — одна точка конвертации в `InvestRepository`; UI работает с одной моделью, без дублирования `profitPercent`/`totalValue` в presentation. Экономия на маппинге ~1 ч.

**Что сделано:** `PortfolioPosition`, `calculateProfitPercent`, `emptyPortfolioPosition`, `toLegacyPortfolioPosition` удалены из `domain/models/Portfolio.kt`. `PortfolioRepository.fetchPositionsResult → AppResult<List<Position>>`. `emptyPosition(instrument, currentPrice, accountId, brokerName, lockedQuantity)` — dispatcher по `Instrument` (7 subtypes). `PortfolioUiState.positions` удалён; `PortfolioViewModel` на `sheet` + `prices`/`priceChangePercents` overlay; `sheetState`/`pricesMap`/`priceChangePercentsMap` удалены. `OrdersUiState.portfolioPositions → List<Position>`; `toLegacyPortfolioPosition` adapter убран. `AssetPositionCard(position: Position, displayCurrentPrice: Double?, priceChangePercent: Double?)`. `PositionFormatter` — чистые функции с примитивами. `PositionFormatterTest` переписан (26 тестов); `PortfolioConvertersTest` +7 кейсов `emptyPosition`; `CalculateProfitPercentTest` удалён. Коммит `4a0e785`, merge `d04ecc4`, PR #90.

**Ссылка на задачу:** `### ✅ H5. PortfolioPosition removed` (`docs/roadmap/ROADMAP-ARCHIVE.md`, merge `d04ecc4`, PR #90). Связано с закрытой `### Рефакторинг расчёта P&L для PortfolioPosition` (PR #81, `dev`).

**Сводный отчёт, 2026-10-31:** да (включена в отчёт от 2026-10-31).

<!-- SUBSECTION_END: Н5 -->

<!-- SUBSECTION_BEGIN: Н7 -->
___
### Н7 ✅ Н7. ttech_figi — временное, без задачи

**Статус:** исправлено (2026-11-02, C-min шаг 1, коммит `241b8cb`).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `InstrumentUi.ttech_figi` — KDoc говорил «**Временно** — нужен для подписки на `LastPrice` через gRPC-стрим (там figi, не uid). Уйдёт, когда стрим перейдёт на `instrumentUid`». Задачи в `docs/ROADMAP.md` / `docs/ISSUES.md` на удаление `ttech_figi` не было.

**Нарушенное правило:** 2d — временное решение без задачи на полное.

**Clean-альтернатива:** задача в ROADMAP «Перевести gRPC `LastPrice` на `instrumentUid`; удалить `ttech_figi`». Плюсы: 2d соблюдён. Минусы: — (это не альтернатива, а фиксация).

**Выгоды:** работает сейчас; поле нужно для стрима.

**Что сделано:** `ttech_figi: String` → `figi: String? = null` в `Instrument`, `Future`, `Share`; `ttech_uid` слит с `tscalpInstrumentId` (C-min шаги 1-2, коммит `241b8cb`). `figi` больше не T-Invest-специфичен (BCS позиции тоже дают figi) — common optional. 2d-фиксация: поле не «временное», а нормальное nullable.

**Ссылка на задачу:** `### ✅ Instrument: C-min (Phase 1)` (ROADMAP).

<!-- SUBSECTION_END: Н7 -->

<!-- SUBSECTION_BEGIN: Н8 -->
___
### Н8 ✅ Н8. OrderTypeSelection — sealed class для sum-type

**Статус:** исправлено (PR #72, `dev`, 2026-10-30).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `domain/models/OrderModels.kt` — `OrderTypeSelection` объявлен как `sealed class` с 5 `object`-ветками. Хранимого состояния нет, но есть два computed default-члена: `stopOrderType: StopOrderType?` (через `when (this)` с `else -> null`) и `isRegular: Boolean`. Формально sum-type «без shared state» верен, но запись это маскировала: простая замена на `sealed interface` требует решить, куда деть computed-члены. По 2a (актуальность): Kotlin 1.5+ рекомендует `sealed interface` для sum-types без shared state. В том же файле для `Order` уже выбрано `sealed interface` (`### Split order domain into sealed Regular/Stop`).

**Нарушенное правило:** Не Clean — стилистическая последовательность (2a).

**Clean-альтернатива:** `sealed interface OrderTypeSelection` с двумя default-членами (`stopOrderType`, `isRegular`) внутри интерфейса. `when (this)` в `stopOrderType` — все 5 веток явно, без `else` (exhaustive на новый подтип). Плюсы: консистентность с `Order`, 2a, усиление exhaustive-check. Минусы: нет (callsites `stopOrderType` / `isRegular` не меняются).

**Выгоды:** — (нет существенных).

**Что сделано:** `sealed class` → `sealed interface`; 5 `object`-подтипов без `()`; `stopOrderType` и `isRegular` — default-члены интерфейса; `when (this)` в `stopOrderType` — все 5 веток явно (`Market, Limit -> null`, без `else`), exhaustive на новый подтип.

**Ссылка на задачу:** PR #72 (`feature/sealed-interface-sumtypes`), коммит `3d491db`.

<!-- SUBSECTION_END: Н8 -->

<!-- SUBSECTION_BEGIN: Н16 -->
___
### Н16 ✅ Н16. TradeCheckResult — sealed class для sum-type

**Статус:** исправлено (PR #72, `dev`, 2026-10-30).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `domain/models/TradingStatus.kt` — `TradeCheckResult` объявлен как `sealed class` (`data object Success` + `data class Error(message: String)`). Тот же класс проблемы, что Н8.

**Нарушенное правило:** Не Clean — стилистическая последовательность (2a).

**Clean-альтернатива:** `sealed interface TradeCheckResult`. Плюсы: консистентность. Минусы: механическая правка `when`-конструкций (~5 мин).

**Выгоды:** — (нет существенных).

**Что сделано:** `sealed class` → `sealed interface`; 3 подтипа (`data object Success`, `data class Error`, `data class InsufficientFunds`) без `()` после имени; `when` в `OrdersViewModel:475` — `is` по всем трём, без `else` (exhaustive сохраняется).

**Ссылка на задачу:** PR #72 (`feature/sealed-interface-sumtypes`), коммит `1b7a944`.

---

<!-- SUBSECTION_END: Н16 -->

<!-- SUBSECTION_BEGIN: Н6 -->
___
### Н6 ✅ Н6. InstrumentUi — god-класс

**Статус:** исправлено (2026-11-02, Phase 2, коммит `bf34061`).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `domain/models/Instruments.kt` — `InstrumentUi` открытый класс с ~50 полями; `FutureUi` и `ShareUi` дублируют **все** общие поля через `override` + добавляют свои. Неприменимые поля явно `null` (например `ShareUi.initialMarginOnBuy = null`).

**Нарушенное правило:** Формально dependency rule не нарушен (всё в domain). Нарушает Interface Segregation на уровне моделей: `ShareUi` «знает» про `initialMarginOnBuy`.

**Clean-альтернатива:** `sealed interface Instrument` (минимальный контракт: `tscalpInstrumentId`, `ticker`, `name`, `currency`, `lot`) + `data class Share(...)` + `data class Future(...)` со своими полями. `when (instrument) { is Share -> ..., is Future -> ... }` — компилятор exhaustive. Плюсы: ISP, компилятор-проверка. Минусы: переписать `TInvestInstrumentMapper.mapProtoToDomain` (118 строк, `@Suppress` в detekt-baseline), `BcsBrokerApi` маппинг, все места `InstrumentUi` (карточки, поиск, окна, `InstrumentRepository`).

**Выгоды:** один тип — простая передача через UI, `instrumentType: String` уже есть. Работает.

**Уточнение (C-min, 2026-11-02):** `Instrument` / `Future` / `Share` (C-min шаги 12-14, коммит `6f75478`) убрали суффикс `Ui`, но god-класс остаётся: `Instrument` — open class с ~50 полями, `Future` / `Share` дублируют через `override`. Phase 2 (sealed interface) — `### Instrument: Phase 2 (sealed interface)` в ROADMAP (триггер: начало BCS/Finam API). До Phase 2 Н6 остаётся открытой.

**Что сделано:** `open class Instrument` (39 полей) → `sealed interface Instrument` (22: core 4 + common 10 + флаги 8) + 7 `data class` (`Share`, `Future`, `Bond`, `Option`, `Etf`, `Currency`, `Other`). Subtype-specific поля — только в своих data class; `when (instrument)` — exhaustive по 7 подтипам. God-класс устранён. `mapProtoToDomain` split на 7 мапперов (`TInvestInstrumentMapper`, коммит `53810b9`); `toInstrument` split на 2 файла (`TInvestRestMappers.kt` + `TInvestRestInstrumentMappers.kt`, коммит `f9d53b9`, detekt `TooManyFunctions`).

**Ссылка на задачу:** `### ✅ Instrument: Phase 2 (sealed interface)` (ROADMAP).

<!-- SUBSECTION_END: Н6 -->

<!-- SECTION_END: clean-audit-L3-ARCHIVE -->
