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

**Статус:** в работе. Разведка артефакта `kotlin-sdk-grpc-core:1.51.0`
завершена, SDK содержит всё нужное. Реализация — в ветке
`feature/positions-grpc-stream`.

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
- **Уровень 2 — polling.** `fetchPositionsSnapshot` раз в
  10–30 секунд. Даёт полные снимки с `averagePositionPrice`,
  `expectedYield`, `pointValue`.
- **Обогащение в `PositionStreamManager`.** Менеджер хранит
  последний полный снимок от polling. При gRPC-событии эмитит
  событие с `balance` из gRPC и остальными полями из кэша.
  UI получает консистентные данные и не мерцает.
- **Fallback.** При падении gRPC-стрима polling остаётся
  единственным источником. UI продолжает получать обновления
  с задержкой 10–30 секунд.

**Роль `PriceStreamManager`:** цены берутся оттуда, как и
раньше. Стрим позиций **не дублирует** подписку на last price.

**Текущая альтернатива (до реализации):** polling через
`PositionStreamManager` с периодом 10 секунд. P&L корректно
отображается с задержкой.

## Рекомендации

## Рекомендации

- **Правильное имя метода:** `OperationsStreamService.positionsStream`
  (через `OperationsStreamServiceAsync`). `PositionsStreamServiceGrpc`
  не существует.
- **Каскад, а не единый источник.** Любой стрим, который может
  упасть, оставляем с fallback'ом. Для позиций: gRPC → polling.
  Для цен: gRPC + polling параллельно (см. `PriceStreamManager`).
- **Обогащение — в менеджере, не в UI.** Если источник отдаёт
  неполные данные (только часть полей), обогащение — задача
  посредника между брокером и UI (`PositionStreamManager`,
  `PriceStreamManager`). UI получает консистентные данные.
- **P&L подтягивается с polling.** Стрим позиций не даёт
  `expectedYield` / `averagePositionPrice`. Это не баг стрима —
  он для количества. P&L — отдельный слой (см.
  `docs/ROADMAP.md` → «Рефакторинг расчёта P&L»).
- При использовании ручного gRPC всегда логировать `onClose`-статус
  и `response.allFields` для диагностики.
- Для песочницы предусмотреть fallback-режим, отключающий стримы,
  которые не поддерживаются.



####################################################
