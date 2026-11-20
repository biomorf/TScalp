<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: ci-roadmap-defer -->
## Отложено










<!-- SUBSECTION_BEGIN: c-11 -->
<a id="c-11"></a>
### [DEFER] C-11 Build: suppress JVM warnings (Unsafe / CDS) + cron-check ktlint-gradle #944
**Мобильная работа:** полное закрытие (правки `gradle.properties` / `app/build.gradle.kts` / `.github/workflows/` — proot + PR; гейт на CI).

**Статус:** отложено — CDS done (`a3becccc`), cron #944 done (`2e7e3170`); Unsafe blocked on CI JDK 25 upgrade (follow-up).

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

---
<!-- SUBSECTION_END: c-11 -->










<!-- SUBSECTION_BEGIN: c-10 -->
<a id="c-10"></a>
### [DEFER] C-10 CI: actions/cache v4 -> v6 (Node 20 deprecation)
**Мобильная работа:** полное закрытие (proot + PR; правка `ci.yml` в 3 местах, gate на CI).

**Статус:** отложено.

**Триггер:** 2026-11-14, PR #152 (`bugfix/ci-robolectric-cache`). GitHub Actions warning в каждом CI-run:
`Node.js 20 is deprecated. The following actions target Node.js 20 but are being forced to run on Node.js 24: actions/cache@v4.`

**Контекст:** PR #152 добавил `actions/cache@v4` в 3 job'а (`fast-check`, `full-build`, `screenshot-test`) для кэша Robolectric `android-all-instrumented`. К ноябрю 2026 актуальна версия `actions/cache@v6` (по 2a). v4 работает, но выдаёт deprecation warning в каждом CI-run.

**Соответствие Clean:** соответствует (организационный слой, 2b).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:** PR #152 merged (`2261145c`, 2026-11-14).

**Файлы:** `.github/workflows/ci.yml` — 3 места (`actions/cache@v4` -> `@v6`): `fast-check`, `full-build`, `screenshot-test`.

**Шаги:**
1. Заменить `actions/cache@v4` -> `actions/cache@v6` (3 места, count = 3).
2. Проверить, что кэш-key (`roborazzi-android-all-...`) не меняется (v6 API совместим с v4 для `path` / `key` / `restore-keys`).
3. YAML parse + `git diff`.
4. PR -> merge.

**Тесты:** нет (CI-изменение). Verify — по CI-run после merge: warning исчез.

**Референсы:** PR #152 (`2261145c`); `https://github.com/actions/cache/releases` — актуальная версия v6 на 2026-11.

**Ограничения:**
- Не менять кэш-key / path — только версию action'а.
- Не трогать другие `uses:` (`checkout@v6`, `setup-java@v5`, `setup-gradle@v5`, `upload-artifact@v7`).

**Критерий готовности:**
- `actions/cache@v6` в 3 местах.
- CI зелёный на PR + после merge в `dev`.
- `Node.js 20 is deprecated` warning исчез из CI-логов.

**Оценка:** ~10 минут (3 замены + YAML + PR).

**Зависимости:** PR #152 merged (✅).

**Связано:** `docs/ROADMAP.md` -> `### OrderFormViewModel: split на sub-VM` (v6 должен быть до PR-3, по запросу пользователя 2026-11-14).

