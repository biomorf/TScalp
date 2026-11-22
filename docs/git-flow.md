<!-- FILE_BEGIN -->
# Git-flow

Соглашение о ветвлении и слияниях в проекте TScalp.
Описывает схему веток, стратегии merge, жизненные циклы
релиза и hotfix.

Документ — **справочник**. План задач по внедрению GitFlow
и связанных изменений — в `docs/ci-roadmap.md` → «Переход
на полный GitFlow».

---










<!-- SECTION_BEGIN: git-flow -->
## Схема веток

Две постоянные ветки (`master`, `dev`) и четыре типа
временных (`feature/*`, `bugfix/*`, `release/*`,
`hotfix/*`).










<!-- SUBSECTION_BEGIN: git-flow-1 -->
<a id="git-flow-1"></a>
### git-flow-1 Диаграмма — gitGraph
Показывает историю как её видит git: коммиты, точки
ветвления, моменты merge. Точнее отражает механику —
видно, что `release/*` отпочковывается от `dev`,
а `hotfix/*` — от тега на `master`.

```mermaid
gitGraph
    commit id: "base"
    branch dev
    checkout dev
    commit id: "init"
    branch feature/analytics
    checkout feature/analytics
    commit id: "feat 1"
    commit id: "feat 2"
    checkout dev
    merge feature/analytics
    branch release/v1.1.0
    checkout release/v1.1.0
    commit id: "rc1"
    commit id: "bugfix"
    checkout master
    merge release/v1.1.0 tag: "v1.1.0"
    checkout dev
    merge master
```

---
<!-- SUBSECTION_END: git-flow-1 -->










<!-- SUBSECTION_BEGIN: git-flow-2 -->
<a id="git-flow-2"></a>
### git-flow-2 Диаграмма — flowchart
Схема процесса без детализации коммитов. Проще читается,
если цель — понять поток, а не механику git.

```mermaid
flowchart LR
    F[feature/*] -->|PR + Merge commit| D[dev]
    D -->|срез ветки| R[release/vX.Y.Z]
    R -->|fix-коммиты| R
    R -->|PR + Merge commit<br/>tag vX.Y.Z| M[master]
    M -->|обратный merge| D
    M -->|публикация| P[GitHub Release]
```

---
<!-- SUBSECTION_END: git-flow-2 -->










<!-- SUBSECTION_BEGIN: git-flow-3 -->
<a id="git-flow-3"></a>
### git-flow-3 `master`
Релизная ветка. Содержит только принятый, готовый к продакшену
код. Любой коммит в `master` — потенциальный релиз.

- Прямой push запрещён (Ruleset `Protect master`).
- Изменения — только через PR.
- Источник PR: `release/*` или `hotfix/*`.
- Каждый merge в `master` сопровождается тегом `vX.Y.Z`.

---
<!-- SUBSECTION_END: git-flow-3 -->










<!-- SUBSECTION_BEGIN: git-flow-4 -->
<a id="git-flow-4"></a>
### git-flow-4 `dev`
Интеграционная ветка. Здесь встречается код из feature- и
bugfix-веток. Отражает текущее состояние разработки — то,
что будет в следующем релизе.

- Прямой push запрещён (Ruleset `Protect dev`).
- Изменения — только через PR.
- Источники PR: `feature/*`, `bugfix/*`, `release/*`
  (обратный merge), `hotfix/*` (обратный merge).
- Служит базой для среза `release/*`.

**Отклонение от канона:** канонический GitFlow называет эту
ветку `develop`. У нас — `dev` (см. раздел «Осознанные
отклонения»).

---
<!-- SUBSECTION_END: git-flow-4 -->










<!-- SUBSECTION_BEGIN: git-flow-5 -->
<a id="git-flow-5"></a>
### git-flow-5 `feature/<name>`
Одна фича — одна ветка. Отпочковывается от `dev`, после
завершения сливается обратно в `dev`.

- Имя: `feature/<короткое-описание>` (например,
  `feature/positions-stream-grpc`).
- Живёт от нескольких часов до нескольких дней.
- Merge в `dev` — Merge commit (не Squash, не Rebase).

**Пример:**

```bash
git checkout dev
git pull
git checkout -b feature/positions-stream-grpc
# ... работа, коммиты ...
git push -u origin feature/positions-stream-grpc
# PR feature/positions-stream-grpc -> dev через web
```

---
<!-- SUBSECTION_END: git-flow-5 -->










<!-- SUBSECTION_BEGIN: git-flow-6 -->
<a id="git-flow-6"></a>
### git-flow-6 `bugfix/<issue-id>`
Один баг — одна ветка. Отпочковывается от `dev`, сливается
обратно в `dev`. Используется, когда баг не в проде, а в
разработке.

- Имя: `bugfix/<issue-id>` или `bugfix/<короткое-описание>`.
- Merge в `dev` — Merge commit.

**Пример:**

```bash
git checkout dev
git pull
git checkout -b bugfix/123-empty-positions-list
# ... фикс ...
git push -u origin bugfix/123-empty-positions-list
```

