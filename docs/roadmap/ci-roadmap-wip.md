<!-- SECTION_BEGIN: ci-roadmap-wip -->
## В работе










<!-- SUBSECTION_BEGIN: c-5 -->
___
### [WIP] C-5 Self-hosted GitLab Runner через Ansible + Podman
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
<!-- SUBSECTION_END: c-5 -->










<!-- SUBSECTION_BEGIN: c-11 -->
___
### [WIP] C-11 Build: suppress JVM warnings (Unsafe / CDS) + cron-check ktlint-gradle #944
**Мобильная работа:** полное закрытие (правки `gradle.properties` / `app/build.gradle.kts` / `.github/workflows/` — proot + PR; гейт на CI).

**Статус:** в работе — CDS done (`a3becccc`), cron #944 done (`2e7e3170`); Unsafe blocked on CI JDK 25 upgrade (follow-up).

**Контекст:** два JVM warning'а шумят в `gate` логах:
1. `sun.misc.Unsafe::objectFieldOffset` — от `kotlin-compiler-embeddable 2.1.0` (IntelliJ `com.intellij.util.containers.Unsafe`), тянут ktlint / detekt Gradle-плагины. Warning включён с JDK 24+ (локальный — 25.0.4). Upstream: `JLLeitschuh/ktlint-gradle#944` — открыт с 2025-08-21; последний релиз 14.2.0 (2026-03-12) его не содержит. Warning подавлен только в ktlint CLI (#3040), не в Gradle-плагине.
2. `Sharing is only supported for boot loader classes because bootstrap classpath has been appended` — CDS warning от JVMTI-агентов (Robolectric / JaCoCo), модифицирующих bootclasspath в test JVM.

**Соответствие Clean:** соответствует (организационный слой, 2b).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**MVVM:** — (не применимо).

**KMP:** — (не применимо).

**Предусловия:** нет.

**Файлы:**
- `app/build.gradle.kts` — `tasks.withType<Test>` (jvmArgs).
- `gradle.properties` — `org.gradle.jvmargs` (для C1, blocked).
- `.github/workflows/ktlint-gradle-944.yml` — cron-check.
- `docs/ci-roadmap.md` — эта задача.

**Шаги:**
1. ✅ CDS: `jvmArgs("-Xshare:off")` в `tasks.withType<Test>` (`a3becccc`). Синтаксис — с двоеточием (`-Xshare:off`), не с равенством (`-Xshare=off` = `Unrecognized option` на JDK 17+).
2. ✅ Cron-check ktlint-gradle #944 (`.github/workflows/ktlint-gradle-944.yml`, `2e7e3170`): daily 09:00 UTC + `workflow_dispatch`; при `state == 'CLOSED'` создаёт issue с label `ktlint-gradle-944` и инструкцией снять флаг.
2a. ⏳ YAML hotfix: PR #158 (`45052d05`) ввёл `.github/workflows/ktlint-gradle-944.yml` с невалидным `run: |` (block scalar, нулевой отступ на строке 64 → `Invalid workflow file ...#L64`). Hotfix `5ce9dbf4` (PR #159): `BODY="..."` → `cat > body.txt <<EOF … EOF` + `--body-file body.txt`. После merge — smoke-test через `workflow_dispatch`. Урок 114.
3. ⏳ Unsafe — **blocked**: `--sun-misc-unsafe-memory-access=allow` появился в JDK 23 (JEP 471); CI на JDK 17 (temurin, `setup-java@v5`) → флаг = `Unrecognized option` → daemon падает. Нужен upgrade CI до JDK 25 (отдельная задача `### CI: JDK 17 → 25`, follow-up). Когда CI JDK 25 + ktlint-gradle #944 закрыт (cron уведомит) — снять флаг и проверить `grep sun.misc.Unsafe::objectFieldOffset`.

**Тесты:** нет (build-конфиг). Verify — по `gate` логам (CDS warning = 0; Unsafe — ждёт JDK 25).

**Референсы:**
- ktlint-gradle #944 (issue, открыт).
- JEP 471 (`sun.misc.Unsafe` deprecation, JDK 23+).
- `-Xshare:off` в JVM docs (Oracle).

**Ограничения:**
- Не добавлять `--sun-misc-unsafe-memory-access=allow` в `org.gradle.jvmargs` до JDK 25 на CI (daemon падает).
- Не путать с C2-правкой (`-Xshare:off`) — она уже в `app/build.gradle.kts`.
- Cron-check — только при закрытии #944; не при каждом релизе.

**Критерий готовности:**
- CDS warning — 0 в `gate` логах (локально и на CI). ✅
- Cron-check #944 — workflow зелёный, при закрытии issue — создаётся issue с label `ktlint-gradle-944`. ✅ (проверить на PR)
- Unsafe warning — 0 в `gate` логах (после upgrade CI до JDK 25 + закрытия #944). ⏳

**Оценка:** CDS + cron ~30 мин (сделано); Unsafe — блокер (CI JDK 17 → 25, отдельная задача).

**Зависимости:** `### CI: JDK 17 → 25` (для Unsafe); ktlint-gradle #944 (upstream, cron уведомит).

**Связано:** `docs/ci-roadmap.md` → `### Compose UI tests: миграция на v2` (закрыт в этом PR); `### CI: JDK 17 → 25` (follow-up, не заведена — завести отдельной docs-задачей после merge).

**Открытые вопросы:**
- `### CI: JDK 17 → 25` — scope (AGP 9 / KSP / Hilt / toolchain / foojay) и риск — отдельная сессия, не в этом PR.
- Точная версия ktlint-gradle с фиксом #944 — неизвестна, cron уведомит.



___
<!-- SUBSECTION_END: c-11 -->





<!-- SECTION_END: ci-roadmap-wip -->
