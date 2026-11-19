<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: ci-roadmap-plan -->
## Запланировано










<!-- SUBSECTION_BEGIN: c-1 -->
___
### [PLAN] C-1 Markdown linter в CI
**Мобильная работа:** полное закрытие. Всё через proot
(правки `.yml`) + web (проверка PR). Локальная сборка
не нужна.

**Статус:** запланировано.

**Триггер:** структурный артефакт в `.md`, прошедший
зелёный гейт. Первый случай — 2026-10-21, двойной `---`
после переноса секции из «Планируемых» в «Закрытые»:
`testDebugUnitTest / lintDebug / ktlintCheck / detekt` —
зелёные, артефакт в файле.

**Контекст:** ни один из гейтов CI не валидирует `.md`.
Ручная проверка (`git diff` + чек-лист в AGENTS.md 5a) —
работает, но:
- пропускает структурные ошибки, невидимые в diff
  (trailing whitespace, дубликаты `---`);
- не ловит битые ссылки на перемещённые/удалённые секции
  (частая проблема при реорганизации ROADMAP);
- не масштабируется на PR от второго разработчика.

**Варианты:**

- **A. `markdownlint-cli2`** (Node.js, отдельный CI-job
  `markdown-lint`). Стабильный, огромное сообщество,
  покрывает всё: fences, headers, trailing whitespace,
  ссылки, таблицы. Минус — Node-зависимость в CI.
- **B. `markdownlint-gradle-plugin`** — Gradle-плагин,
  встраивается в `:app:check`. Минус — давно не обновлялся,
  риск несовместимости с Gradle 9.x.
- **C. `lychee`** (Rust) — только битые ссылки. Не покрывает
  структуру.

Рекомендация: **A**. Проверенное решение, отдельный job
не блокирует `gate` (docs-only коммиты не должны
задерживать код-merge). Запуск — только на PR и push
в защищённые ветки.

**Файлы:**
- `.github/workflows/ci.yml` — новый job `markdown-lint`
  (не в `needs` для `gate`).
- `AGENTS.md` → 5a: обновить «Исключение: docs-only» —
  вместо ручного чек-листа ссылаться на CI-job.

**Шаги:**
1. Проверить актуальность `markdownlint-cli2` (последняя
   версия, установка через `npx`).
2. Создать `.markdownlint.yaml` в корне (правила: line-length
   отключить, чтобы не бороться с длинными строками
   в ROADMAP).
3. Добавить job `markdown-lint` в `ci.yml`:
   - триггер: PR + push в `dev`, `master`;
   - `npx markdownlint-cli2 "docs/**/*.md" "*.md"`.
4. Проверить на тестовом PR — PR с артефактом должен
   падать; без — проходить.
5. (Опционально) required check в Rulesets — не сразу,
   после стабилизации.
6. Обновить AGENTS.md → 5a: ручной чек-лист заменяется
   ссылкой на CI-job.

**Референсы:**
- `markdownlint-cli2`: `https://github.com/DavidAnson/markdownlint-cli2`
- Правила: `https://github.com/DavidAnson/markdownlint/blob/main/doc/Rules.md`
- Текущий гейт: `AGENTS.md` → 5a.

**Ограничения:**
- Не блокировать merge в `dev` на этом job'е сразу —
  сначала накопить опыт, потом в required checks.
- Не проверять `.md` в `app/` и `transport-ktor/` (нет
  файлов, но на будущее — glob `docs/**/*.md` + `*.md`
  в корне).
- `line-length` отключить: в ROADMAP/ISSUES длинные строки
  с URL и кодом — норма.

**Критерий готовности:**
- Job `markdown-lint` зелёный на текущем `dev`.
- PR с намеренным артефактом (двойной `---`) — красный.
- AGENTS.md обновлён: 5a ссылается на job.

**Оценка:** ~40 минут (проверка версии 5, конфиг 10,
job 15, проверка на PR 10).

