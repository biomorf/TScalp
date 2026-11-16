<!-- SECTION_BEGIN: clean-audit-reports -->
## Сводные отчёты










<!-- SUBSECTION_BEGIN: clean-audit-112 -->
___
### Метрики
| Метрика | Значение |
|---|---|
| Всего находок | **74** |
| Открыто | ~70 |
| Принято по 2c | ~4–5 (Н25, Н48, Н52, Н81) |
| Исправлено | 0 |
| Новых в этой сессии | 56 (L4–L9) |
| Файлов кода покрыто | ~55 (app + transport-ktor) |
<!-- SUBSECTION_END: clean-audit-112 -->










<!-- SUBSECTION_BEGIN: clean-audit-113 -->
___
### Распределение по уровням
| Уровень | Находок | Ключевые |
|---|---|---|
| L1 presentation | 7 | Н21 (VM → data.*), Н22 (VM → `di.BrokerManager`) — критично |
| L2 util | 4 | Н19 (AppLogger/android.util.Log), Н20 (io.grpc в util) — высоко |
| L3 domain/models | 7 | Н5 (`PortfolioPosition`, принято 2c) |
| L4 domain/api | 11 | Н25 (fat `BrokerApi`), Н70 (`@Suppress`) |
| L5 domain/managers | 6 | Н38 (`DebugConfig` в `PriceFlowManager`), Н40 (`di.BrokerManager` × 3) |
| L6 data (repository) | 4 | Н44 (`di.BrokerManager` × 3), Н45 (4 fat-метода) |
| L6 data (api) | 24 | Н53 (структура), Н63 (god-класс 622), Н71 (refreshToken в логах — security) |
| L7 di | 4 | Н29 (`BrokerManager` — domain-сервис в `di/`) |
| L8 app | 5 | Н50 (domain-логика в app), Н51 (`refreshInitializationState` × 2) |
| L9 transport-ktor | 7 | Н88 (missing `@Serializable`), Н87 (cold flow) |
<!-- SUBSECTION_END: clean-audit-113 -->










<!-- SUBSECTION_BEGIN: clean-audit-114 -->
___
### Severity
- **Критично** ~10: Н21, Н22, Н25, Н29, Н38, Н40, Н44, Н53, Н63, Н71 (security).
- **Высоко** ~15: Н1, Н19, Н20, Н39, Н45, Н62, Н66, Н67, Н72, Н73, Н76, Н77, Н78, Н88, +1.
- **Средне** ~25.
- **Низко** ~24.
<!-- SUBSECTION_END: clean-audit-114 -->










<!-- SUBSECTION_BEGIN: clean-audit-115 -->
___
### Baseline: до / после
- **До сессии** (initial clean-audit 2026-10-26): 18 находок (L1–L3).
- **После** (2026-10-27): 74 находки (L1–L9).

**Что это даёт:**

- **Полная инвентаризация** — ни одна находка не потеряется, даже если задачи в ROADMAP отменятся (правило 2c, коммит `c9468c3`).
- **Дыры в инвентаризации закрыты** — backfill Н58–Н70 восстановил 13 пропусков.
- **Cross-level связи** — `**Анализ Lx-Нy:**` показывает, что `DebugConfig` протекает в 2 слоя (L1 + L5), `di.BrokerManager` — в 3 (L1 + L5 + L6), `toAppError` — в 3 (L2 + L5 + data).
<!-- SUBSECTION_END: clean-audit-115 -->










<!-- SUBSECTION_BEGIN: clean-audit-116 -->
___
### Что важно из находок (не очевидное)
1. **`di.BrokerManager` — центральная проблема.** 10 потребителей: 3 VM + 3 менеджера + 3 репозитория + 1 app. Тянет за собой Н22, Н29, Н40, Н44. Решение — `BrokerRegistry` (Н22) + `refreshInitializationState` как открытый вопрос (internal / interface / auto).
2. **Н71 (security) — `refreshToken` BcsBrokerApi логируется.** Уже в ISSUES приоритетно. Ждёт `bugfix/security-token-logging`.
3. **`BrokerApi` fat (Н25)** — не «осознанно», как KDoc утверждает, а **переходное состояние**. BCS подтверждает: `postOrder` работает, но `orders = null` делает его недоступным; `subscribePositions` → `emptyFlow()`. Расщеп `OrdersApi` на `Query/Command` — конкретное решение.
4. **`data/api` структурно критичен** (Н53). 17 файлов в одном пакете. Нужна раскладка `common/`, `tinvest/{services,mappers}`, `bcs/`, `finam/`.
5. **`util` — grab-bag** (Н17–Н20): presentation (`TradePhrases`), платформа (`AppLogger`), transport (`AppResultExtensions`), формат (`CurrencyUtils`). Используется в 3 слоях. Разъезд по слоям — большая задача.
<!-- SUBSECTION_END: clean-audit-116 -->










