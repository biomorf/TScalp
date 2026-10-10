# Release process

Документ описывает, как формируются versionName / versionCode
и как проходит релиз.

**Основной CI сейчас — GitHub Actions.** GitLab CI временно
отключён: квота Free Tier исчерпана. Пайплайн GitLab будет
восстановлен после настройки self-hosted runner'а
(см. ROADMAP → «Self-hosted GitLab Runner через Ansible +
Podman»). Раздел про GitLab-релиз сохранён ниже и помечен
как временно отключённый.

## Версии

### versionName

- **Локально** (без `-PreleaseVersionName`): `1.<yy.MM.dd.HHmm>`
  в UTC. Пример: `1.26.10.07.1430`.
- **В CI на теге** `vX.Y.Z`: ровно `X.Y.Z` (без префикса `v`).

### versionCode

Вычисляется из semver тега по формуле:

    versionCode = major * 10_000 + minor * 100 + patch

- `v1.0.0` → 10000
- `v1.2.3` → 10203
- `v2.15.99` → 21599

**Ограничения:**
- `minor` и `patch` — от 0 до 99. При `patch = 100` произойдёт
  переполнение в `minor`.
- `major` — до 214 748 (предел Int).
- Для debug-сборок и локальных release без явной версии
  `versionCode = 1`.

**Почему так:** versionCode должен строго возрастать между
релизами. Формула гарантирует это при корректном semver-инкременте.

---

## Секреты и CI-переменные

### GitHub Repository secrets (актуально)

Задаются в `Settings → Secrets and variables → Actions → New
repository secret`. Используются в `release.yml`.

| Имя                 | Назначение              |
|---------------------|-------------------------|
| `KEYSTORE_BASE64`   | `.jks` в base64         |
| `KEYSTORE_PASSWORD` | пароль хранилища        |
| `KEY_ALIAS`         | alias ключа в keystore  |
| `KEY_PASSWORD`      | пароль ключа            |

`KEY_ALIAS` не является секретом (alias в открытом виде лежит
внутри `.jks`), но и не мешает хранить его в Secrets: доступ
к secrets есть только у workflow'ов репозитория, значение
не отображается в UI.

Про Environment secrets (approval, ограничение по тегам,
per-environment значения) — см. ROADMAP → «Переход на GitHub
Environment secrets».

### GitLab CI/CD Variables (временно отключены)

Сохраняются в GitLab на случай восстановления пайплайна.
Пока не используются.

- `Settings → CI/CD → Variables`.
- `KEYSTORE_BASE64` — Masked + Protected.
- `KEYSTORE_PASSWORD` — Masked + Protected.
- `KEY_ALIAS` — Protected (Masked недоступно: 7 символов
  < минимум 8).
- `KEY_PASSWORD` — Masked + Protected.               |

`KEY_ALIAS` не маскируется: alias не является секретом
(он в открытом виде лежит внутри `.jks`). GitLab всё равно
требует минимум 8 символов для маскировки — `tscalp` (7) не пройдёт.

---

## Теги

### Аннотированные, не легковесные

Релизные теги создаются **только аннотированными**:

    git tag -a v1.0.0 -m "Release v1.0.0"

Почему:

- **Метаданные.** В тег-объект пишутся автор, дата и сообщение.
  GitLab читает их через `CI_COMMIT_TAG_MESSAGE` — можно
  использовать напрямую как release notes, без парсинга
  `git log`.
- **Заметность подмены.** У аннотированного тега своя SHA
  (тег-объект), отдельная от SHA коммита. При перезаписи тега
  меняется SHA тег-объекта — подмена видна сразу. У легковесного
  тега SHA тега = SHA коммита, подмену нужно искать сверкой
  коммитов.
- **Воспроизводимость.** Клонировав репозиторий на тег, любой
  может увидеть сообщение тега без запуска CI.

### Защита тегов

**GitHub Rulesets (актуально):**

- `Settings → Rules → Rulesets → New ruleset`.
- **Target tags:** pattern `v*`.
- **Rules:**
    - `Restrict creations` — создание только Maintainers.
    - `Restrict updates` — force-push запрещён.
    - `Restrict deletions` — удаление запрещено.
- Удалить тег можно только через UI
  (`Releases → Tags → Delete tag`) или через API с правами
  admin.

**GitLab Protected Tags (временно отключены):**

Настроены, но пока не используются:

- `Settings → Repository → Protected tags`;
- pattern: `v*`;
- `Allowed to create: Maintainers`.

Отдельных тумблеров «запретить удаление» / «запретить force-push»
для тегов в GitLab нет: защищённый тег иммутабелен по умолчанию.
Любой push, пытающийся перезаписать или удалить защищённый тег,
отклоняется на уровне протокола. Удалить тег можно только через
веб-интерфейс (`Settings → Repository → Tags`), роль Maintainer+.

Аннотированные теги + Protected Tags — двойная защита:
первое делает подмену заметной, второе делает её невозможной
через push. Локально переписать тег всё ещё можно, но
при попытке push GitLab отклонит операцию.

## Продвижение master

Ветка `master` защищена: прямой push запрещён, продвижение
идёт **только через MR из `dev`** (см. «Настройка GitLab»).
Локальный `git merge --ff-only dev` не используется — его
заменяет MR.

