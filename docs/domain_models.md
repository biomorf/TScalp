# Domain models

Справочник и мастер-план доменных моделей TScalp.
Читается вместе с `AGENTS.md` (правила) и `docs/architecture.md` (слои).

**Статус:** мастер-план в работе. Модели проектируются по одной; первая — `Instruments` (центральная).

**Принцип:** сначала проектируем модель, потом код. На этапе проектирования этот документ — план; после реализации — справочник действующих моделей.

---

## Instruments

### 1. Роль модели

`Instruments` — **«паспорт инструмента»**. Это центральная модель: от неё зависят `Portfolio`, `Orders`, `Positions` и все операции с брокером.

**Жизненный цикл:**
1. **Заполняется** при поиске (`findInstruments`) или полной загрузке (`fetchFullInstrument`).
2. **Используется** как вход для всех последующих операций: подписка на цены, заявки, проверка доступности, портфель.
3. **Транслирует** `tscalpInstrumentId` ↔ broker-specific-id (uid / figi / ticker+classCode / ticker+mic).

**Следствия (проектные ограничения):**
- В модели должны быть **все** broker-specific идентификаторы, нужные хоть для одной операции. Не только order-route. Иначе каждый call-site будет трансформировать сам.
- Domain везде ходит через `tscalpInstrumentId`. Data на границе с брокером достаёт нужный ID из модели. Единая точка трансляции.
- Модель должна быть **пригодна для операций после search**. Если search вернул «короткий» вариант (BCS info API, Finam `Assets`, T-Invest `FindInstrument`) — данные дообогащаются (`GetAsset` / `getInstrumentBy`). Это N+1 — известный компромисс (см. `docs/ISSUES.md` → «T-Invest REST findInstruments fallback (N+1 unary)»).
- Все broker-specific поля, которые могут отсутствовать у части брокеров, — **nullable** (`String?`, `Long?`, `Double?`). Обязательных — минимум (core identity).

### 2. Терминология (переименования)

Здесь зафиксированы решения по именам. Причина — семантическая путаница в текущем коде.

| Было | Стало | Что означает | Почему |
|---|---|---|---|
| `pointValue` (computed) | `moneyPerPointOfPrice` | Сколько денег в одной единице цены: `priceStepCost / minPriceIncrement`. Для акций = 1.0, для фьючерсов — множитель для P&L. | «Пункт» в трейдинге = шаг цены. «Стоимость пункта» — это `priceStepCost`. А `pointValue` — рублей за единицу цены. Неинтуитивно. |
| `minPriceIncrementAmount` | `priceStepCost` | Стоимость шага цены в валюте (например, 10 ₽ за шаг 0.01). | Прямее: «cost of price step». Не путать с шагом. |
| `minPriceIncrement` | `minPriceIncrement` | Шаг цены (например, 0.01). | Интуитивно, менять не нужно. |
| `ttech_uid` | *(удалить)* | Было: `instrument_uid` из T-Invest. Равно `tscalpInstrumentId`. | Дубль. Одно значение — одно поле. |
| `ttech_figi` | `figi` | FIGI — международный идентификатор (Bloomberg). Не T-Invest-специфичный: BCS в позициях тоже даёт figi. | `ttech_` — ложная атрибуция. |

**Формула P&L фьючерса:** `(currentPrice - avgPrice) × quantity × moneyPerPointOfPrice`.
Эквивалентно: `((currentPrice - avgPrice) / minPriceIncrement) × priceStepCost × quantity`.

### 3. Core (обязательные, 5)

Без них инструмент не идентифицируется. Все — non-null.

| Поле | T-Invest | BCS | Finam |
|---|---|---|---|
| `tscalpInstrumentId: String` | `uid` | `figi` | `id` |
| `ticker: String` | `ticker` | `ticker` | `ticker` |
| `isin: String` | `isin` | `isin` | `isin` |
| `name: String` | `name` | `displayName` | `name` |
| `instrumentType: InstrumentType` | `instrumentType` (маппинг) | `type` (= `instrumentType`) | `type` |

`isin` — общий у всех трёх брокеров (подтверждено документацией BCS и Finam). Не nullable.

### 4. Common optional (на интерфейсе, nullable)

Есть в модели у всех, но заполняется только если брокер даёт.