<!-- SUBSECTION_BEGIN: clean-audit-117 -->
___
### Рекомендация по приоритету исправления
План в ROADMAP: **H3 → Разбивка InvestRepository → H21b → H23 → util → H22 → H1/Н4/H24 → H5**.

Clean-audit подтверждает этот порядок и уточняет:

- **H3** (тривиально, 10 мин).
- **Разбивка InvestRepository** — разблокирует `domain.api.repository.*` для H21b (Н21, Н45).
- **H21b** — закрывает Н21 (часть) и Н44 (для `SearchCache`, `InstrumentRepository`).
- **H23** (`DebugFlagsRepository`) — закрывает Н23 (L1) **и** Н38 (L5, `PriceFlowManager` → `DebugConfig`). **Одна задача, два уровня.**
- **util-блок (Н17–Н20)** — закрывает `AppLogger` (Н19 + Н49), `AppResultExtensions` (Н20 + Н39), `TradePhrases` (Н17), `CurrencyUtils` (Н18 + Н55). **Одна umbrella, 4+ уточнения.**
- **H22** (`BrokerRegistry`) — закрывает Н22 (VM), Н29 (L7), Н40 (L5), Н44 (L6). **Одна задача — 4 уровня.**
- **H1/Н4/H24** — presentation-мелочи.
- **H5** (`PortfolioPosition`) — концептуальное разделение, последнее.

**Из L6-part2 (вне ROADMAP-плана), но приоритетно:**

- **Н71** (security) — отдельный bugfix. `bugfix/security-token-logging`.
- **Н76** (KDoc ссылается на несуществующую `### Реализация BCS Broker API`) — пробел в планировании.
- **Н62** (`TInvestBrokerAPI.initialize` не закрывает WS) — в ISSUES.
- **Н88** (missing `@Serializable`) — 5 минут.
- **Н53** (структура `data/api/`) — большая, но критичная.
<!-- SUBSECTION_END: clean-audit-117 -->










<!-- SUBSECTION_BEGIN: clean-audit-118 -->
___
### Метрики
| Метрика | 2026-10-27 | 2026-10-31 | Diff |
|---|---|---|---|
| Всего Н | 74 | 76 | +2 |
| Открыто Н | ~70 | 40 | −30 |
| Закрыто Н | 0 | 36 | +36 |
| Всего M | 0 | 12 | +12 (новая ось) |
| Открыто M | 0 | 12 | +12 |
| Закрыто M | 0 | 0 | 0 |
<!-- SUBSECTION_END: clean-audit-118 -->










<!-- SUBSECTION_BEGIN: clean-audit-119 -->
___
### Распределение Н по уровням
| Уровень | Всего | Открыто | Ключевые открытые |
|---|---|---|---|
| L1 presentation | 8 | Н94 | Н94 (god-функции Compose) |
| L2 util | 4 | 0 | все закрыты: Н17–Н20 |
| L3 domain/models | 7 | 5 | Н5, Н15, Н6, Н7, Н13 |
| L4 domain/api | 11 | 3 | Н27, Н28, Н30 |
| L5 domain/managers | 6 | 3 | Н41, Н58, Н59 |
| L6 data | 24 | 17 | Н45, Н53, Н64–Н69, Н72–Н81 |
| L7 di | 4 | 2 | Н36, Н37 |
| L8 app | 5 | 3 | Н48, Н51, Н52 |
| L9 transport-ktor | 7 | 6 | Н87, Н89–Н93 |
<!-- SUBSECTION_END: clean-audit-119 -->










<!-- SUBSECTION_BEGIN: clean-audit-120 -->
___
### MVVM (M001–M012) — новая ось
Введена в этой сессии, все 12 записей — L1 (presentation), все открыты.

