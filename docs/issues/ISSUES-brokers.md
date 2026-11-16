<!-- SECTION_BEGIN: issues-brokers -->
## Brokers










<!-- SUBSECTION_BEGIN: i-6 -->
___
### [OPEN] I-6 INVALID_ARGUMENT: 30052 при торговле акциями
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
<!-- SUBSECTION_END: i-6 -->










<!-- SUBSECTION_BEGIN: i-8 -->
___
### [WIP] I-8 GrpcExceptionGuard не установлен
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
<!-- SUBSECTION_END: i-8 -->










<!-- SUBSECTION_BEGIN: i-12 -->
___
### [OPEN] I-12 gRPC retry: string matching NOT_FOUND вместо Status.Code
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
<!-- SUBSECTION_END: i-12 -->










<!-- SUBSECTION_BEGIN: i-13 -->
___
### [OPEN] I-13 gRPC retry: бесконечный retry на не-recoverable ошибках
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
<!-- SUBSECTION_END: i-13 -->










<!-- SUBSECTION_BEGIN: i-15 -->
___
### [DONE] I-15 BcsBrokerApi логирует refreshToken (решён)
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
<!-- SUBSECTION_END: i-15 -->










<!-- SUBSECTION_BEGIN: i-16 -->
___
### [OPEN] I-16 BcsBrokerApi создаёт OkHttpClient без CustomTrustManager
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
<!-- SUBSECTION_END: i-16 -->










<!-- SUBSECTION_BEGIN: i-20 -->
___
### [OPEN] I-20 INTERNAL: 70001 при параллельных unary-запросах
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
<!-- SUBSECTION_END: i-20 -->










<!-- SUBSECTION_BEGIN: i-24 -->
___
### [DONE] I-24 T-Invest REST findInstruments fallback (N+1 unary)
**Статус:** решён (2026-11-17, T-6, PR pending).

**Симптом:** REST fallback `findInstruments` (variant B, PR #85) делает N+1 unary-запросов: 1 `InstrumentsService/FindInstrument` + N `InstrumentsService/GetInstrumentBy` (по одному на каждый uid из короткого списка).

**C2-находка (2026-11-15):** `SearchInstrumentsUseCase:49` добавил `updateStatuses(ids)` → +N `getTradingStatuses` → **2N+1 unary** на поиск. Sandbox 6/сек → `RESOURCE_EXHAUSTED` (live-log C2). Rate-limiter (умбрелла) закроет; сокращение — `### Search: сократить N+1 unary в findInstruments`.

**Причина:** REST `FindInstrument` возвращает `InstrumentShort` без `currency`/`lot`, обязательных для `InstrumentUi` (8 обязательных полей в `domain/models/Instruments.kt`). Полный `Instrument` — только через `GetInstrumentBy`. Fallback повторяет gRPC-путь (там тоже N+1).

**Решение:** T-6 (2026-11-17, коммит `a8d9948e`): search возвращает partial `SearchItem` (1 `findInstrument`, без `getInstrumentBy`); REST fallback — тоже 1 unary. Full `Instrument` — lazy на select через `SelectInstrumentUseCase` / `InstrumentRepository` (кэш или 1 `getInstrumentBy`). Rate-limiter (T-3/1a) остаётся защитой. См. `docs/roadmap/ROADMAP-ARCHIVE.md` → `### ✅ T-6 ...`.

**Последствия:** при поиске на 100 инструментов — 101 unary-запрос подряд. Fallback редкий (только когда gRPC `UNAVAILABLE` / `DEADLINE_EXCEEDED` / `UNIMPLEMENTED`), но при совпадении с rate-limit T-Invest (6 unary/сек) — `RESOURCE_EXHAUSTED` и деградация поиска.

**Решение:** rate-limiter для unary-запросов (`docs/ROADMAP.md` → `### Rate-limiter для unary-запросов T-Invest`) — обернуть fallback в него, либо батчинг `GetInstrumentBy` (если API даст batch-метод), либо ограничить concurrency `Semaphore`.

**Файлы:**
- `data/api/tinvest/TInvestRestService.kt` — `findInstrumentsRestFallback` (N+1).
- `data/api/tinvest/TInvestOrchestrator.kt` — `findInstrumentsWithFallback` (вызывает fallback).

**Связано:** `docs/ROADMAP.md` → `### Rate-limiter для unary-запросов T-Invest`; ROADMAP → `### ✅ T-Invest REST: findInstruments fallback` (PR #85).

**Когда вернуться:** при появлении rate-limiter (закроется автоматически), или при жалобах на `RESOURCE_EXHAUSTED` при поиске.
<!-- SUBSECTION_END: i-24 -->










<!-- SUBSECTION_BEGIN: i-32 -->
___
### [OPEN] I-32 T-Invest rate-limit umbrella: cold start + search RESOURCE_EXHAUSTED
**Статус:** открыт.

**Симптом:** на cold start и search — `RESOURCE_EXHAUSTED` от T-Invest API. Live-log C2 (2026-11-15, emulator, sandbox):
- **Search burst:** 6 × `TInvestInstruments: RESOURCE_EXHAUSTED` за 300ms (17:50:40.024–.314) на pair-search.
- **Cold start:** `LoadPortfolioUseCase` / `LoadOrdersAccountsUseCase` / `RestoreOrderFormUseCase` — `UNAVAILABLE: Unable to resolve host` (DNS эмулятора) + `RESOURCE_EXHAUSTED` после.

**Причина:**
- `findInstruments` = N+1 unary (1 + N `getInstrumentBy`); C2 добавил `updateStatuses(ids)` = +N `getTradingStatuses` → **2N+1 unary** на поиск.
- Cold start: 5s×N accounts polling + 10s position polling + 5 min periodic statuses + `restore` (broker init + instrument ×2 + load) → суммарная нагрузка.
- Sandbox 6/сек (global) — burst 2N+1 > 6 → `RESOURCE_EXHAUSTED`.

**Решение:** umbrella `### Rate-limit umbrella: cold start + search RESOURCE_EXHAUSTED` (ROADMAP) + подзадачи (1) Rate-limiter, (2) Search N+1, (3) updateStatuses skip, (4) getTradingStatuses batching.

**Файлы:**
- `domain/usecases/SearchInstrumentsUseCase.kt`
- `data/api/tinvest/TInvestInstrumentsService.kt`
- `data/api/tinvest/TInvestTradingService.kt`
- `data/api/tinvest/TInvestRateLimiter.kt` (новый)
- `docs/rate-limits.md`

**Связано:** `docs/ROADMAP.md` → umbrella; `docs/ISSUES.md` → `## PortfolioScreen: infinite retry loadPortfolio`, `## T-Invest REST findInstruments fallback (N+1 unary)`; `docs/clean-audit.md` → Н66; live-log C2 (2026-11-15).

**Когда вернуться:** после закрытия umbrella (live-test C2 2–7 PASS).

---
<!-- SUBSECTION_END: i-32 -->









<!-- SECTION_END: issues-brokers -->
