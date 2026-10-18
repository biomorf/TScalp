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

## T-Invest WebSocket: 404 от Envoy на Android

**Статус:** отложен. Блокируется фиксом `restoreTInvest`
(см. следующую запись).

**Симптом:** OkHttp на Android получает `HTTP 404 Not Found` от
Envoy на upgrade-запрос к `wss://invest-public-api.tbank.ru/ws`.
В заголовках ответа — `x-k8s-istio-drop-ok: true`,
`x-k8s-istio-sage-group: invest-piapi`, `server: envoy`,
`content-length: 0`. TCP и TLS проходят, отказ на уровне HTTP.

Тот же запрос с десктопа через `websocat` (URL
`wss://invest-public-api.tbank.ru/ws`, **без** слэша, заголовок
`Web-Socket-Protocol: json`) — **101 Switching Protocols**.
Подписка без токена в WS-заголовке молчит (сервер ждёт токен
в `Authorization` или `Web-Socket-Protocol: json, <token>`).

**Перебрано:**
- URL: `/ws/` → 404; `/ws` без слэша → 404 (на Android, websocat
  с `/ws` — 101).
- Auth: `Authorization: Bearer <token>` → 404;
  `Web-Socket-Protocol: json, <token>` → 404 (без Authorization).
- Имя заголовка: `Web-Socket-Protocol` vs
  `Sec-WebSocket-Protocol` — обе дают 404 на Android.
- Формат: `json` (пробовали), `json-proto` (не пробовали).
- `Origin: https://invest-public-api.tbank.ru` — 404.
- `User-Agent: websocat/1.14.1` — 404.
- `protocols(HTTP_1_1)` в OkHttpClient — 404.
- Клиенты: Ktor `client.webSocket` → 404; OkHttp
  `newWebSocket` → 404. Оба на Android.

**Гипотеза:** OkHttp шлёт дефолтные заголовки
(`User-Agent: okhttp/5.5.0`, `Accept-Encoding: gzip`), которые
Envoy режет на WS-маршруте. Гипотеза не подтверждена: в
logcat `HttpLoggingInterceptor` (тег `OkHttpWS`) до сих пор не
появлялся — достоверного полного handshake от OkHttp нет.

**Решение (отложено):**
1. Убедиться, что `HttpLoggingInterceptor` реально в сборке
   (в `DataModule.provideOkHttpClient` есть
   `.addInterceptor(...)`; `logging-interceptor:5.5.0` в
   `app/build.gradle.kts`).
2. Прогнать live-тест с тегом `OkHttpWS` и получить полный
   `--> GET ...` + список заголовков.
3. Сравнить посимвольно с `websocat -v -k ...` на десктопе —
   найти лишнее или отсутствующее.
4. Если причина в `User-Agent` / `Accept-Encoding` — убрать
   через interceptor или явный `.header(...)`.
5. Если не поможет — попробовать `json-proto` и
   `Sec-WebSocket-Protocol: json, <token>` (оставшиеся
   комбинации).

**Когда вернуться:** после фикса `restoreTInvest` (следующая
запись) — без него WS-клиент не запускается на устройстве.
Ветка `feature/ws-okhttp-fallback` содержит все эксперименты;
коммит `20cabfc` — точка отката.

---
