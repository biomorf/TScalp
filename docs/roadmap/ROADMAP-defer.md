<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: roadmap-defer -->
## Отложено










<!-- SUBSECTION_BEGIN: t-21 -->
<a id="t-21"></a>
### [DEFER] T-21 KMP: domain → commonMain
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

---
<!-- SUBSECTION_END: t-21 -->










<!-- SUBSECTION_BEGIN: t-22 -->
<a id="t-22"></a>
### [DEFER] T-22 Плавный переход Hilt → Koin
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

---
<!-- SUBSECTION_END: t-22 -->










<!-- SUBSECTION_BEGIN: t-34 -->
<a id="t-34"></a>
### [DEFER] T-34 Расшифровка кодов ошибок брокеров
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

---
<!-- SUBSECTION_END: t-34 -->










<!-- SUBSECTION_BEGIN: t-14 -->
<a id="t-14"></a>
### [DEFER] T-14 UX-flow Variant D: broker + account до поиска
**Мобильная работа:** невозможно (desktop; UI-ревизия диалога свойств слота + 6+ файлов + live-test).

**Статус:** запланировано. **UI — требует санкции.**

**Контекст:** follow-up к `### ✅ TradingStateSnapshot: CardIdentity? на слот` (PR #146, merge `071e7f3c`). Сейчас broker слота = `instrument.brokerName` (устанавливается при выборе инструмента), account выбирается **после** создания карточки (`onAccountSaved` / `onPairedAccountSaved`). Коллизия: «broker из инструмента vs broker слота» — если пользователь сменит broker в свойствах слота после выбора, состояние разъедется (сейчас broker после создания не меняется, но семантически — не выражено).

**Variant D:** broker + account задаются в диалоге свойств слота **до** поиска. Поиск — у выбранного брокера. Выбор инструмента → `CardIdentity(broker, account, tscalpInstrumentId)` создаётся целиком. После создания — можно менять **только счёт** (broker зафиксирован).

**Плюсы D:** SSoT (card атомарна), `onAccountSaved` / `onPairedAccountSaved` исчезают, usecase упрощаются, UX яснее.

**Минусы D:** UI-санкция (AccountSelector в `SearchBrokerDialog`); поиск блокируется без инициализированного брокера; `restoreOrderForm` при неинициализированном брокере — «половинчатое» восстановление; scope ~3.5–4 ч.

**Соответствие Clean:** соответствует.

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**MVVM:** соответствует.

**KMP:** — (не применимо, presentation).

**Предусловия:** PR #146 merged.

**Файлы:** `OrderSearchState`, `OrderSearchViewModel`, `OrdersBrokerDialogs`, `OrderDialogsViewModel`, `OrdersDialogsSection`, `OrderFormViewModel`, `SelectInstrumentUseCase`, `SelectPairedInstrumentUseCase`.

**Шаги:**
1. **UI-санкция** (правило 1).
2. `OrderSearchState` + `accountId` per slot.
3. `OrderDialogsViewModel.saveSlotSettings(slot, broker, account)`.
4. `SearchBrokerDialog` + AccountSelector.
5. `OrderFormViewModel.onInstrumentSelected(instrument, cardIdentity)`.
6. Удалить `onAccountSaved` / `onPairedAccountSaved` + callbacks.
7. `SelectInstrumentUseCase` / `SelectPairedInstrumentUseCase` — упростить.
8. Тесты.
9. Live-test 7 сценариев.

**Тесты:** ~10 новых + правки существующих.

**Референсы:** `docs/architecture.md` → «CardIdentity и слоты»; PR #146; `### ✅ TradingStateSnapshot: CardIdentity? на слот`.

**Ограничения:** UI-санкция; `TradingStateSnapshot` не трогать (PR #146); не смешивать с `### UI: кнопка swap слотов`; broker после создания disabled.

**Критерий готовности:** broker+account до поиска; `CardIdentity` атомарна; `onAccountSaved` удалены; смена счёта работает; смена broker disabled; live-test PASS.

**Оценка:** ~3.5–4 ч.

**Зависимости:** PR #146 merged (yes).

**Связано:** `### ✅ TradingStateSnapshot: CardIdentity? на слот` (PR #146); `### UI: кнопка swap слотов`.

**Открытые вопросы:**
1. Account selector без брокера — disabled или fallback?
2. `restoreOrderForm` при неинициализированном брокере — ок или retry?
3. `OrderSearchState` — плоский или `SlotSearchState(broker, account, query)`?

---
<!-- SUBSECTION_END: t-14 -->










<!-- SUBSECTION_BEGIN: t-15 -->
<a id="t-15"></a>
### [DEFER] T-15 Рефакторинг архитектурного долга (detekt-группа 3)
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








#### Compose-экраны

**Перенесено в umbrella** `### Compose UI: test infra + 3 экрана (3 проходки)` (см. выше по приоритету; подсекции U0–U3). Здесь оставлен только маркер — не дублировать. `#### Compose-компоненты` — закрыта (2026-11-11, `ROADMAP-ARCHIVE.md`); остаток — Н94 (`clean-audit.md`).







#### BrokerApi-реализации

**Архитектурный долг, вынесено в отдельную задачу.**
`### Рефакторинг BrokerApi-реализаций (D)` — см. «Планируемые
задачи». Полный план (TInvest WS/REST split, BCS/Finam
отложены) — там.








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

---
<!-- SUBSECTION_END: t-15 -->










<!-- SUBSECTION_BEGIN: t-16 -->
<a id="t-16"></a>
### [DEFER] T-16 Orders: P&L в песочнице (expectedYield)
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
    - **B (client-side fallback).** При `expectedYield == null` и `averagePrice > 0`: `profit = (currentPrice - avgPrice) * quantity * moneyPerPointOfPrice`. Это пересекается с `### P&L: sandbox client-side fallback для Position` (низкий приоритет в ROADMAP). Возможно, объединить — «sandbox P&L» = частный случай client-side fallback.
    - **C (не чинить).** Признать sandbox-ограничение, документировать в `docs/ISSUES.md` («P&L в песочнице: expectedYield = null, UI показывает 0»). Боевой счёт работает — не блокер.

   **Рекомендация:** **A** + запись в `docs/ISSUES.md` про sandbox-ограничение. B — отдельная задача (`P&L: client-side fallback`).
2. Если A: `SelectedInstrumentInfo.profit: Double?` / `profitPercent: Double?`; `refreshLastSelectedInstruments` — без `?: 0.0`; `OrdersScreen` — `profit?.let { format(...) } ?: "—"`.
3. UI-санкция (правило 1): отображение «—» вместо «0» на карточке заявки — согласовать.
4. Тесты: `refreshLastSelectedInstruments` — unit (если появится mock-инфра). Live: sandbox — «—», prod — корректный P&L.
5. Гейт.

**Тесты:** live-тест sandbox + prod.

**Референсы:** `### P&L: sandbox client-side fallback для Position` (B-вариант, low priority); `### Position: источники priceChangePercent и lockedQuantity` (`GetPositions` — альтернативный источник P&L); `docs/ISSUES.md`.

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

**Связано:** `### P&L: sandbox client-side fallback для Position` (B-вариант, отдельная задача); `### Position: источники priceChangePercent и lockedQuantity`.

**Открытые вопросы:** `expectedYield` в sandbox — `null` или `0`? Определить шагом 0. Если `0` — возможно, UI показывает 0 корректно (нет P&L), но это не отличимо от «реальный ноль»; тогда — отдельный `ISSUES.md` без фикса.

---
<!-- SUBSECTION_END: t-16 -->










<!-- SUBSECTION_BEGIN: t-17 -->
<a id="t-17"></a>
### [DEFER] T-17 Margin/risk → TradeCheckResult
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

---
<!-- SUBSECTION_END: t-17 -->










<!-- SUBSECTION_BEGIN: t-18 -->
<a id="t-18"></a>
### [DEFER] T-18 T-Invest: subtype-specific поля в mapShare / mapFuture / mapBond / mapOption / mapEtf / mapCurrency
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

---
<!-- SUBSECTION_END: t-18 -->










<!-- SUBSECTION_BEGIN: t-19 -->
<a id="t-19"></a>
### [DEFER] T-19 Position: источники priceChangePercent и lockedQuantity
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
- `data/api/tinvest/TInvestPortfolioService.kt` — из `PositionData.dailyYieldRelati

---
<!-- SUBSECTION_END: t-19 -->










<!-- SUBSECTION_BEGIN: t-32 -->
<a id="t-32"></a>
### [DEFER] T-32 Live-test Broker lifecycle API: BCS/Finam
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

---
<!-- SUBSECTION_END: t-32 -->










<!-- SUBSECTION_BEGIN: t-39 -->
<a id="t-39"></a>
### [DEFER] T-39 Search: drop statuses (variant D for T-7)
**Мобильная работа:** полное закрытие (proot + PR, гейт на CI).

**Статус:** отложено.

**Контекст.** Альтернатива D для T-7: полностью убрать `updateStatuses` из `SearchInstrumentsUseCase`. Сейчас statuses обновляются на каждом поиске (2N+1 unary); drop → N+1 unary на поиск, экономия N unary.

**Почему отложено.** Опирается на три условия, ни одного из которых нет:

1. **Periodic status refresh в Coord.** `UpdateTradingStatusesUseCase` вызывается только из `SearchInstrumentsUseCase` (search); Coord-файла для orders нет (`OrdersCoordState.kt` — только data-класс). Если убрать statuses из search, статусы обновятся только при следующем поиске/старте.
2. **T-8 (`getTradingStatuses` batching).** Без батчинга periodic refresh дорогой — N unary каждые T минут на фоне rate-budget.
3. **UX-санкция (правило 1).** Пользователь принимает, что статусы в результатах поиска обновляются periodic (T-8), а не мгновенно.

**Условия для возврата (все три).**
- Coord periodic реализован (интервал уточнить: 1 или 5 мин).
- T-8 (batching) merged.
- UX-санкция на drop получена.

**Файлы (при старте).**
- `domain/usecases/SearchInstrumentsUseCase.kt` — убрать `updateStatuses`.
- Новый `OrdersCoord` (или расширение существующего) — periodic `UpdateTradingStatusesUseCase`.
- `UpdateTradingStatusesUseCase` — переиспользовать.

**Шаги (при старте).**
1. Coord periodic для `UpdateTradingStatusesUseCase`.
2. Удалить `updateStatuses` из `SearchInstrumentsUseCase`.
3. Тест: search → 0 unary; periodic → N unary по T.

**Соответствие Clean:** соответствует (orchestration в Coord/usecase, не VM).
**Нарушенное правило Clean:** —.
**Clean-альтернатива:** —.
**Выгоды:** —.
**MVVM:** соответствует.
**KMP:** соответствует.

**Предусловия:** T-8 merged; Coord periodic реализован; UX-санкция.

**Референсы:** T-7 (variant A), T-8 (batching), T-3 umbrella; live-log 2026-11-16.

**Ограничения.**
- Не делать без UX-санкции.
- Не делать без periodic в Coord (иначе UX-регресс).

**Критерий готовности.**
- Search = N+1 unary (без +N).
- Periodic = N unary по T.
- Тест.
- UX-санкция зафиксирована.

**Оценка:** ~30 мин code + UX-решение.

**Зависимости:** T-8 merged; Coord periodic.

**Связано:** T-7, T-8, T-3.

**Открытые вопросы.**
- Интервал periodic (1 мин / 5 мин).
- Что делать при cache hit + periodic — пропускать?

---
<!-- SUBSECTION_END: t-39 -->










<!-- SUBSECTION_BEGIN: t-23 -->
<a id="t-23"></a>
### [DEFER] T-23 UI: показать executedPrice / executedQuantity на карточке
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

---
<!-- SUBSECTION_END: t-23 -->










<!-- SUBSECTION_BEGIN: t-24 -->
<a id="t-24"></a>
### [DEFER] T-24 UI-индикатор состояния стримов
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

---
<!-- SUBSECTION_END: t-24 -->










<!-- SUBSECTION_BEGIN: t-25 -->
<a id="t-25"></a>
### [DEFER] T-25 UI: разбивка AccountBalance.total на money/blocked в шапке счёта
**Мобильная работа:** частичное (live-тест UI).

**Статус:** запланировано. **UI — требует санкции.**

**Контекст:** в `AccountHeaderCard` (шапка счёта на `PortfolioScreen`) сейчас
показывается `AccountBalance.total` (= `money + blocked`) через
`formatDomesticCurrency(total)` или «нет данных». Пользователь не видит
разбивку: сколько свободно (`money`), сколько заблокировано (`blocked`).
Для валютных счетов с ненулевым `blocked` (например, средства под
заявками) эта информация важна. Изначальная задача «UI: отображение
blocked на валютной карточке» устарела: валютная карточка
`RUB000UTSTOM` удалена в `Portfolio: модель портфеля`, `money`/`blocked`
переехали в `AccountBalance`, а не в `PortfolioPosition`.

**Соответствие Clean:** соответствует (presentation-изменение, dependency rule не затронута).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:**
- `AccountBalance.money` / `.blocked` заполняются в `PositionFlowManager` /
  `PortfolioRepository` (проверить WS + polling + seed-путь;
  `docs/architecture.md` → «Data flow: позиции»).
- Формула связи `money` ↔ `blocked` ↔ `quantity` для валютных счетов —
  не подтверждена (`docs/ISSUES.md` → «T-Invest WebSocket: решён — канал
  должен быть в URL path»). До выяснения показывать `money` и `blocked`
  как **отдельные значения**, не производные.

**Файлы:**
- `presentation/screens/portfolio/AccountHeaderCard.kt` — отображение
  (варианты ниже).
- `presentation/screens/portfolio/PortfolioViewModel.kt` — если нужен
  `AccountBalance` целиком (сейчас в `AccountHeaderCard` только `rubBalance`).

**Варианты отображения (требуют санкции):**

- **A. Дополнительная строка в шапке.** Под `total` — «Свободно: X ₽ ·
  Заблокировано: Y ₽». Появляется только при `blocked != null && blocked > 0`.
  Минимум правок, явно.
- **B. Развёрнутая шапка по тапу.** Обычный вид — `total`; по тапу
  детализация `money` / `blocked`. Требует состояния «раскрыта» в
  `AccountHeaderCard`.
- **C. Замена `total` на композицию.** `total` → «Свободно: X ₽ ·
  Заблокировано: Y ₽» в одной строке без отдельной. Сильнее меняет вид.

Рекомендация: **A**.

**Шаги:**
1. **Санкция на UI** — вариант (A/B/C), тексты, место в шапке,
   мультивалютность (только RUB или все `AccountBalance`).
2. `AccountHeaderCard`: принять `money` / `blocked` (или `AccountBalance`),
   отобразить по выбранному варианту.
3. `PortfolioViewModel` / `PortfolioUiState`: убедиться, что `money`/
   `blocked` доступны для выбранного счёта.
4. Compose Preview: `blocked == null`, `blocked == 0.0`, `blocked > 0.0`.
5. Гейт + live-тест UI.

**Тесты:** Compose Preview на 3 состояния. Unit-тестов на formatting — нет
(используется существующий `formatDomesticCurrency`).

**Референсы:**
- `docs/architecture.md` → «Data flow: позиции» (источники `money`/`blocked`).
- `docs/domain_models.md` §4 `AccountBalance`.
- `docs/ISSUES.md` → «T-Invest WebSocket: решён» (ограничение sandbox).

**Ограничения:**
- UI-санкция (правило 1) — вариант и тексты до правки.
- Не показывать `blocked` для не-денежных позиций (только `AccountBalance`).
- Не выводить `money + blocked` как «итог», пока формула не подтверждена —
  только отдельные значения.
- Не трогать `OrdersScreen` — карточка там в контексте заявок.
- Мультивалютность — отдельный вопрос (сейчас только `rubBalance`).

**Критерий готовности:**
- `AccountHeaderCard` показывает разбивку `money` / `blocked` (по варианту).
- Не-денежные позиции / не-счета — без изменений.
- Compose Preview 3 состояния.
- Гейт зелёный; live-тест UI PASS.

**Оценка:** ~1.5–2 ч (санкция 10 мин, UI 1 ч, превью+тесты 30 мин, live-тест 30 мин).

**Зависимости:** нет.

**Связано:** `### ✅ Portfolio: модель портфеля` (money/blocked → `AccountBalance`);
`### Ревизия ссылок на PortfolioPosition после H5` (п.1 ревизии);
`docs/domain_models.md` §4.

**Открытые вопросы:**
- Вариант A/B/C — нужно решение.
- Формула `money` ↔ `blocked` ↔ `quantity` — проверить на реальном счёте
  с ненулевым `blocked`.
- Мультивалютность: только RUB или все `AccountBalance`?
- `blocked` в `OrdersScreen` — отдельной задачей при необходимости.

---
<!-- SUBSECTION_END: t-25 -->










<!-- SUBSECTION_BEGIN: t-26 -->
<a id="t-26"></a>
### [DEFER] T-26 UI выбора даты истечения стоп-заявки (GOOD_TILL_DATE)
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

---
<!-- SUBSECTION_END: t-26 -->










<!-- SUBSECTION_BEGIN: t-27 -->
<a id="t-27"></a>
### [DEFER] T-27 Индикатор доступности инструмента в карточке позиции
**Статус:** запланировано.

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

---
<!-- SUBSECTION_END: t-27 -->










<!-- SUBSECTION_BEGIN: t-35 -->
<a id="t-35"></a>
### [DEFER] T-35 OrdersListDialog: empty-state
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

---
<!-- SUBSECTION_END: t-35 -->










<!-- SUBSECTION_BEGIN: t-36 -->
<a id="t-36"></a>
### [DEFER] T-36 Settings: inline status card vs snackbar
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

---
<!-- SUBSECTION_END: t-36 -->










<!-- SUBSECTION_BEGIN: t-39 -->
<a id="t-39"></a>
### [DEFER] T-39 Общий RateLimitedException для всех брокеров
**Мобильная работа:** полное закрытие (proot + PR; data + domain + presentation, гейт на CI).

**Статус:** запланировано.

**Контекст:** T-10 (шаг 1) добавляет `AppError.RateLimited` и маппинг `RateLimitTimeoutException → RateLimited` в `toAppError` (`data/error/AppErrorMapper.kt`). Сейчас `toAppError` импортирует TInvest-specific `RateLimitTimeoutException` из `data/api/tinvest/`. При появлении BCS/Finam rate-limiter'ов (T-30, T-31) — нужен общий `interface RateLimitedException` в `data/api/common/`.

**Соответствие Clean:** соответствует (data → domain, `AppResult`/`AppError` — domain-модели; общий интерфейс — в data/common).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**MVVM:** — (не применимо, data-слой).

**KMP:** соответствует (`RateLimitedException` — data-слой, в domain не протекает; `AppError.RateLimited` — KMP-safe).

**Предусловия:** T-10 закрыт (`AppError.RateLimited` есть); T-30 / T-31 (BCS/Finam API) — хотя бы один начат.

**Файлы:** `data/api/common/RateLimitedException.kt` (новый интерфейс), `data/api/tinvest/RateLimitTimeoutException.kt` (реализация), `data/api/bcs/...`, `data/api/finam/...`, `data/error/AppErrorMapper.kt` (замена `is RateLimitTimeoutException` на `is RateLimitedException`).

**Шаги:**
1. `interface RateLimitedException { val waitedMs: Long; val timeoutMs: Long }` в `data/api/common/`.
2. `TInvestRateLimitTimeoutException : Exception(...), RateLimitedException` — переименовать/расширить существующий.
3. `toAppError`: `is RateLimitedException -> AppError.RateLimited(...)` — вместо `is RateLimitTimeoutException`.
4. BCS/Finam rate-limiter'ы (после T-30/T-31) — свои реализации.
5. Тесты: `AppErrorMapperTest` — кейс на интерфейс; `TInvestRateLimiterTest` — не сломать.

**Тесты:** `AppErrorMapperTest` (обновить кейс), `TInvestRateLimiterTest` (без изменений).

**Референсы:** `docs/architecture.md` → `architecture-23 #4`; T-10 (шаг 1); T-30, T-31.

**Ограничения:**
- Не менять поведение `toAppError` для остальных исключений.
- Не трогать stream-исключения (по T-10 — throw).

**Критерий готовности:**
- `RateLimitedException` — общий интерфейс в `data/api/common/`.
- `toAppError` не импортирует TInvest-specific исключение.
- Гейт зелёный.

**Оценка:** ~1–2 ч.

**Зависимости:** T-10 (закрыт), T-30 / T-31 (начат хотя бы один).

**Связано:** `architecture-22` (rate-limiter), `architecture-23 #4`.

**Открытые вопросы:** нет.

---
<!-- SUBSECTION_END: t-39 -->










<!-- SUBSECTION_BEGIN: t-28 -->
<a id="t-28"></a>
### [DEFER] T-28 P&L: sandbox client-side fallback для Position (низкий приоритет)
**Мобильная работа:** частичное (live-тест данных в песочнице).

**Статус:** запланировано.

**Контекст:** в **песочнице** T-Invest `GetPortfolio.expectedYield` для ряда позиций приходит `null` (серверное ограничение sandbox — P&L не считается). `Position.profit = expectedYield` в этом случае `null`, UI показывает прочерк вместо расчётного P&L. В **prod** `expectedYield` приходит — там fallback **не нужен и вреден** (маскирует брокерские баги).

Клиентский расчёт `(currentPrice - averagePrice) * quantity * moneyPerPointOfPrice` может дать корректную цифру для sandbox-счетов.

`PositionFlowManager.enrichAndCache` уже подставляет `expectedYield` из кеша для partial-событий, когда polling пришёл. Но если сервер не прислал его в самом polling (sandbox) — fallback отсутствует.

**Соответствие Clean:** соответствует.

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** H5 закрыт (`PortfolioPosition`, `toPortfolioPosition()`, `mergeWith()`, `calculateProfitPercent` удалены; `Position` — единственная модель, `PositionFlowManager.sheet` — SSoT).

**Файлы:**
- `domain/models/Portfolio.kt` — новая `fun PositionStreamItem.clientProfit(): Double?` (pure).
- `domain/api/managers/PositionFlowManager.kt` — при построении `Position` из `PositionStreamItem` применять fallback **только при `sandboxMode == true`**; `sandboxMode` уже доступен в `start(accountKeys, sandboxMode, …)`.
- `app/src/test/kotlin/com/gitlab/biomorf/tscalp/domain/models/PortfolioTest.kt` (новый) — unit-тесты.
- `app/src/test/kotlin/.../domain/api/managers/PositionFlowManagerTest.kt` — кейс sandbox vs prod.

**Шаги:**
1. `fun PositionStreamItem.clientProfit(): Double?`: `val avg = averagePrice ?: return null`; `val price = currentPrice ?: return null`; `if (avg <= 0.0 || quantity == 0.0) return null`; `val point = moneyPerPointOfPrice.takeIf { it > 0.0 } ?: 1.0`; `return (price - avg) * quantity * point`.
2. `PositionFlowManager`: в пути конструирования `Position` из `PositionStreamItem` (polling + stream merge) — `if (sandboxMode && expectedYield == null) profit = clientProfit() else profit = expectedYield`.
3. В prod, если `expectedYield == null` — `logger.w("prod: expectedYield == null, fallback disabled")` (аномалия, не подменять).
4. Тесты:
   - `clientProfit()`: server (non-null), futures (`moneyPerPointOfPrice`), `averagePrice == null` → null, `quantity == 0.0` → null, убыток (negative client) — 5 кейсов.
   - `PositionFlowManager`: sandbox + `expectedYield == null` → клиентский P&L; **prod** + `expectedYield == null` → `profit = null`, лог — 2 кейса.
5. Гейт: `testDebugUnitTest / lintDebug / ktlintCheck / detekt`.

**Тесты:** 5 unit-кейсов `clientProfit()` + 2 кейса `PositionFlowManager` (sandbox vs prod).

**Референсы:**
- `### Orders: P&L в песочнице (expectedYield)` — исходная диагностика sandbox-ограничения.
- Закрытая `### ✅ Рефакторинг расчёта P&L для PortfolioPosition`.
- `data/api/tinvest/TInvestPortfolioService.kt` — passthrough `expectedYield` в `fetchPortfolioPositions`.
- `domain/api/managers/PositionFlowManager.enrichAndCache` — заполнение из кеша.
- `docs/architecture.md` → «Data flow: позиции» (`Position`, `PositionStreamItem`).

**Ограничения:**
- **Fallback — только `sandboxMode == true`.** В prod `expectedYield` авторитетен.
- В prod при `expectedYield == null` — **лог, не подмена** (не маскировать брокерские баги клиентским расчётом).
- Не менять модель `Position` (только вычисление `profit` при конструировании).
- `profitPercent` — computed getter на `Position` (не трогать).
- Fallback только при `expectedYield == null` (не при `0.0`).

**Критерий готовности:**
- **Sandbox:** `expectedYield == null && averagePrice != null && currentPrice != null` → `profit` клиентски (UI показывает цифру вместо прочерка).
- **Prod:** `expectedYield == null` → `profit = null` + `logger.w`; `expectedYield != null` → server (поведение 1:1, без изменений).
- Тесты 5 + 2, гейт зелёный.

**Оценка:** ~1.5–2 ч (добавлено 0.5 ч на прокидывание `sandboxMode` в путь конструирования и кейсы sandbox/prod).

**Зависимости:** нет.

**Связано:** `### Orders: P&L в песочнице (expectedYield)` (диагностика, отдельная задача); `### ✅ H5. PortfolioPosition removed` (базовая модель); закрытая `### ✅ Рефакторинг расчёта P&L для PortfolioPosition`.

**Открытые вопросы:**
- В sandbox, если `expectedYield != null` (пришёл), но клиентский расчёт расходится > 5% — логировать? Отложено.
- Если `averagePrice` устарел — проверка «свежести» отдельная задача.








#### Классификатор ошибок

**Файлы:**
- `util/AppResultExtensions.kt` — `Throwable.toAppError`
  (66 строк, complexity 26).

**Пути решения:** разбить на per-broker обработчики
(`toTInvestError`, `toBcsError`

---
<!-- SUBSECTION_END: t-28 -->










<!-- SECTION_END: roadmap-defer -->










<!-- FILE_END -->
