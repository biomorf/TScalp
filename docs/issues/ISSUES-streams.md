<!-- SECTION_BEGIN: issues-streams -->
## Streams










<!-- SUBSECTION_BEGIN: i-2 -->
___
### [OPEN] I-2 Race в start/stop *FlowManager — дублирование стримов
**Статус:** открыт (частично, live-test 2026-11-08, шаг 16b).

**Симптом:** при старте приложения в logcat видно **по два
параллельных подключения** одного и того же транспорта на
каждом менеджере. Пример из live-смоука 2026-10-22
(Pixel 8a, Android 14, старт приложения):

    D PositionFlowManager: Перезапуск потока позиций: null → 2309452027
    D PositionFlowManager: Перезапуск потока позиций: null → 2309452027
    D TInvestMarketData: [stream/gRPC/TInvest]: connecting (attempt=1)
    D TInvestMarketData: [stream/gRPC/TInvest]: connecting (attempt=1)
    D TInvestWS:          [WS/ktor/TInvest]  connecting (attempt=1)
    D TInvestWS:          [WS/ktor/TInvest]  connecting (attempt=1)

Две строки `Перезапуск` — из разных нитей в одну миллисекунду:
`4138-4138` (main, `PortfolioViewModel.loadPortfolio` →
`startPositionUpdates`) и `4138-4224` (worker, `TScalpApplication.restoreTInvest`
→ `positionFlowManager.start`). Оба вызова легитимны, оба
защищены идемпотентностью — она не срабатывает из-за race.

Та же картина на `PriceFlowManager` (два `TInvestWS/prices connecting`
подряд от двух `setInterest` из `PortfolioViewModel` и
`OrdersViewModel`) и на `OrderFlowManager` (симметричный код).

**Наблюдение live-test 2026-11-08 (16b):** после fix PR #34/#35 утечка
orphan-job'ов не воспроизводится (нет двух параллельных `connecting`), но
**остаточный multi-call `startPositionUpdates`** на старте / после
force-stop виден в логах:

    14:11:45.393 startPositionUpdates: global=8e43..., accounts=[8e43, 1fbe]
    14:11:45.449 startPositionUpdates: global=8e43..., accounts=[8e43, 1fbe]
    14:11:45.484 startPositionUpdates: global=8e43..., accounts=[8e43, 1fbe]

— три вызова за ~90 мс. И при force-stop:

    13:37:51.260 startPositionUpdates: global=null, accounts=[1fbe]
    13:37:51.268 startPositionUpdates: global=8e43..., accounts=[8e43, 1fbe]

— `global=null` (per-card restore до `loadAccounts`) → `global=8e43`
(после `loadAccounts`). Функционально корректно: `stopPositionUpdates()`
отменяет `job`, утечки нет, `PositionFlowManager` state проходит
`POLLING → INITIALIZING → POLLING` 2–3 раза. Но лишние переподключения
стрима + дубли `PositionFlowManager: Перезапуск` в логах.

**Причина остаточного multi-call (2026-11-08):** `synchronized(startLock)`
закрывает race между параллельными `start()`, но **не** закрывает
race «два последовательных `start()` с разными `accountId` в одном
потоке» — `_state.update` (один set `selectedAccountId`) + `start`
из `restoreLastSelectedInstruments` (per-card, до `loadAccounts`) +
`start` из `loadAccounts` (global, после). `start()` идемпотентен
по `(currentAccountId, job)` — но второй вызов с другим `accountId`
не идемпотентен и пересоздаёт job. Это **не** утечка (job отменяется),
это **лишний рестарт**.

**Решение (отдельный fix):** не вызывать `start` из
`restoreLastSelectedInstruments` при `globalAccountId == null` —
ждать `loadAccounts` (Coord `selectedAccountId`), который стартует
один раз с полным набором `accounts`. Или debounce `start` (100 мс
после последнего set). Решение — отдельной задачей (1–2 ч).

