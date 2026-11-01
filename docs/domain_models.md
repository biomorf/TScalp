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

### 3. Core (обязательные, 4)

Без них инструмент не идентифицируется. Все — non-null.

| Поле | T-Invest | BCS | Finam |
|---|---|---|---|
| `tscalpInstrumentId: String` | `uid` | `figi` | `id` |
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

**`Bond`** (proto T-Invest `Bond` + BCS `bond_details`):
- `faceValue: Double?`, `couponRate: Double?`, `nextCoupon: Instant?`, `couponsPerYear: Long?`, `accruedInt: Double?`, `creditRating: String?`

**`Option`** (не реализуется сейчас; зафиксировано для полноты по proto T-Invest `Option`):
- `direction: String?` (`PUT`/`CALL`), `paymentType: String?` (`PREMIUM`/`MARGINAL`), `style: String?` (`AMERICAN`/`EUROPEAN`), `settlementType: String?` (`PHYSICAL_DELIVERY`/`CASH_SETTLEMENT`), `strikePrice: Double?`, `firstTradeDate: Instant?`, `lastTradeDate: Instant?`, `expirationDate: Instant?` (последний — уже в common, здесь переопределяется как `non-null` в Phase 2)

**`Etf`, `Currency`, `Other`:** только core + common.

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

- [ ] **0.1.** Прочитать proto T-Invest `Share`/`Future`/`Option`/`Instrument` (`instrument_type`, `instrument_kind`), BCS `type` (`STOCK`/`FOREIGN_STOCK`/`BONDS`/...), Finam `type` (`STOCK`/`BOND`/`FUTURES`/...).
- [ ] **0.2.** Обновить §9 (таблицы): столбец `instrumentType` — маппинг каждого значения.
- [ ] **0.3.** Зафиксировать в §10 неоднозначности, если есть (T-Invest `"fond"` vs `"etf"`, BCS `MUTUAL_FUNDS` vs `ETF`, Finam `type` — точный список).
- **Оценка:** 15 мин. **Блокер:** нет.

#### Коммит 1 — переименования (шаги 1–2)

- [ ] **1.** Удалить `ttech_uid` — `InstrumentUi`, `FutureUi`, `ShareUi`, `mapProtoToDomain`, `toInstrumentUi`. Заменить на `tscalpInstrumentId` (= `uid`) там, где использовалось.
- [ ] **2.** `ttech_figi` → `figi: String? = null` — `InstrumentUi`, `FutureUi`, `ShareUi`, мапперы.
- **Оценка:** 25 мин.

#### Коммит 2 — nullable + новые поля (шаги 3–6)

- [ ] **3.** `classCode: String` → `String? = null` — `InstrumentUi`, мапперы (`?: ""` → `null`).
- [ ] **4.** `currency: String` → `String? = null` — `InstrumentUi`, мапперы.
- [ ] **5.** `lot: Int` → `Long? = null` — `InstrumentUi`, мапперы (`lot.toLong()`), `CalculateTradeDetailsUseCase` (fallback `?: 1L` где нужен non-null).
- [ ] **6.** `+ settlementCurrency: String? = null` — `InstrumentUi`, мапперы.
- **Оценка:** 25 мин.

#### Коммит 3 — `moneyPerPointOfPrice` + удаление кастов (шаги 7–9)

- [ ] **7.** `+ moneyPerPointOfPrice: Double` — **extension** на `InstrumentUi` в `Instruments.kt`, computed из `priceStepCost`/`minPriceIncrement`. `FutureUi.pointValue` — **удалить** (заменён extension).
- [ ] **8.** `expirationDate: Instant?` → в `InstrumentUi` (было только `FutureUi`); `FutureUi`/`ShareUi` — убрать `override` (наследуется).
- [ ] **9.** Удалить 9 кастов `as? FutureUi`:
  - `TInvestPortfolioService:64` — `instrumentUi?.moneyPerPointOfPrice` (extension)
  - `CalculateTradeDetailsUseCase:48,78` — `instrument.moneyPerPointOfPrice` (extension)
  - `OrdersViewModel:201,215,403,915` — `instrument?.moneyPerPointOfPrice` (extension)
  - `SearchCacheImpl:52` — `it.expirationDate`
- **Оценка:** 40 мин.

#### Коммит 4 — `instrumentType` enum + rename (шаги 10–14)

- [ ] **10.** `instrumentType: String` → `enum InstrumentType` — `Instruments.kt` (новый enum), `Portfolio.kt` (2 модели), все `== "..."` и `when`, мапперы (маппинг строки в enum, см. ниже).
- [ ] **11.** Переименовать `pointValue` → `moneyPerPointOfPrice` в `Portfolio.kt` (`PositionStreamItem`, `PortfolioPosition`) — это **снимки**, не computed.
- [ ] **12.** `InstrumentUi` → `Instrument` (~25 call-sites, alias `import ru.tinkoff.piapi.contract.v1.Instrument as ProtoInstrument` в `TInvestInstrumentMapper`).
- [ ] **13.** `FutureUi` → `Future` (~10 call-sites).
- [ ] **14.** `ShareUi` → `Share` (~5 call-sites).
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
- data: `TInvestInstrumentMapper`, `TInvestRestMappers`, `TInvestPositionsWsMapper`, `TInvestPositionsService`, `TInvestPortfolioService`, `TInvestAccountsService`, `TInvestInstrumentsService`
- domain: `PositionFlowManager`, `CalculateTradeDetailsUseCase`
- data: `SearchCacheImpl`
- presentation: `OrdersViewModel`, `AssetPositionCard`, `PortfolioScreen`, `OrdersScreen`, `PositionFormatter`
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

