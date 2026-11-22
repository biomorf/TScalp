<!-- FILE_BEGIN -->
# Release process

Документ описывает, как формируются versionName / versionCode
и как проходит релиз.

**Основной CI сейчас — GitHub Actions.** GitLab CI временно
отключён: квота Free Tier исчерпана. Пайплайн GitLab будет
восстановлен после настройки self-hosted runner'а
(см. `docs/ci-roadmap.md` → «Self-hosted GitLab Runner через
Ansible + Podman»). Раздел про GitLab-релиз сохранён ниже и
помечен
как временно отключённый.










<!-- SECTION_BEGIN: release-process -->
## Версии










<!-- SUBSECTION_BEGIN: release-process-1 -->
<a id="release-process-1"></a>
### release-process-1 versionName
- **Локально** (без `-PreleaseVersionName`): `1.<yy.MM.dd.HHmm>`
  в UTC. Пример: `1.26.10.07.1430`.
- **В CI на теге** `vX.Y.Z`: ровно `X.Y.Z` (без префикса `v`).

---
<!-- SUBSECTION_END: release-process-1 -->










<!-- SUBSECTION_BEGIN: release-process-2 -->
<a id="release-process-2"></a>
### release-process-2 versionCode
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
<!-- SUBSECTION_END: release-process-2 -->










<!-- SUBSECTION_BEGIN: release-process-3 -->
<a id="release-process-3"></a>
### release-process-3 GitHub Repository secrets (актуально)
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
per-environment значения) — см. `docs/ci-roadmap.md` →
«Переход на GitHub Environment secrets».

---
<!-- SUBSECTION_END: release-process-3 -->










<!-- SUBSECTION_BEGIN: release-process-4 -->
<a id="release-process-4"></a>
### release-process-4 GitLab CI/CD Variables (временно отключены)
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
<!-- SUBSECTION_END: release-process-4 -->










<!-- SUBSECTION_BEGIN: release-process-5 -->
<a id="release-process-5"></a>
### release-process-5 Аннотированные, не легковесные
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

---
<!-- SUBSECTION_END: release-process-5 -->










<!-- SUBSECTION_BEGIN: release-process-6 -->
<a id="release-process-6"></a>
### release-process-6 Защита веток и тегов
**Детали — в отдельном справочнике:** `docs/repo-protection.md`.
Там описаны:

- все Rulesets проекта (ветки, теги), их правила и bypass;
- известные ограничения GitHub (picker required checks,
  имя job'а vs UI-имя, gate-job pattern);
- GitLab Protected Branches / Tags и таблица эквивалентов;
- процедуры: добавить required check, восстановиться
  после ошибочного тега.

**Кратко:** защита реализована через GitHub Rulesets.
Ветки `master`, `dev`, `release/**`, `hotfix/**` — только
через PR, с обязательным статусом CI и запретом
force-push / удаления. Теги `v*` — иммутабельны, создание
только для Maintainers.

---
<!-- SUBSECTION_END: release-process-6 -->










<!-- SUBSECTION_BEGIN: release-process-7 -->
<a id="release-process-7"></a>
### release-process-7 Стратегия merge
Используется **Merge commit** (`Create a merge commit`).

Почему не squash и не rebase:

- **Squash** создаёт новый коммит с другим SHA. Ветки `dev`
  и `master` расходятся. При следующем PR GitHub видит
  «конфликт» в файлах, которые не менялись, — потому что
  SHA у параллельных версий разные. Приходится разрешать
  вручную каждый раз.
- **Rebase** сохраняет линейную историю, но переписывает SHA
  всех коммитов PR. Та же проблема с расхождением веток
  после нескольких циклов.
- **Merge commit** сохраняет SHA коммитов из `dev`. Ветки
  не расходятся, конфликты почти исключены, история честно
  отражает, что был PR.

Минус Merge commit — нелинейная история с отдельным
merge-коммитом на каждый PR. Для одиночного проекта это
приемлемо.

---
<!-- SUBSECTION_END: release-process-7 -->










<!-- SUBSECTION_BEGIN: release-process-8 -->
<a id="release-process-8"></a>
### release-process-8 Настройка GitHub
- `Settings → General → Pull requests` → `Allow merge commits`
  включено.
- `Settings → Rules → Rulesets → Protect master` →
  `Require linear history` **выключено** (иначе Merge commit
  недоступен, только Squash и Rebase).

Проверка после merge:

    git fetch origin
    git log origin/master -1 --oneline

Верхний коммит — merge-коммит с сообщением вида
`Merge pull request #N from biomorf/dev`.

---
<!-- SUBSECTION_END: release-process-8 -->










<!-- SUBSECTION_BEGIN: release-process-9 -->
<a id="release-process-9"></a>
### release-process-9 Чек-лист релиза
Перед созданием тега:

1. Убедиться, что `dev` содержит все нужные коммиты.
2. Запушить `dev`:

       git push origin dev

3. Создать ветку `release/vX.Y.Z` от `dev`:

       git checkout dev && git pull
       git checkout -b release/vX.Y.Z
       git push -u origin release/vX.Y.Z

4. Стабилизация в `release/vX.Y.Z` — только фиксы.

5. Создать **Pull Request** `release/vX.Y.Z → master`
   в GitHub UI.

   *GitLab:* Создать MR `release/vX.Y.Z → master`
   в GitLab UI.

6. Дождаться зелёного `ci.yml` на PR.

   *GitLab:* Дождаться зелёного пайплайна на MR.

7. Merge PR через **Merge commit** (единственная
   разрешённая стратегия — см. `docs/git-flow.md`).

   *GitLab:* Смерджить MR.

8. Создать аннотированный тег `vX.Y.Z` на `master`:

       git tag -a vX.Y.Z -m "Release vX.Y.Z"
       git push origin vX.Y.Z

9. Обратный merge `master → dev`:

       # PR master -> dev через web (Merge commit)

10. Локально:

       git checkout master
       git pull --ff-only origin master

   Если `--ff-only` падает — значит `dev` и `master` разошлись,
   разобраться до продолжения.
11. Проверить, что `origin/master` содержит нужный коммит:

       git log origin/master -1 --oneline

Создание тега:

12. `git tag -a vX.Y.Z -m "Release vX.Y.Z"`.
13. **Проверить перед пушем**:

       git branch -r --contains vX.Y.Z

   В выводе **обязан** быть `origin/master`. Если нет — тег
   создан не на том коммите, удалить и повторить с шага 6.
14. `git push origin vX.Y.Z`.

Пуш только тега, без `--tags`: `git push --tags` отправит все
локальные теги, включая случайные.

После пуша:

15. Открыть GitHub → вкладку **Actions** → workflow **Release**.
    Убедиться, что job зелёный.
16. Открыть вкладку **Releases** → проверить, что создан
    релиз с APK и notes.

**При ошибке:**

- Если workflow красный — смотреть лог в Actions, фикс
  отдельным коммитом в `dev`.
- Если тег создан ошибочно — удалить через GitHub UI
  (`Releases → Tags → Delete tag`), снять локально
  (`git tag -d vX.Y.Z`), создать заново после исправлений.

---
<!-- SUBSECTION_END: release-process-9 -->










<!-- SECTION_END: release-process -->










<!-- FILE_END -->