**Зависимости:** нет.

**Открытые вопросы:**
- `markdownlint-cli2` через `npx` (без pin) или через
  `package.json` с pin? Рекомендация — `package.json` +
  `package-lock.json`, чтобы версия была зафиксирована.
- Отдельный job или шаг в существующем `fast-check`?
  Рекомендация — отдельный job: доки не должны попадать
  в required gate, но должны быть проверены.




___
<!-- SUBSECTION_END: c-1 -->










<!-- SUBSECTION_BEGIN: c-2 -->
___
### [PLAN] C-2 GitHub → GitLab mirror через GitHub Action
**Мобильная работа:** полное закрытие. Всё через proot
(правки `.yml`) + web (настройка токенов). Локальная
сборка не требуется.

**Статус:** запланировано. **Хороший кандидат для работы
с телефона.**

**Контекст:** основной CI — GitHub Actions. GitLab —
резерв на случай аварии (см. `docs/ci-roadmap.md` →
«Hot-swap CI на GitLab»). Для работающего резерва нужна
актуальная история в GitLab.

**Что не работает (встроенное):**

- **GitHub push mirror** (Settings → Repository → Mirroring)
  — Enterprise only.
- **GitLab mirror** (Settings → Repository → Mirroring
  repositories) — Premium+.

Оба факта проверить при старте задачи: тарифные линейки
могли измениться.

**Альтернатива — GitHub Action с `git push --mirror`:**

- на каждый push/delete в GitHub (любая ветка) workflow
  делает `git clone --mirror` и `git push --mirror` в GitLab;
- ~10 строк YAML;
- работает на Free-тарифах обоих сервисов.

**Предусловия:**
- Проект `biomorf/tscalp` на GitLab существует (по README
  badge — да).
- В GitLab создан deploy token с `write_repository` или
  PAT с `write_repository`.

**Файлы:**
- `.github/workflows/mirror.yml` (новый) — workflow
  зеркалирования.
- GitHub Settings → Secrets → `GITLAB_MIRROR_TOKEN`.

**Шаги:**

1. В GitLab: Settings → Repository → Deploy tokens →
   создать `github-mirror`, scope `write_repository`.
2. В GitHub: Settings → Secrets and variables → Actions →
   добавить `GITLAB_MIRROR_TOKEN` со значением токена.
3. Создать `.github/workflows/mirror.yml` (шаблон):

   ~~~yaml
   name: Mirror to GitLab
   on:
     push:
     delete:
   jobs:
     mirror:
       runs-on: ubuntu-latest
       steps:
         - uses: actions/checkout@v6
           with:
             fetch-depth: 0
         - run: |
             git fetch --prune --tags
             git push --mirror \
               https://oauth2:${{ secrets.GITLAB_MIRROR_TOKEN }}@gitlab.com/biomorf/tscalp.git
   ~~~

4. Проверить на тестовом коммите: пуш в `dev` GitHub →
   workflow зелёный → в GitLab `dev` синхронизирован.
5. Проверить защищённые ветки GitLab: `master` защищена
   (Protected Branches). `--mirror` пытается пушить все
   ветки, включая `master` — deploy token с
   `write_repository` **обойдёт** protected. Это осознанно:
   зеркало должно быть точным. При необходимости —
   ограничить Protected Branches в GitLab или дать токену
   bypass.
6. Обновить `docs/ci-roadmap.md`: задачу «Hot-swap CI на
   GitLab» — пункт «Зеркалирование» закрыть, с
   перекрёстной ссылкой на эту задачу.

**Референсы:**
- `actions/checkout` — GitHub Marketplace. Версия может
  быть v6+, проверить при старте.
- GitLab Deploy Tokens:
  `https://docs.gitlab.com/ee/user/project/deploy_tokens/`.
- GitLab Protected Branches:
  `docs/repo-protection.md` → «GitLab Protected
  Branches / Tags».

