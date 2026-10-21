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

---

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

---

### Codecov: diff coverage в PR и бейдж покрытия

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

---

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

**Мобильная работа:** частичное (написание Ansible-ролей, inventory, playbook — с телефона; тестирование разворачивания — на удалённом сервере или десктопе).

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

---

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

---
---
