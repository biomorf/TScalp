<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: diagnostics -->










<!-- SUBSECTION_BEGIN: l-diagnostics-85 -->
<a id="l-diagnostics-85"></a>
### [AGENTS-LESSONS] l-diagnostics-85 ktlint `multiline-expression-wrapping` — вынести subject в `val`
**Правило (а) — внешний `when`.** `when (val X = call()) { ... }` с multiline-телом триггерит `A multiline expression should start on a new line`. Вынести `val X = call()` **перед** `when`, subject в `when (X)` становится простым — ktlint пропускает.

**Правило (б) — вложенный `when` внутри `is Success ->`.** `is Success -> when (X.data) { ... }` — тоже multiline-expression. Обернуть в block: `is Success -> { when (X.data) { ... } }` — это уже statement, не expression.

**Правило (в) — переименование subject.** Если переименовываешь `val result` → `val X`, `sed` по одному subject не покроет вхождения **внутри** body (`${result.error.message}`, `result.data`, `result.error.cause`). Они дадут `Unresolved reference` на компиляции, а ktlint — каскад `multiline-expression-wrapping` на вложенном. Править все вхождения сразу (список grep по `result.` до правки), не по одной ошибке.

**Правило (г) — любое multiline-выражение после `=`.** `fun f(...) = runBlocking { ... }` / `val x = if (...) { ... } else { ... }` с multiline-body — тот же `multiline-expression-wrapping`. Перенести body на новую строку: `fun f(...) =\n    runBlocking { ... }` (continuation +4) или `val x =\n    if (...) { ... }`. `ktlintFormat` автофиксит. На mobile (proot) — превентивно.

**Инцидент (г), 2026-11-07, M013 step 4a (`4e0264f`):** `CancelOrderUseCaseTest` — 7 тестов с `fun \`...\`() =\n    runBlocking { ... }`; в исходной версии — `fun \`...\`() = runBlocking { ... }` на одной строке. ktlint `multiline-expression-wrapping` на 46:21, 65:21, 85:21, 103:21, 125:21, 141:21. Плюс 32:52, 35:58 — `mockk { every { ... } }` inline, автофикс. Один `ktlintFormat` закрыл.

**Инцидент:** 2026-11-07, M013 step 1b (`a47ddae`). Три итерации гейта:
1. `when (val result = checkBrokerInitializationUseCase.execute(...)) { ... }` — ktlint на 142:45 (Orders) / 94:41 (Portfolio).
2. Вынес `val initResult = ...` + `when (initResult)`, но оставил внутри `when (result.data)` и `${result.error.message}` — `Unresolved reference 'result'` (143:51, 171:73, 171:97 / 95:47, 116:69, 116:93) + ktlint на вложенном 143:45 / 95:41.
3. Обернул вложенный `when` в `{ }` + переименовал `result.` → `initResult.` — гейт зелёный.

**Проверка:** перед `when (val X = ...) { ... }` с multiline body — вынести `val X = ...` на отдельную строку. Если внутри `is Success -> when (val Y = ...)` — обернуть в `{ when (val Y = ...) { ... } }`. `./gradlew :app:ktlintFormat` (desktop, урок 55) автофиксит (а), но не (б)/(в) — оба руками. На mobile/proot `ktlintFormat` недоступен — превентивно проверять `when (val` в затронутом регионе.

**Связано:** урок 78 (ktlint на mobile — «золотой» гейт), урок 55 (ktlintFormat на desktop).

---
<!-- SUBSECTION_END: l-diagnostics-85 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-94 -->
<a id="l-diagnostics-94"></a>
### [AGENTS-LESSONS] l-diagnostics-94 `.kt` file-level comment — `//`, не `/** */`, если у функций есть KDoc
**Правило:** в `.kt` файле с KDoc у функций file-level описание делать
через `//`-комментарий, не через `/** ... */`. ktlint
`ktlintMainSourceSetCheck`: `a KDoc may not be preceded by a KDoc
(cannot be auto-corrected)`.

**Инцидент:** 2026-11-09, M001, PR #111. В
`presentation/util/TradeDetailsFormat.kt` — file-level KDoc + KDoc
функции через пустую строку → `:app:ktlintMainSourceSetCheck FAILED`
на `TradeDetailsFormat.kt:18:1`. Fix `47729842` (file-level KDoc → `//`).

