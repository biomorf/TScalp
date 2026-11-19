# Structure — SSoT по стилю оформления документации

## 1. Назначение и область

SSoT по стилю оформления `.md`-файлов проекта. `AGENTS/` — **директивы** по применению инструментов (ссылка сюда, без дублирования стиля). Здесь — **стиль**: заголовки, маркеры, разделители, индексы, скрипты.

**Область:** `AGENTS/AGENTS-*.md`, `AGENTS/AGENTS-LESSONS-*.md`, `docs/roadmap/ROADMAP-*.md`, `docs/roadmap/ci-roadmap-*.md`, `docs/roadmap/ROADMAP.md`, `docs/roadmap/ci-roadmap.md`, все `.md` (FILE_BEGIN/END).

**Вне области (сейчас):** `docs/issues/`, `docs/clean-audit/` — структура не подтверждена / не табличная. Отдельные задачи.

**Статус:** ✅ (SSoT актуален; несоответствия Q141-B — исправлены).

## 2. Заголовки

### 2.1 Разделы (`##`)

Формат: `## <title>`. Источник: `project-docs-section-order`, `docs_normalize.py`. **Статус:** ✅.

### 2.2 Подразделы (`###`)

**Целевое (базовое, Q43):** `### [TOKEN] <ID> <title>` — для всех.

- AGENTS: `### [AGENTS] <id> <title>` (Q49).
- AGENTS-LESSONS: `### [AGENTS-LESSONS] <id> <title>` (Q49, Q142).
- ROADMAP / ci-roadmap: `### [WIP|PLAN|DEFER|DONE] <ID> <title>`.
- `### [TOKEN] <ID> <title>` — не `### <id> <title>` (устаревшее, миграция — T-44).

**Статус:** ⏳ (миграция AGENTS → `[AGENTS]`/`[AGENTS-LESSONS]` — шаг 5).

### 2.3 TOKEN — значения

- ROADMAP / ci-roadmap: `WIP` / `PLAN` / `DEFER` / `DONE`.
- AGENTS: `[AGENTS]` (единый, Q49).
- AGENTS-LESSONS: `[AGENTS-LESSONS]` (Q142).
- ISSUES: `OPEN` / `WIP` / `DEFER` / `DONE` — **вне scope**.

**Статус:** ✅ (определены).

### 2.4 ID — формат

- Маркер: kebab-case (`t-39`, `architecture-23`, `r4k`).
- Заголовок ROADMAP: UPPER (`T-39`, `C-5`).
- Заголовок AGENTS: kebab-case (`r4k`, `architecture-23`).

**Статус:** ✅.

## 3. Маркеры

### 3.1 `FILE_BEGIN` / `FILE_END`

HTML-комментарий.
**Формат:** `<!-- FILE_BEGIN -->` / `<!-- FILE_END -->` (без `id`; Q96 — все `.md`, первая/последняя строка, Q97; Q102 — до `SECTION_BEGIN`).

**Статус:** ✅ (в `docs_normalize.py`, `9d387464` + Q117).

### 3.2 `SECTION_BEGIN` / `SECTION_END`

Формат: `<!-- SECTION_BEGIN: <id> -->` / `<!-- SECTION_END: <id> -->`. ID — kebab-case (`integrity`, `docs`, `roadmap-plan`). Источник: `AGENTS-integrity.md`, `docs_normalize.py`. **Статус:** ✅ (assert в `docs_normalize.py`, Q89).

### 3.3 `SUBSECTION_BEGIN` / `SUBSECTION_END`

Формат: `<!-- SUBSECTION_BEGIN: <id> -->` / `<!-- SUBSECTION_END: <id> -->`. ID — kebab-case, **уникален** (Q44). **Без заголовка** — заголовок только в `###`. Источник: `docs_normalize.py`. **Статус:** ✅.

### 3.4 Вложенность

`FILE` > `SECTION` > `SUBSECTION`. `SUBSECTION` — только внутри `SECTION`. **Статус:** ✅ (assert, Q89).

## 4. Разделители

### 4.1 `___` (между `###`)

Источник: `docs_normalize.py` (канон). **Q45: 10 пустых строк** между блоками (единообразно). **Статус:** ✅.

### 4.2 `---` (между `##`)

Источник: `project-docs-section-order` (таблица переходов, перенесена сюда, Q47/Q50). **Статус:** ✅.

### 4.3 Пустые строки

**10** пустых **везде** (Q45, расширено): между `SUBSECTION`-блоками, **перед `SECTION_END`**, **в конце файла** (Q88). **Не 4.** **Статус:** ✅ (`docs_normalize.py`).

### 4.4 Таблица переходов (перенесено из `project-docs-section-order`, Q47/Q50; **обновлено Q45/Q88**)

