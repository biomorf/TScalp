<!-- SECTION_BEGIN: issues-domain -->
## Domain










<!-- SUBSECTION_BEGIN: i-1 -->
___
### [DONE] I-1 OrderSearchViewModel: selectedBroker не влияет на API поиска (решён)
**Статус:** решён (PR #136, merge `58b8233b`).

**Симптом:** в UI диалога свойств поиска (`OrdersBrokerDialogs.kt` →
`SearchBrokerDialog` / `PairSearchBrokerDialog`) пользователь выбирает
брокера (`selectedSearchBroker` / `selectedPairSearchBroker`), но
`SearchInstrumentsUseCase.execute` в `OrderSearchViewModel.onSearchQueryChanged`
/ `onPairSearchQueryChanged` использует **другое** поле — `searchBroker`
/ `pairSearchBroker` (default `TINVEST`). Выбор брокера в диалоге
**не влияет** на то, к какому брокеру идёт запрос поиска.

**Причина:** в `OrderSearchState` два независимых поля на слот —
`searchBroker` (читается usecase'ом) и `selectedSearchBroker` (читается
UI диалога). `saveSearchBrokerSettings` обновляет только
`selectedSearchBroker`; `searchBroker` не меняется никогда (остаётся
default `TINVEST`). Pre-existing баг. В slot-рефакторинге
(`### OrderSearchViewModel: slot-параметр`, ROADMAP) `SlotSearchState`
сохранил оба поля 1:1 — поведение не менялось.

**Решение:** объединить `broker` и `selectedBroker` в `SlotSearchState`
в одно поле `broker`. `saveBroker(slot, broker)` обновляет `broker`;
`onQueryChanged` читает `broker`; UI (`OrdersBrokerDialogs.kt`) читает
`broker`. Аналогично — для pair-слота.

**Файлы:**
- `presentation/screens/orders/OrderSearchState.kt` —
  `SlotSearchState`: `broker` + `selectedBroker` → один `broker`.
- `presentation/screens/orders/OrderSearchViewModel.kt` — `saveBroker`
  / `onQueryChanged` / `refresh`.
- `presentation/screens/orders/OrdersBrokerDialogs.kt` —
  `SearchBrokerDialog` / `PairSearchBrokerDialog`.
- `app/src/test/kotlin/.../OrderSearchViewModelTest.kt` — кейсы
  `saveBroker` → влияет на `execute`-брокера (1 unit).

**Связано:** `docs/ROADMAP.md` → `### OrderSearchViewModel: slot-параметр
(reduce functions, без split)` (баг обнаружен при рефакторинге, PR #135);
`### OrderSearchViewModel: объединить broker + selectedBroker` (fix, PR #136);
`### OrderSearchViewModel: re-search on broker change` (follow-up, PR #137).

**Что сделано:** PR #136 (merge `58b8233b`). `SlotSearchState.selectedBroker`
удалён, оставлен один `broker`. `OrderSearchViewModel.saveBroker(slot, broker)`
пишет в `broker`; `onQueryChanged` / `refresh` читают `broker` (не менялись);
`OrdersBrokerDialogs` — `primary.broker` / `pair.broker`. Тест: 3 assertion →
`.broker`, 2 rename, +1 новый `saveBroker updates broker used by execute`
(10 → 11). Follow-up: PR #137 (merge `c53aaa51`) — `saveBroker` теперь
re-search при смене брокера (`onQueryChanged(slot, currentQuery)`), результаты
сразу от нового брокера; stale-результаты не остаются.

**Когда вернуться:** не требуется. Открыть заново, если появится похожая
дивергенция state (два поля на одно логическое значение в одном `data class`).
<!-- SUBSECTION_END: i-1 -->










<!-- SUBSECTION_BEGIN: i-11 -->
___
### [DONE] I-11 restoreTInvest не использует сохранённый accountId при падении getAccounts
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
<!-- SUBSECTION_END: i-11 -->










<!-- SUBSECTION_BEGIN: i-21 -->
___
### [DONE] I-21 Дублирующий init {} в SettingsViewModel
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
<!-- SUBSECTION_END: i-21 -->










<!-- SUBSECTION_BEGIN: i-23 -->
___
### [OPEN] I-23 Асимметрия lifecycle: старт менеджеров после login vs restore
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
<!-- SUBSECTION_END: i-23 -->










<!-- SUBSECTION_BEGIN: i-31 -->
___
### [DONE] I-31 ClassCastException в PostOrderUseCase при StopLoss без stopPrice
**Статус:** решён (PR #132, merge `718d108c`, 2026-11-11).

**Симптом:** `PrepareOrderRequestUseCase.prepare(StopLoss, stopPrice=null)`
возвращает `PreparedOrders(primaryRequest=BrokerOrderRequest(MARKET),
isPrimaryStop=true)`. `PostOrderUseCase.postPrimary`: `if (isPrimaryStop)
{ postStopOrderResult(primaryRequest as StopOrderRequest) }` → `ClassCastException`.

**Причина:** market-fallback на строках 58–71 `prepare` с ошибочным
`isPrimaryStop = true` (копипаст из market-ветки). UI валидирует
(`OrderFormState.isStopPriceValid`), поэтому из UI недостижимо; public
domain-API достижимо (прямой вызов из тестов/скриптов/будущей автоматизации).

**Решение:** B1 — `sealed OrderRequest` + `sealed PrepareResult`;
StopLoss без `stopPrice` → `Invalid("stopPrice required…")`; `isPrimaryStop`
удалён (тип несёт `OrderRequest.Stop`). Silent drop pair — тоже `Invalid`.
См. `docs/ROADMAP-ARCHIVE.md` → `### ✅ PrepareOrderRequestUseCase: sealed OrderRequest + decompose (B1+B2)`.

**Что сделано:** PR #132 (merge `718d108c`). `sealed interface OrderRequest`
(`Broker` / `Stop`) заменяет `Any` в `PreparedOrders`; `PrepareResult`
(`Prepared` / `Invalid`) — новый контракт `prepare(...)`. `isPrimaryStop` /
`isPairedStop` удалены (тип `OrderRequest.Stop` несёт семантику). `postPrimary`
/ `postPaired` — exhaustive `when`, 0 `as`-cast. StopLoss без `stopPrice` →
`Invalid("stopPrice required…")` → `PostOrderOutcome.InvalidRequest`. Silent
drop pair-stop без `stopPrice` → `Invalid`. `execute` complexity 15 → 8
(extract `executePrepared`). `PostOrderUseCaseTest` — 8 тестов (было 7, +1
`prepare invalid → InvalidRequest`). B2 (decompose `prepare` + `@Suppress`
removal + `PrepareOrderRequestUseCaseTest`) — следующий PR.

**Когда вернуться:** — (закрыто; B2 — отдельная фаза umbrella-задачи).
<!-- SUBSECTION_END: i-31 -->





<!-- SECTION_END: issues-domain -->
