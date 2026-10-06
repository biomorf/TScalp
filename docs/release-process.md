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