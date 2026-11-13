# CI-ROADMAP

План CI/CD, ветвления, релизного процесса, code quality.
Формат — секция на задачу, со статусом и оценкой.

План разработки → `docs/ROADMAP.md`.




---




## Планируемые задачи




### Markdown linter в CI

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




### GitHub → GitLab mirror через GitHub Action

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




### Codecov: diff coverage в PR и бейдж покрытия

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

**Мобильная работа:** частичное (live-тест разворачивания на удалённом сервере).

**Статус:** в работе.
**Ветка:** ci/self-hosted-runner

**Что:** развернуть GitLab Runner в Podman-контейнере на удалённом сервере (Fedora 43, `51.250.36.160`, 2 CPU, 2 GB RAM) и/или домашней машине (Bluefin 44, i9 13980HX, 64 GB RAM) через Ansible. Один хост активен, второй — резерв. Переключение через inventory.

**Зачем:** квота GitLab Free Tier одноразовая. Self-hosted — единственный путь для регулярных сборок без ограничений.

**Архитектурный принцип:** immutable-подход. **Хост отвечает только за запуск Podman/Docker.** Всё остальное — внутри контейнеров. Никаких `dnf install gitlab-runner`, `useradd gitlab-runner`, `systemctl --user` на хосте. Runner — это контейнер `gitlab/gitlab-runner:latest`, job'ы — вложенные контейнеры через Podman socket хоста.

**Схема:**

- **Control node** (Ansible) — в контейнере: proot на телефоне или toolbox на десктопе. Не на хосте.
- **Managed nodes** — сам хост (Fedora 43 / Bluefin 44), на котором есть только Podman (rootless, пользователь `def`).
- **Runner** — контейнер `gitlab/gitlab-runner:latest` (официальный).
- **Job'ы** — через `docker` executor, пробрасывается хост-Podman socket (`/run/user/1000/podman/podman.sock:/var/run/docker.sock`).

**Технические детали:**

- **Ansible-роль:** проверка Podman, `podman pull gitlab/gitlab-runner:latest`, создание `config.toml` (из шаблона), запуск контейнера, регистрация через `podman run --rm gitlab/gitlab-runner register ...`, автозапуск через `podman generate systemd --new` или `podman kube play`.
- **Регистрация:** GitLab 17+ использует authentication token (`--token glrt-...`), не registration token.
- **Executor:** `docker` (через Podman socket). Для job'ов — образ `eclipse-temurin:17-jdk` (на первом этапе, без Android SDK).
- **Ограничения ресурсов:**
  - `remote`: `concurrent = 1`, `--memory=1g`, `--cpus=2`. На 2 GB RAM работает только `fast-check` (lint, ktlint, detekt, unit-тесты). `assembleDebug` не запускается.
  - `home`: `concurrent = 4`, `--memory=8g`, `--cpus=8`. Полный набор job'ов.
- **Кэш Gradle:** том `gradle-cache` на хосте, монтируется в job-контейнеры.
- **Секреты:** токен раннера в Ansible Vault (`ansible/group_vars/all/vault.yml`), пароль Vault — вне репозитория.
- **Кастомный job-образ:** пока не делаем. На готовых образах. Если не хватит — отдельной задачей.

**Бесшовное переключение:**
- Inventory: одна группа `managed`, хосты `remote` и `home`, у каждого `enabled: true/false`.
- Раннеры регистрируются с разными тегами (`remote`, `home`).
- Переключение — смена `enabled` в `inventory/hosts.yml` + `ansible-playbook`.
- Для CI: `tags:` в `.gitlab-ci.yml` выбирает нужный runner.

**План:**
- [x] Создать структуру `ansible/` и базовые конфиги (`ansible.cfg`, `inventory`, `group_vars`, `host_vars`).
- [x] Настроить Ansible Vault для `become_password` и `runner_token`.
- [x] Подключить `fedora.linux_system_roles` (через `requirements.yml` и `install-requirements.sh`).
- [x] Написать роль `gitlab_runner` (задачи, handlers, шаблон quadlet-юнита).
- [x] Проверить YAML-синтаксис playbook (`ansible-playbook --syntax-check`).
- [ ] Выполнить dry-run на `remote` (`ansible-playbook --check --diff -l remote`).
- [ ] Применить роль на `remote`, проверить, что runner зарегистрирован в GitLab.
- [ ] Перевести job `fast-check` на `remote` runner.
- [ ] Развернуть на `home`, перевести `assembleDebug`.
- [ ] Обновить `docs/ci-cd.md` (когда появится) с процедурой переключения.