**Идентификаторы и параметры:**

| Поле | T-Invest | BCS | Finam | Примечание |
|---|---|---|---|---|
| `classCode: String?` | `classCode` | `boards[0].classCode` | — | Нужен BCS для order-route |
| `exchange: String?` | `exchange` | `boards[0].exchange` | `mic` | Значения не канонизируем («MOEX» vs «MISX») |
| `lot: Long?` | `lot: Int` | `lotSize: double` | `lot_size: Decimal` | Лот целый, `toLong()` без потерь |
| `currency: String?` | `currency` | `tradingCurrency` | `quote_currency` | Валюта цены |
| `settlementCurrency: String?` | — | `settlementCurrency` | `bond_details.currency` | Валюта расчётов (Bond, BCS) |
| `figi: String?` | `figi` | `figi` (из позиций) | — | Международный идентификатор |
| `priceStepCost: Double?` | `minPriceIncrementAmount` | `stepPrice` | compute `min_step/10^decimals × contract_size` | Стоимость шага цены |
| `minPriceIncrement: Double?` | `minPriceIncrement` | `minimumStep` | `min_step / 10^decimals` | Шаг цены |
| `expirationDate: Instant?` | `expirationDate` (Future) | `maturityDate` (общее) | `future_details.expiration_date` | Только futures (у T-Invest), общее у BCS/Finam |

**Флаги (T-Invest-специфичные, но лежат в common nullable — BCS/Finam могут не заполнять):**
- `apiTradeAvailableFlag: Boolean?`
- `buyAvailableFlag: Boolean?`
- `sellAvailableFlag: Boolean?`
- `shortEnabledFlag: Boolean?`
- `forIisFlag: Boolean?`
- `forQualInvestorFlag: Boolean?`
- `weekendFlag: Boolean?`
- `blockedTcaFlag: Boolean?`

**Производные (computed):**
- `moneyPerPointOfPrice: Double` — extension `val InstrumentUi.moneyPerPointOfPrice: Double get() = priceStepCost?.let { amount -> minPriceIncrement?.takeIf { it > 0.0 }?.let { inc -> amount / inc } } ?: 1.0`. **Не поле** — SSoT, ноль дублирования.

### 5. Subtype-specific (Этап 2, A)

Поля, применимые только к одному типу инструмента. В C-min — `null` / не используются. В A — переезжают в свой `data class`.

**`Share`:**
- `ipoDate: Instant?`, `issueSize: Long?`, `issueSizePlan: Long?`, `nominal: Double?`, `divYieldFlag: Boolean?`, `shareType: String?`, `liquidityFlag: Boolean?`, `assetUid: String?`

**`Future`:**
- `firstTradeDate: Instant?`, `lastTradeDate: Instant?`, `futuresType: String?`, `assetType: String?`, `basicAsset: String?`, `basicAssetSize: Double?`, `positionUid: String?`, `basicAssetPositionUid: String?`

**`Bond`:**
- `faceValue: Double?`, `couponRate: Double?`, `nextCoupon: Instant?`, `couponsPerYear: Long?`, `accruedInt: Double?`, `creditRating: String?`

**`Option`:**
- `strike: Double?`

**`Etf`, `Currency`, `Other`:** только core + common.

### 6. Вне модели (Этап 2, A; отдельная задача)

**Margin / risk — account-specific.** У T-Invest — свойство инструмента (`initialMarginOnBuy/Sell`, `dlong/dshort`, `dlongMin/dshortMin`, `klong/kshort`). У Finam — `GetAssetParams` с `account_id` (per-account). Это **не свойство инструмента**, а «доступность в этом счёте».

**Решение:** вынести в `TradeCheckResult` / `MarginInfo` (отдельная задача).
**Статус:** вернуться **2026-11-02**. Важно, не забыть.

**Также вне модели:** `tradingStatus` (режим торгов — это не свойство инструмента, а состояние торговой сессии), `initialMargin*`, `dlong*`.

### 7. Этап 1 (C-min) — что делаем в коде

**Цель:** снять 9 кастов `as? FutureUi`, убрать 3 неоднозначных/дублирующих поля, добавить 3 отсутствующих. Модель остаётся `open class` + 2 `data class` — без sealed interface.

