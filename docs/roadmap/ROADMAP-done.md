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










<!-- SUBSECTION_BEGIN: t-45 -->
<a id="t-45"></a>
### [DONE] T-45 Миграция docs/*.md под канон
**Дата закрытия:** 2026-11-22
**Мобильная работа:** полное закрытие (proot + PR; docs, гейт на CI).

**Статус:** выполнено.
**Что сделано:** двенадцать страниц `docs/*.md` приведены к канону: `FILE_BEGIN`/`FILE_END`, `SECTION`-отступы, 111 якорей подсекций, `---` перед каждым `SUBSECTION_END`, забранные `___` удалены, 10 пустых строк на переходах;
содержательный текст не менялся — по каждому файлу проверено, что ни одна удалённая строка не является текстом (только маркеры, `___`, `---` и пустые);
`docs/gradle-setup.md`: приклеенный к тексту `SECTION_END` разрезан на отдельную строку;
`docs/brokers.md`: пять секций с одинаковым id `brokers` переименованы в `brokers-matrix`, `brokers-tinvest`, `brokers-bcs`, `brokers-finam`, `brokers-refs` (внешних ссылок на эти якоря в репозитории нет);
гейт: `SCOPE_DIRS` включает `docs`, добавлена дедупликация путей (иначе `docs/ROADMAP.md` и `docs/ci-roadmap.md` попадали дважды); общий прогон — 56 файлов, `ALL CHECKS PASSED`.
Коммиты: `c92375c4`, `e80eec17`, `97f80968`.

**Контекст:** канон `docs/docs-structure.md` (§1) требует `FILE_BEGIN`/`FILE_END` от **любого** `.md` без исключений. Двенадцать файлов `docs/` не имеют этих маркеров вообще: часть — вообще без структурных маркеров, часть — со старым `___` вместо `---` и без якорей. Пока они такие, гейт `AGENTS/scripts/docs_lint.py --all` не может быть зелёным по всей области, поэтому в T-44 его область сужена до `AGENTS/` + `docs/roadmap/`. Задача закрывает остаток и снимает сужение.

**Соответствие Clean:** — (не применимо, документация).

**Нарушенное правило Clean:** —

**Clean-альтернатива:** —

**Выгоды (для «отклонение по 2c»):** —

**MVVM:** — (не применимо).

**KMP:** — (не применимо).

**Предусловия:** T-44 закрыт (канон, нормализатор, гейт).

**Файлы:**
- `docs/architecture.md`
- `docs/brokers.md`
- `docs/ci-cd.md`
- `docs/domain_models.md`
- `docs/git-flow.md`
- `docs/gradle-setup.md`
- `docs/ISSUES.md`
- `docs/live-test.md`
- `docs/rate-limits.md`
- `docs/release-process.md`
- `docs/repo-protection.md`
- `docs/streams.md`

**Шаги:**
1. По каждому файлу — `docs_lint.py --file`, зафиксировать отклонения в задаче.
2. Снять сужение области в `AGENTS/scripts/docs_lint.py --all`: вернуть `docs/**` в `SCOPE_DIRS`.
3. Прогон `docs_normalize.py --apply` по файлам от малых к крупным (`repair-process`, `repo-protection`, затем `git-flow`, `gradle-setup`, в конце `streams` и `architecture`), после каждого — `git diff` глазами.
4. `docs/streams.md` — отдельным коммитом: там ожидаемо больше всего расхождений (нет FILE/SECTION-маркеров).
5. `docs/ISSUES.md` — уточнить при старте, нужен ли табличный индекс: формат журнала не табличный, но §1 требует маркеры и секции.

**Тесты:** `docs_lint.py --file` по каждому файлу → 0 ошибок; `docs_lint.py --all` по всей области → `ALL CHECKS PASSED`.

**Референсы:** `docs/docs-structure.md` §1–§4; T-44 (канон и инструменты); `AGENTS/scripts/README.md`.

