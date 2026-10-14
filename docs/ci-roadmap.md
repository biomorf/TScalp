# CI-ROADMAP

План CI/CD, ветвления, релизного процесса, code quality.
Формат — секция на задачу, со статусом и оценкой.

План разработки → `docs/ROADMAP.md`.

---

## Планируемые задачи

### Переход на полный GitFlow

**Мобильная работа:** частичное. Rulesets (через web Settings),
документация (`.md` в proot), workflow-правки (proot + push)
— доступны с телефона. Проверка CI — через Actions UI.
Первая настоящая проверка полного цикла (сборка release
bundle, публикация в Play Store) — на десктопе.

**Статус:** в работе. Реализуется поэтапно.
**Ветка:** feat/git-flow

**Прогресс:**

- [x] Этап 1 — Rulesets
- [x] Этап 2 — Соглашение о ветках и merge-стратегиях
- [x] Этап 3 — Реструктуризация workflow
- [ ] Этап 4 — Environment secrets + публикация
- [ ] Этап 5 — Role emulation для тестировщика
- [ ] Этап 6 — Документация

**Контекст:** сейчас упрощённая схема: `master` защищён,
`dev` — нет, релизы через теги из `dev`, публикация в
GitHub Releases. Планируется: Play Store, отдельный
тестировщик (role emulation через второй GitHub-аккаунт),
рост команды. Организационный слой нужно подготовить
заранее — см. AGENTS.md → правило 2b.

**Осознанные отклонения от канона (зафиксировать в
`docs/git-flow.md`):**

1. **Имя ветки `dev`** вместо канонического `develop`.
   Причина: миграция имени = rulesets, workflows, docs,
   локальные ветки у всех участников. Не стоит этого.
2. **Merge-стратегия — Merge commit везде**, включая
   `feature/* → dev`. Канон GitFlow рекомендует Squash для
   feature-ветвей; у нас стратегия завязана на сохранение
   SHA между `dev` и `master` (см. `docs/release-process.md`).
   Squash переписывает SHA и приводит к расхождению ветвей.
   Отдельная задача «Рассмотреть Squash vs Merge commit»
   исследует вопрос.

**Предусловия:** нет.

**Этапы:**

#### Этап 1 — Rulesets

**Файлы:** GitHub Settings → Rules → Rulesets.

**Что сделать:**

1. Защитить `dev` как `master`:
   - Require a pull request before merging;
   - Required approvals: 0 (пока один разработчик; вернуться
     при появлении второго);
   - Require status checks to pass (`CI / build`);
   - Block force pushes;
   - Restrict deletions.
2. Ruleset для `release/**`:
   - Require PR (source: `dev`);
   - Block force pushes, restrict deletions.
3. Ruleset для `hotfix/**`:
   - Require PR (source: `master`);
   - Block force pushes, restrict deletions.

**Оценка:** ~30 минут.

#### Этап 2 — Соглашение о ветках и merge-стратегиях

**Файлы:** `docs/git-flow.md` (новый), `docs/release-process.md`
(правка), `AGENTS.md` (правила работы с ветками).

**Структура веток:**

- `master` — только релизы. Защищена.
- `dev` — интеграция. Защищена. PR только из
  `feature/*`, `bugfix/*`, `release/*`, `hotfix/*`.
- `feature/<name>` — от `dev`. Одна фича = одна ветка.
- `bugfix/<issue-id>` — от `dev`. Один баг = одна ветка.
- `release/<vX.Y.Z>` — от `dev`. Стабилизация перед
  релизом. Сюда — только фиксы, не фичи.
- `hotfix/<vX.Y.Z>` — от `master`. Экстренный фикс прода.
  После закрытия — обратный merge в `master` и `dev`.

**Стратегии merge:**

- `feature/* → dev`: Merge commit. SHA коммитов сохраняются.
- `bugfix/* → dev`: Merge commit.
- Создание `release/*`: ветка от `dev` (без merge).
- `release/* → master`: Merge commit + тег `vX.Y.Z`.
- `master → dev` (обратный merge после релиза): Merge commit.
- `hotfix/* → master`: Merge commit + тег `vX.Y.(Z+1)`.
- `hotfix/* → dev`: Cherry-pick или Merge commit — решить
  на практике при первом hotfix.

**Что отменяется:** схема `dev → master` напрямую (через
Merge commit) больше **не используется**. Релизы идут через
`release/*`. Схема из «Переезд CI на GitHub Actions»
сохраняется как историческая запись, но новым соглашением
отменяется. Пометку о пересмотре добавить в
`docs/release-process.md`.

**Оценка:** ~1 час.

#### Этап 3 — Реструктуризация workflow

**Файлы:** `.github/workflows/`.

**Решение по структуре файлов:** один `ci.yml` с двумя
job'ами и `if:`-условиями. Разбивка на несколько файлов
отклонена — required check `CI / fast-check` должен
существовать в одном месте, а `if:`-условия для двух
job'ов читаются компактно.

**Уровни проверок (этап 3):**

| Триггер | Job | Что запускается | ~Время |
|---|---|---|---|
| Push в `feature/*`, `bugfix/*` | `fast-check` | `testDebugUnitTest`, `ktlintCheck`, `detekt`, `lintDebug` | 2–3 мин |
| PR в `dev` / `master` / `release/**` / `hotfix/**` | `fast-check` (required) | то же | 2–3 мин |
| Push в `dev` | `full-build` | Fast + `assembleDebug` + kover + артефакт APK | 5–7 мин |

**`master` включён в триггеры PR** — иначе required check
`CI / fast-check` на PR в `master` не появится, и PR
`release/* → master` / `hotfix/* → master` зависнут в
состоянии «Expected — Waiting for status to be reported».