---
<!-- SUBSECTION_END: git-flow-6 -->










<!-- SUBSECTION_BEGIN: git-flow-7 -->
<a id="git-flow-7"></a>
### git-flow-7 `release/vX.Y.Z`
Стабилизация перед релизом. Отпочковывается от `dev`, когда
все фичи для релиза готовы. Сливается в `master` с тегом.

- Имя: `release/v<semver>` (например, `release/v1.1.0`).
- Сюда допускаются **только фиксы**, никаких новых фич.
- Merge в `master` — Merge commit + тег `vX.Y.Z`.
- После merge в `master` — обратный merge `master → dev`,
  чтобы фиксы стабилизации не потерялись.

**Пример:**

```bash
git checkout dev
git pull
git checkout -b release/v1.1.0
# ... фиксы, найденные при тестировании ...
git push -u origin release/v1.1.0
# PR release/v1.1.0 -> master через web
# После merge: тег, затем обратный merge master -> dev
```

---
<!-- SUBSECTION_END: git-flow-7 -->










<!-- SUBSECTION_BEGIN: git-flow-8 -->
<a id="git-flow-8"></a>
### git-flow-8 `hotfix/vX.Y.Z`
Экстренный фикс прода. Отпочковывается от **тега** на
`master` (не от `master`-ветки — именно от проблемной
версии). После фикса сливается в `master` (с тегом) и в
`dev`.

- Имя: `hotfix/v<semver>` (например, `hotfix/v1.0.1`).
- База: тег предыдущего релиза.
- Merge в `master` — Merge commit + тег `vX.Y.Z`.
- Merge в `dev` — Merge commit (или Cherry-pick — решить
  на практике).

**Пример:**

```bash
git checkout v1.0.0
git checkout -b hotfix/v1.0.1
# ... фикс креша ...
git push -u origin hotfix/v1.0.1
# PR hotfix/v1.0.1 -> master через web
# После merge: тег v1.0.1
# Затем PR hotfix/v1.0.1 -> dev (чтобы баг не вернулся)
```

---
<!-- SUBSECTION_END: git-flow-8 -->










<!-- SUBSECTION_BEGIN: git-flow-9 -->
<a id="git-flow-9"></a>
### git-flow-9 Работа над фичами
Разработчики создают `feature/*` от `dev`, вливают через PR.

```bash
git checkout dev && git pull
git checkout -b feature/<name>
# ... работа ...
git push -u origin feature/<name>
# PR feature/<name> -> dev
```

---
<!-- SUBSECTION_END: git-flow-9 -->










<!-- SUBSECTION_BEGIN: git-flow-10 -->
<a id="git-flow-10"></a>
### git-flow-10 Срез `release/*`
Когда все фичи для релиза в `dev`, создаётся release-ветка.

```bash
git checkout dev && git pull
git checkout -b release/v1.1.0
git push -u origin release/v1.1.0
```

---
<!-- SUBSECTION_END: git-flow-10 -->










<!-- SUBSECTION_BEGIN: git-flow-11 -->
<a id="git-flow-11"></a>
### git-flow-11 Стабилизация
Только фиксы. Каждый фикс — отдельный коммит в
`release/v1.1.0`.

---
<!-- SUBSECTION_END: git-flow-11 -->










<!-- SUBSECTION_BEGIN: git-flow-12 -->
<a id="git-flow-12"></a>
### git-flow-12 Merge в `master`
```bash
# PR release/v1.1.0 -> master через web (Merge commit)
git checkout master && git pull
git tag -a v1.1.0 -m "Release v1.1.0"
git push origin v1.1.0
```

CI на тег запускает `release.yml`: сборка release APK,
публикация в GitHub Releases.

---
<!-- SUBSECTION_END: git-flow-12 -->










<!-- SUBSECTION_BEGIN: git-flow-13 -->
<a id="git-flow-13"></a>
### git-flow-13 Обратный merge в `dev`
Чтобы фиксы стабилизации не потерялись при следующем
релизе.

```bash
# PR master -> dev через web (Merge commit)
```

**Диаграмма:**

```mermaid
gitGraph
    commit id: "base"
    branch dev
    checkout dev
    commit id: "init"
    branch feature/analytics
    checkout feature/analytics
    commit id: "feat 1"
    commit id: "feat 2"
    checkout dev
    merge feature/analytics
    branch release/v1.1.0
    checkout release/v1.1.0
    commit id: "rc1"
    commit id: "bugfix"
    checkout master
    merge release/v1.1.0 tag: "v1.1.0"
    checkout dev
    merge master
```

---
<!-- SUBSECTION_END: git-flow-13 -->










<!-- SUBSECTION_BEGIN: git-flow-14 -->
<a id="git-flow-14"></a>
### git-flow-14 Диаграмма — gitGraph
```mermaid
gitGraph
    commit id: "v1.0.0" tag: "v1.0.0"
    branch hotfix/v1.0.1
    checkout hotfix/v1.0.1
    commit id: "fix crash"
    checkout master
    merge hotfix/v1.0.1 tag: "v1.0.1"
    checkout dev
    merge hotfix/v1.0.1
```

