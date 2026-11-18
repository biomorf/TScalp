# Structure — SSoT по стилю оформления документации

## 1. Назначение и область

SSoT по стилю оформления `.md`-файлов проекта. `AGENTS/` — **директивы** по применению инструментов (ссылка сюда, без дублирования стиля). Здесь — **стиль**: заголовки, маркеры, разделители, индексы, скрипты.

**Область:** `AGENTS/AGENTS-*.md`, `AGENTS/AGENTS-LESSONS-*.md`, `docs/roadmap/ROADMAP-*.md`, `docs/roadmap/ci-roadmap-*.md`, `docs/roadmap/ROADMAP.md`, `docs/roadmap/ci-roadmap.md`.

**Вне области (сейчас):** `docs/issues/`, `docs/clean-audit/` — структура не подтверждена / не табличная. Отдельные задачи.

## 2. Заголовки

### 2.1 Разделы (`##`)

Формат: `## <title>`. Источник: `project-docs-section-order`, `docs_normalize.py`.

### 2.2 Подразделы (`###`)

**Целевое (базовое, Q43):** `### [TOKEN] <ID> <title>` — для всех.

- AGENTS: `### [AGENTS] <id> <title>` (Q49).
- ROADMAP / ci-roadmap: `### [WIP|PLAN|DEFER|DONE] <ID> <title>`.
- `### [TOKEN] <ID> <title>` — не `### <id> <title>` (устаревшее, миграция — T-44).

### 2.3 TOKEN — значения

- ROADMAP / ci-roadmap: `WIP` / `PLAN` / `DEFER` / `DONE`.
- AGENTS / AGENTS-LESSONS: `[AGENTS]` (единый, заглушка; дифференциация — отдельная задача).
- ISSUES: `OPEN` / `WIP` / `DEFER` / `DONE` — **вне scope**.

### 2.4 ID — формат

- Маркер: kebab-case (`t-39`, `architecture-23`, `r4k`).
- Заголовок ROADMAP: UPPER (`T-39`, `C-5`).
- Заголовок AGENTS: kebab-case (`r4k`, `architecture-23`).

## 3. Маркеры

### 3.1 `FILE_BEGIN` / `FILE_END`

HTML-комментарий. Обёртка корневых `AGENTS.md` / `AGENTS-LESSONS.md`. Источник: `AGENTS-report.md`, `AGENTS-preamble.md`.

### 3.2 `SECTION_BEGIN` / `SECTION_END`

Формат: `<!-- SECTION_BEGIN: <id> -->` / `<!-- SECTION_END: <id> -->`. ID — kebab-case (`integrity`, `docs`, `roadmap-plan`). Источник: `AGENTS-integrity.md`, `docs_normalize.py`.

### 3.3 `SUBSECTION_BEGIN` / `SUBSECTION_END`

Формат: `<!-- SUBSECTION_BEGIN: <id> -->` / `<!-- SUBSECTION_END: <id> -->`. ID — kebab-case, **уникален** (Q44). **Без заголовка** — заголовок только в `###`. Источник: `docs_normalize.py`.

### 3.4 Вложенность

`FILE` > `SECTION` > `SUBSECTION`. `SUBSECTION` — только внутри `SECTION`.

## 4. Разделители

### 4.1 `___` (между `###`)

Источник: `docs_normalize.py` (канон). **Q45: 10 пустых строк** между блоками (единообразно).

### 4.2 `---` (между `##`)

Источник: `project-docs-section-order` (таблица переходов, **переносится** сюда, Q47/Q50).

### 4.3 Пустые строки

**10** пустых между `SUBSECTION`-блоками (AGENTS **и** ROADMAP/ci-roadmap — унификация, Q45). **4** перед `SECTION_END`.

### 4.4 Таблица переходов (перенесено из `project-docs-section-order`, Q47/Q50)

| Переход | Raw-разделитель |
|---|---|
| `## X` → `### Y` | `\n\n\n\n\n` (4 пустые, без маркера) |
| `### X` → `### Y` | `\n\n\n\n___\n\n\n\n` (4 пустые, `___`, 4 пустые) |
| `### X` → `## Y` | `\n\n\n\n___\n\n\n\n---\n\n\n\n` (4 пустые, `___`, 4 пустые, `---`, 4 пустые) |
| `## X` → `## Y` | `\n\n\n\n---\n\n\n\n` (4 пустые, `---`, 4 пустые) |
| Конец файла | `\n` (1 `\n`, без маркера) |