---
<!-- SUBSECTION_END: l-diagnostics-94 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-96 -->
<a id="l-diagnostics-96"></a>
### [AGENTS-LESSONS] l-diagnostics-96 ktlint multiline: аргументы и цепочки — по одному на строке
**Правило:** на mobile (proot) `ktlintFormat` недоступен, поэтому при написании **нового** `.kt`/тестового кода:
- вызов, разбитый на строки, — **каждый** аргумент на своей строке: `foo(\n    any(),\n    any(),\n    ...,\n)` (а не `foo(any(), any(), ..., any())` в одну строку);
- assert с цепочкой — `.foo()` **с новой строки** после закрывающей `)`: `assertTrue(\n    x.y\n        .z(),\n)` (а не `assertTrue(x.y.z())`);
- ktlint: `Argument should be on a separate line` и `Expected newline before '.'` — оба симптома этого.

**Инцидент:** 2026-11-09, M016 (PR #112). `ktlintTestSourceSetCheck` упал 3 цикла подряд на `OrderFormViewModelTest.kt` (`calculate(...)`: 9× `any()` на одной строке → `Argument should be on a separate line`) и `OrderSearchViewModelTest.kt` (`vm.state.value.searchResults.isEmpty()` в одну строку → `Expected newline before '.'`). Fix `8aecbea6`/`1269cc28`.

**Проверка:** перед коммитом `.kt`-теста — визуально: (а) в multiline-вызове `(` … `)` — все аргументы на своих строках; (б) в assert с `.foo()` — `)` и `.` не на одной строке. На desktop — `./gradlew :app:ktlintFormat` (урок 55).

---
<!-- SUBSECTION_END: l-diagnostics-96 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-98 -->
<a id="l-diagnostics-98"></a>
### [AGENTS-LESSONS] l-diagnostics-98 `advanceUntilIdle` + `while(isActive){delay}` в `init` = guaranteed hang
**Правило:** в unit-тесте ViewModel, у которой `init { ... while (isActive) { delay(…) } }`, **запрещён** `advanceUntilIdle()`. Он гонит виртуальное время бесконечно: после `delay` корутина просыпается, перепланирует следующий `delay`, `runTest` никогда не станет idle → CI hang (не падение, а бесконечный run). Правильно:
- `runCurrent()` вместо `advanceUntilIdle()` — выполняет задачи на **текущем** времени, не трогает `delay(5 * 60 * 1000)`.
- `finally { vm.viewModelScope.cancel() }` — отменяет pending `delay` до `runTest` cleanup.
- `Dispatchers.setMain(UnconfinedTestDispatcher())` — корутины стартуют сразу, `finally cancel` всё равно нужен.

**Инцидент:** 2026-11-09, M6 umbrella (PR #114). `OrdersViewModelTest` с `advanceUntilIdle()`. `OrdersViewModel.init` — `while (isActive) { delay(5 * 60 * 1000L); … }`, фоновое обновление trading statuses. `fast-check` висел 1.5 часа. 5 CI-циклов: (1) `Expecting '->'`, (2) unused import, (3) `Unresolved reference 'viewModelScope'`, (4) `runCurrent + finally cancel` (этот урок), (5) зелёный.

**Проверка:** перед `advanceUntilIdle()` — `grep 'while (isActive)' ViewModel.kt`. Есть — `runCurrent + finally cancel`. `viewModelScope` — extension из `androidx.lifecycle.viewModelScope`, нужен явный import.

**Связано:** урок 78 (ktlint на mobile), урок 55 (`ktlintFormat` на desktop).

---
<!-- SUBSECTION_END: l-diagnostics-98 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-99 -->
<a id="l-diagnostics-99"></a>
### [AGENTS-LESSONS] l-diagnostics-99 Compose UI на Robolectric: runner + GraphicsMode NATIVE + ConscryptMode OFF
**Правило:** Compose UI smoke-тест под Robolectric (JVM, без эмулятора) требует **всех четырёх**:
- `@RunWith(RobolectricTestRunner::class)` — не `AndroidJUnit4` (без instrumentation);
- `@Config(sdk = [34])` — иначе targetSdk может уйти в 36 и Robolectric не поднимется;
- `@GraphicsMode(GraphicsMode.Mode.NATIVE)` — иначе Compose `waitForIdle` уходит в 5s `ComposeTimeoutException`;
- `@ConscryptMode(ConscryptMode.Mode.OFF)` — иначе `java.lang.UnsatisfiedLinkError: no conscrypt_jni` в `AndroidTestEnvironment.setUpApplicationState`.
Плюс `createComposeRule()` (не `createAndroidComposeRule<ComponentActivity>()`) — для plain composable не тянет `ActivityScenario` и `ui-test-manifest` в test-рантайм.

**Инцидент:** 2026-11-09, U0 Compose UI infra (PR #115). `OrderCardTest`: 3 CI-цикла подряд (5s hang → `UnsatisfiedLinkError`), 4-й зелёный. Первый прогон: `createAndroidComposeRule<ComponentActivity>` + `@Config(sdk = [34])` без `@GraphicsMode` и без `@ConscryptMode` → `renders` 0.006s (`UnsatisfiedLinkError`), `onCancel` 5.016s (`ComposeTimeoutException`). Второй: `RobolectricTestRunner` + `@GraphicsMode(NATIVE)` + `createComposeRule()` → `onCancel` всё ещё 4.2s `UnsatisfiedLinkError`. Третий: `+@ConscryptMode(OFF)` → зелёный.

**Проверка:** перед первым push нового Compose-test-класса — `grep -E '@(RunWith|Config|GraphicsMode|ConscryptMode)' file.kt` (должны быть все 4); `grep 'createComposeRule'` (не `createAndroidComposeRule<ComponentActivity>`). Если тест «висит» ~5s — `@GraphicsMode(NATIVE)`. Если `UnsatisfiedLinkError: no conscrypt_jni` — `@ConscryptMode(OFF)`.

**Связано:** урок 98 (`advanceUntilIdle` + `while(isActive){delay}` hang), урок 96 (ktlint multiline), урок 78 (ktlint на mobile).

---
<!-- SUBSECTION_END: l-diagnostics-99 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-105 -->
<a id="l-diagnostics-105"></a>
### [AGENTS-LESSONS] l-diagnostics-105 `data class *State`: два поля на одно значение — красный флаг
**Правило:** при ревью `data class *State` в presentation — проверять, что два похожих поля не описывают одно логическое значение. Признак дивергенции: один код пишет в `x`, другой читает `selectedX`, значения никогда не синхронизируются. Легитимные пары: primary/pair слоты (`x` + `pairedX`), флаг + значение (`showXDialog` + `selectedX`), разные сущности (`selectedBroker` + `selectedAccountId`).

**Инцидент:** 2026-11-12. `OrderSearchState` (создан в `eea0ec5f`, Fase 2, 11a-3) содержал `searchBroker` (читался `SearchInstrumentsUseCase.execute` в `onQueryChanged`) и `selectedSearchBroker` (писался `saveSearchBrokerSettings`, читался UI `SearchBrokerDialog`). Выбор брокера в диалоге не влиял на API поиска. Баг переехал 1:1 через 6+ сессий: `OrdersUiState` → M012 → M013 → 11a → 11b → slot-рефакторинг (PR #135) → всплыл только при ручном чтении кода в сессии slot-рефакторинга. Fix — PR #136 (merge `58b8233b`): одно поле `broker` в `SlotSearchState`.

**Проверка:** `grep -nE '^\s+val ' <file>State.kt` — на пары похожих имён. Для каждой пары: (а) кто пишет, (б) кто читает, (в) синхронизируются ли. Если писатель одного и читатель другого не совпадают — дивергенция. Пример: `OrderSearchState` до PR #136 — `searchBroker` (писатель: никто) + `selectedSearchBroker` (писатель: `saveSearchBrokerSettings`) → расхождение. После: одно поле `broker`. Проверено 2026-11-12 (grep): `OrderFormState.selectedTicker` — **dead field** (0 reads/writes в main, кроме declaration + KDoc), **не** дивергенция. Удалить при reduce `### 2. OrderFormViewModel`.

**Связано:** AGENTS 2c, урок 29 (не по памяти), `docs/ISSUES.md` → «OrderSearchViewModel: selectedBroker не влияет на API поиска (решён)».


## Диагностика

---
<!-- SUBSECTION_END: l-diagnostics-105 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-17 -->
<a id="l-diagnostics-17"></a>
### [AGENTS-LESSONS] l-diagnostics-17 KDoc vs код — grep фактическое использование
**Правило:** перед находкой «KDoc не соответствует коду» — `grep` фактическое использование в репозитории. Не доверять тексту KDoc как источнику правды.

**Инцидент:** 2026-10-27, clean-audit L9. Нашёл «KDoc говорит Engine: Ktor CIO, actual OkHttp» (Н82). После grep: `provideTInvestWsHttpClient` реально CIO, `createTransportHttpClient` (OkHttp) — dead code. KDoc был прав. Н82 отозвана.

**Проверка:** `grep -rn "createTransportHttpClient\|provideTInvestWsHttpClient" app/src` — что вызывается на самом деле.

---
<!-- SUBSECTION_END: l-diagnostics-17 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-18 -->
<a id="l-diagnostics-18"></a>
### [AGENTS-LESSONS] l-diagnostics-18 Не угадывать архитектурный intent
**Правило:** перед рекомендацией «X должен использоваться в Y» — `grep` потребителей X и понять, почему Y не использует X. Не предполагать «не используется = неполнота», возможно — осознанное решение.

**Инцидент:** 2026-10-27, clean-audit L4. Запись Н32: «`StreamCombinators.streamWithFallback` в domain, но менеджеры должны использовать его вместо ручного merge». Пользователь: «Он нужен только для организации переподключения... И да, мне экстремально важно знать откуда пришли данные: из стрима или из polling. В этом весь смысл существования трекера состояний». Оказалось: менеджеры **сознательно** не используют `streamWithFallback`. Н32 переформулирована.

**Проверка:** `grep -rn "streamWithFallback" app/src` перед рекомендацией; посмотреть, какие менеджеры его (не) используют и какие альтернативы у них в коде.

---
<!-- SUBSECTION_END: l-diagnostics-18 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-19 -->
<a id="l-diagnostics-19"></a>
### [AGENTS-LESSONS] l-diagnostics-19 Структурные находки в документах — grep, не память
**Правило:** перед отчётом о положении заголовков/секций в `.md` — `grep -n "^## \|^### "` и сверка с фактическим файлом. Не по памяти прошлых сессий.

**Инцидент:** 2026-10-27, аудит ROADMAP. В отчёте написал «`### Расшифровка кодов ошибок брокеров` в Закрытых задачах» — фактически она в Планируемых (строка 2207, до `## Закрытые` на 2231). Пользователь поймал. Также «двойной `---` перед detekt» — фактически дубль был у хвоста umbrella и уехал вместе с ним.

**Проверка:** `grep -n "^### \|^## Планируемые\|^## Закрытые" docs/ROADMAP.md` перед каждым отчётом.

---
<!-- SUBSECTION_END: l-diagnostics-19 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-25 -->
<a id="l-diagnostics-25"></a>
### [AGENTS-LESSONS] l-diagnostics-25 `awk /^### X/,/^### /` закрывается на самом X
**Правило:** для вывода секции `.md` по границам — `grep -n "^### "` + `sed -n 'N,Mp'` по номерам. Не `awk '/^### X/,/^### /'`.

**Инцидент:** 2026-10-23, 3 раза (`StreamState`, `DebugConfig`, `TInvest-specific`). Обе границы `awk` матчат первую строку — вывод 1 строка или пусто. Приходилось перезапрашивать через `grep -n` + `sed`. Для `## ` та же проблема.

**Проверка:** `grep -n "^### X$\|^### Y$" file` → два номера → `sed -n 'N,Mp'`. Или `awk '/^### X$/{f=1} f && /^### Y$/{exit} f'`.

---
<!-- SUBSECTION_END: l-diagnostics-25 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-31 -->
<a id="l-diagnostics-31"></a>
### [AGENTS-LESSONS] l-diagnostics-31 «UI не обновляется» — по слоям, не по симптомам
**Правило:** при баге «данные не отображаются / не обновляются» — диагностировать снизу вверх: (1) источник эмитит? лог в `collect`; (2) ViewModel получает? лог в обработчике; (3) `_uiState` обновляется? лог в `_uiState.update`; (4) Composable читает? лог в `LaunchedEffect`. Не начинать с UI-правок.

**Инцидент:** 2026-10-28, «карточки в портфеле не обновляются». Начал с UI (переписал дизайн кнопок, скролл, оверлеи) — не помогло. Только после `Log.d` в `SharedPositionStreamManager` и `updatePositionPnl` увидел: `portfolioPositions` пуст, потому что `replay = 0`. Правильный порядок сэкономил бы 3-4 итерации.

**Проверка:** `Log.d` в 4 точках (`emit`, `collect`, `_uiState.update`, `LaunchedEffect`) — смотреть, где обрывается.

---
<!-- SUBSECTION_END: l-diagnostics-31 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-32 -->
<a id="l-diagnostics-32"></a>
### [AGENTS-LESSONS] l-diagnostics-32 `MutableSharedFlow(replay = 0)` не отдаёт текущее состояние
**Правило:** если на Flow подписываются **после** первой эмиссии (восстановление состояния, вход на экран, переподключение) — `replay = 1`. `replay = 0` даёт только новые эмиссии; «пустое» состояние до следующего тика.

**Инцидент:** 2026-10-28, `SharedPositionStreamManager`. После перезапуска приложения выбранный инструмент восстанавливался, но позиции не заполнялись, пока не придёт следующий poll (10 с). `replay = 0` — первый снапшот терялся для новых подписчиков. После `replay = 1` — данные приходят сразу.

**Проверка:** `grep -n "MutableSharedFlow.*replay" app/src/main/java/com/example/tscalp/data/api/SharedPositionStreamManager.kt` — `replay = 1`.

---
<!-- SUBSECTION_END: l-diagnostics-32 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-46 -->
<a id="l-diagnostics-46"></a>
### [AGENTS-LESSONS] l-diagnostics-46 Удаление fat-метода — проверять supertype реализаций
**Правило:** перед удалением метода из интерфейса — `grep -rn "override.*fun <method>" app/src/main/kotlin/**/data` — какие реализации есть. Если брокер не имеет supertype для этого метода (роль `= null`) — override станет orphan (`NOTHING_TO_OVERRIDE`).

**Инцидент:** 2026-10-28, fat-part. Удалил fat из `BrokerApi`, оставил `override fun openSandboxAccount` в BCS/Finam (supertype `SandboxApi` нет, `sandbox = null`). CI: 14+15 `overrides nothing`.

**Проверка:** `grep -E "^class \w+.*: .*Api" <broker>.kt` — supertype list. Override допустим только для supertype-ролей.

---
<!-- SUBSECTION_END: l-diagnostics-46 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-47 -->
<a id="l-diagnostics-47"></a>
### [AGENTS-LESSONS] l-diagnostics-47 `grep -c` на структурных маркерах — ложные тревоги
**Правило:** `grep -c` — для точного сравнения с ожидаемым; для «есть/нет» — `grep -q`. Для классов/интерфейсов — `grep -nE '^class \w+'` + `head`. `@Suppress` в KDoc ≠ `@Suppress` на классе — считать с `^@Suppress`.

**Инцидент:** 2026-10-28. `grep -c "@Suppress"` вернул 1 — был в KDoc `@Suppress снят`. `grep -cE "^    (suspend )?fun "` вернул 3 — были role overrides. `ls | grep -cE '^PortfolioApi'` вернул 0 — глюк в Termux-контексте.

**Проверка:** `^`-anchor, `grep -n`, смотреть на контекст.

---
<!-- SUBSECTION_END: l-diagnostics-47 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-48 -->
<a id="l-diagnostics-48"></a>
### [AGENTS-LESSONS] l-diagnostics-48 Import-order чекер — `java`/`javax`/`kotlin` в конце
**Правило:** для проверки порядка — ktlint-правило (`java`, `javax`, `kotlin` в конце), не чистый алфавит. `grep -E "^import " file | awk 'NR>1 && $0<prev'` — неверно, `javax` лексикографически раньше `kotlinx`, но по правилу — позже.

**Инцидент:** 2026-10-28. Self-check показал `FAIL: kotlinx.coroutines.withContext > javax.inject.Inject` — ложная тревога, ktlint-стандарт `javax` в конце.

**Проверка:** для быстрой сверки — `ktlintFormat` локально или полагаться на CI.

**Инцидент-счётчик (2026-11-07, Session Orders-VM-Split, 1 нарушение):**
Мой pre-commit блок для 12a включал `awk '/^import / { if (prev != "" && $0 < prev) print "OOD: " ... }'` как fallback-проверку import-order. На корректном файле (`import kotlinx.coroutines.launch` → `import javax.inject.Inject` — по ktlint-правилу `javax` в конце) awk дал ложный OOD. Гейт (`:app:ktlintCheck`) в том же прогоне был зелёный — awk **имитировал** ktlint-правило неверно.

**Правило (расширено):** fallback-проверка import-order в bash-скрипте **запрещена**, если не реализует ktlint-правило (`java`/`javax`/`kotlin` — в конце, остальное — лексикографически). Либо (а) полагаться на `:app:ktlintCheck` в гейте, либо (б) писать ktlint-совместимый awk с явным разделением группы: `java`/`javax`/`kotlin` — в конце, остальное — `$0 < prev` в своей группе.

**Проверка:** перед добавлением awk-import-order в pre-commit — `grep -q "ktlintCheck"` в гейте. Если есть — awk не нужен.

**Инцидент-счётчик (2026-11-07, Session Orders-VM-Split, 2 нарушения):**
ещё 2 случая в той же сессии. (а) Python `post-assert` для 12a-fix использовал
чистый лексикографический порядок (`for i in range(len(imports)-1):
assert imports[i] <= imports[i+1]`) — упал на корректном файле с
`import kotlinx.coroutines.launch` → `import javax.inject.Inject` (по ktlint
`javax` в конце). (б) При вставке нового импорта позиционно — `PriceConsumer`
вставлен после `PriceFlowManager` (оба `domain.api.managers`; `C < F` — нужно
до); `AppResult` вставлен перед `OrderDirection` (оба `domain.models`;
`AppResult < InstrumentType` — нужно раньше). Три раза — ktlint упал на
`Imports must be ordered`, каждый раз — фикс-цикл.

**Правило (расширено, окончательно):** при вставке **нового** импорта в
`.kt` — **пересортировать весь блок imports** по ktlint-группам
(`java`/`javax`/`kotlin` — в конце, остальное — лексикографически в своей
группе). Позиционная вставка («после anchor») — **запрещена** (урок 62
расширяется на insert, не только на rename). В python post-assert:
`imports.sort()` неверен; нужен `sorted(imports, key=ktlint_key)` с явным
разделением групп.

**Проверка:** в скрипте, вставляющем импорт — собрать `set(imports) | {new}`,
отсортировать `ktlint_key`, переписать блок. Post-assert — `ktlint_groups_ok`
(группы, не чистый алфавит).

---
<!-- SUBSECTION_END: l-diagnostics-48 -->










<!-- SUBSECTION_BEGIN: l-diagnostics-90 -->
<a id="l-diagnostics-90"></a>
### [AGENTS-LESSONS] l-diagnostics-90 DataStore на устройстве — `exec-out run-as base64`, не `shell run-as cat`
**Правило:** читать файл DataStore (`*.preferences_pb`) с устройства —
только через `adb exec-out run-as <pkg> base64 <path>` → `base64 -d`
на хосте. `adb shell run-as <pkg> cat <binary>` в TTY отдаёт пусто
(SE Linux / TTY-обработка), `strings` не находит ключей — ложный
вывод «ключ не записан».

**Инцидент:** 2026-11-07, live-regression A4 (`OrdersViewModel` split).
`adb shell run-as com.gitlab.biomorf.tscalp cat files/datastore/*.preferences_pb
| strings | grep order_cards` → пусто. Ложный вывод: `order_cards`
не пишется → гипотезы F3/F4 (SaveCardSettingsUseCase не сохраняет).
Реально: `ls -la` показал 541 байт; `exec-out run-as base64` +
decode → `order_cards = TINVEST|e6123145-…|SBER|1fbeef7a-…` на месте.
Потерян раунд диагностики.

**Проверка:** `adb shell run-as <pkg> stat -c '%s' <path>` — размер
> 0 подтверждает запись; `adb exec-out run-as <pkg> base64 <path> |
base64 -d | strings -n 3` — видит ключи и значения.

---
<!-- SUBSECTION_END: l-diagnostics-90 -->










<!-- SECTION_END: diagnostics -->










<!-- FILE_END -->