| Переход | Raw-разделитель |
|---|---|
| `## X` → `### Y` | `\n` × 10 (10 пустых, без маркера) |
| `### X` → `### Y` | `\n` × 10 + `___` + `\n` × 10 |
| `### X` → `## Y` | `\n` × 10 + `___` + `\n` × 10 + `---` + `\n` × 10 |
| `## X` → `## Y` | `\n` × 10 + `---` + `\n` × 10 |
| Конец файла | `\n` × 10 (Q88) |

**Статус:** ✅ (правило); ⏳ (миграция существующих файлов — `docs_normalize.py --all`, шаг 5).

## 5. Индексы

### 5.1 AGENTS / AGENTS-LESSONS

Формат: `| ID | Заголовок | Файл | Якорь |`. Источник: `AGENTS.md` (индекс), `docs_rebuild_index.py`. Обновление: `docs_rebuild_index.py --mode agents` / `--mode agents-lessons`. **Статус:** ⏳ (4b').

### 5.2 ROADMAP / ci-roadmap

Формат: `| TOKEN | ID | Title | File |` (подтверждено). Источник: `ROADMAP.md`, `ci-roadmap.md`, `docs_rebuild_index.py`. Обновление: `docs_rebuild_index.py --mode roadmap` / `--mode ci-roadmap`. **Статус:** ⏳ (4b').

### 5.3 ISSUES (вне scope)

Формат: `| TOKEN | ID | Title | Component | File |` (5 колонок). **Не включаем** сейчас.

### 5.4 clean-audit (вне scope)

Формат: список файлов (не таблица). **Не включаем** сейчас.

### 5.5 Обновление

`docs_rebuild_index.py --mode <mode>` + `--index-paths` (override, Q83 A+D). **Статус:** ⏳ (4b').

## 6. Скрипты

### 6.1 `docs_add.py`

- **Сейчас:** `--file`, `--id`, `--title`, `--body-file`, `--after`/`--at-end`, `--apply`.
- **Целевое:** `--mode` (agents / roadmap / ci-roadmap), `--token`, `--id`, `--title` (без `[TOKEN]`), `--report` (Q60/Q67), `--expect N` (сверка с `grep` из Python, Q60 A+B+C), **локальная нормализация** (только правка, не весь файл — Q67).
- **Статус:** ⏳ (4c').

### 6.2 `docs_cut.py`

- **Сейчас:** `--file`, `--id`, `--force`, `--apply`; `tmp` с `r4k`-fallback.
- **Целевое:** без изменений (или `--mode` для валидации).
- **Статус:** ⏳ (4d, опционально).

### 6.3 `docs_normalize.py`

- **Сейчас:** `--file` / `--all` (явные имена, Q67) / `--apply` / `--check` / `--dry-run`. **Удалено:** `--check-index`, `--index-paths`, `--root`, `rglob` (Q83, Q80). **`--help`** — исчерпывающий (Q93/Q94). `FILE_BEGIN/END` (Q96-Q98), 10 пустых везде (Q45), idempotent (Q131).
- **Статус:** ✅ (`9d387464` + Q117).

### 6.4 `docs_rebuild_index.py`

- **Сейчас:** `--dry-run` / `--apply` / `--root` / `--index-paths`.
- **Целевое:** `--mode` (agents / agents-lessons / roadmap / ci-roadmap), по умолчанию — все 4; `--index-paths` — override (Q83 A+D). `HEADING_RE` — с TOKEN (для roadmap/ci-roadmap).
- **Статус:** ⏳ (4b').

## 7. Диаграмма вложенности

```mermaid
flowchart TD
    F[FILE_BEGIN/END] --> S[SECTION_BEGIN/END ##]
    S --> SS[SUBSECTION_BEGIN/END ###]
    SS --> H[### [TOKEN] ID title]
    SS --> B[body]
```

Подпись: иерархия маркеров — FILE > SECTION > SUBSECTION > ### heading + body. **Статус:** ✅.

## 8. Связанные документы

- `AGENTS/AGENTS-integrity.md` — маркеры (ссылка).
- `AGENTS/AGENTS-project-docs.md` → `project-docs-section-order` — разделители (ссылка, таблица перенесена сюда).
- `AGENTS/AGENTS-style-docs.md` → `r4j` — скрипты (ссылка).
- `AGENTS/AGENTS-report.md` — `FILE_BEGIN/END` (ссылка).

## 9. Открытые вопросы (сводка)

**Закрыты:** Q43–Q143 (сессия 2026-11-18/19). **Открытых нет.**

- Q44: маркер — `id` достаточно ✅
- Q45: 10 пустых везде (включая `SECTION_END` и конец файла) ✅
- Q49: TOKEN AGENTS — `[AGENTS]`, LESSONS — `[AGENTS-LESSONS]` (Q142) ✅
- Q83: `docs_normalize.py` — `--check-index` удалён; `docs_rebuild_index.py` — A+D ⏳
- Q67: локальная нормализация ⏳
- Q93/Q94: `--help` исчерпывающий ✅
- Q96–Q98/Q102: `FILE_BEGIN/END` (без id, первая/последняя) ✅
- Q88: 10 пустых в конце файла ✅
