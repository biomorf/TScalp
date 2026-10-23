##################################################
# Стримы в T-Invest API (gRPC)

Справочник по работе со стримами в TScalp. Документ описывает
успешные реализации и грабли, на которые мы наступали.

## Общие принципы

- **Разделяйте каналы (ManagedChannel)** для торговых операций и
  стримов, чтобы избежать взаимного влияния.
- **Используйте Kotlin-обёртки (`*GrpcKt`)**, если они доступны
  и нет конфликтов зависимостей.
- **При отсутствии Kotlin-стаба** стройте gRPC-вызов вручную через
  `MethodDescriptor` — это надёжно и не зависит от версий SDK.
- **Проверяйте доступность стримов в sandbox** — многие из них
  возвращают `UNAUTHENTICATED`.

## Реализованные стримы

### MarketDataStream (LastPrice)

- **Метод:** `subscribeLastPrices(uids: List<String>)`.
- **Канал:** `pricesStreamChannel` (отдельный от общего API-канала).
- **Реализация:** `MarketDataStreamServiceGrpc.newStub(channel)` +
  `StreamObserver`.
- **Статус:** работает в песочнице и боевом режиме.

### OrderStateStream

- **Метод:** `subscribeOrderState(accountId: String)`.
- **Канал:** `ordersStateChannel` (отдельный).
- **Реализация:** `OrdersStreamServiceGrpc.newStub(channel)`.
- **Статус:** работает в боевом режиме. В песочнице —
  `UNAUTHENTICATED: 40003`.

### PositionsStream

**Статус:** решён. gRPC-стрим реализован и работает в боевом
и песочнице. Основной источник изменений количества; polling
остаётся параллельным fallback'ом. См. `docs/ROADMAP.md` →
«gRPC-стрим позиций» (закрытая задача).

**История попыток:**

- **Попытка 1 (SDK 1.48.1):** Kotlin-stub через
  `OperationsStreamServiceGrpcKt.OperationsStreamServiceCoroutineStub`.
  Ошибка `Cannot access 'io.grpc.kotlin.AbstractCoroutineStub'`
  из-за конфликта `grpc-kotlin-stub`. Добавление явной зависимости
  `io.grpc:grpc-kotlin-stub:1.5.0` не помогло.
- **Попытка 2 (SDK 1.48.1):** ручной `MethodDescriptor` для
  `OperationsStreamService/PositionsStream`. Стрим запускается
  без ошибок компиляции, но данных нет:
    - песочница → `UNAUTHENTICATED: 40003`;
    - боевой → ошибок нет, но `hasPosition()` не приходит (пустой
      стрим).
- **Вывод по 1.48.1:** метод не удалось завести. Причина
  осталась невыясненной — старый SDK, права, либо поведение
  метода.

**Что дала разведка 1.51.0:**

- `OperationsStreamServiceAsync.positionsStream(request, vararg callbacks)`
  — доступно через `InvestApi.getOperationsStreamServiceAsync()`.
- `PositionsStreamRequest`: `addAccounts`, `setWithInitialPositions`,
  `setPingSettings`.
- `PositionsStreamResponse`: `hasInitialPositions` / `hasPosition` /
  `hasPing` / `hasSubscriptions`.
- `PositionData`: `moneyList`, `securitiesList`, `futuresList`,
  `optionsList`.
- `PositionsSecurities` / `PositionsFutures`: `figi`, `blocked`,
  `balance` (Long), `positionUid`, `instrumentUid`, `ticker`,
  `classCode`.

**Важно:** стрим даёт **только** изменения количества и
идентификаторы. Полей `currentPrice`, `averagePositionPrice`,
`expectedYield`, `pointValue` в стриме **нет**. Подробности —
в `docs/ROADMAP.md` → «gRPC-стрим позиций».

**Архитектурное решение — каскад:**

- **Уровень 1 — gRPC-стрим.** Первичный источник изменений
  количества. Даёт неполные `PositionStreamItem` (без P&L).
- **Уровень 2 — polling.** `broker.positionsSnapshot` раз в
  10 секунд (`PositionFlowManager.runPolling`). Даёт полные
  снимки с `averagePositionPrice`, `expectedYield`, `pointValue`.
- **Обогащение в `PositionFlowManager`.** Менеджер хранит
  последний полный снимок от polling. При gRPC-событии эмитит
  событие с `balance` из gRPC и остальными полями из кэша.
  UI получает консистентные данные и не мерцает.
