# ROADMAP

План задач проекта TScalp: что в работе, что запланировано, что
отложено. Формат — секция на задачу, со статусом и оценкой.

---

## Расшифровка кодов ошибок брокеров

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

## Реализация Finam Broker API

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
3. Реализовать `fetchPositionsRest(accountId, sandboxMode)`.
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

## Рефакторинг архитектурного долга (detekt-группа 3)

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

### BrokerApi-реализации

**Файлы:** `data/api/BcsBrokerApi.kt` (25 функций),
`data/api/FinamBrokerApi.kt` (24), `data/api/TInvestBrokerApi.kt`
(22), `domain/api/BrokerApi.kt` (интерфейс, 19).

**Пути решения:**
- Разделить `BrokerApi` на ролевые интерфейсы (`AccountsApi`,
  `OrdersApi`, `MarketDataApi`, `StreamsApi`) с агрегацией.
- Разбить реализации на сервисы по образцу
  `TInvestMarketDataService` / `TInvestOrdersService`.

**Оценка:** ~3 часа.

### Репозитории

**Файлы:** `data/repository/InvestRepository.kt` (17 функций),
`data/repository/SettingsRepository.kt` (15).

**Пути решения:** разбить по доменным областям (аккаунты,
заявки, портфель, стримы — для `InvestRepository`; токены,
настройки UI, режим sandbox — для `SettingsRepository`).

**Оценка:** ~2 часа.

### ViewModel-слой

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

### UseCase-слой

**Файлы:**
- `domain/usecases/CalculateTradeDetailsUseCase.kt` —
  `calculate` (complexity 25).
- `domain/usecases/PrepareOrderRequestUseCase.kt` —
  `prepare` (92 строки, complexity 20).

**Пути решения:** разбить `calculate` на отдельные функции
по типу заявки (market/limit/stop). `prepare` — вынести
валидацию, сборку request, разрешение uid в отдельные шаги.

**Оценка:** ~2 часа.

### Data-слой T-Invest

**Файлы:**
- `data/api/TInvestMarketDataService.kt` — 19 функций;
  `fetchPositionsRest` (complexity 26).
- `data/api/TInvestInstrumentMapper.kt` — `mapProtoToDomain`
  (118 строк).

**Пути решения:** `fetchPositionsRest` разбить на
`fetchPositions` + `enrichWithInstrumentData` +
`enrichWithMarginData`. `mapProtoToDomain` — на
`mapFutures` / `mapShares` / `mapDefault`.

**Оценка:** ~2 часа.

### Compose-экраны

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

**Оценка:** ~6 часов.

### Compose-компоненты

**Файлы:**
- `ui/components/AssetPositionCard.kt` — `AssetPositionCard`
  (109 строк), `AssetCardContent` (136 строк, complexity 20).
- `ui/components/BrokerAccountDialog.kt` (105 строк).
- `ui/components/OrdersListViewModel.kt` — `loadOrders`
  (62 строки).

**Оценка:** ~2 часа.

### Классификатор ошибок

**Файлы:**
- `util/AppResultExtensions.kt` — `Throwable.toAppError`
  (66 строк, complexity 26).

**Пути решения:** разбить на per-broker обработчики
(`toTInvestError`, `toBcsError`, `toFinamError`) с
диспетчеризацией по `BrokerName`.

**Оценка:** ~1 час.

### Индикатор доступности инструмента в карточке позиции

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

## Переход на edge-to-edge (statusBarColor deprecated)

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

## CI/CD улучшения

**Статус:** частично сделано, остальное в плане.

**Уже работает (инфраструктура):**
- `.gitlab-ci.yml`: стадии `test → build → release`.
- Три job'а в стадии `test`: `unit-tests`, `lint`, `assemble-debug`.
- `build-release` на тегах `vX.Y.Z`: проверка
  `git merge-base`, подпись из `KEYSTORE_BASE64` +
  `keystore.properties`, сборка
  `assembleRelease -PreleaseVersionName=X.Y.Z`.
- `publish-release`: GitLab Release с APK в assets и release
  notes из git log (только `feat`/`fix`).
- Кэш Gradle: стабильный ключ `gradle-cache-v1`, `.gradle/jdks`
  в paths.