**2c:** соответствует Clean (поля-изменения в domain; мапперы в data; dependency rule не нарушен).

**Шаги (11):**

1. **Удалить `ttech_uid`** — `InstrumentUi`, `FutureUi`, `ShareUi`, `mapProtoToDomain`, `toInstrumentUi`. Заменить на `tscalpInstrumentId` там, где использовалось (= `uid`).
2. **`ttech_figi` → `figi: String? = null`** — `InstrumentUi`, `FutureUi`, `ShareUi`, мапперы.
3. **`classCode: String` → `String? = null`** — `InstrumentUi`, мапперы (`?: ""` → `null`).
4. **`currency: String` → `String? = null`** — `InstrumentUi`, мапперы.
5. **`lot: Int` → `Long? = null`** — `InstrumentUi`, мапперы (`lot.toLong()`), `CalculateTradeDetailsUseCase`.
6. **`+ settlementCurrency: String? = null`** — `InstrumentUi`, мапперы.
7. **`+ moneyPerPointOfPrice` (extension)** — в `Instruments.kt`, computed из `priceStepCost`/`minPriceIncrement`; `FutureUi.pointValue` — **удалить** (заменён extension).
8. **`expirationDate: Instant?` → в `InstrumentUi`** (было только `FutureUi`); `FutureUi`/`ShareUi` — убрать `override` (наследуется).
9. **Удалить 9 кастов `as? FutureUi`** — заменить на extension/поле:
   - `TInvestPortfolioService:64` — `instrumentUi?.moneyPerPointOfPrice` (extension)
   - `CalculateTradeDetailsUseCase:48,78` — `instrument.moneyPerPointOfPrice` (extension)
   - `OrdersViewModel:201,215,403,915` — `instrument?.moneyPerPointOfPrice` (extension)
   - `SearchCacheImpl:52` — `it.expirationDate`
10. **`instrumentType: String` → `enum InstrumentType`** — `Instruments.kt` (новый enum), `Portfolio.kt` (2 модели), все `== "..."` и `when`, мапперы (маппинг строки в enum).
11. **Переименовать `pointValue` → `moneyPerPointOfPrice`** в `Portfolio.kt` (`PositionStreamItem`, `PortfolioPosition`) — это **снимки**, не computed.

**Enum `InstrumentType` (значения):**
`SHARE`, `FOREIGN_SHARE`, `BOND`, `NOTE`, `EURO_BOND`, `DEPOSITARY_RECEIPT`, `ETF`, `MUTUAL_FUND`, `FUTURES`, `OPTION`, `CURRENCY`, `COMMODITY`, `INDEX`, `OTHER`.

**Маппинг брокерских значений → enum:**
- T-Invest: `"share"` → `SHARE`, `"bond"` → `BOND`, `"etf"` → `ETF`, `"currency"` → `CURRENCY`, `"futures"` → `FUTURES`, `"option"` → `OPTION`, `"fond"` → `MUTUAL_FUND`, остальное → `OTHER`.
- BCS: `STOCK` → `SHARE`, `FOREIGN_STOCK` → `FOREIGN_SHARE`, `BONDS` → `BOND`, `NOTES` → `NOTE`, `DEPOSITARY_RECEIPTS` → `DEPOSITARY_RECEIPT`, `EURO_BONDS` → `EURO_BOND`, `MUTUAL_FUNDS` → `MUTUAL_FUND`, `ETF` → `ETF`, `FUTURES` → `FUTURES`, `OPTIONS` → `OPTION`, `CURRENCY` → `CURRENCY`, `GOODS` → `COMMODITY`, `INDICES` → `INDEX`, остальное → `OTHER`.
- Finam: `STOCK` → `SHARE`, `BOND` → `BOND`, `FUTURES` → `FUTURES`, остальное — по `type`.

**Call-sites (сводно):**
- `InstrumentUi`, `FutureUi`, `ShareUi` (`Instruments.kt`)
- `PositionStreamItem`, `PortfolioPosition` (`Portfolio.kt`)
- `TInvestInstrumentMapper`, `TInvestRestMappers`, `TInvestPositionsWsMapper`, `TInvestPositionsService`, `TInvestPortfolioService`, `TInvestAccountsService`, `TInvestInstrumentsService` (data)
- `PositionFlowManager` (domain)
- `CalculateTradeDetailsUseCase` (domain)
- `SearchCacheImpl` (data)
- `OrdersViewModel` (presentation)
- `AssetPositionCard`, `PortfolioScreen`, `OrdersScreen` (presentation)
- `PositionFormatter` (presentation)
- `CalculateTradeDetailsUseCaseTest`, другие тесты.

