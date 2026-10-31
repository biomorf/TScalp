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

## Separator между секциями ROADMAP не везде по шаблону

**Статус:** открыт.

**Симптом:** AGENTS.md (раздел «Порядок секций в плановых
документах») требует разделитель `\n\n\n\n---\n` (три пустые
строки, `---`, одна пустая) между секциями плановых документов.
Фактически в `docs/ROADMAP.md` между некоторыми секциями —
`\n\n\n\n` без `---` (например, между концом секции
«Разбивка InvestRepository» и началом `### H21b`).

**Причина:** шаблон separator добавлен в AGENTS.md позже, чем
создавались существующие секции. При вставке новых задач через
скрипты (4c) separator воспроизводится по шаблону только для
новых; старые секции остаются как были.

**Последствия:**
- Markdown-рендер: секции визуально слипаются (нет
  горизонтальной линии-разделителя).
- Скрипты, ожидающие `---` как границу блока (например, при
  извлечении секции), не находят её.
- Неоднородность: часть секций с `---`, часть без.

**Решение:** нормализовать separator во всех секциях
`docs/ROADMAP.md` и `docs/ci-roadmap.md` — привести к шаблону
`\n\n\n\n---\n`. Механически через скрипт (4c), идемпотентно
(4d). Отдельной задачей или в рамках следующей правки этих
файлов.

**Когда вернуться:** при следующей правке ROADMAP / ci-roadmap
с вставкой нескольких секций, или при подключении markdown-
линтера (ROADMAP → «Markdown linter в CI»).

**Примечание (2026-10-27):** обнаружено при вставке задачи
«BrokerApi: ревизия fat-части…». Новая задача вставлена с
`---` (по шаблону AGENTS), соседние секции оставлены как были.

---

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

**Примечание (2026-10-26):** правило добавлено в AGENTS 4d
(`rstrip()` каждой строки в скриптах вставки). Одноразовая
чистка `sed -i 's/[[:space:]]*$//' docs/*.md` — pending.

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

## GrpcExceptionGuard не установлен

**Статус:** в работе (deferred closure; AppMetrica 2–3 дня).

**Симптом:** на эмуляторе без интернета приложение падает с
`FATAL EXCEPTION: main` без пользовательского стека в crash-буфере.
На реальном устройстве не воспроизводится.

**Подтверждение:** 2026-10-21 в AppMetrica зафиксирован краш
`io.grpc.StatusException at Status.java:547`. Значит, проблема
реальна и влияет на пользователей (1 устройство).

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

**Когда вернуться:** сейчас. Падение подтверждено в AppMetrica.


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
падает в `catch`, `positionFlowManager.start(accountId)` не
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
      positionFlowManager.start(accountId)    // не вызывается
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
     продолжить к `positionFlowManager.start()`;
   - если `savedAccountId == null` и sandbox — попробовать
     `openSandboxAccount()` в отдельном `try/catch`;
   - если `savedAccountId == null` и не sandbox — лог и выход.
3. `positionFlowManager.start()` вызывать независимо от
   того, прошёл ли `getAccounts`.
4. При успешном `getAccounts` — валидировать `savedAccountId`
   (как сейчас: `accounts.any { it.id == savedAccountId }`).

**Сделано:** `restoreTInvest` разбит на три метода
(`restoreTInvest` / `resolveTInvestAccountId` / `tryOpenSandboxAccount`),
`savedAccountId` читается до `getAccounts`, `getAccounts` обёрнут
в отдельный `try/catch` с fallback на сохранённый счёт,
`positionFlowManager.start()` вызывается независимо от результата
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

**Симптом:** в `TInvestPositionsService.subscribePositionsGrpcWithReconnect`
и `subscribePositionsPolling` условие остановки —
`e.message?.contains("NOT_FOUND") == true`. `e.message` у
`StatusException` — человекочитаемый текст, не код. Если формат
изменится, текст локализуется или исключение обернётся в
`CompletionException` — проверка **молча** перестанет срабатывать.
Тогда NOT_FOUND (счёт удалён) → бесконечный retry с backoff до 60s.

**Решение:** `Status.fromThrowable(e).code == Status.Code.NOT_FOUND` —
стандартный API, не зависит от формата `message`.

**Файлы:** `data/api/tinvest/TInvestPositionsService.kt`.

