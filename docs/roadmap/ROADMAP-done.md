<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: roadmap-done -->
## Сделано (ожидает архивации)










<!-- SUBSECTION_BEGIN: t-13 -->
<a id="t-13"></a>
### [DONE] T-13 Compose UI: test infra + 3 экрана — ✅ done (U0–U4)
**Статус:** выполнено.

См. `ROADMAP-ARCHIVE.md` → `### ✅ Compose UI: test infra + 3 экрана (U0–U4)`.

---
<!-- SUBSECTION_END: t-13 -->










<!-- SUBSECTION_BEGIN: t-1 -->
<a id="t-1"></a>
### [DONE] T-1 docs/: рефакторинг журналов (ROADMAP, ci-roadmap, ISSUES, clean-audit)
**Статус:** выполнено (2026-11-16)
**Мобильная работа:** полное закрытие
**Контекст:** `docs/` — 16 файлов, 1.5MB, без `SECTION_BEGIN`/
`SUBSECTION_BEGIN`. Крупные журналы (`ROADMAP.md` 153KB,
`clean-audit.md` 210KB, `ISSUES.md` 93KB, `ci-roadmap.md` 57KB)
читаются ассистентом целиком — дорого и медленно. Нужно:
разбить на секции-файлы, тонкие индексы, статус-токены + ID
для задач, шаблоны — в отдельные файлы, линтер в CI.
**Соответствие Clean:** — (не применимо, документация)
**Нарушенное правило Clean:** —
**Clean-альтернатива:** —
**Выгоды:** —
**MVVM:** — (не применимо)
**KMP:** — (не применимо)
**Предусловия:** `AGENTS/scripts/` (normalize, add, cut,
rebuild_index) — есть; `seed_markers_docs.py` (oneoff) — создан.
**Файлы:**
- `docs/roadmap/` — ROADMAP + ci-roadmap: индексы, `-wip`/
  `-plan`/`-defer`/`-done`, шаблоны `*-task-template.md`.
- `docs/issues/` — ISSUES.md (индекс), ISSUES-ARCHIVE.md
  (новый), `ISSUES-<component>.md` (streams, brokers, ui,
  data, domain, di, ci, misc).
- `docs/clean-audit/` — clean-audit.md (индекс),
  `clean-audit-L1.md` … `L8.md`.
- `AGENTS/scripts/oneoff/seed_markers_docs.py` — одноразовый.
- `docs/scripts/check-docs.sh` — линтер (5 проверок).
- `AGENTS/scripts/docs_normalize.py`, `docs_add.py`, `docs_cut.py`,
  `docs_rebuild_index.py` — переиспользуются с `--root docs/`.
**Шаги:**
1. **Фаза 0:** `seed_markers_docs.py --apply` — `SECTION_*`
   вокруг `##`, `SUBSECTION_*` вокруг `###` в `docs/*.md`
   (кроме архивов).
2. **Фаза 1 (ROADMAP + ci-roadmap):**
   - шаблоны → `ROADMAP-task-template.md`,
     `ci-roadmap-task-template.md`;
   - статус-токены `[WIP]/[PLAN]/[DEFER]/[DONE]` + ID
     `T-<N>`/`C-<N>` в каждый `###`;
   - разбивка по статусам: `-wip`/`-plan`/`-defer`/`-done`
     (архивы не трогаем);
   - `add.py --at-end` в обратном порядке (прямой порядок
     в файле).
3. **Фаза 2 (ISSUES):** разбивка по компонентам (A),
   `ISSUES.md` — индекс, `ISSUES-ARCHIVE.md` — пустой.
4. **Фаза 3 (clean-audit):** разбивка по слоям `L1`–`L8`.
5. **Фаза 4 (линтер):** `docs/scripts/check-docs.sh` —
   normalize `--check`, check-index, статус-токен + ID,
   `**Статус:**` ↔ токен, link checker.
**Референсы:** `AGENTS/scripts/*.py`, `docs/ROADMAP.md`
(текущий), `docs/ci-roadmap.md`, `docs/ISSUES.md`,
`docs/clean-audit.md`.
**Ограничения:** архивы (`ROADMAP-ARCHIVE.md`,
`CI-ROADMAP-ARCHIVE.md`) — не трогать. Архив `ISSUES` пока
пустой. Статус-токены — строго
`[WIP]/[PLAN]/[DEFER]/[DONE]`, имена файлов — строго
`-wip`/`-plan`/`-defer`/`-done`.
**Критерий готовности:** 4 фазы; `ROADMAP.md` ≤200 строк
(индекс); `check-docs.sh` зелёный; `git status` чист;
все `###` в ROADMAP/ci-roadmap имеют токен + ID.
**Оценка:** ~6–8 часов (4 фазы + линтер).
**Зависимости:** — (Фаза 0 уже начата).
**Открытые вопросы:** —

---
<!-- SUBSECTION_END: t-1 -->