---
<!-- SUBSECTION_END: git-flow-14 -->










<!-- SUBSECTION_BEGIN: git-flow-15 -->
<a id="git-flow-15"></a>
### git-flow-15 Диаграмма — flowchart
```mermaid
flowchart LR
    M[master<br/>v1.0.0] -->|срез| H[hotfix/v1.0.1]
    H -->|фикс| H
    H -->|PR + Merge commit<br/>tag v1.0.1| M
    H -->|PR + Merge commit<br/>чтобы баг не вернулся| D[dev]
    M -->|публикация| P[GitHub Release]
```

---
<!-- SUBSECTION_END: git-flow-15 -->










<!-- SUBSECTION_BEGIN: git-flow-16 -->
<a id="git-flow-16"></a>
### git-flow-16 Шаги
1. **Срез от тега.**

   ```bash
   git checkout v1.0.0
   git checkout -b hotfix/v1.0.1
   ```

2. **Фикс + пуш.**

   ```bash
   # ... работа над фиксом ...
   git push -u origin hotfix/v1.0.1
   ```

3. **Merge в `master` + тег.**

   ```bash
   # PR hotfix/v1.0.1 -> master через web
   git checkout master && git pull
   git tag -a v1.0.1 -m "Hotfix v1.0.1"
   git push origin v1.0.1
   ```

4. **Обратный merge в `dev`.**

   ```bash
   # PR hotfix/v1.0.1 -> dev через web
   ```

   **Альтернатива:** `git cherry-pick` коммита фикса, если
   в `dev` уже далеко ушли от точки среза. Решается по
   ситуации при первом hotfix.

---
<!-- SUBSECTION_END: git-flow-16 -->










<!-- SUBSECTION_BEGIN: git-flow-17 -->
<a id="git-flow-17"></a>
### git-flow-17 Имя ветки `dev` вместо `develop`
**Канон:** `develop`.

**У нас:** `dev`.

**Причина:** миграция имени требует обновления Rulesets,
workflows, документации, локальных ветвей у всех
участников. Экономия от соответствия канону не окупает
эту работу.

**Как учитывать:** в командах `git flow` (плагин
`git-flow`) имя базовой ветки по умолчанию `develop` — при
использовании плагина переопределяется флагом
(`git flow init -d` с указанием `dev`).

---
<!-- SUBSECTION_END: git-flow-17 -->










<!-- SUBSECTION_BEGIN: git-flow-18 -->
<a id="git-flow-18"></a>
### git-flow-18 Merge commit везде, без Squash
**Канон:** Squash и merge для feature-ветвей — «схлопнуть
50 коммитов в один чистый». Rebase — линейная история.

**У нас:** Merge commit везде, включая `feature/* → dev`.

**Причина:** сохранение SHA. Squash/Rebase переписывают
SHA коммитов, из-за чего `dev` и `master` расходятся —
при следующем merge GitHub видит конфликт в файлах,
которые не менялись. Merge commit сохраняет оригинальные
SHA коммитов из `feature/*`, ветки не расходятся,
конфликты почти исключены.

**Минус:** нелинейная история с отдельным merge-коммитом
на каждый PR. Приемлемо для одиночного проекта, и
компенсируется тем, что мы не теряем промежуточные
коммиты (в них видно эволюцию решения).

**Отдельная задача:** «Рассмотреть Squash vs Merge commit»
в `docs/ci-roadmap.md`. Решение может быть пересмотрено.

---
<!-- SUBSECTION_END: git-flow-18 -->










<!-- SECTION_END: git-flow -->










<!-- SECTION_BEGIN: git-flow -->
## Защита веток и тегов

Защита реализована через GitHub Rulesets:
`master`, `dev`, `release/**`, `hotfix/**` — только через
PR, с обязательным статусом CI, запретом force-push и
удаления. Теги `v*` — иммутабельны, создание только для
Maintainers.

**Детали, список Rulesets, известные ограничения GitHub
(picker, имя job'а, gate-job) и GitLab Protected Branches —
в `docs/repo-protection.md`.**

---










<!-- SECTION_END: git-flow -->










<!-- SECTION_BEGIN: git-flow -->
## Связанные документы

- `docs/repo-protection.md` — защита веток и тегов,
  GitHub Rulesets, GitLab Protected Branches, известные
  ограничения платформ.
- `docs/release-process.md` — процедура релиза,
  versionName / versionCode, теги, секреты.
- `docs/ci-roadmap.md` — план задач по CI/CD и
  инфраструктуре. Закрытая задача «Переход на полный
  GitFlow» — история внедрения этой схемы.
- `AGENTS.md` → правило 5g — жизненный цикл задачи
  в ROADMAP при работе с feature-ветками.
- `AGENTS.md` → правило 2b — организационные практики
  воспроизводятся по индустриальным стандартам.










<!-- SECTION_END: git-flow -->










<!-- FILE_END -->
