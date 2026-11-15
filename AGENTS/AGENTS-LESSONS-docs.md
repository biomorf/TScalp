<!-- LESSONS_SECTION_BEGIN: docs -->
## Обязательные файлы LESSONS

Документ описывает обязательные файлы набора `AGENTS-LESSONS-*.md`
и формат отчёта по ним. Читается вместе с `AGENTS/AGENTS-preamble.md`
и `AGENTS/AGENTS-LESSONS-preamble.md` в начале каждой сессии.

### Обязательные файлы

В начале каждой сессии ассистент **настойчиво требует** и читает:

- **`AGENTS-LESSONS-scripts.md`** — уроки по скриптам и правкам
  (grep, heredoc, идемпотентность, assert'ы, fence).
- **`AGENTS-LESSONS-kotlin.md`** — уроки по Kotlin / Compose
  (smart cast, data class, Locale, Currency, Hilt-test infra).
- **`AGENTS-LESSONS-diagnostics.md`** — уроки по диагностике
  (KDoc vs код, слои, `replay`, dataStore, ktlint multiline).
- **`AGENTS-LESSONS-clean-audit.md`** — уроки по clean-audit
  (инвентаризация, перенос в «Исправленные», `✅`).
- **`AGENTS-LESSONS-ui.md`** — уроки по UI и дизайну
  (дизайн только по явному указанию).
- **`AGENTS-LESSONS-ci.md`** — уроки по CI
  (AGP↔Gradle↔Hilt, ktlint multiline, paths-ignore).
- **`AGENTS-LESSONS-estimates.md`** — уроки по оценкам
  (scriptable-задачи — 5–10× меньше).
- **`AGENTS-LESSONS-process.md`** — процессные уроки
  (start/feature, stash, reset --hard, revert).
- **`AGENTS-LESSONS-application.md`** — уроки по применению
  (merge origin/dev, 4f, 5f-freeze, test → feature).

### Формат отчёта

См. `AGENTS/AGENTS-report.md` — единый формат отчёта о прочтении.

### Причина

Без оглашения ассистент не отличает «прочитал всё» от «прочитал
первые 500 строк». Оглашение — единственная точка, где
пользователь видит, что контекст неполный, до начала работы.
<!-- LESSONS_SECTION_END: docs -->
