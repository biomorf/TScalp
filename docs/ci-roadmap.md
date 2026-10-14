# CI-ROADMAP

План CI/CD, ветвления, релизного процесса, code quality.
Формат — секция на задачу, со статусом и оценкой.

План разработки → `docs/ROADMAP.md`.

---

## Планируемые задачи

### Справочник по защите репозитория

**Мобильная работа:** полное закрытие. Создание `.md`-файла,
правки ссылок — через proot.

**Статус:** запланировано.

**Контекст:** за время работы над GitFlow накопились
особенности защиты репозитория, которые легко забыть и
снова наступить на те же грабли:

- **GitHub Rulesets picker.** Показывает только те checks,
  что прогонялись на защищаемой ветке за последние 7 дней.
  После паузы >7 дней — исчезают, PR зависает в «Expected
  — Waiting for status to be reported».
- **Required check указывается по имени job'а.** `gate`,
  `fast-check` — не `CI / gate (pull_request)`. Полное
  UI-имя не матчится с рантаймом.
- **Gate-job pattern.** `gate` в `ci.yml` — обход picker'а:
  всегда присутствует в CI, не исчезает из списка.
- **Bypass для Repository admin** — нужен для аварийного
  восстановления (например, удаление случайного тега).
- **GitLab Protected Branches/Tags** — эквиваленты
  GitHub Rulesets, но с другой терминологией и другим
  набором правил.

Всё это сейчас разбросано: частично в `docs/git-flow.md`
(таблица защиты), частично в закрытых задачах
`docs/ci-roadmap.md` (`✅ Gate-job для required checks`),
частично в истории обсуждений. При следующем обновлении
Rulesets или переключении на GitLab придётся собирать
по кусочкам.

**Цель:** единый справочник `docs/repo-protection.md`,
который отвечает на вопрос «как устроена защита веток и
тегов в проекте» для обеих платформ.

**Файлы:**
- `docs/repo-protection.md` — новый документ.
- `docs/git-flow.md` — добавить ссылку (раздел
  «Защита веток (Rulesets)»).
- `docs/ci-roadmap.md` — добавить ссылку из задачи
  `### Hot-swap CI на GitLab` (как в связанный документ).

**Структура `docs/repo-protection.md`:**

