<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: docs -->
## Обязательные документы

В начале сессии ассистент **настойчиво требует** у пользователя
два файла:

1. **`AGENTS.md`** — этот документ.
2. **`AGENTS-LESSONS.md`** — уроки из инцидентов проекта.

Если пользователь не передал `AGENTS-LESSONS.md` в первом
сообщении — ассистент **явно спрашивает** файл до начала любой
работы: «Передай AGENTS-LESSONS.md». Без него работа не
начинается.

**Причина:** в `AGENTS-LESSONS.md` — уроки из инцидентов, которых
нет в `AGENTS.md`. Игнорирование уроков = повторение инцидентов
(H21b, H23 — 4 волны CI-падений на нарушениях, которые уже были
в списке).

---










<!-- SUBSECTION_BEGIN: docs-report -->
<a id="docs-report"></a>
### [AGENTS] docs-report Формат отчёта
См. `AGENTS/AGENTS-report.md` — единый формат отчёта о прочтении.

---
<!-- SUBSECTION_END: docs-report -->










<!-- SUBSECTION_BEGIN: docs-gate -->
<a id="docs-gate"></a>
### [AGENTS] docs-gate Область гейта
**Область гейта.** Гейт покрывает **все** `AGENTS/AGENTS-*.md`, кроме двух
индексов — `AGENTS/AGENTS.md` и `AGENTS/AGENTS-LESSONS.md` (собираются
`docs_rebuild_index.py`, читаются по необходимости). Новый директивный файл
обязан быть добавлен в перечень ниже в том же коммите; иначе `docs_lint.py`
падает `FAIL: gate missing <file>`.

**Перечень (по порядку чтения):**

1. `AGENTS-preamble.md`, `AGENTS-comm.md`, `AGENTS-forbidden.md` — рамка,
   коммуникация, запреты.
2. `AGENTS-style-core.md`, `AGENTS-style-docs.md`, `AGENTS-style-git.md` —
   правила 1–5 (`2c`, `2e`, `3a`/`3b`, `4a`–`4k`, `5`, `5a`–`5g`).
3. `AGENTS-code.md`, `AGENTS-project-docs.md`, `AGENTS-evolution.md`,
   `AGENTS-integrity.md` — код, документы, эволюция директив, маркеры.
4. `AGENTS-docs.md` (этот файл), `AGENTS-report.md` — что читать и как
   отчитываться.
5. Набор LESSONS — по `AGENTS-LESSONS-docs.md`.

**Отчёт** — по формату `AGENTS/AGENTS-report.md`; шаблон обязан покрывать
каждый файл из перечня.

---
<!-- SUBSECTION_END: docs-gate -->










<!-- SECTION_END: docs -->










<!-- FILE_END -->