**Оценка:** ~1.5–2 ч.

**Критерий готовности:** `grep "as? FutureUi"` = 0; `grep "ttech_uid\|ttech_figi\|minPriceIncrementAmount\|pointValue"` в main = 0; `instrumentType` — enum везде; гейт зелёный.

### 8. Этап 2 (A) — sealed interface

**Цель:** ISP/LSP, exhaustive `when`, subtype-specific поля — только в своих типах.

**Шаги:**

1. `sealed interface Instrument` (core 5 + common 9 + флаги).
2. `data class Share`, `Future`, `Bond`, `Etf`, `Currency`, `Option`, `Other` — с subtype-specific.
3. `InstrumentUi` / `FutureUi` / `ShareUi` — удалить.
4. `mapProtoToDomain` (146 строк) → 6 мапперов + диспетчер.
5. `toInstrumentUi` (4 функции) → 6 функций.
6. `SearchCacheImpl` сортировка — `when (instrument)` вместо `as? FutureUi`.
7. Margin/risk — вынести в `TradeCheckResult` (отдельная задача, `2026-11-02`).
8. Все `when (instrumentType)` → `when (instrument)` — exhaustive.

**Оценка:** ~5–6 ч.

**Триггер:** начало реализации BCS Broker API или Finam Broker API. Сейчас `userInstruments = null` у обоих — модель не нагружена.

### 9. Сравнительные таблицы

#### Акции

| Domain | T-Invest | BCS | Finam |
|---|---|---|---|
| `tscalpInstrumentId` | `uid` | `figi` | `id` |
| `ticker` | `ticker` | `ticker` | `ticker` |
| `isin` | `isin` | `isin` | `isin` |
| `name` | `name` | `displayName` | `name` |
| `instrumentType` | `"share"` | `STOCK` | `STOCK` |
| `classCode` | `classCode` | `boards[0].classCode` | — |
| `exchange` | `exchange` | `boards[0].exchange` | `mic` |
| `lot` | `lot: Int` | `lotSize: double` | `lot_size: Decimal` (только `GetAsset`) |
| `currency` | `currency` | `tradingCurrency` | `quote_currency` |
| `settlementCurrency` | — | `settlementCurrency` | — |
| `figi` | `figi` | `figi` (из позиций) | — |
| `priceStepCost` | — | `stepPrice` | — |
| `minPriceIncrement` | `minPriceIncrement` | `minimumStep` | `min_step / 10^decimals` |
| `moneyPerPointOfPrice` | computed = 1.0 | computed = 1.0 | computed = 1.0 |

**Источники (акции):**
- T-Invest: `ru.tinkoff.piapi.contract.v1.Share` (proto), SDK `kotlin-sdk-grpc-contract`.
- BCS: https://trade-api.bcs.ru/http/information (get-instruments-by-isins, by-type, by-tickers).
- Finam: https://api.finam.ru/docs/grpc/#allassets (Assets, GetAsset).

#### Фьючерсы

| Domain | T-Invest | BCS | Finam |
|---|---|---|---|
| `tscalpInstrumentId` | `uid` | `figi` | `id` |
| `ticker` | `ticker` | `ticker` | `ticker` |
| `isin` | `isin` | `isin` | `isin` |
| `name` | `name` | `displayName` | `name` |
| `instrumentType` | `"futures"` | `FUTURES` | `FUTURES` |
| `classCode` | `classCode` | `boards[0].classCode` | — |
| `exchange` | `exchange` | `boards[0].exchange` | `mic` |
| `lot` | `lot: Int` | `lotSize: double` | `lot_size: Decimal` |
| `currency` | `currency` | `tradingCurrency` | `quote_currency` |
| `figi` | `figi` | `figi` | — |
| `priceStepCost` | `minPriceIncrementAmount` | `stepPrice` | compute: `min_step/10^decimals × contract_size` |
| `minPriceIncrement` | `minPriceIncrement` | `minimumStep` | `min_step / 10^decimals` |
| `moneyPerPointOfPrice` | computed | computed | computed |
| `expirationDate` | `expirationDate` | `maturityDate` | `future_details.expiration_date` |
| `firstTradeDate` | `firstTradeDate` | — | — |
| `lastTradeDate` | `lastTradeDate` | — | — |
| `futuresType` | `futuresType` | `baseAssetFuture` (?) | — |
| `assetType` | `assetType` | — | — |
| `basicAsset` | `basicAsset` | `baseAsset` | — |
| `basicAssetSize` | `basicAssetSize` | — | `future_details.contract_size` |
| `positionUid` | `positionUid` | — | — |

