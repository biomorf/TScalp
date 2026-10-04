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

- **Статус:** не реализован. Отложен до обновления SDK.
- **Попытка 1:** Kotlin-stub через
  `OperationsStreamServiceGrpcKt.OperationsStreamServiceCoroutineStub`.
  Ошибка `Cannot access 'io.grpc.kotlin.AbstractCoroutineStub'`
  из-за конфликта `grpc-kotlin-stub`. Добавление явной зависимости
  `io.grpc:grpc-kotlin-stub:1.5.0` не помогло.
- **Попытка 2:** ручной `MethodDescriptor` для
  `OperationsStreamService/PositionsStream`. Стрим запускается
  без ошибок компиляции, но данных нет:
    - песочница → `UNAUTHENTICATED: 40003`;
    - боевой → ошибок нет, но `hasPosition()` не приходит (пустой
      стрим).
- **Вывод:** метод не поддерживается в `kotlin-sdk-grpc-core:1.48.1`
  либо требует отдельных прав.

**Текущая альтернатива:** polling через
`SharedPositionStreamManager` с периодом 10 секунд. P&L корректно
отображается с задержкой.

## Рекомендации

- При обновлении SDK проверить наличие `PositionsStreamServiceGrpc`
  и перейти на официальный Kotlin-stub.
- При использовании ручного gRPC всегда логировать `onClose`-статус
  и `response.allFields` для диагностики.
- Для песочницы предусмотреть fallback-режим, отключающий стримы,
  которые не поддерживаются.



####################################################
