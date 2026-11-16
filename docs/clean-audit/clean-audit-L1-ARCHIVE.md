<!-- SECTION_BEGIN: clean-audit-L1-ARCHIVE -->
## L1 — presentation (архив)










<!-- SUBSECTION_BEGIN: clean-audit-5 -->
___
### Исправленные (L1)
<!-- SUBSECTION_END: clean-audit-5 -->










<!-- SUBSECTION_BEGIN: clean-audit-6 -->
___
### ✅ M1/M7. SearchInstrumentsUseCase: presentation prefix
**Статус:** исправлено (2026-11-15, PR #160, merge `4af3b682`).

**Серьёзность:** низко.

**Live тест:** -.

**Описание:** `SearchInstrumentsUseCase.kt:59` формировал UI-текст `"Ошибка поиска: ${e.message}"` (M1 — domain делает presentation-работу; M7 — конвертация domain→presentation). `OrderSearchViewModel:105` добавлял тот же префикс → двойной `"Ошибка поиска: Ошибка поиска: RESOURCE_EXHAUSTED"` в live-test (2026-11-15).

**Нарушенное правило:** M1 — domain форматирует UI; M7 — presentation-форматирование в domain.

**Clean-альтернатива:** domain возвращает `AppError.Unknown(e.message ?: "Search failed", e)` — сырое сообщение; presentation (`OrderSearchViewModel`) форматирует префикс.

**MVVM-альтернатива:** то же — presentation форматирует.

**Что сделано:** `SearchInstrumentsUseCase.kt:59` → `AppError.Unknown(e.message ?: "Search failed", e)`. Двойной префикс исчез. PR #160, коммит `9297b4b1`.

**Ссылка на задачу:** PR #160 (merge `4af3b682`).

**Связано:** `docs/ISSUES.md` → `## T-Invest rate-limit umbrella...` (RESOURCE_EXHAUSTED — отдельная причина).
<!-- SUBSECTION_END: clean-audit-6 -->










<!-- SUBSECTION_BEGIN: M016 -->
___
### ✅ M016. One-shot events через StateFlow (M6) — statusMessage / isError
**Статус:** исправлено (2026-11-09, PR #112, merge 967b28fb).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `statusMessage` / `isError` живут в 4 state: `OrderSearchState` (30, 32), `OrderFormState` (73, 74), `OrdersCoordState` (36, 37), `OrdersListViewModel` state. UI читает через `collectAsState`; consume — `clearStatus()` + `LaunchedEffect(...statusMessage)` (workaround). `StateFlow` реплеит последнее значение → при пересоздании Composable snackbar показывается повторно.

**Нарушенное правило:** M6 — one-shot события через `StateFlow` без consume.

**Clean-альтернатива:** `SharedFlow<StatusEvent>` / `Channel<StatusEvent>` в каждой VM; `statusMessage` / `isError` удаляются из state. Плюсы: no replay, snackbar — 1:1. Минусы: миграция 4 VM + state + UI.

**MVVM-альтернатива:** то же — event stream.

**Ссылка на задачу:** `### ✅ 4. M6: one-shot events (StatusEvent)` (`docs/roadmap/ROADMAP-ARCHIVE.md`, PR #112, merge `967b28fb`).

**Сводный отчёт, 2026-11-06:** да (включена в отчёт от 2026-11-06).
<!-- SUBSECTION_END: M016 -->










<!-- SUBSECTION_BEGIN: M001 -->
___
### ✅ M001. OrdersViewModel форматирует overlay (формулы в VM)
**Статус:** исправлено (2026-11-09, PR #111, merge 7bef1808).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `OrdersViewModel.formatCostOverlay` (1148) и `formatMultiplierOverlay` (1162) — private-функции VM строят строки `costOverlay`/`multiplierOverlay` через `formatPrice` / `formatCurrency` / `formatDomesticCurrency`. Форматирование — обязанность UI-слоя; VM должна отдавать `TradeDetails` (числа), а не строки отображения.

**Нарушенное правило:** M1 — VM делает работу presentation (форматирование). Также M7 (конвертация domain↔presentation в VM).

**Clean-альтернатива:** `formatCostOverlay`/`formatMultiplierOverlay` — в `presentation/util/` (рядом с `CurrencyFormat`), вызываются из Composable. Плюсы: VM отдаёт `TradeDetails`, UI форматирует. Минусы: правка `OrdersScreen` (2 callsites).

**MVVM-альтернатива:** перенести форматирование в Composable (через `remember(details)`). Плюсы: VM — только состояние. Минусы: `OrdersScreen` уже 646 строк (см. detekt-группу 3 → Compose-экраны).

**Выгоды:** —.

**Ссылка на задачу:** `### ✅ M001: formatting в presentation` (`docs/roadmap/ROADMAP-ARCHIVE.md`, PR #111, merge `7bef1808`).

**Сводный отчёт, 2026-10-31:** да (включена в отчёт от 2026-10-31).
<!-- SUBSECTION_END: M001 -->










<!-- SUBSECTION_BEGIN: M013 -->
___
### ✅ M013. Orchestration в Orders / Portfolio / OrdersList ViewModels (7 методов)
**Статус:** исправлено (2026-11-09, A2 закрыт; PR #110; live-test 1-2,5,6-partial PASS).

**Серьёзность:** высоко.

**Live тест:** ✓.

**Описание:** после A2 (M006) orchestration вынесена из `SettingsViewModel` в usecase. Ревизия остальных ViewModel (2026-11-06) нашла тот же паттерн M2 в 3 VM, 7 методов, ~290 строк orchestration:

| VM | Метод | Оркестрация |
|---|---|---|
| `PortfolioViewModel` | `loadPortfolio` | getAccounts → seedSheet (getBalance × accounts) → startPositionUpdates → syncPriceInterest |
| `OrdersViewModel` | `checkApiInitialization` | init → loadAccounts + syncPriceInterest / logout cleanup (stop streams, clearInterest) |
| `OrdersViewModel` | `loadAccounts` | getAccounts → loadDefaultAccountId → state |
| `OrderDialogsViewModel` | `openBrokerDialog` | M2-fixed (OpenCardDialogUseCase) |
| `OrderDialogsViewModel` | `loadDialogAccounts` | M2-fixed in 12a (LoadCardAccountsUseCase) |
| `OrdersListViewModel` | `cancelOrder` | getAccounts → cancelOrder / cancelStopOrder |
| `OrdersListViewModel` | `loadOrders` | getAccounts → getOrders / getStopOrders |
| `OrdersViewModel` | `restoreState` | load() + getInstrument × 2 + pointValue + brokerReady → форма |
| `OrdersViewModel` | `onPairedInstrumentSelected` | getLastPrices → paired price / pointValue |

**Не в scope:** размер `OrdersViewModel` (1159 строк, 22 launch) — архитектурный вопрос разбиения на несколько VM, отдельная задача. M001 (formatted overlay), M011 (`as?` в UI) — отдельные записи.

**Нарушенное правило:** M2 — business-logic (orchestration) в VM, не в UseCase.

**Clean-альтернатива:** usecase в `domain.usecases` на каждый метод:
- `LoadPortfolioUseCase` — accounts + seed + reload; возврат `PortfolioSheet` (seedSheet → в usecase).
- `CheckBrokerInitializationUseCase` — init / logout cleanup (stop streams + clearInterest).
- `LoadOrdersAccountsUseCase` — accounts + auto-select default; переиспользовать `LoadBrokerAccountsUseCase` (M006).
- `OpenBrokerDialogUseCase` — findCard + accounts; тоже через `LoadBrokerAccountsUseCase`.
- `LoadDialogAccountsUseCase` — accounts для диалога; через `LoadBrokerAccountsUseCase`.
- `CancelOrderUseCase` — accounts + cancel (stop/regular); возврат `AppResult<Unit>`.
- `LoadOrdersUseCase` — accounts + getOrders / getStopOrders; возврат `AppResult<List<Order>>`.

VM — тонкие: `usecase.execute + _uiState.update`. Плюсы: VM тестируемы без mock-инфраструктуры, usecase покрыт unit-тестами. Минусы: 5–7 новых usecase (~2–3 ч) + миграция callsites.

**MVVM-альтернатива:** то же — orchestration в UseCase, VM — state + delegation.

**Выгоды:** —.

**Что сделано:** A2 закрыт 2026-11-09 (PR #110). 7 из 9 методов вынесены в usecase: `LoadPortfolioUseCase`, `CheckBrokerInitializationUseCase` (1 для Portfolio + Orders, Q5), `LoadOrdersAccountsUseCase`, `LoadCardAccountsUseCase` (1 для `openBrokerDialog` / `loadDialogAccounts`, Q10), `CancelOrderUseCase`. `openBrokerDialog` / `loadDialogAccounts` переехали в `OrderDialogsViewModel` (split 13d), wiring → usecases (M015). `loadOrders` — по Q3/F3 manager-lifecycle, остаётся в VM (`startPositionUpdates`). `startPositionUpdates` / `stopPositionUpdates` / `syncPriceInterest` — lifecycle (Q2), остаются в VM. M013-related follow-ups закрыты: M002 (`PlaceOrderUseCase`), M015 (`OpenCardDialogUseCase`), M017 (`OrderFormStateMapper`). Live-test: 1-2,5,6-partial PASS (write-token, sandbox T-Invest); 3-4 (смена брокера) — deferred closure в отдельной задаче `### Live-test Broker lifecycle API: BCS/Finam` (ROADMAP); `NoneInitialized`-ветка — pre-existing `BrokerRegistry.anyInitialized` (`docs/ISSUES.md`).

**Ссылка на задачу:** `### ✅ A2: orchestration out of Orders/Portfolio/OrdersList (M013)` (`docs/roadmap/ROADMAP-ARCHIVE.md`, PR #110, merge `a590122a`).

**Сводный отчёт, 2026-11-06:** да.
<!-- SUBSECTION_END: M013 -->










<!-- SUBSECTION_BEGIN: M014 -->
___
### ✅ M014. OrdersCoordState — `activeCard` / `activePairedCard` → pure extensions
**Статус:** исправлено (2026-11-07, 13d-F, `9776172`).

**Серьёзность:** низко.

**Live тест:** -.

**Описание:** `OrdersUiState.activeCard` / `.activePairedCard` — computed
properties в state, неявно читающие `selectedInstrument` (Form) +
`lastSelectedInstruments` (Coord). После split 13d эти поля живут в
разных state; вычисление в Coord-state было бы cross-VM. 13d-F вынес их:
`OrdersCoordState.activeCardFor(instrument)` / `.activePairedCardFor(instrument)`
— pure extensions, принимающие `instrument` (Form) явным параметром.
UI callsites (`OrdersSearch` / `OrdersPairTrading`) передают
`formState.selectedInstrument` / `formState.pairedInstrument`.

**Нарушенное правило:** M1 — state computed property неявно
cross-references Form + Coord data; UI читает через state-обёртку вместо
явного параметра.

**Clean-альтернатива:** pure extension на `OrdersCoordState` с явным
параметром `instrument` (2c fixation в 13d-F KDoc). Плюсы: нет скрытых
cross-state reads; Coord-state immutable, без computed от другого state.
Минусы: UI передаёт параметр явно (1 extra line per callsite).

**MVVM-альтернатива:** то же — pure extension + parameter (standard
Compose state read).

**Что сделано:** `OrdersUiState.activeCard` / `.activePairedCard` (computed)
→ `OrdersCoordState.activeCardFor` / `.activePairedCardFor` (extensions,
13d-F). UI callsites в `OrdersSearch` / `OrdersPairTrading` обновлены.

**Ссылка на задачу:** `### OrdersViewModel: разбить на несколько VM` (ROADMAP, шаг 13d-F).
<!-- SUBSECTION_END: M014 -->










<!-- SUBSECTION_BEGIN: M015 -->
___
### ✅ M015. OrderDialogsViewModel.openBrokerDialog — M2 partial
**Статус:** исправлено (2026-11-08, M015-A1b+A2, `6a4a597` + `5a2a4e3`).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `OrderDialogsViewModel.openBrokerDialog` (75–119) содержит orchestration:
- `cardsRepository.identities.first().find { it.ticker == ticker }` (78) — findCard по ticker в VM;
- `accountSelectionRepository.selected(BrokerName.TINVEST).first()` (82) — read selected account в VM;
- далее `loadCardAccountsUseCase.execute(...)` + `_state.update`.

Часть orchestration (getAccounts + savedFallback) уже в `LoadCardAccountsUseCase`; findCard + selected fallback — в VM. `loadDialogAccounts` (181–207) — **thin delegate** на `LoadCardAccountsUseCase` (M2-fixed in 12a); `saveBrokerSettings` — thin delegate на `SaveCardSettingsUseCase`; M2 в них нет.

**Нарушенное правило:** M2 — business-orchestration (findCard + selected fallback) в VM, не в UseCase.

**Clean-альтернатива:** `OpenCardDialogUseCase(ticker): AppResult<CardAccountsResult>` — findCard + selectedFallback + `LoadCardAccountsUseCase.execute(...)` в domain. Плюсы: VM тонкая. Минусы: +1 usecase (обёртка над `LoadCardAccountsUseCase`).

**MVVM-альтернатива:** то же — orchestration в UseCase, VM — state + delegation.

**Что сделано:** `OpenCardDialogUseCase` (`5d8462c`): findCard по ticker из
`CardsRepository.identities` + selected fallback из
`AccountSelectionRepository.selected(TINVEST)` + `sandboxMode` из
`SandboxPreferencesRepository` + делегирование в `LoadCardAccountsUseCase`.
`OpenCardDialogOutcome` (`6a4a597`): sealed Success/Failure — failure несёт
`broker` (иначе VM не могла 1:1 выставить `selectedBroker`). `OrderDialogsViewModel.openBrokerDialog`
→ thin (`5a2a4e3`); 6 тестов переведены на мок `openCardDialogUseCase`;
`loadCardAccountsUseCase` остался для `onBrokerSelected` / `saveBrokerSettings`.
`OpenCardDialogUseCaseTest` (4 кейса: success / no card + fallback / failure
passthrough / cancellation).

**Ссылка на задачу:** `### OpenCardDialogUseCase (M2 partial + M013-таблица)` (ROADMAP, follow-up).

**Сводный отчёт, 2026-11-06:** нет (создана 2026-11-08).
<!-- SUBSECTION_END: M015 -->










<!-- SUBSECTION_BEGIN: M017 -->
___
### ✅ M017. OrderFormViewModel.buildPlaceOrderRequest — presentation собирает domain-модель
**Статус:** исправлено (2026-11-08, M017, `4ee4174`).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `OrderFormViewModel.buildPlaceOrderRequest(state: OrderFormState): PlaceOrderRequest?` (159) — private-функция VM собирает domain-модель `PlaceOrderRequest` (10 полей) из presentation-Form-полей. В 13d-C-A1 было зафиксировано в KDoc как 2c-фиксация (P11); пересмотр 2026-11-08 — это **проблема** (M1), нужна задача.

**Нарушенное правило:** M1 — presentation конструирует domain-модель (dependency rule обратно).

**Clean-альтернатива:** `BuildPlaceOrderRequestUseCase(state): AppResult<PlaceOrderRequest>` в `domain.usecases`; либо `PlaceOrderUseCase.execute(FormSnapshot, direction)` — build + orchestration в одном usecase. Плюсы: VM без domain-конструирования, usecase тестируется. Минусы: Form→domain→Form (presentation DTO в domain usecase) — компромисс; либо 20+ параметров (раздувание).

**MVVM-альтернатива:** то же — build в UseCase.

**Ссылка на задачу:** — (задача не заводится). Решение — **вариант D** (boundary-mapper `OrderFormState.toPlaceOrderRequest()` в `presentation/util/`); `BuildPlaceOrderRequestUseCase` (вариант A) отклонён — use case без бизнес-правил (только перекладывание 10 полей), нарушает принцип «use case — сценарий, не маппер».

**Что сделано:** `OrderFormViewModel.buildPlaceOrderRequest` удалён; маппинг
вынесен в `presentation/util/OrderFormStateMapper.kt`
(`fun OrderFormState.toPlaceOrderRequest(): PlaceOrderRequest?`, те же guards:
ticker fallback to `selectedInstrument`, `quantityAsLong`, `tscalpInstrumentId`).
`OrderFormViewModel.postOrder` вызывает `_state.value.toPlaceOrderRequest()`;
import `PlaceOrderRequest` из VM удалён. `OrderFormStateMapperTest` — 4 кейса
(full map / blank ticker fallback / null quantity / null instrument). Коммит
`4ee4174`.

**Сводный отчёт, 2026-11-06:** нет (создана 2026-11-08).
<!-- SUBSECTION_END: M017 -->










<!-- SUBSECTION_BEGIN: M006 -->
___
### ✅ M006. TInvestSettingsPanel — счета и orchestration в UI
**Статус:** исправлено (2026-11-06, PR #103).

**Серьёзность:** высоко.

**Live тест:** ✓

**Описание:** `SettingsScreen.kt` → `TInvestSettingsPanel` (172–525) — Composable сам вызывает `settingsViewModel.getAccountsResult` / `openSandboxAccountResult` / `closeSandboxAccountResult` в `LaunchedEffect` + `scope.launch`, а также **принимает business-решение**: при пустом `defaultAccountId` автоматически выбирает первый счёт и вызывает `saveDefaultAccountId`. Выбор счёта по умолчанию — не UI-обязанность.

**Нарушенное правило:** M2 — business-logic (выбор счёта, orchestration sandbox-операций) в UI.

**Clean-альтернатива:** `TInvestSettingsViewModel` с `UiState` (`availableAccounts`, `defaultAccountId`, `isRefreshing`), методами `loadAccounts()`, `openSandboxAccount()`, `closeSandboxAccount()`. UI — только рендер + `onClick`. Плюсы: тестируемо, логика вне UI. Минусы: VM-слой растёт.

**MVVM-альтернатива:** тоже VM с UiState (стандартный паттерн). Плюсы: единая точка состояния.

**Выгоды:** —.

**Что сделано:** 5 panel-VM (`TInvestSettingsViewModel`, `BcsSettingsViewModel`, `FinamSettingsViewModel`, `TradeSettingsViewModel`, `InfoSettingsViewModel`) + 6 usecase (`LoadBrokerAccountsUseCase`, `OpenSandboxAccountUseCase`, `CloseSandboxAccountUseCase`, `DisconnectBrokerUseCase`, `ToggleDebugFlagUseCase`, `SetSandboxModeUseCase`). `SettingsViewModel` — 361 → 31 строк (1 метод + 1 инжект); `SettingsUiState` удалён. `SettingsScreen.kt` — 5 панелей на panel-VM, оркестрация ушла из UI. 34 новых теста. Live-test sandbox 12/12. PR #103, merge `24497eb`.

**Ссылка на задачу:** `### ✅ Рефакторинг settings-VM: 6 VM-специфичных (A2, M006)` (`docs/roadmap/ROADMAP-ARCHIVE.md`, PR #103, merge `24497eb`).

**Сводный отчёт, 2026-10-31:** да (включена в отчёт от 2026-10-31).

**Сводный отчёт, 2026-11-06:** да.
<!-- SUBSECTION_END: M006 -->










<!-- SUBSECTION_BEGIN: M011 -->
___
### ✅ M011. OrdersListDialog — `as?` и расчёт в UI
**Статус:** исправлено (2026-11-06, PR #103).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `OrdersListDialog.kt` — `totalCost = (order as? Order.Stop)?.let { it.stopPrice * it.quantity }`. UI делает type-cast и расчёт `totalCost`. Для `Order.Regular` — `null`, значит totalCost не показывается (возможно, ошибка). Логика отображения + расчёт — не UI-обязанность.

**Нарушенное правило:** M1 — Composable делает расчёт и type-cast.

**Clean-альтернатива:** `Order` (sealed interface) — computed property `totalCost: Double? get() = when (this) { is Regular -> price * quantity; is Stop -> stopPrice * quantity }` (domain). UI — `order.totalCost` без cast. Плюсы: exhaustiveness, один расчёт. Минусы: домен-модель получает computed-поле.

**MVVM-альтернатива:** `totalCost` через VM (маппинг в `OrdersListUiState`). Плюсы: без `as?` в UI.

**Выгоды:** —.

**Что сделано:** 5 panel-VM (`TInvestSettingsViewModel`, `BcsSettingsViewModel`, `FinamSettingsViewModel`, `TradeSettingsViewModel`, `InfoSettingsViewModel`) + 6 usecase (`LoadBrokerAccountsUseCase`, `OpenSandboxAccountUseCase`, `CloseSandboxAccountUseCase`, `DisconnectBrokerUseCase`, `ToggleDebugFlagUseCase`, `SetSandboxModeUseCase`). `SettingsViewModel` — 361 → 31 строк (1 метод + 1 инжект); `SettingsUiState` удалён. `SettingsScreen.kt` — 5 панелей на panel-VM, оркестрация ушла из UI. 34 новых теста. Live-test sandbox 12/12. PR #103, merge `24497eb`.

**Ссылка на задачу:** `### ✅ Рефакторинг settings-VM: 6 VM-специфичных (A2, M006)` (`docs/roadmap/ROADMAP-ARCHIVE.md`, PR #103, merge `24497eb`).

**Сводный отчёт, 2026-10-31:** да (включена в отчёт от 2026-10-31).

**Сводный отчёт, 2026-11-06:** да.
<!-- SUBSECTION_END: M011 -->










<!-- SUBSECTION_BEGIN: M003 -->
___
### ✅ M003. PortfolioViewModel.payInSandbox — orchestration в VM
**Статус:** исправлено (2026-11-06, PR #101).

**Серьёзность:** средне.

**Live тест:** ✓

**Описание:** `PortfolioViewModel.payInSandbox` (275–343) выполняет сценарий «получить счета → выбрать → sandboxPayIn → обновить состояние» с разбором `AppResult` на каждом шаге. Это business-orchestration, а не presentation.

**Нарушенное правило:** M2 — business-logic в VM.

**Clean-альтернатива:** `PayInSandboxUseCase` (domain.usecases): `suspend fun execute(accountId: String, amount: SandboxMoney): AppResult<Unit>`. VM — вызов + `_uiState.update`. Плюсы: VM тонкий. Минусы: ~1.5 ч + тесты.

**MVVM-альтернатива:** тоже UseCase; тестируется без mock VM-инфраструктуры.

**Выгоды:** —.

**Что сделано:** `domain/usecases/PayInSandboxUseCase.kt` (новый) — `@Singleton @Inject constructor(accountsRepository, logger)`, `suspend fun execute(accountId, amount: SandboxMoney): AppResult<Unit>`; `CancellationException` rethrow, прочие `Exception` → `AppResult.Failure(AppError.Unknown(...))` + `logger.e`. `PortfolioViewModel.payInSandbox` — тонкий (~20 строк), `@Suppress("LongMethod")` снят. `PayInSandboxUseCaseTest` — 4 кейса (success / failure passthrough / exception → Unknown / `CancellationException` rethrow). Live-test sandbox (эмулятор Android 14): 4 пополнения × 4 счёта, `sandboxPayIn: success` → `loadPortfolio: 3 accounts`, balance +100 000 ₽. PR #101, merge `e9c592d`. Коммит `b120cd3`.

**Ссылка на задачу:** `### ✅ M003: payInSandbox → PayInSandboxUseCase` (`docs/roadmap/ROADMAP-ARCHIVE.md`, PR #101, merge `e9c592d`).

**Сводный отчёт, 2026-10-31:** да (включена в отчёт от 2026-10-31).

**Сводный отчёт, 2026-11-06:** да.
<!-- SUBSECTION_END: M003 -->










<!-- SUBSECTION_BEGIN: M004 -->
___
### ✅ M004. SettingsViewModel.initialize* — login-orchestration в VM
**Статус:** исправлено (2026-11-06, feature/initialize-broker-usecase, PR pending).

**Серьёзность:** средне.

**Live тест:** ✓

**Описание:** `SettingsViewModel.initializeTInvest/Bcs/Finam` выполняли login-orchestration в VM: save creds → `lifecycle.initialize` → `refreshInitializationState`.

**Нарушенное правило:** M2 — business-logic в VM.

**Clean-альтернатива:** `InitializeBrokerUseCase` (domain.usecases): `suspend fun execute(credentials: BrokerCredentials): AppResult<Boolean>`. VM — вызов + `_uiState.update`. Плюсы: VM тонкий, тестируемость.

**MVVM-альтернатива:** тоже UseCase.

**Что сделано:** `InitializeBrokerUseCase` (`domain/usecases/InitializeBrokerUseCase.kt`) — `execute(credentials): AppResult<Boolean>`, `when` по sealed `BrokerCredentials`, 3 private ветки. Порядок save/init сохранён 1:1 (TInvest: save→init; Bcs: init→save; Finam: save→init). `refreshInitializationState` — один раз после успеха. `SettingsViewModel.initializeTInvest/Bcs/Finam` → `connectTInvest/Bcs/Finam` (тонкие обёртки над private `connect()`); `_uiState.update` (statusMessage, isError, `*Connected`) — в VM. `withContext(Dispatchers.IO)` не добавлялся (M005 — исправлено ранее). Live-test через UI: TInvest sandbox, BCS refresh, Finam stub — PASS.

**Ссылка на задачу:** `### InitializeBrokerUseCase (M004 + M007)` (ROADMAP, feature/initialize-broker-usecase).

**Сводный отчёт, 2026-11-06:** да.
<!-- SUBSECTION_END: M004 -->










<!-- SUBSECTION_BEGIN: M005 -->
___
### ✅ M005. SettingsViewModel — Dispatchers.IO в VM
**Статус:** исправлено (2026-11-06, feature/initialize-broker-usecase, PR pending).

**Серьёзность:** средне.

**Live тест:** ✓

**Описание:** `SettingsViewModel.kt:246, 282, 317` — `withContext(Dispatchers.IO)` вокруг `brokerManager.getBroker(...)` в `initializeTInvest/Bcs/Finam`. VM дублировал диспатчеризацию.

**Нарушенное правило:** M3 — VM знает платформу (`Dispatchers.IO`).

**Clean-альтернатива:** убрать `withContext(Dispatchers.IO)` из VM — data-слой уже сам диспатчеризирует.

**MVVM-альтернатива:** то же — VM не управляет диспатчерами.

**Что сделано:** `initializeTInvest/Bcs/Finam` удалены вместе с M004 (перенесены в `InitializeBrokerUseCase`); `connectTInvest/Bcs/Finam` и private `connect()` не содержат `withContext(Dispatchers.IO)`. Все три `withContext` ушли вместе с телом старых методов. Побочный fix M004; отдельно не заявлялся. Проверено `grep -n "withContext\|Dispatchers.IO" app/src/main/.../SettingsViewModel.kt` — пусто.

**Ссылка на задачу:** `### InitializeBrokerUseCase (M004 + M007)` (ROADMAP, feature/initialize-broker-usecase).

**Сводный отчёт, 2026-11-06:** да.
<!-- SUBSECTION_END: M005 -->










<!-- SUBSECTION_BEGIN: M007 -->
___
### ✅ M007. SettingsScreen — login-orchestration в UI
**Статус:** исправлено (2026-11-06, feature/initialize-broker-usecase, PR pending).

**Серьёзность:** средне.

**Live тест:** ✓

**Описание:** `SettingsScreen.kt` — три панели (`TInvestSettingsPanel`, `BcsSettingsPanel`, `FinamSettingsPanel`) вызывали `settingsViewModel.initialize*` из `scope.launch { ... }` в `onClick` кнопки «Подключиться». UI оркестрировал login-сценарий: старт → success/failure → `statusMessage`.

**Нарушенное правило:** M2 — orchestration в UI.

**Clean-альтернатива:** VM-метод `connect*(...)`, который сам вызывает use case, обновляет `*Connected` в `UiState`, выставляет `statusMessage`. UI — `onClick { vm.connect*(...) }`.

**MVVM-альтернатива:** тоже VM-метод.

**Что сделано:** `SettingsScreen` — 3 callsites `settingsViewModel.initializeTInvest/Bcs/Finam` → `connectTInvest/Bcs/Finam`; UI не оркестрирует login-сценарий. Все `_uiState.update` (statusMessage / isError / `*Connected`) — в `SettingsViewModel`. Тексты, кнопки, поля UI не менялись. Live-test: TInvest / BCS / Finam через «Подключиться» — PASS.

**Ссылка на задачу:** `### InitializeBrokerUseCase (M004 + M007)` (ROADMAP, feature/initialize-broker-usecase).

**Сводный отчёт, 2026-11-06:** да.
<!-- SUBSECTION_END: M007 -->










<!-- SUBSECTION_BEGIN: M008 -->
___
### ✅ M008. OrdersScreen — расчёт pairExecPrice/pairedOrderType в Composable
**Статус:** исправлено (2026-11-05, merge `34ffed5`, PR #94).

**Серьёзность:** средне.

**Live тест:** -.

**Описание:** `OrdersScreen.kt` — расчёт `pairExecPrice` / `pairedOrderType` для парной торговли был business-логикой в Composable (`PairOrderMapper.map(...)`). После пакета C расчёт вынесен в **pure-функцию** `calculatePairExec(uiState)` (вне Composable). Остаток — перенос в `OrdersViewModel` (в `OrdersUiState` готовые поля).

**Нарушенное правило:** M1 — Composable делает работу VM/UseCase (расчёт).

**Clean-альтернатива:** `pairExecPrice`/`pairedOrderType` — в `OrdersUiState` (вычисляются в VM при `onOrderTypeChanged`/`onQuantityChanged`/`setPairTradingEnabled`). Composable читает готовые поля. Плюсы: UI — только рендер. Минусы: VM добавляет 2 поля.

**MVVM-альтернатива:** то же — вычисления в VM. Плюсы: тестируемо без Compose.

**Выгоды (по 2c):** —.

**Что сделано:** пакет C (PR #92) — `pairExecPrice`/`pairedOrderType` расчёт вынесен из Composable в pure `calculatePairExec(uiState)` (приватная функция в `OrdersScreen.kt`, но не в теле Composable). `OrdersConfirmDialogSection` вызывает `calculatePairExec` — логика вне UI-дерева. Полностью M008 не закрыт: расчёт всё ещё в presentation-слое. **Полностью закрыто (2026-11-05, merge `34ffed5`, PR #94):** `CalculatePairExecUseCase` в `domain/usecases` (`PairExecInput` / `PairExecResult` / `execute(input)`), `OrdersUiState` +`pairExecPrice` / `pairedOrderType`, `OrdersViewModel` +13 триггеров `updatePairExecDetails()`, `calculatePairExec` удалён из `OrdersPairTrading.kt`, `CalculatePairExecUseCaseTest` (5 тестов).

**Ссылка на задачу:** `### ✅ M008: перенос pairExec в OrdersViewModel` (ROADMAP, PR #94).

**Сводный отчёт, 2026-10-31:** да (включена в отчёт от 2026-10-31).
<!-- SUBSECTION_END: M008 -->










<!-- SUBSECTION_BEGIN: M002 -->
___
### ✅ M002. OrdersViewModel.postOrder — orchestration в VM
**Статус:** исправлено (2026-11-03, PR #89, `dev`).

**Серьёзность:** высоко.

**Live тест:** ✓

**Описание:** `OrdersViewModel.postOrder` (456–657, 200 строк) выполняет полный сценарий: выбор брокера/счёта/инструмента из `_uiState`, `checkTradeAvailability`, разбор `TradeCheckResult` (Success/InsufficientFunds/Error), `prepareOrderRequest.prepare`, `postOrder`, обновление статусов. Это orchestration торговой операции — business-логика, а не presentation.

**Нарушенное правило:** M2 — business-logic (orchestration торгового сценария) в VM вместо UseCase.

**Clean-альтернатива:** `PostOrderUseCase` (domain.usecases): принимает `BrokerOrderRequest` + `accountId` + `brokerName`, возвращает `AppResult<PostOrderResult>`. VM — вызов use case + `_uiState.update`. Плюсы: VM тонкий, логика тестируется без mock-инфраструктуры. Минусы: новый use case + тесты (~2–3 ч).

**MVVM-альтернатива:** тоже `PostOrderUseCase`; VM — «нажатие → use case → обновление UI». Плюсы: тестируемость, переиспользование для BCS/Finam. Минусы: миграция `OrdersScreen` callsites.

**Выгоды:** —.

**Ссылка на задачу:** `### ✅ OrdersViewModel: миграция на PortfolioSheet` (PR #89, `dev`, 2026-11-03).

**Расширено (13d, 2026-11-07, `a793c38` + `9954877`):** postOrder
orchestration fully removed from VM (13d-C-A1 + A2):

- `PlaceOrderUseCase` (`a793c38`) — full orchestration: cards / account /
  sandbox from domain repositories (`CardsRepository.identities`,
  `AccountSelectionRepository.selected(TINVEST)`,
  `SandboxPreferencesRepository.isSandboxMode()`); `postOrder` →
  `PostOrderUseCase.execute`; `freeBalance` from
  `PortfolioRepository.getBalanceResult`. No cross-VM parameters.
- `OrderFormViewModel` (`9954877`) — `postOrder` / `buildPlaceOrderRequest` /
  `handlePostOrderSuccess` / `handlePostOrderInsufficient` /
  `handlePostOrderError` / `formatPostOrderSuccess`. VM thin:
  `PlaceOrderUseCase.execute + _state.update`. `formatPostOrderSuccess`
  (M1 formatting) — отдельная задача `### M001`.
- `refreshLastSelectedInstruments` не вызывается из Form (Coord реактивен
  через `PositionFlowManager.sheet`, 2c fixation).

**Сводный отчёт, 2026-10-31:** да (включена в отчёт от 2026-10-31).
<!-- SUBSECTION_END: M002 -->










<!-- SUBSECTION_BEGIN: M009 -->
___
### ✅ M009. OrdersScreen — состояние торговой операции в UI
**Статус:** исправлено (2026-11-03, PR #89, `dev`).

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `OrdersScreen.kt:56–57` — `var showConfirmDialog by remember { mutableStateOf(false) }` и `var pendingDirection by remember { mutableStateOf("") }`. Флаг «диалог подтверждения открыт» + направление pending-заявки — состояние торговой операции, а не UI. При recomposition/конфигурационном изменении теряется (не `rememberSaveable`, не VM).

**Нарушенное правило:** M5 — состояние в UI вместо `UiState` VM.

**Clean-альтернатива:** `showConfirmDialog` + `pendingDirection` — в `OrdersUiState`, VM-методы `onBuyClick()`/`onSellClick()` сразу выставляют `showConfirmDialog = true` + `pendingDirection`, `onConfirm()` — отправляет + сбрасывает. Composable — рендер по state. Плюсы: переживает config change, тестируемо. Минусы: —.

**MVVM-альтернатива:** то же — состояние в VM. Плюсы: standard MVVM.

**Выгоды:** —.

**Ссылка на задачу:** `### ✅ OrdersViewModel: миграция на PortfolioSheet` (PR #89, `dev`, 2026-11-03).

**Сводный отчёт, 2026-10-31:** да (включена в отчёт от 2026-10-31).
<!-- SUBSECTION_END: M009 -->










<!-- SUBSECTION_BEGIN: M010 -->
___
### ✅ M010. OrdersScreen — UI читает VM через метод, не через state
**Статус:** исправлено (2026-11-03, PR #89, `dev`).

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `OrdersScreen.kt` — `if (viewModel.isConfirmOrdersEnabled()) { pendingDirection = "..."; showConfirmDialog = true } else { viewModel.onBuyClick() }`. Метод `isConfirmOrdersEnabled(): Boolean = _uiState.value.confirmOrdersEnabled` — прокси к `uiState.confirmOrdersEnabled`. UI читает поле через метод, а не через `uiState`, из-за чего при recomposition используется снимок `_uiState.value`, а не наблюдаемое значение.

**Нарушенное правило:** M1 — UI работает с VM-методом вместо state (нарушение data flow).

**Clean-альтернатива:** UI читает `uiState.confirmOrdersEnabled`. Метод `isConfirmOrdersEnabled()` удаляется. Плюсы: однонаправленный data flow. Минусы: — (~5 мин).

**MVVM-альтернатива:** то же — `state → UI`.

**Выгоды:** —.

**Ссылка на задачу:** `### ✅ OrdersViewModel: миграция на PortfolioSheet` (PR #89, `dev`, 2026-11-03).

**Сводный отчёт, 2026-10-31:** да (включена в отчёт от 2026-10-31).
<!-- SUBSECTION_END: M010 -->










<!-- SUBSECTION_BEGIN: M012 -->
___
### ✅ M012. OrdersViewModel — MutableStateFlow-поля вместо UiState
**Статус:** исправлено (2026-11-03, PR #89, `dev`).

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `OrdersViewModel.kt:1088–1110` + `OrdersScreen.kt:718–742` — 4 поля (`selectedSearchBroker`, `showSearchBrokerDialog`, `selectedPairSearchBroker`, `showPairSearchBrokerDialog`) живут как отдельные `MutableStateFlow` в VM, а UI читает их через `viewModel.X.value` (`.value` в Composable — bypass `collectAsState`). Это раздвоение состояния: часть в `_uiState`, часть — в отдельных flow.

**Нарушенное правило:** M5 — несколько `StateFlow` / дублирование состояния; плюс UI читает `.value` в обход observable.

**Clean-альтернатива:** 4 поля → в `OrdersUiState`; VM-методы обновляют через `_uiState.update { it.copy(...) }`. UI — `uiState.showSearchBrokerDialog` из `collectAsState`. Плюсы: единый `UiState`, observable. Минусы: — (~30 мин).

**MVVM-альтернатива:** то же — один `StateFlow<OrdersUiState>`.

**Выгоды:** —.

**Ссылка на задачу:** `### ✅ OrdersViewModel: миграция на PortfolioSheet` (PR #89, `dev`, 2026-11-03).

**Сводный отчёт, 2026-10-31:** да (включена в отчёт от 2026-10-31).

---
<!-- SUBSECTION_END: M012 -->





<!-- SUBSECTION_BEGIN: Н95 -->
___
### Н95 ✅ Н95. OrderFormViewModel — @Suppress("TooManyFunctions")

**Статус:** исправлено (2026-11-15, PR-4b Phase 6.1-7.2, feature/orderform-split-c2-p4b).

**Серьёзность:** средне.

**Live тест:** -.

**Описание:** `OrderFormViewModel` — 27 fun (16 public + 1 override `onCleared` + 10 private) в 544 строках, `@Suppress("TooManyFunctions")` на строке 75 (порог detekt = 11). Extract-helpers не даёт снятия: pure top-level extraction невозможен (все 10 private мутируют `_state` / `_status` / async), merge 3× `handlePostOrder*` + `updateTradeDetails`/`updatePairExecDetails` = −3 fun → 24 ≫ 11. Split VM отклонён 2c 2026-11-07 (KDoc 13d-B), но после закрытия M001 (`TradeDetailsFormat.kt`), M017 (`OrderFormStateMapper.kt`), A2, 13d обоснование устарело: формат/мапперы вынесены, cross-state reads в VM минимальны (4 поля).

**Нарушенное правило Clean:** `@Suppress("TooManyFunctions")` как постоянный маркер долга (god-класс 544 строки / 27 fun).

**Clean-альтернатива:** split на 4 sub-VM (`OrderInstrumentViewModel`, `OrderInputViewModel`, `OrderTradeDetailsViewModel`, `OrderExecutionViewModel`), coord → lifecycle-контейнер ≤ 2 fun, `@Suppress` снят. Плюсы: coord ≤ 11 fun, M5-инвариант усилен, god-класс устранён. Минусы: ~12–16 ч + 6 UI-файлов переписать (API `formState` → 4 sub-state) + test infra (Roborazzi, property-based, Extended behavioural).

**Выгоды (по 2c):** работает сейчас, god-класс ограничен presentation-слоем, cross-state reads в VM минимальны (4 поля). `@Suppress` остаётся до закрытия C2 umbrella — reduce-task закрыта как accepted (`cf911ae8`), C2 — целевая задача с явным триггером.

**Что сделано:** split выполнен (PR-4b, feature/orderform-split-c2-p4b): Coord `OrderFormViewModel` удалён (Phase 6.1, `051b7a50`); `OrderFormState` / `OrderFormStateMapper` удалены (Phase 7.1, `844bf1ba`); 4 sub-VM (`OrderInstrumentViewModel` / `OrderInputViewModel` / `OrderTradeDetailsViewModel` / `OrderExecutionViewModel`) + `OrderFormMediator` (`@ActivityRetainedScoped`) + `OrderMediatorViewModel` (UI-facing wrapper); UI-orchestration (`OrdersScreen` 4 `LaunchedEffect`: checkApiInit / restore / reactive recalc / reactive save); `@Suppress("TooManyFunctions")` исчез вместе с файлом; Roborazzi baseline (8 PNG) + property-тесты + Extended behavioural 20 зелёные. `### C2: SaveFormStateUseCase primitives (M4/M7 fix)` — follow-up задача (закрывает M4/M7 pre-existing; Н95 не требует).

**MVVM-альтернатива:** — (не нарушение MVVM).

**Ссылка на задачу:** `### OrderFormViewModel: split на sub-VM (C2, снятие @Suppress) + 2 test infra prereqs` (ROADMAP, `e1f62ffc`; merge pending 8.2 → `docs/roadmap/ROADMAP-ARCHIVE.md` → `### ✅ OrderFormViewModel: split`); reduce-task closed accepted — `docs/roadmap/ROADMAP-ARCHIVE.md` → `### ✅ OrderFormViewModel: @Suppress accepted (2c)` (`cf911ae8`). Связано: M001 (закрыт PR #111), M017 (закрыт `4ee4174`), M013 (закрыт PR #110).


<!-- SUBSECTION_END: Н95 -->

<!-- SUBSECTION_BEGIN: Н94 -->
___
### Н94 ✅ Н94. God-функции Compose под `@Suppress` (частично)

**Статус:** частично исправлено (2026-11-04, merge `5d4e867`, PR #92; split `5bb9184`, PR #93; InstrumentColors `e28af70`, PR #95; пакеты A+B+C).

**Серьёзность:** средне.

**Live тест:** -.

**Описание:** 10 Composable-функций несли `@Suppress("LongMethod")` / `@Suppress("LongMethod", "CyclomaticComplexMethod")` как **постоянный** маркер долга. После A+B+C: `BrokerAccountDialog` — `@Suppress` снят (extract `BrokerSelector` + `AccountSelector`); `OrdersScreen` — `CyclomaticComplexMethod` снят (complexity 7 < 14), `LongMethod` остаётся (221 > 60); `OrdersScreen.kt` — `@file:Suppress("TooManyFunctions")` добавлен в PR #92 (18 функций, было 3), снят в PR #93 (split на 5 файлов, `OrdersScreen.kt` — 3 функции); `OrdersPriceFields` (63) и `OrdersQuantityRow` (62) — новые `@Suppress("LongMethod")` после extract. Осталось 10 `@Suppress` + `@file:Suppress`: `SettingsScreen` ×4, `OrdersScreen` ×1 (`LongMethod`), `OrdersPriceFields` ×1 (`LongMethod`), `OrdersQuantityRow` ×1 (`LongMethod`), `OrdersPairSection` ×1 (`LongMethod` + `Cyclomatic`), `InstrumentSearchField` ×1 (`LongMethod` + `Cyclomatic`), `PortfolioScreen` ×1 (`LongMethod` + `Cyclomatic`), `AssetPositionCard` ×3 (`AssetPositionCard` + `AssetCardHeader` + `AssetCardLowerSection`), `@file:Suppress("TooManyFunctions")` — 1.

**Нарушенное правило:** SRP — Composable-функции делают слишком много. Формально — `LongMethod`/`CyclomaticComplexMethod`.

**Clean-альтернатива:** декомпозировать до < 60 строк / < 14 complexity. Плюсы: читаемость, снятие `@Suppress`. Минусы: ~6 ч.

**MVVM-альтернатива:** UI-композиция (Extract composables + один UiState) — часть того же рефакторинга.

**Выгоды (по 2c):** функции работают; `@Suppress` — осознанный маркер, не «молчаливый костыль» (KDoc-пометка «Н94» на каждой). Extract A+B+C дал выгоду (читаемость, дублирование 40 строк убрано; `OrdersScreen` 662→221); split (PR #93) — `OrdersScreen.kt` 1043→289 строк, 3 функции, `@file:Suppress("TooManyFunctions")` снят. Но `@Suppress` остаются — дальнейшая декомпозиция до < 60 — задача `### ✅ UI: OrdersScreen decompose (пакет C, частично)` (сделано, 662→221) + `#### Compose-экраны` / `#### Compose-компоненты` (остаток).

**Что сделано:** пакет A — `SearchResultItem` (убрано 40 строк дублирования); пакет B — `AssetCardHeader` + `AssetCardLowerSection`, `SwipeActionButtons` (`BoxScope`) + `DraggableCardContent`, `PortfolioAccountBlock`, `BrokerSelector` + `AccountSelector`. `BrokerAccountDialog` — `@Suppress` снят. `@Suppress` возвращён на `AssetPositionCard` / `AssetCardHeader` / `AssetCardLowerSection` (`LongMethod` 68–72 > 60) и `PortfolioScreen` (`LongMethod` + `CyclomaticComplexMethod` 16 > 14) с KDoc-пометкой. Merge `aa06690`, PR #91. Пакет C — `OrdersScreen`: 662 → 221 строк, 10 подкомпонентов, `CyclomaticComplexMethod` снят (7 < 14), `LongMethod` остаётся (KDoc); `@file:Suppress("TooManyFunctions")` (18 функций, было 3); `calculatePairExec` — pure-функция (M008 частично); `OrdersPriceFields` (63) и `OrdersQuantityRow` (62) — новые `@Suppress("LongMethod")`. Merge `5d4e867`, PR #92. Split (PR #93) — `OrdersScreen.kt` разнесён на 5 файлов (`OrdersBrokerDialogs`, `OrdersConfirmDialog`, `OrdersBottomButtons`, `OrdersPairTrading`, `OrdersSearch`), cross-file функции `internal`; `@file:Suppress("TooManyFunctions")` снят; `@Suppress("MagicNumber")` на `getInstrumentTypeColor` (5 цветов — вынос в `presentation/theme` отдельной задачей); `PRICE_FIELDS_ANIMATION_MS = 200` const. Merge `5bb9184`, PR #93. InstrumentColors (PR #95, merge `e28af70`): палитра 5 цветов `InstrumentType` в `theme/InstrumentColors.kt`; `@Suppress("MagicNumber")` на `getInstrumentTypeColor` снят; `OrdersSearch.kt` — локальная функция + KDoc + `@Suppress` удалены, импорт из theme; `AssetPositionCard.kt` — локальный `when` → `getInstrumentTypeColor(...)`, `InstrumentType`-импорт убран; fix `30b82ad` (unused import).

**Что осталось (вне A+B+C):**
- `InstrumentSearchField` (113 строк, `LongMethod` + `CyclomaticComplexMethod`) — пакет A, `@Suppress` остаётся.
- `PortfolioScreen` (139, `LongMethod` + `CyclomaticComplexMethod`) — пакет B, `@Suppress` остаётся.
- `AssetPositionCard` (70) / `AssetCardHeader` (68) / `AssetCardLowerSection` (72) — пакет B, `LongMethod` остаётся.
- `OrdersScreen` (221, `LongMethod`) — пакет C, `@Suppress` остаётся (KDoc).
- `OrdersPriceFields` (63) / `OrdersQuantityRow` (62) — пакет C, `LongMethod` остаётся.
- `OrdersPairSection` (~130, `LongMethod` + `CyclomaticComplexMethod`) — пакет C, `@Suppress` остаётся.
- `SettingsScreen` ×4 (`SettingsScreen` 65, `TInvestSettingsPanel` 356, `BcsSettingsPanel` 201, `FinamSettingsPanel` 164) — не трогали.
- BCS/Finam/TInvest панели — заглушки, переписываются в `### Реализация BCS/Finam Broker API`.
- `AssetPositionCard` — hex-цвета «действие/P&L» (`Color(0xFFC62828)` «удалить», `Color(0xFF2E7D32)` «вверх», `Color(0x2200C853)` / `Color(0x22FF1744)` полупрозрачные фоны) — отдельная семантика (не `@Suppress`), вынос в `### UI: ActionColors` (не заведена).

**Ссылка на задачу:** `### ✅ UI: SearchResultItem extract (пакет A, частично)`; `### ✅ UI: extract composables — пакет B (частично)`; `### ✅ UI: OrdersScreen decompose (пакет C, частично)`; `### Рефакторинг архитектурного долга (detekt-группа 3)` → `#### Compose-экраны` / `#### Compose-компоненты` (остаток); `### ✅ OrdersScreen.kt: split helper-composable files` (PR #93); `### ✅ UI: InstrumentColors в presentation/theme` (PR #95).

**Сводный отчёт, 2026-10-31:** да (включена в отчёт от 2026-10-31).



<!-- SUBSECTION_END: Н94 -->

<!-- SUBSECTION_BEGIN: Н24 -->
___
### Н24 ✅ Н24. Дублирование обогащения в двух VM

**Статус:** исправлено (2026-10-29, PR #69).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `OrdersViewModel.updatePositionPnl` и `PortfolioViewModel.updatePortfolioItem` реализуют одну логику (обогащение `PositionStreamItem` → `PortfolioPosition` через `toPortfolioPosition` / `calculateProfitPercent`), но с разной реализацией (в одной — `index == -1` → добавить, в другой — аналогично, но с доп. полями `isin`/`classCode`). Дублирование, рассинхронизация.

**Нарушенное правило:** Не строго Clean — DRY / Single Source of Truth для конверсии.

**Clean-альтернатива:** вынести обогащение в `PositionFlowManager` (domain.api.managers) — эмитит готовые `PortfolioPosition`, VM не парсит `PositionStreamItem`. Плюсы: одна точка правды, VM — тонкие. Минусы: ~2 ч, переписать 2 VM + менеджер.

**Выгоды:** —

**Что сделано:** SSoT-helper `PortfolioPosition.mergeWith(item: PositionStreamItem)` в `domain/models/Portfolio.kt` (канон по `PortfolioViewModel`): `quantity` из события, `currentPrice`/`averagePrice`/`pointValue` non-null fallback, `totalValue = currentPrice * quantity`, `profit` = `expectedYield`, `profitPercent` через `calculateProfitPercent`, `isin`/`classCode` — `ifBlank { old }`. Оба VM используют `old.mergeWith(item)`. Политики (фильтр partial в Orders, без фильтра в Portfolio) — presentation.

**Отклонение от плана:** вместо `portfolioFlow` в `PositionFlowManager` (в плане) — SSoT-helper в domain-модели. Причина: `PortfolioPosition` содержит presentation-поля (`totalValue`, `profitPercent`), эмитить их из domain-менеджера — углубление clean-audit Н5, не решение Н24. `mergeWith` в domain-модели сохраняет SSoT без нарушения dependency rule.

**Ссылка на задачу:** — (не заведена). Связано с `### Рефакторинг ViewModel-слоя`.

<!-- SUBSECTION_END: Н24 -->

<!-- SUBSECTION_BEGIN: Н1 -->
___
### Н1 ✅ Н1. Presentation конструирует PortfolioPosition

**Статус:** исправлено (2026-10-29, PR #68).

**Серьёзность:** высоко.

**Live тест:** -

**Описание:** `OrdersScreen.kt` (два места: основной инструмент ~353, парный ~580) вручную вызывает `PortfolioPosition(...)` с полями `profit = null`, `profitPercent = null` для инструмента, которого нет в портфеле. По Clean presentation имеет право читать domain-модели, но не создавать их — правила конструирования принадлежат domain. После #9 `PortfolioPosition` — именно domain-модель.

**Нарушенное правило:** Dependency rule — presentation не конструирует domain-модели в обход domain-логики.

**Clean-альтернатива:** фабрика во ViewModel (или domain-функция `emptyPortfolioPosition(instrument, currentPrice)`) — UI получает готовую модель. Плюсы: правила конструирования в domain/presentation-фабрике. Минусы: ~30 мин правок + правка `OrdersUiState`.

**Выгоды:** —

**Что сделано:** фабрика `emptyPortfolioPosition(instrument, currentPrice, pointValue, instrumentType)` в `domain/models/Portfolio.kt`; `OrdersScreen` — 2 raw `PortfolioPosition(...)` → вызов фабрики. `toPortfolioPosition` (data-конвертер) не тронут.

**Ссылка на задачу:** — (не заведена).

<!-- SUBSECTION_END: Н1 -->

<!-- SUBSECTION_BEGIN: Н4 -->
___
### Н4 ✅ Н4. UI конвертирует String → domain-модель

**Статус:** исправлено (2026-10-28, растворён в Split order, коммит `ec9c7d5`).

**Серьёзность:** средне.

**Live тест:** -

**Описание:** `OrdersListDialog.kt` в `OrderCard` передаёт `orderType = TradePhrases.stringToOrderType(order.type)`. Конверсия «сырое значение из data → domain-модель» выполняется в presentation.

**Нарушенное правило:** Presentation не должна маппить сырые данные в domain-модели — это забота data-слоя (маппер).

**Clean-альтернатива:** `OrderListItem.type` → `OrderTypeSelection` в маппере `TInvestOrdersMapper` (data). UI получает готовый `OrderTypeSelection`. Плюсы: presentation без маппинга. Минусы: зависит от `### Split order domain into sealed Regular/Stop` (там переделка модели).

**Выгоды:** —

**Ссылка на задачу:** `### Split order domain into sealed Regular/Stop` (в его рамках решается).


<!-- SUBSECTION_END: Н4 -->

<!-- SUBSECTION_BEGIN: Н21 -->
___
### Н21 ✅ Н21. ViewModel → data.repository.*

**Статус:** исправлено (PR #57, `dev`, 2026-10-27).

**Серьёзность:** критично.

**Live тест:** -

**Описание:** три ViewModel (`OrdersViewModel`, `PortfolioViewModel`, `SettingsViewModel`) инжектят `InvestRepository`, `InstrumentRepository`, `SearchCache`, `SettingsRepository`, `TradingStateRepository` из `data.repository.*` напрямую. По Clean presentation должна зависеть от domain-интерфейса, а data — реализовывать его.

**Уточнение (2026-10-27):** по факту потребителей 5, не 3 —
добавились `OrdersListViewModel` (`SettingsRepository`) и
`TScalpApplication` (`SettingsRepository` через `@Inject lateinit var`).
Также удалён мёртвый `InstrumentRepository.refreshInstrument` (0 callsites).

**Нарушенное правило:** Dependency rule — presentation не должна знать про data (зависимость наружу).

**Clean-альтернатива:** `domain.api.repository.*` — по одному интерфейсу на репозиторий (для `InvestRepository` частично сделано в `### Разбивка InvestRepository по доменным областям`, но не для остальных 4). VM инжектит интерфейс, data реализует через `@Binds`. Плюсы: Clean-дисциплина, KMP-совместимость, фейки в тестах. Минусы: переезд 4 репозиториев (~2–3 ч) + правка DI.

**Выгоды:** —

**Ссылка на задачу:** `### Разбивка InvestRepository по доменным
областям` (PR #56, `dev`) + `### H21b. Domain-интерфейсы для 4
репозиториев` (PR #57, `dev`).

---

<!-- SUBSECTION_END: Н21 -->

<!-- SUBSECTION_BEGIN: Н23 -->
___
### Н23 ✅ Н23. ViewModel → data.orchestrator.DebugConfig

**Статус:** исправлено (PR #59, `dev`, 2026-10-28).
**Серьёзность:** средне.

**Live тест:** ✓

**Описание:** `SettingsViewModel` импортирует `data.orchestrator.DebugConfig`, читает/пишет `disableGrpc`, `disableWs`, `forceRest` через `toggleGrpc/toggleWs/toggleRest`. Debug-флаги транспорта в presentation.

**Нарушенное правило:** Dependency rule — presentation не должна знать про `data.orchestrator`.

**Clean-альтернатива:** `domain.api.DebugFlagsRepository` с геттерами/сеттерами; реализация — `data.orchestrator.DebugConfig` за интерфейсом. Плюсы: presentation → domain.api. Минусы: ~30 мин правок.

**Выгоды:** —

**Анализ L5-Н38:** подтверждается — `DebugConfig` протекает в `PriceFlowManager` (L5), не только в `SettingsViewModel` (L1). Нарушение шире, чем presentation.

**Ссылка на задачу:** — (не заведена). Связано с закрытыми `### Debug-тумблеры` и `### DebugConfig: только для debug-сборок` (функционал работает; переезд API — отдельная задача).

---

<!-- SUBSECTION_END: Н23 -->

<!-- SUBSECTION_BEGIN: Н22 -->
___
### Н22 ✅ Н22. ViewModel → di.BrokerManager

**Статус:** исправлено (PR #60, `dev`, 2026-10-28).

**Серьёзность:** критично.

**Live тест:** -

**Описание:** все потребители (`OrdersViewModel`, `PortfolioViewModel`, `SettingsViewModel`, `TScalpApplication`, `domain/api/managers/*`, `data/repository/*`) инжектят `com.gitlab.biomorf.tscalp.di.BrokerManager` напрямую. Вызывают `getAllBrokers()`, `getBroker(name)?.lifecycle?.initialize(...)`, `getAvailableBrokerNames()`, `refreshInitializationState()`, `getDefaultBroker()`. `BrokerManager` — точка сборки брокеров, живёт в `di/`.

**Нарушенное правило:** Dependency rule — presentation зависит от `di` (сборочный слой).

**Clean-альтернатива:** `domain.api.BrokerRegistry` — интерфейс с теми же методами (`getAllBrokers`, `getBroker`, `getAvailableBrokerNames`, `getDefaultBroker`, `refreshInitializationState`). Реализация — `data/registry/BrokerRegistryImpl` или `di/BrokerRegistryImpl`, `@Binds` в Hilt. VM инжектит `BrokerRegistry`. Плюсы: presentation → domain.api; `BrokerRegistry` в KMP-модуле. Минусы: переезд + переименование `BrokerManager` (~1–2 ч) + правка 3 VM + тестов.

**Выгоды:** —


**Анализ L7-Н29:** подтверждается — анализ L7 показал, что `BrokerManager` — domain-сервис в `di/`, нарушение шире, чем только VM: потребители включают `domain/api/managers/*` и `data/repository/*`. См. L7-Н29.

**Анализ L7-part1:** подтверждается — полный L7 показал 3 находки (Н35–Н37); `BrokerManager` — единственная утечка `di/` наружу (10 файлов). Дизайн `BrokerRegistry` уточняется: `BrokerRegistryImpl(brokers: Set<BrokerApi>)`, порядок из `BrokerName.entries`, `refreshInitializationState` — открытый вопрос (см. L7-Н29).

**Анализ L5-Н40:** подтверждается — L5 (3 менеджера) показал, что `di.BrokerManager` протекает шире: 3 VM (L1) + 3 менеджера (L5) + 3 репозитория (L6) + 1 app (L8) = 10 потребителей.

**Анализ L8-Н51:** подтверждается — `refreshInitializationState` вызывается из `TScalpApplication` 2 раза (`restoreTInvest`, `restoreBcs`), всего 6 callsites (2 app + 4 SettingsViewModel). Усиливает открытый вопрос H22.

**Ссылка на задачу:** `### H22. BrokerRegistry` (PR #60, `dev`).

<!-- SUBSECTION_END: Н22 -->

<!-- SUBSECTION_BEGIN: Н3 -->
___
### Н3 ✅ Н3. OrdersListViewModel в ui.components

**Статус:** исправлено (PR #58, `dev`, 2026-10-27). `OrdersListViewModel`
в `presentation/screens/orders/`, package обновлён.

**Серьёзность:** низко.

**Live тест:** -

**Описание:** `OrdersListViewModel` живёт в `ui.components` (рядом с `OrdersListDialog.kt`, импортируется оттуда). Остальные ViewModel — в `presentation/screens/*/`. Нарушение конвенции проекта.

**Нарушенное правило:** Не Clean в строгом смысле — архитектурная конвенция пакетов.

**Clean-альтернатива:** `presentation/screens/orders/OrdersListViewModel.kt` (рядом со своим экраном). Плюсы: консистентность. Минусы: `git mv` + правка импорта в 1 файле (~10 мин).

**Выгоды:** —

**Ссылка на задачу:** `### H3. Move OrdersListViewModel to presentation/screens/orders/` (PR #58, `dev`).
<!-- SUBSECTION_END: Н3 -->

<!-- SECTION_END: clean-audit-L1-ARCHIVE -->