**Текущее состояние (2026-10-20):**

- Роль написана, все конфиги на месте.
- Коллекция `fedora.linux_system_roles` v2.5.0 установлена в proot.
- `ansible-playbook --syntax-check` проходит без ошибок.
- **Следующий шаг:** запустить `ansible-playbook playbook.yml --check --diff -l remote` в директории `ansible/`. Ожидается проверка SSH, Vault-пароля и корректности шаблонов без реальных изменений.
- Если dry-run успешен — переход к реальному запуску (без `--check`).
- **Ветка:** `ci/self-hosted-runner` (запушена).

**Структура `ansible/`:**

    ansible/
    ├── ansible.cfg
    ├── playbook.yml
    ├── inventory/hosts.yml
    ├── group_vars/all/{main,vault}.yml
    ├── host_vars/{remote,home}.yml
    └── roles/gitlab_runner/
        ├── tasks/main.yml
        ├── templates/config.toml.j2
        ├── defaults/main.yml
        ├── handlers/main.yml
        └── meta/main.yml

**Оценка:** ~4–5 часов (роль 2 ч, отладка на сервере 1.5 ч, интеграция в `.gitlab-ci.yml` 1 ч, документация 0.5 ч).

**Предусловия:**
- Проект `biomorf/tscalp` на gitlab.com существует.
- Authentication token для runner создан в GitLab UI.
- SSH-доступ к `51.250.36.160` по ключу проверен (`ansible_user: def`).
- Podman установлен на оба хоста (rootless, юзер `def`) — по правилу самодостаточности Ansible-роль проверяет и ставит, если нет.

**Открытые вопросы:**
- `fail2ban` на сервер — пока нет, по решению пользователя.
- `build-release` переносить на self-hosted сразу или после стабилизации? (Пока оставить на SaaS.)
- Кастомный job-образ с Android SDK — отдельная задача после MVP.
- Если 2 GB RAM на remote окажется мало — увеличить после внедрения.




___




### Hot-swap CI на GitLab

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




### Play Store Internal Testing

**Мобильная работа:** полное закрытие (Play Console через web, .aab на CI).

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

**Мобильная работа:** полное закрытие (Play Console через web, .aab на CI).

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



___



### Desktop: pre-push hook (ktlintFormat + ktlintCheck)

**Мобильная работа:** полное закрытие (desktop; на mobile hook не работает — Gradle в proot нестабилен).

**Статус:** запланировано.

**Контекст:** на desktop `./gradlew :app:ktlintMainSourceSetCheck :app:ktlintTestSourceSetCheck` — ~10 сек. В CI — 2–3 мин на цикл. `pre-push` hook запускает ktlint **до** push, экономит цикл CI на чисто-стилевых ошибках. На mobile (proot) hook не работает — там CI остаётся единственным гейтом.

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

**Тесты:** shell-hook (не unit).

