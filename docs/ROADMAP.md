# ROADMAP

План задач проекта TScalp (разработка): что в работе, что
запланировано, что отложено. Формат — секция на задачу,
со статусом и оценкой.

CI/CD-планирование → `docs/ci-roadmap.md`.


## Планируемые задачи

---

### Оркестратор стримов и Ktor WS transport

**Мобильная работа:** частичное (правки `.kt`/`.kts`/`.md` — через proot; сборка и проверка WS — на десктопе).

**Статус:** в работе.

**Контекст:** три транспорта (gRPC, Ktor WS, Ktor REST) × несколько стримов (позиции, цены, ордера) × три брокера (T-Invest, BCS, Finam). Без общего движка композиции логика fallback размазывается по брокерам. Оркестратор — единая точка композиции для каждого брокера.

**Архитектурная схема — два уровня, fallback внутри каждого:**

```
                  Стрим-уровень              Polling-уровень
                  (мгновенные обновления)    (снимки раз в 10 сек)
TInvest:          gRPC-stream → WS           gRPC-unary → REST
BCS:              WS                        REST
Finam:            gRPC-stream               REST

                  └────────── merge() ───────────┘
                                  ↓
                          Общий поток событий
                                  ↓
                          PositionStreamManager
                                  ↓
                              UI
```

Уровни работают параллельно. Внутри каждого — primary и fallback. У T-Invest все три транспорта, у BCS — WS + REST, у Finam — gRPC-stream + REST.

**Ключевые принципы:**

- Оркестратор — без Hilt-аннотаций, без Android-импортов. Готов к переносу в KMP-модуль (Уровень 1 iOS-совместимости).
- Hilt-обвязка — в `:app/di/`.
- Транспорты не знают про домен. Оркестратор знает про домен.
- `streamWithFallback` — общая утилита в `domain/api/StreamCombinators.kt`.
- Отсутствующие транспорты — `null` (ветка замьючена).

**Псевдокод оркестратора (T-Invest):**

```kotlin
fun positionsStream(accountId: String): Flow<PositionStreamItem> =
    merge(
        streamWithFallback(
            grpc.positionsStream(accountId),
            ktor.wsPositionsStream(accountId),
        ),
        streamWithFallback(
            grpc.positionsPolling(accountId),
            ktor.restPositionsPolling(accountId),
        ),
    )
```

**Предусловия:** нет.

**Фазы:**

**Фаза 1 — Оркестратор (рефакторинг, без изменений поведения).**
- Создать `app/data/orchestrator/TInvestOrchestrator.kt`.
- Создать `domain/api/StreamCombinators.kt` с `streamWithFallback`.
- Перенести композицию gRPC-stream + gRPC-unary polling из `TInvestMarketDataService` в оркестратор.
- `TInvestBrokerAPI.positionsStream` делегирует в оркестратор.
- Сборка, 63 теста зелёные. Поведение не меняется.

**Фаза 2 — Ktor WS transport.**
- Создать `:transport-ktor` KMP-модуль с одним `androidTarget()`.
- Ktor 3.6.0: `ktor-client-core`, `ktor-client-okhttp`, `ktor-client-websockets`, `ktor-serialization-kotlinx-json`.
- Реализовать `TInvestPositionsWebSocket` в `commonMain`.
- Hilt-провайдер в `:app/di/`.
- Оркестратор использует WS как fallback стрим-уровня.
- Проверка: gRPC работает → WS в standby; отключить gRPC → WS даёт данные.

**Фаза 3 — Раскат на цены и ордера.**
- Аналогично для `pricesStream` и `orderStateStream`.
- Проверка на каждом.

**Файлы:**

- `settings.gradle.kts` — `include(":transport-ktor")`.
- `transport-ktor/build.gradle.kts` (новый).
- `transport-ktor/src/commonMain/kotlin/.../TInvestPositionsWebSocket.kt` (новый).
- `transport-ktor/src/androidMain/kotlin/.../HttpEngine.android.kt` (новый).
- `domain/api/StreamCombinators.kt` (новый).
- `app/data/orchestrator/TInvestOrchestrator.kt` (новый).
- `app/data/api/TInvestMarketDataService.kt` — переработать.
- `app/di/DataModule.kt` — Hilt-провайдер для WS.

**Референсы:**

- AsyncAPI-спецификация WS: `RussianInvestments/investAPI` → `src/docs/ws/asyncapi.yaml`.
- Канал WS: `/tinkoff.public.invest.api.contract.v1.OperationsStreamService/PositionsStream`.
- Ktor Client WebSocket: `https://ktor.io/docs/client-websockets.html`.
- `docs/streams.md` → «PositionsStream».

**Ограничения:**

- Только позиции на первом этапе (фаза 2). Раскат на цены и ордера — фаза 3.
- Только WS. REST-fallback для polling — отдельная задача.
- Оркестратор без Hilt-аннотаций и без Android-импортов.
- iOS-таргет не добавляем. Только `androidTarget()`.
- Логика fallback — в оркестраторе, не в брокере.
- Ktor 3.6.0 (проверено на Maven Central).
- Не смешивать с миграцией REST.

**Критерий готовности:**

- Модуль `:transport-ktor` собирается.
- `TInvestOrchestrator` композирует два уровня с fallback'ами.
- Брокер делегирует в оркестратор.
- При отключённом gRPC-стриме данные идут через WS.
- 63 unit-теста зелёные, ktlint и detekt чистые.

**Оценка:** ~6–8 часов (фаза 1: 2 ч; фаза 2: 3–4 ч; фаза 3: 1–2 ч).

**Зависимости:** нет. **Разблокирует** «Ktor REST transport для polling», «StreamState в data-слое», «UI-индикатор состояния стримов».

**Открытые вопросы:**

- Точная структура JSON-конверта WS-подписки. Источник: `asyncapi.yaml`.
- URL и заголовки для sandbox/prod WS.
- Есть ли у Finam gRPC-stream (сейчас в схеме указан, но требует подтверждения).




---

### Ktor REST transport для polling

**Мобильная работа:** невозможно (требует сборки и проверки на реальных данных).

**Статус:** запланировано.

**Контекст:** polling у T-Invest идёт через gRPC unary. REST API у T-Invest — прокси Swagger над gRPC unary-методами. Если gRPC-протокол недоступен (блокировка портов, региональные ограничения), REST даёт альтернативу. У BCS REST — уже основной транспорт, сейчас на OkHttp.