1. **Обзор.** Что защищаем: ветки, теги. Зачем.
2. **GitHub Rulesets.**
   - Список наших ruleset'ов (имена, target, правила).
   - Required checks: как задаются (по имени job'а), где
     взять (picker, Add checks).
   - Bypass: где и зачем.
3. **Известные ограничения GitHub.**
   - Picker: 7-дневное окно «засветки» check.
   - Required check по имени job'а, не по UI-имени.
   - Gate-job pattern как обход.
4. **GitLab Protected Branches / Tags.**
   - Что настроено (по факту — только `master`).
   - Терминология: Protected Branches, Protected Tags,
     Merge Request Approval Rules.
   - Что делать при активации GitLab CI (синхронизация
     с GitHub).
5. **Таблица эквивалентов.** GitHub ↔ GitLab.
6. **Процедуры.**
   - Добавить новый required check.
   - Восстановиться после ошибочного тега.
   - Переключить CI на GitLab (ссылка на `docs/ci-cd.md`,
     когда появится).

**Шаги:**

1. Собрать текущее состояние Rulesets из
   `docs/git-flow.md`, `docs/ci-roadmap.md` (закрытая
   задача Gate-job).
2. Проверить фактическое состояние на GitHub:
   Settings → Rules. Убедиться, что описание совпадает
   с реальностью.
3. Проверить GitLab: Protected Branches / Tags,
   CI/CD Variables, MR Approval Rules.
4. Написать `docs/repo-protection.md` по структуре выше.
5. Добавить ссылки из `docs/git-flow.md` и
   `docs/ci-roadmap.md`.
6. Проверить `grep`: файл не битый, ссылки взаимны.

**Референсы:**
- GitHub Docs → Rulesets, Required status checks.
- GitLab Docs → Protected branches, Protected tags,
  Merge request approvals.
- `docs/ci-roadmap.md` → `### ✅ Gate-job для required
  checks` — практический кейс с picker'ом.

**Ограничения:**
- Не дублировать содержимое `docs/git-flow.md` — там
  схема веток и merge. Здесь — только защита.
- Не дублировать `docs/release-process.md` — там
  процедура релиза. Здесь — только правила платформы.
- GitLab-раздел — по факту (защищён только `master`),
  не выдумывать будущее.

**Критерий готовности:**
- `docs/repo-protection.md` создан, покрывает все
  перечисленные разделы.
- Ссылки из `docs/git-flow.md` и `docs/ci-roadmap.md`
  добавлены.
- Информация соответствует фактическому состоянию
  GitHub и GitLab.
- `grep` по проекту не находит битых ссылок.

**Оценка:** ~1.5 часа.

**Зависимости:** нет.

**Открытые вопросы:** нет.





### Role emulation для тестировщика

**Мобильная работа:** полное закрытие. Всё делается через
web Settings GitHub + правка `docs/git-flow.md`.

**Статус:** отложено. **Триггер:** появление реального
второго участника (тестировщика или разработчика).

**Контекст:** этап 5 плана GitFlow. Второй GitHub-аккаунт
нужен, чтобы approve на PR был **реальным** шагом, а не
формальностью. Пока в проекте один человек — эмуляция
роли тестировщика фиктивным аккаунтом создаёт overhead
(двойной логин на каждый PR) без практического смысла.

**Предусловия:**
- Появился реальный второй участник (человек, а не
  фиктивный аккаунт).

**Файлы:**
- `docs/git-flow.md` — раздел о ролях и требуемых approve.
- GitHub Settings → Collaborators.
- Rulesets.

**Шаги:**

1. Добавить второго участника как Collaborator с ролью
   **Write** (роль Read или Triage **не может одобрять
   PR** — это ограничение GitHub security model).
2. В Rulesets для `dev`, `release/**`, `hotfix/**` поднять
   `Required approvals: 1`.
3. В `docs/git-flow.md` добавить раздел «Роли» с описанием:
   кто может approve, что даёт approve, как выглядит
   процесс.
4. Проверить на тестовом PR: PR без approve → merge
   заблокирован; approve от второго участника → merge
   разрешён.

**Референсы:**
- GitHub Docs → Repository roles.
- `docs/git-flow.md` → «Осознанные отклонения».

**Ограничения:**
- Не создавать фиктивный аккаунт «для тренировки» — ждать
  реального участника.
- Не давать роль выше Write — read-only и triage не
  подходят из-за невозможности approve.

**Критерий готовности:**
- Второй участник добавлен как Collaborator.
- Required approvals: 1 на всех PR в `dev` / `release/**` /
  `hotfix/**`.
- Процесс задокументирован в `docs/git-flow.md`.

**Оценка:** ~30 минут.

**Зависимости:** нет.

**Открытые вопросы:**
- Роль второго участника: Write или Maintainer? Решить
  при появлении. Для approve достаточно Write.

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

### Hot-swap CI на GitLab

**Мобильная работа:** частичное. Планирование и правки
`.gitlab-ci.yml` / `docs/ci-cd.md` — через proot. Проверка
пайплайна — только после появления self-hosted runner'а
(блокируется отдельной задачей).

**Статус:** запланировано. **Скелет.** Детализация — после
внедрения GitFlow.

**Контекст:** GitLab задуман как горячий резерв CI/CD на
случай аварии GitHub. Сейчас:
- зеркалирование GitHub → GitLab **не настроено**;
- `.gitlab-ci.yml` сохранён с эпохи до перехода на GitHub
  Actions, использует устаревший образ
  (`mobiledevops/android-sdk-image:36.1.0`) и старую схему
  триггеров (`dev`/`master`/MR/tag без GitFlow);
- GitLab CI/CD Variables (keystore, пароли) — сохранены
  и актуальны;
- GitLab Protected Branches — только `master`;
- Free Tier квота исчерпана, pipeline не запускается;
- self-hosted runner — отдельная задача
  (`### Self-hosted GitLab Runner через Ansible + Podman`).

**Цель:** полное зеркало CI/CD (вариант «в» из обсуждения).
Возможность лёгкого, бесшовного переключения между GitHub
и GitLab как CI/CD-инфраструктурой.

**Почему скелет, а не полный план:** структура job'ов и
триггеров в `.gitlab-ci.yml` должна быть зеркалом GitHub
Actions, которая сама будет перестроена на этапе 3 GitFlow
(«Реструктуризация workflow»). Планировать `.gitlab-ci.yml`
до этого — двойная работа.

**Что фиксируется сейчас (не зависит от GitFlow):**

1. **Зеркалирование GitHub → GitLab.** Настроить
   автоматический push-mirror из GitHub Settings
   (Settings → Repository → Mirroring). Проверить, что
   ветки и теги уезжают.
2. **Образ.** Обновить `mobiledevops/android-sdk-image`
   до актуальной версии (на момент старта задачи).
3. **`when: manual` на всех job'ах.** Квота Free Tier
   исчерпана, pipeline не запускается. Когда runner
   появится — все job'ы должны требовать ручного запуска,
   чтобы случайно не сжечь лимиты.
4. **Синхронизация секретов.** CI/CD Variables в GitLab
   (keystore, пароли, `KEY_*`) — проверить, что
   соответствуют текущей схеме GitHub. Если GitHub
   переходит на Environment secrets (этап 4 GitFlow) —
   отразить это и в GitLab (protected variables для
   тегов `v*`).
5. **`docs/ci-cd.md`** — новый документ. Процедура
   переключения:
    - как активировать pipeline в GitLab вручную;
    - как отключить GitHub Actions (чтобы не было дублей);
    - что делать с секретами;
    - как запустить конкретный job.
6. **Protected Branches / Tags в GitLab** — привести в
   соответствие с GitHub Rulesets после этапа 1 GitFlow.

**Что откладывается до GitFlow:**

- Структура job'ов (fast / full / release / production).
- Триггеры `rules:` под `feature/*`, `release/**`,
  `hotfix/**`.
- Схема publish (GitHub Release / Play Store / GitLab
  Release).
- Интеграция с Play Store / Firebase, когда они появятся.

**Предусловия:**
- Этап 1 GitFlow (Rulesets) — хотя бы для `dev`, чтобы
  схема веток устоялась.
- Self-hosted GitLab Runner (`### Self-hosted GitLab Runner
  через Ansible + Podman`) — иначе pipeline не запустить.

**Файлы:**
- `.gitlab-ci.yml` — правки (образ, `when: manual`).
- `docs/ci-cd.md` — новый документ.
- `docs/release-process.md` — обновить раздел «Релиз через
  GitLab CI» после реализации.

**Оценка:** ~3 часа после снятия предусловий (переписать
`.gitlab-ci.yml`, написать `docs/ci-cd.md`, прогнать).

**Зависимости:**
- **Блокируется** «Переход на полный GitFlow» (структура
  workflow).
- **Блокируется** «Self-hosted GitLab Runner через Ansible
    + Podman» (возможность запуска pipeline).

**Открытые вопросы:**
- Настроить зеркалирование: push из GitHub Settings или
  pull в GitLab? Проверить при старте.
- Хранить ли `.gitlab-ci.yml` в актуальном состоянии
  постоянно, или обновлять только при активации
  (перед переключением)? Решить — это влияет на объём
  поддержки.
- Единая структура job'ов в GitHub Actions и GitLab CI,
  или допускается расхождение в деталях? Решить на
  этапе детализации после GitFlow.

---

### Play Store Internal Testing

**Мобильная работа:** частичное. Планирование и правка
`.github/workflows/ci.yml` — через proot. Настройка Play
Console и Service Account — через web. Реальная проверка
публикации — только на десктопе или после первой сборки.

**Статус:** запланировано. **Заблокировано** созданием
Google Play Console listing.

**Контекст:** после этапа 4a (Environment secrets для
релиза) остался нереализованным release-tier: push в
`release/**` должен собирать `.aab` и публиковать его в
Play Store Internal Testing для QA. Блокер — отсутствие
Play Console listing и Service Account JSON.

**Предусловия:**
- Создан Google Play Console listing для приложения.
- Сгенерирован Service Account JSON с правами на публикацию
  в Internal Testing.
- Listing заполнен (иконки, скриншоты, описание) — минимум
  для внутреннего трека.

**Файлы:**
- `.github/workflows/ci.yml` — добавить job `release-build`.
- GitHub Settings → Environments → `release` — добавить
  secret `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.

**Шаги:**

1. Создать listing в Play Console (если ещё нет).
2. Сгенерировать Service Account JSON в Google Cloud
   Console, привязать к Play Console.
3. Добавить JSON как Environment secret `release`.
4. Добавить job `release-build` в `ci.yml`:
    - триггер: `push` в `release/**`;
    - `needs: fast-check`;
    - `environment: release`;
    - сборка `:app:bundleRelease` с подписью из Environment
      secrets;
    - upload `.aab` в Play Internal Testing через
      `r0adkll/upload-google-play@v1`;
    - track `internal`.
5. Обновить Ruleset `Protect release branches`: добавить
   `CI / release-build` в required checks.
6. Тестовая проверка: срезать `release/v0.0.1-test` от
   `dev`, запушить, дождаться зелёного workflow, удалить
   ветку и тестовую публикацию.

**Референсы:**
- `r0adkll/upload-google-play` — GitHub Action.
- `docs/git-flow.md` → «Жизненный цикл релиза».

**Ограничения:**
- Не публиковать в Production на этом этапе — только
  Internal.
- Не менять `release.yml` (тег `v*`) — production-tier
  отдельная задача.
- `versionCode` для Play Store должен строго возрастать —
  проверить, что формула из semver даёт валидные значения.

**Критерий готовности:**
- Push в `release/**` публикует `.aab` в Play Internal
  Testing.
- Required check `CI / release-build` настроен.
- Публикация видна в Play Console для тестировщиков.

**Оценка:** ~4 часа (включая настройку Play Console и
Service Account).

**Зависимости:**
- Блокируется Play Console listing.
- Разблокирует «Play Store Production» (общий Service
  Account).

**Открытые вопросы:** нет.

### Play Store Production

**Мобильная работа:** частичное. Правка `.github/workflows/release.yml`
— через proot. Проверка публикации — только на десктопе
(сборка с подписью и upload в Play).

**Статус:** запланировано. **Заблокировано** «Play Store
Internal Testing» (общий Service Account).

**Контекст:** production-tier — публикация релиза в
Production track Play Store. Привязан к тегу `v*` на
`master`. Расширяет существующий `release.yml`.

**Предусловия:**
- Выполнен «Play Store Internal Testing».
- Service Account JSON с правами на публикацию в Production.

**Файлы:**
- `.github/workflows/release.yml` — расширить job
  `build-and-release`.

**Шаги:**

1. Добавить в `release.yml` после шага «Create GitHub
   Release»:
    - сборка `:app:bundleRelease`;
    - upload `.aab` в Play Production (track `production`,
      status `draft` для ручного approve в Play Console).
2. Проверить, что Environment `release` уже привязан к
   job'у (сделано в 4a).
3. Тестовый прогон: пробный тег `v0.0.x-test` → дождаться
   зелёного workflow → проверить, что `.aab` появился в
   Play Console как draft → удалить draft и тег.

**Референсы:**
- `r0adkll/upload-google-play` → параметр `status: draft`.
- `docs/git-flow.md` → «Жизненный цикл релиза».

**Ограничения:**
- Публикация в Production — только `status: draft`. Релиз
  в прод делает человек через Play Console.
- Не менять сборку APK для GitHub Releases — она остаётся
  как есть, `.aab` собирается дополнительно.

**Критерий готовности:**
- Tag `vX.Y.Z` на `master` создаёт:
    - GitHub Release с APK (как раньше);
    - `.aab` в Play Console Production track со статусом
      `draft`.

**Оценка:** ~2 часа.

**Зависимости:** блокируется «Play Store Internal Testing».

**Открытые вопросы:** нет.

---

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

---

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

---
