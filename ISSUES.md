# ISSUES

---

## Шаблон для новых записей

```markdown
## <Короткое название>

**Статус:** открыт / отложен / решён.
**Симптом:** ...
**Причина:** ...
**Решение:** ...
**Когда вернуться:** ...

Известные проблемы и отложенные решения проекта TScalp.
```

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
сервер не возвращает `postOrder response` (запрос отклоняется до
обработки).

**Дальнейшие шаги:**
- Обратиться в поддержку Т-Инвестиций с дампом `PostOrderRequest`
  и `tracking_id` фьючерсного ответа для сравнения.
- Проверить через `grpcurl` с тем же токеном — подтвердить
  проблему вне нашего приложения.
- Обновить `kotlin-sdk-grpc-core` до актуальной версии.
- После решения — вернуть проверки `buyAvailable`/`sellAvailable`
  в `checkTradeAvailability`.

---

## PositionsStream не доставляет данные

**Статус:** отложен. Низкий приоритет (P&L можно смотреть в
нативном приложении брокера).

**Симптом:** P&L не обновляется в реальном времени через стрим.
- Песочница: `UNAUTHENTICATED: 40003`.
- Боевой: ошибок нет, но `hasPosition()` не приходит.

**Причина:** предположительно `OperationsStreamServiceGrpc`
отсутствует в `kotlin-sdk-grpc-core:1.48.1`, либо метод требует
отдельных прав.

**Текущее решение:** polling через `SharedPositionStreamManager`
с периодом 10 секунд. P&L корректно отображается с задержкой.

**Детали реализации и попыток:** `docs/streams.md`.

**Когда вернуться:** после обновления SDK до версии с
`PositionsStreamServiceGrpc` — перейти на официальный стрим.

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

**Когда вернуться:** после завершения миграции на `AppResult`
(закрыто). Большинство gRPC-вызовов обёрнуто в
`runCatchingAppResult`, число «висячих» колбэков снизилось.
Оценить необходимость guard для оставшихся.

**Связанные логи:**
```19:55:30.743 AppMetrica: Unhandled exception received:
at io.grpc.Status.asException(Status.java:547)
at io.grpc.kotlin.ClientCalls$rpcImpl$1$1$1.onClose(ClientCalls.kt:264)
at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:133)
at java.util.concurrent.ThreadPoolExecutor.runWorker
```

---

## Позиции портфеля не очищаются при выходе из аккаунта

**Статус:** открыт. Низкий приоритет.

**Симптом:** после «Отключить» в Настройках → Подключение → TInvest
позиции в Портфеле продолжают отображаться. Цены по ним обновляются
(polling раз в 5 секунд), P&L пересчитывается. При этом API уже
не инициализирован — запросы уходят с невалидным токеном и падают
в лог.

**Причина:** при `clearBrokerCredentials(TINVEST)`:
1. `SettingsRepository.clearBrokerCredentials` стирает токен из DataStore.
2. `TradingStateRepository.clear()` стирает снимок торгового состояния.
3. `OrdersViewModel.checkApiInitialization()` обновляет флаг
   `isApiInitialized = false`.

Но `PortfolioViewModel._uiState.positions` и `OrdersViewModel._uiState.portfolioPositions`
**не сбрасываются**. Также не останавливаются активные корутины:
- `PortfolioViewModel.priceUpdateJob` — цикл обновления цен.
- `PortfolioViewModel` подписка на `SharedPositionStreamManager.flow`.
- `OrdersViewModel.positionStreamJob` — то же.
- `SharedPositionStreamManager.job` — общий стрим позиций продолжает
  polling.

**Последствия:**
- Пользователь видит устаревшие данные с неверными ценами.
- Лишняя нагрузка на сеть (запросы, которые всегда падают).
- В логах — ошибки с невалидным токеном, шум.

**Возможное решение:**
1. `PortfolioViewModel` — при получении `anyInitialized == false`
   очищать `positions`, `tradingStatuses`, `totalValue`, останавливать
   `priceUpdateJob`.
2. `OrdersViewModel` — аналогично очищать `portfolioPositions`, `lastSelectedInstruments`,
   останавливать `positionStreamJob` и `priceStreamJob`.
3. `SharedPositionStreamManager` — при получении пустого/невалидного
   accountId останавливать polling. Возможно, нужен метод `reset()`,
   который вызывается при logout.

**Когда вернуться:** после DataStore/переименования. Не блокирует.

**Связанные места:**
- `PortfolioViewModel.checkApiInitialization`
- `OrdersViewModel.checkApiInitialization`
- `SharedPositionStreamManager.start` / `stop`
- `SettingsViewModel.clearBrokerCredentials`

---
## Накопление collect'ов на SharedPositionStreamManager.flow

**Статус:** открыт. Низкий приоритет.

**Симптом:** при каждом `loadPortfolio` (вызывается из
checkApiInitialization, refresh из ON_RESUME, payInSandbox)
создаётся новый коллектор `positionStreamManager.flow.collect { ... }`.
Старые не отменяются. При длительной работе с частыми
переключениями экрана количество коллекторов растёт.

**Причина:** `loadPortfolio` — launch без сохранения Job'а и без
отмены предыдущего; каждый вызов `positionStreamManager.flow.collect`
запускает новую долгоживущую корутину.

**Возможное решение:** хранить `portfolioStreamJob: Job?` как поле
ViewModel и отменять его перед каждым `loadPortfolio`. Либо
вынести collector из `loadPortfolio` в `startPositionUpdates`,
симметрично OrdersViewModel.

**Когда вернуться:** при следующем рефакторинге PortfolioViewModel.



---
## SharedPositionStreamManager игнорирует смену accountId

**Статус:** открыт. Низкий приоритет.

**Симптом:** `SharedPositionStreamManager.start(accountId)` возвращается
сразу, если job уже активен (`if (job?.isActive == true) return`).
При переключении на другой счёт в настройках поток продолжает polling
старый accountId.

**Причина:** метод идемпотентен, но не различает «тот же счёт» и
«другой счёт».

**Возможное решение:** запоминать текущий accountId в поле и
перезапускать job при его смене.

**Когда вернуться:** при работе над единым источником цен для
всех вкладок (см. ROADMAP).



---
#################################################