| Тип | Кол-во | ID |
|---|---|---|
| M1 — слой/подслой делает не-свои обязанности | 4 | M001, M008, M010, M011 |
| M2 — business-logic в VM/UI (не в UseCase) | 5 | M002, M003, M004, M006, M007 |
| M3 — VM знает платформу | 1 | M005 |
| M5 — несколько StateFlow / дублирование | 2 | M009, M012 |
| M7 — конвертация domain↔presentation в VM | 1 (в составе M001) | M001 |

M001 — гибридная (M1 + M7), учтена в обеих строках.
<!-- SUBSECTION_END: clean-audit-120 -->










<!-- SUBSECTION_BEGIN: clean-audit-121 -->
___
### Diff с 2026-10-27
- **Н:** 74 → 76 (+2: Н94 — новый в L1; +1 — по списку открытых L-уровней).
- **Закрыто:** 0 → 36. Сессия 2026-10-27 → 2026-10-31: Пакеты A/B/C/D, H21b, H22, H23, H24, H25, H26, H29, H31, H32, H32a, H35, H38, H39, H40, H44, H46, H47, H49, H50, H60, H61, H62, H63, H68, H70, H71, H88, Н1, Н3, Н4, Н8, Н16, Н17–Н20.
- **M:** 0 → 12 (вся ось введена в этой сессии).
<!-- SUBSECTION_END: clean-audit-121 -->










<!-- SUBSECTION_BEGIN: clean-audit-122 -->
___
### Что важно из находок (не очевидное)
1. **Н5 (PortfolioPosition — presentation-поля)** — центральная проблема. Блокирует H5 (split модели). Связана с Н15 (TradingStateSnapshot) и `UI: blocked`. По факту кода: `PortfolioPosition` без `money`/`blocked`, `toPortfolioPosition`/`mergeWith` их не передают — разрыв в domain, не в UI.
2. **MVVM (M001–M012)** — 12 записей, все в L1. Связаны с Н94 (god-функции Compose, 10 функций / 5 файлов). `OrdersViewModel.postOrder` (M002) и `TInvestSettingsPanel` (M006) — самые дорогие (orchestration в VM/UI).
3. **L6 (24)** — самый большой уровень. Большая часть открытых (Н72–Н81) — Finam/BCS заглушки, закроются при реализации BCS/Finam API. **Н45, Н69** — вероятно, устарели после Пакета C (`BrokerApi: 0 fun`, `InvestRepository` удалён); требуют ревизии.
4. **KMP-блокер — `javax.inject` в 4 менеджерах** (`BrokerLifecycleManager`, `OrderFlowManager`, `PositionFlowManager`, `PriceFlowManager`), не 3, как в `docs/architecture.md` — `BrokerLifecycleManager` добавлен в PR #77 после KMP-аудита. `Dispatchers.IO` и `java.time` — чисто (PR #73, #74).
5. **Н53 (`data/api` — плоская структура)** — вероятно, частично закрыт: PR #84 (шаг 0) переехал `data/api/` по подпапкам (`tinvest/`, `bcs/`, `finam/`, `common/`). Требует ревизии.
<!-- SUBSECTION_END: clean-audit-122 -->










<!-- SUBSECTION_BEGIN: clean-audit-123 -->
___
### Рекомендация по приоритету
По цифрам отчёта (не по интуиции):

1. **A. `T-Invest REST: findInstruments fallback`** (топ-2 ROADMAP; топ-1 KMP блокирован Koin'ом) — ~1.5 ч, полное закрытие в proot, без блокеров.
2. **`UI: blocked` — domain-разрыв** — 3 правки в `Portfolio.kt` (модель, `toPortfolioPosition`, `mergeWith`), разблокирует H5. UI-санкция — отдельно.
3. **Koin** — разблокирует KMP (топ-1). ~8–12 ч, отдельная сессия (не «следующая задача»).
4. **Н45, Н53, Н69 — ревизия** (вероятно, закрыты после Пакета C) — ~30 мин, отдельным docs-коммитом.

Оценка отчёта: ~45 мин (включая эту запись).
<!-- SUBSECTION_END: clean-audit-123 -->









<!-- SECTION_END: clean-audit-reports -->
