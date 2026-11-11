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
| `brokerName: BrokerName` | `TINVEST` (контекст) | `BCS` | `FINAM` |
| `ticker: String` | `ticker` | `ticker` | `ticker` |
| `name: String` | `name` | `displayName` | `name` |
| `instrumentType: InstrumentType` | контекст маппера (см. ниже) | `type` | `type` |

**Почему `isin` не в core.** proto T-Invest (v1.51+): `Share` и `Instrument` имеют `isin`, **`Future` и `Option` — нет**. Если оставить `isin: String` в core, для futures/options пришлось бы писать `""` (ложь). `isin` — common optional (§4).

**Почему `instrumentType` не из proto-поля.** У T-Invest `Share`/`Future`/`Option` **нет общего** `instrument_type` — есть только `Instrument.instrument_type` и `Instrument.instrument_kind` (через `GetInstrumentBy`). Поле заполняется **маппером по контексту**: `Shares()` → `SHARE`, `Futures()` → `FUTURES`, `Options()` → `OPTION`. В Phase 2 (A) тип определяется самим `data class`.

### 4. Common optional (на интерфейсе, nullable)

Есть в модели у всех, но заполняется только если брокер даёт.

**Идентификаторы и параметры:**

| Поле | T-Invest | BCS | Finam | Примечание |
|---|---|---|---|---|
| `classCode: String?` | `classCode` | `boards[0].classCode` | — | Нужен BCS для order-route |
| `isin: String?` | `isin` (Share/Instrument), **нет у Future/Option** | `isin` | `isin` | Common optional: у фьючерсов и опционов T-Invest отсутствует |
| `exchange: String?` | `exchange` | `boards[0].exchange` | `mic` | Значения не канонизируем («MOEX» vs «MISX») |
| `lot: Long?` | `lot: Int` | `lotSize: double` | `lot_size: Decimal` | Лот целый, `toLong()` без потерь |
| `currency: String?` | `currency` | `tradingCurrency` | `quote_currency` | Валюта цены |
| `settlementCurrency: String?` | `settlement_currency` (только Option) | `settlementCurrency` | `bond_details.currency` | Валюта расчётов |
| `figi: String?` | `figi` | `figi` (из позиций) | — | Международный идентификатор |
| `priceStepCost: Double?` | `minPriceIncrementAmount` | `stepPrice` | compute `min_step/10^decimals × contract_size` | Стоимость шага цены |
| `minPriceIncrement: Double?` | `minPriceIncrement` | `minimumStep` | `min_step / 10^decimals` | Шаг цены |
| `expirationDate: Instant?` | `expirationDate` (Future/Option) | `maturityDate` (общее) | `future_details.expiration_date` | T-Invest — только Future/Option (у Share нет), BCS/Finam — общее |

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

**`Share`** (proto T-Invest `Share` + BCS + Finam):
- `ipoDate: Instant?`, `issueSize: Long?`, `issueSizePlan: Long?`, `nominal: Double?`, `divYieldFlag: Boolean?`, `shareType: String?`, `liquidityFlag: Boolean?`

**`Future`** (proto T-Invest `Future` + BCS + Finam):
- `firstTradeDate: Instant?`, `lastTradeDate: Instant?`, `futuresType: String?`, `assetType: String?`, `basicAsset: String?`, `basicAssetSize: Double?`, `basicAssetPositionUid: String?`
- Margin/risk (10, из §6, переносятся из `Instrument` в Phase 2; `### Margin/risk` вынесет их позже в `TradeCheckResult`): `initialMarginOnBuy: Double?`, `initialMarginOnSell: Double?`, `dlong: Double?`, `dshort: Double?`, `dlongMin: Double?`, `dshortMin: Double?`, `dlongClient: Double?`, `dshortClient: Double?`, `klong: Double?`, `kshort: Double?`

**`Bond`** (proto T-Invest `Bond` + BCS `bond_details`):
- `faceValue: Double?`, `couponRate: Double?`, `nextCoupon: Instant?`, `couponsPerYear: Long?`, `accruedInt: Double?`, `creditRating: String?`

**`Option`** (не реализуется сейчас; зафиксировано для полноты по proto T-Invest `Option`):
- `direction: String?` (`PUT`/`CALL`), `paymentType: String?` (`PREMIUM`/`MARGINAL`), `style: String?` (`AMERICAN`/`EUROPEAN`), `settlementType: String?` (`PHYSICAL_DELIVERY`/`CASH_SETTLEMENT`), `strikePrice: Double?`, `firstTradeDate: Instant?`, `lastTradeDate: Instant?`, `expirationDate: Instant?` (последний — уже в common, здесь переопределяется как `non-null` в Phase 2)

**`Etf`, `Currency`, `Other`:** только core + common.

**Удаляем в Phase 2** (нет в §4 common, в main не читаются — YAGNI):
- `first1minCandleDate: Instant?`, `first1dayCandleDate: Instant?` — даты первой свечи, не используются.
- `countryOfRisk: String?`, `countryOfRiskName: String?`, `sector: String?`, `brand: String?` — метаданные эмитента, не используются.

**Явно не включаем в модель** (решения от 2026-11-01):
- `requiredTests: List<String>` — пользователь проходит тесты на сайте брокера; при попытке операции ошибка ловится с сервера. YAGNI.
- `positionUid`, `assetUid` — не нужны текущим операциям.
- `otcFlag` — «ранее для внебиржевых», deprecated по смыслу.
- `realExchange: RealExchange` — реальная площадка расчётов; у нас `exchange` достаточно.
- `klong`, `kshort` — `[deprecated = true]` в proto T-Invest, не тащить.

### 6. Вне модели (Этап 2, A; отдельная задача)

**Margin / risk — account-specific.** У T-Invest — свойство инструмента (`initialMarginOnBuy/Sell`, `dlong/dshort`, `dlongMin/dshortMin`, `klong/kshort`). У Finam — `GetAssetParams` с `account_id` (per-account). Это **не свойство инструмента**, а «доступность в этом счёте».

**Решение:** вынести в `TradeCheckResult` / `MarginInfo` (отдельная задача).
**Статус:** вернуться **2026-11-02**. Важно, не забыть.

**Также вне модели:** `tradingStatus` (режим торгов — это не свойство инструмента, а состояние торговой сессии), `initialMargin*`, `dlong*`.

