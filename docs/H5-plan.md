# H5 handoff — полное удаление legacy `PortfolioPosition`

План для исполнителя (человек или агент) без доступа к истории чата.
Читается вместе с `AGENTS.md`, `AGENTS-LESSONS.md`,
`docs/clean-audit.md` → Н5, `docs/ROADMAP.md` → `### H5. PortfolioPosition:
разделение domain/presentation`.

---

## 1. Что уже сделано

Задача стартовала как «разделить `PortfolioPosition` (presentation-поля в domain-модели) на `Position` (domain) + `PortfolioPositionUi` (presentation)». Первая половина закрыта в `feature/portfolio-model` (PR #89).

**Готово (main, `dev`):**

- `domain/models/Portfolio.kt`:
  - `Position` — `sealed interface`, 7 subtypes (`SharePosition` / `FuturePosition` / `BondPosition` / `OptionPosition` / `EtfPosition` / `CurrencyPosition` / `OtherPosition`), симметрично `Instrument`.
  - Computed getters: `totalValue` (`= currentPrice * quantity * instrument.moneyPerPointOfPrice`), `profitPercent` (от `averagePrice`).
  - `AccountBalance` / `AccountPortfolio` / `PortfolioSheet` / `AccountKey`.
  - `PositionStreamItem.toPosition(instrument)` / `toAccountBalance()` — конвертеры (16 тестов).
  - `Position.toStreamItem()` — internal reverse-adapter.
- `PositionFlowManager` эмитит `sheet: StateFlow<PortfolioSheet>` (мультисчёт).
- `PortfolioViewModel` / `PortfolioScreen` / `OrdersViewModel` — на `sheet` (после PR #89).
- `toPortfolioPosition` / `PortfolioPosition.mergeWith` — **удалены** (`a859185`).
- `Обогащение name в PortfolioPosition (вариант C)` — **закрыта**: `name = instrument.name` во всех адаптерах.

**Остаток (этот план):**

`PortfolioPosition` — **presentation-модель** — физически лежит в `domain/models/Portfolio.kt` и возвращается из domain-интерфейса `PortfolioRepository.fetchPositionsResult → AppResult<List<PortfolioPosition>>`. Нарушение dependency rule (presentation-модель в domain-пакете и в domain-контракте). Задача — **удалить `PortfolioPosition` полностью**, UI должен работать с `Position` напрямую.

---

## 2. Ключевые решения (приняты, не пересматриваются без согласования)

1. **`PortfolioPositionUi` НЕ создавать.** Идея «отдельная presentation-модель» отклонена: `Position` уже даёт всё, что нужно UI (`instrument.*` + computed getters). Дополнительный класс — лишняя сущность.

2. **`PositionFormatter` — чистые функции** (без `PortfolioPosition` / `Position` как аргументов). Presentation-формат не должен зависеть от domain-модели, только от примитивов.

3. **`AssetPositionCard` принимает `Position` + overlay-параметры.** `displayCurrentPrice: Double? = null` (tick-to-tick overlay из `PriceFlowManager`), `priceChangePercent: Double? = null` (tick-to-tick). Внутри Composable: `effectivePrice = displayCurrentPrice ?: position.currentPrice`.

4. **`PortfolioUiState.positions` удаляется**, `PortfolioScreen` читает `uiState.sheet.accounts` напрямую. Overlay цен / % — в отдельных полях `uiState.prices: Map<String, Double>` + `uiState.priceChangePercents: Map<String, Double>`.

5. **`PortfolioViewModel.loadPortfolio` делает seed `PortfolioSheet`** из первичного fetch per-account (для UX «до первого polling»). Менеджер затем замещает `sheet` первым emit'ом. Это не SSoT-нарушение: менеджер — источник, seed — начальное приближение.

6. **`emptyPosition(instrument, currentPrice, accountId, brokerName, lockedQuantity)` — dispatcher** по `Instrument` (7 private factories: `emptyShare`, `emptyFuture`, …). Заменяет `emptyPortfolioPosition` (2 callsites в `OrdersScreen`).

7. **`quantity: Long → Double`.** `Position.quantity: Double` (Finam дробное). `PositionFormatter` форматирует: `if (q == q.toLong()) q.toLong().toString() else "%.1f".format(q)`.

8. **`calculateProfitPercent` — удалить.** Логика дублируется в `Position.profitPercent` getter. `CalculateProfitPercentTest` — удалить.

---

## 3. Файлы и изменения

### Domain

**`domain/api/repository/PortfolioRepository.kt`**

~~~kotlin
// Было
suspend fun fetchPositionsResult(
    brokerName: BrokerName,
    accountId: String,
    sandboxMode: Boolean,
): AppResult<List<PortfolioPosition>>

// Стало
suspend fun fetchPositionsResult(
    brokerName: BrokerName,
    accountId: String,
    sandboxMode: Boolean,
): AppResult<List<Position>>
~~~

Импорт `Position` вместо `PortfolioPosition`.

**`data/repository/PortfolioRepositoryImpl.kt`**

- `fetchPositionsResult` — возвращает `List<Position>` (без `.map { it.toLegacyPortfolioPosition() }`).
- Удалить adapter `private fun Position.toLegacyPortfolioPosition()`.
- Удалить импорты `PortfolioPosition`, `toLegacyPortfolioPosition`.

**`domain/models/Portfolio.kt`**

Удалить:
- `data class PortfolioPosition(...)` (строки 120–148 в актуальной версии).
- `fun calculateProfitPercent(...)` (строки 161–174).
- `fun emptyPortfolioPosition(...)` (строки 189–~210).
- `internal fun Position.toLegacyPortfolioPosition()` (строки ~549–~567).
- KDoc-упоминания `PortfolioPosition` в других местах.

Добавить:
~~~kotlin
/**
 * Пустая [Position] для инструмента, которого нет в портфеле
 * (например, в карточке заявки, когда позиция ещё не открыта).
 *
 * Диспетчеризация по [Instrument]: `Share` -> [SharePosition] и т.д.
 * `quantity = 0.0`, `averagePrice = null`, `profit = null`,
 * `priceChangePercent = null`, `lockedQuantity` — из параметра.
 *
 * @param instrument инструмент.
 * @param currentPrice текущая цена (0.0 если неизвестна).
 * @param accountId id счёта (может быть `""` для карточки заявки до выбора счёта).
 * @param brokerName брокер.
 * @param lockedQuantity заблокированное количество (nullable).
 */
fun emptyPosition(
    instrument: Instrument,
    currentPrice: Double,
    accountId: String,
    brokerName: BrokerName,
    lockedQuantity: Double? = null,
): Position = when (instrument) {
    is Share -> SharePosition(instrument, brokerName, accountId, 0.0, currentPrice, null, null, lockedQuantity, null)
    is Future -> FuturePosition(instrument, brokerName, accountId, 0.0, currentPrice, null, null, lockedQuantity, null)
    is Bond -> BondPosition(instrument, brokerName, accountId, 0.0, currentPrice, null, null, lockedQuantity, null)
    is Option -> OptionPosition(instrument, brokerName, accountId, 0.0, currentPrice, null, null, lockedQuantity, null)
    is Etf -> EtfPosition(instrument, brokerName, accountId, 0.0, currentPrice, null, null, lockedQuantity, null)
    is Currency -> CurrencyPosition(instrument, brokerName, accountId, 0.0, currentPrice, null, null, lockedQuantity, null)
    is Other -> OtherPosition(instrument, brokerName, accountId, 0.0, currentPrice, null, null, lockedQuantity, null)
}
~~~

### Presentation — Portfolio

**`presentation/screens/portfolio/PortfolioUiState.kt`**

~~~kotlin
data class PortfolioUiState(
    val accountId: String? = null,
    val sheet: PortfolioSheet = PortfolioSheet(emptyMap()),
    val prices: Map<String, Double> = emptyMap(),               // overlay из PriceFlowManager
    val priceChangePercents: Map<String, Double> = emptyMap(),  // tick-to-tick
    val totalValue: Double = 0.0,
    val balance: Double = 0.0,  // устарело, но поле пока используется (см. ниже)
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val isApiInitialized: Boolean = false,
    val sandboxMode: Boolean = false,
    val tradingStatuses: Map<String, TradingAvailability> = emptyMap(),
)
// positions: List<PortfolioPosition> — УДАЛИТЬ.
// balance — TODO: устарело, выяснить при ревью (либо удалить, либо source — AccountBalance RUB total).
~~~

**`presentation/screens/portfolio/PortfolioViewModel.kt`**

- `loadPortfolio`:
  - `allPositions: MutableList<PortfolioPosition>` → `seeded: MutableMap<AccountKey, AccountPortfolio>`.
  - per-account fetch → `seeded[key] = AccountPortfolio(accountId, brokerName, positions = r.data, balances = emptyList())`.
  - `_uiState.update { it.copy(sheet = PortfolioSheet(seeded), isLoading = false) }`.
  - `keys = seeded.keys` (или `accounts.map { AccountKey(TINVEST, it.id) }` — эквивалентно).
- `startPositionUpdates` — без изменений (уже на `sheet`).
- `syncPriceInterest` — `_uiState.value.sheet.accounts.values.flatMap { it.positions }.map { it.instrument.tscalpInstrumentId }`.
- `updatePriceFromStream` — обновляет `_uiState.prices` / `_uiState.priceChangePercents` через `_uiState.update`. Не пересоздаёт sheet.
- `recomputePositions` — **удалить**.
- `Position.toUiPosition` — **удалить**.
- `updateTradingStatuses(positions: List<Position>)` — принимает `List<Position>`. `ids = posList.map { it.instrument.tscalpInstrumentId }`.

**`presentation/screens/portfolio/PortfolioScreen.kt`**

- Удалить `uiState.positions.groupBy { ... }`.
- Итерировать `uiState.sheet.accounts` напрямую:
  ~~~kotlin
  val allKeys = uiState.sheet.accounts.keys.sortedWith(
      compareBy({ it.brokerName.ordinal }, { it.accountId }),
  )
  for (key in allKeys) {
      val account = uiState.sheet.accounts[key]!!
      AccountHeaderCard(...)
      if (account.positions.isEmpty()) { Text("Нет позиций") }
      else {
          account.positions.forEach { position ->
              val id = position.instrument.tscalpInstrumentId
              AssetPositionCard(
                  position = position,
                  displayCurrentPrice = uiState.prices[id],
                  priceChangePercent = uiState.priceChangePercents[id],
                  tradingAvailability = uiState.tradingStatuses[id] ?: TradingAvailability.UNKNOWN,
              )
          }
      }
      HorizontalDivider(...)
  }
  ~~~

### Presentation — Orders

**`presentation/screens/orders/OrdersUiState.kt`**

~~~kotlin
// Было
val portfolioPositions: List<PortfolioPosition> = emptyList(),
// Стало
val portfolioPositions: List<Position> = emptyList(),
~~~

**`presentation/screens/orders/OrdersViewModel.kt`**

- `updatePositionsFromSheet`:
  ~~~kotlin
  val positions = accountPortfolio.positions  // без adapter
  _uiState.update { state -> state.copy(portfolioPositions = positions) }
  ~~~
- Удалить импорт `toLegacyPortfolioPosition`.

**`presentation/screens/orders/OrdersScreen.kt`**

- 2 callsites `emptyPortfolioPosition(instrument, currentPrice, moneyPerPointOfPrice, instrumentType)`:
  ~~~kotlin
  // Было
  emptyPortfolioPosition(
      instrument = instrument,
      currentPrice = currentPrice,
      moneyPerPointOfPrice = uiState.currentPointValue,
      instrumentType = uiState.selectedInstrument?.instrumentType ?: InstrumentType.OTHER,
  )
  // Стало
  emptyPosition(
      instrument = instrument,
      currentPrice = currentPrice,
      accountId = uiState.selectedAccountId ?: "",
      brokerName = BrokerName.TINVEST,
  )
  ~~~
- `portfolioPos.copy(currentPrice = ..., totalValue = ...)` → **не копировать**. Вместо этого:
  ~~~kotlin
  val position = portfolioPos ?: emptyPosition(...)
  AssetPositionCard(
      position = position,
      displayCurrentPrice = currentPrice,  // свежий тик
      priceChangePercent = uiState.selectedPriceChangePercent,
      ...
  )
  ~~~
- Параметры `instrumentType` / `pointValue` в `AssetPositionCard` — **не передаются** (читаются из `position.instrument` внутри).

### Presentation — `AssetPositionCard`

**`presentation/components/AssetPositionCard.kt`**

Сигнатуры:

~~~kotlin
fun AssetPositionCard(
    position: Position,
    modifier: Modifier = Modifier,
    displayCurrentPrice: Double? = null,
    priceChangePercent: Double? = null,
    onDelete: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    resetSwipe: Boolean = false,
    tradingAvailability: TradingAvailability? = null,
) { ... }

private fun AssetCardContent(
    position: Position,
    displayCurrentPrice: Double?,
    priceChangePercent: Double?,
    onClick: (() -> Unit)?,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    tradingAvailability: TradingAvailability? = null,
) { ... }
~~~

Внутри:
- `val effectivePrice = displayCurrentPrice ?: position.currentPrice`.
- `previousPrice` / `LaunchedEffect` — от `effectivePrice`.
- `position.instrument.instrumentType` — вместо параметра `instrumentType`.
- `position.instrument.moneyPerPointOfPrice` — вместо параметра `pointValue`.
- `position.instrument.name`, `.ticker`, `.isin`, `.classCode`, `.currency` — через `position.instrument`.
- `position.lockedQuantity` — для «В блоке: N».
- `position.quantity` (Double) — форматирование в `PositionFormatter`.
- `PositionFormatter.formatAverage(quantity = position.quantity, averagePrice = position.averagePrice, currency = position.instrument.currency, instrumentType = position.instrument.instrumentType)`.
- `PositionFormatter.formatProfit(profit = position.profit, profitPercent = position.profitPercent, instrumentType = position.instrument.instrumentType, pointValue = position.instrument.moneyPerPointOfPrice)`.
- `PositionFormatter.formatCurrentPrice(price = effectivePrice, instrumentType = position.instrument.instrumentType, pointValue = position.instrument.moneyPerPointOfPrice)`.

Preview (3 шт) — `SharePosition(instrument = Share(...), ...)` / `FuturePosition(...)` / etc. `displayCurrentPrice = null` (используется `position.currentPrice`).

### Presentation — `PositionFormatter`

**`presentation/util/PositionFormatter.kt`**

~~~kotlin
object PositionFormatter {
    fun formatAverage(
        quantity: Double,
        averagePrice: Double?,
        currency: String?,
        instrumentType: InstrumentType,
    ): Pair<String?, String?> { ... }

    fun formatProfit(
        profit: Double?,
        profitPercent: Double?,
        instrumentType: InstrumentType,
        pointValue: Double?,
    ): Triple<String?, Color, String?> { ... }

    fun formatCurrentPrice(
        price: Double,
        instrumentType: InstrumentType,
        pointValue: Double?,
    ): Pair<String?, String?> { ... }

    fun formatQuantity(quantity: Double): String =
        if (quantity == quantity.toLong().toDouble()) quantity.toLong().toString() else "%.1f".format(quantity)

    fun priceChangeColor(priceChangePercent: Double?): Color { ... }  // без изменений
}
~~~

Импорт `PortfolioPosition` — удалить.

### Tests

**`presentation/util/PositionFormatterTest.kt`** — переписать (22 теста):

- Хелпер `position(...)` — удалить. В тестах — чистые примитивы: `formatAverage(quantity = 5.0, averagePrice = 100.0, currency = "rub", instrumentType = SHARE)`.
- Логика тестов — та же (проверяют форматирование), аргументы — примитивы.
- Добавить тесты на `formatQuantity`: `10.0 → "10"`, `10.5 → "10.5"`.

**`domain/models/CalculateProfitPercentTest.kt`** — **удалить** (функция удаляется; логика в `Position.profitPercent` getter, покрыта `PortfolioConvertersTest` / `Position` getters).

**`domain/models/PortfolioConvertersTest.kt`** — дополнить `emptyPosition`:
- 7 кейсов (каждый subtype + accountId).
- Проверить: `quantity = 0.0`, `averagePrice = null`, `profit = null`, `lockedQuantity` пробрасывается, `accountId` пробрасывается.

---

## 4. Порядок работ (коммиты)

Ветка: `feature/h5-clean-final` от `dev`.

1. `refactor(domain): PortfolioRepository -> List<Position>`
   - `PortfolioRepository.fetchPositionsResult → AppResult<List<Position>>`.
   - `PortfolioRepositoryImpl` — без adapter.
   - Гейт.
2. `refactor(domain): emptyPosition instead of emptyPortfolioPosition`
   - `emptyPosition(instrument, currentPrice, accountId, brokerName, lockedQuantity)` в `Portfolio.kt`.
   - `OrdersScreen`: 2 callsites → `emptyPosition`.
   - Гейт.
3. `refactor(presentation): PortfolioViewModel on sheet (no uiState.positions)`
   - `PortfolioUiState`: удалить `positions`; +`prices` / +`priceChangePercents`.
   - `PortfolioViewModel`: seed sheet, `syncPriceInterest` из sheet, `updatePriceFromStream` только overlay, `recomputePositions` / `toUiPosition` — удалить.
   - `PortfolioScreen`: `sheet.accounts` напрямую.
   - Гейт.
4. `refactor(presentation): OrdersUiState/OrdersViewModel on Position`
   - `OrdersUiState.portfolioPositions: List<Position>`.
   - `OrdersViewModel.updatePositionsFromSheet` — без adapter.
   - `OrdersScreen` — `AssetPositionCard(position = ..., displayCurrentPrice = ..., priceChangePercent = ...)`.
   - Гейт.
5. `refactor(presentation): AssetPositionCard(Position) + PositionFormatter pure functions`
   - `AssetPositionCard(position: Position, displayCurrentPrice, priceChangePercent)`.
   - `PositionFormatter` — чистые функции.
   - `PositionFormatterTest` — переписать.
   - Гейт.
6. `cleanup: remove PortfolioPosition + CalculateProfitPercentTest`
   - Удалить `PortfolioPosition`, `calculateProfitPercent`, `toLegacyPortfolioPosition`, `emptyPortfolioPosition`.
   - Удалить `CalculateProfitPercentTest`.
   - Гейт.
7. `docs(clean-audit, roadmap): H5 fully closed`
   - `docs/clean-audit.md` → Н5 → `исправлено` (перенос в `### Исправленные (L3)`).
   - `docs/ROADMAP.md` → `### ✅ H5: PortfolioPosition removed` (перенос в «Закрытые»).
   - Гейт не нужен (docs-only, 5a).

---

## 5. Что НЕ делать

- **Не создавать `PortfolioPositionUi`** — лишняя сущность, отклонено.
- **Не менять `Position` / `PortfolioSheet` / `AccountPortfolio` / `AccountBalance`** — это готовая domain-модель.
- **Не менять `PositionFlowManager`** — уже эмитит `sheet`.
- **Не менять `Instrument` / subtype** — стабильны.
- **Не менять `OrdersViewModel.postOrder` / `CalculateTradeDetailsUseCase`** — не связаны.
- **Не удалять `Position.profitPercent` getter** — это domain-логика.
- **Не добавлять `priceChangePercent` в `Position`** — это DTO-поле брокера (задача `### Position: источники priceChangePercent`); tick-to-tick — presentation overlay.
- **UI-санкция** (правило 1 AGENTS): правки `PortfolioScreen`, `OrdersScreen`, `AssetPositionCard` (визуальные) — согласовать с пользователем до старта.
- **Не «оптимизировать» без нужды** — только то, что в плане.

---

## 6. Критерий готовности

- `grep -rn "PortfolioPosition" app/src` — **пусто** (кроме исторического KDoc в `docs/`).
- `PortfolioRepository.fetchPositionsResult → AppResult<List<Position>>`.
- `PortfolioUiState.positions` — удалён.
- `OrdersUiState.portfolioPositions: List<Position>`.
- `AssetPositionCard(position: Position, displayCurrentPrice: Double?, priceChangePercent: Double?)`.
- `PositionFormatter` — нет параметров `PortfolioPosition` / `Position`.
- `calculateProfitPercent` / `CalculateProfitPercentTest` / `emptyPortfolioPosition` / `toLegacyPortfolioPosition` — удалены.
- `PositionFormatterTest` — переписан (22 теста).
- `PortfolioConvertersTest` — +7 кейсов `emptyPosition`.
- `clean-audit` Н5 → `исправлено`.
- `ROADMAP` H5 → `### ✅ H5`.
- Гейт зелёный.
- Live-тест (UI): карточка портфеля, карточка заявки (2 счёта с SBER), «Пополнить» в sandbox, `lockedQuantity`.

---

## 7. Оценка

| Блок | Оценка |
|---|---|
| Domain (`PortfolioRepository` + `emptyPosition` + удаление legacy) | 1–1.5 ч |
| Presentation: Portfolio (UiState / VM / Screen) | 1.5–2 ч |
| Presentation: Orders (UiState / VM / Screen) | 1 ч |
| `AssetPositionCard` + `PositionFormatter` | 1 ч |
| `PositionFormatterTest` (переписать 22) + `PortfolioConvertersTest` (+7) | 1 ч |
| Гейт + PR-цикл + live-тест | 30 мин |
| **Итого** | **~5.5–6.5 ч** |

---

## 8. Открытые вопросы (перед стартом)

1. **`PortfolioUiState.balance`** — устаревшее поле (никогда не заполнялось). Удалить при миграции или оставить (с plan на `AccountBalance RUB total`)? **Решение:** удалить, если не используется в `PortfolioScreen`. Проверить grep.
2. **`PortfolioUiState.totalValue`** — сумма `Position.totalValue` по всем счетам. Используется в UI? Проверить grep. Если не используется — удалить.
3. **`OrdersScreen`: `uiState.currentPointValue` / `pairedPointValue`** — после удаления параметра `pointValue` в `AssetPositionCard` эти поля могут стать ненужными. Но они могут использоваться в `OrdersViewModel` для расчётов (`updateTradeDetails`, оверлеи). Проверить, **не удалять в этой задаче** (out of scope), только убрать передачу в `AssetPositionCard`.
4. **`emptyPosition` при `accountId == ""`** — `OrdersScreen` до выбора счёта. `Position.accountId` — non-null `String`. `""` — допустимый fallback. Если `Position`-модель когда-нибудь получит non-empty invariant — отдельная задача.


---

## 9. После закрытия H5

H5 — не последний шаг. После merge PR с удалением `PortfolioPosition` **обязательно** закрыть задачу:

**`### Ревизия ссылок на PortfolioPosition после H5`** (`docs/ROADMAP.md`, сразу после H5).

Это формальное предупреждение следующей сессии: после H5 в `docs/` останутся устаревшие ссылки и формулировки. Задача — 30–45 мин, docs-only.

**Что нужно отревизовать (кратко; подробности — в самой задаче):**

1. **`### UI: отображение blocked на валютной карточке`** (ROADMAP) — после `Portfolio: модель портфеля` деньги (`money`/`blocked`) переехали в `AccountBalance`, валютная карточка `RUB000UTSTOM` удалена с `PortfolioScreen`, баланс — в `AccountHeaderCard`. Задача либо **закрывается как выполненная**, либо переформулируется под остаток.

2. **`### P&L: client-side fallback для PortfolioPosition`** (ROADMAP) — все ссылки на `PortfolioPosition` устареют. Fallback теперь применяется к `Position` / `PositionStreamItem`. Требуется переименовать задачу (`... для Position`) и обновить тело.

3. **`docs/architecture.md` → «Data flow: позиции»** — упоминает `PortfolioPosition` как legacy. После H5 — устареет; `Position` — единственная модель. Обновить «Модели» / «Legacy `flow`» / «ViewModel».

4. **`docs/clean-audit.md` → Н5** — статус → `исправлено`, запись переносится в `### Исправленные (L3)`. Проверить связанные упоминания.

5. **KDoc в коде** — после H5 `grep -rn "PortfolioPosition" app/src` = 0 (это критерий H5), но KDoc-ссылки на удалённые функции (`toPortfolioPosition`, `mergeWith`) в других файлах могут остаться. Проверить `grep -rn "toPortfolioPosition\|mergeWith\|PortfolioPosition" app/src` и почистить.

**Критерий завершения ревизии:**

- `grep -rn "PortfolioPosition" docs/ROADMAP.md docs/architecture.md` — пусто (кроме записей в `### Исправленные` / исторических).
- `grep -rn "PortfolioPosition" app/src` — пусто.
- `docs/clean-audit.md` Н5 → `исправлено`.

**Не забыть:** без этой ревизии следующая сессия будет введена в заблуждение устаревшими ссылками — потенциальный источник ошибок в задачах `P&L: client-side fallback` и `UI: отображение blocked`.