- **Fallback.** При падении gRPC-стрима polling остаётся
  единственным источником. UI продолжает получать обновления
  с задержкой 10–30 секунд.

**Роль `PriceFlowManager`:** цены берутся оттуда, как и
раньше. Стрим позиций **не дублирует** подписку на last price.

**Состояние источника.** После рефакторинга (PR «StreamState
в data-слое») polling позиций перенесён из
`TInvestOrchestrator.positionsStream` в
`PositionFlowManager.runPolling` — все три менеджера
(`PositionFlowManager`, `PriceFlowManager`, `OrderFlowManager`)
агрегируют источники сами (stream + polling), оркестратор
владеет только транспортами. Каждый менеджер отдаёт
`connectionState: StateFlow<StreamState>` (`LIVE` / `POLLING` /
`STALE` / `STOPPED`). UI-индикатор — отдельная задача
(ROADMAP → «UI-индикатор состояния стримов»).

## WebSocket (T-Invest)

Стриминг через WebSocket — альтернатива gRPC-стримам для тех же
каналов (`PositionsStream`, `MarketDataStream`, `OrdersStream`).
Реализован в `:transport-ktor` (`TInvestPositionsWebSocket`),
движок Ktor CIO.

### Адрес

**Канал — часть URL path**, а не только тело подписки:

    # Prod
    wss://invest-public-api.tbank.ru/ws/tinkoff.public.invest.api.contract.v1.OperationsStreamService/PositionsStream

    # Sandbox
    wss://sandbox-invest-public-api.tbank.ru/ws/tinkoff.public.invest.api.contract.v1.OperationsStreamService/PositionsStream

Голый `/ws` без канала возвращает 404 / timeout / silent hang.
Подтверждено end-to-end через websocat (`POSITIONS_SUBSCRIPTION_STATUS_SUCCESS`
и `initialPositions` с реальными securities).

**Маршрутизация по режиму.** В `TInvestBrokerAPI.buildWsUrl(sandbox)`
URL выбирается по флагу: prod → `invest-public-api.tbank.ru`,
sandbox → `sandbox-invest-public-api.tbank.ru`. Без этого
sandbox-токен уходил на прод-хост, и сервер закрывал соединение
сразу после подписки.

### Авторизация

Два допустимых способа (по документации T-Invest):

- `Authorization: Bearer <token>` — HTTP-заголовок, работает.
- `Web-Socket-Protocol: json, <token>` — токен в заголовке
  протокола, тоже допустим, но не проверяли.

Сейчас в коде используется `Authorization`.

### Формат сообщений: json или json-proto

T-Invest поддерживает два режима через заголовок
`Web-Socket-Protocol`:

- **`json`** (camelCase) — стандартный protobuf JSON mapping.
  Поля: `initialPositions`, `instrumentUid`, `classCode`,
  `withInitialPositions`.
- **`json-proto`** (snake_case) — имена как в proto-контрактах.
  Поля: `initial_positions`, `instrument_uid`, `class_code`,
  `with_initial_positions`.

**Выбрано: `json`.** Архитектурное обоснование:

1. **Не протекает proto в транспорт.** `:transport-ktor` — KMP-
   модуль, без зависимости от `ru.tinkoff.piapi`. Если бы выбрали
   `json-proto` ради совпадения имён с proto-классами, единственный
   сценарий выгоды — втащить `ru.tinkoff.piapi` внутрь transport,
   чтобы делить типы с gRPC. Это ломает KMP-совместимость
   (T-Invest SDK — Android-only) и убивает цель модуля.
2. **Согласованность с другими JSON-слоями.** BCS и Finam отдают
   camelCase (REST-конвенция). Если WS будет snake_case, в
   `data/api/` окажутся DTO двух стилей — лишняя когнитивная цена.
3. **Читаемость логов и документации.** `json` совпадает с
   asyncapi-примерами T-Invest и с тем, что приходит в websocat.
   `json-proto` выглядит как внутренности proto, случайно
   просочившиеся наружу.
4. **Простая точка перехода, если понадобится.** Смена режима —
   одна строка в заголовке + переименование `@SerialName`
   в DTO. Не архитектурный разворот, а локальная правка.

**Когда `json-proto` стал бы лучше:** если бы `:transport-ktor`
был не отдельным слоем, а тонкой обёрткой над gRPC-proto, и мы
были бы готовы втащить `ru.tinkoff.piapi` внутрь него. У нас —
два разных слоя по дизайну, и `json` эту границу уважает.

