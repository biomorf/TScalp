# Защита репозитория

Как устроена защита веток и тегов в проекте TScalp.
Обе платформы — GitHub (основной CI) и GitLab (резерв).

Документ — **справочник**. Процедура релиза — в
`docs/release-process.md`. Схема веток и стратегия merge —
в `docs/git-flow.md`.

---

## Обзор

Что защищаем:

- **Ветки** — `master`, `dev`, `release/**`, `hotfix/**`.
  Защита: запрет прямого push, обязательный PR, обязательный
  статус CI, запрет force-push, запрет удаления.
- **Теги** — `v*` (релизные). Защита: иммутабельность
  (нельзя перезаписать или удалить), создание только для
  Maintainers.

Зачем:

- Никакая ошибка (или взлом) не должна попасть в релиз
  без проверки.
- История релизов должна быть неизменяемой — тег указывает
  на конкретный коммит, который уже видели пользователи.

---

## GitHub Rulesets

Механизм защиты в GitHub — **Rulesets**. Настраиваются в
`Settings → Rules → Rulesets`.

### Наши Rulesets

| Ruleset | Target | Ключевые правила |
|---|---|---|
| `Protect master` | `master` | PR + required check `gate` + block force pushes + restrict deletions |
| `Protect dev` | `dev` | то же |
| `Protect release branches` | `release/**` | то же |
| `Protect hotfix branches` | `hotfix/**` | то же |
| `Protect release tags` | `v*` | restrict updates, restrict deletions, bypass для Repository admin |
| `Restrict tag creation to maintainers` | `v*` | создание только для Maintainers |

**Общие правила для четырёх branch-ruleset'ов:**

- `Require a pull request before merging`
  - Required approvals: `0` (пока один разработчик,
    вернуться при появлении второго)
  - Require conversation resolution before merging: вкл.
- `Require status checks to pass`
  - Required check: `gate` (см. «Gate-job pattern»)
  - Require branches to be up to date before merging: вкл.
  - Do not require status checks on creation: вкл.
    (иначе создание `release/*` / `hotfix/*` заблокировано,
    пока CI на новой ветке не отработает)
- `Block force pushes`
- `Restrict deletions`

**Allowed merge methods:** только `Merge`. `Squash` и
`Rebase` отключены — стратегия единая (см.
`docs/git-flow.md` → «Осознанные отклонения»).

**Bypass:** Repository admin во всех ruleset'ах. Нужен для
восстановления (force-push после ошибочного тестового
коммита, удаление случайного тега). При появлении второго
разработчика — пересмотреть.

---

## Известные ограничения GitHub

За время работы накопились особенности GitHub Rulesets,
которые легко забыть и наступить на те же грабли.

### Picker required checks — 7-дневное окно

Rulesets picker (`Add checks` в Ruleset → Require status
checks to pass) показывает **только те checks, что
прогонялись на защищаемой ветке за последние 7 дней**.

**Что происходит при паузе >7 дней:**

- Нужный check исчезает из picker'а.
- Если вписать его имя вручную — не матчится с рантаймом,
  PR зависает в состоянии «Expected — Waiting for status
  to be reported».
- Merge заблокирован без возможности обойти (кроме
  admin bypass, что обесценивает защиту).

**Как обходим:** gate-job (см. ниже).

### Required check указывается по имени job'а

В Rulesets required check задаётся **именем job'а** из
workflow — `gate`, `fast-check`. **Не** полным UI-именем
`CI / gate (pull_request)`.

**Симптом ошибки:** вписали `CI / fast-check` руками →
PR зависает, потому что такого check в рантайме нет.
В UI Actions отображается `CI / fast-check (pull_request)`,
но GitHub матчит по имени job'а без префикса workflow.

**Как правильно:** в Ruleset → Edit → Required status
checks → **Add checks** → начать печатать имя
(`gate`, `fast-check`) → выбрать из suggestions.
Не вписывать вручную.

### Gate-job pattern

Обход 7-дневного окна picker'а. Job `gate` в `ci.yml`:

- `needs: [fast-check, full-build]`
- `if: always()` — запускается даже при падении upstream,
  иначе не создал бы статус, и required check завис бы.
- Проверяет результат upstream: `fast-check` → `success`,
  `full-build` → `success` или `skipped`.
- Падает, если проверки не прошли.

**Почему работает:** `gate` присутствует в **каждом**
запуске CI. Даже после паузы >7 дней первый же push/PR
запускает CI → `gate` появляется → picker видит.

**При добавлении новых job'ов в `ci.yml`** — включать их в
`needs` gate-job'а, иначе они не будут блокировать merge.

### Восстановление после ошибочного тега

