# AGENTS.md — указатель

Полный индекс — `AGENTS/AGENTS.md`. Здесь — только гейт обязательного
чтения и краткая карта.

## Обязательное чтение (гейт запуска)

До любой работы — по порядку:

1. `AGENTS/AGENTS-preamble.md` — обязательства и рамка директив.
2. `AGENTS/AGENTS-docs.md` — обязательные файлы проекта и формат отчёта.
3. `AGENTS/AGENTS-LESSONS-preamble.md` — обязательства и рамка уроков.
4. `AGENTS/AGENTS-LESSONS-docs.md` — обязательные файлы LESSONS и формат отчёта.
`AGENTS/AGENTS-report.md` — единый формат отчёта о прочтении.
5. Выдать отчёт по формату из `AGENTS/AGENTS-docs.md` и
   `AGENTS/AGENTS-LESSONS-docs.md`.

Без шагов 1–5 работа не начинается.

## Справочник (по мере необходимости)

| ID | Заголовок | Файл | Якорь |
|---|---|---|---|
| code-project | Проект | AGENTS/AGENTS-code.md | #code-project |
| code-workflows | Рабочие сценарии: mobile (proot) и desktop (Bluefin) | AGENTS/AGENTS-code.md | #code-workflows |
| code-instrument-ids | Идентификаторы инструментов | AGENTS/AGENTS-code.md | #code-instrument-ids |
| code-before-edits | Перед правками | AGENTS/AGENTS-code.md | #code-before-edits |
| code-after-edits | После правок | AGENTS/AGENTS-code.md | #code-after-edits |
| code-unit-mockk-suspend | Unit-тесты: mockk для suspend-функций | AGENTS/AGENTS-code.md | #code-unit-mockk-suspend |
| code-self-contained-scripts | Самодостаточность инфраструктурных скриптов | AGENTS/AGENTS-code.md | #code-self-contained-scripts |
| code-git-workflow-origin | Git workflow: синхронизация с origin | AGENTS/AGENTS-code.md | #code-git-workflow-origin |
| code-git-workflow-pr | Git workflow: ветки и PR | AGENTS/AGENTS-code.md | #code-git-workflow-pr |
| docs-report | Формат отчёта | AGENTS/AGENTS-docs.md | #docs-report |
| evolution-priority | Правки директив приоритетнее любой деятельности | AGENTS/AGENTS-evolution.md | #evolution-priority |
| evolution-workflow | Workflow-находки фиксируются сразу | AGENTS/AGENTS-evolution.md | #evolution-workflow |
| integrity-report | Формат отчёта | AGENTS/AGENTS-integrity.md | #integrity-report |
| project-docs-all-planned | Всё планируемое — в документах | AGENTS/AGENTS-project-docs.md | #project-docs-all-planned |
| project-docs-diagrams | Диаграммы в документации | AGENTS/AGENTS-project-docs.md | #project-docs-diagrams |
| project-docs-mobile | Пометка о мобильной работе | AGENTS/AGENTS-project-docs.md | #project-docs-mobile |
| project-docs-section-order | Порядок секций в плановых документах | AGENTS/AGENTS-project-docs.md | #project-docs-section-order |
| r3b | Дефолты не считаются утверждёнными | AGENTS/AGENTS-style-core.md | #r3b |
| r3a | Явные дефолты при неполном ответе | AGENTS/AGENTS-style-core.md | #r3a |
| r3 | Не выдумывать | AGENTS/AGENTS-style-core.md | #r3 |
| r2e | Триада обязательных требований к решениям и планам | AGENTS/AGENTS-style-core.md | #r2e |
| r2d | Временные решения явно маркируются и закрываются задачей | AGENTS/AGENTS-style-core.md | #r2d |
| r2c | Проверка решений на соответствие Clean-архитектуре | AGENTS/AGENTS-style-core.md | #r2c |
| r2b | Организационные практики — по индустрии | AGENTS/AGENTS-style-core.md | #r2b |
| r2a | Актуальность решений, не традиция | AGENTS/AGENTS-style-core.md | #r2a |
| r2 | Чистота кода — приоритет | AGENTS/AGENTS-style-core.md | #r2 |
| r1 | Не менять UI без санкции | AGENTS/AGENTS-style-core.md | #r1 |
| r4k | tmp/ — primary /tmp, fallback $HOME/tmp | AGENTS/AGENTS-style-docs.md | #r4k |
| r5c | Структура ответа | AGENTS/AGENTS-style-docs.md | #r5c |
| r4j | Скрипты для AGENTS/ | AGENTS/AGENTS-style-docs.md | #r4j |
| r4i | Шаблон bash-скриптов | AGENTS/AGENTS-style-docs.md | #r4i |
| r4h | Проверка правок на соответствие перед коммитом | AGENTS/AGENTS-style-docs.md | #r4h |
| r4e | Безопасность shell-команд в ответах | AGENTS/AGENTS-style-docs.md | #r4e |
| r4d | Идемпотентность операций | AGENTS/AGENTS-style-docs.md | #r4d |
| r4c | Механические правки — скриптом | AGENTS/AGENTS-style-docs.md | #r4c |
| r4b | Один шаг за раз | AGENTS/AGENTS-style-docs.md | #r4b |
| r4a | Формат показа правок | AGENTS/AGENTS-style-docs.md | #r4a |
| r4 | Маленькие шаги с проверкой | AGENTS/AGENTS-style-docs.md | #r4 |
| r5g | Работа с задачами в ROADMAP | AGENTS/AGENTS-style-git.md | #r5g |
| r5f-freeze | Ожидание merge: заморозка feature-ветки и dev | AGENTS/AGENTS-style-git.md | #r5f-freeze |
| r5f | Post-PR checklist | AGENTS/AGENTS-style-git.md | #r5f |
| r5e | Самодостаточность планов | AGENTS/AGENTS-style-git.md | #r5e |
| r5d | Жизненный цикл задачи | AGENTS/AGENTS-style-git.md | #r5d |
| r5b | Правки в docs — сразу с коммитом | AGENTS/AGENTS-style-git.md | #r5b |
| r5a | Порядок коммита: локально vs CI | AGENTS/AGENTS-style-git.md | #r5a |
| r5 | Коммит после каждой задачи | AGENTS/AGENTS-style-git.md | #r5 |
| r4g | Git-команды без дефолтов | AGENTS/AGENTS-style-git.md | #r4g |
| r4f | Дорогие операции: правки → гейт → коммит → push | AGENTS/AGENTS-style-git.md | #r4f |
