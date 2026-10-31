# Architecture

Справочник архитектурных решений TScalp. Читается вместе с `AGENTS.md`
(правила) и `docs/clean-audit.md` (отклонения).

## Data flow: позиции (portfolio positions)

### Модели

**Одна domain-модель:** `PortfolioPosition` (`domain/models/Portfolio.kt`).

- **Domain-данные (stored):** `tscalpInstrumentId`, `brokerName`, `name`, `ticker`,
  `quantity`, `currentPrice`, `averagePrice`, `profit`, `money`, `blocked`,
  `pointValue`, `instrumentType`, `classCode`, `isin`, `currency`.
- **Derived domain (computed getters, не хранятся):**
  - `val totalValue: Double get() = currentPrice * quantity`
  - `val profitPercent: Double? get() = calculateProfitPercent(profit, averagePrice, quantity, pointValue)`
- **Presentation (вне модели):** `priceChangePercent` (передаётся в `AssetPositionCard`
  отдельным параметром); цвета — `PositionFormatter.priceChangeColor`; форматирование —
  `presentation/util/CurrencyFormat`.

**Почему так (Clean):**
- Derived-поля — pure Kotlin, без platform/framework. Это domain-факты
  (`totalValue` — стоимость позиции, не «как её показать»), а не UI-предпочтения.
- Computed getters исключают рассогласование (нечего «забыть обновить»).
- Presentation-формат вынесен в presentation (PR #64): domain не знает о Locale/Compose.

**Отменённое решение (clean-audit Н5):** разделение на `Position` (domain) +
`PortfolioPositionUi` (presentation). Причина отмены: derived-поля — domain,
presentation-часть уже вынесена. Держать одну модель проще и достаточно Clean.

### Обогащение (SSoT)

**Владелец:** `PositionFlowManager` (`domain/api/managers/PositionFlowManager.kt`).

- **Cache:** `Map<String, PositionStreamItem>` — всегда актуальное enriched raw-состояние.
  - Full-событие (polling, `currentPrice != null`) → `cache[uid] = item`.
  - Partial-событие (stream, `currentPrice == null`) → `cache[uid] = mergeWith(cached, item)`.
    Ключевое: **partial тоже пишется в cache** (иначе cache отстаёт от UI).
- **Emission:** `_positions: StateFlow<List<PortfolioPosition>>`.
  - На каждое событие: `cache[uid] = ...`, затем `_positions.value = cache.values.map { it.toPortfolioPosition() }`.
  - Derived-поля (`totalValue`, `profitPercent`) вычисляются геттерами при чтении —
    хранить их не нужно.
- **Начальная загрузка:** `fetchPositionsResult` (репозиторий) → менеджер кладёт в cache,
  `_positions` заполняется до первого события. UI не пустой на старте.

**Правило merge (`PortfolioPosition.mergeWith` / `PositionStreamItem.mergeWith`):**
- `quantity` — из события.
- `currentPrice` / `averagePrice` / `pointValue` — non-null fallback.
- `money` / `blocked` — non-null fallback.
- `profit` — `expectedYield` из события (passthrough; client-side fallback — отдельная задача).
- `isin` / `classCode` — `ifBlank { old }`.
- `instrumentType` — из события.

### ViewModel

- Подписывается на `positionFlowManager.positions` (`StateFlow<List<PortfolioPosition>>`).
- Кладёт список в `UiState`. **Не мержит, не вычисляет derived-поля.**
- `priceChangePercent` — если нужен, VM вычисляет из истории цен (presentation-логика)
  и передаёт в UI отдельно.

### Границы (dependency rule)

~~~
presentation → domain ← data
~~~

- `domain` не знает о `data` (источник `PositionStreamItem`) и `presentation`
  (формат/цвета).
- `data` создаёт `PositionStreamItem` (raw), отдаёт менеджеру.
- `presentation` читает `PortfolioPosition` (готовую), не мержит.

### Связанные документы

- `docs/clean-audit.md` → Н5 (отменён), Н24 (mergeWith SSoT).
- `AGENTS.md` → 2c (отклонения), 2d (временные решения).
- `docs/ROADMAP.md` → `### P&L: client-side fallback` (отдельная задача).

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
