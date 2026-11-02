# Architecture

Справочник архитектурных решений TScalp. Читается вместе с `AGENTS.md`
(правила) и `docs/clean-audit.md` (отклонения).

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

**Presentation (legacy, там же):**

- `PortfolioPosition` — legacy-модель для `OrdersViewModel`
  (`uiState.portfolioPositions`). Удаление — задача
  `### OrdersViewModel: миграция на PortfolioSheet`.

**Почему (Clean):**

- `Position` — domain-факт; subtype соответствует subtype `Instrument`.
- Derived-поля (`totalValue`, `profitPercent`) — computed getters,
  нечего «забыть обновить».
- Presentation-формат (Locale / Compose / цвета) — вне domain.

**Решение по clean-audit Н5:** `PortfolioPosition` — legacy, удаляется.
Целевая domain-модель — `Position` (sealed) без presentation-полей;
presentation-поля (`priceChangePercent` day-change, цвета) — в
`PositionFormatter` и presentation-util.

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
    `mergePartial` (только `quantity`, `currentPrice`, `averagePrice`,
    `profit`).
  - `Position` нет, `Instrument` есть в `instrumentCache` →
    `toPosition(instrument)`.
  - `Instrument` нет → skip (ждём polling, который заполнит
    `instrumentCache`).
- **Domestic currency** (`isDomesticCurrency = true`) → `mergeBalance`
  в `AccountBalance`. Иностранная валюта → `CurrencyPosition` через
  общий merge.
- **`instrumentCache: Map<AccountKey, Map<tscalpInstrumentId, Instrument>>`** —
  заполняется polling'ом.

**Правило `mergePartial`:** `quantity` — из события; `currentPrice` /
`averagePrice` / `profit` — non-null fallback. Политика фильтрации
partial-событий — в manager.

**Окно отката:** polling раз в 10 сек может «откатить» свежее WS-обновление
(≤10 сек). Приемлемо.

### Legacy `flow`

`flow: SharedFlow<PositionStreamItem>` — retained для `OrdersViewModel`
(`updatePositionPnl`). Эмитится из обоих источников (polling — через
`position.toStreamItem()`, stream — как пришло). Миграция — задача
`### OrdersViewModel: миграция на PortfolioSheet`.

### ViewModel

`PortfolioViewModel`:

- Подписывается на `positionFlowManager.sheet` (`StateFlow<PortfolioSheet>`).
- Adapter `Position → PortfolioPosition` в private `toUiPosition()`:
  структурные поля из `Position`, overlay `currentPrice` /
  `priceChangePercent` из `PriceFlowManager` (tick-to-tick,
  presentation-логика; day-change — задача
  `### Position: источники priceChangePercent и lockedQuantity`).
- `uiState.positions: List<PortfolioPosition>` — legacy, для
  `PortfolioScreen` до миграции на `sheet`. Группировка per-account —
  по `AccountKey(brokerName, accountId)`.

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
- `docs/clean-audit.md` → Н5 (пересматривается: legacy `PortfolioPosition`
  → `Position`), Н24 (mergeWith SSoT).
- `AGENTS.md` → 2c (отклонения), 2d (временные решения).
- `docs/ROADMAP.md` → `### OrdersViewModel: миграция на PortfolioSheet`
  (удаление legacy), `### Position: источники priceChangePercent и
  lockedQuantity` (day-change, lockedQuantity, balances), `### P&L:
  client-side fallback` (отдельная задача).

## KMP readiness

Цель: `domain` → `:shared/commonMain`. Аудит 2026-10-29 (read-only, `grep` по `domain/`).

### Блокеры в domain

| # | Что | Где | Фикс |
|---|---|---|---|
| 2 | `javax.inject.Inject` / `Singleton` | 4 менеджера | Koin (без аннотаций) — задача `### Плавный переход Hilt → Koin` |

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
- `domain/usecases/*` — не аудировано (проверить при первом касании).

### Не в этой фазе

- `data/` — не готов к `commonMain` (`android.*`: Context, DataStore, CustomTrustManager → `androidMain`; `java.*` в 8 файлах; `javax.inject` → уйдёт с Koin).
- Koin — предусловие (задача `### Плавный переход Hilt → Koin`).

### Порядок

1. ✅ Фикс #1 (`Instant`) + #3 (`currentTimeMillis`) — закрыто в PR #73.
2. ✅ Фикс #4 (`Dispatchers.IO` → инжект `CoroutineDispatcher`) — закрыто (PR #74).
3. Koin — снимает #2.
4. `domain` → `:shared/commonMain` (umbrella-задача `### KMP: domain → commonMain`).
5. `data` → split `commonMain`/`androidMain` (отдельная фаза).