**Причина (подтверждена чтением кода):** `start()` в
`PositionFlowManager` / `PriceFlowManager` / `OrderFlowManager`
имеет критическую секцию без синхронизации между проверкой
идемпотентности и присвоением `job`:

    fun start(accountId: String, forceRestart: Boolean = false) {
        if (!forceRestart && job?.isActive == true && currentAccountId == accountId) {
            return
        }
        if (currentAccountId != accountId) { /* snapshotCache.clear() */ }
        job?.cancel()                 // ← нет синхронизации
        currentAccountId = accountId  // ← нет синхронизации
        job = scope.launch { ... }    // ← нет синхронизации
    }

Interleaving при двух параллельных вызовах (A из main, B из worker):

1. A: `job?.isActive == true && currentAccountId == accountId` →
   `false` (оба null) → проверка пройдена.
2. B: то же → проверка пройдена.
3. A: `job?.cancel()` (no-op), `currentAccountId = acc`, `job_A = launch{...}`.
4. B: `job?.cancel()` (no-op или отменяет `job_A` в зависимости от
   точного момента), `currentAccountId = acc`, `job_B = launch{...}`
   — перезаписывает ссылку.

**Результат:** два активных job'а, ссылка на один (`job_B`).
`job_A` — orphan: продолжает эмитить в `_flow` до перезапуска
процесса. Следующий `stop()` отменит только `job_B`, `job_A`
останется. Это **утечка job'ов**, а не только дубль транспорта.

**Уточнение к старой записи «Двойное WS-подключение к PositionsStream»**
(была отложена, требует подтверждения — теперь подтверждена и
расширена):
- Гипотеза №3 (два подписчика на cold flow
  `broker.subscribePositions()`) **не подтвердилась**: подписчики
  на `_flow` (hot `SharedFlow`), не на cold flow брокера.
- Гипотеза №2 (перезапуск создаёт второй `collect`, оставляя
  первый активным) — подтвердилась, но **механизм другой**:
  не «оставил активным», а «создал orphan из-за race в
  `start/stop`».
- Гипотеза №1 (двойной вызов `start`) — подтвердилась:
  `TScalpApplication` + ViewModel.

**Происхождение:** паттерн «cancel-then-restart» вошёл в код
вместе с задачей «Ktor REST transport для polling» и
debug-переключателями транспортов (`toggleRest` →
`reapplyDebugFlags()`). В `PositionFlowManager.reapplyDebugFlags`
и `PriceFlowManager.reapplyDebugFlags` используется та же схема
`job?.cancel(); start(..., forceRestart = true)` — потенциально
с той же race-природой. **Сами переключатели нужны** для
динамических live-тестов транспортов — их не удалять. Фикс
(`synchronized`) закрывает и их случай тоже.

**Последствия без фикса (усугубление при BCS / Finam):**

1. **Больше вызовов.** `TScalpApplication.restoreBrokerConnection`
   вызывается в цикле по всем брокерам. N брокеров × M ViewModel =
   N×M потенциальных гонок.
2. **Больше транспортов на один дубль.** У Finam три транспорта
   (gRPC / WS / REST) — двойной `start()` = двойной набор всех
   трёх, не одного.
3. **Rate-limits.** T-Invest: `80001` «превышен лимит одновременных
   stream-соединений»; BCS: 429 Too Many Requests; Finam — свои
   лимиты. Дубль подключений съедает квоты без пользы.
4. **Диагностика.** События приходят в два потока, UI получает
   один из них — расследование инцидентов в проде усложняется
   пропорционально числу дублей.

**Решение:**

1. Добавить приватный lock-объект в каждый менеджер:

        private val startLock = Any()

2. Обернуть в `synchronized(startLock) { ... }`:
   - `PositionFlowManager.start` / `stop` / `reapplyDebugFlags`
   - `PriceFlowManager.setInterest` / `clearInterest` /
     `reapplyDebugFlags`
   - `OrderFlowManager.start` / `stop`
3. Критическая секция — короткая: только чтение `job` /
   `currentAccountId`, изменение, `launch`. Внутрь секции
   `scope.launch` кладётся без `join` (сам launch — не suspend),
   так что блокировки на IO не будет.
4. Покрыть unit-тестом: два параллельных `start(accountId)`
   из разных корутин → `job` одинаковый (сравнение ссылок),
   `broker.subscribePositions()` вызван один раз (мок-счётчик).

**Почему `synchronized`, а не `Mutex`:**
- `start()/stop()/setInterest()` — не `suspend`, ни одного
  suspension point в критической секции. `synchronized` проще,
  не требует `coroutineScope`/`runBlocking` на вызывающей стороне.