- master защищён; MR через fast-forward merge
  (`Settings → Merge requests → Merge method`).
- Protected Tags `v*`.

**Сделано:**
- Lint job в стадии `test`. Baseline `app/lint-baseline.xml`,
  `warningsAsErrors = true`, `disable` для динамических
  детекторов (`AndroidGradlePluginVersion`, `GradleDependency`,
  `NewerVersionAvailable`) — несовместимы с baseline, так как
  сообщение зависит от последней версии в Maven Central.
  HTML/XML-отчёты в артефактах CI.
- Smoke-сборка `assembleDebug` в стадии `test`, параллельно
  `unit-tests` и `lint`. Ловит ошибки, которые не покрывают
  тесты и lint: битые ресурсы, конфликты в `packaging {}`,
  `AndroidManifest.xml`, а также `renameDebugApk`.
- Release notes из git log между предыдущим и текущим тегом.
  Фильтр по типу коммита: только `feat` и `fix`. Fallback
  «No user-facing changes».
- Build cache: стабильный ключ вместо хэша конфигурационных
  файлов.
- `versionName` в имени release APK — через задачу
  `renameReleaseApk` в `build.gradle.kts`.

**Запланировано** (в порядке приоритета):




### Self-hosted GitLab Runner через Ansible + Podman

**Статус:** запланировано.

**Что:** развернуть GitLab Runner на домашней машине (i9 13980HX,
64 ГБ RAM, Bluefin 44) через Ansible, используя Podman как
executor. Предусмотреть возможность переключения на удалённый
сервер через inventory.

**Зачем:** квота GitLab Free Tier (400 минут/мес) исчерпана.
Self-hosted даёт бесплатные сборки, полный контроль над
окружением и лучшую производительность.

**Технические детали:**
- Podman как drop-in замена Docker в GitLab Runner 15.1+.
  В `config.toml`: `executor = "docker"`,
  `host = "unix:///run/user/<uid>/podman/podman.sock"`.
- Rootless Podman: без root, без демона, безопаснее.
- Ansible роль: создание пользователя `gitlab-runner`,
  `loginctl enable-linger`, `systemctl --user enable podman.socket`,
  регистрация раннера, настройка `config.toml`.
- Ограничения ресурсов: `--memory=16g`, `--cpus=8` (с запасом,
  начать с 8g/4).
- Кэш Gradle монтируется с хоста для ускорения сборок.

**Бесшовное переключение:**
- Inventory с двумя хостами: `local` и `remote`.
- Раннеры регистрируются с разными тегами (`home`, `remote`).
- Переключение — смена тега в `.gitlab-ci.yml` или через
  переменную CI/CD.

**План:**
1. Написать Ansible роль для GitLab Runner + Podman.
2. Протестировать на домашней машине (`ansible-playbook -i inventory.yml playbook.yml`).
3. Зарегистрировать раннер в проекте.
4. Перевести job'ы `unit-tests`, `lint`, `assemble-debug` на self-hosted.
5. `build-release` и `publish-release` оставить на GitLab SaaS
   или перенести после проверки.

**Оценка:** ~2–3 часа на написание роли и отладку.
**Когда:** после решения вопроса с Gradle upgrade.




---

### Переход на GitHub Environment secrets

**Статус:** запланировано. Отложено до появления триггера.

