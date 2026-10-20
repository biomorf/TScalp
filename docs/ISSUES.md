# ISSUES

Известные проблемы и отложенные решения проекта TScalp.

**Формат записи:**
- **Статус:** открыт / отложен / решён
- **Симптом:** краткое описание
- **Причина:** почему возникает
- **Решение:** что делать / что сделано
- **Когда вернуться:** триггер для возврата

Справочные документы:
- `docs/streams.md` — работа со стримами T-Invest API
- `docs/gradle-setup.md` — настройка Gradle в Android Studio

---

## Шаблон для новых записей

```markdown
## <Короткое название>

**Статус:** открыт / отложен / решён.
**Симптом:** ...
**Причина:** ...
**Решение:** ...
**Когда вернуться:** ...
```

## Trailing whitespace в docs/*.md

**Статус:** открыт.

**Симптом:** в `docs/ROADMAP.md` есть строки с невидимыми
пробелами в конце (`cat -A` показывает `$` после пробелов).
Встречается в статус-строках задач, разделителях, пустых
строках между блоками.

**Причина:** скрипты вставки через heredoc, которые
ассистент применял для правок `.md`, содержали лишние
отступы в шаблонах. Также возможно — копипаст из
markdown-рендера, где trailing spaces не видны.

**Последствия:**
- `assert` в скриптах не совпадает со строкой (пример:
  `**Статус:** в работе.` не равна `**Статус:** в работе.   `).
- ktlint/markdown-линтеры могут ругаться, если мы когда-то
  их подключим для `.md`.
- git diff показывает шум в конце строк.

**Решение:**
1. Одноразовая чистка: `sed -i 's/[[:space:]]*$//' docs/*.md`
   (проверить через `git diff`, что diff только пробелы).
2. В `AGENTS.md` добавить пункт в правило 4d: «Скрипты
   вставки в `.md` не создают trailing spaces. Шаблон в
   heredoc не содержит отступов после последнего
   непробельного символа каждой строки.»
3. По возможности использовать `rstrip()` для каждой строки
   вставляемого блока перед записью.

**Когда вернуться:** при следующей правке любого
`docs/*.md` или при подключении markdown-линтера.

---

## Gradle 10: устаревшие API в сторонних плагинах

**Статус:** отложен. Не наш код.

**Симптом:** при сборке (Gradle 9.8.1) в конце выводится
предупреждение:

    Deprecated Gradle features were used in this build, making it
    incompatible with Gradle 10.

**Причина:** один или несколько сторонних плагинов (Hilt, KSP,
AGP) используют API, запланированные к удалению в Gradle 10.
`--warning-mode all` не показывает источников из наших скриптов —
значит, предупреждение приходит от плагинов.

**Решение:** ожидать обновлений плагинов. Отслеживать через
release notes Hilt, KSP, AGP. Перед апгрейдом до Gradle 10 —
проверить, что все плагины поддерживают.

**Когда вернуться:** при подготовке к апгрейду Gradle 10
(ожидается в 2027+), либо если предупреждение начнёт
накапливаться с новыми source-локациями.

---

## INVALID_ARGUMENT: 30052 при торговле акциями

**Статус:** открыт. Локализован на стороне API Т-Инвестиций.

**Симптом:** заявки на покупку/продажу акций (SBER, GAZP,
BBG004730N88) завершаются ошибкой `INVALID_ARGUMENT: 30052`.
Фьючерсы работают корректно в том же приложении, с теми же
токенами и счетами. В нашей таблице `TInvestErrorMessages` код
30052 расшифровывается как «Инструмент недоступен для торговли
через API».

**Хронология отладки (2026-05-07):**
1. `checkTradeAvailability` возвращал Error — временно
   закомментировали проверки `buyAvailable`/`sellAvailable`,
   добавили fallback-запрос статуса по `uid`.
2. `GetMarginAttributes` в песочнице → `UNAUTHENTICATED: 40003`.
   Теперь при любой ошибке маржинального запроса флаги `false`,
   заявка не блокируется.
3. Для акций API требует `instrument_uid`. В `BrokerOrderRequest`
   и `StopOrderRequest` добавлено поле `instrumentUid`.
4. Для рыночных заявок передаётся `Quotation(units=1, nano=0)` —
   документация требует ненулевое значение цены.
5. Разделены gRPC-каналы `pricesStreamChannel` / `ordersStateChannel`.
6. Структура `PostOrderRequest` для акции и фьючерса идентична
   (лог `postOrder request fields`). Разницы нет.

**Вывод:** клиентский код корректен, ошибка на стороне API —
сервер не возвращает `postOrder response` (запрос отклоняется
до обработки).