- Если в будущем понадобится suspend внутри (например,
  асинхронная проверка инициализации брокера) — заменить на
  `Mutex`, но сейчас это over-engineering.

**Файлы:**
- `app/src/main/kotlin/.../domain/api/PositionFlowManager.kt`
- `app/src/main/kotlin/.../domain/api/PriceFlowManager.kt`
- `app/src/main/kotlin/.../domain/api/OrderFlowManager.kt`
- `app/src/test/kotlin/.../domain/api/PositionFlowManagerTest.kt`
  (дополнить тест на race)

**Когда вернуться:** сейчас. Фикс изолированный, разблокирует
BCS / Finam без утечек.

**Связанные записи:**
- «Двойное WS-подключение к PositionsStream» — объединено в
  эту запись (2026-10-22).
- `docs/ROADMAP.md` → «Оркестратор стримов и Ktor WS transport» —
  оркестратор как downstream потребитель корректен, проблема
  upstream (в менеджерах).

---
<!-- SUBSECTION_END: i-2 -->










<!-- SUBSECTION_BEGIN: i-7 -->
___
### [DONE] I-7 Позиции не обновляются при смене счёта
**Статус:** решён (фикс в `### StreamState в data-слое`).

**Симптом:** при переключении счёта в UI остаются позиции
предыдущего счёта. Позиции нового счёта добавляются, но
старые не исчезают.

**Причина (подтверждена аудитом 2026-10-18):**

`PositionFlowManager._flow` — `MutableSharedFlow(replay = 1)`.
`stop()` вызывает `_flow.resetReplayCache()`, а `start(newAccountId)`
— **нет**. При смене счёта:

1. `OrdersViewModel` / `PortfolioViewModel` вызывают
   `positionFlowManager.start(newAccountId)` без
   предварительного `stop()`. `stop()` есть **только в
   logout-ветке** (`else` от `isInitialized`), а не при смене
   счёта.
2. `start()` делает `snapshotCache.clear()` и `job?.cancel()`,
   но не `_flow.resetReplayCache()`. Replay-кэш сохраняет
   последний элемент от **старого** счёта.
3. Новый коллектор в ViewModel подписывается на `_flow` и
   первым получает элемент от старого счёта. `updatePortfolioItem`
   / `updatePositionPnl` вставляет его в UI.
4. Если `tscalpInstrumentId` этой позиции нет в новом счёте —
   она не будет вытеснена никогда. Отсюда «залипшие» позиции.

Подтверждение по коду: `start()` и `stop()` в
`PositionFlowManager`; смена счёта в `OrdersViewModel`
(`startPositionUpdates()`, строка ~905) и `PortfolioViewModel`
(`startPositionUpdates(accountId)`, строка ~150) — обе без
`stop()` у менеджера.

**Решение:**

В `PositionFlowManager.start(accountId)` при
`currentAccountId != accountId` — вызвать
`_flow.resetReplayCache()` (до `job?.cancel()`, чтобы новый
подписчик гарантированно не получил старый элемент). Это
симметрично `stop()`, который уже делает сброс.

Альтернатива — добавить `positionFlowManager.stop()` в
`startPositionUpdates()` ViewModel'ей перед `start()`, но это
задевает три ViewModel и оставляет возможность вызвать
`start()` напрямую (например, из `TScalpApplication`). Фикс в
`start()` менеджера — единственная точка, надёжнее.

**Остальные гипотезы (не подтверждены, оставлены как контекст):**

2. `PortfolioViewModel` не очищает список позиций при смене
   счёта — накапливает элементы с разными
   `tscalpInstrumentId`. Проверить, если после фикса №1
   симптом повторится.
3. Гонка между `start(newAccountId)` и первыми событиями
   gRPC / polling для нового счёта. Проверить, если
   повторится.

**Файлы:**

- `domain/api/PositionFlowManager.kt` — фикс (`start()`).
- `presentation/screens/portfolio/PortfolioViewModel.kt` —
  контекст (смена счёта).
- `presentation/screens/orders/OrdersViewModel.kt` — контекст.

