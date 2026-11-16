<!-- SECTION_BEGIN: clean-audit-L9 -->
## L9 — transport-ktor (отдельный модуль)

<!-- SUBSECTION_BEGIN: Н87 -->
___
### Н87 Н87. TInvestPositionsWebSocket — cold flow, риск дублирования соединений

**Статус:** открыто.

**Серьёзность:** средне.

**Live тест:** ✓

**Описание:** `TInvestPositionsWebSocket.subscribePositions(accountId)` возвращает `flow { ... }` (cold). Каждый подписчик → **новое** WS-соединение. В отличие от `TInvestPricesWebSocket.prices` (hot SharedFlow, один upstream на любое число коллекторов) и `TInvestOrderStateWebSocket.states` (hot SharedFlow). Сейчас один подписчик (через оркестратор), но архитектурный риск: второй подписчик = второе WS к T-Invest (rate-limit, утечка).

**Нарушенное правило Clean:** не строго Clean — консистентность архитектурных решений между тремя WS-клиентами.

**Clean-альтернатива:** hot SharedFlow как в `TInvestPricesWebSocket` (единый upstream) или KDoc с явным предупреждением «один подписчик». Плюсы: консистентность, защита от дублирования. Минусы: переделка модели.

**Выгоды:** cold flow проще; `withInitialPositions = true` не требует дополнительной подписки.

**Ссылка на задачу:** — (не заведена). Связано с закрытой `### Add stop() to TInvestOrderStateWebSocket` (прецедент hot vs cold).

<!-- SUBSECTION_END: Н87 -->

<!-- SUBSECTION_BEGIN: Н89 -->
___
### Н89 Н89. TInvestRestTransport — DTO в одном файле

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `TInvestRestTransport.kt` — 220 строк, содержит сам транспорт + 14 DTO. Отдельный файл `dto/TInvestRestDto.kt` был бы чище и совпадал с другими DTO (`dto/PositionsStreamDto.kt`, `dto/MarketDataStreamDto.kt`, `dto/OrderStateStreamDto.kt`).

**Нарушенное правило Clean:** не строго Clean — организация файлов.

**Clean-альтернатива:** `dto/TInvestRestDto.kt`. Плюсы: консистентность. Минусы: `git mv` + правка импортов в `TInvestRestService.kt` и `RestMappers.kt`.

**Выгоды:** —.

**Анализ L9-part2-Н89:** подтверждается — контраст наглядный: в `transport-ktor/dto/` DTO вынесены правильно (`PositionsStreamDto.kt`, `MarketDataStreamDto.kt`, `OrderStateStreamDto.kt`), а в `app/data/api/TInvestRestTransport.kt` — 14 DTO в одном файле с транспортом.

**Ссылка на задачу:** — (не заведена). Связано с Н53 (`data/api/` структура).

<!-- SUBSECTION_END: Н89 -->

<!-- SUBSECTION_BEGIN: Н90 -->
___
### Н90 Н90. RestDto — избыточные @SerialName

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `TInvestRestTransport.kt`: большинство `@SerialName("...")` совпадает с именем поля (`@SerialName("orderId") val orderId`, `@SerialName("lotsRequested") val lotsRequested`). Это шум. Исключение — поля, где реально нужно маппинг (нужно свериться с API).

**Нарушенное правило Clean:** не строго Clean — шум.

**Clean-альтернатива:** удалить избыточные `@SerialName`. Плюсы: чисто. Минусы: нужно свериться с API, чтобы не сломать реальный маппинг.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена).

<!-- SUBSECTION_END: Н90 -->

<!-- SUBSECTION_BEGIN: Н91 -->
___
### Н91 Н91. createTransportHttpClient — dead code

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `expect fun createTransportHttpClient()` в `commonMain` + `actual` в `androidMain` (OkHttp engine). Grep 2026-10-27: **0 вызовов** в `app/src/` и `transport-ktor/src/`. `app/src/main/kotlin/.../di/DataModule.kt` создаёт `HttpClient` вручную (`provideTInvestWsHttpClient`, CIO engine). `createTransportHttpClient` никогда не вызывается.

**Нарушенное правило Clean:** не строго Clean — dead code.

**Clean-альтернатива:** удалить `HttpClientFactory.kt` + `HttpClientFactory.android.kt`; **или** перевести `provideTInvestWsHttpClient` на `createTransportHttpClient()` (тогда KMP-фабрика используется по назначению, а DI-конфиг сокращается). Плюсы: без dead code, единая фабрика. Минусы: правка `DataModule`.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Связано с Н36 (транспортная конфигурация в di).

---

<!-- SUBSECTION_END: Н91 -->

<!-- SUBSECTION_BEGIN: Н92 -->
___
### Н92 Н92. Общие DTO между WS-каналами — в PositionsStreamDto.kt

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** Общие DTO живут в `PositionsStreamDto.kt`, но используются в нескольких WS-каналах:
- `QuotationDto` — `PositionsStreamDto.kt` и `MarketDataStreamDto.kt`;
- `PingDelaySettingsDto` — `PositionsStreamDto.kt` и `MarketDataStreamDto.kt`;
- `PingDto` — `PositionsStreamDto.kt`, `MarketDataStreamDto.kt`, `OrderStateStreamDto.kt`;
- `MoneyValueDto` — `PositionsStreamDto.kt` и `OrderStateStreamDto.kt`.

Кросс-зависимость между файлами каналов.

**Нарушенное правило Clean:** не строго Clean — организация DTO.

**Clean-альтернатива:** вынести общие DTO в `dto/CommonDto.kt`. Плюсы: изоляция каналов, отсутствие кросс-импортов. Минусы: правка импортов в 3 файлах каналов + мапперах.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Связано с Н53 (`data/api/` структура).

<!-- SUBSECTION_END: Н92 -->

<!-- SUBSECTION_BEGIN: Н93 -->
___
### Н93 Н93. PositionsStreamDto — устаревшие комментарии-гипотезы

**Статус:** открыто.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `PositionsStreamDto.kt` содержит два комментария, устаревших после live-теста 2026-10-19:
1. «ВНИМАНИЕ: точная структура конверта подписки (есть ли внешний ключ "positionsStream" или поля идут плоско) в спецификации не приведена. Гипотеза — обёртка с именем канала или метода. Проверить при первой реализации отправки.»
2. «Гипотеза: protobuf JSON mapping передаёт int64 как string. Если окажется long — поправить на Long?.»

Оба вопроса решены: отправка работает end-to-end (ISSUES: «T-Invest WebSocket: решён — канал должен быть в URL path»), `balance` — строка (`String?`). Комментарии вводят в заблуждение.

**Нарушенное правило Clean:** не строго Clean — актуальность документации.

**Clean-альтернатива:** удалить оба комментария. Плюсы: KDoc соответствует реальности. Минусы: —.

**Выгоды:** —.

**Ссылка на задачу:** — (не заведена). Связано с `docs/ISSUES.md` → «T-Invest WebSocket: решён...».

---

<!-- SUBSECTION_END: Н93 -->

<!-- SECTION_END: clean-audit-L9 -->
