markdown

\# ISSUES: protobuf-field-descriptors



\*\*Контекст\*\*  

При реализации методов `getOrders()` и `getStopOrders()` в `TInvestService` возникли систематические проблемы с доступом к полям protobuf-сообщений:

\- `Unresolved reference` для геттеров `order.price`, `order.stopOrderType`, `order.createDate`

\- Конфликт типов `String!` (platform type) vs `kotlin.String`

\- Невозможность прямого вызова `addAllStatus()` из‑за отсутствия метода в конкретной версии SDK



\*\*Причина\*\*  

Сгенерированные Java-классы protobuf могут не экспонировать все поля через геттеры, особенно если используются разные версии контракта или обёртки. Кроме того, platform‑тип `String!` не всегда приводится к `kotlin.String` без явного `as String`.



\*\*Решение\*\*  

Использовать protobuf-дескрипторы полей (`FieldDescriptor`) для прямого доступа к любому полю сообщения, независимо от доступности геттеров.  

Шаблон (на примере поля `order\_type` в `StopOrder`):

```kotlin

val fieldDescriptor = order.descriptorForType.findFieldByName("order\_type")

val value = fieldDescriptor?.let { order.getField(it) }

// Приведение к нужному типу, например EnumValueDescriptor

val typeName = (value as? EnumValueDescriptor)?.name?.removePrefix("PREFIX\_")

Аналогично для create\_date, price и других полей.

Для строк: явное приведение as String после проверки на null.

Этот подход не зависит от версии SDK и гарантирует компиляцию.



Что нужно помнить при обновлении SDK



Проверять, не появились ли нативные геттеры для нужных полей – тогда можно упростить код.



Имена полей в дескрипторе соответствуют protobuf-определению (использовать snake\_case, как в .proto файле).



При падении в рантайме – включить логирование Log.d(TAG, "allFields: ${message.allFields}"), чтобы увидеть реальные имена полей.



Дата: 2026-04-30

Автор: совместная отладка









# INVALID_ARGUMENT: 30052 при торговле акциями (Instrument forbidden for trading by API)

## Статус
**Открыт.** Локализован на стороне API Т‑Инвестиций после исчерпывающей диагностики на клиенте.

## Краткое описание
Заявки на покупку/продажу акций (любых тикеров) завершаются ошибкой `INVALID_ARGUMENT: 30052`.
Фьючерсы работают корректно в том же приложении, с теми же токенами и счетами.

## Хронология отладки (2026-05-07)
1. **Локализация `checkTradeAvailability`** – метод возвращал `TradeCheckResult.Error("Покупка недоступна")`, блокируя отправку.
   - Причина: запрос `GetTradingStatus` по `figi` возвращал `buyAvailable=false` для акций.
   - Решение: временно закомментировали проверки `buyAvailable`/`sellAvailable`; позже добавили fallback-запрос статуса по `uid`.
2. **Маржинальные проверки** – `GetMarginAttributes` в песочнице возвращал `UNAUTHENTICATED: 40003`, что прерывало проверку.
   - Решение: при любой ошибке маржинального запроса считаем флаги `false` и продолжаем, не блокируя заявку.
3. **Идентификатор инструмента** – для акций API требует `instrument_uid` вместо `figi`.
   - Решение: в `BrokerOrderRequest` и `StopOrderRequest` добавлено поле `instrumentUid`; `postOrder` приоритетно использует `uid`, если он передан; для фьючерсов временно оставлен `figi`.
4. **Параметр `price` в рыночной заявке** – документация требует ненулевое значение цены даже для рынка.
   - Решение: для рыночных заявок передаётся `Quotation(units=1, nano=0)`.
5. **Разделение gRPC-каналов** – стрим LastPrice обрывался при отправке заявки, влияя на другие запросы.
   - Решение: выделены независимые каналы `pricesStreamChannel` и `ordersStateChannel`.
6. **Тест с принудительным uid для всех инструментов** – убрали fallback на `figi`, везде передаётся только `setInstrumentId(uid)`. Фьючерсы продолжили работать, акции – нет.
7. **Сравнение запросов** – структура `PostOrderRequest` для акции и фьючерса идентична (лог `postOrder request fields`). Цена ненулевая, `confirmMarginTrade=true`, `instrument_id` корректен.

**Вывод:** клиентский код полностью корректен. Ошибка возникает на стороне сервера Т‑Инвестиций до формирования ответа (нет `postOrder response` в логах).

## Дополнительные наблюдения
- Нативное приложение Т‑Инвестиций исполняет акции без ошибок.
- Токен имеет максимальные права (полный доступ, статус квалифицированного инвестора).
- Песочница и боевой режим ведут себя одинаково.
- Ошибка воспроизводится для всех tested акций: `SBER`, `GAZP`, `BBG004730N88`.

## Возможные причины на стороне API
- Ограничение на уровне договора (счёт не предназначен для торговли акциями).
- Некорректная сессия или расписание торгов для акций (хотя `tradingStatus=NORMAL_TRADING`).
- Изменение поведения API без обновления SDK (используем `kotlin-sdk-grpc-core:1.48.1`).

## Дальнейшие шаги
1. **Обратиться в поддержку Т‑Инвестиций** с предоставлением полного дампа запроса `PostOrderRequest` и ответа сервера (включая `tracking_id` из фьючерсного ответа для сравнения).
2. **Проверить доступность акций через другой SDK/gRPC-клиент** (например, `grpcurl`) с тем же токеном и параметрами, чтобы подтвердить проблему вне нашего приложения.
3. **Обновить SDK** до актуальной версии, когда она станет доступна, – возможно, исправлена несовместимость.
4. **После решения на стороне API** убрать временные заглушки в `checkTradeAvailability` (раскомментировать проверки `buyAvailable`/`sellAvailable`).

---

_Дата последнего обновления: 2026-05-07_



# ISSUE: PositionsStream не доставляет данные (P&L не обновляется)

**Статус:** Отложен  
**Дата:** 2026-05-08  
**Приоритет:** Низкий (функциональность P&L не критична, можно смотреть в других приложениях)

---

## Контекст
Пытались внедрить `PositionsStream` для отображения прибыли/убытка (P&L) на карточках инструментов в реальном времени. Стрим должен был доставлять `expected_yield`, `average_position_price`, `current_price` и другие поля позиций.

---

## Что сделано
1. **Модель `PositionStreamItem`** — содержит `instrumentUid`, `ticker`, `quantity`, `currentPrice`, `averagePositionPrice`, `expectedYield`.
2. **Метод `subscribePositionsStream`** добавлен в `BrokerApi` и реализован в `TInvestService` через универсальный gRPC-вызов (из-за отсутствия `PositionsStreamServiceGrpc` в текущей версии SDK).
3. **Интеграция в `OrdersViewModel`** — подготовлен метод `updatePositionPnl`, который обновляет `profit`/`profitPercent` в `portfolioPositions`. PositionsStream запускается отдельно от стрима цен.

---

## Проблемы
- **Песочница:** ошибка `UNAUTHENTICATED: 40003` сразу после запуска стрима. Метод, вероятно, недоступен в sandbox-окружении.
- **Боевой режим:** ошибок аутентификации нет, но данных `hasPosition()` не поступает. Возможно, стрим несовместим с текущей версией SDK (1.48.1) или требуется другой способ подписки (например, через `OperationsStreamServiceGrpc`, который отсутствует).
- **Логирование:** Сообщения `updatePositionPnl` не появляются, что подтверждает отсутствие входящих данных.

---

## Возможные решения (на будущее)
1. **Обновить SDK** до версии, где `PositionsStreamServiceGrpc` станет доступен, и использовать официальный `OperationsStreamServiceCoroutineStub`.
2. **Исправить универсальный gRPC-вызов:** проверить правильность имени метода (`OperationsStreamService/PositionsStream`) и параметров.
3. **Временно использовать fallback-расчёт P&L** — вычислять прибыль на клиенте на основе `averagePositionPrice` из `getPositions` и текущей цены из `subscribeLastPrices`. Это даст работающий P&L без стрима.
4. **Проверить доступность метода в документации API** Т‑Инвестиций — возможно, `PositionsStream` не входит в стандартный набор или требует отдельных прав.

---

## Дополнительно
- Функциональность P&Л не критична, поэтому задача отложена.
- Код стрима и обновления позиций сохранён, может быть легко активирован после обновления SDK или исправления gRPC-вызова.




# Настройка стримов в T-Invest API (gRPC)

**Статус:** Рабочие стримы (`MarketDataStream`, `OrderStateStream`) успешно внедрены.  
`PositionsStream` отложен до обновления SDK.

---

## Общие принципы

- **Разделяйте каналы (ManagedChannel)** для торговых операций и стримов, чтобы избежать взаимного влияния.
- **Используйте Kotlin-обёртки (`*GrpcKt`),** если они доступны и нет конфликтов зависимостей.
- **При отсутствии Kotlin-стаба** стройте gRPC-вызов вручную через `MethodDescriptor` – это надёжно и не зависит от версий SDK.
- **Всегда проверяйте доступность стримовых методов в sandbox-окружении** – многие из них возвращают `UNAUTHENTICATED`.

---

## Реализованные стримы

### 1. `MarketDataStream` (LastPrice)
- **Метод:** `subscribeLastPrices(uids: List<String>)`
- **Канал:** `pricesStreamChannel`
- **Особенности:** использует `MarketDataStreamServiceGrpc.newStub(channel)` и `StreamObserver`.
- **Статус:** Работает в песочнице и боевом режиме.

### 2. `OrderStateStream`
- **Метод:** `subscribeOrderState(accountId: String)`
- **Канал:** `ordersStateChannel`
- **Особенности:** использует `OrdersStreamServiceGrpc.newStub(channel)`.
- **Статус:** Работает в боевом режиме; в песочнице `UNAUTHENTICATED`.

---

## Проблемный стрим: `PositionsStream`

### Попытка 1: Kotlin-stub
- Использовали `OperationsStreamServiceGrpcKt.OperationsStreamServiceCoroutineStub`.
- **Ошибка:** `Cannot access 'io.grpc.kotlin.AbstractCoroutineStub'` из-за конфликта `grpc-kotlin-stub`.
- **Решение:** Добавили `implementation("io.grpc:grpc-kotlin-stub:1.5.0")`, но ошибка сохранилась.

### Попытка 2: Ручной gRPC-вызов
- Построили `MethodDescriptor` для `OperationsStreamService/PositionsStream`.
- **Результат:** Стрим запускается без ошибок компиляции.
- **Песочница:** `UNAUTHENTICATED: 40003`.
- **Боевой режим:** Ошибок нет, но данные `hasPosition()` не поступают (пустой стрим).

### Вывод
- `PositionsStream` не поддерживается в текущей версии SDK (1.48.1).
- Возможно, метод не входит в стандартный набор прав или требует отдельного подключения.
- **Решение:** Отложить до обновления SDK.

---

## Альтернатива для P&L (без PositionsStream)
Если нужно отображать прибыль/убыток, можно вычислять её на клиенте:
- В `getPositions` заполнять `averagePositionPrice` из API.
- При каждом обновлении цены (стрим `LastPrice`) пересчитывать `profit = (currentPrice - avgPrice) * quantity` и `profitPercent`.
- Этот подход не зависит от PositionsStream и работает уже сейчас.

---

## Рекомендации на будущее

- При обновлении SDK проверить наличие `PositionsStreamServiceGrpc` – тогда использовать официальный Kotlin-stub.
- При использовании ручного gRPC всегда логировать `onClose` статус и `response.allFields` для диагностики.
- Для песочницы предусмотреть fallback-режим, отключающий стримы, которые не поддерживаются.




markdown
# ISSUE: Отсутствие данных стоимости одного пункта цены для фьючерсов в API Т-Инвестиций

## Описание проблемы
При работе с фьючерсами через API Т-Инвестиций (`T-Invest API`) поле `min_price_increment_amount` (стоимость шага цены) не заполняется в ответе метода `getInstrumentByUid`.  
Из-за этого:

- Невозможно вычислить стоимость одного пункта цены (`pointValue`) для фьючерсов.
- Рублёвый эквивалент прибыли/убытка и текущей цены не отображается в карточках портфеля и на вкладке заявок.
- Карточки на разных экранах (Портфель, Заявки) показывали разные данные: на одном рублёвый эквивалент был, на другом – отсутствовал.

**Технические детали**  

- Используется Kotlin SDK `kotlin-sdk-grpc-core` версии 1.48.1.
- Метод `GetFuturesMargin` (из того же SDK) возвращает `min_price_increment_amount`, но требует отдельного вызова.
- Ранее инструменты маппились в общий класс `InstrumentUi`, который не имел специализированных полей для фьючерсов.

## Реализованное решение

### 1. Специализированные доменные модели
Созданы классы-наследники `InstrumentUi`:

- `FutureUi` – для фьючерсов, содержит вычисляемое свойство `pointValue` (стоимость пункта).
- `ShareUi` – для акций (аналогичная структура, но без `pointValue`).

```kotlin
data class FutureUi(..., val minPriceIncrement: Double?, val minPriceIncrementAmount: Double?) : InstrumentUi(...) {
    val pointValue: Double get() {
        val inc = minPriceIncrement ?: 0.0
        val amount = minPriceIncrementAmount ?: 0.0
        return if (inc > 0.0 && amount > 0.0) amount / inc else 1.0
    }
}
##2. Получение стоимости шага цены через API
В TInvestInvestService добавлен метод getFuturesMargin(figi):