`Protect release tags` запрещает удаление тегов. Ошибочно
созданный тег (например, `v0.0.1-test`) удаляется:

1. **Через web UI** — `Releases → Tags → Delete tag`,
   роль Maintainer+ (или Repository admin с bypass).
2. **Не через `git push --delete`** — repository rule
   отклоняет push. Даже admin не пройдёт без bypass в
   ruleset'е.

**Bypass для Repository admin в `Protect release tags`
обязателен** — иначе теги копятся навсегда.

---

## GitLab Protected Branches / Tags

GitLab использует другую терминологию. Эквиваленты:

| GitHub | GitLab |
|---|---|
| Rulesets (branch) | Protected branches |
| Rulesets (tag) | Protected tags |
| Required status checks | Required pipeline status (в настройках проекта) |
| Required approvals | Merge Request Approval Rules |
| Bypass list | Allowed to merge / Allowed to push |

### Текущее состояние

**Protected branches:**

- `master` — защищена. Прямой push запрещён, только через
  MR. `Allowed to merge: Maintainers`. `Allowed to push
  and merge: No one`.
- `dev` — **не защищена**. GitLab — резерв, при активации
  CI нужно поднять защиту до уровня GitHub Rulesets.

**Protected tags:**

- Pattern `v*`, `Allowed to create: Maintainers`.

**CI/CD Variables:**

Сохранены с эпохи до перехода на GitHub Actions (сейчас
временно не используются):

- `KEYSTORE_BASE64` — Masked + Protected.
- `KEYSTORE_PASSWORD` — Masked + Protected.
- `KEY_ALIAS` — Protected (Masked недоступно: 7 символов
  < минимум 8).
- `KEY_PASSWORD` — Masked + Protected.

### Особенности GitLab

**Иммутабельность тегов.** Отдельных тумблеров «запретить
удаление» / «запретить force-push» для тегов в GitLab нет:
защищённый тег иммутабелен по умолчанию. Любой push,
перезаписывающий или удаляющий защищённый тег, отклоняется
на уровне протокола. Удалить тег можно только через
веб-интерфейс (`Settings → Repository → Tags`), роль
Maintainer+.

**Аннотированные теги + Protected Tags — двойная защита.**
Аннотированный тег делает подмену заметной (свой SHA, автор,
дата). Protected Tags делает её невозможной через push.

**Approval rules для MR.** В GitLab есть встроенный механизм
Merge Request Approval Rules — обязательное одобрение MR
перед merge. Аналог `Require approvals: 1` в GitHub
Rulesets. Для одиночного проекта сейчас бесполезно (approve
своего же MR недоступен), но при появлении второго
разработчика — активируется.

### Что делать при переключении на GitLab

Процедура описана в `docs/ci-cd.md` (когда появится — этап
Hot-swap CI в `docs/ci-roadmap.md`). Здесь — только
защита:

1. Поднять `dev` до уровня `master` (Protected branch).
2. Настроить Protected Branches для `release/**` и
   `hotfix/**`.
3. Проверить CI/CD Variables (актуальны ли keystore-пароли).
4. Синхронизировать Protected Tags с GitHub
   (`Restrict tag creation to maintainers`).

---

## Процедуры

### Добавить новый required check

1. Job должен существовать в `ci.yml` и хотя бы один раз
   отработать на защищаемой ветке (или через PR в неё).
2. `Settings → Rules → Rulesets → <ruleset> → Edit`.
3. Блок **Require status checks to pass** → **Add checks**.
4. **Начать печатать** имя job'а (например, `gate`).
5. Выбрать из suggestions кликом. **Не вписывать вручную.**
6. Save.

### Восстановиться после ошибочного тега

1. Открыть `Releases` → найти релиз → удалить (если создан).
2. `Releases → Tags` → найти тег → **Delete tag**.
3. Если кнопки нет — Ruleset `Protect release tags` не
   имеет bypass для admin. Добавить bypass и повторить.

### Проверить, что правила работают

```bash
# Пробный push в защищённую ветку — должен быть отклонён
git push origin dev
# Ожидаем: remote: error: GH013: Repository rule violations

# Пробный push в feature-ветку — должен пройти
git push origin feature/test
# Ожидаем: успешный push
```

Если push в защищённую ветку прошёл — либо bypass
сработал (для admin), либо правило не применилось.

---

## Связанные документы

- `docs/git-flow.md` — схема веток, стратегии merge,
  жизненные циклы release и hotfix.
- `docs/release-process.md` — процедура релиза, версии,
  теги, секреты.
- `docs/ci-roadmap.md` — план задач по CI/CD и инфраструктуре.
  Закрытая задача «Gate-job для required checks» —
  практический кейс с picker'ом.