**Ограничения:** содержательный текст не менять — только скелет (маркеры, якоря, `---`, отступы). `docs/issues/` и `docs/clean-audit/` вне области (§1). Архивы — только уровень 1.

**Критерий готовности:** все 12 файлов проходят `docs_lint.py --file` в ноль; гейт зелёный по `docs/**`; сужение области в гейте снято.

**Оценка:** 3–5 ч. (малые файлы по 15–20 мин, `streams.md` 1–1.5 ч).

**Зависимости:** T-44.

**Связано:** T-2 (консолидация скриптов), T-44.

**Открытые вопросы:** нужен ли `docs/ISSUES.md` табличный индекс — или достаточно маркеров и секций; решается на шаге 5.
**Решение по открытому вопросу:** `docs/ISSUES.md` — файл-указатель (шапка и список файлов `docs/issues/*`), табличного индекса в нём нет и по §1 не требуется; достаточно `FILE`-маркеров.

---
<!-- SUBSECTION_END: t-45 -->










<!-- SUBSECTION_BEGIN: t-48 -->
<a id="t-48"></a>
### [DONE] T-48 docs_normalize: контроль региона между блоками
**Дата закрытия:** 2026-11-22
**Мобильная работа:** полное закрытие (proot + PR; гейт на CI).
**Статус:** выполнено.
**Что сделано:** `docs_normalize.py` перед записью проверяет регион между `SUBSECTION_END` и следующим `SUBSECTION_BEGIN`: допустимы только пустые строки, `---`, `___`. Иначе — `AssertionError` с номером строки и цитатой содержимого, файл не пишется;
проверка и её поведение описаны в epilog `--help` и в `AGENTS/scripts/README.md`;
тесты: абзац между блоками → `rc=2`, файл байт-в-байт прежний; `___` между блоками → проходит; прогон по всей области (56 файлов) → 0 записей, 0 отклонений;
случай с приклеенным к тексту маркером (T-45, `docs/gradle-setup.md`) сознательно не чинится — единичная аномалия, решение зафиксировано в задаче;
Коммит: `3e8c3f23`.
**Контекст:** `docs_normalize.py` молча выбрасывает всё между `SUBSECTION_END` и следующим `SUBSECTION_BEGIN`: регион не попадает ни в `head_lines`, ни в `tail_lines`, ни в тело блока. В T-45 это страховалось внешней проверкой «ни одна удалённая строка не является текстом», но в инструменте такой защиты нет — следующая сессия поверит нормализатору на слово.
**Соответствие Clean:** — (не применимо, инструменты документации).
**Нарушенное правило Clean:** —
**Clean-альтернатива:** —
**Выгоды (для «отклонение по 2c»):** —
**MVVM:** — (не применимо).
**KMP:** — (не применимо).
**Предусловия:** T-47 и T-45 закрыты (инструменты и канон в `dev`).
**Файлы:**
- `AGENTS/scripts/docs_normalize.py` — контроль региона между блоками.
- `AGENTS/scripts/README.md` — строка про поведение.
**Шаги:**
1. Перед записью проверять регион между `SUBSECTION_END` и следующим `SUBSECTION_BEGIN`: допустимы только пустые строки, `---`, `___`; иначе падение с номером строки и цитатой содержимого (файл не пишется).
2. Тот же регион — в отклонения как `dropped N non-blank lines between blocks: <цитата>`, чтобы это было видно до apply.
3. README: описание проверки.
**Тесты:** копия файла с текстовой строкой между блоками → падение до записи (файл байт-в-байт прежний); копия с `___` и пустыми строками → проходит; прогон по всей области (56 файлов) → без падений и без изменений; `docs_lint.py --all` зелёный.
**Референсы:** `docs/docs-structure.md` §4; `AGENTS/scripts/README.md`; T-45 (источник находки).
**Ограничения:** для корректных файлов поведение нормализатора не меняется; `docs_cut.py` и `docs_add.py` не трогаем.
**Критерий готовности:** три сценария проверены; сообщение содержит номер строки и цитату; `--all` зелёный.
**Оценка:** 25–35 мин.
**Зависимости:** T-47, T-45.
**Связано:** T-45, T-47.
**Открытые вопросы:** приклеенный к тексту маркер (случай `docs/gradle-setup.md` в T-45) сознательно не чинится — единичная невоспроизводимая аномалия, автофикс под неё — overhead; сообщение гейта для этого случая оставлено как есть. Повторно не открывать.