**Ограничения:**
- Не зеркалировать GitHub Actions artifacts — только
  git-история.
- Не пушить `--tags` иначе как через `--mirror`: `--tags`
  отдельно может конфликтовать с защищёнными тегами
  GitLab.
- Не коммитить токен в код — только через Secrets.
- Проверить: `push --mirror` удаляет ветки в GitLab, если
  их нет в GitHub. Если в GitLab есть локальные
  ветки (например, эксперименты), зеркало их **удалит**.
  При необходимости — использовать `git push origin
  --all` + `--tags` вместо `--mirror` (без удаления
  лишних).

**Критерий готовности:**
- Push в любую ветку GitHub → GitLab синхронизирован в
  течение ~30 секунд.
- Delete ветки в GitHub → удалена в GitLab.
- Теги синхронизируются.
- Workflow зелёный, история GitLab совпадает с GitHub
  (`git log --oneline` совпадает).

**Оценка:** ~10 минут активной работы (токены 3, YAML 3,
проверка 4).

**Зависимости:** нет. **Разблокирует** «Hot-swap CI на
GitLab» — без зеркала переключение на резерв невозможно.

**Открытые вопросы:**
- `--mirror` (полная точная копия, удаляет лишнее) или
  `--all + --tags` (только добавление, без удаления)?
  Рекомендация — `--mirror`, если в GitLab нет
  локальных экспериментов.
- Должен ли workflow запускаться на push в защищённые
  ветки (`dev`, `master`)? Скорее да — именно их история
  важна для резерва.
- Что делать при падении mirror workflow: отдельный
  алерт/issue, или игнорировать и разбираться при
  реальном переключении на GitLab? Рекомендация —
  игнорировать до реального сбоя, но добавить в
  monitoring, когда он появится.




___
<!-- SUBSECTION_END: c-2 -->










<!-- SUBSECTION_BEGIN: c-3 -->
___
### [PLAN] C-3 Codecov: diff coverage в PR и бейдж покрытия
**Мобильная работа:** полное закрытие. Всё делается через
proot (правки `.yml`) + web (регистрация, настройка,
наблюдение за PR). Локальная Gradle-сборка не требуется.

**Статус:** запланировано. **Хороший кандидат для работы
с телефона.**

**Контекст:** Kover считает покрытие и проверяет общий
порог (`minBound(3)`), но не показывает, как **изменение
в PR** повлияло на покрытие. Codecov даёт:

- PR-комментарий с **diff coverage** — процент покрытия
  именно изменённых строк;
- **статус-чек** в PR (можно сделать обязательным в
  Rulesets, как `gate`);
- **историю покрытия** по коммитам и веткам — видно,
  когда и что просело;
- **бейдж в README** — обновляется автоматически.

Для **публичного** репозитория Codecov бесплатен, токен
не обязателен (`GITHUB_TOKEN` достаточно).

**Предусловия:**
- Kover настроен и генерирует XML (`koverXmlReportDebug`
  в `ci.yml` уже есть).
- Репозиторий на GitHub — публичный (`biomorf/TScalp`).

**Файлы:**
- `.github/workflows/ci.yml` — добавить шаг загрузки
  отчёта в Codecov после `koverXmlReportDebug`.
- `codecov.yml` (новый, корень) — настройка порогов и
  игнорирования путей.
- `README.md` — добавить бейдж покрытия рядом с CI-бейджем.
- `docs/ci-roadmap.md` — этот файл (обновление статуса
  при закрытии).

**Шаги:**

1. Зарегистрироваться на `https://codecov.io` через
   GitHub OAuth. Дать доступ к репозиторию
   `biomorf/TScalp`.
2. Взять **upload token** из настроек репозитория на
   Codecov (для публичного репо не обязателен, но лучше
   добавить — защита от подмены).
3. Добавить в GitHub **Repository secrets**:
   `CODECOV_TOKEN`.
