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

**Статус:** открыт, не воспроизводится. Оставлен для
наблюдения.

**Симптом:** при переключении счёта в UI остаются позиции
предыдущего счёта. Позиции нового счёта добавляются, но
старые не исчезают.

**Контекст:** регресс от PR #16 (переход на каскад
gRPC + polling, позже переделан на параллельную схему в
PR #20). Появился после изменений в `PositionStreamManager`
и `TInvestMarketDataService`.

**Текущее состояние (2026-10-15):** не воспроизводится.
Проверено 3–4 переключения между боевым счётом и песочницей
в свежей сессии — позиции замещаются корректно.

**Возможные причины:**
- Гонка (race condition) при первом запуске.
- Побочный эффект состояния эмулятора после падения.
- Одноразовый сбой, который больше не повторялся.

**Гипотезы (если воспроизведётся):**
1. `_flow` в `PositionStreamManager` имеет `replay = 1`.
   При смене счёта через `start(newAccountId)` replay-кэш
   не сбрасывается (сброс только в `stop()`). Новые
   подписчики могут получить элемент от старого счёта.
2. `PortfolioViewModel` не очищает список позиций при
   смене счёта — накапливает элементы с разными
   `tscalpInstrumentId`.
3. Гонка между `PositionStreamManager.start(newAccountId)`
   и первыми событиями от gRPC / polling для нового счёта.

**Файлы для отладки:**
- `domain/api/PositionStreamManager.kt`
- `presentation/screens/portfolio/PortfolioViewModel.kt`
- `presentation/screens/orders/OrdersViewModel.kt`

**Когда вернуться:**
- При воспроизведении на реальном устройстве.
- При жалобах пользователей на «залипшие» позиции.
- Если не повторится через несколько релизов — закрыть
  с пометкой «не воспроизводится».

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

## T-Invest WebSocket и REST-стримы недоступны (серверная деградация)

**Статус:** открыт, не наш код. Блокирует фазу 2 `stream-orchestrator`
(WS-fallback). gRPC и polling работают штатно.

**Симптом (сводный):**

- **WebSocket handshake** на `wss://invest-public-api.tbank.ru/ws`
  на Android и на десктопе через websocat **зависает без ответа**
  (TCP устанавливается, TLS проходит, HTTP upgrade не отвечает
  101 и не отбивается кодом).
- **REST-прокси** `POST /rest/tinkoff.public.invest.api.contract.v1.OperationsStreamService/PositionsStream`
  возвращает **HTTP 404 от Envoy** (и на HTTP/2, и на HTTP/1.1,
  с `Authorization` и без).
- **gRPC** `OperationsStreamService/positionsStream` работает
  штатно — приходят `SUBSCRIPTIONS`, `INITIAL_POSITIONS`, `PING`.

**Полная матрица экспериментов (2026-10-18):**

На Android (OkHttp):
- URL `/ws/` + `Authorization` + `Web-Socket-Protocol: json` → **404** (`x-k8s-istio-drop-ok: true`).
- URL `/ws` (без слэша) + те же заголовки → **404**.
- `Web-Socket-Protocol: json, <token>` без `Authorization` → **404**.
- `Sec-WebSocket-Protocol: json` (вместо `Web-Socket-Protocol`) → **404**.
- Добавление `Origin: https://invest-public-api.tbank.ru` → **404**.
- Добавление `User-Agent: websocat/1.14.1` → **404 исчез, пришёл timeout 10s**.
  Это был первый прогресс: Envoy пропустил запрос, но handshake не завершился.
- `protocols(HTTP_1_1)` в OkHttpClient → timeout.
- `minWebSocketMessageToCompress(Long.MAX_VALUE)` → timeout.
  `Sec-WebSocket-Extensions: permessage-deflate` в handshake **остался**
  (OkHttp не позволяет отключить публично, параметр влияет только
  на исходящее сжатие сообщений).

На Android (Ktor CIO):
- `HttpClient(CIO)` + `CustomTrustManager` через `engine { https { trustManager = ... } }`,
  заголовки `Authorization` + `Web-Socket-Protocol: json` → **silent hang**
  (20+ секунд без `connected`, без `failure` — handshake не завершился).

На десктопе (websocat 1.14.1, `-k` для игнора сертификата):
- В начале сессии (до всех экспериментов): URL `/ws` + `Web-Socket-Protocol: json`
  → **101 Switching Protocols** ✅ (эталон, что сервер вообще жив).
- Позже в той же сессии: URL `/ws` + `Web-Socket-Protocol: json` → **silent hang** ❌.
- Тот же URL + `Authorization: Bearer <token>` → **silent hang**.
- `Web-Socket-Protocol: json, <token>` → **silent hang**.

На десктопе (curl, REST-прокси):
- `POST /rest/.../PositionsStream` HTTP/2 с `Authorization` → **404** (Envoy).
- Тот же запрос HTTP/1.1 (`--http1.1`) → **404** (Envoy, chunked).

**Вывод:**

- **Проблема не в нашем коде.** Websocat (эталонный клиент) с теми же
  URL и заголовками получал 101 в начале сессии и зависает сейчас.
  Значит, стриминговые эндпоинты T-Invest (WS и REST) **деградировали
  на стороне сервера** в течение сессии. Это может быть технические
  работы, изменения в ingress-конфигурации, DDoS или регресс в API.
- **Оба наших клиента (OkHttp и CIO) ведут себя консистентно** —
  повторяют состояние сервера. Код корректен по документации T-Invest
  (URL, заголовки, формат подписки).
- **gRPC-канал на том же хосте работает.** Это подтверждает, что
  TCP/TLS до `invest-public-api.tbank.ru` проходят, проблема именно
  в стриминговых HTTP-эндпоинтах.

**Решение:**

1. **Не тратить время на перебор клиентов, пока сервер не восстановится.**
   Все разумные комбинации (OkHttp / CIO / curl / websocat, URL
   с/без слэша, все заголовки, HTTP/1.1 и HTTP/2) перепробованы.
2. **Ждать восстановления сервиса T-Invest.** Проверять периодически
   через `websocat -k wss://invest-public-api.tbank.ru/ws -H "Web-Socket-Protocol: json"` —
   появление 101 означает, что можно возобновить работу.
3. **gRPC + polling остаются основными источниками данных.**
   `stream-orchestrator` закрывается без WS-fallback; WS добавляется
   позже как опциональное улучшение.

**Когда вернуться:**

- Когда websocat снова начнёт получать 101 (проверять раз в несколько дней).
- При появлении информации о восстановлении стримов в T-Invest
  (новости, статус-страница API, ответ поддержки).
- При жалобах от пользователей на поведение стримов.

**Точка отката:** ветка `feature/ws-okhttp-fallback`, коммит `934646d`
(миграция на Ktor CIO). Содержит все эксперименты — OkHttp-версию,
CIO-версию, заголовки, `User-Agent`, `minWebSocketMessageToCompress`.

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
