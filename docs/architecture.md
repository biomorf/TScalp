# Architecture

Справочник архитектурных решений TScalp. Читается вместе с `AGENTS.md`
(правила) и `docs/clean-audit.md` (отклонения).

Архитектурные решения и планы проверяются по триаде Clean + MVVM +
KMP-ready — `AGENTS.md` → 2e. Три поля фиксации в задаче:
`**Соответствие Clean:**`, `**MVVM:**`, `**KMP:**`.

## Data flow: позиции (portfolio positions)

### Модели

**Domain (`domain/models/Portfolio.kt`):**

- `Position` (sealed interface, 7 subtypes: `SharePosition` / `FuturePosition` /
  `BondPosition` / `OptionPosition` / `EtfPosition` / `CurrencyPosition` /
  `OtherPosition`) — позиция по инструменту в конкретном счёте. Поле
  `instrument: Instrument` типизировано по subtype (компилятор проверяет
  соответствие `Position` ↔ `Instrument`). Общие поля: `accountId`,
  `brokerName`, `quantity: Double`, `currentPrice`, `averagePrice?`,
  `profit?`, `lockedQuantity?`, `priceChangePercent?`. Computed getters:
  `totalValue` (`= currentPrice * quantity * instrument.moneyPerPointOfPrice`),
  `profitPercent` (от `averagePrice`).
- `AccountBalance(currency, money, blocked)` — деньги по валюте в счёте.
  Computed `total`.
- `AccountPortfolio(accountId, brokerName, positions: List<Position>,
  balances: List<AccountBalance>)` — агрегат одного счёта. Computed
  `totalProfit`.
- `AccountKey(brokerName, accountId)` — составной ключ счёта.
- `PortfolioSheet(accounts: Map<AccountKey, AccountPortfolio>)` —
  мультисчётный агрегат. **Единственный публичный эмитируемый тип**
  `PositionFlowManager`.
- `PositionStreamItem` — internal-событие источника (stream / polling).
  Наружу не эмитится; конверсия в `Position` / `AccountBalance` — через
  `toPosition(instrument)` / `toAccountBalance()`.

**Почему (Clean):**

- `Position` — domain-факт; subtype соответствует subtype `Instrument`.
- Derived-поля (`totalValue`, `profitPercent`) — computed getters,
  нечего «забыть обновить».
- Presentation-формат (Locale / Compose / цвета) — вне domain.