kotlin
private suspend fun getFuturesMargin(figi: String): Double? {
    val request = GetFuturesMarginRequest.newBuilder().setFigi(figi).build()
    val response = currentApi.instrumentsServiceSync.getFuturesMargin(request)
    return response.minPriceIncrementAmount?.let { it.units + it.nano / 1_000_000_000.0 }
}
##3. Централизованный источник полных данных
Создан InstrumentRepository – in‑memory кэш, который для каждого tscalpInstrumentId (uid) хранит полностью загруженный InstrumentUi (с реальным pointValue для фьючерсов).

Метод fetchFullInstrument объединяет:

загрузку protobuf-инструмента (fetchProtoInstrument)

при необходимости – вызов getFuturesMargin

маппинг в доменную модель (mapProtoToDomain)

kotlin
class InstrumentRepository {
    suspend fun getInstrument(uid: String): InstrumentUi? { ... }
}
##4. Интеграция репозитория во ViewModel и UI
OrdersViewModel – при выборе инструмента сразу получает актуальный InstrumentUi через репозиторий.

OrdersScreen / PortfolioScreen – для создания карточки используется PortfolioPosition из портфеля (если есть), иначе – временная позиция, но с обязательным заполнением pointValue из FutureUi.

Все экраны теперь используют один и тот же объект InstrumentUi из кэша.

##5. Переименование методов для ясности
В TInvestInvestService:

getInstrumentByUid → fetchProtoInstrument

mapInstrumentToUi → mapProtoToDomain

getInstrumentUiByUid → fetchFullInstrument

Это явно отделяет слой работы с protobuf от доменного слоя.

Результат
Единообразное отображение – рублёвый эквивалент показывается на всех вкладках (Портфель, Заявки) одинаково.

Консистентность данных – карточки всегда строятся из одного источника (InstrumentRepository).

Чистая архитектура – разделение ответственности между сервисом, репозиторием и UI.

Масштабируемость – добавление новых типов инструментов (опционы, облигации) не потребует переписывания Presentation-слоя.

text