4. В `.github/workflows/ci.yml` — после шага с
   `koverXmlReportDebug` добавить:

   ~~~yaml
   - name: Upload coverage to Codecov
     uses: codecov/codecov-action@v5
     with:
       files: app/build/reports/kover/reportDebug.xml
       fail_ci_if_error: false
     env:
       CODECOV_TOKEN: ${{ secrets.CODECOV_TOKEN }}
   ~~~

   `fail_ci_if_error: false` — на первом этапе. Когда
   интеграция стабилизируется — можно переключить на
   `true`, чтобы падение upload'а блокировало merge.
5. Создать `codecov.yml` в корне (минимальный):

   ~~~yaml
   coverage:
     status:
       project:
         default:
           target: auto
           threshold: 1%
       patch:
         default:
           target: 50%
   ignore:
     - "app/src/main/kotlin/com/gitlab/biomorf/tscalp/di/**"
     - "app/src/main/kotlin/com/gitlab/biomorf/tscalp/ui/theme/**"
   comment:
     layout: "diff, files"
   ~~~

   Значения — стартовые, уточняются по факту (текущее
   покрытие ~4.5% — `target: auto` покажет тренд, не
   абсолют).
6. Проверить на тестовом PR: открыть маленький PR,
   дождаться, что Codecov оставил комментарий и статус-чек
   прошёл.
7. (Опционально) В Rulesets `Protect dev` добавить
   `codecov/project` в required checks — блокировать PR,
   если покрытие упало больше чем на 1%.