### 7. Этап 1 (C-min) — чек-лист

**Цель:** снять 9 кастов `as? FutureUi`, убрать 3 неоднозначных/дублирующих поля, добавить 3 отсутствующих, переименовать модель. Модель остаётся `open class` + 2 `data class` — без sealed interface.

**2c:** соответствует Clean (поля-изменения в domain; мапперы в data; dependency rule не нарушен).

**Оценка:** ~3 ч (шаг 0 + 14 шагов + 4 гейта). **Ветка:** `feature/instrument-cmin`, один PR, 4 коммита внутри.

#### Шаг 0. Маппинг `InstrumentType` (read-only)

- [x] **0.1.** Прочитать proto T-Invest `Share`/`Future`/`Option`/`Instrument` (`instrument_type`, `instrument_kind`), BCS `type` (`STOCK`/`FOREIGN_STOCK`/`BONDS`/...), Finam `type` (`STOCK`/`BOND`/`FUTURES`/...).
- [x] **0.2.** Обновить §9 (таблицы): столбец `instrumentType` — маппинг каждого значения.
- [x] **0.3.** Зафиксировать в §10 неоднозначности, если есть (T-Invest `"fond"` vs `"etf"`, BCS `MUTUAL_FUNDS` vs `ETF`, Finam `type` — точный список).
- **Оценка:** 15 мин. **Блокер:** нет.

#### Коммит 1 — переименования (шаги 1–2)

- [x] **1.** Удалить `ttech_uid` — `InstrumentUi`, `FutureUi`, `ShareUi`, `mapProtoToDomain`, `toInstrumentUi`. Заменить на `tscalpInstrumentId` (= `uid`) там, где использовалось.
- [x] **2.** `ttech_figi` → `figi: String? = null` — `InstrumentUi`, `FutureUi`, `ShareUi`, мапперы.
- **Оценка:** 25 мин.

#### Коммит 2 — nullable + новые поля (шаги 3–6)

- [x] **3.** `classCode: String` → `String? = null` — `InstrumentUi`, мапперы (`?: ""` → `null`).
- [x] **4.** `currency: String` → `String? = null` — `InstrumentUi`, мапперы.
- [x] **5.** `lot: Int` → `Long? = null` — `InstrumentUi`, мапперы (`lot.toLong()`), `CalculateTradeDetailsUseCase` (fallback `?: 1L` где нужен non-null).
- [x] **6.** `+ settlementCurrency: String? = null` — `InstrumentUi`, мапперы.
- **Оценка:** 25 мин.

#### Коммит 3 — `moneyPerPointOfPrice` + удаление кастов (шаги 7–9)

- [x] **7.** `+ moneyPerPointOfPrice: Double` — **extension** на `InstrumentUi` в `Instruments.kt`, computed из `priceStepCost`/`minPriceIncrement`. `FutureUi.pointValue` — **удалить** (заменён extension).
- [x] **8.** `expirationDate: Instant?` → в `InstrumentUi` (было только `FutureUi`); `FutureUi`/`ShareUi` — убрать `override` (наследуется).
- [x] **9.** Удалить 8 кастов `as? FutureUi`:
  - `TInvestPortfolioService:64` — `instrumentUi?.moneyPerPointOfPrice` (extension)
  - `CalculateTradeDetailsUseCase:48,78` — `instrument.moneyPerPointOfPrice` (extension)
  - `OrdersViewModel:201,215,403,915` — `instrument?.moneyPerPointOfPrice` (extension)
  - `SearchCacheImpl:52` — `it.expirationDate`
- **Оценка:** 40 мин.

#### Коммит 4 — `instrumentType` enum + rename (шаги 10–14)

- [x] **10.** `instrumentType: String` → `enum InstrumentType` — `Instruments.kt` (новый enum), `Portfolio.kt` (2 модели), `OrderModels.kt` (`Order`, `OrderState`), все `== "..."` и `when`, мапперы (маппинг строки в enum, см. ниже).
- [x] **11.** Переименовать `pointValue` → `moneyPerPointOfPrice` в `Portfolio.kt` (`PositionStreamItem`, `PortfolioPosition`) — это **снимки**, не computed.
- [x] **12.** `InstrumentUi` → `Instrument` (~25 call-sites, alias `import ru.tinkoff.piapi.contract.v1.Instrument as ProtoInstrument` в `TInvestInstrumentMapper`).
- [x] **13.** `FutureUi` → `Future` (~10 call-sites).
- [x] **14.** `ShareUi` → `Share` (~5 call-sites).
- **Оценка:** 65 мин.

#### Enum `InstrumentType`

`SHARE`, `FOREIGN_SHARE`, `BOND`, `NOTE`, `EURO_BOND`, `DEPOSITARY_RECEIPT`, `ETF`, `MUTUAL_FUND`, `FUTURES`, `OPTION`, `CURRENCY`, `COMMODITY`, `INDEX`, `OTHER`.

#### Маппинг брокерских значений → enum

- **T-Invest:** `"share"` → `SHARE`, `"bond"` → `BOND`, `"etf"` → `ETF`, `"currency"` → `CURRENCY`, `"futures"` → `FUTURES`, `"option"` → `OPTION`, `"fond"` → `MUTUAL_FUND`, остальное → `OTHER`.
- **BCS:** `STOCK` → `SHARE`, `FOREIGN_STOCK` → `FOREIGN_SHARE`, `BONDS` → `BOND`, `NOTES` → `NOTE`, `DEPOSITARY_RECEIPTS` → `DEPOSITARY_RECEIPT`, `EURO_BONDS` → `EURO_BOND`, `MUTUAL_FUNDS` → `MUTUAL_FUND`, `ETF` → `ETF`, `FUTURES` → `FUTURES`, `OPTIONS` → `OPTION`, `CURRENCY` → `CURRENCY`, `GOODS` → `COMMODITY`, `INDICES` → `INDEX`, остальное → `OTHER`.
- **Finam:** `STOCK` → `SHARE`, `BOND` → `BOND`, `FUTURES` → `FUTURES`, остальное — по `type` (точный список в шаге 0).

#### Call-sites (сводно)

