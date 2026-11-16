<!-- AGENTS_SECTION_BEGIN: integrity -->
## Маркеры целостности (инициализационное)

Каждый `AGENTS.md` / `AGENTS-LESSONS.md` обёрнут парой
`FILE_BEGIN` / `FILE_END`. Каждый `##`-раздел (и, для §Стиль работы,
каждое `### N`-правило) — парой `SECTION_BEGIN: <id>` /
`SECTION_END: <id>` с коротким ASCII-id.

**Формат маркеров** — HTML-комментарии, не видны в рендере
(шаблон схематичный, реальные маркеры — в файле, не в этом блоке):

    FILE_BEGIN
    ...
    SECTION_BEGIN: docs
    ## Обязательные документы
    ...
    SECTION_END: docs
    ...
    FILE_END

Полный список `<id>` — в первом ответе ассистента.

**ID секций AGENTS.md:**
`integrity` (§Маркеры целостности), `docs` (§Обязательные документы),
`style` (§Стиль работы) + подправила `r1`–`r5g`, `r5f-freeze` (§Ожидание merge), `code` (§Работа с кодом),
`forbidden` (§Запрещено), `project-docs` (§Документы проекта),
`evolution` (§Эволюция директив), `comm` (§Коммуникация).

**ID секций AGENTS-LESSONS.md:**
`template`, `structure`, `scripts`, `kotlin`, `diagnostics`,
`clean-audit`, `ui`, `ci`, `estimates`, `process`, `application`.

<!-- SUBSECTION_BEGIN: integrity-report -->
___
### integrity-report Формат отчёта

См. `AGENTS/AGENTS-report.md` — единый формат отчёта о прочтении
(раздел «Обязанность оглашения при получении»).

<!-- SUBSECTION_END: integrity-report -->
<!-- AGENTS_SECTION_END: integrity -->