**Триггер:** начало реализации BCS Broker API или Finam Broker API. Сейчас `userInstruments = null` у обоих — модель не нагружена.

**Ветка:** `feature/instrument-sealed-interface`, один PR, 3 коммита внутри.

**Что НЕ входит в Phase 2** (сознательно):

- **Margin/risk** (`initialMarginOnBuy/Sell`, `dlong*`, `klong/kshort`) — отдельная задача `### Margin/risk → TradeCheckResult` в ROADMAP, дата **2026-11-02**. Зависит от Phase 2: после неё однозначно видно, что эти поля — Future-specific и account-specific (Finam `GetAssetParams` с `account_id`), их место — не в `Instrument`.

#### Шаг 0. Полный маппинг subtype-specific (read-only)

- [ ] **0.1.** T-Invest: proto `Share`, `Future`, `Bond`, `Option`, `Etf`, `Currency` — полные списки полей. `Share`/`Future`/`Option`/`Instrument` — уже получены 2026-11-01. `Bond`/`Etf`/`Currency` — при реализации (тривиальные списки).
- [ ] **0.2.** BCS: `instruments/by-isins`, `by-type` — какие поля относятся к Share/Future/Bond/Etf. Сверить `(?)`-пометки в §9 (например, `emissionDate` ↔ `ipoDate`, `faceValue` ↔ `nominal`, `dividendYield` vs `divYieldFlag`, `subType` ↔ `shareType`, `liquidityRating` vs `liquidityFlag`).
- [ ] **0.3.** Finam: `AssetsService/GetAsset` — `future_details`, `bond_details`, `option_details`. Сверить.
- [ ] **0.4.** Обновить §5 и §9: полные списки, без `(?)` там, где подтверждено; оставшиеся `(?)` — в §10.
- **Оценка:** 30 мин. **Блокер:** нет.

#### Коммит 1 — sealed interface + data classes (шаги 1–2)

- [ ] **1.** `sealed interface Instrument` (core 4 + common 9 + флаги). Общие члены — на интерфейсе: core non-null, common nullable, флаги nullable.
- [ ] **2.** 7 `data class`: `Share`, `Future`, `Bond`, `Etf`, `Currency`, `Option`, `Other` — с subtype-specific полями из §5.
  - `Etf`, `Currency`, `Other` — только core + common (нет subtype-specific по §5). Различаются **самим типом**, не полем — в этом смысл sealed.
  - `Option` — с полями из §5 (`direction`, `paymentType`, `style`, `settlementType`, `strikePrice`, `firstTradeDate`, `lastTradeDate`). Не реализуется в мапперах и операциях, но **в модели есть** — иначе `when` не exhaustive при появлении опционов.
- **Оценка:** 1 ч.

#### Коммит 2 — `mapProtoToDomain` split (шаги 3–4)

- [ ] **3.** Сменить relation (после C-min `InstrumentUi` → `Instrument`, `FutureUi` → `Future`, `ShareUi` → `Share`):
  - `open class Instrument` → `sealed interface Instrument`.
  - `data class Future`/`Share` — `: InstrumentUi(...)` → `: Instrument`.
  - Позиционное делегирование (48 аргументов в `FutureUi`/`ShareUi`) **удаляется**: `override` в декларации, не в вызове.
- [ ] **4.** `mapProtoToDomain` (146 строк) → 6 мапперов + диспетчер:
  - `mapShare(proto: Share): Share`, `mapFuture(proto: Future): Future`, `mapBond(proto: Bond): Bond`, `mapOption(proto: Option): Option`, `mapEtf(...): Etf`, `mapCurrency(...): Currency`.
  - Диспетчер: `when (proto) { is Share -> mapShare(...); is Future -> mapFuture(...); ... }` — exhaustive по proto-типам.
- **Оценка:** 1.5 ч.

#### Коммит 3 — `toInstrumentUi` split + SearchCache + exhaustive `when` (шаги 5–7)

- [ ] **5.** `toInstrumentUi` (4 функции в `TInvestRestMappers`) → 6 функций по аналогии с шагом 4.
- [ ] **6.** `SearchCacheImpl:52` — сортировка: `when (instrument) { is Future -> instrument.expirationDate; ... }` вместо `as? FutureUi`.
- [ ] **7.** Все `when (instrumentType)` → `when (instrument)`:
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

*Раздел в проектировании. Открывается после завершения `Instruments`.*

---

## Заявки (Orders)

*Раздел в проектировании. Открывается после `Instruments` и `Portfolio`.*