8. В `README.md` добавить бейдж:

   ~~~markdown
   [![codecov](https://codecov.io/gh/biomorf/TScalp/branch/dev/graph/badge.svg)](https://codecov.io/gh/biomorf/TScalp)
   ~~~

9. Обновить этот файл: перенести задачу в «Закрытые».

**Референсы:**
- Codecov: `https://about.codecov.io/`
- `codecov/codecov-action` на GitHub Marketplace (v5).
- Kover: `docs/ci-roadmap.md` → «Coverage в unit-tests».
- Kover XML-отчёт: `app/build/reports/kover/reportDebug.xml`.

**Ограничения:**
- Не переключать `fail_ci_if_error: true` до стабилизации
  (первый PR может упасть по причинам Codecov — сеть,
  сервис).
- Не менять общий порог Kover (`minBound(3)`) — Codecov
  работает параллельно, не заменяет.
- `codecov.yml` начинать с консервативных значений
  (`target: auto`, `threshold: 1%`), не с жёстких.
- Бейдж в README — **после** того, как отработает хотя бы
  один PR (иначе бейдж будет `unknown`).

**Критерий готовности:**
- В PR от Codecov приходит комментарий с diff coverage.
- Статус-чек `codecov/project` в PR зелёный.
- Бейдж покрытия в README отображается (не `unknown`).
- История покрытия видна на codecov.io по ветке `dev`.

**Оценка:** ~30–40 минут активной работы (регистрация 5,
правка `ci.yml` 10, `codecov.yml` 10, проверка 10).

**Зависимости:** нет. **Разблокирует** контроль diff
coverage в PR; может стать обязательным чеком в Rulesets
позже.

**Открытые вопросы:**
- `target: 50%` для patch — стартовое, проверить на первом
  PR. Текущее общее покрытие ~4.5%, patch-порог может
  оказаться слишком строгим или наоборот.
- Игнорировать ли `presentation/screens/**` в `codecov.yml`
  (UI-код, покрытие unit-тестами малополезно). Решить
  после первого PR.
- Добавлять ли `codecov/project` в required checks
  Rulesets сразу или после стабилизации. Рекомендация —
  после.




___
<!-- SUBSECTION_END: c-3 -->










<!-- SUBSECTION_BEGIN: c-6 -->
___
### [PLAN] C-6 Hot-swap CI на GitLab
**Мобильная работа:** частичное (live-тест пайплайна после self-hosted runner).

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




___
<!-- SUBSECTION_END: c-6 -->













<!-- SUBSECTION_BEGIN: c-9 -->
___
### [PLAN] C-9 Desktop: pre-push hook (ktlintFormat + ktlintCheck)
**Мобильная работа:** полное закрытие (desktop; на mobile hook не работает — Gradle в proot нестабилен).

**Статус:** запланировано.

**Контекст:** на desktop `./gradlew :app:ktlintMainSourceSetCheck :app:ktlintTestSourceSetCheck` — ~10 сек. В CI — 2–3 мин на цикл. `pre-push` hook запускает ktlint **до** push, экономит цикл CI на чисто-стилевых ошибках. На mobile (proot) hook не работает — там CI остаётся единственным гейтом. Для docs-only изменений hook запускает `.md` mini-gate из `AGENTS.md` 5a (trailing ws, double `---`, fence parity, broken `docs/*.md`) — ~5 сек вместо полного Gradle-гейта.

**Соответствие Clean:** соответствует (организационный слой, 2b).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** нет.

**Файлы:**
- `scripts/pre-push.sh` (новый).
- `docs/gradle-setup.md` — инструкция по установке (или `docs/ci-cd.md` — выбрать при старте).

**Шаги:**
1. `scripts/pre-push.sh`:
   - `set -euo pipefail`
   - `./gradlew :app:ktlintMainSourceSetCheck :app:ktlintTestSourceSetCheck`
   - echo-заголовок; exit-код = gradle.
   - **Не** `ktlintFormat` (format меняет файлы — только по явному `--fix`).
2. `chmod +x scripts/pre-push.sh`.
3. `docs/gradle-setup.md` — раздел «Pre-push hook»:
   - `ln -sf ../../scripts/pre-push.sh .git/hooks/pre-push`
   - bypass: `git push --no-verify` (редко, осознанно).
4. Тест: на desktop, `git push` с намеренной ktlint-ошибкой → hook блокирует. `git push --no-verify` — проходит.
5. Commit script + docs.
6. `.md` mini-gate в `scripts/pre-push.sh` — **только когда в staged есть `.md`** (по 5a, Gradle ktlint для docs бесполезен):
   - `git diff --cached --check` (trailing ws);
   - `awk` double `---`;
   - `grep -cE '^(```|~~~)'` parity;
   - broken `docs/*.md` loop (по extract'у каждого файла, не глобально).
   - Источник: `AGENTS.md` 5a mini-gate.
   - bypass: `git push --no-verify` (тот же, что для ktlint).

**Тесты:** shell-hook (не unit).

**Референсы:** M016 (PR #112) — 4 push-цикла, мотивация; AGENTS 4f; `gradle/actions/setup-gradle@v5`; **`AGENTS.md` 5a — mini-gate (4 команды, источник для `.md`)**.

**Ограничения:**
- Не заменяет CI (mobile не покрывает).
- `git push --no-verify` — escape, не запрещать.
- Не смешивать с задачей `CI: fast-ktlint job`.
- На mobile hook не устанавливается — документация это явно говорит.

**Критерий готовности:**
- `scripts/pre-push.sh` коммитится, исполняемый.
- `docs/gradle-setup.md` — инструкция + bypass.
- На desktop: push с ktlint-ошибкой блокируется; `--no-verify` проходит.
- На desktop: push с docs-изменением + broken link → hook блокирует (mini-gate).
- CI `fast-ktlint` — окончательный гейт (как раньше).

**Оценка:** ~1 ч.

**Зависимости:** нет.

**Связано:** `### CI: fast-ktlint job` (предыдущая).

**Открытые вопросы:** `pre-push` vs `pre-commit`? Рекомендую `pre-push` (не блокирует локальные коммиты, ktlint-fix обычно делается в самом конце).



___
<!-- SUBSECTION_END: c-9 -->





<!-- SECTION_END: ci-roadmap-plan -->
<!-- FILE_END -->
ть job
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



___
<!-- SUBSECTION_END: c-8 -->










<!-- SUBSECTION_BEGIN: c-9 -->
___
### [PLAN] C-9 Desktop: pre-push hook (ktlintFormat + ktlintCheck)
**Мобильная работа:** полное закрытие (desktop; на mobile hook не работает — Gradle в proot нестабилен).

**Статус:** запланировано.

**Контекст:** на desktop `./gradlew :app:ktlintMainSourceSetCheck :app:ktlintTestSourceSetCheck` — ~10 сек. В CI — 2–3 мин на цикл. `pre-push` hook запускает ktlint **до** push, экономит цикл CI на чисто-стилевых ошибках. На mobile (proot) hook не работает — там CI остаётся единственным гейтом. Для docs-only изменений hook запускает `.md` mini-gate из `AGENTS.md` 5a (trailing ws, double `---`, fence parity, broken `docs/*.md`) — ~5 сек вместо полного Gradle-гейта.

**Соответствие Clean:** соответствует (организационный слой, 2b).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** нет.

**Файлы:**
- `scripts/pre-push.sh` (новый).
- `docs/gradle-setup.md` — инструкция по установке (или `docs/ci-cd.md` — выбрать при старте).

**Шаги:**
1. `scripts/pre-push.sh`:
   - `set -euo pipefail`
   - `./gradlew :app:ktlintMainSourceSetCheck :app:ktlintTestSourceSetCheck`
   - echo-заголовок; exit-код = gradle.
   - **Не** `ktlintFormat` (format меняет файлы — только по явному `--fix`).
2. `chmod +x scripts/pre-push.sh`.
3. `docs/gradle-setup.md` — раздел «Pre-push hook»:
   - `ln -sf ../../scripts/pre-push.sh .git/hooks/pre-push`
   - bypass: `git push --no-verify` (редко, осознанно).
4. Тест: на desktop, `git push` с намеренной ktlint-ошибкой → hook блокирует. `git push --no-verify` — проходит.
5. Commit script + docs.
6. `.md` mini-gate в `scripts/pre-push.sh` — **только когда в staged есть `.md`** (по 5a, Gradle ktlint для docs бесполезен):
   - `git diff --cached --check` (trailing ws);
   - `awk` double `---`;
   - `grep -cE '^(```|~~~)'` parity;
   - broken `docs/*.md` loop (по extract'у каждого файла, не глобально).
   - Источник: `AGENTS.md` 5a mini-gate.
   - bypass: `git push --no-verify` (тот же, что для ktlint).

**Тесты:** shell-hook (не unit).

**Референсы:** M016 (PR #112) — 4 push-цикла, мотивация; AGENTS 4f; `gradle/actions/setup-gradle@v5`; **`AGENTS.md` 5a — mini-gate (4 команды, источник для `.md`)**.

**Ограничения:**
- Не заменяет CI (mobile не покрывает).
- `git push --no-verify` — escape, не запрещать.
- Не смешивать с задачей `CI: fast-ktlint job`.
- На mobile hook не устанавливается — документация это явно говорит.

**Критерий готовности:**
- `scripts/pre-push.sh` коммитится, исполняемый.
- `docs/gradle-setup.md` — инструкция + bypass.
- На desktop: push с ktlint-ошибкой блокируется; `--no-verify` проходит.
- На desktop: push с docs-изменением + broken link → hook блокирует (mini-gate).
- CI `fast-ktlint` — окончательный гейт (как раньше).

**Оценка:** ~1 ч.

**Зависимости:** нет.

**Связано:** `### CI: fast-ktlint job` (предыдущая).

**Открытые вопросы:** `pre-push` vs `pre-commit`? Рекомендую `pre-push` (не блокирует локальные коммиты, ktlint-fix обычно делается в самом конце).
<!-- SUBSECTION_END: c-9 -->






<!-- SECTION_END: ci-roadmap-plan -->
<!-- FILE_END -->
