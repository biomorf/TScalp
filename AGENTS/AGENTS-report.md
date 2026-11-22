<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: report -->
## Формат отчёта о прочтении

Единый формат отчёта о прочтении файлов директив. На него
ссылаются `AGENTS/AGENTS-docs.md` и `AGENTS/AGENTS-LESSONS-docs.md`.

### Обязанность оглашения при получении

Первый ответ ассистента после получения файлов **обязан**
начаться с блока-оглашения:

    **Маркеры прочитанного** (перечень — `AGENTS/AGENTS-docs.md` → `docs-gate`):

    Директивные файлы:
    - AGENTS/AGENTS-preamble.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-comm.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-forbidden.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-style-core.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-style-docs.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-style-git.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-code.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-project-docs.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-evolution.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-integrity.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-docs.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-report.md: FILE_BEGIN / FILE_END

    Файлы LESSONS (перечень — `AGENTS/AGENTS-LESSONS-docs.md`):
    - AGENTS/AGENTS-LESSONS-preamble.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-LESSONS-docs.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-LESSONS-scripts.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-LESSONS-kotlin.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-LESSONS-diagnostics.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-LESSONS-clean-audit.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-LESSONS-ui.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-LESSONS-ci.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-LESSONS-estimates.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-LESSONS-process.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-LESSONS-application.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-LESSONS-structure.md: FILE_BEGIN / FILE_END
    - AGENTS/AGENTS-LESSONS-template.md: FILE_BEGIN / FILE_END

    Для файлов LESSONS дополнительно:
    - SECTION_END (видел): <исчерпывающий список>
    - SECTION_BEGIN без SECTION_END: <id> (обрыв внутри)

`✓` — маркер виден в полученном тексте; `✗` — не виден.
`SECTION_END` — только фактически видимые. `SECTION_BEGIN
без SECTION_END` — точка обрыва.

### Если `FILE_END` = `✗`

Ассистент **не начинает** работу. Говорит «файл получен
частично, обрыв в <id>», ждёт оставшийся фрагмент от
пользователя. Работа стартует только после того, как все
`FILE_END` = `✓`.

### Если `FILE_END` = `✓` для всех файлов

Ассистент продолжает обычным порядком. Оглашение в первом
ответе остаётся — для контроля пользователем.

### Причина

Без явного оглашения ассистент не отличает «прочитал всё» от
«прочитал первые 500 строк». Оглашение — единственная точка,
где пользователь может увидеть, что контекст неполный, до
того как ассистент начнёт на нём работать.










<!-- SECTION_END: report -->










<!-- FILE_END -->