- `InstrumentUi`, `FutureUi`, `ShareUi` (`Instruments.kt`)
- `PositionStreamItem`, `PortfolioPosition` (`Portfolio.kt`)
- `Order`, `OrderState` (`OrderModels.kt`)
- data: `TInvestInstrumentMapper`, `TInvestRestMappers`, `TInvestPositionsWsMapper`, `TInvestPositionsService`, `TInvestPortfolioService`, `TInvestAccountsService`, `TInvestInstrumentsService`, `TInvestOrdersMapper`
- domain: `PositionFlowManager`, `CalculateTradeDetailsUseCase`
- data: `SearchCacheImpl`
- presentation: `OrdersViewModel`, `PortfolioViewModel`, `AssetPositionCard`, `ConfirmOrderDialog`, `OrderCard`, `OrdersListDialog`, `PortfolioScreen`, `OrdersScreen`, `PositionFormatter`, `CurrencyFormat`
- tests: `CalculateTradeDetailsUseCaseTest`, другие.

#### Критерий готовности

- `grep "as? FutureUi"` в `main` = 0.
- `grep "ttech_uid\|ttech_figi\|minPriceIncrementAmount\|pointValue\|InstrumentUi\|FutureUi\|ShareUi"` в `main` = 0 (кроме исторических KDoc-ссылок в комментариях — проверить глазами).
- `instrumentType` — enum везде.
- Гейт (`testDebugUnitTest / lintDebug / ktlintCheck / detekt`) зелёный на каждом коммите.
- `docs/domain_models.md` §7 — все чек-боксы `[x]` (обновляется в том же PR).

### 8. Этап 2 (A) — sealed interface

**Цель:** ISP/LSP, exhaustive `when`, subtype-specific поля — только в своих типах.

**2c:** соответствует Clean (domain-модель меняет форму; dependency rule не нарушен).

**Оценка:** ~5 ч (step 0 + 7 шагов + 3 гейта). После C-min rename — экономия ~30 мин на переименовании: `Instrument`/`Future`/`Share` уже названы правильно, остаётся только сменить relation.

**Триггер:** снят явно (2026-11-02, руководитель). Phase 2 выполнена без BCS/Finam — модель готова; шаги 0.2/0.3 (BCS/Finam mapping) отложены до `### Реализация BCS Broker API` / `### Реализация Finam Broker API`.

**Ветка:** `feature/instrument-sealed-interface`, один PR, 3 коммита внутри.

**Что НЕ входит в Phase 2** (сознательно):

- **Margin/risk** (`initialMarginOnBuy/Sell`, `dlong*`, `klong/kshort`) — отдельная задача `### Margin/risk → TradeCheckResult` в ROADMAP, дата **2026-11-02**. Зависит от Phase 2: после неё однозначно видно, что эти поля — Future-specific и account-specific (Finam `GetAssetParams` с `account_id`), их место — не в `Instrument`.

#### Шаг 0. Полный маппинг subtype-specific (read-only)

- [x] **0.1.** T-Invest: proto `Share`, `Future`, `Bond`, `Option`, `Etf`, `Currency` — полные списки полей. `Share`/`Future`/`Option`/`Instrument` — уже получены 2026-11-01. `Bond`/`Etf`/`Currency` — при реализации (тривиальные списки). **Выполнено (2026-11-02):** списки зафиксированы в §5, для `Bond`/`Etf`/`Currency` — стартовые; точные proto-поля — при реализации.
- [ ] **0.2.** BCS: `instruments/by-isins`, `by-type` — какие поля относятся к Share/Future/Bond/Etf. Сверить `(?)`-пометки в §9 (например, `emissionDate` ↔ `ipoDate`, `faceValue` ↔ `nominal`, `dividendYield` vs `divYieldFlag`, `subType` ↔ `shareType`, `liquidityRating` vs `liquidityFlag`). **Отложено:** BCS-документации нет в переданных docs; `(?)` в §9 остаются до `### Реализация BCS Broker API`.
- [ ] **0.3.** Finam: `AssetsService/GetAsset` — `future_details`, `bond_details`, `option_details`. Сверить. **Отложено:** Finam-документации нет в переданных docs; до `### Реализация Finam Broker API`.
- [x] **0.4.** Обновить §5 и §9: полные списки, без `(?)` там, где подтверждено; оставшиеся `(?)` — в §10. **Выполнено (2026-11-02):** §5 актуален (списки + margin + удаляем 6 полей); §9 `(?)` (3 шт) остаются — BCS mapping, отложено (см. §10).
- **Оценка:** 30 мин. **Блокер:** нет.

#### Коммит 1 — sealed interface + data classes (шаги 1–2)

- [x] **1.** `sealed interface Instrument` (core 4 + common 9 + флаги). Общие члены — на интерфейсе: core non-null, common nullable, флаги nullable.
- [x] **2.** 7 `data class`: `Share`, `Future`, `Bond`, `Etf`, `Currency`, `Option`, `Other` — с subtype-specific полями из §5.
  - `Etf`, `Currency`, `Other` — только core + common (нет subtype-specific по §5). Различаются **самим типом**, не полем — в этом смысл sealed.
  - `Option` — с полями из §5 (`direction`, `paymentType`, `style`, `settlementType`, `strikePrice`, `firstTradeDate`, `lastTradeDate`). Не реализуется в мапперах и операциях, но **в модели есть** — иначе `when` не exhaustive при появлении опционов.
- **Оценка:** 1 ч.

#### Коммит 2 — `mapProtoToDomain` split (шаги 3–4)

- [x] **3.** Сменить relation (после C-min `InstrumentUi` → `Instrument`, `FutureUi` → `Future`, `ShareUi` → `Share`):
  - `open class Instrument` → `sealed interface Instrument`.
  - `data class Future`/`Share` — `: InstrumentUi(...)` → `: Instrument`.
  - Позиционное делегирование (48 аргументов в `FutureUi`/`ShareUi`) **удаляется**: `override` в декларации, не в вызове.
- [x] **4.** `mapProtoToDomain` (146 строк) → 6 мапперов + диспетчер:
  - `mapShare(proto: Share): Share`, `mapFuture(proto: Future): Future`, `mapBond(proto: Bond): Bond`, `mapOption(proto: Option): Option`, `mapEtf(...): Etf`, `mapCurrency(...): Currency`.
  - Диспетчер: `when (proto) { is Share -> mapShare(...); is Future -> mapFuture(...); ... }` — exhaustive по proto-типам.
- **Оценка:** 1.5 ч.