**Примечание:** Q45 — **10** пустых между `SUBSECTION`-блоками (AGENTS/ROADMAP). Таблица выше — **историческая** (4 пустые, `project-docs-section-order`). **Целевое:** 10 везде. **Миграция** — `docs_normalize.py --all` (после Q67).

## 5. Индексы

### 5.1 AGENTS / AGENTS-LESSONS

Формат: `| ID | Заголовок | Файл | Якорь |`. Источник: `AGENTS.md` (индекс), `docs_rebuild_index.py`. Обновление: `docs_rebuild_index.py --mode agents` / `--mode agents-lessons`.

### 5.2 ROADMAP / ci-roadmap

Формат: `| TOKEN | ID | Title | File |` (подтверждено). Источник: `ROADMAP.md`, `ci-roadmap.md`, `docs_rebuild_index.py`. Обновление: `docs_rebuild_index.py --mode roadmap` / `--mode ci-roadmap`.

### 5.3 ISSUES (вне scope)

Формат: `| TOKEN | ID | Title | Component | File |` (5 колонок). **Не включаем** сейчас.

### 5.4 clean-audit (вне scope)

Формат: список файлов (не таблица). **Не включаем** сейчас.

### 5.5 Обновление

`docs_rebuild_index.py --mode <mode>` — целевое. Сейчас: без `--mode`, только `AGENTS/` (root) — **проблема**, решается в T-44.

## 6. Скрипты

### 6.1 `docs_add.py`

- **Сейчас:** `--file`, `--id`, `--title`, `--body-file`, `--after`/`--at-end`, `--apply`.
- **Целевое:** `--mode` (agents / roadmap / ci-roadmap), `--token` (для roadmap/ci-roadmap), `--id`, `--title` (без `[TOKEN]`), `--report` (вывод нормализации), `--expect N` (явное число вхождений, сверка с `grep` из Python). **Локальная** нормализация (только правка) — Q60/Q67.

### 6.2 `docs_cut.py`

- **Сейчас:** `--file`, `--id`, `--force`, `--apply`; `tmp` с `r4k`-fallback.
- **Целевое:** без изменений (или `--mode` для валидации).

### 6.3 `docs_normalize.py`

- **Сейчас:** `--apply` / `--check` / `--dry-run` / `--file` / `--all` / `--check-index` / `--root` / `--index-paths`.
- **Целевое:** `--all` — **только** явные имена файлов (Q67: «избежим паники ассистента»). `--file` — один файл. По умолчанию — **без** нормализации (только check).

### 6.4 `docs_rebuild_index.py`

- **Сейчас:** `--dry-run` / `--apply` / `--root` / `--index-paths`.
- **Целевое:** `--mode` (agents / agents-lessons / roadmap / ci-roadmap), по умолчанию — все 4. `HEADING_RE` — с TOKEN (для roadmap/ci-roadmap).

## 7. Диаграмма вложенности

```mermaid
flowchart TD
    F[FILE_BEGIN/END] --> S[SECTION_BEGIN/END ##]
    S --> SS[SUBSECTION_BEGIN/END ###]
    SS --> H[### [TOKEN] ID title]
    SS --> B[body]
```

Подпись: иерархия маркеров — FILE > SECTION > SUBSECTION > ### heading + body.

## 8. Связанные документы

- `AGENTS/AGENTS-integrity.md` — маркеры (ссылка).
- `AGENTS/AGENTS-project-docs.md` → `project-docs-section-order` — разделители (ссылка, таблица перенесена сюда).
- `AGENTS/AGENTS-style-docs.md` → `r4j` — скрипты (ссылка).
- `AGENTS/AGENTS-report.md` — `FILE_BEGIN/END` (ссылка).

## 9. Открытые вопросы (сводка)

- Q44: маркер — `id` достаточно (принято).
- Q45: 10 пустых везде (принято, миграция — `docs_normalize.py --all`).
- Q49: TOKEN для AGENTS — `[AGENTS]` (принято; дифференциация — отдельная задача).
- Q39: `docs_add.py` — вариант 1/2/3 (Q60: A+B+C — принято).
- Q67: локальная нормализация (принято).