Политика — **fast-forward**: после merge `master` указывает
ровно на HEAD `dev`. Это позволяет проверить вручную, что
зарелизили именно то, что было на dev.

Полный merge с созданием merge-коммита не используется:
merge-коммит существует только на master, и тег на нём сложнее
сверить с состоянием dev.

### Настройка GitLab

Ветка `master` защищена (см. ROADMAP → «Защита ветки master»),
прямой push в неё запрещён. Продвижение идёт через MR из `dev`.

Чтобы MR **не создавал merge-коммит**, в GitLab включён режим
**Fast-forward merge**:

    Settings → Merge requests → Merge method → Fast-forward merge

Без этой настройки GitLab по умолчанию создаёт коммит
`Merge branch 'dev' into 'master'`, и тег ставится не на HEAD dev,
а на merge-коммит — политика ff-only нарушается.

Проверка после merge:

    git fetch origin
    git log origin/master -1 --oneline

Коммит на `origin/master` должен совпадать с последним
коммитом `dev` на момент merge.

## Релиз через GitHub Actions

Полный цикл:

1. Продвинуть `master` через Pull Request из `dev`
   (см. «Продвижение master»).
2. Поставить **аннотированный** тег и запушить:

       git tag -a vX.Y.Z -m "Release vX.Y.Z"
       git push origin vX.Y.Z

3. GitHub Actions запускает `release.yml`:
    - триггер на тег `v*`;
    - checkout, setup JDK 17, setup Gradle;
    - декодирует keystore из `KEYSTORE_BASE64`;
    - создаёт `keystore.properties` из четырёх secrets;
    - собирает `assembleRelease -PreleaseVersionName=X.Y.Z`;
    - переименовывает APK в `tscalp-release-vX.Y.Z.apk`;
    - создаёт GitHub Release с APK и автоматически
      сгенерированными release notes.

Отдельный workflow `ci.yml` прогоняет `testDebugUnitTest`,
`lintDebug` и `assembleDebug` на push и PR в `dev` / `master`.

## Релиз через GitLab CI (временно отключён)

Пайплайн сохранён в `.gitlab-ci.yml`, но не запускается:
квота Free Tier исчерпана. Будет восстановлен после настройки
self-hosted runner'а (см. ROADMAP).

Порядок — тот же, что для GitHub Actions, но с MR вместо PR
и `release-cli` вместо `softprops/action-gh-release`:

1. Продвинуть `master` через MR из `dev`.
2. Поставить **аннотированный** тег `vX.Y.Z` и запушить:

       git tag -a vX.Y.Z -m "Release vX.Y.Z"
       git push origin vX.Y.Z

3. GitLab CI собирает pipeline:
    - `unit-tests`, `lint`, `assemble-debug` — на всех ветках
      и тегах;
    - `build-release` — проверяет, что тег стоит на `master`,
      расшифровывает keystore из `KEYSTORE_BASE64`, собирает
      `assembleRelease -PreleaseVersionName=X.Y.Z`;
    - `publish-release` — создаёт GitLab Release с APK в assets
      и release notes из коммитов между предыдущим и текущим
      тегом. В notes попадают только `feat` и `fix`;
      инфраструктурные (`chore`, `docs`, `refactor`, `test`)
      отфильтровываются по типу коммита.

---

### Чек-лист релиза

Перед созданием тега:

1. Убедиться, что `dev` содержит все нужные коммиты.
2. Запушить `dev`:

       git push origin dev

3. Создать **Pull Request** `dev → master` в GitHub UI.

   *GitLab:* Создать MR `dev → master` в GitLab UI.

4. Дождаться зелёного `ci.yml` на PR.

   *GitLab:* Дождаться зелёного пайплайна на MR.

5. Merge PR через **Squash** или **Merge commit** в зависимости
         от политики. При включённом `Require linear history`
         в Ruleset GitHub потребует **Rebase** или **Squash**.

   *GitLab:* Смерджить MR.

6. Локально:

       git checkout master
       git pull --ff-only origin master

   Если `--ff-only` падает — значит `dev` и `master` разошлись,
   разобраться до продолжения.
7. Проверить, что `origin/master` содержит нужный коммит:

       git log origin/master -1 --oneline

Создание тега:

8. `git tag -a vX.Y.Z -m "Release vX.Y.Z"`.
9. **Проверить перед пушем**:

       git branch -r --contains vX.Y.Z

   В выводе **обязан** быть `origin/master`. Если нет — тег
   создан не на том коммите, удалить и повторить с шага 6.
10. `git push origin vX.Y.Z`.

Пуш только тега, без `--tags`: `git push --tags` отправит все
локальные теги, включая случайные.

После пуша:

11. Открыть GitHub → вкладку **Actions** → workflow **Release**.
    Убедиться, что job зелёный.
12. Открыть вкладку **Releases** → проверить, что создан
    релиз с APK и notes.

**При ошибке:**

- Если workflow красный — смотреть лог в Actions, фикс
  отдельным коммитом в `dev`.
- Если тег создан ошибочно — удалить через GitHub UI
  (`Releases → Tags → Delete tag`), снять локально
  (`git tag -d vX.Y.Z`), создать заново после исправлений.

---