**Дальнейшие шаги:**
- Обратиться в поддержку Т-Инвестиций с дампом `PostOrderRequest`
  и `tracking_id` фьючерсного ответа для сравнения.
- Проверить через `grpcurl` с тем же токеном — подтвердить
  проблему вне нашего приложения.
- Обновить `kotlin-sdk-grpc-core` до актуальной версии.
- После решения — вернуть проверки `buyAvailable`/`sellAvailable`
  в `checkTradeAvailability`.

---

## Позиции не обновляются при смене счёта

**Статус:** открыт, причина найдена. Фикс — одна строка, при
следующей сессии на десктопе.

**Симптом:** при переключении счёта в UI остаются позиции
предыдущего счёта. Позиции нового счёта добавляются, но
старые не исчезают.

**Причина (подтверждена аудитом 2026-10-18):**

`PositionStreamManager._flow` — `MutableSharedFlow(replay = 1)`.
`stop()` вызывает `_flow.resetReplayCache()`, а `start(newAccountId)`
— **нет**. При смене счёта:

1. `OrdersViewModel` / `PortfolioViewModel` вызывают
   `positionStreamManager.start(newAccountId)` без
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
`PositionStreamManager`; смена счёта в `OrdersViewModel`
(`startPositionUpdates()`, строка ~905) и `PortfolioViewModel`
(`startPositionUpdates(accountId)`, строка ~150) — обе без
`stop()` у менеджера.

**Решение:**

В `PositionStreamManager.start(accountId)` при
`currentAccountId != accountId` — вызвать
`_flow.resetReplayCache()` (до `job?.cancel()`, чтобы новый
подписчик гарантированно не получил старый элемент). Это
симметрично `stop()`, который уже делает сброс.

Альтернатива — добавить `positionStreamManager.stop()` в
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

- `domain/api/PositionStreamManager.kt` — фикс (`start()`).
- `presentation/screens/portfolio/PortfolioViewModel.kt` —
  контекст (смена счёта).
- `presentation/screens/orders/OrdersViewModel.kt` — контекст.

**Когда вернуться:** сейчас, при следующей сессии на десктопе.
Отдельная ветка `bugfix/position-stream-replay` от `dev`,
фикс + тест (проверить, что после смены счёта replay-кэш пуст
до первого элемента нового счёта).

---

## GrpcExceptionGuard не установлен

**Статус:** отложен осознанно.

**Симптом:** на эмуляторе без интернета приложение падает с
`FATAL EXCEPTION: main` без пользовательского стека в crash-буфере.
На реальном устройстве не воспроизводится.

**Причина:** `io.grpc.kotlin.ClientCalls` при ошибке канала
доставляет `StatusException` через поздний callback в уже
завершённую suspend-корутину. Доставить некому → исключение
уходит в `Thread.getDefaultUncaughtExceptionHandler()` →
kill process. `CoroutineExceptionHandler` и `.catch { }` не
помогают — исключение не проходит через контекст корутины.

**Решение (отложено):** глобальный `UncaughtExceptionHandler` в
`TScalpApplication`, который проглатывает
`io.grpc.StatusException` / `StatusRuntimeException` и пробрасывает
всё остальное в оригинальный handler.

**Почему отложено:** перехват gRPC-исключений скроет настоящие
сетевые проблемы от AppMetrica.

**Когда вернуться:** если такие падения начнут появляться в
AppMetrica на реальных устройствах. Пока статистика чистая —
не трогаем.

**Связанные логи:**
19:55:30.743 AppMetrica: Unhandled exception received:
at io.grpc.Status.asException(Status.java:547)
at io.grpc.kotlin.ClientCalls$rpcImpl$1$1$1.onClose(ClientCalls.kt:264)
at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
at java.util.concurrent.ThreadPoolExecutor.runWorker

---

## PositionsStream не доставляет данные

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
- Обогащение в `PositionStreamManager`: gRPC-события дополняются
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

## T-Invest WebSocket: решён — канал должен быть в URL path

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

## restoreTInvest не использует сохранённый accountId при падении getAccounts