#### Коммит 3 — `toInstrumentUi` split + SearchCache + exhaustive `when` (шаги 5–7)

- [x] **5.** `toInstrumentUi` (4 функции в `TInvestRestMappers`) → 6 функций по аналогии с шагом 4.
- [x] **6.** `SearchCacheImpl:52` — сортировка: `when (instrument) { is Future -> instrument.expirationDate; ... }` вместо `as? FutureUi`.
- [x] **7.** Все `when (instrumentType)` → `when (instrument)`:
  - `OrdersScreen.getInstrumentTypeColor` — `when (instrument) { is Share -> ...; is Future -> ...; ... }`.
  - `TInvestPositionsWsMapper`, `TInvestPortfolioService` — проверка типа через `is`.
  - `PositionFlowManager:234,237` — склейка currency через `is Currency`.
  - Все оставшиеся `== InstrumentType.XXX` в `main` — `is XXX`.
- **Оценка:** 40 мин.

#### Критерий готовности

- `grep "as? FutureUi\|as? ShareUi\|as? InstrumentUi"` в `main` = 0.
- `grep "InstrumentUi\|FutureUi\|ShareUi"` в `main` = 0.
- Все `when (instrument)` — без `else`, exhaustive (компилятор подтверждает).
- `InstrumentType` enum — остаётся как поле на интерфейсе (удобно для логов, метрик, UI-текстов; маппинг enum → строка — в presentation).
- Гейт зелёный на каждом коммите.
- §8 чек-боксы `[x]` — в том же PR.

**Почему sealed interface, а не open class.** Короткое обоснование, чтобы через полгода не возвращаться:

- **Exhaustive `when` — главный бонус.** `when (instrument) { is Share -> ...; is Future -> ... }` без `else` — компилятор не даст забыть новый тип (`Bond`, `Etf`, `Option`). Сейчас `when (instrumentType: String)` с `else` молча проглатывает новое значение (`OrdersScreen.getInstrumentTypeColor`, `TInvestPositionsWsMapper`, `TInvestPortfolioService` и т.д.).
- **Убирает positional delegation.** `Future`/`Share` сейчас дублируют ~48 позиционных аргументов в `: InstrumentUi(...)` (Phase 2 снимает это окончательно). Ошибка «перепутал `classCode` и `name`» — оба `String`, компилятор молчит, ловится только runtime. С sealed — `override` в декларации класса, не в вызове.
- **No phantom fields (ISP).** `Share` наследует `expirationDate`, `initialMarginOnBuy`, `basicAsset` — все `null`, никогда не используются. С sealed — `Share` только share-поля, `Future` — только futures.
- **Type-safe subtype-доступ.** `SearchCacheImpl:52`: `(it as? Future)?.expirationDate : Instant?` — тип говорит «может быть null», хотя для `Future` он всегда есть. С `when (is Future)` — smart cast в non-null `Instant`.
- **LSP честно.** `Share IS-A InstrumentUi` формально валидно, но объект с бессмысленными полями. Sealed — сиблинги с минимальным интерфейсом, иерархии нет.

**Честный trade-off.** Больше `override` в декларациях (каждый общий член переобъявляется). Нет shared behavior — но у `InstrumentUi` методов с реализацией ноль (`moneyPerPointOfPrice` — extension по C-min). Если бы были методы с реализацией — интерфейс потребовал бы default-реализаций.

**Что sealed НЕ даёт.** Заменяет `as? Future` на `is Future` + smart cast (форма exhaustive, но проверка типа никуда не уходит). Не ускоряет JVM-`instanceof`. Не решает «сколько типов» — если брокеры дадут 50 `type`, sealed превратится в 50 `data class` (поэтому `InstrumentType` enum — 14 значений — компромисс, а sealed — на 7 конкретных типах).