**Касается также:** `domain/api/managers/PositionFlowManager.kt` —
`runPolling` использует тот же pattern
`e.message?.contains("NOT_FOUND") == true` (остановка polling
при удалённом счёте). При изменении формата `e.message`
проверка молча перестанет срабатывать — polling продолжит
крутиться на удалённом счёте. Фикс — тот же:
`Status.fromThrowable(e).code == Status.Code.NOT_FOUND`.
Найдено при аудите L5 (`docs/clean-audit.md`).

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

**Файлы:** `data/api/tinvest/TInvestPositionsService.kt`.

**Касается также:** `domain/api/StreamCombinators.kt` —
`retryWithBackoff` (в `TInvestOrchestrator`, единственный
потребитель — grep 2026-10-27). Там **нет** проверки
non-retryable вообще: при `UNAUTHENTICATED` / `NOT_FOUND` /
`PERMISSION_DENIED` / `INVALID_ARGUMENT` — вечный retry-loop
с backoff до 60s. Фикс — тот же (классификация по
`Status.fromThrowable(e).code`). См. `docs/clean-audit.md` ->
L4-Н32.

**Когда вернуться:** вместе с предыдущей записью.

---

## TInvestBrokerAPI.initialize() не закрывает предыдущий positionsWebSocket

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

## BcsBrokerApi логирует refreshToken (решён)

**Статус:** решён (2026-10-28, ветка `bugfix/security-token-logging`).

**Симптом:** `BcsBrokerApi.initialize(refreshToken, clientId)` пишет
в лог полный refresh-токен:
`AppLogger.d("BcsBrokerApi", "Инициализация с refreshToken=$refreshToken, clientId=$clientId")`.
В debug-сборке — в logcat открытым текстом. В release `AppLogger.d`
отсекается, но `logcat -b all` / крэш-репорты / screenshot при
отладке могут сохранить.

**Причина:** отладочный лог, добавленный при разработке, не
вычищен. Refresh-токен BCS живёт 90 дней и даёт полный доступ
к портфелю и торговле — утечка в логи эквивалентна компрометации.

**Решение:** заменить `$refreshToken` на `***` (redact) или
`refreshToken.len=${refreshToken.length}`. Проверить остальные
логи на предмет утечки токенов (grep по `$token`, `$refreshToken`,
`$accessToken` в `data/api/`).

**Файлы:** `data/api/BcsBrokerApi.kt` (`initialize(refreshToken, clientId)`).

**Связано:** `docs/clean-audit.md` -> L6-Н71.

**Что сделано:** `$refreshToken` заменён на `${refreshToken.length}`.
Остальные логи проверены (`grep` по `$token`, `$refreshToken`,
`$accessToken` в `data/api/`) — других утечек нет.

**Когда вернуться:** не требуется. Открыть заново, если появится
новая логируемая переменная с секретом.

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

## PriceFlowManager.collectStream: потенциальный busy loop

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

## PriceFlowManager.runPolling swallows CancellationException

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

## Race в start/stop *FlowManager — дублирование стримов