**Статус:** решён (2026-10-18, PR #25, коммит `776f8b4`).

**Симптом:** если `getAccounts` в `TScalpApplication.restoreTInvest`
возвращает `UNAVAILABLE: upstream connect error`, весь метод
падает в `catch`, `positionStreamManager.start(accountId)` не
вызывается. Стримы gRPC, polling и WS не стартуют. UI показывает
пустоту, даже если `savedAccountId` сохранён в DataStore и
валиден.

**Причина:** `restoreTInvest` построен линейно, без fallback:

    try {
      creds = loadBrokerCredentials() ?: return
      tInvest.initialize(token, sandbox)
      brokerManager.refreshInitializationState()
      accounts = tInvest.getAccounts(sandbox)   // падает UNAVAILABLE
      savedAccountId = loadDefaultAccountId(TINVEST)
      accountId = when { ... }                  // не доходит
      positionStreamManager.start(accountId)    // не вызывается
    } catch (e) { log }

- `savedAccountId` читается **после** `getAccounts`. Если
  `getAccounts` бросает — сохранённое значение не используется.
- Один `try/catch` на весь метод: любой сбой сети или брокера
  прерывает весь `restoreTInvest`.
- `saveDefaultAccountId` вызывается только при успешном
  выборе, но сам accountId в DataStore **работает** и переживает
  перезапуски.

**Решение:**
1. Читать `savedAccountId` **до** `getAccounts`.
2. Обернуть `getAccounts` в отдельный `try/catch`. При падении:
   - если `savedAccountId != null` — использовать его,
     продолжить к `positionStreamManager.start()`;
   - если `savedAccountId == null` и sandbox — попробовать
     `openSandboxAccount()` в отдельном `try/catch`;
   - если `savedAccountId == null` и не sandbox — лог и выход.
3. `positionStreamManager.start()` вызывать независимо от
   того, прошёл ли `getAccounts`.
4. При успешном `getAccounts` — валидировать `savedAccountId`
   (как сейчас: `accounts.any { it.id == savedAccountId }`).

**Сделано:** `restoreTInvest` разбит на три метода
(`restoreTInvest` / `resolveTInvestAccountId` / `tryOpenSandboxAccount`),
`savedAccountId` читается до `getAccounts`, `getAccounts` обёрнут
в отдельный `try/catch` с fallback на сохранённый счёт,
`positionStreamManager.start()` вызывается независимо от результата
`getAccounts`.

**Проверено:** в live-тесте при `UNAVAILABLE: UNAUTHENTICATED: 40003`
от `getAccounts` в логах видно
`TScalpApplication: TInvest: getAccounts failed, fallback to savedAccountId`,
и стрим запускается по сохранённому accountId.

**Когда вернуться:** не требуется. Открыть заново, если снова
появятся репорты о пустом старте после переустановки или логина.

---

## gRPC retry: string matching NOT_FOUND вместо Status.Code

**Статус:** открыт. Небольшой фикс. Связан с WS/retry — см. следующую запись.

**Симптом:** в `TInvestMarketDataService.subscribePositionsGrpcWithReconnect`
и `subscribePositionsPolling` условие остановки —
`e.message?.contains("NOT_FOUND") == true`. `e.message` у
`StatusException` — человекочитаемый текст, не код. Если формат
изменится, текст локализуется или исключение обернётся в
`CompletionException` — проверка **молча** перестанет срабатывать.
Тогда NOT_FOUND (счёт удалён) → бесконечный retry с backoff до 60s.

**Решение:** `Status.fromThrowable(e).code == Status.Code.NOT_FOUND` —
стандартный API, не зависит от формата `message`.

**Файлы:** `data/api/TInvestMarketDataService.kt`.

**Когда вернуться:** при следующей сессии на десктопе, ветка
`bugfix/grpc-error-classification`.

---

## gRPC retry: бесконечный retry на не-recoverable ошибках

**Статус:** открыт. Фикс вместе с предыдущей записью. **Критичен
для WS** — эта ошибка была в логах при отзыве токена.

**Симптом:** только NOT_FOUND останавливает retry. UNAUTHENTICATED
(токен отозван), PERMISSION_DENIED (read-token на write-операцию),
INVALID_ARGUMENT (битый запрос) — retry вечно каждые 60s. Пользователь
с отозванным токеном видит шум в логах, посаженную батарею, лишний
трафик. В этой сессии было ровно так: `UNAUTHENTICATED: 40003`
после отзыва токена — retry-loop крутился, пока не сделали force-stop.
Параллельный WS-retry-loop (см. ROADMAP «Rate-limiter» и «stream-orchestrator»)
мог усугублять rate-limit на стороне T-Invest.

**Решение:** классификация по `Status.fromThrowable(e).code`:
- **не retry:** NOT_FOUND, UNAUTHENTICATED, PERMISSION_DENIED,
  INVALID_ARGUMENT, FAILED_PRECONDITION;
- **retry:** UNAVAILABLE, DEADLINE_EXCEEDED, INTERNAL, UNKNOWN;
- **RESOURCE_EXHAUSTED** — retry, но с отдельной задержкой
  (учитывает rate-limit, см. `docs/rate-limits.md`).

**Файлы:** `data/api/TInvestMarketDataService.kt`.

**Когда вернуться:** вместе с предыдущей записью.

---

## TInvestBrokerAPI.initialize() не закрывает предыдущий positionsWebSocket

**Статус:** открыт. Небольшой фикс. **Критичен для WS** — утечка
WS-соединения напрямую бьёт по rate-limit.

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

## BcsBrokerApi создаёт OkHttpClient без CustomTrustManager

**Статус:** открыт. Латентный. Не для WS.

**Симптом:** `BcsBrokerApi` содержит
`private val client = OkHttpClient()` — отдельный клиент без
российских корневых сертификатов. Сейчас работает (BCS на стандартных
CA), но мина: если BCS введёт российские CA (как T-Invest) — TLS
сломается тихо. Плюс инстанс не переиспользуется с DI-провайдером
(OkHttpClient shared), хотя сейчас других потребителей OkHttp в
DI и нет (после отката провайдера).

**Решение:** инжектить `OkHttpClient` из DI. При появлении других
потребителей (WS, REST) — один shared клиент на всё приложение с
`CustomTrustManager`.

**Файлы:** `data/api/BcsBrokerApi.kt`, `di/DataModule.kt`.

**Когда вернуться:** при интеграции BCS WS, при первой жалобе на
TLS BCS, или когда понадобится shared OkHttpClient из DI.

---

## PriceStreamManager.runGrcpStream: потенциальный busy loop

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

Сейчас `PriceStreamManager` хардкодит `BrokerName.TINVEST`, у
T-Invest `subscribeLastPrices` не пустой. Латентный: станет
реальным при расширении `PriceStreamManager` на BCS / Finam.

**Решение:** если `collect` завершился без единой эмиссии за N
секунд — `delay(1_000)` перед следующим кругом. Или проверять
через `first()` с таймаутом и трактовать отсутствие эмиссии как
ошибку (ретрай с delay).

**Файлы:** `domain/api/PriceStreamManager.kt`.

**Когда вернуться:** при добавлении BCS / Finam в
`PriceStreamManager`, или при жалобах на батарею в фоновом режиме.

---

## PositionsStream over WS не отдаёт данные в песочнице

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
reconnect-loop без пользы. `TInvestPositionsWebSocket` пока
создаётся всегда, включая sandbox — это остаток от первой
реализации. Исправить до PR (тот же принцип: не создавать WS
в sandbox).

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

## Двойное WS-подключение к PositionsStream

**Статус:** отложен, требует подтверждения.

**Симптом:** в logcat `TInvestWS` периодически видны два
параллельных WS-подключения с одинаковым `accountId`. Оба
проходят handshake и подписку, сервер подтверждает обе
(`POSITIONS_SUBSCRIPTION_STATUS_SUCCESS`). В других сессиях
— одно подключение.

**Наблюдение:** факт зафиксирован один раз, в логе от
2026-10-20 17:41:28.988 — два `connecting (attempt=1)` из
разных тредов (12745 и 12752). В логах 18:53 и 19:00 —
одно подключение. Значит, воспроизводится не всегда.

**Причина:** не подтверждена. Гипотезы:
1. `TInvestOrchestrator.positionsStream` вызывается дважды —
   например, `subscribePositions` собирается из двух мест
   (прямой вызов + через `PositionStreamManager`).
2. `PositionStreamManager` перезапускает поток и создаёт
   второй `collect` на WS-flow, оставляя первый активным.
3. `TInvestPositionsWebSocket.subscribePositions` возвращает
   `flow { ... }` — каждый `collect` создаёт новую
   корутину и новое `client.webSocket(...)`. Если flow
   собирается дважды (рекомпозиция во ViewModel, два
   подписчика), получаем два параллельных подключения.

**Файлы для отладки:**
- `transport-ktor/src/commonMain/.../TInvestPositionsWebSocket.kt` —
  `subscribePositions` (создание корутин-канала на каждый
  `collect`).
- `app/src/main/kotlin/.../data/orchestrator/TInvestOrchestrator.kt` —
  вызов `wsFallback(accountId)`.
- `domain/api/PositionStreamManager.kt` — `start()`, защита
  «уже запущен», сбор flow.
- `presentation/screens/portfolio/PortfolioViewModel.kt` —
  точка подписки на `PositionStreamManager.positions`.

**Решение:** пока не принято. Функционально не блокирует
(сервер принимает обе подписки), но создаёт лишнюю нагрузку
и шум в логах. При подтверждении — либо защита от повторного
`collect` в `TInvestPositionsWebSocket` (например,
`shareIn` / `stateIn` с уникальным upstream), либо
устранение двойного вызова на уровне оркестратора.

**Когда вернуться:**
- При следующей правке `PositionStreamManager` или
  `TInvestOrchestrator`.
- При появлении жалоб на нагрузку или лаги от WS.
- При подключении WS к ценам и ордерам (та же природа
  проблемы, проявится вероятнее на частых стримах).

---
