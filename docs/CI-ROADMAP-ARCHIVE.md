# CI-ROADMAP-ARCHIVE

Закрытые CI/CD-задачи из `docs/ci-roadmap.md`.

## Закрытые задачи




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




### ✅ versionName в GitHub Release (отменено)

Исходная задача из эпохи GitLab CI: добавить `Release vX.Y.Z`
в description. В GitHub это решено через `name: Release
${{ inputs.tag }}` в `release.yml` — версия видна в заголовке
релиза, дублирование в body не нужно




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
    - `PositionFlowManager` перемещён в `domain.api`;
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




### ✅ CI: пропускать сборку на docs-only коммитах

Workflow `ci.yml` больше не запускается на коммитах, которые
трогают только документацию.

**Сделано:**

- В `.github/workflows/ci.yml` в блоки `push` и
  `pull_request` добавлен `paths-ignore`:
  - `**.md` — все Markdown-файлы (ROADMAP, ISSUES, AGENTS,
    README, docs/*.md);
  - `docs/**` — директория документации;
  - `LICENSE` — файл лицензии.
- Rulesets на `dev` не настроены (проверено — только
  `Protect master` и два ruleset'а для тегов), поэтому
  required checks не мешают — обошлись без notifier-job'а
  и без разделения на два workflow.
- `release.yml` не тронут — он срабатывает на теги, а не на
  push/PR.

**Проверено:**

- Коммит с правкой `ci.yml` — CI запустился и зелёный.
- Docs-only коммит — workflow не запустился.

**Итог:** экономия CI-минут на документационных коммитах.
Полный CI по-прежнему срабатывает на любом коммите с кодом
(`app/**`, `gradle/**`, `build.gradle.kts`, `.github/**`).

cat >> docs/ROADMAP.md <<'EOF'




### ✅ Перенос ROADMAP и ISSUES в docs/

Планирование проекта вынесено из корня в `docs/`.

**Сделано:**

- `git mv ROADMAP.md docs/ROADMAP.md`,
  `git mv ISSUES.md docs/ISSUES.md`.
- В `AGENTS.md` обновлены все ссылки (13 упоминаний):
  `ROADMAP.md`/`ISSUES.md` → `docs/ROADMAP.md`/`docs/ISSUES.md`.
- Правило 5b упрощено: перечисление конкретных путей
  заменено на `docs/` или `AGENTS.md`. Полные пути остались
  в разделе «Документы проекта».
- В `docs/ROADMAP.md` поправлены 2 ссылки на `docs/ISSUES.md`.
- Остальные `docs/*.md` и `README.md` проверены — прямых
  ссылок на `ROADMAP.md`/`ISSUES.md` не было.

**Структура после миграции:**

```
/
├── README.md
├── LICENSE
├── AGENTS.md
└── docs/
    ├── ROADMAP.md
    ├── ISSUES.md
    ├── gradle-setup.md
    ├── release-process.md
    └── streams.md
```

**Итог:** в корне остались только конвенциональные файлы
(`README.md`, `LICENSE`, `AGENTS.md`). Вся проектная
документация — в `docs/`.




___




### ✅ Разделение планирования: dev-roadmap / ci-roadmap

CI/CD-планирование вынесено в отдельный файл `docs/ci-roadmap.md`.

**Сделано:**

- Создан `docs/ci-roadmap.md` с шапкой и обратной ссылкой
  на `docs/ROADMAP.md`.
- Перенесены открытые секции:
  - `## CI/CD улучшения` (с подразделами
    `### Self-hosted GitLab Runner через Ansible + Podman`,
    `### Переход на GitHub Environment secrets`);
  - `## Переход на полный GitFlow`.
- Перенесены закрытые секции (хронологический порядок
  сохранён):
  - Защита ветки master
  - versionName в GitHub Release
  - Обновление GitHub Actions до Node.js 24
  - Переезд CI на GitHub Actions
  - Renovate: self-hosted через GitHub Actions
  - Coverage в unit-tests
  - ktlint enforcement
  - detekt enforcement
  - CI: пропускать сборку на docs-only коммитах
  - Перенос ROADMAP и ISSUES в docs/
- `docs/ROADMAP.md` содержит только задачи разработки
  (код, архитектура, data-слой, UI, рефакторинг).
- В шапках обоих файлов — взаимные ссылки.
- `docs/release-process.md` — 3 ссылки обновлены на
  `docs/ci-roadmap.md`.
- `AGENTS.md` → «Документы проекта»: добавлен
  `docs/ci-roadmap.md`, уточнено описание
  `docs/ROADMAP.md` (план разработки), убрана дублирующая
  строка.

**Принцип разделения (применение правила 2b на уровне
файлов):**

- **`docs/ROADMAP.md`** — разработка: код, архитектура,
  data-слой, UI, рефакторинг.
- **`docs/ci-roadmap.md`** — организация: CI/CD, ветвление,
  релизный процесс, code quality, публикация артефактов,
  secrets, environment.

Code-quality инструменты (ktlint, detekt, Kover) живут в
CI-плане, потому что их роль — гейт на merge через CI,
а не решение задачи кода.

**Итог:** два файла вместо одного, каждый со своим
потребителем и слоем.




### ✅ Переход на полный GitFlow

Полный GitFlow внедрён: Rulesets, соглашение о ветках,
реструктуризация workflow, Environment secrets.

**Сделано (этапы 1, 2, 3, 4a, 6):**

- **Этап 1 — Rulesets.** Созданы 4 branch-ruleset'а
  (`Protect dev`, `Protect release branches`,
  `Protect hotfix branches` — плюс существовавший
  `Protect master`) с required check `CI / fast-check`,
  block force pushes, restrict deletions, Merge-only
  merge strategy.
  В `Protect release tags` добавлен bypass для
  Repository admin — для восстановления после ошибок
  (например, удаление случайного тега `v0.0.1-test`).
- **Этап 2 — Соглашение о ветках.** Создан
  `docs/git-flow.md` (полное описание схемы веток,
  стратегий merge, жизненных циклов release и hotfix,
  осознанных отклонений от канона, правил защиты).
  Обновлён `docs/release-process.md`: схема `dev → master`
  заменена на `release/* → master` + обратный
  `master → dev`. В `AGENTS.md` добавлен раздел
  «Git workflow: ветки и PR» с картой merge.
- **Этап 3 — Реструктуризация workflow.** `ci.yml`
  разделён на два job'а: `fast-check` (unit + lint +
  ktlint + detekt) и `full-build` (kover + assembleDebug).
  Триггеры расширены под GitFlow, `master` добавлен
  в PR-триггеры, `paths-ignore` убран с PR. Required check
  в Rulesets переименован `CI / build` → `CI / fast-check`.
- **Этап 4a — Environment secrets.** Создан Environment
  `release` с tag-scoped доступом (`v*`), без admin bypass.
  4 keystore-секрета перенесены из Repository secrets
  в Environment. В `release.yml` добавлен
  `environment: release`. Repository secrets удалены.
  Проверено холостым прогоном на теге `v0.0.2` —
  workflow зелёный, GitHub Release создан.
- **Этап 6 — Документация.** Реализован как часть
  этапа 2 (`docs/git-flow.md`, `docs/release-process.md`,
  `AGENTS.md`).

**Осознанные отклонения от канона GitFlow:**

1. **Ветка `dev`** вместо `develop` — миграция имени
   дороже, чем отступление.
2. **Merge commit везде** (без Squash и Rebase) —
   сохранение SHA, отсутствие расхождения `dev`/`master`.
   Отдельная задача «Рассмотреть Squash vs Merge commit»
   в `docs/ci-roadmap.md` (Планируемые).

**Вынесено в отдельные задачи:**

- `### Play Store Internal Testing` — release-tier
  публикация в Play Console (заблокирована Play Console).
- `### Play Store Production` — production-tier
  (зависит от Internal).
- `### Role emulation для тестировщика` — этап 5,
  отложен до появления второго участника.

**Итог:** организационный слой воспроизводит
индустриальный GitFlow (см. правило 2b AGENTS.md).
Единственный работающий CI сейчас — GitHub Actions;
GitLab — резерв (см. `### Hot-swap CI на GitLab`).




### ✅ Gate-job для required checks

Устойчивый required check, который не исчезает из
picker'а Rulesets после паузы в проекте.

**Проблема:** GitHub Rulesets picker показывает только те
checks, что прогонялись на защищаемой ветке за последние
7 дней. После паузы >7 дней required check исчезал из
picker'а, вписанный вручную не матчился с рантаймом (имя
check в Rulesets — это имя job'а без префикса workflow,
а не полное `CI / fast-check (pull_request)`), PR зависал
в «Expected — Waiting for status to be reported».

**Решение:** job `gate` в `ci.yml`.

- `needs: [fast-check, full-build]`
- `if: always()` — запускается даже при падении upstream,
  иначе не создал бы статус и required check завис бы.
- Проверяет результат upstream: `fast-check` → `success`,
  `full-build` → `success` или `skipped`.
- Падает, если проверки не прошли → gate красный → merge
  заблокирован.

**Сделано:**

- Job `gate` добавлен в `.github/workflows/ci.yml`.
- Проверен на `feature/gate-job`: gate зелёный,
  `fast-check` success, `full-build` skipped.
- Проверен на push в `dev` после merge PR #12: gate
  зелёный, `full-build` запустился (success).
- Обновлены все четыре branch-ruleset'а (`Protect dev`,
  `Protect master`, `Protect release branches`,
  `Protect hotfix branches`): в required status checks
  добавлен `gate` (через picker Add checks — не вручную).
  Из `Protect master` / `release` / `hotfix` удалён
  ошибочно вписанный `CI / fast-check (pull_request)`.

**Открытие:** required check в Rulesets указывается **по
имени job'а** (`gate`, `fast-check`), а не по полному
UI-имени (`CI / gate (pull_request)`). Причина, по которой
в прошлый раз не работал `CI / fast-check` — мы указывали
его с префиксом workflow. Правильно — просто `fast-check`.

**Итог:** первый PR после паузы >7 дней запускает CI,
gate появляется в picker'е, merge не блокируется вручную.




### ✅ Справочник по защите репозитория

Создан `docs/repo-protection.md` — единый справочник
по защите веток и тегов на обеих платформах.

**Сделано:**

- Создан `docs/repo-protection.md`:
    - обзор: что защищаем и зачем;
    - GitHub Rulesets: все 6 ruleset'ов, общие правила,
      merge-стратегия, bypass;
    - известные ограничения GitHub: picker required
      checks (7-дневное окно), required check по имени
      job'а (не UI-имени), gate-job pattern,
      восстановление после ошибочного тега;
    - GitLab Protected Branches / Tags: текущее состояние,
      особенности, таблица эквивалентов GitHub ↔ GitLab;
    - процедуры: добавить required check, восстановиться
      после ошибочного тега, проверить работу правил;
    - связанные документы.
- `docs/release-process.md` — секция «Защита веток и
  тегов (GitHub Rulesets)» заменена короткой справкой
  со ссылкой на новый справочник. Удалены пошаговая
  настройка (теперь в справочнике) и устаревшее
  упоминание `CI / build`.
- `docs/git-flow.md` — секция «Защита веток (Rulesets)»
  заменена короткой справкой со ссылкой. Таблица
  rulesets перенесена в справочник. Раздел «Связанные
  документы» обновлён: добавлена ссылка на
  `docs/repo-protection.md`, поправлена устаревшая
  ссылка на «Переход на полный GitFlow» (задача
  закрыта).

**Консолидация:** знания о защите репозитория, ранее
разбросанные по `docs/git-flow.md`,
`docs/release-process.md` и закрытой задаче Gate-job,
теперь в одном месте. При следующем обновлении Rulesets
или переключении на GitLab — не придётся собирать по
кусочкам.

**Открытие, зафиксированное в справочнике:**
required check в Rulesets задаётся именем job'а
(`gate`, `fast-check`), а не полным UI-именем
(`CI / gate (pull_request)`). Указано с процедурой
«Как добавить новый required check».

**Итог:** справочник создан, ссылки взаимны, дубли
устранены.