**Что:** перенести 4 секрета release-сборки
(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`) из **Repository secrets** в
**Environment secrets**.

**Чем отличаются Environment secrets:**

- **Required reviewers** — job не стартует, пока человек не
  одобрит. Для одиночного проекта бесполезно: GitHub, как и
  GitLab, не даёт approve свой же workflow. Работает только
  при наличии второго Maintainer'а.
- **Wait timer** — принудительная задержка перед запуском.
  Защита от случайного нажатия.
- **Deployment branches and tags** — секрет доступен только
  для указанных веток или тегов (например, `v*`).
  Repository secrets, наоборот, доступны любому workflow в
  любой момент.
- **Разделение окружений** — если появится staging/production,
  разные значения для каждого.

**Что Repository secrets НЕ умеют:** ограничение по веткам,
approval, per-environment значения. Всё остальное совпадает —
оба варианта хранят значения в зашифрованном виде, оба
доступны в `${{ secrets.NAME }}` без изменений в синтаксисе.

**Как перейти (когда наступит триггер):**

1. `Settings → Environments` → создать environment
   `release`.
2. Добавить 4 секрета туда.
3. Удалить их из Repository secrets.
4. В `release.yml` добавить `environment: release` в job
   `build-and-release`.

**Когда вернуться:**
- при появлении второго Maintainer'а;
- при первом релизе, требующем approval-гейта;
- при разделении staging/production окружений.

**Оценка:** ~15 минут.




---

## PositionsStreamServiceGrpc — вернуться к стриму позиций

**Статус:** запланировано. Связано с ISSUES.md → «PositionsStream
не доставляет данные».

**Контекст:** сейчас P&L через polling `PositionStreamManager`
раз в 10 секунд. Официальный стрим не работал на
`kotlin-sdk-grpc-core:1.48.1`; были подозрения, что
`PositionsStreamServiceGrpc` отсутствует в SDK или требует
отдельных прав.

**Что изменилось:** SDK обновлён 1.48.1 → 1.51.0. Есть шанс,
что нужный сервис появился.

**Шаги при возврате:**
1. Проверить наличие `PositionsStreamServiceGrpc` в артефакте
   `kotlin-sdk-grpc-core:1.51.0` (распаковать jar/AAR,
   `grep -r PositionsStreamServiceGrpc`).
2. Если есть — написать минимальный тест-подписки вне
   приложения (отдельный `main`, не трогая `PositionStreamManager`).
3. Песочница: проверить `UNAUTHENTICATED: 40003` — воспроизводится
   ли на 1.51.0.
4. Боевой: проверить, приходит ли `hasPosition()`.
5. Если работает — перевести `PositionStreamManager` на стрим,
   polling оставить как fallback на случай разрыва.
6. Если не работает — обновить ISSUES.md с новыми деталями и
   оставить polling.

**Оценка:** ~2 часа на проверку + ~2 часа на миграцию, если
сервис рабочий.

**Когда вернуться:** сразу после CI-задач 1–3 (быстрые победы),
до задач 4, 5, 6.




## Каскад обновления позиций

**Статус:** запланировано.

**Контекст:** сейчас `TInvestMarketDataService.subscribePositions`
реализует polling через `fetchPositionsRest` раз в 10 секунд.
gRPC-стрим не подключён. Официальная документация T-Invest
рекомендует переподключаться при отсутствии данных.
`PositionsStream` живёт в `OperationsStreamService`
(`PositionsStreamServiceGrpc` не существует).

**Предусловия:**
- SDK `kotlin-sdk-grpc-core:1.51.0` (уже в проекте).
- Проверить в артефакте наличие `OperationsStreamServiceCoroutineStub`
  и классов `PositionsStreamRequest` / `PositionsStreamResponse`.

**Файлы:**
- `data/api/TInvestMarketDataService.kt` — основной файл.
- `data/api/TInvestChannelFactory.kt` — возможно, добавить
  канал `operationsStream` в `Channels`.
- `domain/models/Portfolio.kt` — модель `PositionStreamItem`
  (уже есть, не менять без причины).

**Шаги:**

1. Рефакторинг: переименовать текущий `subscribePositions`
   → `subscribePositionsPolling` (private). Сборка.
2. Добавить заглушки `subscribePositionsGrpc(accountId): Flow<PositionStreamItem>?`
   и `subscribePositionsWebSocket(accountId): Flow<PositionStreamItem>?`,
   обе возвращают `null`. Сборка.
3. Реализовать `subscribePositions(accountId)` как циклический
   каскад в `channelFlow`:
   - gRPC-уровень → при ошибке/завершении → WS-уровень;
   - WS-уровень → при ошибке/завершении → polling один раз;
   - polling → `delay(RETRY_DELAY_MS = 10_000)` → повтор цикла.
   - `LevelOutcome { Ended, Cancelled, Fatal }` для управления.
   - Fatal (`NOT_FOUND` по счёту) останавливает каскад.
   Сборка.
4. Реализовать gRPC-уровень через
   `OperationsStreamServiceCoroutineStub(channel).positionsStream(flowOf(request))`.
   - `request = PositionsStreamRequest.newBuilder()
     .addAccounts(accountId)
     .setWithInitialPositions(true)
     .build()`.
   - Конвертеры `V1PositionsSecurities` → `PositionStreamItem`
     и `V1PositionsFutures` → `PositionStreamItem`.
   - Поле `balance` — **String**, конвертировать через `toLongOrNull()`.
   Сборка.
5. Реализовать WebSocket-уровень (после получения схемы сообщений).
   - OkHttp 5.4.0 (уже в зависимостях).
   - Рассмотреть использование Ktor для мультиплатформенности
   - URL: `wss://invest-public-api.tbank.ru/ws/`.
   - Заголовки: `Authorization: Bearer <token>`, `Web-Socket-Protocol: json`.
   Сборка.
6. Реализовать `subscribePositionsPolling` как одиночный
   `fetchPositionsRest` (без внутреннего `while` и `delay`).
   Сборка.
7. Debug-логи с префиксом `[positions]` на каждом переходе
   (уровни D/I/W/E — см. «Референсы»).

**Референсы:**
- `RussianInvestments/invest-api-kotlin-sdk` →
  `kotlin-sdk-grpc-example/src/main/kotlin/ru/ttech/piapi/example/`
  → `OperationStreamServiceExample.kt`, метод `examplePositionsStream`.
- `operations.proto` в пакете `ru.tinkoff.piapi.contract.v1` —
  определения `PositionsStreamRequest` / `PositionsStreamResponse`.
- `docs/streams.md` → раздел «PositionsStream» — история попыток.

**Ограничения:**
- **Не использовать** `PositionsStreamServiceGrpc` — класса не существует.
- **Не переподключаться чаще, чем раз в 3 секунды** —
  документация T-Invest предупреждает: «не пытайтесь
  переподключаться слишком часто — это может усугубить ситуацию».
- **Не использовать `retryWhen`** — цикл сам управляет
  переподключениями.
- **Не унифицировать** с каскадом цен — семантика разная
  (sequential vs parallel sources).
- Polling не должен «подниматься» обратно на gRPC внутри
  одной итерации — только через cooldown и полный повтор.

**Критерий готовности:**
- При обрыве gRPC-стрима каскад переходит на WS без паузы.
- При обрыве WS — один polling-fetch, затем `delay(10s)`, затем
  повтор с gRPC.
- `NOT_FOUND` по счёту останавливает каскад.
- В `logcat` видны `[positions]`-сообщения на каждом переходе.
- Unit-тесты (при наличии) зелёные, сборка зелёная.

**Оценка:** ~4 часа (рефакторинг 30 мин, gRPC 2 ч, WS 1.5 ч).

**Зависимости:** нет. Блокирует «StreamState в data-слое»
и «UI-индикатор состояния стримов».

**Открытые вопросы:**
- Точная структура JSON-конверта для подписки через WebSocket.
  Источник: официальная AsyncAPI-спецификация T-Invest API →
  `RussianInvestments/investAPI` → `src/docs/ws/asyncapi.yaml`.
  Канал: `/tinkoff.public.invest.api.contract.v1.OperationsStreamService/PositionsStream`.
  Схема сообщений соответствует proto-контракту
  (`V1PositionsStreamRequest` / `V1PositionsStreamResponse`).
- Точный состав полей `V1PositionsSecurities` / `V1PositionsFutures`.
  Где искать: распаковать AAR 1.51.0.

**Референс для WebSocket-уровня:**
- AsyncAPI-спецификация: `src/docs/ws/asyncapi.yaml` в репозитории
  `RussianInvestments/investAPI`.
- URL: `wss://invest-public-api.tbank.ru/ws/`
- Заголовки: `Authorization: Bearer <token>`,
  `Web-Socket-Protocol: json` (или `json-proto`).
- Формат: protobuf JSON mapping.




## StreamState в data-слое

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

**Зависимости:** блокируется каскадом позиций
и reconnect-loop для цен.




## UI-индикатор состояния стримов

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




## Унификация каскадов стримов

**Статус:** отложено.

**Контекст:** два каскада — цены (параллельные источники)
и позиции (последовательный каскад). Возник вопрос: выделить
общий движок.

**Предусловия:** каскад позиций и reconnect-loop для цен
должны быть реализованы и обкатаны.

**Решение:** **не унифицировать.** Семантики принципиально
разные. Преждевременная абстракция рискует усложнить оба каскада.

**Что можно выделить без унификации:**
- reconnect-loop с backoff (см. отдельную задачу для цен);
- единый формат `[stream]`-логов;
- health-check как отдельный wrapper.

**Когда вернуться:** после того, как оба каскада заработают.
Тогда станет видно, есть ли реально общий код, достойный выделения.

**Оценка:** ~1 час на анализ, ~2 часа на рефакторинг,
если решение окажется положительным.




## Парсинг expireDate для stop-order'ов GOOD_TILL_DATE

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

## Миграция сетевого слоя на Ktor Client (KMP-подготовка)

**Статус:** запланировано.

**Контекст:** сейчас сетевой слой построен на OkHttp 5.4.0
(BCS) и gRPC-каналах (T-Invest). OkHttp официально прекратил
поддержку Kotlin Multiplatform — использовать его в `commonMain`
при переходе на KMP не получится. Ktor Client — основной
KMP-совместимый HTTP-клиент, работает на Android, iOS, JVM
из одного кода. На Android может использовать OkHttp как
движок под капотом, так что производительность не пострадает.

**Зачем планировать заранее:** переход на Ktor — это
инфраструктурная задача, не требующая срочности. Но если
закладывать её сейчас, при доработке reconnect-loop и каскада
позиций мы будем использовать Ktor-совместимые паттерны
(например, плагин `HttpRequestRetry`), а не OkHttp-специфичные.
Иначе через 3–6 месяцев придётся переделывать.

**Предусловия:** нет. Можно начинать в любой момент, когда
текущие задачи (reconnect-loop, каскад позиций) закрыты.

**Файлы:**
- `app/build.gradle.kts` — добавить зависимости Ktor.
- `gradle/libs.versions.toml` (если есть) — версии Ktor.
- `data/api/NetworkClient.kt` (новый) — фабрика `HttpClient`
  с плагинами.
- `data/api/BcsBrokerApi.kt` — перевести REST-запросы на Ktor.
- `data/api/TInvestMarketDataService.kt` — REST-часть (цены,
  портфель, счета, инструменты).
- `data/repository/InvestRepository.kt` — только если
  сигнатуры изменятся.
- `data/api/FinamBrokerApi.kt` — то же, при реализации.

**Шаги:**

1. Выбрать версию Ktor (актуальную на момент старта; на
   2026-10 — 3.x).
2. Добавить зависимости:
    - `io.ktor:ktor-client-core`
    - `io.ktor:ktor-client-okhttp` (Android engine)
    - `io.ktor:ktor-client-content-negotiation`
    - `io.ktor:ktor-serialization-kotlinx-json` или gson-adapter
    - `io.ktor:ktor-client-logging`
3. Создать `NetworkClient.kt` — фабрика `HttpClient` с:
    - движком OkHttp (для Android);
    - `ContentNegotiation` (JSON);
    - `HttpRequestRetry` (экспоненциальный backoff,
      переиспользует паттерн из reconnect-loop);
    - `Logging` через AppLogger (или кастомный логгер).
4. Пилот: перевести `subscribeLastPrices` на Ktor-клиент
   (или сразу gRPC-канал через Ktor-совместимый вариант).
5. Поэтапно перевести `BcsBrokerApi` REST-запросы.
6. Поэтапно перевести REST-часть `TInvestMarketDataService`.
7. Удалить OkHttp-зависимости, если не останется прямого
   использования (gRPC-каналы используют okhttp transport —
   уточнить, оставлять ли).
8. Убедиться, что 63 unit-теста зелёные на каждом шаге.

**Референсы:**
- Официальная документация Ktor Client:
  https://ktor.io/docs/client-create-new-application.html
- Плагин Retry:
  https://ktor.io/docs/client-retry.html
- Плагин ContentNegotiation:
  https://ktor.io/docs/client-serialization.html
- KMP-статус OkHttp (прекращение поддержки): issue в
  square/okhttp о KMP.

**Ограничения:**
- **Не смешивать** с другими задачами — миграция отдельная,
  может идти параллельно.
- **Не менять** публичные контракты `InvestRepository` /
  `BrokerApi` без миграции вызывающих.
- **Не удалять OkHttp полностью**, пока gRPC-каналы
  (`TInvestChannelFactory`) используют его transport.
  Разобраться на шаге 7.
- Тесты должны оставаться зелёными после каждого шага.

**Критерий готовности:**
- Все REST-запросы (BCS, T-Invest REST-часть) идут через
  Ktor Client.
- `NetworkClient.kt` — единая точка конфигурации HTTP-слоя.
- OkHttp остаётся только как transport для gRPC (если нужен).
- Сетевой слой готов к переносу в `commonMain` KMP-модуля
  без изменений бизнес-логики.
- 63 unit-теста зелёные.

**Оценка:** ~4–6 часов.

**Зависимости:** лучше после reconnect-loop для цен
и каскада позиций — чтобы не переписывать свежий код
дважды.

**Когда вернуться:** после закрытия reconnect-loop и
каскада позиций, до начала работы над KMP-миграцией UI.

**Открытые вопросы:**
- Оставлять ли OkHttp как transport для gRPC в KMP — зависит
  от того, поддерживает ли `grpc-kotlin` KMP-таргеты.
  Уточнить при старте задачи.
- Какой JSON-сериализатор использовать: `kotlinx.serialization`
  (KMP-native) или `gson-adapter` (текущий gson). Для KMP —
  рекомендуется `kotlinx.serialization`, но потребуется миграция
  data-классов.




## Reconnect-loop для gRPC-стрима цен

**Статус:** запланировано.

**Контекст:** `PriceStreamManager.runGrcpStream` при ошибке
логирует и завершается. Переподключения нет. Polling (раз в 5 с)
продолжает работать параллельно, но до следующего `setInterest`
со сменой union gRPC-стрим мёртв. При длинной сессии пользователь
видит задержку до 5 секунд на каждой цене.

**Предусловия:** нет.

**Файлы:**
- `domain/api/PriceStreamManager.kt` — метод `runGrcpStream`.

**Шаги:**

1. Обернуть `repository.subscribeLastPrices(...).collect { ... }`
   в цикл `while (currentCoroutineContext().isActive)` с `try/catch`.
2. При `CancellationException` — пробросить (штатное завершение).
3. При прочих исключениях — увеличить `attempt`, вычислить
   `delayMs` по backoff `[1s, 5s, 15s, 30s, 60s]`, cap 60 с,
   залогировать `W [prices] gRPC error (attempt=$n), retry in ${ms}ms: ${msg}`,
   затем `delay(delayMs)`.
4. При нормальном завершении `collect` — сбросить `attempt = 0`.
5. Polling (`runPolling`) **не трогать** — он работает параллельно
   как есть.

**Референсы:**
- `docs/streams.md` → раздел «Общие принципы» — про разделение
  каналов и поведение стримов.
- Официальная документация T-Invest → «Stream-соединения» →
  «Что делать при разрыве стрима» (экспоненциальная задержка
  1/10/60 с).

**Ограничения:**
- **Не унифицировать** с каскадом позиций — семантика разная.
- **Не подключать** WebSocket — он не нужен, polling дешёв
  и работает параллельно.
- **Не менять** сигнатуру `prices: SharedFlow<PriceUpdate>`.

**Критерий готовности:**
- При искусственном обрыве gRPC-стрима (например, отключить
  сеть на 10 секунд) стрим переподключается автоматически,
  в логах видны `W [prices] gRPC error` и повторная подписка.
- Polling во время паузы продолжает эмитить цены.

**Оценка:** ~30 минут + 15 минут на тест.

**Зависимости:** нет.



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

### ✅ Защита ветки master

Настроено в GitLab (Settings → Repository → Protected branches):
- `master` защищён; прямой push запрещён — только через MR;
- force push выключен;
- `Allowed to merge: Maintainers`;
- `Allowed to push and merge: No one`.

Require approval намеренно не включён: проект одиночный,
approve своего же MR GitLab не разрешает. Вернуться при появлении
второго Maintainer'а.

CI-проверка в `build-release` (`git merge-base --is-ancestor`)
остаётся как вторая линия обороны — тег обязан стоять на коммите
из master.

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

### ✅ versionName в GitHub Release (отменено)

Исходная задача из эпохи GitLab CI: добавить `Release vX.Y.Z`
в description. В GitHub это решено через `name: Release
${{ inputs.tag }}` в `release.yml` — версия видна в заголовке
релиза, дублирование в body не нужно

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

### ✅ Обновление GitHub Actions до Node.js 24

- `actions/checkout`: v4 → v6
- `actions/upload-artifact`: v4 → v7

GitHub форсит Node.js 24 для actions с 2 июня 2026;
Node.js 20 будет удалён с раннеров 16 сентября 2026.
`actions/setup-java@v5` и `gradle/actions/setup-gradle@v5`
уже были на Node.js 24.

### ✅ Переезд CI на GitHub Actions

CI/CD перенесён с GitLab на GitHub Actions.

- `ci.yml` — `testDebugUnitTest`, `lintDebug`,
  `assembleDebug` на push и PR в `dev` / `master`.
- `release.yml` — сборка и публикация release APK на тег
  `v*`, плюс ручной запуск через `workflow_dispatch` с
  input `tag` для повторных релизов существующих тегов.
- Repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS`, `KEY_PASSWORD`.
- Rulesets: `Protect master` (PR, status checks, restrict
  deletions, block force pushes), `Protect release tags`
  (restrict updates, restrict deletions; для создания —
  bypass для Repository admin).
- Стратегия merge для PR `dev → master` — **Merge commit**
  (не squash, не rebase: они переписывают SHA и вызывают
  расхождение веток).

Первый релиз: `v1.0.3` — успешно собран и опубликован
в GitHub Releases.

`.gitlab-ci.yml` сохранён для будущего восстановления
пайплайна через self-hosted runner (см. ROADMAP →
«Self-hosted GitLab Runner через Ansible + Podman»).

### ✅ Renovate: self-hosted через GitHub Actions

Автоматическое отслеживание обновлений Gradle-зависимостей
через self-hosted Renovate в GitHub Actions.

- `renovate.json` — группировка Kotlin/KSP/Compose Compiler,
  Hilt, AndroidX Compose, gRPC. `protobuf-*` отключены
  (намеренный pin 3.25.8 для GeneratedMessageV3).
- `.github/workflows/renovate.yml` — запуск раз в неделю по
  расписанию (суббота 00:00 UTC) + `workflow_dispatch` для
  ручного теста. Расписание только в cron; в `renovate.json`
  блоки `schedule` и `timezone` отсутствуют.
- PAT бот-аккаунта `tscalp-renovate-bot` (scope `repo` +
  `workflow`), хранится как Secret `RENOVATE_TOKEN`.
  Срок действия 90 дней — обновлять по уведомлению.
- Default branch в GitHub переключён с `master` на `dev`:
  workflows индексируются только из default branch.

### ✅ Coverage в unit-tests

Инструмент — **Kover** (не JaCoCo). Миграция выполнена.

- `kover {}` в `app/build.gradle.kts`: exclusions для Hilt,
  Compose, BuildConfig, Dagger; порог `minBound(3)` по инструкциям.
- CI: `koverXmlReportDebug`, `koverHtmlReportDebug`,
  `koverVerifyDebug` в `ci.yml`.
- Итоговое покрытие: **4.53%** инструкций, 7.86% веток.
- Отчёт сохраняется в артефактах CI, HTML доступен в браузере.

**Отложено:** per-class пороги (`PositionFormatter` 70%,
`CurrencyUtils` 70%, `CalculateTradeDetailsUseCase` 50% и т.д.).
Синтаксис per-class правил в Kover требует проверки — сначала
работает общий порог. Вернуться при следующей итерации по
покрытию.

**Итог:** JaCoCo полностью удалён из проекта, Kover единственный
инструмент покрытия.

### ✅ ktlint enforcement

Плагин `org.jlleitschuh.gradle.ktlint` 14.2.0 подключён
в root и app `build.gradle.kts`. CI-job `ktlintCheck`
выполняется на push и PR в `dev` / `master`.

**Сделано:**

- `.editorconfig` в корне проекта: отключены правила,
  конфликтующие со стилем проекта:
    - `function-naming` — Compose-функции по соглашению
      начинаются с большой буквы;
    - `no-wildcard-imports` — используются повсеместно;
    - `max-line-length` — узко для Compose-экранов;
    - `filename` — не критично для проекта.
- `ktlintFormat` — автофикс ~2200 нарушений
  (trailing-comma, indent, comment-spacing, import-ordering,
  multiline-expression-wrapping, redundant curly braces,
  final newline, consecutive blank lines).
- Вручную исправлены 16 неавтофиксируемых нарушений:
  дублирующие KDoc (`TInvestInstrumentMapper`),
  EOL-комментарии перед KDoc (`BrokerApi`),
  комментарии на строке value-параметра
  (`OrderModels`, `TradingStatus`, `BrokerAccountDialog`),
  однострочный `if` со скобками (`OrdersViewModel`).
- `ignoreFailures = false` — enforcement включён.
  Baseline не нужен: все три отчёта (main, test, kotlin-script)
  показывают no issues.

**Итог:** `:app:ktlintCheck` падает при любом новом
нарушении стиля. CI блокирует merge. 63 unit-теста зелёные.

### ✅ detekt enforcement

Плагин мигрирован с `io.gitlab.arturbosch.detekt` 1.23.8 на
`dev.detekt` 2.0.0-alpha.6. CI-job `detekt` выполняется на
push и PR в `dev` / `master`.

**Сделано:**

- **Миграция плагина.** Стабильная ветка 1.x несовместима
  с JDK 25: она передаёт `--jvm-target 25`, который Kotlin
  2.0.21 (встроенный в detekt 1.x) отвергает. Alpha-ветка
  2.x поддерживает JDK 25, Kotlin 2.4, AGP 9.x.
  Config переехал из `detekt {}` в
  `tasks.withType<dev.detekt.gradle.Detekt>().configureEach {}`,
  `jvmTarget.set("25")` явно.
- **`config/detekt/detekt.yml`.** Отключены правила, дающие
  ложные срабатывания или конфликтующие со стилем проекта:
  `FunctionNaming` (Compose), `MatchingDeclarationName`,
  `WildcardImport`, `MaxLineLength`, `ReturnCount`,
  `TooGenericExceptionCaught`, `TooGenericExceptionThrown`,
  `SwallowedException`, `UnusedParameter`. Настроены
  `MagicNumber` (ignoreNumbers), `ComplexCondition`
  (allowedConditions=4), `ConstructorParameterNaming`
  (excludeClassPattern для `InstrumentUi`/`FutureUi`/`ShareUi`).
- **Ручные фиксы группы 4** (16 issues):
    - `CustomTrustManager.printStacktrace` → `AppLogger.w`;
    - мёртвый `parseDate` в `TInvestOrdersService` удалён
      (восстановление — см. ROADMAP → «Парсинг expireDate»);
    - TODO-комментарии `FinamBrokerApi` → class-level KDoc
      со ссылкой на «Реализация Finam Broker API»;
    - `PositionFormatterTest` перемещён в `util/`;
    - `PositionStreamManager` перемещён в `domain.api`;
    - `BcsBrokerApi.obtainAccessToken` разбит на
      `obtainAccessToken` + `parseAccessTokenResponse`
      (фикс `ThrowsCount`);
    - `TScalpApplication.onCreate` разбит на
      `restoreBrokerConnection` + `restoreTInvest` + `restoreBcs`
      (фикс `LoopWithTooManyJumpStatements` и
      `CyclomaticComplexMethod`).
- **Baseline для `MagicNumber`** (45 записей) —
  `app/detekt-baseline.xml`. Массовые UI-размеры и
  alpha/color-константы, точечное подавление непрактично.
- **`@Suppress` для архитектурного долга** —
  `LongMethod`, `CyclomaticComplexMethod`,
  `TooManyFunctions`, `LargeClass` (32 issues) подавлены
  с inline-комментариями и ссылками на ROADMAP →
  «Рефакторинг архитектурного долга (detekt-группа 3)».
- **`ignoreFailures = false`** — enforcement включён.

**Итог:** `:app:detekt` падает при любом новом нарушении.
CI блокирует merge. 63 unit-теста зелёные.

**Открытие:** `@Suppress("UnusedParameter")` в detekt
2.0.0-alpha.6 игнорируется — правило пришлось отключить
целиком. Для остальных правил группы 3 `@Suppress`
работает.

---