**`paths-ignore` для `pull_request` убираем.** Оставляем
только на `push`. Причина: если PR состоит из docs-only
коммитов и workflow пропускается по триггеру — required
check не создаётся, PR зависает. На push docs-only skip
работает (см. `### ✅ CI: пропускать сборку на docs-only
коммитах`).

**Итого `paths-ignore` в новой конфигурации:**

- `push` — есть (docs-only коммиты не запускают CI).
- `pull_request` — нет (все PR запускают fast-check,
  даже документационные).
**Release-tier (push в `release/**`) и production-tier
(tag `v*` на `master`) переносятся в этап 4** — они
требуют keystore и Environment secrets, без которых
`bundleRelease` не собирается. Логически связаны с
публикацией, идут одной задачей.

**Required check:** после этапа 3 имя обязательной проверки
в Rulesets меняется с `CI / build` на `CI / fast-check`.
Обновить во всех 4 branch-ruleset'ах (`dev`, `master`,
`release/**`, `hotfix/**`).

**Что сохранить:** `paths-ignore` для docs-only коммитов
(уже сделано).

**Оценка:** ~2 часа (один файл `ci.yml` + обновление
Rulesets).

#### Этап 4 — Environment secrets + публикация

**Файлы:** GitHub Settings → Environments,
`.github/workflows/ci.yml` (release-job), `release.yml`
(production-tier), возможно `.github/workflows/release.yml`.

**Что сделать:**

1. Environment `release` с секретами (миграция 4 секретов
   из Repository secrets — уже запланировано отдельной
   задачей «Переход на GitHub Environment secrets», которая
   становится подзадачей этой).
2. Google Play Service Account JSON → Environment secret
   `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.
3. Firebase App Distribution token + appId → Environment
   secrets.
4. Обновить workflow'ы: `environment: release` в job'ах,
   которые публикуют.

**Release-tier (push в `release/**`):**

Добавить в `ci.yml` третий job `release-build`:

- триггер: push в `release/**`;
- `needs: fast-check` (не запускать без зелёного fast);
- сборка `bundleRelease` с подписью (keystore из
  Environment `release`);
- upload `.aab` в Google Play Internal Testing
  (track `internal`);
- `environment: release` — активирует Environment secrets
  и потенциальные approval-гейты.

**Production-tier (tag `v*` на `master`):**

Обновить существующий `release.yml`:

- добавить `environment: release` в job `build-and-release`;
- текущая логика (сборка APK, публикация в GitHub Releases)
  сохраняется;
- дополнительно — upload `.aab` в Google Play Production
  (track `production`, status `draft` для ручного approve
  в Play Console);
- keystore уже используется в `release.yml` — перевести
  на Environment secrets (пункт 4).

**Required check в Rulesets:** добавить `CI / release-build`
как обязательный для `release/**` ruleset. Для `dev` /
`master` / `hotfix/**` — остаётся `CI / fast-check`.

**Итоговая матрица триггеров (после этапа 4):**

| Триггер | Job | Публикация |
|---|---|---|
| Push в `feature/*`, `bugfix/*` | `fast-check` | — |
| PR в `dev` / `release/**` / `hotfix/**` | `fast-check` | — |
| Push в `dev` | `full-build` | — |
| Push в `release/**` | `release-build` | Google Play Internal |
| Tag `v*` на `master` | `release.yml` | GitHub Release + Google Play Production |

**Оценка:** ~4 часа (Environment secrets + release-job
в `ci.yml` + обновление `release.yml` + Play Store
Service Account).

**Зависимости:**
- **Блокируется** созданием Google Play Console listing
  (нужен для Service Account).
- **Блокируется** регистрацией Firebase проекта (для
  App Distribution token).

#### Этап 5 — Role emulation для тестировщика

**Файлы:** GitHub Settings → Collaborators, Rulesets.

**Что сделать:**

1. Создать второй GitHub-аккаунт (`tscalp-qa` или аналог).
2. Добавить как Collaborator с ролью Read или Triage.
3. Настроить Ruleset `dev` с `Required approvals: 1` —
   PR требует approve от второго аккаунта.
4. Документировать процесс в `docs/git-flow.md`.

**Когда:** после этапов 1–3, когда процесс уже работает
и approve будет реальным шагом, а не формальностью.

**Оценка:** ~30 минут.

#### Этап 6 — Документация

**Файлы:**
- `docs/git-flow.md` (новый) — полный цикл с Mermaid-
  диаграммой, стратегии merge, отклонения от канона.
- `docs/release-process.md` — обновить: убрать `dev → master`
  напрямую, добавить `release/*` и `hotfix/*`.
- `AGENTS.md` — правила работы с ветками (какие ветки, какие
  PR куда, стратегия merge).

**Оценка:** ~1.5 часа.

**Общая оценка:** ~9–10 часов.

**Зависимости:** нет. Блокирует «Переход на GitHub Environment
secrets» (становится этапом 4) и «Hot-swap CI на GitLab»
(структура workflow определяет структуру `.gitlab-ci.yml`).

**Открытые вопросы:**
- Один workflow-файл с `if:` или разбивка на несколько?
  Решить на старте этапа 3.
- Cherry-pick или Merge commit для `hotfix/* → dev`?
  Решить после первого hotfix.
- Как эмулировать тестировщика — Triage-роль (не может
  approve) или Maintainer с ограничениями? Уточнить на
  этапе 5.



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

### CI/CD улучшения

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




#### Self-hosted GitLab Runner через Ansible + Podman

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

#### Переход на GitHub Environment secrets

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

---