**Статус:** решён (PR #34, PR #35, live-test 2026-10-22).

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

## INTERNAL: 70001 при параллельных unary-запросах

**Статус:** открыт. Причина подтверждена.

**Симптом:** `getLastPricesByTscalpInstrumentId` (gRPC unary)
периодически падает с
`io.grpc.StatusException: INTERNAL: 70001` под параллельной
нагрузкой. Наблюдалось в live-тесте 2026-10-21 при открытии
портфеля:

    W PriceFlowManager: REST poll failed: INTERNAL: 70001
    io.grpc.StatusException: INTERNAL: 70001
        at io.grpc.Status.asException(Status.java:547)
        ...

Один цикл polling упал, следующий (через 5 сек) прошёл
успешно — `try/catch` в `PriceFlowManager.runPolling`
корректно обрабатывает, цикл продолжается.

**Причина:** подтверждена по документации T-Invest
(https://developer.tbank.ru/invest/intro/developer/error-codes/errors).
Код `70001` — «Внутренняя ошибка сервиса, попробуйте выполнить
запрос позднее». Код уже расшифрован в
`util/TInvestErrorMessages` (`"70001" to "Внутренняя ошибка
сервиса. Попробуйте позже."`).

Ошибка транзиентная, на стороне T-Invest. В live-тесте
следующий цикл polling (через 5 сек) прошёл успешно.

**Решение (сделано):**
- В `PriceFlowManager.runPolling` логирование переведено
  на `toAppError(BrokerName.TINVEST).message` — в логах
  теперь расшифровка, а не сырой `INTERNAL: 70001`.
- Цикл polling корректно продолжается после ошибки
  (`try/catch`). UI не ломается.

**Дальнейшие шаги:**
- Rate-limiter для unary-запросов (ROADMAP → «Rate-limiter
  для unary-запросов T-Invest») всё ещё актуален для кодов
  `80001`-`80003` (RESOURCE_EXHAUSTED), но не для `70001`.
- Ошибка `70001` на нашей стороне не лечится — только
  ожиданием / повтором.

---

## Дублирующий init {} в SettingsViewModel

**Статус:** решён (2026-10-27). Ветка `bugfix/settingsvm-duplicate-init`
отсутствует локально и на remote; в `SettingsViewModel` сейчас один
`init {}` (проверено 2026-10-27).

**Симптом:** в `SettingsViewModel` два блока `init {}` подряд
(строки ~63–95). Оба запускают `viewModelScope.launch` с
идентичным по составу `_uiState.update` (`isSandboxMode`,
`isConfirmOrdersEnabled`, `hasSavedToken` ×3,
`loadDefaultAccountId`). Первый дополнительно устанавливает
debug-флаги (`disableGrpc` / `disableWs` / `forceRest`).
Второй также подписывается на `brokerManager.anyInitialized`.

**Git-история (уточнено 2026-10-22):**
- Второй `init` появился в `79b3543` (drop OrdersViewModel,
  2026-10-06): в диффе — `}init {` без перевода строки
  (признак копипаста «на скорую руку»), первый `launch`
  полностью дублирует существующий `init`, второй —
  подписка на `brokerManager.anyInitialized`.
  В commit message — только про отвязку от OrdersViewModel,
  про FlowManagers ни слова.
- Debug-флаги добавлены позже, в `952f975` (2026-10-22),
  в **первый** `init`. Второй при этом не тронут.

**Причина:** копипаст при добавлении подписки
`brokerManager.anyInitialized` в `79b3543`. Автор скопировал
существующий `init` целиком, добавил второй `launch`,
исходный оставил. Гипотеза о «workaround для FlowManagers
после restart» **не подтверждается git-историей**: ни в
`79b3543`, ни в связанном `5c90c01` (deinitialize on logout)
не было правок FlowManagers внутри `init`.

**Последствия:**
- Два `_uiState.update` подряд при создании ViewModel — два
  эмита `StateFlow` вместо одного.
- Порядок `viewModelScope.launch` не гарантирован: если второй
  `launch` выполнится раньше первого, debug-флаги на мгновение
  окажутся `false` (default `SettingsUiState`), затем
  установятся первым. Микро-race на первом отображении
  экрана настроек.
- Читателю неочевидно, какой блок «главный».

**Решение:**
1. Объединить два `init {}` в один.
2. Debug-флаги (`disableGrpc` / `disableWs` / `forceRest`) —
   в тот же `_uiState.copy`, что и остальные поля.
3. Подписку `brokerManager.anyInitialized.collect` оставить
   в том же объединённом `init` отдельным `launch`.

**Файлы:**
- `app/src/main/kotlin/.../presentation/screens/settings/SettingsViewModel.kt` —
  строки ~72–108 (два `init {}`).

**Live-тест (2026-10-22, эмулятор Pixel 8a, Android 14):**

Сценарий A — debug-флаги переживают пересоздание VM:
- В Настройках включить `disableGrpc` и `forceRest`.
- Перейти на «Заявки» (Settings VM уничтожается).
- Вернуться в Настройки.
- **Ожидание / результат:** оба тумблера сохранили `true`.
  До фикса второй `init` перезаписывал `_uiState` без debug-полей —
  race мог сбросить `true → false`.

Сценарий B — `anyApiInitialized` работает (logout/login):
- Logout TInvest → вкладка «Заявки» показывает «API не подключён».
  В логе: `Channel shutdownNow invoked`, `[unary/gRPC/TInvest]:
  stopped`, `clearInterest(ORDERS), union changed: 2 → 0`,
  `Union empty, gRPC stream stopped`.
- Login TInvest → стримы поднимаются.
  В логе: `setInterest(ORDERS), union changed: 0 → 2`,
  `Перезапуск потока позиций: null → 2309452027`,
  `[stream/gRPC/TInvest]: SUBSCRIBED → INITIAL_POSITIONS`,
  три `Поток позиций уже запущен` (race-фикс PR #34 работает,
  дублей нет).

Сценарий C — `toggleRest` без дублей:
- Включить `forceRest`. В логе: `toggleRest: enabled=true`
  (ровно 1), `PositionFlowManager: reapplyDebugFlags` (ровно 1),
  `[unary/gRPC/TInvest]: stopped` + `started`, один
  `[stream/gRPC/TInvest]: connecting (attempt=1)`.
- Выключить `forceRest`. Тот же паттерн, без дублей.
- **Наблюдение:** в сценарии C был открыт экран Настроек —
  интерес к ценам пуст, `PriceFlowManager: Union empty, gRPC
  stream stopped`, ветка `forceRest` для `PriceFlowManager`
  в этом сценарии не проверялась. Покрыта отдельно в live-тесте
  PR #34 (`TInvestOrchestrator: pricesSnapshot: forceRest=true`
  → `TInvestRest: POST .../GetLastPrices` → `[manager/polling]`).
  Находка про непустой union как предусловие для проверки
  `forceRest` — записана в «Полировка debug-переключателей»
  (`docs/ROADMAP.md`, коммит `ccd363c`).

**Когда вернуться:** следующая UI-задача по `SettingsScreen`
или в рамках «Рефакторинг ViewModel-слоя»
(ROADMAP → «Рефакторинг архитектурного долга»).

---

## Strip warnings в debug APK

**Статус:** отложен. Не наш код.

**Симптом:** при `./gradlew :app:assembleDebug` (или
`:app:installDebug`) в выводе:

    > Task :app:stripDebugDebugSymbols
    Unable to strip the following libraries, packaging them as
    they are: libandroidx.graphics.path.so, libconscrypt_jni.so,
    libdatastore_shared_counter.so.

**Причина:** `stripDebugDebugSymbols` пытается удалить отладочные
символы из `.so` библиотек для уменьшения APK. Часть библиотек
не содержит strip-таблиц (собраны без них), поэтому strip не
может их обработать и оставляет как есть. Сборка не падает —
`.so` попадают в APK в исходном виде.

**Последствия:** нет. Единственное — debug APK чуть больше.
В release-сборке (`minifyEnabled = false`) strip выполняется,
на маленьких библиотеках разница незначительна.

**Решение:** не наша проблема. Все три — сторонние:
- `libandroidx.graphics.path.so` — AndroidX (core).
- `libconscrypt_jni.so` — Conscrypt (SSL на Android).
- `libdatastore_shared_counter.so` — DataStore (Preferences).

Если понадобится убрать warning — `packaging { jniLibs {
keepDebugSymbols += "..." } }` или `doNotStrip`. Лишний конфиг
ради косметики.

**Когда вернуться:** если warning начнёт накапливаться новыми
библиотеками (5+) или размер debug APK станет проблемой.

---

## Асимметрия lifecycle: старт менеджеров после login vs restore

**Статус:** открыт.

**Симптом:** после fresh login через
`SettingsViewModel.initializeTInvest` стримы поднимаются не мгновенно,
а с задержкой, зависящей от того, активны ли ViewModel экранов. В
live-тесте #2 (2026-10-25 05:18:28–34, лог `~/lifecycle-live.log`):
- позиции (gRPC+WS): `05:18:28.884 connecting` — через 0.7 сек после
  `initializeTInvest: success`;
- цены: `05:18:29.089 setInterest: 2 uids` — через 1.0 сек;
- заказы: `05:18:34.622 setInterest: accountId=2309452027` — через
  6.6 сек.

При старте приложения (`TScalpApplication.restoreTInvest`) стрим
позиций поднимается **сразу** — `restoreTInvest` вызывает
`positionFlowManager.start(accountId)` напрямую.

**Причина:** асимметрия между двумя путями инициализации:
- `TScalpApplication.restoreTInvest` → `lifecycle.initialize` **и**
  `positionFlowManager.start(accountId)`;
- `SettingsViewModel.initializeTInvest/Bcs/Finam` → `lifecycle.initialize`,
  **без** `positionFlowManager.start` / `priceFlowManager.setInterest` /
  `orderFlowManager.start`.

Старт менеджеров после свежего login остаётся на ViewModel'ях
экранов (`PortfolioViewModel`, `OrdersViewModel`) — вероятно, они
триггерят `start` / `setInterest`, когда UI активен. Если
пользователь логинится и остаётся на экране настроек, менеджеры
могут не стартовать до первого перехода на другую вкладку. В
live-тесте #2 задержки почти не было видно — ViewModel'и
предыдущего сеанса были активны (recomposition), триггер сработал
быстро.

**Решение (варианты, не приняты в #2):**
- (A) `SettingsViewModel.initializeTInvest/Bcs/Finam` после успешного
  `lifecycle.initialize` вызывают `positionFlowManager.start(accountId)` /
  `priceFlowManager.setInterest(...)` / `orderFlowManager.start(...)` —
  симметрично `restoreTInvest`. Минус: ViewModel знает про менеджеры
  (уже знает — инжектит для `clearBrokerCredentials.stop()`).
- (B) Менеджеры реактивно подписываются на `brokerManager.anyInitialized`
  + `defaultAccountId` и стартуют сами при их смене. Дороже, но
  убирает специфику старта в `TScalpApplication`.
- (C) Оставить как есть, зафиксировать known behavior.

**Когда вернуться:** при появлении UI-индикатора состояния стримов
(`docs/ROADMAP.md` → `### UI-индикатор состояния стримов`) или при
следующей итерации lifecycle (после `### Реализация BCS Broker API` /
`### Реализация Finam Broker API` — там тоже понадобится решение,
как стартуют менеджеры после login).

**Первое обнаружение:** live-тест #2 `### Broker lifecycle API`,
2026-10-25, лог `~/lifecycle-live.log`. До этой задачи
`TScalpApplication` и `SettingsViewModel` ходили через касты
`as? <ConcreteApi>`, единого пути не было — асимметрия была не
видна. С унификацией через `BrokerLifecycleApi` она проявилась в
логах как разное время старта трёх стримов после login.

**Связанные файлы:**
- `presentation/screens/settings/SettingsViewModel.kt` —
  `initializeTInvest` / `initializeBcs` / `initializeFinam`.
- `TScalpApplication.kt` — `restoreTInvest` / `restoreBcs`.
- `domain/api/PositionFlowManager.kt` /
  `PriceFlowManager.kt` / `OrderFlowManager.kt` — точки `start` /
  `setInterest`, вызываемые из ViewModel.

---

## T-Invest REST findInstruments fallback (N+1 unary)

**Статус:** открыт. Латентный, не блокер.

**Симптом:** REST fallback `findInstruments` (variant B, PR #85) делает N+1 unary-запросов: 1 `InstrumentsService/FindInstrument` + N `InstrumentsService/GetInstrumentBy` (по одному на каждый uid из короткого списка).

**Причина:** REST `FindInstrument` возвращает `InstrumentShort` без `currency`/`lot`, обязательных для `InstrumentUi` (8 обязательных полей в `domain/models/Instruments.kt`). Полный `Instrument` — только через `GetInstrumentBy`. Fallback повторяет gRPC-путь (там тоже N+1).

**Последствия:** при поиске на 100 инструментов — 101 unary-запрос подряд. Fallback редкий (только когда gRPC `UNAVAILABLE` / `DEADLINE_EXCEEDED` / `UNIMPLEMENTED`), но при совпадении с rate-limit T-Invest (6 unary/сек) — `RESOURCE_EXHAUSTED` и деградация поиска.

**Решение:** rate-limiter для unary-запросов (`docs/ROADMAP.md` → `### Rate-limiter для unary-запросов T-Invest`) — обернуть fallback в него, либо батчинг `GetInstrumentBy` (если API даст batch-метод), либо ограничить concurrency `Semaphore`.

**Файлы:**
- `data/api/tinvest/TInvestRestService.kt` — `findInstrumentsRestFallback` (N+1).
- `data/api/tinvest/TInvestOrchestrator.kt` — `findInstrumentsWithFallback` (вызывает fallback).

**Связано:** `docs/ROADMAP.md` → `### Rate-limiter для unary-запросов T-Invest`; ROADMAP → `### ✅ T-Invest REST: findInstruments fallback` (PR #85).

**Когда вернуться:** при появлении rate-limiter (закроется автоматически), или при жалобах на `RESOURCE_EXHAUSTED` при поиске.