**Зачем:** fallback для polling-уровня. Плюс — единый стиль на Ktor для всех REST-запросов (BCS мигрирует с OkHttp). Ktor даёт единый HTTP-клиент для KMP, OkHttp — нет.

**Предусловия:** выполнена задача «Оркестратор стримов и Ktor WS transport» — нужен оркестратор как точка композиции.

**Фазы:**

**Фаза 1 — Ktor REST-клиент для T-Invest.**
- `TInvestRestTransport` в `:transport-ktor/commonMain`.
- Методы: `fetchPositionsSnapshot`, `getLastPrices`, `getBalance`.
- Прокси над REST-эндпоинтами T-Invest.

**Фаза 2 — Fallback для polling T-Invest.**
- Оркестратор композирует gRPC-unary + REST в polling-уровне.
- При недоступности gRPC polling идёт через REST.
- Логика переключения — в оркестраторе, не в брокере.

**Фаза 3 — Миграция BCS на Ktor.**
- Перевести `BcsBrokerApi` с OkHttp на Ktor REST.
- Удалить прямые OkHttp-зависимости (если не останется).
- Опционально — сделать `:transport-ktor` точкой входа для всех REST-брокеров.

**Файлы:**

- `transport-ktor/src/commonMain/kotlin/.../TInvestRestTransport.kt` (новый).
- `transport-ktor/src/commonMain/kotlin/.../BcsRestTransport.kt` (новый).
- `app/data/api/TInvestMarketDataService.kt` — обновить.
- `app/data/api/BcsBrokerApi.kt` — миграция.
- `app/data/orchestrator/TInvestOrchestrator.kt` — композиция polling с REST.

**Референсы:**

- REST API T-Invest: `https://developer.tbank.ru/invest/intro/developer/protocols/restapi`.
- Формат запроса: `POST https://invest-public-api.tbank.ru/rest/tinkoff.public.invest.api.contract.v1.<Service>/<Method>`.
- Ktor Client: `https://ktor.io/docs/client-create-new-application.html`.
- Существующая реализация `BcsBrokerApi` — образец REST-клиента.

**Ограничения:**

- Не смешивать с WS-транспортом (отдельные фазы, отдельные PR).
- Логика fallback — в оркестраторе.
- Лимиты REST те же, что у gRPC unary: 6 запросов/сек.
- Не удалять OkHttp полностью, пока gRPC-транспорт использует его как engine.
- Транспорт не знает про домен — возвращает DTO или сырой JSON, конвертация — в репозитории.

**Критерий готовности:**

- При недоступности gRPC polling идёт через REST.
- `BcsBrokerApi` использует Ktor REST.
- `TInvestOrchestrator` композирует gRPC unary + REST.
- 63 unit-теста зелёные, ktlint и detekt чистые.

**Оценка:** ~4–6 часов.

**Зависимости:** блокируется «Оркестратор стримов и Ktor WS transport».




---






Набросок правки ROADMAP

Ниже — компактный план, что менять. Сохрани в черновик, на десктопе применим.

---

Проблема в текущем планировании

Три задачи пересекаются, но описаны как независимые:

1. ### Каскад обновления позиций — описывает последовательный каскад внутри TInvestMarketDataService. Устарело: gRPC-уровень сделан, последовательный каскад отменён в пользу параллельного.
2. ### Унификация каскадов стримов — статус «отложено». По обсуждению — должно быть «в работе», потому что де-факто унификация нужна.
3. ### Миграция сетевого слоя на Ktor Client — описывает миграцию REST-запросов на Ktor. Устарело: реально нужен Ktor для WebSocket как fallback для стримов.

---

Целевая архитектура (набросок)

```
ViewModel
    ↓
PositionStreamManager / PriceStreamManager (domain)
    ↓
BrokerApi (domain interface)
    ↓
TInvestBrokerAPI (data adapter)
    ↓
StreamOrchestrator (data — НОВЫЙ слой)
    ├── gRPC transport    (T-Invest only)
    ├── Ktor WS transport (все брокеры с WS)
    └── Ktor REST polling (все брокеры)
```

Ключевое:

· Оркестратор — общий. Брокер только композиция транспортов.
· Отсутствующие транспорты — null (ветка замьючена).
· Логика fallback — в оркестраторе, не в брокере.

Матрица брокеров:

Транспорт T-Invest BCS Finam
gRPC stream ✅ ❌ ❌
Ktor WS stream ✅ ? ❌
Ktor REST polling ✅ ✅ ✅

---

Правки в ROADMAP

1. Переформулировать «Каскад обновления позиций»

Убрать всё про последовательный каскад и LevelOutcome. Заменить на:

