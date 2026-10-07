# Release process

Документ описывает, как формируются versionName / versionCode
и как проходит релиз через GitLab CI.

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

**Статус:** настроено.

В GitLab включены **Protected Tags**:

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

Релиз делается **через fast-forward**:

    git checkout master
    git merge --ff-only dev
    git tag -a vX.Y.Z -m "Release vX.Y.Z"

После этого `master`, `dev` и тег указывают на один и тот же
коммит — легко проверить вручную, что зарелизили именно то,
что было на dev.

Полный `git merge dev` (создание merge-коммита) не используется:
merge-коммит существует только на master, и тег на нём сложнее
сверить с состоянием dev. Если когда-нибудь появится требование
аудита через merge-коммиты — пересмотреть.

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

## Релиз через GitLab CI

1. Убедиться, что нужный коммит в `master`.
2. Поставить тег `vX.Y.Z` и запушить:

       git tag v1.0.0
       git push origin v1.0.0

3. CI собирает pipeline:
    - `unit-tests` — прогон на всех ветках + тегах;
    - `build-release` — проверяет, что тег стоит на `master`,
      расшифровывает keystore из `KEYSTORE_BASE64`, собирает
      `assembleRelease -PreleaseVersionName=X.Y.Z`;
    - `publish-release` — создаёт GitLab Release с APK в assets.

---

### Чек-лист релиза

Перед созданием тега:

1. Убедиться, что `dev` содержит все нужные коммиты.
2. Запушить `dev`: `git push origin dev`.
3. Создать MR `dev → master` в GitLab UI.
4. Дождаться зелёного пайплайна на MR.
5. Смерджить MR.
6. Локально: `git checkout master && git pull --ff-only origin master`.
7. Проверить, что `origin/master` содержит нужный коммит:
   `git log origin/master -1 --oneline`.

Создание тега:

8. `git tag -a vX.Y.Z -m "Release vX.Y.Z"`.
9. **Проверить перед пушем**:
   `git branch -r --contains vX.Y.Z`.
   В выводе **обязан** быть `origin/master`. Если нет — тег
   создан не на том коммите, удалить и повторить с шага 6.
10. `git push origin vX.Y.Z`.

Пуш только тега, без `--tags`: `git push --tags` отправит все
локальные теги, включая случайные.

---

## CI-переменные

Все задаются в GitLab → Settings → CI/CD → Variables.

| Переменная          | Masked | Protected | Назначение                     |
|---------------------|--------|-----------|--------------------------------|
| `KEYSTORE_BASE64`   | да     | да        | `.jks` в base64                |
| `KEYSTORE_PASSWORD` | да     | да        | пароль хранилища               |
| `KEY_ALIAS`         | нет    | да        | alias ключа в keystore         |
| `KEY_PASSWORD`      | да     | да        | пароль ключа                   |

`KEY_ALIAS` не маскируется: alias не является секретом
(он в открытом виде лежит внутри `.jks`). GitLab всё равно
требует минимум 8 символов для маскировки — `tscalp` (7) не пройдёт.