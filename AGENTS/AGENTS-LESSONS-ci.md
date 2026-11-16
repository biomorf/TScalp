<!-- LESSONS_SECTION_BEGIN: ci -->
## CI

<!-- SUBSECTION_BEGIN: l-ci-8 -->
___
### l-ci-8 `feature/**` push → CI запускается сразу

**Правило:** после push в feature — смотреть CI на коммите. Не
нужен PR, чтобы увидеть `gate`.

**Проверка:** `.github/workflows/ci.yml` → `on.push.branches` содержит
`'feature/**'`.

<!-- SUBSECTION_END: l-ci-8 -->
<!-- SUBSECTION_BEGIN: l-ci-9 -->
___
### l-ci-9 Порядок падений CI: compile → ktlint → detekt

**Правило:** если несколько ошибок — чинить в порядке
compile → ktlint → detekt, не смешивать. Сначала «чтобы
скомпилировалось», потом «стиль».

**Инцидент:** H23 — 4 волны CI-падений, в каждой одна группа.

**Проверка:** вывод CI, `Task :app:... FAILED`.

<!-- SUBSECTION_END: l-ci-9 -->
<!-- SUBSECTION_BEGIN: l-ci-10 -->
___
### l-ci-10 `paths-ignore` — docs-only не запускает CI

**Правило:** docs-only коммиты (`.md`, `docs/**`, `LICENSE`) не
тратят Actions-минуты и не показывают красный. Не паниковать,
что «CI не запустился».

**Проверка:** `.github/workflows/ci.yml` → `on.push.paths-ignore`.

<!-- SUBSECTION_END: l-ci-10 -->
<!-- SUBSECTION_BEGIN: l-ci-11 -->
___
### l-ci-11 HTML-репорт ktlint на CI — недоступен с телефона

**Правило:** вывод в консоли CI содержит `file:line:col` — читать
консоль, не html.

**Проверка:** `Task :app:ktlintTestSourceSetCheck FAILED` и строки
с `file.kt:line:col: Rule`.

<!-- SUBSECTION_END: l-ci-11 -->
<!-- SUBSECTION_BEGIN: l-ci-33 -->
___
### l-ci-33 Матрица AGP ↔ Gradle ↔ Hilt — точные версии

**Правило:** AGP 9.0.0 → Gradle **9.1.0** (`bin.zip` существует на services.gradle.org; 9.1.1 — нет). Hilt 2.59.2+ требует AGP ≥ 9.0.0. Hilt 2.51.1 — совместим с AGP 8.x. При обновлении любого звена — сверять матрицу.

**Инцидент:** 2026-10-28. AGP 9.0.0 + Gradle 9.1.1 — `Not Found` по URL `gradle-9.1.1-bin.zip`. AGP 8.13.2 + Hilt 2.59.2 — `Hilt plugin requires AGP 9.0.0`. Пришлось перебирать версии. Дополнительно: с AGP 9.0 `kapt` несовместим со встроенным Kotlin (`android.builtInKotlin`), миграция на KSP обязательна.

**Проверка:** `grep -n "distributionUrl" gradle/wrapper/gradle-wrapper.properties`; `grep -n "hilt-android" build.gradle.kts app/build.gradle.kts`. URL дистрибутива — сверять с https://services.gradle.org/distributions/.

<!-- SUBSECTION_END: l-ci-33 -->
<!-- SUBSECTION_BEGIN: l-ci-70 -->
___
### l-ci-70 ktlint multiline chain — `.` с новой строки

**Правило:** если `val x =` на отдельной строке, все `.` в правой части — на новых строках с +4 indent от `val` (continuation indent). `ktlint` ловит `Expected newline before '.'`.

**Инцидент:** 2026-11-04, CI `ktlintMainSourceSetCheck FAILED`: `PortfolioViewModel.kt:76:61 Expected newline before '.'` — `_uiState.value.sheet.accounts.values.flatMap { it.positions }` на одной строке (82 символа).

**Проверка:** `grep -nE "\)\.[a-z]" file.kt` — multiline-выражения с `.` на одной строке. `ktlintFormat` (desktop, урок 55) или ручная разбивка.

<!-- SUBSECTION_END: l-ci-70 -->
<!-- SUBSECTION_BEGIN: l-ci-71 -->
___
### l-ci-71 detekt `CyclomaticComplexMethod` на Compose — расширять `@Suppress`

**Правило:** при правке Compose-функции (`?:`, `if`, `when` — каждое даёт ветвление) — `grep -n "@Suppress" file` и сравнить с существующим. Если в файле `AssetCardContent` уже нёс `@Suppress("LongMethod", "CyclomaticComplexMethod")`, консолидировать `AssetPositionCard` до того же. Декомпозиция — отдельная задача (Н94, ROADMAP → `#### Compose-компоненты`).

**Инцидент:** 2026-11-04, CI `detekt FAILED`: `AssetPositionCard.kt:58:5 The function AssetPositionCard appears to be too complex ... (complexity: 15). The maximum allowed complexity for methods is set to '14'`. Миграция на `Position` добавила `displayCurrentPrice ?: ...` и `position.instrument.*`.

**Проверка:** `grep -nE "^@Suppress" file.kt` — все Compose-функции в файле; расширять `@Suppress` консистентно. `cyclomatic` (complexity) не входит в 4h pre-commit — ловится только detekt.

---

<!-- SUBSECTION_END: l-ci-71 -->
<!-- SUBSECTION_BEGIN: l-ci-95 -->
___
### l-ci-95 PR title — `<type>(<scope>): …`, как commit message

**Правило:** заголовок PR/MR — `<type>(<scope>): <краткое описание>`,
тот же формат, что и commit message (AGENTS 5, урок про commit — 43 / 77).
Не свободный текст.

**Инцидент:** 2026-11-09, M001, PR #111. Первая версия title —
`M001: extract format* + withCard into presentation/util` (нет
`type(scope)`). Пользователь поправил. Вторая — `refactor(m001):
extract format* + withCard into presentation/util`.

**Проверка:** перед открытием PR — `title` соответствует regex
`^(feat|fix|refactor|chore|docs|test)\([a-z0-9_-]+\): .+$`
(список типов — AGENTS 5).

---

<!-- SUBSECTION_END: l-ci-95 -->
<!-- SUBSECTION_BEGIN: l-ci-102 -->
___
### l-ci-102 `--rerun-tasks` не обходит Gradle build-cache; новые тесты — `--no-build-cache`

**Правило:** `./gradlew ... --rerun-tasks` **не** гарантирует перекомпиляцию, если build-cache содержит результат: `compileDebugUnitTestKotlin` может взять из кеша старую версию (до добавления новых файлов). Для локального gate, где добавлены новые тест-файлы — `--no-build-cache`.

**Инцидент:** U4 step-1 (2026-11-11). `OrdersScreenBehaviourTest` добавлен, gate зелёный, но в `app/build/test-results` нового XML нет — `compileDebugUnitTestKotlin` UP-TO-DATE из cache. `--no-build-cache` — решил.

**Проверка:** `./gradlew ... --rerun-tasks --no-build-cache`. Перед gate с новыми тест-файлами — `git status` показывает `??`, значит compile должен их видеть; если тест не появляется в test-results — cache.

---

<!-- SUBSECTION_END: l-ci-102 -->
<!-- LESSONS_SECTION_END: ci -->