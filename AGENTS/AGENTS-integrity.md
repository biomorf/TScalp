<!-- FILE_BEGIN -->
# Целостность и маркеры

Форматы маркеров (`FILE`, `SECTION`, `SUBSECTION`), вложенность,
уникальность и якоря описаны в `docs/docs-structure.md` (§3). Здесь —
только роль маркеров в отчётах о прочтении.










<!-- SECTION_BEGIN: integrity -->
## Роль маркеров

`FILE_BEGIN` / `FILE_END` задают границы вырезки/вставки и нужны для
верификации прочтения. `SECTION` / `SUBSECTION` — границы разделов и
подсекций. Отчёт о прочтении оглашает, какие маркеры видны, чтобы
отличить «прочитал всё» от «прочитал первые 500 строк».










<!-- SUBSECTION_BEGIN: integrity-report -->
<a id="integrity-report"></a>
### [AGENTS] integrity-report Формат отчёта
См. `AGENTS/AGENTS-report.md` — единый формат отчёта о прочтении
(раздел «Обязанность оглашения при получении»).

---
<!-- SUBSECTION_END: integrity-report -->










<!-- SECTION_END: integrity -->










<!-- FILE_END -->