---
<!-- SUBSECTION_END: t-48 -->










<!-- SUBSECTION_BEGIN: t-49 -->
<a id="t-49"></a>
### [DONE] T-49 docs_lint: состояние задачи для wip и plan
**Дата закрытия:** 2026-11-22
**Мобильная работа:** полное закрытие (proot + PR; гейт на CI).
**Статус:** выполнено.
**Что сделано:** `docs_lint.py --task-state T-N` — находит блок задачи в `ROADMAP-{wip,plan,defer,done}.md` и `ci-roadmap-*`, печатает файл и токен, сверяет заголовок `### [TOKEN]` и строку `**Статус:**` с `TOKEN_STATUS`;
`docs_lint.py --require-done T-N` — прежняя семантика (DONE в `*-done.md` либо поле `**Подтверждение:**` у отложенной задачи); её вызывает `post-pr.sh`;
флаг `--task` удалён (переходный алиас не вводился: единственный потребитель в репозитории — `post-pr.sh`, переведён этим же коммитом); `allow_abbrev=False`, чтобы старое имя не маппилось молча в `--task-state` — проверено, `rc=2`;
`README.md` (аргументы `docs_lint.py`) и `r5g` (строки «проверить состояние» и «проверить закрытие») обновлены;
тесты: `--task-state T-45` → `ROADMAP-done.md, token DONE`; `--task-state T-49` → `ROADMAP-wip.md, token WIP`; `T-999` → not found; `--require-done T-49` → FAIL; `--require-done T-45` → OK; `--file` и `--all` (56 файлов) зелёные;
Коммит: `e7760f24`.
**Контекст:** `docs_lint.py --task T-N` (`check_task_state`) принимает только `[DONE]` в `*-done.md` или поле `**Подтверждение:**` в wip/plan/defer. Для задачи «в работе» команда всегда даёт `FAIL … no Подтверждение field`, поэтому на старте задачи состояние проверить нечем — в T-45 это подтвердилось дважды (T-45 и T-47).
**Соответствие Clean:** — (не применимо, инструменты документации).
**Нарушенное правило Clean:** —
**Clean-альтернатива:** —
**Выгоды (для «отклонение по 2c»):** —
**MVVM:** — (не применимо).
**KMP:** — (не применимо).
**Предусловия:** T-47 закрыт (гейт и `gate-commit.sh` в `dev`).
**Файлы:**
- `AGENTS/scripts/docs_lint.py` — два флага вместо одного.
- `AGENTS/scripts/post-pr.sh` — вызов `--require-done`.
- `AGENTS/scripts/README.md` — описание флагов.
- `AGENTS/AGENTS-style-git.md` → `r5f`, `r5g` — ссылки и примеры.
**Шаги:**
1. `docs_lint.py --task-state T-N`: найти задачу в статусных файлах, напечатать файл и статус; FAIL «not found» либо «status X != token Y».
2. `docs_lint.py --require-done T-N`: текущая семантика (DONE в `*-done.md` либо `Подтверждение`), без изменений.
3. Решить судьбу алиаса `--task`: оставить с предупреждением об устаревании либо удалить сразу.
4. `post-pr.sh`: перевести на `--require-done`.
5. `README.md`, `r5f` (шаг 9), `r5g` (таблица операций «проверить состояние») — обновить примеры.
**Тесты:** `--task-state T-45` → OK (done, выполнено); `--task-state T-48` → OK (plan, запланировано); `--task-state T-999` → FAIL not found; `--require-done T-48` → FAIL; `--require-done T-45` → OK; `bash -n AGENTS/scripts/post-pr.sh`; `docs_lint.py --all` зелёный.
**Референсы:** `AGENTS/AGENTS-style-git.md` → `r5f`, `r5g`; `AGENTS/scripts/README.md`; T-45, T-47.
**Ограничения:** поведение `--require-done` идентично текущему `--task`; мягких проверок на закрытие не вводится.
**Критерий готовности:** состояние задачи проверяется одной командой на старте; post-PR по-прежнему падает, если задача не в `done`; документация и README обновлены; `--all` зелёный.
**Оценка:** 30–45 мин.
**Зависимости:** T-47.
**Связано:** T-45, T-47.
**Открытые вопросы:** оставлять ли алиас `--task` — решается на шаге 3.