<!-- SUBSECTION_BEGIN: t-47 -->
<a id="t-47"></a>
### [DONE] T-47 docs_cut: тело блока и обёртка inline.sh
**Дата закрытия:** 2026-11-22
**Мобильная работа:** полное закрытие (proot + PR; гейт на CI).
**Статус:** выполнено.
**Что сделано:** `docs_cut.py` пишет вторым выходом тело блока (`agents-body-<id>.md`), пара `cut → add` работает без `nested BEGIN`;
добавлен `inline.sh` — каркас тела (`tee`, `RC`, `wc`, `exit`, `--log`, `--keep`, предупреждение о теле без шапки), тело остаётся обычным самодостаточным скриптом;
добавлен `gate-commit.sh` — `run-local-gate.sh` → сверка индекса с явным списком → коммит → push только по `--push`;
в `run-local-gate.sh` исправлен пропущенный `shift` у `--keep` (разбор аргументов зацикливался);
`r4i` переписан под одну обёртку: тело — обычный скрипт, захват кода — `set +e` вокруг вызова, гейт — вызов `gate-commit.sh`.
Гейт `docs_lint.py --all` зелёный.
**Контекст:** штатная операция `r5g` «перенести / start / defer / закрыть» парой `docs_cut.py` → `docs_add.py` не работает: `docs_cut.py` пишет в tmp целый блок (`SUBSECTION_BEGIN` + якорь + `### …` + тело + `---` + `SUBSECTION_END`), а `docs_add.py` сам добавляет `BEGIN`, якорь и `### <title>`, а `--body-file` подставляет как есть. На входе получается вложенный `SUBSECTION_BEGIN`, `normalize_text` падает `AssertionError: nested BEGIN` — перенос задачи невозможен. Обнаружено при старте T-45.
**Второй дефект:** каркас inline-скриптов (`r4i`, шаблон 1) копируется в каждый блок руками — `tee`, `RC=${PIPESTATUS[0]}`, `echo&&echo "---"`, `wc -l`, `exit $RC`. Забытый элемент искажает exit-код и вывод.
**Соответствие Clean:** — (не применимо, инструменты документации).
**Нарушенное правило Clean:** —
**Clean-альтернатива:** —
**Выгоды (для «отклонение по 2c»):** —
**MVVM:** — (не применимо).
**KMP:** — (не применимо).
**Предусловия:** T-44 закрыт (скрипты `docs_add` / `docs_cut` / `docs_normalize` / `docs_lint` существуют).
**Файлы:**
- `AGENTS/scripts/docs_cut.py` — второй выход: тело блока.
- `AGENTS/scripts/inline.sh` — новый.
- `AGENTS/scripts/README.md` — строки в таблице инструментов.
- `AGENTS/AGENTS-style-docs.md` → `r4i` — альтернативный запуск через `inline.sh`.
**Шаги:**
1. `docs_cut.py`: кроме `agents-cut-<id>.md` (блок, поведение не меняется) писать `agents-body-<id>.md` — только тело: без `SUBSECTION_BEGIN`, без якоря, без строки `### …`, без завершающих `---` и `SUBSECTION_END`. Pre-assert: блок найден ровно один раз. Post-assert: в теле нет `SUBSECTION_`, `FILE_`, `<a id=`; размер > 0.
2. `inline.sh`: читает stdin во временный файл (`4k`: `/tmp`, fallback `$HOME/tmp`), исполняет **отдельным** `bash` (тело самодостаточно — без обёртки работает так же), затем `tee` в `$HOME/deepseek-chat.txt`, `RC`, `echo&&echo "---"`, `wc -l`, `exit $RC`. Опции: `--log <file>`, `--keep`. `WARN`, если в теле нет `set -e` / `set -uo pipefail` — без падения.
3. `AGENTS/scripts/README.md`: строка `inline.sh` в таблицу; у `docs_cut.py` — упоминание второго выхода.
4. `r4i`: добавить альтернативный запуск (`bash AGENTS/scripts/inline.sh <<'BASH_END'`) и два раздела: «что гарантирует скрипт» (tee / RC / wc / exit) и «что остаётся на авторе» (`set -euo pipefail`, явный `cd` и пути, pre/post-assert с сообщением, verify в том же блоке, line-anchored проверки, `echo` без backticks, `printf` для commit message, внешний fence 5 тильд).
5. `docs_rebuild_index.py --apply`, затем `docs_lint.py --all` — зелёный.
**Тесты:** `inline.sh` на теле `set -euo pipefail` + `false` → exit 1, лог непустой; тело без шапки → `WARN`, exit 0; `docs_cut.py --apply` на копии `ROADMAP-plan.md` → оба файла, тело без маркеров; `docs_add.py --body-file agents-body-<id>.md` → вставка без `nested BEGIN`; `docs_lint.py --all` → `ALL CHECKS PASSED`.
**Референсы:** `AGENTS/AGENTS-style-docs.md` → `r4i`, `r4c`, `r4d`; `AGENTS/AGENTS-style-git.md` → `r5g`; `AGENTS/scripts/README.md`; `docs/docs-structure.md` §4.
**Ограничения:** `docs_add.py` не меняется; вывод `docs_cut.py` для блока не меняется; `r5g` не меняется (пара `cut → add` остаётся штатной, `--body-file` указывает на `agents-body-…`); автодетекта блока в `docs_add` не вводится.
**Критерий готовности:** старт и перенос задачи проходят парой `docs_cut → docs_add` без падения; `inline.sh` исполняет самодостаточное тело и печатает `RC` и `wc -l`; README содержит строки обоих скриптов; `docs_lint.py --all` зелёный.
**Оценка:** 30–45 мин.
**Зависимости:** T-44.
**Связано:** T-44, T-45 (потребитель — старт и закрытие задачи).
**Открытые вопросы:** старт T-47 выполняется разово inline-переносом (bootstrap: инструмент, делающий перенос штатным, появляется этой же задачей; решение пользователя в этой сессии). Возможное расширение — `docs_move.py` (одна атомарная операция): рассмотрено и отклонено в пользу минимальной правки `docs_cut.py`.

---
<!-- SUBSECTION_END: t-47 -->










<!-- SECTION_END: roadmap-done -->










<!-- FILE_END -->
