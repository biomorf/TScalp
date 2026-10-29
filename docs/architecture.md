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