**Phase 2 закрыта (2026-11-02).** 9/11 шагов; шаги 0.2/0.3 (BCS/Finam mapping subtype-полей) отложены до `### Реализация BCS Broker API` / `### Реализация Finam Broker API`. 9 коммитов (`bf34061`..`f9d53b9` + fix'ы), CI зелёный.

### 9. Сравнительные таблицы

#### Акции

| Domain | T-Invest | BCS | Finam |
|---|---|---|---|
| `tscalpInstrumentId` | `uid` | `figi` | `id` |
| `ticker` | `ticker` | `ticker` | `ticker` |
| `isin` | `isin` | `isin` | `isin` |
| `name` | `name` | `displayName` | `name` |
| `instrumentType` | `SHARE` (enum; контекст `Shares()`) | `STOCK` | `STOCK` |
| `classCode` | `classCode` | `boards[0].classCode` | — |
| `exchange` | `exchange` | `boards[0].exchange` | `mic` |
| `lot` | `lot: Int` | `lotSize: double` | `lot_size: Decimal` (только `GetAsset`) |
| `currency` | `currency` | `tradingCurrency` | `quote_currency` |
| `settlementCurrency` | — | `settlementCurrency` | — |
| `figi` | `figi` | `figi` (из позиций) | — |
| `priceStepCost` | — | — | — |
| `minPriceIncrement` | `minPriceIncrement` | `minimumStep` | `min_step / 10^decimals` |
| `moneyPerPointOfPrice` | computed = 1.0 | computed = 1.0 | computed = 1.0 |
| `ipoDate` | `ipoDate` | `emissionDate` (?) | — |
| `issueSize` | `issueSize` | — | — |
| `issueSizePlan` | `issueSizePlan` | — | — |
| `nominal` | `nominal` | `faceValue` | — |
| `divYieldFlag` | `divYieldFlag` | `dividendYield` (double, не флаг) | — |
| `shareType` | `shareType` | `subType` (?) | — |
| `liquidityFlag` | `liquidityFlag` | `liquidityRating` (строка, не флаг) | — |

**Источники (акции):**
- T-Invest: `ru.tinkoff.piapi.contract.v1.Share` (proto), SDK `kotlin-sdk-grpc-contract`.
- BCS: https://trade-api.bcs.ru/http/information (get-instruments-by-isins, by-type, by-tickers).
- Finam: https://api.finam.ru/docs/grpc/#allassets (Assets, GetAsset).

#### Фьючерсы

| Domain | T-Invest | BCS | Finam |
|---|---|---|---|
| `tscalpInstrumentId` | `uid` | `figi` | `id` |
| `ticker` | `ticker` | `ticker` | `ticker` |
| `isin` | **—** (у proto `Future` нет) | `isin` | `isin` |
| `name` | `name` | `displayName` | `name` |
| `instrumentType` | `FUTURES` (enum; контекст `Futures()`) | `FUTURES` | `FUTURES` |
| `classCode` | `classCode` | `boards[0].classCode` | — |
| `exchange` | `exchange` | `boards[0].exchange` | `mic` |
| `lot` | `lot: Int` | `lotSize: double` | `lot_size: Decimal` |
| `currency` | `currency` | `tradingCurrency` | `quote_currency` |
| `settlementCurrency` | — | `settlementCurrency` | — |
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
| `basicAssetPositionUid` | `basicAssetPositionUid` | — | — |

**Источники (фьючерсы):**
- T-Invest: `ru.tinkoff.piapi.contract.v1.Future` (proto), `GetFuturesMargin`.
- BCS: https://trade-api.bcs.ru/http/information (get-instruments-by-isins, by-type).
- Finam: https://api.finam.ru/docs/grpc/#allassets (`future_details`).

#### Опционы

Не реализуется сейчас. Зафиксировано для полноты (proto T-Invest `Option`); поля BCS/Finam — из их документации.

| Domain | T-Invest | BCS | Finam |
|---|---|---|---|
| `tscalpInstrumentId` | `uid` | `figi` | `id` |
| `ticker` | `ticker` | `ticker` | `ticker` |
| `name` | `name` | `displayName` | `name` |
| `instrumentType` | `OPTION` (enum; контекст `Options()`) | `OPTIONS` | — |
| `classCode` | `classCode` | `boards[0].classCode` | — |
| `exchange` | `exchange` | `boards[0].exchange` | `mic` |
| `lot` | `lot: Int` | `lotSize: double` | — |
| `currency` | `currency` | `tradingCurrency` | — |
| `settlementCurrency` | `settlement_currency` | `settlementCurrency` | — |
| `figi` | `figi` | `figi` | — |
| `priceStepCost` | — | — | — |
| `minPriceIncrement` | `minPriceIncrement` | `minimumStep` | — |
| `moneyPerPointOfPrice` | computed = 1.0 | computed = 1.0 | computed = 1.0 |
| `direction` | `direction` (`PUT`/`CALL`) | — | — |
| `paymentType` | `paymentType` (`PREMIUM`/`MARGINAL`) | — | — |
| `style` | `style` (`AMERICAN`/`EUROPEAN`) | — | — |
| `settlementType` | `settlementType` (`PHYSICAL_DELIVERY`/`CASH_SETTLEMENT`) | — | — |
| `strikePrice` | `strike_price` (MoneyValue) | `strike` (double) | — |
| `expirationDate` | `expirationDate` | `maturityDate` | — |
| `firstTradeDate` | `firstTradeDate` | — | — |
| `lastTradeDate` | `lastTradeDate` | — | — |

**Источники (опционы):**
- T-Invest: `ru.tinkoff.piapi.contract.v1.Option` (proto).
- BCS: https://trade-api.bcs.ru/http/information (get-instruments-by-type, `type=OPTIONS`).
- Finam: не подтверждено; при реализации — `AssetsService/GetAsset` (если появятся опционы).

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

| 9 | **BCS mapping subtype-полей** (`emissionDate` ↔ `ipoDate`, `subType` ↔ `shareType`, `baseAssetFuture` ↔ `futuresType`, `faceValue` ↔ `nominal`, `dividendYield` vs `divYieldFlag`, `liquidityRating` vs `liquidityFlag`) — §9 `(?)` (3 шт) остаются до `### Реализация BCS Broker API`. Finam `future_details`/`bond_details`/`option_details` — до `### Реализация Finam Broker API`. | §9 | при реализации BCS/Finam |

**Закрытые (2026-11-01):**
- `requiredTests` — не включаем (Q3). Тесты проходятся на сайте брокера; ошибка операции ловится как `AppError` с сервера.
- `positionUid`, `assetUid`, `otcFlag`, `realExchange` — не включаем (Q4, YAGNI). Добавим при реальной необходимости.
- `klong`/`kshort` — `[deprecated = true]` в proto T-Invest; не тащим.
- `instrumentType` — заполняется маппером по контексту (`Shares()` → `SHARE`), не из proto-поля: у `Share`/`Future`/`Option` нет общего `instrument_type`. В Phase 2 (A) subtype сам определяет тип.

### 11. Связанные документы

- `docs/architecture.md` — слои, dependency rule.
- `docs/clean-audit.md` — Н5 (`PortfolioPosition` — presentation-поля), Н6 (`InstrumentUi` — god-класс), Н7 (`ttech_figi` — временное).
- `docs/ROADMAP.md` — задачи реализации.
- `docs/brokers.md` — справка по брокерам.
- `docs/ISSUES.md` — известные проблемы (N+1 при search).

---

## Портфель (Portfolio)

### 1. Роль моделей

- **`Position`** — снимок позиции по инструменту в конкретном счёте. Тип инструмента — через `instrument: Instrument` (композиция). Плоские subtypes: `SharePosition`, `FuturePosition`, `BondPosition`, `EtfPosition`.
- **`AccountBalance`** — денежный остаток по валюте в конкретном счёте. Не позиция (валюта — не ценная бумага).
- **`AccountPortfolio`** — агрегат одного счёта: позиции + балансы.
- **`PortfolioSheet`** — мультисчётный агрегат: `Map<AccountKey, AccountPortfolio>`.

**Жизненный цикл:**

1. Заполняется при unary snapshot (`GetPortfolio` / `GET /portfolio` / `GetAccount`) и обновляется через WS-стрим (T-Invest `PositionsStream`, BCS `/portfolio/ws`; у Finam WS портфеля нет — только unary).
2. UI рендерит по счетам: шапка счёта (баланс — графических элементов пока нет, предстоит сделать) + список позиций.
3. Derived-поля (`totalValue`, `profitPercent`, `priceChangePercent`, `AccountBalance.total`, `AccountPortfolio.totalProfit`) — computed getters, не хранятся.

**Следствия (проектные ограничения):**

- Мультиброкерность и мультисчётность обязательны: на Портфеле одновременно отображаются счета разных брокеров, у одного брокера — несколько счетов.
- Составной ключ `AccountKey(brokerName, accountId)` — единственный идентификатор счёта в агрегате.
- `Position` не зависит от того, какие подтипы есть в `Instrument`; добавление `Bond` / `Etf` не ломает `Position` — только добавляет `BondPosition` / `EtfPosition`.

### 2. Терминология (переименования)

| Было | Стало | Что означает | Почему |
|---|---|---|---|
| `PortfolioPosition` | `Position` (sealed interface) + subtypes | Позиция по инструменту в счёте | Тип инструмента через `instrument: Instrument`; плоские subtypes по паттерну проекта |
| `PortfolioPosition.money` | `AccountBalance.money` | Свободный остаток по валюте | Деньги — не позиция |
| `PortfolioPosition.blocked` | `AccountBalance.blocked` | Заблокированный остаток по валюте | То же |
| `PortfolioPosition.totalValue` | `Position.totalValue` (getter) | Стоимость позиции | Derived, не хранится |
| `PortfolioPosition.profitPercent` | `Position.profitPercent` (getter) | ROI на вложенное | Derived |
| `PortfolioPosition.priceChangePercent` | `Position.priceChangePercent` (поле) | Изменение цены за торговый день | Не getter — источник из DTO брокера: T-Invest `dailyYieldRelative`, BCS `dailyPercentPL`, Finam `daily_pnl` |
| `PortfolioPosition.name` | `Position.instrument.name` | Имя инструмента | Композиция; отдельного `name` нет |
| `PortfolioPosition.brokerName: String` | `Position.brokerName: BrokerName` | Брокер | Enum, типизация |
| — (новое) | `Position.accountId: String` | ID счёта | Мультисчётность |
| — (новое) | `Position.lockedQuantity: Double?` | Заблокированное количество бумаг | T-Invest `blocked`, BCS `locked` |
| — (новое) | `AccountKey(brokerName, accountId)` | Составной ключ | Мультиброкерность |
| — (новое) | `AccountPortfolio` / `PortfolioSheet` | Агрегаты | Мультисчётность |

### 3. Core `Position` (обязательные)

| Поле | T-Invest | BCS | Finam |
|---|---|---|---|
| `instrument: Instrument` | `instrumentUid` + `GetInstrumentBy` (композиция) | `ticker` + `board` + `AssetsService` (композиция) | `symbol` + `AssetsService/GetAsset` (композиция) |
| `brokerName: BrokerName` | `TINVEST` (контекст) | `BCS` | `FINAM` |
| `accountId: String` | `accountId` (из запроса) | `account` (в записи) | `account_id` (из запроса) |
| `quantity: Double` | `balance` (Long) → `toDouble()` | `quantity` (number) | `quantity.value` (Decimal) → `toDouble()` |
| `currentPrice: Double` | `currentPrice` | `currentPrice` | `current_price.value` |
| `averagePrice: Double?` | `averagePositionPrice` | `balancePrice` | `average_price.value` (null для FORTS) |
| `profit: Double?` | `expectedYield` | `unrealizedPL` | `unrealized_pnl.value` |
| `lockedQuantity: Double?` | `blocked` (WS) | `locked` | — (null) |

`quantity: Double` — компромисс: Finam поддерживает дробное количество, T-Invest/BCS — целое. Presentation-формат («10 шт» vs «10.5 шт») — в UI.

### 4. Common optional (`AccountBalance`, `AccountPortfolio`, `PortfolioSheet`)

**`AccountBalance`** — по валюте, множественный (`List<AccountBalance>` в `AccountPortfolio`):

| Поле | T-Invest | BCS | Finam |
|---|---|---|---|
| `currency: String` | `currency` (из `moneyList`) | `currency` (из `moneyLimit`) | `cash.currency_code` |
| `money: Double` | `units + nanos / 1e9` | `quantity` (из `moneyLimit`) | `units + nanos / 1e9` |
| `blocked: Double` | `blocked` (из `moneyList`) | `locked` (из `moneyLimit`) | `portfolio_forts.money_reserved` (только FORTS); иначе `0.0` |

**`AccountPortfolio`** — агрегат счёта:

- `accountId: String`
- `brokerName: BrokerName`
- `positions: List<Position>`
- `balances: List<AccountBalance>`
- `totalProfit: Double?` — getter

**`PortfolioSheet`**:

- `accounts: Map<AccountKey, AccountPortfolio>`

**`AccountKey`**:

- `brokerName: BrokerName`
- `accountId: String`

**Derived getters (computed, не хранятся):**

- `Position.totalValue: Double get() = currentPrice * quantity * instrument.moneyPerPointOfPrice`
- `Position.profitPercent: Double? get() = profit / (averagePrice * quantity * instrument.moneyPerPointOfPrice)`
- `Position.priceChangePercent: Double?` — **поле** (не getter), источник — DTO брокера (см. §2). Семантика: изменение цены за торговый день от цены открытия.
- `AccountBalance.total: Double get() = money + blocked`
- `AccountPortfolio.totalProfit: Double? get() = positions.sumOf { it.profit ?: 0.0 }.takeIf { positions.any { it.profit != null } }`

`moneyPerPointOfPrice` — extension из `Instrument` (C-min §7 шаг 7). Единая формула `totalValue` для shares / futures / bonds / etf.

### 4.x `PortfolioSheet` — агрегат мультисчётности

**Что.** Полный снимок всех счетов пользователя: все инициализированные брокеры × все счета × позиции × `AccountBalance` per currency.

**Зачем.** Единственный публичный эмитируемый тип `PositionFlowManager`. Убирает плоский `List<PortfolioPosition>` из `PortfolioViewModel`. UI группирует по счёту (`AccountKey`), не по брокеру.

**Кто эмитит.** `PositionFlowManager.portfolioSheet: StateFlow<PortfolioSheet>` — multi-account. Legacy `flow: SharedFlow<PositionStreamItem>` — временно для `OrdersViewModel` (миграция — задача `### OrdersViewModel: миграция на PortfolioSheet`).

**Кто потребляет.** `PortfolioViewModel` — единственный потребитель на момент введения. `OrdersViewModel` — через `flow` (legacy). После миграции `OrdersViewModel` — только `portfolioSheet`.

**Инварианты.**
- Ключ `AccountKey(brokerName, accountId)` уникален в `accounts`.
- Порядок: `BrokerName.entries` (TINVEST, BCS, FINAM), внутри — `accountId` лексикографически. Детерминированно, не зависит от UI-состояния.
- `PortfolioSheet` — immutable снимок. При обновлении одной позиции/баланса одного счёта создаётся новый `PortfolioSheet` (не мутируется).

**Разница с `AccountPortfolio`.** `AccountPortfolio` — один счёт (`positions`, `balances`, `totalProfit`). `PortfolioSheet` — все счета. `AccountPortfolio` — элемент `Map`, а не самостоятельный эмитируемый тип.

**Жизненный цикл.** Создаётся при `PositionFlowManager.start(accountKeys)` — все счета, которые UI запросил. Обновляется per-account (stream / polling). Сбрасывается в `stop()`.

**Связь с `PositionStreamItem` / `Position`.** `PositionStreamItem` — internal-событие источника (stream / polling). `Position` — domain-модель позиции (в `AccountPortfolio.positions`). Конверсия `PositionStreamItem → Position` — в маппере / менеджере. `PortfolioSheet` наружу не отдаёт `PositionStreamItem`.

### 5. Subtype-specific `Position`

Плоские subtypes (7, симметрично `Instrument`): `SharePosition`, `FuturePosition`, `BondPosition`, `OptionPosition`, `EtfPosition`, `CurrencyPosition`, `OtherPosition`. Каждый — `data class` с тем же набором override-полей, что и интерфейс. Subtype-специфичных полей **сейчас нет** (все поля — common). Если когда-нибудь появится (например, `margin` только для фьючерсов) — место уже есть.

`CurrencyPosition` — валюта иностранного государства. Деньги в домашней валюте (RUB) — в `AccountBalance`, не в `Position`.

Плоскость следует из `Instrument`: `SharePosition.instrument: Share`, `FuturePosition.instrument: Future`, `BondPosition.instrument: Bond`, `OptionPosition.instrument: Option`, `EtfPosition.instrument: Etf`, `CurrencyPosition.instrument: Currency`, `OtherPosition.instrument: Other`. Компилятор проверяет соответствие.

### 6. Связь с `Instrument`

- Композиция: `Position.instrument: Instrument`. Поля `Instrument` (ticker, name, classCode, isin, currency, moneyPerPointOfPrice, …) не дублируются.
- `moneyPerPointOfPrice` — extension из `Instruments` C-min §7 шаг 7.
- `Position` не зависит от того, какие подтипы есть в `Instrument`. Добавление `Bond` / `Etf` в `Instrument` не ломает `Position` — только добавляет `BondPosition` / `EtfPosition`.
- `when (position)` — exhaustive по `Position` (7 подтипов); `when (position.instrument)` — вложенный exhaustive по `Instrument` (7 подтипов). Оба — явные.

### 7. Вне модели

- `equity` (Finam, account-level) — не включаем. При появлении UI-шапки — extension или отдельная задача.
- `unrealized_profit` (Finam, account-level) — не включаем. `AccountPortfolio.totalProfit` — computed.
- `dailyPL`, `dailyPercentPL` (BCS), `daily_pnl` (Finam) — PnL за день, отдельная концепция. Не включаем.
- `maintenance_margin` (Finam, FORTS), `initial_margin` (Finam `portfolio_mc`), `lockedForFutures` (BCS) — margin/risk, отдельная задача `### Margin/risk → TradeCheckResult`.
- `portfolioShare`, `balanceValue*`, `currentValue*` (BCS) — производные от `Position.totalValue` (в разных валютах). Не включаем.
- `unrealizedPercentPL` (BCS) — совпадает с `profitPercent` (проверено на SBER: `(296 - 300.5267) / 300.5267 = -1.5062%`), **не** с `priceChangePercent`. Не включаем — `profitPercent` computed. `priceChangePercent` — это day change, источник — `dailyPercentPL` (см. §2).
- `faceValue`, `accruedIncome` (BCS, облигации) — subtype-специфика `Instrument` (Bond), не позиции. В `Instrument` — при реализации Bond.
- `portfolio_mc` / `portfolio_forts` / `portfolio_mct` (Finam) — margin/risk, не портфель.
- `open_account_date`, `first_trade_date`, `first_non_trade_date` (Finam) — метаданные счёта, не портфель.
- `isBlocked` / `isBlockedTradeAccount` (BCS) — статус, не портфель.
- `otcLimit` (BCS) — внебиржевые, не поддерживаем, пропускать.
- `futuresLimit` (BCS) — открытый вопрос §10.

**UI-шапка счёта с балансом** — реализована (`AccountHeaderCard`, `AccountBalance.total`). Разбивка `total` на `money` / `blocked` — задача `### UI: разбивка AccountBalance.total на money/blocked в шапке счёта`.

### 8. Этапы

**Одна фаза.** `Position` сразу `sealed interface` + плоские `data class`. `AccountBalance`, `AccountPortfolio`, `PortfolioSheet`, `AccountKey` — `data class`.

**Триггер:** `Instrument` Phase 2 (sealed interface) реализована. «Сначала Instruments, затем Portfolio». Проектирование — сейчас; реализация — после `Instrument` Phase 2 (не после C-min).

**Ветка:** `feature/portfolio-model`, один PR.

**Шаги (заполняются при реализации):**

1. `Position` (sealed interface) + `SharePosition` / `FuturePosition` / `BondPosition` / `EtfPosition`.
2. `AccountBalance` + `AccountPortfolio` + `PortfolioSheet` + `AccountKey`.
3. `moneyPerPointOfPrice` — extension на `Instrument` (из C-min §7 шаг 7), использовать в `totalValue` / `profitPercent`.
4. Мапперы `TInvestPortfolioMapper` / `BcsPortfolioMapper` / `FinamPortfolioMapper` — из DTO в `AccountPortfolio`.
5. `PositionFlowManager` — эмитит `PortfolioSheet` вместо `SharedFlow<PositionStreamItem>`; merge stream + polling.
6. UI — `AssetPositionCard` принимает `Position`; `PortfolioScreen` — по счетам.
7. Удалить `PortfolioPosition`, `PositionStreamItem` (если больше не нужен), `toPortfolioPosition`, `mergeWith`.
8. Гейт.

### 9. Сравнительные таблицы

#### 9.1 Позиции (`Position`)

| Domain | T-Invest | BCS | Finam |
|---|---|---|---|
| `instrument` | `instrumentUid` + `GetInstrumentBy` | `ticker` + `board` + `AssetsService` | `symbol` + `AssetsService/GetAsset` |
| `brokerName` | `TINVEST` | `BCS` | `FINAM` |
| `accountId` | `accountId` | `account` | `account_id` |
| `quantity` | `balance: Long` → `toDouble()` | `quantity: number` | `quantity.value: string` (Decimal) → `toDouble()` |
| `currentPrice` | `currentPrice` | `currentPrice` | `current_price.value` |
| `averagePrice` | `averagePositionPrice` | `balancePrice` | `average_price.value` (null для FORTS) |
| `profit` | `expectedYield` | `unrealizedPL` | `unrealized_pnl.value` |
| `lockedQuantity` | `blocked` (WS) | `locked` | — (null) |

**Источники (позиции):**

- T-Invest: `GetPortfolio` (gRPC unary) + REST fallback, `PositionsStream` WS.
- BCS: `GET https://be.broker.ru/trade-api-bff-portfolio/api/v1/portfolio`, `wss://ws.broker.ru/trade-api-bff-portfolio/api/v1/portfolio/ws`.
- Finam: `AccountsService/GetAccount` (gRPC), `GET /v1/accounts/{account_id}` (REST); WS портфель не отдаёт.

#### 9.2 Денежные остатки (`AccountBalance`)

| Domain | T-Invest | BCS | Finam |
|---|---|---|---|
| `currency` | `currency` | `currency` | `cash.currency_code` |
| `money` | `units + nanos / 1e9` | `quantity` | `units + nanos / 1e9` |
| `blocked` | `blocked` | `locked` | `portfolio_forts.money_reserved` (только FORTS); иначе `0.0` |
| `total` | getter | getter | getter |

**Источники (балансы):**

- T-Invest: `moneyList` в `GetPortfolio` (gRPC/REST) и `PositionsStream` WS.
- BCS: `moneyLimit` в `/portfolio` (HTTP/WS).
- Finam: `cash[]` в `GetAccount`; `portfolio_forts.money_reserved` — blocked для FORTS.

#### 9.3 Матрица источников портфеля

| Брокер | Unary snapshot | WS stream | Периодичность polling |
|---|---|---|---|
| T-Invest | `GetPortfolio` (gRPC) / REST fallback | `PositionsStream` (`moneyList` + `securities` / `futures`) | 10 с (`PositionFlowManager.runPolling`) |
| BCS | `GET /portfolio` | `wss://ws.broker.ru/trade-api-bff-portfolio/api/v1/portfolio/ws` (формат = HTTP-ответ) | при реализации |
| Finam | `GetAccount` (gRPC) / `GET /v1/accounts/{id}` (REST) | **нет** (WS только ORDERS / QUOTES / TRADES / BARS / ORDER_BOOK) | при реализации; REST-лимит 200 req/min |

### 10. Открытые вопросы

| # | Вопрос | Где | Когда |
|---|---|---|---|
| 1 | **BCS `futuresLimit`** — деньги под ГО или фьючерсная позиция? Не угадываем. | `type` enum BCS | при реализации BCS |
| 2 | **Finam `positions.quantity` включает теоретические позиции** (по неисполненным активным заявкам). Разделять `actual` vs `theoretical` или нет? | `Position.quantity` | при реализации Finam |
| 3 | **Finam `current_price_currency` / `average_price_currency`** — только для общих счетов Мосбиржи. Наша модель — одна `instrument.currency`. Если валюты разные — пометить как ограничение. | `Position` | при реализации Finam |
| 4 | **Источник `money` / `blocked` для T-Invest gRPC/REST и BCS** — приходит в `GetPortfolio` / `/portfolio` или нужен отдельный запрос. WS — `moneyList`, подтверждено. | data | при реализации |
| 5 | **`PortfolioSheet` — Map vs List.** Финал: Map. | — | — |
| 6 | **`Position.lockedQuantity` для Finam** — `null`, `maintenance_margin` — деньги ГО, не количество бумаг. Если нужен — отдельная семантика (деньги), не в `Position`. | `lockedQuantity` | при реализации Finam |
| 7 | **`AccountPortfolio.totalProfit`** — у Finam `unrealized_profit` на уровне счёта (может включать комиссии). Считать самим — потенциальное расхождение с серверным. | `totalProfit` | при появлении UI-шапки |

**Закрытые (2026-11-01):**

- Все проектные вопросы (модель, поля, getters, ключ агрегата, форма subtypes, источники) — зафиксированы в этом разделе.
- `lockedQuantity: Double?` — включено.
- `futuresLimit` (BCS) — открытый вопрос §10.

### 11. Связанные документы

- `docs/architecture.md` — «Data flow: позиции» (текущее состояние, `PortfolioPosition`, `PositionFlowManager`).
- `docs/clean-audit.md` — Н5 (`PortfolioPosition` — presentation-поля, пересматривается), Н15 (`TradingStateSnapshot`).
- `docs/ROADMAP.md` — `### H5. PortfolioPosition: разделение domain/presentation` (реализация), `### UI: разбивка AccountBalance.total на money/blocked в шапке счёта`, `### Обогащение name в PortfolioPosition (вариант C)` (отменяется как подзадача — `name` в `Instrument`), `### Margin/risk → TradeCheckResult`, `### Instrument: C-min → sealed interface` (предусловие).
- `docs/domain_models.md` → `## Instruments` (связь через `Position.instrument: Instrument`).
- **Источники брокеров:**
  - T-Invest: `docs/brokers.md`, `docs/streams.md`, `docs/rate-limits.md`.
  - BCS: `https://trade-api.bcs.ru/http/portfolio`, `https://trade-api.bcs.ru/websocket/portfolio/`.
  - Finam: `https://api.finam.ru/docs/grpc/#introduction`, `https://api.finam.ru/docs/async-api/#introduction`, `https://api.finam.ru/docs/rest/`.

### 12. Реализация — отдельная задача

Этот документ — **проектирование**. Реализация — `docs/ROADMAP.md` → `### Portfolio: модель портфеля`. Задача самодостаточная (5e), по образцу `### Instrument: C-min → sealed interface`.

---

## Заявки (Orders)

*Раздел в проектировании. Открывается после `Instruments` и `Portfolio`.*