### Коды закрытия WS

Сервер T-Invest использует стандартные WebSocket close codes
(1000–4999) плюс собственные application-коды в reason:

- **`1013 Try Again Later`** — соединение открыто, но подписка
  не пришла за ~10 секунд. Сервер закрывает по таймауту ожидания
  подписки. В нашем клиенте не возникает — `TInvestPositionsWebSocket`
  отправляет подписку сразу после `connected`.
- **`4001 Unauthorized`** — handshake прошёл, но токен не принят
  в `Authorization`. Проверить токен / режим (sandbox vs prod).
- **`1011` + `reason: "50004"`** — `NOT_FOUND` (счёт не существует).
  Проверить accountId: соответствует ли он токену.

### Sandbox WS не отдаёт данные

В песочнице сервер принимает handshake и подписку, но **сразу
закрывает соединение без единого кадра** (`Received WebSocket
close message`, без `subscriptions`, без `initialPositions`,
без кода ошибки). Подтверждено `websocat -k` с тем же URL,
токеном и payload — идентичный результат. Это ограничение
сервера, не клиента.

В песочнице данные идут через gRPC + polling; WS молча
закрывается, оркестратор продолжает получать данные от двух
других источников. Для UI прозрачно. Запись:
`docs/ISSUES.md` → «PositionsStream over WS не отдаёт данные
в песочнице».

### Подписка: encodeDefaults

`withInitialPositions` в DTO имеет default `true`. Без
`encodeDefaults = true` в `Json` kotlinx.serialization
**не сериализует поле с default-значением** — payload уходит
коротким (`{"accounts":["..."]}`, 27 байт вместо 75), и сервер
не присылает `initialPositions` (только `subscriptions`).

Лечится `Json { ignoreUnknownKeys = true; encodeDefaults = true }`
в `TInvestPositionsWebSocket`.

### Money и blocked: раздельно, без формулы

Валютные остатки приходят двумя account-level массивами:
`money` (свободные) и `blocked` (заблокированные), каждый
элемент — `MoneyValue` с одним полем `currency` (без uid и
ticker).

`TInvestPositionsWsMapper` группирует `money` и `blocked`
по `currency` в один `PositionStreamItem` на валюту
(`instrumentType = "currency"`, пустой `tscalpInstrumentId`).
`PositionFlowManager.enrichAndCache` склеивает этот item
с валютной позицией из polling по полю `currency`.

`money` и `blocked` хранятся на `PositionStreamItem`
**раздельно**. Формула связи `money` ↔ `blocked` ↔ `quantity`
не подтверждена по документации — интерпретация отложена до
UI-задачи (см. `docs/ROADMAP.md` → «UI: отображение blocked
на валютной карточке»).

### Связанные документы

- `docs/ISSUES.md` → «T-Invest WebSocket: решён — канал должен
  быть в URL path» — root cause, матрица экспериментов,
  коды закрытия.
- `docs/ISSUES.md` → «PositionsStream over WS не отдаёт данные
  в песочнице» — sandbox-ограничение сервера.
- `docs/ROADMAP.md` → «Оркестратор стримов и Ktor WS transport» —
  план фаз 2 и 3.

---

## Рекомендации

- **Правильное имя метода:** `OperationsStreamService.positionsStream`
  (через `OperationsStreamServiceAsync`). `PositionsStreamServiceGrpc`
  не существует.
- **Каскад, а не единый источник.** Любой стрим, который может
  упасть, оставляем с fallback'ом. Для позиций: gRPC → polling.
  Для цен: gRPC + polling параллельно (см. `PriceFlowManager`).
- **Обогащение — в менеджере, не в UI.** Если источник отдаёт
  неполные данные (только часть полей), обогащение — задача
  посредника между брокером и UI (`PositionFlowManager`,
  `PriceFlowManager`). UI получает консистентные данные.
- **P&L подтягивается с polling.** Стрим позиций не даёт
  `expectedYield` / `averagePositionPrice`. Это не баг стрима —
  он для количества. P&L — отдельный слой (см.
  `docs/ROADMAP.md` → «Рефакторинг расчёта P&L»).
- При использовании ручного gRPC всегда логировать `onClose`-статус
  и `response.allFields` для диагностики.
- Для песочницы предусмотреть fallback-режим, отключающий стримы,
  которые не поддерживаются.



####################################################