**Источники (фьючерсы):**
- T-Invest: `ru.tinkoff.piapi.contract.v1.Future` (proto), `GetFuturesMargin`.
- BCS: https://trade-api.bcs.ru/http/information (get-instruments-by-isins, by-type).
- Finam: https://api.finam.ru/docs/grpc/#allassets (`future_details`).

#### Заявки (order-route)

| Broker | Формат order-ID | Строится из |
|---|---|---|
| T-Invest | `uid` (один) | `tscalpInstrumentId` |
| BCS | `ticker` + `classCode` (два) | `ticker` + `classCode!!` |
| Finam | `symbol = ticker@mic` (один, составной) | `ticker` + `exchange!!` (= mic) |

**Валидация в data:**
- BCS `placeOrder`: `classCode == null` → `AppResult.Failure("BCS instrument without classCode")`.
- Finam `placeOrder`: `exchange == null` → `AppResult.Failure("Finam instrument without mic")`.

**Источники (заявки):**
- T-Invest: `ru.tinkoff.piapi.contract.v1.PostOrderRequest` (proto).
- BCS: https://trade-api.bcs.ru/http/operations/sync/create-order-sync (`ticker` + `classCode`).
- Finam: https://api.finam.ru/docs/grpc/#placeorder (`symbol` = `ticker@mic`).

### 10. Открытые вопросы

| # | Вопрос | Где | Когда |
|---|---|---|---|
| 1 | `priceStepCost` для Finam futures — формула `min_step/10^decimals × contract_size` требует live-подтверждения. Пока — `null` fallback. | `TInvestRestMappers` / Finam mapper | При реализации Finam |
| 2 | `lot: double → Long` — BCS `lotSize: double`, Finam `Decimal`. Лот целый, `toLong()` без потерь. | `lot` | C-min |
| 3 | `name` BCS = `displayName` (полное) или `shortName` (краткое)? D8 = `displayName`. `shortName` — в `BcsSpecific`, если понадобится. | `name` | BCS mapping |
| 4 | `settlementCurrency` — реально ли нужно отдельное поле, или `currency` достаточно? | `settlementCurrency` | BCS mapping |
| 5 | `exchange` values — «MOEX» (T-Invest/BCS) vs «MISX» (Finam). Не канонизируем. | `exchange` | — |
| 6 | **Margin/risk → `TradeCheckResult`** (отдельная задача). | `initialMargin*`, `dlong*` | **2026-11-02** |
| 7 | `InstrumentType` enum — точный маппинг T-Invest proto (`SHARE`/`BOND`/`ETF`/`FUTURES`/...) и Finam `type` (по документации). | `InstrumentType` | C-min |
| 8 | `PositionFlowManager:234,237` — склейка валютных позиций по `currency` (и `instrumentType == "currency"`). При переименовании в enum — `InstrumentType.CURRENCY`. | `PositionFlowManager` | C-min |

### 11. Связанные документы

- `docs/architecture.md` — слои, dependency rule.
- `docs/clean-audit.md` — Н5 (`PortfolioPosition` — presentation-поля), Н6 (`InstrumentUi` — god-класс), Н7 (`ttech_figi` — временное).
- `docs/ROADMAP.md` — задачи реализации.
- `docs/brokers.md` — справка по брокерам.
- `docs/ISSUES.md` — известные проблемы (N+1 при search).

---

## Портфель (Portfolio)

*Раздел в проектировании. Открывается после завершения `Instruments`.*

---

## Заявки (Orders)

*Раздел в проектировании. Открывается после `Instruments` и `Portfolio`.*