---
<!-- SUBSECTION_END: t-49 -->










<!-- SUBSECTION_BEGIN: t-9 -->
<a id="t-9"></a>
### [DONE] T-9 PortfolioScreen: double-load + TradingStatusFlowManager
**Дата закрытия:** 2026-11-24

**Мобильная работа:** полное закрытие (proot + PR; presentation + domain; гейт на CI).
**Статус:** выполнено.



**Контекст:** `PortfolioScreen:38-42` — `LaunchedEffect(lifecycleOwner) { repeatOnLifecycle(RESUMED) { refresh() } }` дергает полный `loadPortfolio()` при каждом входе в RESUMED (back-stack, foreground, поворот). Плюс `init { brokerManager.anyInitialized.collect { checkApiInitialization() } }` — ещё один `loadPortfolio()` при первом `anyInitialized` (`PortfolioViewModel:69-75`, `:95-105`). Итог: 2+ полных загрузки на cold start. `refresh()` = `viewModelScope.launch { loadPortfolio() }`, а `loadPortfolio()` сам делает `viewModelScope.launch` — двойной launch (`:262-264`, `:128-129`); retry/backoff отсутствует (см. «Связано»: T-3 sub-5 поглощается T-9; «infinite retry» — в T-3 не подтверждён; retry/backoff отсутствует, T-3 sub-5 = double-load + guard (поглощена T-9). Дополнительно: `tradingStatuses` в `PortfolioUiState` — своё поле (`PortfolioUiState:22`), VM поллит `while (isActive) { delay(5 min); updateTradingStatuses() }` (`:83-91`) и сам вызывает `marketDataRepository.getTradingStatusesResult` (`:214-235`) — дублирует `UpdateTradingStatusesUseCase` + `TradingStatusRepository` (M2/M5). Orders уже работает passthrough (`OrdersViewModel:70`), Portfolio — нет: два независимых кэша одного значения.

**Соответствие Clean:** отклонение по 2c (принято) — менеджер поллинга в `domain/api/managers/` (по образцу `PositionFlowManager`/`PriceFlowManager`), не в VM/presentation.

**Нарушенное правило Clean:** dependency rule — периодический polling статусов (domain-policy) в VM (`while (isActive)`), при этом `TradingStatusRepository` (SSoT) существует и не используется.

**Clean-альтернатива:** (а) polling в `TradingStatusRepositoryImpl` — неверно (repository пассивный); (б) polling в `UseCase` — policy, не usecase; (в) polling в VM — оставляет M2. Правильно — менеджер в `domain/api/managers/`.

**Выгоды (для «отклонение по 2c»):** полная аналогия с существующими менеджерами (`PositionFlowManager`, `PriceFlowManager`) — паттерн проекта, не изобретение; единый SSoT (`TradingStatusRepository`) для Portfolio+Orders; rate-limit safe при 60 с (batch primary + singular fallback — T-8 закрыт).

**MVVM:** `M1` + `M2` + `M5` — закрываются в T-9: UI не командует загрузкой на RESUMED (только `LaunchedEffect(Unit)` + guard от повторной загрузки); VM не поллит domain (`setInterest`/`clearInterest`); `tradingStatuses` в UI — passthrough из `TradingStatusRepository`, поле в `PortfolioUiState` удаляется.

**KMP:** соответствует по времени/транспорту; **блокер (унаследованный)** — DI: `@Singleton`/`@Inject` в domain, как в `PositionFlowManager`/`PriceFlowManager`. Не новый; фиксируется тем же отклонением, снятие — T-21.

**Предусловия:** нет (T-8 закрыт — batch primary + singular fallback есть).

**Файлы:**
- `presentation/screens/portfolio/PortfolioScreen.kt` — `repeatOnLifecycle` → `LaunchedEffect(Unit)`; источник `tradingStatuses` (стр. 111).
- `presentation/screens/portfolio/PortfolioViewModel.kt` — убрать `while (isActive)` и свой `updateTradingStatuses`; `refresh()` без двойного launch + guard; passthrough на `TradingStatusRepository.statuses`; `setInterest`/`clearInterest`.
- `presentation/screens/portfolio/PortfolioUiState.kt` — удалить поле `tradingStatuses`.
- `domain/api/managers/TradingStatusFlowManager.kt` (новый) + `TradingStatusConsumer.kt` (новый, enum).
- `test/.../domain/api/managers/TradingStatusFlowManagerTest.kt` (новый).

**Шаги (S2 — два PR):**

**Прогресс:** PR 5a merged (PR #174, `7ceacadc`); PR 5b merged (PR #175, `ebf5e8cd`); PR 5c — in progress (back-stack guard, loadJob, byBroker group, payInSandbox isLoading).

**PR 5a (~20-30 мин):**
1. `PortfolioScreen.kt:38-42`: `LaunchedEffect(lifecycleOwner) { lifecycleOwner.lifecycle.repeatOnLifecycle(RESUMED) { refresh() } }` → `LaunchedEffect(Unit) { viewModel.refresh() }`; импорты `Lifecycle`, `repeatOnLifecycle`, `LocalLifecycleOwner` удалить, если больше не используются.
2. `PortfolioViewModel.refresh()`: `fun refresh() { loadPortfolio() }` (убрать внешний `launch`); в `loadPortfolio()` первой строкой тела — `if (_uiState.value.isLoading) return` (дедуп двух триггеров cold start).
3. `while (isActive)` polling оставить (удаляется в 5b, регрессии нет).
4. Гейт на CI.

**PR 5b (~2-2.5 ч):**
1. `TradingStatusFlowManager` — конкретный `@Singleton` с `@Inject constructor` (DI-модуль не нужен, как у `PositionFlowManager`):
   - `setInterest(consumer: TradingStatusConsumer, byBroker: Map<BrokerName, Set<String>>)` — регистрирует/заменяет интерес потребителя целиком по всем брокерам за один вызов (один рестарт polling, один раунд fetch); при первом непустом объединении — immediate fetch + `while (isActive) { delay(intervalMs); fetchAndUpdate() }`;
   - `clearInterest(consumer)` — снимает интерес; при пустом объединении polling останавливается;
   - `fetchAndUpdate()` — группирует объединение ids по `brokerName` (как `PortfolioViewModel:214-229`) и вызывает `UpdateTradingStatusesUseCase.execute(brokerName, ids)` (готовый fetch+update; дублировать нельзя);
   - `INTERVAL_MS = 60_000L` (прод-дефолт; тестовый шов — internal-конструктор с `intervalMs`); scope — `CoroutineDispatcher` из `DispatcherModule` + `limitedParallelism(1)` + `SupervisorJob` + `CoroutineExceptionHandler`.
2. `TradingStatusConsumer` (enum рядом с менеджером): `PORTFOLIO`, `ORDERS` — аналог `PriceConsumer`.
3. `PortfolioViewModel`:
   - убрать `while (isActive)` и свой `updateTradingStatuses`;
   - inject `TradingStatusFlowManager` + `TradingStatusRepository`;
   - `val tradingStatuses: StateFlow<Map<String, TradingAvailability>> = tradingStatusRepository.statuses` (passthrough, как `OrdersViewModel:70`);
   - после успешного `loadPortfolio` — `tradingStatusFlowManager.setInterest(TradingStatusConsumer.PORTFOLIO, ids)`;
   - `onCleared` — `clearInterest(PORTFOLIO)` (**не** `stop()`: менеджер общий с Orders, `stop()` убьёт чужой polling);
   - в `checkApiInitialization` ветка `NoneInitialized` — `clearInterest(PORTFOLIO)` вместо `tradingStatuses = emptyMap()`.
4. `PortfolioUiState`: удалить `tradingStatuses`; `PortfolioScreen:111` — `val tradingStatuses by viewModel.tradingStatuses.collectAsState()`.
5. Гейт на CI.

**Тесты:** `TradingStatusFlowManagerTest` — `setInterest` стартует и грузит сразу; два потребителя → union ids; `clearInterest` одного не останавливает polling; `clearInterest` последнего → polling остановлен; периодичность на коротком инжектированном интервале 50 мс (`advanceTimeBy` невозможен: `UpdateTradingStatusesUseCase` final → только mockk, а `runTest` + `limitedParallelism(1)` + mockk даёт deadlock); batch-вызов → один раунд fetch; broker-группировка (2 брокера → 2 вызова usecase). `PortfolioScreenBehaviourTest` не ломать: в тесте реальный граф Hilt, новых биндингов не требуется (`TradingStatusRepository` биндится в `RepositoryBindingModule:82`, менеджер — `@Inject`).

**Референсы:** `PositionFlowManager.kt:66-105` (scope, `startLock`, `limitedParallelism`), `PriceFlowManager.kt` + `PriceConsumer.kt` (interest-паттерн), `TradingStatusRepository.kt`, `UpdateTradingStatusesUseCase.kt:37-60`, `OrdersViewModel.kt:70` (passthrough), `TInvestTradingService.kt:52-160` (batch primary + singular fallback), live-log C2 (2026-11-15).

**Ограничения:**
- Не трогать `TradingStatusRepositoryImpl` (SSoT остаётся).
- Не трогать `UpdateTradingStatusesUseCase` (используется Search и Orders).
- Не менять UI (кроме источника `tradingStatuses` — данные, не дизайн).
- Не переносить polling в repository (концептуально неверно).
- Не удалять periodic polling совсем (биржи открываются/закрываются несколько раз в день).
- Не вызывать `stop()` менеджера из VM — менеджер общий (T-41).

**Критерий готовности:**
- 1 полная загрузка на cold start (guard от повторного `loadPortfolio`).
- 0 загрузок на циклах RESUMED (поворот, foreground) — `repeatOnLifecycle` удалён.
- 0 загрузок на повторный вход в экран (back-stack, смена вкладки) — `loadIfNeeded()`.
- `tradingStatuses` в UI — passthrough из `TradingStatusRepository`; поля в `PortfolioUiState` нет.
- Polling 60 с — в `TradingStatusFlowManager`; остановка при пустом объединении интересов.
- `RESOURCE_EXHAUSTED` в live-test 6 — 0.
- Гейт зелёный.

**Оценка:** ~2.5-3 ч (5a ~20-30 мин + 5b ~2-2.5 ч).

**Зависимости:** нет. Блокирует live-test 6.

**Поглощает:** T-40 `PortfolioUiState: passthrough tradingStatuses StateFlow` — реализуется шагами 3-4 PR 5b; отдельный PR не нужен. Секция T-40 вырезана из `ROADMAP-plan.md` (текст — в истории git).

**Связано:** T-3 umbrella sub-5 (поглощается T-9: double-load + guard + payInSandbox); T-8 (закрыт — batch+singular); T-41 (подключение ORDERS к TradingStatusFlowManager); T-42; `docs/rate-limits.md`.

**Открытые вопросы:** нет. DI-модуль для менеджера не нужен: `PositionFlowManager`/`PriceFlowManager` — конкретные `@Singleton @Inject`; `DispatcherModule:26` уже провайдит `CoroutineDispatcher`.

---
<!-- SUBSECTION_END: t-9 -->










<!-- SECTION_END: roadmap-done -->










<!-- FILE_END -->