· Что сделано: gRPC-стрим + параллельный polling (PR #20).
· Что осталось: WS-уровень (через Ktor).
· Куда идёт: становится частью «Унификации каскадов».

Или закрыть как отдельную задачу — всё её содержимое уже в других местах.

2. Переформулировать «Унификация каскадов стримов»

Из «отложено» → «в работе». Новое содержание:

· Зачем: три транспорта (gRPC, Ktor WS, Ktor REST) × несколько стримов (позиции, цены, ордера) × три брокера (T-Invest, BCS, Finam). Без общего движка — дублирование.
· Что унифицируется:
  · StreamOrchestrator — класс композиции.
  · streamWithFallback(primary, fallback, polling) — утилита.
  · Единый формат [stream]-логов.
  · Общий механизм backoff.
· Что НЕ унифицируется: доменная логика (позиции ≠ цены по семантике).
· Фазы:
  1. Выделить оркестратор из TInvestMarketDataService (рефакторинг без изменений поведения).
  2. Добавить Ktor WS-транспорт для позиций.
  3. Раскатать на цены и ордера.
  4. HTTP REST fallback для polling.

3. Переформулировать «Миграция на Ktor Client»

Сузить: первый этап — только WS. REST-миграция — отдельная задача после.

Или удалить и создать новую: «Ktor WebSocket transport».

4. Новая задача: :transport-ktor (KMP-модуль)

Создать модуль с одним androidTarget(), Ktor 3.6.0, WS-клиент для позиций T-Invest.

---

Открытые вопросы

1. Где живёт StreamOrchestrator:
   · :app/data/orchestrator/ — просто, но не KMP.
   · :transport-ktor/orchestrator/ — модуль назван неточно (оркестратор не про транспорт).
   · Новый :streams модуль — чище, но +1 модуль.
2. Имя модуля: :transport-ktor — вариант для транспорта. Для оркестратора — :stream-core, :streams.
3. Порядок фаз: сначала оркестратор (рефакторинг) или сразу WS (быстрая польза)?

---

Что делать на десктопе

1. Открыть docs/ROADMAP.md.
2. Пройти по трём задачам, применить правки выше.
3. Решить открытые вопросы (1, 2, 3).
4. Коммит docs(roadmap): rework stream cascade planning.

---




### Рефакторинг расчёта P&L для PortfolioPosition

**Мобильная работа:** невозможно (требует сборки и проверки
на реальных данных).

**Статус:** запланировано.

**Контекст:** расчёт `profit` и `profitPercent` в
`TInvestMarketDataService.fetchPositionsSnapshot` содержит
непоследовательность, которая может давать расхождения между
прибыльными и убыточными позициями.

**Обнаруженные проблемы:**

1. **Условная логика выбора источника.** Текущий код:
   ```kotlin
   val profit: Double? = if (expectedYield > 0.0) {
       expectedYield          // серверный P&L
   } else if (avgPrice != null && avgPrice > 0.0) {
       (currentPrice - avgPrice) * quantity  // клиентский
   } else null
   ```
    - Для **положительного** P&L — берётся серверный
      `expectedYield`.
    - Для **отрицательного** или **нулевого** — клиентский
      расчёт.
    - Профит и убыток считаются по разным формулам.
    - `expectedYield == 0.0` (нет изменения) уходит в клиентскую
      ветку.

2. **Клиентский расчёт для фьючерсов.** Формула
   `(currentPrice - avgPrice) * quantity` **не учитывает
   `pointValue`**. Для фьючерсов цена в пунктах, реальный
   P&L зависит от стоимости пункта. В закрытой задаче
   «Унификация profitPercent для PortfolioPosition» починили
   `profitPercent`, но не сам `profit`.

3. **Источники серверного P&L.** `expectedYield` от T-Invest
   может отличаться от `(currentPrice - avgPrice) * quantity`
   из-за комиссий, дивидендов, корпоративных событий,
   НКД по облигациям. Серверный — точнее, но не всегда
   положительный/нулевой корректно обрабатывается.

**Связано:** задача «gRPC-стрим позиций». Стрим
`PositionsStream` даёт только `balance` (количество) и
идентификаторы, но **не даёт** `expectedYield`,
`averagePositionPrice`, `currentPrice`. Если рефакторинг
P&L не сделан до интеграции стрима, карточки потеряют P&L
при переходе на gRPC.

**Варианты решения:**

**A. Только серверный `expectedYield`.**
- `profit = expectedYield` всегда (если оно есть в ответе).
- Плюс: единая формула, точнее учёт комиссий/дивидендов.
- Минус: зависимость от того, что сервер всегда присылает
  поле. Если `expectedYield` = 0, непонятно — это «нет
  изменения» или «сервер не посчитал».

**B. Только клиентский расчёт.**
- `profit = (currentPrice - avgPrice) * quantity * pointValue`
  — универсальная формула.
- Плюс: работает без серверного `expectedYield`, единая
  логика для всех типов инструментов.
- Минус: не учитывает комиссии, дивиденды, корпоративные
  события. Для точного P&L — недостаточно.

**C. Гибрид с приоритетом серверного.**
- Если `expectedYield` есть (не null) — используем его.
- Иначе — клиентский расчёт.
- Плюс: серверный в приоритете (точнее), клиентский —
  fallback.
- Минус: нужно чётко определить, когда `expectedYield`
  считается «есть» (в proto у него double, нет отдельного
  `hasExpectedYield`).

**D. Разделить поля.**
- `profit` — всегда серверный `expectedYield`.
- `clientProfit` — клиентский расчёт.
- UI решает, что показывать.
- Плюс: обе цифры доступны для сверки.
- Минус: удвоение модели, UI должен выбрать.

**Рекомендация:** **A** для `profit`, с fallback на
клиентский расчёт только если сервер вернул 0 и это
подозрительно (нужен критерий). `profitPercent` — уже
унифицирован, оставить.

**Файлы:**
- `data/api/TInvestMarketDataService.kt` — метод
  `fetchPositionsSnapshot`.
- `domain/models/Portfolio.kt` — модель и
  `calculateProfitPercent`, если потребуется.
- `domain/models/Instruments.kt` — `FutureUi.pointValue`,
  если потребуется.

**Шаги:**

1. Собрать данные на реальных аккаунтах: сравнить серверный
   `expectedYield` и клиентский расчёт на разных позициях
   (акции, фьючерсы, положительный / отрицательный /
   нулевой P&L).
2. Определить, воспроизводится ли расхождение и в каких
   случаях.
3. Выбрать вариант (A/B/C/D).
4. Реализовать.
5. Покрыть unit-тестами:
    - акция с положительным P&L;
    - акция с отрицательным P&L;
    - фьючерс с положительным P&L (проверка `pointValue`);
    - фьючерс с отрицательным P&L;
    - отсутствие `expectedYield`.

**Референсы:**
- `docs/ROADMAP.md` → «Унификация profitPercent для
  PortfolioPosition» (закрытая) — что уже сделано.
- Официальная документация T-Invest API → `PositionData` →
  `expectedYield`, `averagePositionPrice`.
- Proto-контракт: `ru.tinkoff.piapi.contract.v1.PositionData`.

**Ограничения:**
- Не менять модель `PortfolioPosition` без согласования —
  она используется в UI.
- Не смешивать с задачей «gRPC-стрим позиций» — отдельная
  работа.
- Серверный `expectedYield` остаётся приоритетным, если
  применим вариант A или C.

**Критерий готовности:**
- Формула расчёта P&L однозначна (не зависит от знака).
- Фьючерсы корректно учитывают `pointValue`.
- Поведение покрыто unit-тестами.
- Расхождения серверного и клиентского P&L либо устранены,
  либо объяснены и задокументированы.

**Оценка:** ~2–3 часа (включая сбор данных на реальных
аккаунтах).

**Зависимости:** нет. **Желательно до** интеграции
gRPC-стрима позиций — иначе переход на стрим сломает P&L
в карточках.




---

### Compose Preview для компонентов

**Мобильная работа:** частичное (написание `@Preview`-функций
— с телефона; проверка рендера — только в Android Studio).

**Статус:** запланировано. Вариант A — сейчас, вариант B —
опционально после A.

**Контекст:** сейчас правки UI проверяются на телефоне через
полный цикл «сборка → установка → навигация до экрана».
Это медленно, особенно для карточек, которые рисуются в разных
состояниях (положительная/отрицательная прибыль, разные типы
инструментов, выбранное/невыбранное состояние).

`ui-tooling-preview` уже подключён. `ui-tooling` — только
`debugImplementation`, этого достаточно для IDE-превью.

**Предусловия:**
- Проверить, что `AssetPositionCard` и `OrderCard` принимают
  все данные через параметры (без `hiltViewModel()` внутри).
  Если нет — превью невозможно без рефакторинга, в этом случае
  задача переходит в блокер «Рефакторинг Compose-компонентов».

**Вариант A (обязательный):**

**Файлы:**
- `ui/components/AssetPositionCard.kt` — preview-функции.
- `ui/components/OrderCard.kt` — preview-функции.

**Что покрыть:**
- `AssetPositionCard` — 3–4 превью:
    - акция с положительной прибылью;
    - акция с отрицательной прибылью;
    - фьючерс;
    - выбранное состояние (isSelected = true).
- `OrderCard` — 2–3 превью:
    - рыночная заявка;
    - лимитная заявка;
    - исполненная заявка.

**Ограничения:**
- Preview-функции — `private`, с аннотацией
  `@Preview(showBackground = true)`.
- Оборачивать в `Theme { }` (наш `TScalpTheme`).
- Данные — константы внутри preview-функции, не через
  ViewModel.
- **Не менять** сами компоненты — только добавлять превью.
  Если компонент требует рефакторинга — выносить в отдельную
  задачу.

**Критерий готовности:**
- В Android Studio при открытии файла компонента видны
  превью-панели.
- Превью рендерятся без ошибок, отображают реальные данные.
- Сборка и тесты зелёные (preview не влияет на runtime).

**Оценка:** ~30–40 минут.

**Зависимости:** нет.

**Вариант B (опциональный, после A):**

**Файлы:**
- `ui/components/BrokerAccountDialog.kt` — 2–3 превью
  (разные брокеры, пустой список счетов, загрузка).
- `ui/components/ConfirmOrderDialog.kt` — 2 превью
  (рыночная/лимитная заявка).

**Оценка:** ~40 минут.

**Открытые вопросы:**
- Есть ли у `OrderCard` и `ConfirmOrderDialog` зависимости
  от `hiltViewModel()` или чего-то непередаваемого через
  параметры? Проверить при старте задачи.




---

### Парсинг expireDate для stop-order'ов GOOD_TILL_DATE

**Мобильная работа:** частичное (парсер + unit-тест — с
телефона; сборка и прогон теста — с десктопа).

**Статус:** запланировано. Потерянная логика.

**Контекст:** в `TInvestOrdersService.kt:155` устанавливался
`expireDate` через `parseDate(request.expireDate)`, но метод
`parseDate` возвращал пустой `com.google.protobuf.Timestamp` —
то есть фактически ничего не парсил. Это была недописанная
логика. При чистке detekt-issues (UnusedParameter) метод
и его вызов удалены, чтобы не оставлять сломанный код.

**Последствие:** при создании stop-order с типом экспирации
`GOOD_TILL_DATE` поле `expireDate` в `PostStopOrderRequest`
не устанавливается. Такие заявки либо отклоняются биржей,
либо обрабатываются некорректно.

**Файлы:**
- `data/api/TInvestOrdersService.kt` — восстановить парсинг.
- `domain/models/OrderModels.kt` — `expireDate` в
  `StopOrderRequest` (нужно уточнить формат — String?).

**Шаги:**
1. Уточнить формат `expireDate`, приходящего из UI
   (ISO-8601? unix timestamp? yyyy-MM-dd?).
2. Уточнить формат, ожидаемый T-Invest API:
   `com.google.protobuf.Timestamp` (секунды + наносекунды
   от epoch UTC).
3. Реализовать парсер с обработкой ошибок
   (невалидный формат → возвращать `AppResult.Failure`).
4. Восстановить вызов `builder.setExpireDate(...)` при
   `expirationType == GOOD_TILL_DATE`.
5. Покрыть unit-тестом.

**Референс:**
- Документация T-Invest API → PostStopOrder → expireDate.
- `com.google.protobuf.Timestamp` — стандартный proto-тип,
  поля `seconds` (int64), `nanos` (int32).

**Критерий готовности:**
- Stop-order с `GOOD_TILL_DATE` создаётся и принимается
  биржей; `expireDate` в запросе совпадает с UI-значением.
- Покрыто тестом.

**Оценка:** ~1 час.

**Зависимости:** нет.




---

### UI-индикатор состояния стримов

**Статус:** запланировано. **UI — требует санкции.**

**Контекст:** пользователь не может оценить на глаз,
актуальны ли данные. Индикатор снимает вопрос
«это задержка или данные устарели».

**Предусловия:** StreamState реализован в обоих менеджерах.

**Файлы:**
- `presentation/.../MainScreen.kt` (или где определён NavBar) —
  бейдж-точка на иконке вкладки.
- `presentation/.../SettingsScreen.kt` — раздел «Диагностика».
- `presentation/.../MainViewModel.kt` (или соответствующие
  ViewModels) — проброс `StreamState` в UI.

**UI-описание:**

- **Бейдж-точка** на иконке вкладки:
  - Заявки → `PriceStreamManager.connectionState`;
  - Портфель → `PositionStreamManager.connectionState`;
  - POLLING → 🟡 (`MaterialTheme.colorScheme.tertiary`);
  - STALE → 🔴 (`MaterialTheme.colorScheme.error`);
  - LIVE / INITIALIZING / STOPPED → **бейджа нет** (тишина = норма).
- **Snackbar** при переходе в деградацию:
  - debounce 3 секунды;
  - тексты: см. п. «Тексты» ниже;
  - восстановление в LIVE — без Snackbar.
- **Раздел «Диагностика»** в Настройках:
  - текущее состояние обоих стримов;
  - время последнего обновления;
  - источник (gRPC/REST/WS);
  - расшифровка цветов бейджа.

**Тексты:**
- POLLING (цены): «Стрим цен: резервный режим. Обновление раз в 5 секунд.»
- POLLING (позиции): «Стрим позиций: резервный режим. Обновление раз в 10 секунд.»
- STALE (цены): «Стрим цен: нет данных. Проверьте соединение.»
- STALE (позиции): «Стрим позиций: нет данных. Проверьте соединение.»

**Ограничения:**
- **Не менять** цвет текста NavBar — конфликт с семантикой
  selected/unselected.
- **Не делать** long-press на вкладке — не discoverable,
  конфликт с системным long-press. Подсказка доступна через
  раздел «Диагностика».

**Критерий готовности:**
- При переходе стрима в POLLING появляется жёлтая точка
  и один Snackbar (не повторяется каждые N секунд).
- При STALE — красная точка и Snackbar.
- Раздел «Диагностика» показывает актуальное состояние.

**Оценка:** ~2 часа.

**Зависимости:** блокируется StreamState.




---

### StreamState в data-слое

**Мобильная работа:** невозможно (требует сборки и проверки
реактивного поведения).

**Статус:** запланировано.

**Контекст:** для UI-индикатора деградации стримов нужен
реактивный источник состояния. Полезен и без UI — для логов,
AppMetrica, отладки.

**Предусловия:** имеет смысл делать после каскада позиций
(иначе состояние не будет отражать все уровни).

**Файлы:**
- `domain/models/StreamState.kt` — новый файл.
- `domain/api/PriceStreamManager.kt` — добавить
  `connectionState: StateFlow<StreamState>`.
- `domain/api/PositionStreamManager.kt` — то же.

**Шаги:**

1. Создать `enum class StreamState { INITIALIZING, LIVE, POLLING, STALE, STOPPED }`.
2. В `PriceStreamManager` завести `private val _connectionState =
   MutableStateFlow(StreamState.STOPPED)` и `val connectionState`.
   Обновлять при переходе между gRPC и polling, при ошибках,
   при отсутствии обновлений > 15 секунд (→ STALE).
3. В `PositionStreamManager` — аналогично. Порог STALE — 30 секунд.
4. «Биржа закрыта» ≠ STALE: если polling возвращает цену
   (пусть и неизменную), состояние остаётся POLLING.

**Референсы:** нет.

**Ограничения:**
- **Не менять** публичный контракт `prices: SharedFlow<...>`.
- Не путать STALE с отсутствием интереса — STOPPED при пустом union.

**Критерий готовности:**
- `connectionState` реактивно меняется при переключении
  источников и при отсутствии данных.
- В `logcat` при смене состояния видно `[stream] state: LIVE → POLLING`
  (или аналогичное).

**Оценка:** ~30 минут.

**Зависимости:** блокируется «Оркестратором стримов и Ktor
WS transport» — нужен финальный слой композиции, иначе
состояние не отражает все уровни.




---

### Переход на edge-to-edge (statusBarColor deprecated)

**Статус:** запланировано, требует отдельной подготовки.

**Что:** `window.statusBarColor` и `window.navigationBarColor` устарели.
С Android 15 система применяет edge-to-edge принудительно для приложений
с target SDK 35+.

**Действия:**
1. Убрать `window.statusBarColor = ...` из `Theme.kt`.
2. Убедиться, что в `MainActivity` вызывается `enableEdgeToEdge()`.
3. Проверить все 4 экрана (Заявки, Портфель, Настройки, MainScreen)
   на Android 12, 13, 14, 15.
4. При необходимости — добавить `WindowInsets` в `Scaffold`-ы.

**Оценка:** ~2 часа + прогон на устройствах разных версий.
**Когда:** при подготовке релиза под Android 15 или target SDK 36.




---

### Рефакторинг архитектурного долга (detekt-группа 3)

**Статус:** запланировано.

**Контекст:** при внедрении detekt 2.0.0-alpha.6 (см. задачу
`detekt` в разделе выше) выявлены 10 групп архитектурного
долга. Правила `LongMethod`, `CyclomaticComplexMethod`,
`TooManyFunctions`, `LargeClass` подавлены в коде через
`@Suppress` со ссылкой на эту секцию. `MagicNumber` (45
записей) зафиксирован в `app/detekt-baseline.xml`.

**Общие предусловия:**
- Перед рефакторингом каждой группы — прогнать тесты,
  зафиксировать текущее поведение.
- Разбивать на подзадачи: один класс / одна функция за раз,
  коммит после каждого шага (правило 4b AGENTS.md).
- После рефакторинга — снять `@Suppress`, убедиться что
  detekt проходит без него.

**Общие ограничения:**
- Не менять UI без санкции (правило 1 AGENTS.md).
- Не ломать публичные контракты без миграции вызывающих.
- Тесты (63 unit) должны оставаться зелёными.

**Общий критерий готовности:** все `@Suppress` из этой секции
сняты; `:app:detekt` проходит без новых подавлений; тесты
зелёные.

**Оценка:** ~15–20 часов суммарно.

**Зависимости:** нет.

#### Compose-экраны

**Файлы:**
- `presentation/screens/orders/OrdersScreen.kt` — `OrdersScreen`
  (646 строк, complexity 69), `InstrumentSearchField`
  (130 строк, complexity 16).
- `presentation/screens/portfolio/PortfolioScreen.kt` (161 строка,
  complexity 19).
- `presentation/screens/settings/SettingsScreen.kt` —
  `TInvestSettingsPanel` (324 строки, complexity 35),
  `BcsSettingsPanel` (182), `FinamSettingsPanel` (149).

**Пути решения:** вынести секции экрана в приватные
composables. Для `OrdersScreen` — отдельно форма заявки,
список, поиск, диалоги.

**Дополнительно — Compose Preview (вариант C):**
после разделения экранов на wrapper + content добавить
`@Preview` для каждого экрана во всех состояниях
(loading / error / empty / data). Обёртка с `hiltViewModel()`
остаётся без превью; чистый content принимает `uiState`
и колбэки, для него — превью с моками. Это ускорит
дальнейшие UI-итерации (см. ROADMAP → «Compose Preview
для компонентов», варианты A/B).

**Оценка:** ~6 часов на рефакторинг + ~1–2 часа на preview
всех экранов (вариант C).

#### Compose-компоненты

**Файлы:**
- `ui/components/AssetPositionCard.kt` — `AssetPositionCard`
  (109 строк), `AssetCardContent` (136 строк, complexity 20).
- `ui/components/BrokerAccountDialog.kt` (105 строк).
- `ui/components/OrdersListViewModel.kt` — `loadOrders`
  (62 строки).

**Оценка:** ~2 часа.

#### ViewModel-слой

**Файлы:**
- `presentation/screens/orders/OrdersViewModel.kt` — 49 функций,
  LargeClass; `postOrder` (172 строки, complexity 28).
- `presentation/screens/portfolio/PortfolioViewModel.kt` —
  12 функций; `payInSandbox` (61 строка).
- `presentation/screens/settings/SettingsViewModel.kt` —
  20 функций.

**Пути решения:** вынести бизнес-логику в UseCase,
оставить во ViewModel только состояние и делегирование.
Для `OrdersViewModel` — возможно разделение на
`OrderFormViewModel` / `OrderListViewModel`.

**Оценка:** ~5 часов.

#### BrokerApi-реализации

**Файлы:** `data/api/BcsBrokerApi.kt` (25 функций),
`data/api/FinamBrokerApi.kt` (24), `data/api/TInvestBrokerApi.kt`
(22), `domain/api/BrokerApi.kt` (интерфейс, 19).

**Пути решения:**
- Разделить `BrokerApi` на ролевые интерфейсы (`AccountsApi`,
  `OrdersApi`, `MarketDataApi`, `StreamsApi`) с агрегацией.
- Разбить реализации на сервисы по образцу
  `TInvestMarketDataService` / `TInvestOrdersService`.

**Оценка:** ~3 часа.

#### Репозитории

**Файлы:** `data/repository/InvestRepository.kt` (17 функций),
`data/repository/SettingsRepository.kt` (15).

**Пути решения:** разбить по доменным областям (аккаунты,
заявки, портфель, стримы — для `InvestRepository`; токены,
настройки UI, режим sandbox — для `SettingsRepository`).

**Оценка:** ~2 часа.

#### UseCase-слой

**Файлы:**
- `domain/usecases/CalculateTradeDetailsUseCase.kt` —
  `calculate` (complexity 25).
- `domain/usecases/PrepareOrderRequestUseCase.kt` —
  `prepare` (92 строки, complexity 20).

**Пути решения:** разбить `calculate` на отдельные функции
по типу заявки (market/limit/stop). `prepare` — вынести
валидацию, сборку request, разрешение uid в отдельные шаги.

**Оценка:** ~2 часа.

#### Data-слой T-Invest

**Файлы:**
- `data/api/TInvestMarketDataService.kt` — 19 функций;
  `fetchPositionsSnapshot` (complexity 26).
- `data/api/TInvestInstrumentMapper.kt` — `mapProtoToDomain`
  (118 строк).

**Пути решения:** `fetchPositionsSnapshot` разбить на
`fetchPositions` + `enrichWithInstrumentData` +
`enrichWithMarginData`. `mapProtoToDomain` — на
`mapFutures` / `mapShares` / `mapDefault`.

**Оценка:** ~2 часа.

#### Классификатор ошибок

**Файлы:**
- `util/AppResultExtensions.kt` — `Throwable.toAppError`
  (66 строк, complexity 26).

**Пути решения:** разбить на per-broker обработчики
(`toTInvestError`, `toBcsError`, `toFinamError`) с
диспетчеризацией по `BrokerName`.

**Оценка:** ~1 час.

#### Индикатор доступности инструмента в карточке позиции

**Файлы:**
- `ui/components/AssetPositionCard.kt` — параметр
  `tradingAvailability` объявлен, но не используется.

**Контекст:** данные передаются из UI (`PortfolioScreen`,
`OrdersScreen`), но визуальный индикатор не реализован.
UI-изменение требует санкции (правило 1 AGENTS.md).

**Пути решения:** добавить в карточку бейдж или цветовой
индикатор статуса. Тексты и иконки согласовать заранее.

**Оценка:** ~1 час (после санкции на UI).

**Когда вернуться:** при следующей итерации по UI портфеля.




---

### Реализация Finam Broker API

**Статус:** запланировано.

**Контекст:** `FinamBrokerApi` — заглушка. Все методы
`BrokerApi` либо возвращают пустые значения (`emptyList`,
`emptyMap`, `0.0`), либо бросают `NotImplementedError` через
`TODO("Not yet implemented")`. Брокер зарегистрирован
в `DataModule` наравне с T-Invest и BCS, но фактически
не работает.

**Предусловия:**
- Доступ к Finam API (документация, тестовый токен).
- Выбор HTTP-клиента: OkHttp (уже в зависимостях,
  используется BCS) или Ktor. Рекомендуется OkHttp для
  консистентности с BCS.

**Файлы:**
- `data/api/FinamBrokerApi.kt` — основная реализация.
- `util/FinamErrorMessages.kt` — расшифровка кодов ошибок
  (может не существовать, см. ROADMAP → «Расшифровка
  кодов ошибок брокеров»).
- `data/api/FinamModels.kt` (новый) — data-классы для
  JSON-ответов API.
- `data/api/FinamConverters.kt` (новый) — конвертеры
  JSON ↔ доменные модели.

**Шаги:**

1. Изучить документацию Finam API: базовый URL
   (боевой и sandbox), авторизация, формат запросов
   и ответов.
2. Реализовать `getAccounts(sandboxMode)`.
3. Реализовать `fetchPositionsSnapshot(accountId, sandboxMode)`.
4. Реализовать `getBalance(accountId)`.
5. Реализовать `findInstruments(query)`.
6. Реализовать `postOrder` / `postStopOrder` /
   `cancelOrder` / `cancelStopOrder`.
7. Реализовать `getOrders` / `getStopOrders`.
8. Реализовать `getLastPricesByTscalpInstrumentId`.
9. Реализовать `getTradingStatuses`.
10. Реализовать стримы (`subscribePositions`,
    `subscribeOrderState`, `subscribeLastPrices`) —
    если Finam поддерживает; иначе polling с fallback.
11. Заполнить `FinamErrorMessages.kt` по мере
    появления кодов ошибок.
12. Покрыть unit-тестами (моки OkHttp).

**Референсы:**
- Официальная документация Finam API (URL уточнить).
- Существующая реализация `BcsBrokerApi` — образец
  REST-клиента на OkHttp.

**Ограничения:**
- **Не оставлять `TODO("Not yet implemented")`** в коде,
  который может быть вызван: заменять на явный
  `AppResult.Failure` или `emptyList` с KDoc, если операция
  не поддерживается.
- Логирование — через `AppLogger`, не `printStackTrace`.
- Идентификаторы инструментов — через `tscalpInstrumentId`
  в domain-слое (см. AGENTS.md).

**Критерий готовности:**
- Все методы `BrokerApi` реализованы через REST-запросы
  к Finam API.
- `TODO("Not yet implemented")` в `FinamBrokerApi.kt`
  отсутствуют.
- В UI Finam выбирается наравне с T-Invest и BCS,
  выполняет базовые операции (счёт, портфель, заявка).

**Оценка:** ~8–12 часов (зависит от документации
и наличия тестового доступа).

**Зависимости:** нет. Блокирует полноценное использование
Finam в UI.




---

### Расшифровка кодов ошибок брокеров

**Статус:** частично закрыто.

**Сделано:**
- `util/BrokerErrorMessages.kt` — центральный реестр
  `Map<BrokerName, Map<code, msg>>`.
- `util/TInvestErrorMessages.kt` — ~130 кодов T-Invest по
  официальной документации.
- `AppError.Api` расширен полями `brokerName`, `brokerCode`.
- `toAppError(brokerName)` и `runCatchingAppResult(brokerName, block)`.
- `InvestRepository` передаёт `brokerName` во все вызовы.

**Осталось:**
- `BcsErrorMessages.kt` — заполнить по мере нахождения кодов BCS.
- `FinamErrorMessages.kt` — то же.
- Дополнять T-Invest таблицу новыми кодами по мере появления в логах.

**Оценка:** 15 минут на нового брокера + 5 минут на новый код.




---

## Закрытые задачи

### ✅ Единый источник цен (PriceStreamManager)

Реализовано в серии из 6 подкоммитов:
1. `PriceConsumer` enum + скелет `PriceStreamManager`.
2. Union + gRPC-стрим с пересозданием при смене union.
3. REST-fallback раз в 5 секунд.
4. Миграция `OrdersViewModel`.
5. Миграция `PortfolioViewModel` (+ фикс карточек в `OrdersScreen`:
   цена берётся из `uiState.currentPrice`, а не из медленного
   `portfolioPositions`).
6. Переименование `SharedPositionStreamManager` → `PositionStreamManager`.

### ✅ Унификация profitPercent для PortfolioPosition

Введён `calculateProfitPercent(yield, avgPrice, quantity, pointValue)` —
единая формула для всех типов инструментов. Для акций совпадает
с прежними формулами, для фьючерсов корректно учитывает
`pointValue`. Покрыто 11 тестами.

### ✅ Переход на DataStore

`SettingsRepository` и `TradingStateRepository` работают на
`DataStore<Preferences>`. `SharedPreferences` удалён из проекта
полностью. Все методы стали `suspend`, ViewModel'и обновляют
кэшированное состояние в `uiState` реактивно.

### ✅ Версионирование debug-сборок по последнему релизу

`versionName` debug-сборок получает префикс последнего релизного
тега: `1.0.2-dev.26.10.08.1430` вместо `1.26.10.08.1430`. Видно,
от какого релиза отпочковалась сборка.

Реализовано функцией `lastReleaseVersion()` в
`app/build.gradle.kts` — вызывает
`git tag --sort=-v:refname --list 'v*'`, fallback `0.0.0` при
отсутствии тегов. В `.gitlab-ci.yml` добавлен глобальный
`GIT_DEPTH: 0`, чтобы теги были видны в job'ах `test`-стадии.

Release-сборки не затронуты: при наличии
`-PreleaseVersionName=X.Y.Z` git не вызывается.

### ✅ Обновление Gradle 9.3.1 → 9.8.1

Wrapper обновлён до 9.8.1. Локальная сборка зелёная,
предупреждений об устаревании Kotlin DSL не осталось.

Попутно устранены устаревшие делегаты `by tasks.registering`
(удалены в Gradle 9.6) — заменены на
`tasks.register<Copy>("name") { ... }` в `build.gradle.kts`.

Kotlin 2.4.20 официально поддерживает Gradle до 9.7.0;
9.8.1 вне заявленного диапазона, но работает. Вернуться при
апгрейде Kotlin, если появятся проблемы.

### ✅ Обновление AGP 9.0.0 → 9.4.0

AGP обновлён до 9.4.0. Требования: Gradle 9.6.0+ (у нас 9.8.1),
JDK 17+ (у нас 25). Kotlin 2.4.20 и KSP 2.3.12 совместимы.

Плагин `org.jetbrains.kotlin.android` остался объявленным в root
build-скрипте. AGP 9.x его не требует, но и не конфликтует с ним —
сборка проходит с обоими. Удаление плагина — отдельная задача
по чистке, не часть этого апгрейда.

### ✅ Обновление зависимостей до актуальных версий

Обновлены:

- `androidx.core:core` 1.18.0 → 1.19.1
- `androidx.lifecycle:lifecycle-runtime-ktx` 2.9.4 → 2.11.0
- Compose BOM 2025.12.00 → 2026.09.00
- `androidx.navigation:navigation-compose` 2.9.8 → 2.10.2
- `kotlinx-coroutines-android` 1.10.2 → 1.11.0
- `kotlinx-coroutines-test` 1.10.2 → 1.11.0
- `io.grpc:*` 1.80.0 → 1.84.1
- `com.squareup.okhttp3:okhttp` 5.3.2 → 5.4.0
- `com.google.code.gson:gson` 2.13.2 → 2.14.0

Попутно:

- Убраны явные версии у `activity-compose`,
  `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` —
  управляются Compose BOM.
- Удалён дублирующий `grpc-stub` из блока «ManagedChannel»;
  он и так приходит транзитивно через `grpc-kotlin-stub` и
  `grpc-protobuf-lite`.

**Не трогали:** `protobuf-java` и `protobuf-kotlin` остаются
`3.25.8` — намеренный pin для совместимости с gRPC и
T-Invest SDK.

### ✅ Удаление legacy `.webp`-иконок

Удалены `ic_launcher.webp` и `ic_launcher_round.webp` из
`mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/`. Файлы
`ic_launcher_foreground.webp` оставлены — на них ссылается
adaptive XML `mipmap-anydpi/ic_launcher.xml`. Иконка
в лаунчере проверена на устройстве.

**Возможное улучшение в будущем:** перенести
`ic_launcher_foreground` в `drawable-{density}/` или заменить
векторным foreground. Отдельная задача, не в этой.

### ✅ Смена лицензии GPL v3 → Apache 2.0

`LICENSE` заменён на Apache 2.0, `README.md` обновлён.
Автор один, стороннего GPL-кода нет, SPDX-заголовков
в исходниках нет. Юридическая оговорка о неотзываемости
GPL для уже распространённых копий зафиксирована в README.

### ✅ Обновление compileSdk до 37

`compileSdk = 37`, `targetSdk = 36`. AGP 9.4.0 поддерживает
API 37. Доступ к новым API Android 17 без активации изменений
поведения. `targetSdk` оставлен на 36 — Google Play пока
требует только его.

### ✅ Reconnect-loop для gRPC-стрима цен

`PriceStreamManager.runGrcpStream` теперь переподключается
при ошибке с экспоненциальным backoff.

**Сделано:**

- `subscribeLastPrices` обёрнут в
  `while (currentCoroutineContext().isActive)` с `try/catch`.
- `CancellationException` пробрасывается — штатная отмена job
  не логируется как ошибка.
- Backoff `[1s, 5s, 15s, 30s, 60s]`, cap 60 секунд — по
  рекомендации официальной документации T-Invest
  («Что делать при разрыве стрима»).
- Нормальное завершение стрима сервером (`collect` вернулся
  без исключения) сбрасывает счётчик попыток и переподключается
  без задержки.
- Логи с префиксом `[prices]`: `W [prices] gRPC error (attempt=N),
  retry in Xms` и `D [prices] gRPC stream completed by server,
  reconnecting`.
- Parallel polling (`runPolling`) не тронут — продолжает
  работать, прикрывая паузы между попытками.

**Итог:** при обрыве gRPC-стрима цены продолжают идти через
polling (раз в 5 с), а основной источник восстанавливается
самостоятельно. Один коммит: `feat(prices): add reconnect-loop
for gRPC price stream` (PR #10).

### ✅ Переименование fetchPositionsRest → fetchPositionsSnapshot

Метод в `BrokerApi` переименован: имя отражает семантику
(«снимок» vs «стрим»), а не транспорт.

**Сделано:**

- `domain/api/BrokerApi.kt` — интерфейс + KDoc очищен:
  убраны дублирующие формулировки, транспорт-зависимость
  описана явно (gRPC unary для T-Invest, REST для
  BCS / Finam).
- `data/api/TInvestBrokerApi.kt` — override.
- `data/api/TInvestMarketDataService.kt` — реализация
  + 2 внутренних вызова.
- `data/api/BcsBrokerApi.kt` — override + 2 внутренних
  вызова.
- `data/api/FinamBrokerApi.kt` — override + 2 внутренних
  вызова.
- `data/repository/InvestRepository.kt` — вызов.
- `docs/ROADMAP.md` — 5 упоминаний обновлены.

**Итог:** имя унифицировано по семантике (snapshot / stream),
транспорт больше не «протекает» в название. Разблокирует
задачи по fallback-каскадам для стримов (позиции, цены,
ордера), где важна прозрачность «снимок vs стрим».

Один коммит: `refactor(domain): rename fetchPositionsRest
to fetchPositionsSnapshot` (PR #17).

### ✅ gRPC-стрим позиций

gRPC-стрим позиций реализован и работает в боевом и
песочнице. Основной источник обновлений количества;
polling остаётся параллельным fallback'ом.

**Что сделано:**

- **Разведка артефакта SDK 1.51.0.** Подтверждено:
  `OperationsStreamServiceAsync.positionsStream` доступен,
  классы `PositionsStreamRequest` / `PositionsStreamResponse`
  есть. Сервис был и в 1.48.1 — прошлые попытки падали
  не из-за отсутствия классов, а из-за несуществующего
  имени `PositionsStreamServiceGrpc`.
- **`subscribePositionsGrpc`** в `TInvestMarketDataService`.
  Через `api.operationsStreamServiceAsync.positionsStream`,
  с `withInitialPositions = true` (сервер присылает
  снапшот при подключении).
- **Параллельная схема вместо последовательного каскада.**
  `merge(subscribePositionsGrpcWithReconnect, subscribePositionsPolling)`.
  gRPC и polling работают одновременно, эмитят в общий
  поток. Отказ от каскада: gRPC может молчать (в первых
  тестах — да), а polling обязан работать.
- **Автопереподключение gRPC** с экспоненциальным
  backoff `[1s, 5s, 15s, 30s, 60s]`. Ожидание появления
  API внутри flow (без early-return).
- **Обогащение в `PositionStreamManager`.** Полные события
  (от polling, `currentPrice != null`) обновляют
  `snapshotCache`. Неполные события (от gRPC, без P&L)
  дополняются `currentPrice`, `averagePositionPrice`,
  `expectedYield`, `isin`, `pointValue` из кэша.
- **Диагностический лог** `[positions]/gRPC: response — subs=, init=, pos=, ping=, case=`.
  Нужен, потому что `PING` приходит раз в 60 секунд;
  без него не отличить «стрим мёртв» от «стрим жив,
  изменений нет».

**Проверено:**

- Боевой аккаунт: `SUBSCRIPTIONS` → `INITIAL_POSITIONS`
  за 100 мс, затем `PING` раз в 60 секунд.
- Песочница: тот же flow, `hasPosition()` приходит при
  изменении позиции.
- **Обогащение работает:** gRPC-событие `POSITION` для
  `5f72f0f4-...` (qty=7) получило `price=103.54,
  yield=42.08` из кэша polling.
- 63 unit-теста зелёные, ktlint и detekt чистые.

**Ограничение:** валютные остатки (`moneyList`) gRPC-стрим
не отдаёт — обрабатываются только `securities` и `futures`.
Для UI это некритично: цена валюты = 1.0, polling приносит
полные данные.

**Итог:** задача разблокирует WS-уровень в каскаде позиций
(см. `### Каскад обновления позиций`). Миграция
`PositionStreamManager` на стрим как единственный источник
не потребовалась — параллельная схема оказалась надёжнее.

Один коммит с закрытием — см. `docs/ROADMAP.md` → этот
раздел.

---
