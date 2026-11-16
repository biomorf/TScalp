# AGENTS-LESSONS.md — указатель

Полный индекс — `AGENTS/AGENTS-LESSONS.md`. Здесь — только гейт
обязательного чтения и краткая карта.

## Обязательное чтение (гейт запуска)

До любой работы — по порядку:

1. `AGENTS/AGENTS-LESSONS-preamble.md` — обязательства и рамка уроков.
2. `AGENTS/AGENTS-LESSONS-docs.md` — обязательные файлы LESSONS и формат отчёта.
`AGENTS/AGENTS-report.md` — единый формат отчёта о прочтении.
3. Все `AGENTS/AGENTS-LESSONS-*.md` — уроки (scripts, kotlin,
   diagnostics, clean-audit, ui, ci, estimates, process, application).
4. Выдать отчёт по формату из `AGENTS/AGENTS-LESSONS-docs.md`.

Без шагов 1–4 работа не начинается.

## Справочник (по мере необходимости)

| ID | Заголовок | Файл | Якорь |
|---|---|---|---|
| l-application-89 | Merge `origin/dev` в feature перед PR — через `--no-commit --no-ff` | AGENTS/AGENTS-LESSONS-application.md | #l-application-89 |
| l-application-93 | 4f: правки → пауза → гейт+коммит → пауза (не в одном блоке) | AGENTS/AGENTS-LESSONS-application.md | #l-application-93 |
| l-application-104 | 5f-freeze: dev заморожен до трёх условий merge | AGENTS/AGENTS-LESSONS-application.md | #l-application-104 |
| l-application-106 | Test-код → feature-ветка + PR, не push в dev | AGENTS/AGENTS-LESSONS-application.md | #l-application-106 |
| l-application-107 | 4h mobile pre-commit check — до commit, не после | AGENTS/AGENTS-LESSONS-application.md | #l-application-107 |
| l-ci-8 | `feature/**` push → CI запускается сразу | AGENTS/AGENTS-LESSONS-ci.md | #l-ci-8 |
| l-ci-9 | Порядок падений CI: compile → ktlint → detekt | AGENTS/AGENTS-LESSONS-ci.md | #l-ci-9 |
| l-ci-10 | `paths-ignore` — docs-only не запускает CI | AGENTS/AGENTS-LESSONS-ci.md | #l-ci-10 |
| l-ci-11 | HTML-репорт ktlint на CI — недоступен с телефона | AGENTS/AGENTS-LESSONS-ci.md | #l-ci-11 |
| l-ci-33 | Матрица AGP ↔ Gradle ↔ Hilt — точные версии | AGENTS/AGENTS-LESSONS-ci.md | #l-ci-33 |
| l-ci-70 | ktlint multiline chain — `.` с новой строки | AGENTS/AGENTS-LESSONS-ci.md | #l-ci-70 |
| l-ci-71 | detekt `CyclomaticComplexMethod` на Compose — расширять `@Suppress` | AGENTS/AGENTS-LESSONS-ci.md | #l-ci-71 |
| l-ci-95 | PR title — `<type>(<scope>): …`, как commit message | AGENTS/AGENTS-LESSONS-ci.md | #l-ci-95 |
| l-ci-102 | `--rerun-tasks` не обходит Gradle build-cache; новые тесты — `--no-build-cache` | AGENTS/AGENTS-LESSONS-ci.md | #l-ci-102 |
| l-clean-audit-87 | Подсчёт функций класса через grep занижен при multi-prefix | AGENTS/AGENTS-LESSONS-clean-audit.md | #l-clean-audit-87 |
| l-clean-audit-20 | Инвентаризация не пропускает «уже известное» | AGENTS/AGENTS-LESSONS-clean-audit.md | #l-clean-audit-20 |
| l-clean-audit-66 | Перенос в «Исправленные (L<N>)» — ### ✅ и актуальная ссылка на задачу | AGENTS/AGENTS-LESSONS-clean-audit.md | #l-clean-audit-66 |
| l-diagnostics-85 | ktlint `multiline-expression-wrapping` — вынести subject в `val` | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-85 |
| l-diagnostics-94 | `.kt` file-level comment — `//`, не `/** */`, если у функций есть KDoc | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-94 |
| l-diagnostics-96 | ktlint multiline: аргументы и цепочки — по одному на строке | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-96 |
| l-diagnostics-98 | `advanceUntilIdle` + `while(isActive){delay}` в `init` = guaranteed hang | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-98 |
| l-diagnostics-99 | Compose UI на Robolectric: runner + GraphicsMode NATIVE + ConscryptMode OFF | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-99 |
| l-diagnostics-105 | `data class *State`: два поля на одно значение — красный флаг | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-105 |
| l-diagnostics-17 | KDoc vs код — grep фактическое использование | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-17 |
| l-diagnostics-18 | Не угадывать архитектурный intent | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-18 |
| l-diagnostics-19 | Структурные находки в документах — grep, не память | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-19 |
| l-diagnostics-25 | `awk /^### X/,/^### /` закрывается на самом X | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-25 |
| l-diagnostics-31 | «UI не обновляется» — по слоям, не по симптомам | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-31 |
| l-diagnostics-32 | `MutableSharedFlow(replay = 0)` не отдаёт текущее состояние | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-32 |
| l-diagnostics-46 | Удаление fat-метода — проверять supertype реализаций | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-46 |
| l-diagnostics-47 | `grep -c` на структурных маркерах — ложные тревоги | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-47 |
| l-diagnostics-48 | Import-order чекер — `java`/`javax`/`kotlin` в конце | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-48 |
| l-diagnostics-90 | DataStore на устройстве — `exec-out run-as base64`, не `shell run-as cat` | AGENTS/AGENTS-LESSONS-diagnostics.md | #l-diagnostics-90 |
| l-docs-obligatory-files | Обязательные файлы | AGENTS/AGENTS-LESSONS-docs.md | #l-docs-obligatory-files |
| l-docs-report-format | Формат отчёта | AGENTS/AGENTS-LESSONS-docs.md | #l-docs-report-format |
| l-docs-reason | Причина | AGENTS/AGENTS-LESSONS-docs.md | #l-docs-reason |
| l-estimates-12 | Scriptable-задачи — реальное время в 5–10× меньше оценки | AGENTS/AGENTS-LESSONS-estimates.md | #l-estimates-12 |
| l-kotlin-86 | `assert "${X}" not in text` — ловит X в других методах | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-86 |
| l-kotlin-88 | Idempotency guard `if new_fragment in text: skip` неверен, когда new ⊂ old | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-88 |
| l-kotlin-91 | Verify = call-form / declaration-form, никогда `not in text` для symbols | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-91 |
| l-kotlin-92 | `re.sub` replacement — raw-string или без escaped-symbol; `SyntaxWarning` = сигнал | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-92 |
| l-kotlin-97 | uniqueness pre-check до replace: `count == 1`, а не `new in text` | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-97 |
| l-kotlin-108 | Idempotency guard — в extract'е блока, не глобальный `grep` по файлу | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-108 |
| l-kotlin-109 | `grep -c` при 0 совпадениях возвращает exit 1 — убивает `set -e` | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-109 |
| l-kotlin-110 | `cmd && echo OK` под `set -e` не abort'ит — только левая часть как statement | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-110 |
| l-kotlin-111 | Commit message — английский, включая цитаты названий и полей | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-111 |
| l-kotlin-112 | Idempotency guard — по `old`, не по `new`; `new ⊂ old` — ложно-«уже применено» | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-112 |
| l-kotlin-113 | JVM/Gradle-флаги — синтаксис проверить до применения | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-113 |
| l-kotlin-114 | YAML block scalar (`run: \|`) — многострочный контент одинаково сдвинут | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-114 |
| l-kotlin-72 | Smart cast невозможен для computed getter | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-72 |
| l-kotlin-73 | `data class` non-null common param без default — обязателен | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-73 |
| l-kotlin-74 | Locale-dependent `format` — `Locale.US` в продакшене | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-74 |
| l-kotlin-75 | `Currency.getInstance` — ISO 4217 uppercase | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-75 |
| l-kotlin-78 | Mobile/proot: ktlint-ошибки на `.kt` — только CI | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-78 |
| l-kotlin-84 | `TooManyFunctions` на классе-менеджере — extraction в top-level extension | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-84 |
| l-kotlin-103 | Hilt-test infra pitfalls (Robolectric + Compose) | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-103 |
| l-kotlin-115 | Rate-limiter per-minute bucket capacity = 1s burst, not full minute | AGENTS/AGENTS-LESSONS-kotlin.md | #l-kotlin-115 |
| l-process-21 | reset --hard — перечислить теряемые коммиты | AGENTS/AGENTS-LESSONS-process.md | #l-process-21 |
| l-process-22 | В proot писать в $HOME, не /tmp | AGENTS/AGENTS-LESSONS-process.md | #l-process-22 |
| l-process-34 | Миграция DI — поэтапно, компиляция между этапами | AGENTS/AGENTS-LESSONS-process.md | #l-process-34 |
| l-process-35 | Hilt: три аннотации — Application, Activity, ViewModel | AGENTS/AGENTS-LESSONS-process.md | #l-process-35 |
| l-process-36 | `SharedPreferences` — один `@Provides` на всё приложение | AGENTS/AGENTS-LESSONS-process.md | #l-process-36 |
| l-process-37 | Рефакторинг ≠ изменение поведения | AGENTS/AGENTS-LESSONS-process.md | #l-process-37 |
| l-process-38 | «Обсуждаем» / «планируем» — не код | AGENTS/AGENTS-LESSONS-process.md | #l-process-38 |
| l-process-50 | `git revert --no-commit` — не забыть `--continue`/`--abort` | AGENTS/AGENTS-LESSONS-process.md | #l-process-50 |
| l-scripts-1 | Grep по `main` и `test` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-1 |
| l-scripts-2 | `str.count` ≠ `grep -c` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-2 |
| l-scripts-3 | Supertype в Impl → import Interface | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-3 |
| l-scripts-4 | `internal` + `public @Binds` = EXPOSED_PARAMETER_TYPE | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-4 |
| l-scripts-5 | `mockk(relaxed = true)` — импорт типа не нужен | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-5 |
| l-scripts-6 | Порядок импортов — по алфавиту, всегда | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-6 |
| l-scripts-7 | `grep -v` фильтр в verify может врать | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-7 |
| l-scripts-23 | `text.find("### Header")` находит inline-ссылку | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-23 |
| l-scripts-24 | `count("**Marker:**") == 1` ложно-отрицателен для общих маркеров | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-24 |
| l-scripts-26 | `assert "old_name" not in text` — KDoc содержит старое имя | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-26 |
| l-scripts-27 | Голый `assert` в скрипте — нет контекста падения | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-27 |
| l-scripts-28 | `write_text` до post-write assert — файл уже изменён | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-28 |
| l-scripts-13 | Anchor в скрипте — grep перед написанием | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-13 |
| l-scripts-14 | Fence вложенного блока — 4 backticks или `~~~` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-14 |
| l-scripts-15 | Серия скриптов на один файл — по одному, `git diff` после каждого | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-15 |
| l-scripts-16 | Скрипт + верификация в одном блоке | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-16 |
| l-scripts-29 | Не изобретать — grep перед использованием | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-29 |
| l-scripts-30 | `return@label` требует наличия label в области видимости | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-30 |
| l-scripts-40 | `git add -A` — источник инцидента | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-40 |
| l-scripts-41 | `mv` без `-f` на телефоне — зависание на `overwrite?` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-41 |
| l-scripts-42 | `.tmp + mv` — коммит может захватить `.tmp` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-42 |
| l-scripts-43 | Heredoc на телефоне — риск потери `MSGEOF` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-43 |
| l-scripts-44 | `set -e` в блоках с несколькими командами | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-44 |
| l-scripts-45 | Парсер multi-line overrides — brace-balance, не regex | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-45 |
| l-scripts-49 | Не писать в `.tmp` до полной проверки всех `.tmp` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-49 |
| l-scripts-51 | `bash -c '...'` ломается от одинарных кавычек в содержимом | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-51 |
| l-scripts-52 | `write_text` в Python ломает line endings | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-52 |
| l-scripts-53 | `git diff --cached --stat` перед коммитом | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-53 |
| l-scripts-54 | Шаблон 4f блока 2 — не урезать grep-фильтр | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-54 |
| l-scripts-55 | На desktop — `./gradlew :app:ktlintFormat`, не ручной перенос импортов | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-55 |
| l-scripts-56 | `assert count == 1` для имени задачи — только line-anchored; структурные проверки — awk, не grep-контекст | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-56 |
| l-scripts-57 | JVM/JUnit API-ограничения: проверять до первого запуска | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-57 |
| l-scripts-58 | post-assert count — считать по факту диагностики, не по «логической» сумме | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-58 |
| l-scripts-59 | Откат блока в `.kt`/`.md` — anchor на следующий член, не на `}` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-59 |
| l-scripts-60 | detekt `TooManyFunctions` на DI-модуле — split на доменные модули, не `@Suppress` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-60 |
| l-scripts-61 | Двусторонняя проверка импортов после удаления кода | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-61 |
| l-scripts-62 | Import order после rename — лексикографически, не только «нет старого имени» | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-62 |
| l-scripts-63 | Rename класса — все формы | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-63 |
| l-scripts-64 | echo с backticks внутри двойных кавычек — command substitution | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-64 |
| l-scripts-65 | assert new not in text, когда new — подстрока old | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-65 |
| l-scripts-67 | `assert "### X" not in text` — line-anchored regex, не подстрока | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-67 |
| l-scripts-68 | `Path.read_text()` возвращает trailing `\n` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-68 |
| l-scripts-69 | Вставка в конец файла — `rstrip()` + `\n` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-69 |
| l-scripts-76 | `unused import` после удаления кода | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-76 |
| l-scripts-77 | Commit message / PR body / tag — English only, strict pre-check | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-77 |
| l-scripts-79 | post-assert: только новое + `old_frag not in text` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-79 |
| l-scripts-80 | `echo` с backtick — 3-й раз; проверяй факт, не форму | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-80 |
| l-scripts-81 | Multiline pre-check в скрипте — `\($` / `\([^)]*$`, не `\(` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-81 |
| l-scripts-82 | 5g.4 при extract — перенос комментариев | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-82 |
| l-scripts-83 | Clean-audit refs — проверять ID | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-83 |
| l-scripts-100 | `git stash push` no-op → `git stash pop` в том же блоке запрещён | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-100 |
| l-scripts-101 | `grep -cE` с неэкранированной `(` — Unmatched; для unused-import — `grep -cF` + skip wildcards | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-101 |
| l-scripts-115 | adb в heredoc: `< /dev/null`, `timeout --foreground`, `--pid` | AGENTS/AGENTS-LESSONS-scripts.md | #l-scripts-115 |
| l-ui-39 | Дизайн/стили/иконки — только по явному указанию | AGENTS/AGENTS-LESSONS-ui.md | #l-ui-39 |
