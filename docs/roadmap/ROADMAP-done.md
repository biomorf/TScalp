<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: roadmap-done -->
## Сделано (ожидает архивации)










<!-- SUBSECTION_BEGIN: t-13 -->
___
### [DONE] T-13 Compose UI: test infra + 3 экрана — ✅ done (U0–U4)
См. `ROADMAP-ARCHIVE.md` → `### ✅ Compose UI: test infra + 3 экрана (U0–U4)`.

___
<!-- SUBSECTION_END: t-13 -->










<!-- SUBSECTION_BEGIN: t-1 -->
___
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

___
<!-- SUBSECTION_END: t-1 -->










<!-- SECTION_END: roadmap-done -->
<!-- FILE_END -->