**Открытые вопросы:**
- Точная версия `actions/cache` на 2026-11 (v6 подтверждён пользователем; проверить при apply — `latest` в репозитории action'а).

---
<!-- SUBSECTION_END: c-10 -->










<!-- SUBSECTION_BEGIN: c-12 -->
<a id="c-12"></a>
### [DEFER] C-12 CI: JDK 17 → 25
**Мобильная работа:** полное закрытие (правки `.yml` / `.kts` через proot; гейт на CI).

**Статус:** отложено. **Блокер для** `### Build: suppress JVM warnings (Unsafe / CDS) + cron-check ktlint-gradle #944` (Unsafe-часть).

**Контекст:** JDK 25 даёт флаг `--sun-misc-unsafe-memory-access=allow` (JEP 471), который подавляет warning `sun.misc.Unsafe::objectFieldOffset` от `kotlin-compiler-embeddable 2.1.0` (тянут ktlint / detekt Gradle-плагины). Без JDK 25 на CI флаг = `Unrecognized option` → daemon падает → нельзя снять Unsafe-warning (см. `### Build: suppress JVM warnings` → шаг 3, статус `blocked`). Локально у нас JDK 25.0.4 — warning виден только потому, что gate на desktop; на CI (JDK 17) warning не появляется, но и флаг не применить.

**Соответствие Clean:** соответствует (организационный слой, 2b).

**Нарушенное правило Clean:** —.

**Clean-альтернатива:** —.

**Выгоды (для «отклонение по 2c»):** —.

**Предусловия:**
- Проверить AGP 9 / KSP / Hilt на JDK 25 (совместимость) — в отдельной ветке, `java-version: '17' → '25'` + прогон `gate`.
- Проверить foojay-resolver (auto-provision JDK 17 toolchain для bytecode) — если toolchain остаётся 17, а daemon 25.

**Файлы:**
- `.github/workflows/ci.yml` — 4 × `setup-java@v5` (`java-version: '17'` → `'25'`).
- `.github/workflows/release.yml` — 1 × `setup-java@v5` (`java-version: '17'` → `'25'`).
- `app/build.gradle.kts` — `jvmToolchain(17)` — оставить 17 (bytecode); `compilerOptions.jvmTarget = JVM_17` — оставить. Daemon = 25 (для флага), bytecode = 17 (для runtime).
- `gradle.properties` — `org.gradle.jvmargs` + `--sun-misc-unsafe-memory-access=allow` (после merge JDK 25).

**Шаги:**
1. Ветка `feature/ci-jdk-25`. Заменить `java-version: '17'` → `'25'` в 5 местах (4 × `ci.yml` + 1 × `release.yml`); имя шага `Set up JDK 17` → `Set up JDK 25` (5 мест).
2. Если `setup-java@v5` не тянет 25 — проверить `distribution: 'temurin'` актуальность (25 LTS?); при необходимости — `'25-ea'` или `'25.0.x'`.
3. PR → дождаться `gate` зелёный на JDK 25.
4. После merge — снять Unsafe-warning: `gradle.properties` `org.gradle.jvmargs` + `--sun-misc-unsafe-memory-access=allow`; `grep -c 'sun.misc.Unsafe::objectFieldOffset'` в gate-логе = 0.
5. Связать: `### Build: suppress JVM warnings` → шаг 3 ⏳ → ✅ (если #944 уже закрыт) / остаётся ⏳ (cron уведомит).

**Тесты:** нет (CI-конфиг). Verify — `gate` зелёный на PR + после merge; Unsafe-warning = 0 в gate-логе.

**Референсы:**
- JEP 471 (`sun.misc.Unsafe` deprecation, JDK 23+).
- `actions/setup-java@v5` (temurin distribution, версии JDK).
- `gradle/actions/setup-gradle@v5` (если меняется JDK для toolchain).

**Ограничения:**
- Не менять `jvmToolchain(17)` / `jvmTarget(JVM_17)` — bytecode остаётся 17 (runtime на устройствах Android).
- Не смешивать с `### Build: suppress JVM warnings` (CDS + cron уже done, эта задача — только JDK 25 upgrade).
- `release.yml` — трогать осторожно (релизный pipeline); прогонять `release.yml` smoke — только после merge на `dev` через тестовый тег.
- Если JDK 25 ломает AGP 9 / Hilt / KSP — откатить `java-version` на 17, задача → «blocked on AGP/Hilt», отдельная сессия.

**Критерий готовности:**
- `ci.yml` (4 места) + `release.yml` (1 место): `java-version: '25'`.
- `gate` зелёный на PR (JDK 25).
- `release.yml` smoke (тестовый тег) — собирает APK на JDK 25.
- После merge: Unsafe-warning 0 в gate-логе (`org.gradle.jvmargs` + флаг); `### Build: suppress JVM warnings` → шаг 3 → ✅.

**Оценка:** ~1–2 ч (риск AGP / KSP / Hilt совместимости с JDK 25 — может затянуться).

**Зависимости:** нет (эта задача — самостоятельная; блокирует Unsafe-часть `### Build: suppress`). ktlint-gradle #944 — отдельный upstream-триггер, cron уведомит.

**Связано:** `docs/ci-roadmap.md` → `### Build: suppress JVM warnings (Unsafe / CDS) + cron-check ktlint-gradle #944` (шаг 3 Unsafe → ✅ после merge этой задачи); `docs/ci-roadmap.md` → `### Hot-swap CI на GitLab` (JDK 25 будет в self-hosted runner, при переносе).

**Открытые вопросы:**
- `setup-java@v5` + `temurin` + `java-version: '25'` — доступна ли 25 LTS через setup-java (или нужен `'25-ea'` / `'25.0.x'`)? Проверить при apply.
- AGP 9 / KSP / Hilt на JDK 25 — есть ли известные несовместимости? Проверить в отдельной ветке (пункт 1 шагов).
- `jvmToolchain(17)` при daemon 25: нужен ли foojay auto-provision для toolchain 17? Проверить (если нет — `jvmToolchain(25)` + `jvmTarget 17` всё равно работает для bytecode).

---
<!-- SUBSECTION_END: c-12 -->










<!-- SUBSECTION_BEGIN: c-4 -->
<a id="c-4"></a>
### [DEFER] C-4 Role emulation для тестировщика
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

---
<!-- SUBSECTION_END: c-4 -->










<!-- SUBSECTION_BEGIN: c-7 -->
<a id="c-7"></a>
### [DEFER] C-7 Play Store Internal Testing
**Мобильная работа:** полное закрытие (Play Console через web, .aab на CI).

**Статус:** отложено. **Заблокировано** созданием
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

---
<!-- SUBSECTION_END: c-7 -->










<!-- SUBSECTION_BEGIN: c-8 -->
<a id="c-8"></a>
### [DEFER] C-8 Play Store Production
**Мобильная работа:** полное закрытие (Play Console через web, .aab на CI).

**Статус:** отложено. **Заблокировано** «Play Store
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
<!-- SUBSECTION_END: c-8 -->










<!-- SECTION_END: ci-roadmap-defer -->










<!-- FILE_END -->