**Референсы:** M016 (PR #112) — 4 push-цикла, мотивация; AGENTS 4f; `gradle/actions/setup-gradle@v5`.

**Ограничения:**
- Не заменяет CI (mobile не покрывает).
- `git push --no-verify` — escape, не запрещать.
- Не смешивать с задачей `CI: fast-ktlint job`.
- На mobile hook не устанавливается — документация это явно говорит.

**Критерий готовности:**
- `scripts/pre-push.sh` коммитится, исполняемый.
- `docs/gradle-setup.md` — инструкция + bypass.
- На desktop: push с ktlint-ошибкой блокируется; `--no-verify` проходит.
- CI `fast-ktlint` — окончательный гейт (как раньше).

**Оценка:** ~1 ч.

**Зависимости:** нет.

**Связано:** `### CI: fast-ktlint job` (предыдущая).

**Открытые вопросы:** `pre-push` vs `pre-commit`? Рекомендую `pre-push` (не блокирует локальные коммиты, ktlint-fix обычно делается в самом конце).



___



### Compose UI tests: миграция на v2

**Мобильная работа:** полное закрытие (правки `.kt` в тестах — proot + PR;
гейт на CI).

**Статус:** запланировано.

**Триггер:** Compose UI test API v1 (`androidx.compose.ui.test.junit4.createComposeRule`
и `junit4.createAndroidComposeRule`) объявлен deprecated. Новый screenshot-тест
(`OrdersScreensScreenshotTest`, `ed5111c1`) уже на v2. Существующие
Behaviour-тесты на v1 компилируются с warning'ом; при обновлении Compose BOM
v1 может исчезнуть.

**Контекст:** 20 Compose UI-тестов используют v1 API:
- `createAndroidComposeRule` (3): `OrdersScreenBehaviourTest`,
  `PortfolioScreenBehaviourTest`, `SettingsScreenBehaviourTest`;
- `createComposeRule` (17): `ApiNotInitializedCardTest`, `OrderCardTest`,
  `OrdersQuantityRowTest`, `OrdersTypeChipsTest`, `SearchResultItemTest`,
  `AccountHeaderCardTest`, `EmptyPortfolioCardTest`, `PortfolioAccountBlockTest`,
  `BcsTokenHelpCardTest`, `CloseSandboxAccountDialogTest`, `ConnectStatusCardTest`,
  `FinamTokenHelpCardTest`, `SettingsHubListTest`, `StatusMessageCardTest`,
  `TInvestAccountSectionTest`, `TInvestStatusCardTest`, `TInvestTokenHelpCardTest`.

V2 переключает `StandardTestDispatcher` вместо `UnconfinedTestDispatcher` —
поведенческие тесты, полагающиеся на немедленное выполнение корутин, потребуют
явной синхронизации (`waitUntil`, `mainClock.advanceTimeBy`). Screenshot-тест
(`OrdersScreensScreenshotTest`) уже на v2 — из этой задачи исключён.

**Предусловия:** нет.

**Файлы:** 20 файлов в `app/src/test/kotlin/.../presentation/`
(`*BehaviourTest.kt`, `*Test.kt`). Полный список —
`grep -rln 'junit4.createComposeRule\|junit4.createAndroidComposeRule' app/src/test`.

**Шаги:**
1. Заменить импорт `androidx.compose.ui.test.junit4.createComposeRule` →
   `androidx.compose.ui.test.junit4.v2.createComposeRule` в 17 файлах.
2. Заменить `androidx.compose.ui.test.junit4.createAndroidComposeRule` →
   `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule` в 3 файлах.
3. Прогнать `:app:testDebugUnitTest` локально; исправить падения, вызванные
   `StandardTestDispatcher` (добавить `waitUntil` / `mainClock.advanceTimeBy`
   где нужно).
4. Прогнать полный гейт.

**Тесты:** все существующие Compose UI-тесты — цель: зелёные без изменения
утверждений (только синхронизация, где `UnconfinedTestDispatcher` скрывал race).

**Референсы:** AGENTS.md → 2a (актуальность);
[Compose ui-test v2 migration guide](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/junit4/v2/package-summary).

**Ограничения:**
- Не смешивать с Roborazzi baseline (уже на v2).
- Не менять утверждения тестов — только импорты и синхронизация.
- V1-импорты (deprecated) — единственный сигнал; сам пакет
  `androidx.compose.ui.test.*` (`onNodeWithText` и т.п.) не меняется.

**Критерий готовности:**
- Все 20 файлов на `junit4.v2`.
- 0 warnings `createComposeRule is deprecated`.
- Полный гейт зелёный.

**Оценка:** ~1.5–2 ч.

**Зависимости:** нет.

**Связано:** `docs/ROADMAP.md` → `### OrderFormViewModel: split на sub-VM`
(предзадача Roborazzi — новые тесты уже на v2, `ed5111c1`).

**Открытые вопросы:**
- Требуется ли `@OptIn(ExperimentalTestApi::class)` для v2-API в текущей
  Compose BOM — проверить по факту (по 3 не выдумываю: миграционный гайд
  Google говорит «no @OptIn», но Compose BOM у нас собран 2026-11; при
  первом PR — эмпирически).




___



---




## Закрытые

Вынесены в [`CI-ROADMAP-ARCHIVE.md`](CI-ROADMAP-ARCHIVE.md) (23 задачи).