**Когда вернуться:** сейчас, при следующей сессии на десктопе.
Отдельная ветка `bugfix/position-stream-replay` от `dev`,
фикс + тест (проверить, что после смены счёта replay-кэш пуст
до первого элемента нового счёта).

---
<!-- SUBSECTION_END: i-7 -->










<!-- SUBSECTION_BEGIN: i-9 -->
___
### [DONE] I-9 PositionsStream не доставляет данные
**Статус:** решён (2026-10-15, PR #20).

**Симптом (до решения):** P&L не обновлялся в реальном времени
через стрим.
- Песочница: `UNAUTHENTICATED: 40003`.
- Боевой: ошибок нет, но `hasPosition()` не приходил.

**Причина:** прошлые попытки (SDK 1.48.1) искали несуществующий
класс `PositionsStreamServiceGrpc`. Стрим позиций живёт в
`OperationsStreamService.positionsStream` — там же, где
`PortfolioStream`. Дополнительные факторы: старый SDK и ручной
`MethodDescriptor` вместо SDK-обёртки.

**Решение:**
- Обновление SDK 1.48.1 → 1.51.0.
- `subscribePositionsGrpc` через
  `api.operationsStreamServiceAsync.positionsStream(request)` с
  `withInitialPositions = true`.
- Параллельная схема (gRPC + polling) в `subscribePositions`.
  Отказ от последовательного каскада: gRPC может молчать, а
  polling обязан работать.
- Обогащение в `PositionFlowManager`: gRPC-события дополняются
  `currentPrice`, `averagePositionPrice`, `expectedYield` из кэша
  polling.
- Автопереподключение gRPC с backoff [1s, 5s, 15s, 30s, 60s].

**Проверено:**
- Боевой: `SUBSCRIPTIONS` → `INITIAL_POSITIONS` за 100 мс,
  `PING` раз в 60 секунд.
- Песочница: тот же flow, `hasPosition()` приходит.
- Обогащение: gRPC `POSITION` (qty=7) получило
  `price=103.54, yield=42.08` из кэша polling.

**Детали:** `docs/streams.md` → «PositionsStream»,
`docs/ROADMAP.md` → закрытая задача «gRPC-стрим позиций».

---
<!-- SUBSECTION_END: i-9 -->










<!-- SUBSECTION_BEGIN: i-10 -->
___
### [DONE] I-10 T-Invest WebSocket: решён — канал должен быть в URL path
**Статус:** решён (2026-10-19). Транспорт подтверждён через
websocat на прод-аккаунте (initialPositions с реальными данными).
Live-тест на устройстве через `TInvestPositionsWebSocket` —
ожидается на десктопе.

**Причина (главное):** канал T-Invest — **часть URL path**, а не
только тело подписки. Правильный URL:

    wss://invest-public-api.tbank.ru/ws/tinkoff.public.invest.api.contract.v1.OperationsStreamService/PositionsStream

Голый `/ws` без канала → сервер не знает, какой стрим открывать,
и отвечает `404` / `timeout` / silent hang. С каналом в пути —
handshake проходит за миллисекунды.

**Подтверждение (websocat 1.14.1, `-k`, прод-токен, боевой счёт
2309452027):**

    websocat -vv -k "wss://invest-public-api.tbank.ru/ws/tinkoff.public.invest.api.contract.v1.OperationsStreamService/PositionsStream" \
      -H "Web-Socket-Protocol: json" \
      -H "Authorization: Bearer <token>"

Ответ сервера:

- `Connected to ws` + `upgrade: websocket`, `sec-websocket-accept`,
  `x-k8s-istio-drop-ok: true` (это флаг «пропущено», не ошибка).
- После отправки подписки
  `{"accounts":["2309452027"],"withInitialPositions":true}`:
  `{"subscriptions": {"accounts": [{"accountId": "2309452027",
  "subscriptionStatus": "POSITIONS_SUBSCRIPTION_STATUS_SUCCESS"}],
  "trackingId": "...", "streamId": "..."}}`
- Далее `initialPositions` с реальными securities
  (SBER, TMON@, instrumentUid совпадает с логами приложения).

**Коды закрытия WS (диагностический справочник):**

- **`1013`** — соединение открыто, но подписка не пришла за
  ~10 секунд. Сервер закрывает по таймауту ожидания подписки.
  В нашем клиенте не возникает — `TInvestPositionsWebSocket`
  отправляет подписку сразу после `connected`.
- **`4001 Unauthorized`** — handshake прошёл, но токен не
  принят в `Authorization`. Проверить токен / режим
  (sandbox vs prod).
- **`1011` + `reason: "50004"`** — `NOT_FOUND` (счёт не
  существует). Проверить accountId: соответствует ли он токену.

**Что было опровергнуто по ходу сессии:**

Все три гипотезы оказались ложными — они были следствием
неправильного URL, а не самостоятельными причинами:

1. ~~Rate-limit по IP / User-Agent.~~ Мобильный IP давал тот же
   silent hang на голом `/ws`, что и домашний. С каналом в URL
   работает с любого IP.
2. ~~Серверная деградация T-Invest.~~ Сервер всё время работал,
   просто мы били не туда.
3. ~~permessage-deflate в OkHttp.~~ Отличие OkHttp от websocat
   в handshake есть, но не оно решало: с правильным URL и OkHttp,
   и Ktor CIO прошли бы одинаково. CIO оставляем как рабочий
   вариант (уже мигрирован в `934646d`).

**Матрица экспериментов (историческая, 2026-10-18):**

Все варианты ниже — на голом `/ws` без канала в пути. Каждый
давал `404` / `timeout` / silent hang, но не из-за заголовков
или клиента, а из-за отсутствия канала в URL:

- URL `/ws/` + `Authorization` + `Web-Socket-Protocol: json` → 404.
- URL `/ws` (без слэша) + те же → 404.
- `Web-Socket-Protocol: json, <token>` без `Authorization` → 404.
- `Sec-WebSocket-Protocol: json` → 404.
- `+Origin` → 404.
- `+User-Agent: websocat/1.14.1` → timeout 10s (сдвиг с 404, но
  не решение).
- `protocols(HTTP_1_1)` в OkHttpClient → timeout.
- `minWebSocketMessageToCompress(Long.MAX_VALUE)` → timeout.
- Ktor CIO с теми же заголовками → silent hang.
- `curl POST /rest/.../PositionsStream` HTTP/2 и HTTP/1.1 → 404
  от Envoy. REST-прокси для streaming — отдельная история, к
  WS отношения не имеет.

**Решение (сделано в коде):**

- `TInvestBrokerApi.WS_URL` разбит на `WS_BASE_URL` +
  `WS_POSITIONS_CHANNEL` + собранный `WS_URL` (коммит `777fa60`).
- `buildSubscriptionEnvelope` — плоский JSON
  `{accounts, withInitialPositions}` (без обёртки `positionsStream`).
- `TInvestPositionsWebSocket` — Ktor CIO
  (`HttpClient(CIO)` + `CustomTrustManager` через
  `engine { https { trustManager = ... } }`), коммит `934646d`.

**Что осталось:**

- Live-тест на десктопе: пересобрать, установить, запустить,
  в logcat `TInvestWS` ожидаем `connected` → `subscription sent`
  → `frame received`.
- Если данные идут — merge `feature/ws-okhttp-fallback` в
  `feature/stream-orchestrator`, затем в `dev`.
- Правка `TInvestPositionsWebSocket`: retry на `404` и `1011 +
  50004` не нужен (это не временные ошибки). Retry только на
  `timeout` / `IOException` / `SSLException`, интервал 60s+
  вместо 1s. Оформить отдельной задачей.

**Точка отката:** ветка `feature/ws-okhttp-fallback`, коммиты
`777fa60` (URL с каналом), `b34d614` (DebugConfig),
`934646d` (CIO).

---
<!-- SUBSECTION_END: i-10 -->










<!-- SUBSECTION_BEGIN: i-14 -->
___
### [DONE] I-14 TInvestBrokerAPI.initialize() не закрывает предыдущий positionsWebSocket
**Статус:** решён (#11, `TInvestWsService.stop()` + lifecycle).

**Симптом:** `initialize(token, sandbox)` перезаписывает
`positionsWebSocket = TInvestPositionsWebSocket(...)` без закрытия
предыдущего. Ре-логин, смена токена или повторный «Сохранить» в
настройках без logout → старая WS-сессия держит сокет, получает
ping/frames в никуда. Утечка соединения + лишняя нагрузка на сервер (WS-сессии
накапливаются, каждая держит сокет и heartbeat). Ссылка на
запись про транспорт: `ISSUES → «T-Invest WebSocket: решён —
канал должен быть в URL path»`.

**Решение:** в `initialize()` перед созданием нового — вызвать
`positionsWebSocket?.close()`. Метод `close()` в
`TInvestPositionsWebSocket` сейчас отсутствует — добавить (закрыть
OkHttp/CIO сессию, отменить Flow). `deinitialize()` уже обнуляет
`positionsWebSocket`, но тоже без `close()` — та же утечка при logout.

**Файлы:** `data/api/TInvestBrokerApi.kt`,
`transport-ktor/.../TInvestPositionsWebSocket.kt`.

**Когда вернуться:** при следующей сессии на десктопе, ветка
`bugfix/ws-lifecycle`.

---
<!-- SUBSECTION_END: i-14 -->










<!-- SUBSECTION_BEGIN: i-17 -->
___
### [OPEN] I-17 PriceFlowManager.collectStream: потенциальный busy loop
**Статус:** открыт. Латентный, не воспроизводится сейчас. Не для WS.

**Симптом:**

    while (isActive) {
      try {
        repository.subscribeLastPrices(broker, union.toList()).collect { ... }
        attempt = 0  // stream completed by server, reconnecting
      } catch (e) { ... delay(...) }
    }

Если `subscribeLastPrices` вернёт `emptyFlow()` (дефолт `BrokerApi`,
используется BCS / Finam), `collect` завершится **мгновенно**, без
исключения. `attempt = 0`, «stream completed by server», новый
вызов, снова мгновенно. **Busy loop без задержки** — CPU 100%,
батарея в ноль.

Сейчас `PriceFlowManager` хардкодит `BrokerName.TINVEST`, у
T-Invest `subscribeLastPrices` не пустой. Латентный: станет
реальным при расширении `PriceFlowManager` на BCS / Finam.

**Решение:** если `collect` завершился без единой эмиссии за N
секунд — `delay(1_000)` перед следующим кругом. Или проверять
через `first()` с таймаутом и трактовать отсутствие эмиссии как
ошибку (ретрай с delay).

**Файлы:** `domain/api/PriceFlowManager.kt`.

**Касается также:** `domain/api/StreamCombinators.kt` —
`retryWithBackoff`. При normal completion
(`source().collect { emit(it) }` вернулся без исключения)
`attempt = 0`, новый цикл **без delay** -> busy loop, если
source возвращается мгновенно-пустым (сервер закрыл стрим,
gRPC завершился нормально). Фикс — тот же (delay при
отсутствии эмиссии / первый шаг с задержкой). См.
`docs/clean-audit.md` -> L4-Н32.

**Когда вернуться:** при добавлении BCS / Finam в
`PriceFlowManager`, или при жалобах на батарею в фоновом режиме.

---
<!-- SUBSECTION_END: i-17 -->










<!-- SUBSECTION_BEGIN: i-18 -->
___
### [OPEN] I-18 PriceFlowManager.runPolling swallows CancellationException
**Статус:** открыт.

**Симптом:** в `PriceFlowManager.runPolling` единственный
`catch (e: Exception)` без предшествующего
`catch (e: CancellationException) { throw e }`.
`CancellationException` перехватывается и логируется как
«REST poll failed», затем цикл выходит по
`while (currentCoroutineContext().isActive)`, но сигнал
отмены родителю не пробрасывается — менеджер останавливается
«тихо», без propagation.

**Причина:** нет `catch (e: CancellationException) { throw e }`
перед общим `catch (e: Exception)`, как в
`PositionFlowManager.runPolling` и `OrderFlowManager.runPolling`.

**Решение:** добавить `catch (e: CancellationException) { throw e }`
перед общим `catch (e: Exception)` в `runPolling`.

**Файлы:** `domain/api/managers/PriceFlowManager.kt`, метод
`runPolling`.

**Связанные:** `docs/clean-audit.md` -> L5-Н39 (та же функция
`runPolling` переписывается при выносе `toAppError` из domain).

**Когда вернуться:** при следующей правке `PriceFlowManager`,
или вместе с L5-Н39.

---
<!-- SUBSECTION_END: i-18 -->










<!-- SUBSECTION_BEGIN: i-19 -->
___
### [OPEN] I-19 PositionsStream over WS не отдаёт данные в песочнице
**Статус:** открыт. Ограничение сервера, не наш код.

**Симптом:** при подключении к sandbox-WS
(`wss://sandbox-invest-public-api.tbank.ru/ws/.../PositionsStream`)
сервер принимает handshake и подписку, но сразу закрывает
соединение (`Received WebSocket close message`) без единого
кадра. Ни `subscriptions`, ни `initialPositions`, ни ошибки.

**Подтверждено:**
- Ktor CIO клиент (`TInvestPositionsWebSocket`) — воспроизводится.
- `websocat -k` с тем же URL, токеном и payload
  (`{"accounts":["<sandbox-accountId>"],"withInitialPositions":true}`)
  — идентичный результат: `Connected to ws` → `close message`
  без кадров.

**Контекст:** тот же `PositionsStream`, тот же payload на проде
(`wss://invest-public-api.tbank.ru/ws/...`) работает end-to-end:
`subscriptions` → `POSITIONS_SUBSCRIPTION_STATUS_SUCCESS`,
`initialPositions` со списком позиций, `ping` раз в 60 секунд.

**Причина:** неизвестна. Гипотеза — ограничение песочницы
T-Invest на WS `PositionsStream`, аналогичное известному
ограничению `OrderStateStream` в sandbox
(`UNAUTHENTICATED: 40003`, см. `docs/streams.md`).

**Что сделано в клиенте:** WS URL теперь маршрутизируется по
режиму: прод → `invest-public-api.tbank.ru`, sandbox →
`sandbox-invest-public-api.tbank.ru` (см.
`TInvestBrokerAPI.buildWsUrl`). Это закрыло другую проблему —
sandbox-токен раньше уходил на прод-хост. Само ограничение
sandbox-WS не лечится на нашей стороне.

**Решение:** в песочнице WS-стрим позиций фактически не
работает; данные идут через gRPC + polling. Fallback работает
как задумано: WS молча закрывается, оркестратор продолжает
получать данные от двух других источников. Для UI это
прозрачно.

**Дополнение (2026-10-21):** при добавлении WS для цен
(`TInvestPricesWebSocket`) и ордеров
(`TInvestOrderStateWebSocket`) эти клиенты создаются **только
при `!sandbox`** — иначе они крутили бы бесконечный
reconnect-loop без пользы. С 2026-10-27 `TInvestPositionsWebSocket`
тоже создаётся только при `!sandbox`: `TInvestWsService.initialize`
начинается с `if (sandbox) return`. Все три WS-клиента управляются
единым `initialize` / `stop`.

**Когда вернуться:**
- При обновлении SDK/документации T-Invest — возможно,
  ограничение снято.
- При официальном подтверждении от поддержки T-Invest.
- Если на проде WS перестанет работать по той же схеме
  (закрытие без кадров) — это уже другой баг, не sandbox.

**Связанные файлы:**
- `app/src/main/kotlin/.../data/api/TInvestBrokerApi.kt` —
  `buildWsUrl`.
- `transport-ktor/src/commonMain/.../TInvestPositionsWebSocket.kt` —
  клиент.
- `docs/streams.md` — похожее ограничение OrderStateStream.

---
<!-- SUBSECTION_END: i-19 -->










<!-- SUBSECTION_BEGIN: i-27 -->
___
### [OPEN] I-27 REST poll failed: REST transport not initialized (W при disconnect)
**Статус:** открыт (pre-existing, не блокер).

**Симптом:** при disconnect брокера TInvest в logcat появляется:

    W PriceFlowManager: REST poll failed: REST transport not initialized
    W PriceFlowManager: java.lang.IllegalStateException: REST transport not initialized
        at com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestRestService.pricesRestFallback(TInvestRestService.kt:50)

**Причина:** `DisconnectBrokerUseCase` (и старый `SettingsViewModel.clearBrokerCredentials` до M006) вызывает `lifecycleTInvest?.deinitialize()` — REST-транспорт закрывается. `PriceFlowManager` polling loop (running in separate scope) может находиться в полёте в момент disconnect, следующий tick polling → REST fallback → `IllegalStateException("REST transport not initialized")` → ловится в `try/catch` (`runPolling`), логируется как W. Не краш.

**Влияние:** W-шум в логах, не влияет на функциональность (disconnect проходит корректно, стримы останавливаются). После `setInterest(PORTFOLIO, size=0)` от UI → `Union empty, gRPC stream stopped` → polling остановится.

**Решение:** (вариант A) остановить `priceFlowManager` в `DisconnectBrokerUseCase` ДО `lifecycle.deinitialize()` — сейчас порядок: repo clear → tradingState → deinit → stop position+order. Не хватает `stop priceFlowManager` в TInvest-ветке. Тогда polling остановится до deinit, W исчезнет.

(вариант B) Пропустить (W не критичен, pre-existing с самого введения `DisconnectBrokerUseCase` / старого `clearBrokerCredentials`).

**Файлы:**
- `domain/usecases/DisconnectBrokerUseCase.kt` — добавить `priceFlowManager.stop()` для TInvest (или `clearInterest(PriceConsumer.PORTFOLIO)` + `stop()`).
- `domain/api/managers/PriceFlowManager.kt` — опционально: обработка `IllegalStateException` в `runPolling` как тихий skip, если REST не инициализирован.

**Связано:** `docs/ROADMAP.md` → `### ✅ M006` (закрыта, PR #103), `### PriceFlowManager.runPolling swallows CancellationException` (та же функция `runPolling`).

**Когда вернуться:** при следующем касании `DisconnectBrokerUseCase` / `PriceFlowManager`; или при появлении жалобы на W-шум в логах.
---
<!-- SUBSECTION_END: i-27 -->










<!-- SUBSECTION_BEGIN: i-29 -->
___
### [OPEN] I-29 PriceFlowManager retry loop после logout при неактивных VM
**Статус:** открыт (pre-existing, не блокер).

**Симптом:** после logout (`DisconnectBrokerUseCase: success`) и при неактивных VM (`PortfolioViewModel` / `OrdersViewModel` не созданы — пользователь на экране Настроек) в logcat:
- `PriceFlowManager: state: LIVE → INITIALIZING`
- `PriceFlowManager: state: INITIALIZING → LIVE` (через ~30с) — retry loop на `UNAUTHENTICATED` (токен отозван)
- `PriceFlowManager: state: LIVE → STALE` (через ~17с)

Менеджер не останавливается, продолжает retry. `clearInterest` не вызывается (`checkApiInitialization` не сработал из-за pre-existing бага `anyInitialized` — см. запись выше).

**Причина:** `DisconnectBrokerUseCase` останавливает `positionFlowManager` + `orderFlowManager`, но **не** `priceFlowManager`. Цена останавливается только через `clearInterest(PriceConsumer.PORTFOLIO/ORDERS)` в VM — а VM неактивны. С retry-loop на `UNAUTHENTICATED` в `collectStream` (см. `retryWithBackoff` / `collectStream` в `PriceFlowManager`).

**Решение:** (A) `DisconnectBrokerUseCase` останавливает `priceFlowManager` для TInvest (симметрично position/order). (B) `PriceFlowManager.collectStream` — останавливать retry на non-recoverable ошибках (`UNAUTHENTICATED`, `PERMISSION_DENIED`); частично сделано в `retryWithBackoff` (см. `clean-audit` L4-Н60), но `collectStream` — нет.

**Файлы:** `domain/usecases/DisconnectBrokerUseCase.kt`, `domain/api/managers/PriceFlowManager.kt` (`collectStream`).

**Связано:** ISSUES «REST poll failed: REST transport not initialized (W при disconnect)» (та же область), `docs/clean-audit.md` → L4-Н60 (`retryWithBackoff` non-retryable), запись выше (`anyInitialized` после disconnect — причина не-вызова `checkApiInitialization`).

**Когда вернуться:** при следующем касании `DisconnectBrokerUseCase` / `PriceFlowManager`, или вместе с «REST poll failed».
<!-- SUBSECTION_END: i-29 -->





<!-- SECTION_END: issues-streams -->