**Решение по clean-audit Н5 (исправлено, PR #90):** `PortfolioPosition`
удалён; `Position` (sealed) — единственная модель. Presentation-поля
(`priceChangePercent` day-change, цвета) — в `PositionFormatter` и
presentation-util.

### Обогащение (SSoT)

**Владелец:** `PositionFlowManager` (`domain/api/managers/PositionFlowManager.kt`).

- **`sheet: StateFlow<PortfolioSheet>`** — единственный источник истины.
- **Polling** (`broker.portfolio?.fetchPortfolioPositions(accountId, sandboxMode)`,
  раз в 10 сек): возвращает `AccountPortfolio`. Заменяет
  `sheet.accounts[accountKey]` целиком. Existing `AccountBalance`
  сохраняются, если polling не дал balances (`GetPortfolio` не отдаёт
  `money/blocked`; интеграция через `GetPositions` — отдельная задача).
- **Stream** (`broker.streams?.subscribePositions(accountId)`):
  - `Position` уже есть в `sheet.accounts[accountKey].positions` →
    `mergeStreamItem` (top-level extension `Position.mergeStreamItem(item)`;
    обновляет `quantity`, `currentPrice`, `averagePrice`, `profit`).
  - `Position` нет, `Instrument` есть в `instrumentCache` →
    `toPosition(instrument)`.
  - `Instrument` нет → skip (ждём polling, который заполнит
    `instrumentCache`).
- **Domestic currency** (`isDomesticCurrency = true`) → `mergeBalance`
  в `AccountBalance`. Иностранная валюта → `CurrencyPosition` через
  общий merge.
- **`instrumentCache: Map<AccountKey, Map<tscalpInstrumentId, Instrument>>`** —
  заполняется polling'ом.

**Seed (`start(accountKeys, sandboxMode, seed = PortfolioSheet?)`):**
начальный снимок из `PortfolioViewModel.loadPortfolio` (первичный fetch +
`getBalanceResult` per-account → `AccountBalance(currency = "rub",
money, blocked = 0.0)`). `applySeed()` merge-ит seed в `_sheet`: новый
счёт → seed целиком, существующий → только `balances` (positions
остаются из manager'а — polling / WS могли обновить свежее). Без seed
первый polling emit (раз в 10 сек) перезаписал бы `_sheet` пустыми
balances (`GetPortfolio` не отдаёт `money/blocked`) → шапка счёта
«нет данных».

**Правило `mergeStreamItem`:** `quantity` — из события; `currentPrice` /
`averagePrice` / `profit` — non-null fallback. Политика фильтрации
partial-событий — в manager.

**Окно отката:** polling раз в 10 сек может «откатить» свежее WS-обновление
(≤10 сек). Приемлемо.

### ViewModels

#### `PortfolioViewModel`

- Подписывается на `positionFlowManager.sheet` (`StateFlow<PortfolioSheet>`).
- Читает `uiState.sheet.accounts` напрямую (per-account группировка —
  по `AccountKey(brokerName, accountId)` в `PortfolioScreen`).
- Overlay `currentPrice` / `priceChangePercent` из `PriceFlowManager`
  (tick-to-tick, presentation-логика) — `uiState.prices` /
  `uiState.priceChangePercents`; day-change — задача
  `### Position: источники priceChangePercent и lockedQuantity`.

#### `OrdersViewModel` split (4 VM)

После 13d (split `OrdersViewModel`) — 4 VM:

| VM | State | Use cases / domain API | Lifecycle |
|---|---|---|---|
| `OrdersViewModel` (Coord) | `OrdersCoordState` (8 полей: accounts, selectedAccountId, isApiInitialized, lastSelectedInstruments, portfolioPositions, isLoading, statusMessage, isError) + **passthrough** `tradingStatuses: StateFlow<Map>` = `tradingStatusRepository.statuses` | `LoadOrdersAccountsUseCase`, `CheckBrokerInitializationUseCase`, `UpdateTradingStatusesUseCase`, `RefreshSelectedInstrumentsUseCase`; `CardsRepository`; `PositionFlowManager` | `startPositionUpdates` / `stopPositionUpdates` / `onCleared` (stop) |
| `OrderFormViewModel` | `OrderFormState` (29 полей: instrument / quantity / orderType / prices / pair / confirm / status) | `SelectInstrumentUseCase`, `SelectPairedInstrumentUseCase`, `RestoreOrderFormUseCase`, `PlaceOrderUseCase`, `CalculateTradeDetailsUseCase`, `CalculatePairExecUseCase`; `PriceFlowManager.setInterest`; `TradingStateRepository`, `TradingPreferencesRepository` | `onCleared` (clearInterest ORDERS) |
| `OrderSearchViewModel` | `OrderSearchState` (15 полей: search / pair-search / broker dialogs / status) | `SearchInstrumentsUseCase`; `SearchCacheRepository`; `BrokerRegistry` | — (stateless) |
| `OrderDialogsViewModel` | `OrderDialogsState` (6 полей: dialog flag / ticker / broker / account / swipe reset) | `LoadCardAccountsUseCase`, `SaveCardSettingsUseCase`; `CardsRepository`, `AccountSelectionRepository`, `SandboxPreferencesRepository` | — (stateless) |

**Cross-VM — 5 связей, все через domain или UI-orchestration (не через `_uiState`):**

1. **Search → Form** (user-intent, **UI оркестрирует**, P4 KDoc):
   `onSearchResultSelected` + `formVM.onInstrumentSelected`;
   `onPairedSearchResultSelected` + `formVM.onPairedInstrumentSelected`.
2. **Dialogs → Coord** (domain): `saveBrokerSettings` →
   `SaveCardSettingsUseCase` → `CardsRepository.identities` →
   Coord подписан (A4-b-1).
3. **Coord → Form** (data-flow, domain): `PositionFlowManager.sheet`
   — оба читают.
4. **Search → UI** (data-flow, domain): Coord passthrough
   `TradingStatusRepository.statuses` (P1) — UI читает
   `coordVM.tradingStatuses`.
5. **Form → Form** (data-flow, domain): `PriceFlowManager.setInterest(
   ORDERS, uids)` — Form-метод (`syncPriceInterest`).

~~~mermaid
flowchart LR
    subgraph VM[4 ViewModels]
        C[OrdersViewModel<br/>Coord: accounts + lifecycle]
        F[OrderFormViewModel<br/>form + postOrder + prices]
        S[OrderSearchViewModel<br/>search + pair-search]
        D[OrderDialogsViewModel<br/>dialog state]
    end
    subgraph DOM[domain]
        REPO[CardsRepository /<br/>TradingStateRepository /<br/>TradingStatusRepository]
        MG[PositionFlowManager /<br/>PriceFlowManager]
    end
    UI[OrdersScreen<br/>+ OrdersSearch / OrdersPairTrading]
    S -->|user-intent<br/>UI orchestrates| F
    D -->|saveCard| REPO
    REPO -->|identities<br/>reactive| C
    MG -->|sheet<br/>reactive| C
    MG -->|sheet / prices<br/>reactive| F
    REPO -->|statuses<br/>passthrough| C
    F -->|setInterest ORDERS| MG
    C -->|coordState| UI
    S -->|searchState| UI
    F -->|formState| UI
    D -->|dialogState| UI
~~~

Подпись: 4-VM структура `Orders` после split 13d — 4 state, 5 cross-VM связей через domain / UI-orchestration, ноль `_uiState`-чтений.

### Границы (dependency rule)

~~~
presentation → domain ← data
~~~

- `domain` не знает о `data` (`PositionStreamItem` — internal источника)
  и `presentation` (формат / цвета).
- `data` создаёт `PositionStreamItem` (raw) и `AccountPortfolio`
  (полный polling-снимок), отдаёт менеджеру.
- `presentation` читает `PortfolioSheet` (готовый), не мержит.

### Связанные документы

- `docs/domain_models.md` → `## Портфель (Portfolio)` §1–§12 —
  проектный мастер-план.
- `docs/clean-audit.md` → Н5 (исправлено, merge `d04ecc4`, PR #90: legacy
  `PortfolioPosition` удалён), Н24 (SSoT; `mergeWith` удалён в H5,
  SSoT — `PositionFlowManager.sheet` + `mergeStreamItem`).
- `AGENTS.md` → 2c (отклонения), 2d (временные решения).
- `docs/ROADMAP.md` → `### ✅ OrdersViewModel: миграция на PortfolioSheet`,
  `### ✅ H5. PortfolioPosition removed` (`ROADMAP-ARCHIVE.md`,
  legacy `PortfolioPosition`),
  `### Position: источники priceChangePercent и lockedQuantity`
  (day-change, lockedQuantity, balances),
  `### P&L: sandbox client-side fallback для Position` (отдельная задача).

## CardIdentity и слоты

Модель карточки инструмента на экране «Заявки» и в «Последних просмотренных»:
как карточки создаются, хранятся и различаются.

### CardIdentity — иммутабельный identity

`CardIdentity(tscalpInstrumentId, brokerName, accountId)` — **иммутабельная**
identity карточки. Все три поля задаются при создании и не меняются до
удаления карточки.

- `tscalpInstrumentId` — broker-specific паспорт (`uid` T-Invest / `figi`
  BCS / `id` Finam). См. `docs/domain_models.md` §3.
- `brokerName` — брокер, у которого найден инструмент.
- `accountId` — счёт этого брокера.

**Различие карточек — по полному composite.** Один `tscalpInstrumentId`
сам по себе не уникален между брокерами и счетами:

- `(uid_1, T, A)` и `(uid_1, T, B)` — **две разные** карточки (один
  инструмент, один брокер, разные счета);
- `(uid_1, T, A)` и `(uid_2, B, A)` — **две разные** карточки (разные
  инструменты у разных брокеров);
- `(uid_1, T, A)` и `(uid_1, B, A)` — **две разные** карточки (один uid
  у разных брокеров, если такое встретится).

`ticker` в identity **не входит** — только data-граница (BCS/Finam-запросы)
и display.

### Создание карточки

1. Пользователь задаёт брокера в свойствах слота (до поиска).
2. В строке поиска ищет инструмент у этого брокера.
3. Выбирает инструмент → карточка создаётся с
   `(tscalpInstrumentId, brokerName слота, accountId)`.
   `accountId` на момент создания может быть `null`.
4. В свойствах карточки выбирается `accountId` → карточка
   **пересоздаётся** `(uid, broker, A)`. Старая `(uid, broker, null)`
   умирает.

**Смена счёта — пересоздание identity, не мутация.** `SaveCardSettings` —
не «обновляет account в карточке», а «удаляет старую identity + создаёт
новую».

### Слоты (primary / pair)

На экране «Заявки» два независимых слота: основной и парный. В каждом —
своя **активная карточка** (или none до выбора).

Каждый слот имеет собственные свойства (broker + account), независимые
от другого слота. Поиск в слоте — у брокера слота.

- `activeCardFor(slot)` — find по полному `CardIdentity` слота
  `(uid, broker, accountId)`.
- Один и тот же инструмент в двух слотах — **две разные** карточки
  (разные `accountId` или разные `broker`).

### Коллизия при смене счёта

Если пользователь в свойствах карточки выбирает `accountId`, который
уже занят **другой** карточкой того же `(uid, broker)` в этом или
соседнем слоте — счёт в диалоге **disabled** (α-вариант).

Иначе одна identity оказалась бы в двух местах — неоднозначно для
`activeCardFor` и `SaveCardSettings`.

**Swap слотов** при α: через временный счёт или удаление + пересоздание.
Быстрый swap отдельной кнопкой рядом со slider «Парная торговля» —
отдельная задача (`### UI: кнопка swap слотов`).

### Портфель на карточке

Если инструмент `(uid, broker, accountId)` уже есть в портфеле (на этом
счёте у этого брокера) — карточка показывает `quantity` / `averagePrice`
/ `profit` как на вкладке «Портфель». Источник —
`PositionFlowManager.sheet`, ключ `AccountKey(broker, accountId)`.

### Границы (Clean)

`CardIdentity` — domain-модель. Presentation конструирует её только через
usecase (`SelectInstrumentUseCase` / `SaveCardSettingsUseCase`); прямая
конструкция в UI запрещена (M4). `SaveCardSettings` и `OpenCardDialog` —
usecase с I/O и бизнес-правилом (пересоздание карточки / коллизия), не
presentation-функции.

## KMP readiness

Цель: `domain` → `:shared/commonMain`. Аудит 2026-10-29 (read-only, `grep` по `domain/`).

### Блокеры в domain

| # | Что | Где | Фикс |
|---|---|---|---|
| 2 | `javax.inject.Inject` / `Singleton` | 4 менеджера | Koin (без аннотаций) — задача `### Плавный переход Hilt → Koin` |

**Принцип (2e → KMP → DI):** `domain` не содержит DI-аннотаций
(`javax.inject.Inject` / `Singleton`, `@HiltViewModel`). DI-граф —
снаружи (`di/` сейчас, Koin потом). `domain/usecases/*` — без
`@Inject`: DI через `@Provides` в `di/UseCaseModule.kt`. Известный
долг — 4 менеджера в `domain/api/managers/` (строка #2): там нужен
Koin, не `@Provides` (многослойные зависимости).

**`javax.inject` — 4 файла** в `domain/api/managers/`: `PositionFlowManager`, `PriceFlowManager`, `OrderFlowManager`, `BrokerLifecycleManager`. Аудит 2026-10-29 зафиксировал 3; `BrokerLifecycleManager` добавлен позже (PR #77, H50, 2026-10-31). Строка #4 (PR #74) — 3 менеджера, потому что `BrokerLifecycleManager` тогда ещё не существовал.

**Исправлено (PR #73, `dev`, 2026-10-30):**

- #1 — `java.time.Instant` → `kotlin.time.Instant` (stdlib, KMP-safe); 14 полей в `InstrumentUi`/`FutureUi`/`ShareUi`.
- #3 — `System.currentTimeMillis()` → инжект `kotlin.time.Clock`; `StreamStateTracker`/`OrderCache` на `Instant`/`Duration`; `di/TimeModule.kt`.

**Исправлено (PR #74, `dev`, 2026-10-30):**

- #4 — `Dispatchers.IO` → инжект `CoroutineDispatcher` через DI (`di/DispatcherModule.kt`); `limitedParallelism(1)` сохранён (доменная сериализация); 3 менеджера + тесты.

### Чисто (KMP-safe)

- `domain/models/*` — KMP-safe (PR #73: `kotlin.time.Instant`).
- `domain/api/repository/*` (8 интерфейсов) — ноль platform.
- `domain/api/roles/*` — чисто.
- `domain/log/Logger` — pure Kotlin interface.
- `domain/usecases/*` — **24 файла с `@Inject`/`@Singleton`** —
  известный долг по 2e → KMP → DI. Refactoring to `@Provides` —
  задача `### Н37 (rename RepositoryModule + domain without DI)`.

### Не в этой фазе

- `data/` — не готов к `commonMain` (`android.*`: Context, DataStore, CustomTrustManager → `androidMain`; `java.*` в 8 файлах; `javax.inject` → уйдёт с Koin).
- Koin — предусловие (задача `### Плавный переход Hilt → Koin`).

### Порядок

1. ✅ Фикс #1 (`Instant`) + #3 (`currentTimeMillis`) — закрыто в PR #73.
2. ✅ Фикс #4 (`Dispatchers.IO` → инжект `CoroutineDispatcher`) — закрыто (PR #74).
3. Koin — снимает #2.
4. `domain` → `:shared/commonMain` (umbrella-задача `### KMP: domain → commonMain`).
5. `data` → split `commonMain`/`androidMain` (отдельная фаза).
