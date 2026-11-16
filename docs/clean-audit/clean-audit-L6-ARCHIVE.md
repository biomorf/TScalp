<!-- SECTION_BEGIN: clean-audit-L6-ARCHIVE -->
## L6 — data/repository (архив)










<!-- SUBSECTION_BEGIN: clean-audit-78 -->
___
### Исправленные (L6)
<!-- SUBSECTION_END: clean-audit-78 -->









<!-- SUBSECTION_BEGIN: Н79 -->
___
### Н79 ✅ Н79. CustomTrustManager loadCertificateToKeyStore — swallowed Exception

**Статус:** исправлено (2026-11-13, PR #144).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `CustomTrustManager.loadCertificateToKeyStore` — `catch (Exception) { AppLogger.w(TAG, "Не удалось загрузить сертификат из ресурса $resId", e) }`. Если CA-сертификат не загрузился — тихо пропускаем. При подключении к T-Invest: systemTrustManager не знает Russian CA, customTrustManager без сертификата → неинформативная TLS-ошибка. Причина (не загрузился конкретный ресурс) теряется.

**Нарушенное правило Clean:** не строго Clean — resilience-логика молча деградирует.

**Clean-альтернатива:** различать «ресурс отсутствует» (binding-ошибка, не должна случаться) от «сертификат битый» (runtime). При битом — `throw IllegalStateException("CA load failed: res=$resId", e)` (fail-fast), не продолжать с усечённым набором. Плюсы: TLS-проблемы видны сразу. Минусы: —.

**Выгоды:** —.

**Что сделано:** `loadCertificateToKeyStore` после `logger.w` бросает `IllegalStateException("CA load failed: res=$resId", e)` — fail-fast при битом/отсутствующем CA. Коммит `447d8df2`, PR #144.

**Ссылка на задачу:** PR #144 (merge `f3d3d916`).

<!-- SUBSECTION_END: Н79 -->

<!-- SUBSECTION_BEGIN: Н80 -->
___
### Н80 ✅ Н80. CustomTrustManager — нет проверки загрузки всех 5 CA

**Статус:** исправлено (2026-11-13, PR #144).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `CustomTrustManager.init` загружает 5 CA (`russian_trusted_root_ca`, `_ca_gost_2025`, `_sub_ca`, `_sub_ca_2024`, `_sub_ca_gost_2025`). Если один упал — `customTrustManager` работает с 4/5. `getAcceptedIssuers` вернёт усечённый набор. Индикации нет.

**Нарушенное правило Clean:** не строго Clean — молчаливая деградация.

**Clean-альтернатива:** после загрузки проверить `customTrustManager.acceptedIssuers.size == 5`; если меньше — `throw IllegalStateException` или `AppLogger.e` (громкий). Плюсы: известно, что все CA активны. Минусы: —.

**Выгоды:** —.

**Что сделано:** В `init` после `customTmf.init` — `check(customTrustManager.acceptedIssuers.size >= EXPECTED_CA_COUNT)` (5). При недоборе — `IllegalStateException`. Коммит `447d8df2`, PR #144.

**Ссылка на задачу:** PR #144 (merge `f3d3d916`). Связано с Н79.

<!-- SUBSECTION_END: Н80 -->

<!-- SUBSECTION_BEGIN: Н68 -->
___
### Н68 ✅ Н68. SettingsRepository — @Suppress("TooManyFunctions")

**Статус:** исправлено (PR #81, `dev`, 2026-10-31).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `SettingsRepository` — 125 строк, 15 функций, `@Suppress("TooManyFunctions")`. Объединяет: учётные данные брокеров (5 методов), счёт по умолчанию (2), режим песочницы (2), подтверждение заявок (3), ключи (3). KDoc ссылается на `### Рефакторинг репозиториев`.

**Нарушенное правило Clean:** SRP — репозиторий отвечает за 4 области настроек.

**Clean-альтернатива:** разбить на `BrokerCredentialsRepository`, `AccountPreferencesRepository`, `SandboxPreferencesRepository`, `TradingPreferencesRepository`. Плюсы: SRP. Минусы: 4 репозитория вместо 1; `@Binds` для каждого.

**Выгоды:** один репозиторий, простая навигация.


**Уточнение (2026-10-27, H21b):** после `git mv` класс →
`SettingsRepositoryImpl`; 12 функций (было 15: KDoc устарел).
`@Suppress("TooManyFunctions")` остался на `SettingsRepositoryImpl` и
добавлен на интерфейс `domain.api.repository.SettingsRepository`
(13 членов). Декомпозиция (4 области) — не выполнена, не в scope H21b.

**Что сделано:** split на 4 интерфейса (`BrokerCredentialsRepository`, `AccountPreferencesRepository`, `SandboxPreferencesRepository`, `TradingPreferencesRepository`) + 4 `*Impl` + `SettingsKeys` (общие DataStore-ключи, legacy-формат). 4 `@Binds` — в новый `PreferencesRepositoryBindingModule` (иначе `RepositoryBindingModule` превышал detekt `TooManyFunctions`: 13 > 11, после удаления старого `@Binds` было бы 12). Мигрированы 6 потребителей + `BrokerLifecycleManagerTest`. `SettingsRepository` / `SettingsRepositoryImpl` / `bindSettingsRepository` удалены; `RepositoryBindingModule` — 8 binds. Коммиты `d96687a`, `ec464bd`, `ad53ef5`.

**Ссылка на задачу:** PR #81 (`feature/clean-audit-pkg-b`, `dev`).

<!-- SUBSECTION_END: Н68 -->

<!-- SUBSECTION_BEGIN: Н71 -->
___
### Н71 ✅ Н71. BcsBrokerApi.initialize логирует refreshToken

**Статус:** исправлено (2026-10-28, ветка `bugfix/security-token-logging`).

**Серьёзность:** критично.

**Live тест:** ✓

**Описание:** `BcsBrokerApi.initialize(refreshToken, clientId)` пишет в лог: `AppLogger.d("BcsBrokerApi", "Инициализация с refreshToken=$refreshToken, clientId=$clientId")`. Refresh-токен — секрет (90 дней, полный доступ к портфелю/торговле). В debug-сборке попадает в logcat в открытом виде; в release `AppLogger.d` отсекается, но `logcat -b all` / устройство может сохранить.

**Нарушенное правило Clean:** AGENTS 4e — секреты не должны попадать в логи.

**Clean-альтернатива:** логировать `refreshToken=***` (redact) или `refreshToken.len=${refreshToken.length}`. Плюсы: секрет не в логах. Минусы: —.

**Выгоды:** —.

**Что сделано:** `$refreshToken` → `${refreshToken.length}`; других утечек нет (grep по `data/api/`).

**Ссылка на задачу:** `docs/ISSUES.md` → «BcsBrokerApi логирует refreshToken» (security-запись).

<!-- SUBSECTION_END: Н71 -->

<!-- SUBSECTION_BEGIN: Н46 -->
___
### Н46 ✅ Н46. SearchCache.search бросает IllegalArgumentException

**Статус:** исправлено (PR #75, `dev`, 2026-10-30).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `SearchCache.search` при ненайденном брокере: `throw IllegalArgumentException("Брокер … не найден")`. Остальные методы `InvestRepository` / data — возвращают `AppResult<T>`, не бросают. Исключение из data в presentation (`OrdersViewModel.onSearchQueryChanged` ловит `Exception`, показывает «Ошибка поиска: …»).

**Нарушенное правило Clean:** контракт data — `AppResult<T>` (по AGENTS: «`AppResult<T>` (Success/Failure), не исключения на границах слоёв»).

**Clean-альтернатива:** `SearchCache.search` возвращает `AppResult<List<InstrumentUi>>` (или `List<InstrumentUi>` без throw — пустой при ненайденном брокере, как `InvestRepository`). Плюсы: консистентность с data-контрактом. Минусы: правка `OrdersViewModel` (2 callsites).

**Выгоды:** —.

**Что сделано:** `SearchCacheRepository.search` → `AppResult<List<InstrumentUi>>`; `SearchCacheImpl` — `AppResult.Failure(AppError.Unknown(…))` при ненайденном брокере, `AppResult.Success(sorted)`; `OrdersViewModel` — 2 callsites через `when`, `try/catch` для `CancellationException`; KDoc обновлён.

**Ссылка на задачу:** PR #75 (`dev`, 2026-10-30).

<!-- SUBSECTION_END: Н46 -->

<!-- SUBSECTION_BEGIN: Н47 -->
___
### Н47 ✅ Н47. SearchCache.search — избыточный withContext(Dispatchers.IO)

**Статус:** исправлено (PR #75, `dev`, 2026-10-30).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `SearchCache.search`: `withContext(Dispatchers.IO) { broker.userInstruments?.findInstruments(query) ?: emptyList() }`. `findInstruments` — suspend, внутри data-слоя брокер сам выбирает диспатчер (gRPC/REST). `withContext(Dispatchers.IO)` в data — избыточная диспатчеризация.

**Нарушенное правило Clean:** не строго Clean — data не доверяет слою ниже, дублирует диспатчеризацию.

**Clean-альтернатива:** убрать `withContext(Dispatchers.IO)` — вызывать `findInstruments` напрямую. Плюсы: чисто. Минусы: —.

**Выгоды:** —.

**Что сделано:** `withContext(Dispatchers.IO)` убран (`findInstruments` suspend; T-Invest gRPC-путь защищён внутри `TInvestMarketDataService`; BCS/Finam — `userInstruments = null`, не вызывают); импорты `Dispatchers`/`withContext` удалены; REST-fallback для `findInstruments` — отдельная задача в ROADMAP.

**Ссылка на задачу:** PR #75 (`dev`, 2026-10-30). Связано: `### T-Invest REST: findInstruments fallback` (ROADMAP). Мелочь, правится при следующем касании `SearchCache`.

---

<!-- SUBSECTION_END: Н47 -->

<!-- SUBSECTION_BEGIN: Н44 -->
___
### Н44 ✅ Н44. data/repository/* → di.BrokerManager

**Статус:** исправлено (PR #60, `dev`, 2026-10-28).

**Серьёзность:** критично.

**Live тест:** -

**Описание:** `SearchCache`, `InstrumentRepository`, `InvestRepository` инжектят `com.gitlab.biomorf.tscalp.di.BrokerManager` через конструктор. Data-слой зависит от `di/`.

**Нарушенное правило Clean:** dependency rule — data зависит от `di`.

**Clean-альтернатива:** `BrokerRegistry` (domain-интерфейс, дизайн в L7-Н22/Н29). 3 репозитория инжектят `BrokerRegistry`. Плюсы: data → domain. Минусы: правка 3 файлов.

**Выгоды:** —.


**Расширение (2026-10-27, InvestRepository split + H21b):** было 3 файла
(`SearchCache`, `InstrumentRepository`, `InvestRepository`). Стало 8
`*RepositoryImpl`: `AccountsRepositoryImpl`, `InstrumentRepositoryImpl`,
`MarketDataRepositoryImpl`, `OrdersRepositoryImpl`,
`PortfolioRepositoryImpl`, `SearchCacheImpl`, `SettingsRepositoryImpl`,
`TradingStateRepositoryImpl`. Все инжектят `BrokerManager`. Закрывается
в H22 (`BrokerRegistry`), не в H21b.

**Ссылка на задачу:** `### H22. BrokerRegistry` (PR #60, `dev`).

<!-- SUBSECTION_END: Н44 -->

<!-- SUBSECTION_BEGIN: Н62 -->
___
### Н62 ✅ Н62. TInvestBrokerAPI.initialize не закрывает positionsWebSocket

**Статус:** исправлено (PR #78, `dev`, 2026-10-31).

**Серьёзность:** критично.

**Live тест:** ✓

**Описание:** `TInvestBrokerAPI.initialize` перезаписывает `positionsWebSocket` без `close()` предыдущего. Ре-логин, смена токена, повторное «Сохранить» в настройках — старая WS-сессия держит сокет, получает ping/frames в никуда. Утечка соединения + нагрузка на сервер (WS-сессии накапливаются). `deinitialize` тоже обнуляет без `close()` — та же утечка при logout.

**Нарушенное правило Clean:** не строго Clean — ресурсный leak в lifecycle.

**Clean-альтернатива:** `positionsWebSocket?.close()` перед перезаписью; добавить `close()` в `TInvestPositionsWebSocket`. Плюсы: нет утечки. Минусы: —.

**Выгоды:** —.

**Что сделано:** `TInvestWsService.initialize` — `stop()` первой строкой (до `if (sandbox) return`): старый `wsScope` (prices / orderState) отменяется, клиенты закрываются. Формулировка уточнена: `positionsWebSocket` — cold flow (Н87), `= null` в `stop()` достаточно. `TInvestBrokerApi.initialize` не менялся (делегирует lifecycle в `wsService.initialize`). Коммит `2e2df67`. **Live-тест PASS** (2026-10-31): сценарий A — logout → нет `connecting` до login; login → ровно 1 `connecting` на prices и 1 на positions; frames от старых соединений не идут. Сценарий C — sandbox → нет `connecting` (`if (sandbox) return`).

**Ссылка на задачу:** PR #78 (`dev`, 2026-10-31). `docs/ISSUES.md` → «TInvestBrokerAPI.initialize() не закрывает предыдущий positionsWebSocket».

<!-- SUBSECTION_END: Н62 -->

<!-- SUBSECTION_BEGIN: Н63 -->
___
### Н63 ✅ Н63. TInvestMarketDataService — god-класс 622 строки

**Статус:** исправлено (2026-10-31, PR #84, `dev`).

**Серьёзность:** критично.

**Live тест:** -

**Описание:** `TInvestMarketDataService` — 622 строки, 19 функций, `@Suppress("TooManyFunctions")`. Отвечает за: счета (`getAccounts`, `openSandboxAccount`, `closeSandboxAccount`), портфель (`getPortfolio`, `fetchPortfolioPositions`), gRPC-стримы позиций (`subscribePositionsGrpc`, `subscribePositionsGrpcWithReconnect`, `convertSecurity`, `convertFuture`), баланс (`getBalance`, `sandboxPayIn`), инструменты (`fetchFullInstrument`, `findInstruments`, `findInstrumentShorts`, `fetchProtoInstrument`, `getFuturesMargin`), цены/статусы (`getLastPricesByTscalpInstrumentId`, `getTradingStatuses`), gRPC-стримы цен/ордеров (`subscribeLastPrices`, `subscribeOrderState`), проверка (`checkTradeAvailability`).

**Нарушенное правило Clean:** SRP — класс отвечает за 8+ доменных областей.

**Clean-альтернатива:** разбить по областям: `TInvestAccountsService`, `TInvestPortfolioService`, `TInvestPositionsService`, `TInvestInstrumentsService`, `TInvestPricesService`, `TInvestTradingService`. Плюсы: один сервис — одна область. Минусы: 6 файлов вместо 1, `TInvestApiState` шарится.

**Выгоды:** все методы работают на общем `TInvestApiState`, единая точка для gRPC-каналов. Разбивка — при рефакторинге.

**Ссылка на задачу:** `### ✅ Пакет C: TInvestMarketDataService split (Н63)` (ROADMAP, `dev`, 2026-10-31; PR #84).

<!-- SUBSECTION_END: Н63 -->

<!-- SUBSECTION_BEGIN: Н45 -->
___
### Н45 ✅ Н45. InvestRepository использует 4 fat-метода BrokerApi

**Статус:** исправлено (PR #56, `dev`). `InvestRepository` удалён;
4 fat-метода `BrokerApi` (`getLastPricesByTscalpInstrumentId`,
`fetchPortfolioPositions`, `checkTradeAvailability`,
`subscribePositions`) остались без потребителя — ревизия fat-части
вынесена задачей `### BrokerApi: ревизия fat-части после
InvestRepository split` (ROADMAP).

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `InvestRepository` вызывает 4 fat-метода `BrokerApi`:
- `broker.getLastPricesByTscalpInstrumentId(ids)` — `getLastPricesResult`;
- `broker.fetchPortfolioPositions(accountId, sandboxMode)` — `fetchPositionsResult`;
- `broker.checkTradeAvailability(...)` — `checkTradeAvailabilityResult`;
- `broker.subscribePositions(accountId)` — `subscribePositions` (дубль: есть `streams?.subscribePositions`).

Остальные 12 методов — через nullable-val роли. KDoc `BrokerApi` говорит «`InvestRepository` — фасад на fat `BrokerApi`, 29 callsites, использует все» — фактически использует 4 fat-метода, остальное через роли.

**Нарушенное правило Clean:** Interface Segregation — `InvestRepository` зависит от fat `BrokerApi` ради 4 методов.

**Clean-альтернатива (по H25):** выделить `PortfolioApi` (`fetchPortfolioPositions`), `MarketDataApi` (`getLastPricesByTscalpInstrumentId`), `TradeApi` (`checkTradeAvailability`); `subscribePositions` — на `streams?.subscribePositions`. `InvestRepository` инжектит роли. Плюсы: полный ISP. Минусы: 4 callsite правок в `InvestRepository` + новые роли.

**Выгоды:** —.

**Ссылка на задачу:** `### ✅ BrokerApi: ревизия fat-части после InvestRepository split` (закрыта, PR #61). **Связано с H25**.

<!-- SUBSECTION_END: Н45 -->

<!-- SUBSECTION_BEGIN: Н53 -->
___
### Н53 ✅ Н53. `data/api` — плоская структура (17 файлов в одном пакете)

**Статус:** исправлено (PR #84, `dev`, 2026-10-31).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `app/.../data/api/` содержит 17 файлов в одном пакете: 13 `TInvest*`, 1 `Bcs*`, 1 `Finam*`, `CustomTrustManager.kt`, `RestMappers.kt` + подпапка `error/`. При добавлении новых брокеров и сервисов (например, 6 сервисов из `### Пакет C`) пакет растёт линейно, навигация ухудшается. Находка упоминалась в сводном отчёте 2026-10-27 (стр. 1799, 1823) и в cross-ссылках Н89/Н90, но **инвентарной записи не имела** (нарушение 2c). Backfill 2026-10-31.

**Нарушенное правило Clean:** не строго Clean — организация пакета data-слоя (SRP на уровне модуля/пакета).

**Clean-альтернатива:** раскладка по подпапкам:
- `data/api/common/` — `CustomTrustManager.kt`, `RestMappers.kt`;
- `data/api/tinvest/` — 13 `TInvest*` (при #63 — `tinvest/services/`, `tinvest/mappers/`);
- `data/api/bcs/` — `BcsBrokerApi.kt`;
- `data/api/finam/` — `FinamBrokerApi.kt`;
- `data/api/error/` — без изменений.

Плюсы: навигация, изоляция брокеров, новые файлы создаются сразу в целевом пакете. Минусы: `git mv` 17 файлов + 14 строк импорта в 8 файлах (4 main + 4 test) — механическая работа.

**Выгоды:** —.

**Ссылка на задачу:** `### ✅ Пакет C: TInvestMarketDataService split (Н63)` (закрыта, PR #84) — шаг 0 (переезд).

**Сводный отчёт, 2026-10-31:** да (включена в отчёт от 2026-10-31).

<!-- SUBSECTION_END: Н53 -->

<!-- SUBSECTION_BEGIN: Н69 -->
___
### Н69 ✅ Н69. InvestRepository — @Suppress("TooManyFunctions")

**Статус:** исправлено (PR #56, `dev`). `InvestRepository` удалён;
`@Suppress` ушёл вместе с ним.

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `InvestRepository` — 357 строк, 17 функций, `@Suppress("TooManyFunctions")`. Объединяет 4 доменные области (аккаунты, заявки, портфель, стримы) + фасад над `BrokerApi`.

**Нарушенное правило Clean:** SRP + Clean-граница (presentation → domain, а не presentation → data → domain).

**Clean-альтернатива:** `### Разбивка InvestRepository по доменным областям` — 4 domain-интерфейса + 4 data-реализации.

**Выгоды:** один фасад, простой доступ для UI.

**Ссылка на задачу:** `### ✅ Разбивка InvestRepository по доменным областям` (закрыта, PR #56).

<!-- SUBSECTION_END: Н69 -->

<!-- SUBSECTION_BEGIN: Н65 -->
___
### Н65 ✅ Н65. TInvestInstrumentMapper.mapProtoToDomain — @Suppress("LongMethod")

**Статус:** исправлено (2026-11-05, PR #83).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `TInvestInstrumentMapper.mapProtoToDomain` — 118 строк, `@Suppress("LongMethod")`. Единый `when` по `type` с тремя ветками (futures / share / else), в каждой — полный конструктор модели (~40 аргументов).

**Нарушенное правило Clean:** SRP — функция маппит 3 типа инструмента.

**Clean-альтернатива:** `mapFutures`, `mapShares`, `mapDefault` + диспетчеризация в `mapProtoToDomain`. Плюсы: каждая ветка ~30 строк. Минусы: —.

**Выгоды:** один `when`, вся логика в одном месте.

**Что сделано:** `mapProtoToDomain` разбит на dispatcher + 7 мапперов (`mapFuture`, `mapShare`, `mapBond`, `mapOption`, `mapEtf`, `mapCurrency`, `mapOther`), `@Suppress("LongMethod")` снят. Merge `53810b9` + fix `fb6a67d`, PR #83.

**Ссылка на задачу:** `### ✅ Instrument: Phase 2 (sealed interface)` (PR #83).

<!-- SUBSECTION_END: Н65 -->

<!-- SUBSECTION_BEGIN: Н64 -->
___
### Н64 ✅ Н64. fetchPortfolioPositions — @Suppress("CyclomaticComplexMethod")

**Статус:** исправлено (2026-11-05, merge `662d1e4`, PR #96).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `TInvestPortfolioService.fetchPortfolioPositions` — `@Suppress("CyclomaticComplexMethod")`. ~40 строк, внутри: mapNotNull по позициям, fetchProtoInstrument для каждой, getFuturesMargin для фьючерсов, маппинг proto → domain, вычисление quantity/currentPrice/expectedYield/averagePrice, сборка `PositionStreamItem`.

**Нарушенное правило Clean:** SRP — функция делает 3 вещи (fetch instrument, fetch margin, map position).

**Clean-альтернатива:** `fetchPortfolioPositions` = `getPortfolio` + `.map { mapPositionToStreamItem(it, fetchInstrumentFn) }`. Плюсы: декомпозиция. Минусы: —.

**Выгоды:** один метод, работает.

**Что сделано:** `fetchPortfolioPositions` 59 → 20 строк: `getPortfolio` + `mapNotNull { enrichWithInstrumentData(pos, accountId) }`. 2 private helper'а: `enrichWithInstrumentData` (suspend, fetch proto + margin + map + toPosition) и `enrichWithMarginData` (сборка `PositionStreamItem`, 14 полей). `@Suppress("CyclomaticComplexMethod")` снят. Поведение 1:1: `balances = emptyList()` + комментарий про `money/blocked` сохранены (5g.4). Merge `662d1e4`, PR #96.

**Ссылка на задачу:** `### ✅ Рефакторинг data-слоя T-Invest (Н64)` (ROADMAP, PR #96).

<!-- SUBSECTION_END: Н64 -->

<!-- SECTION_END: clean-audit-L6-ARCHIVE -->
