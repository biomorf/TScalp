<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: clean-audit -->










<!-- SUBSECTION_BEGIN: l-clean-audit-87 -->
<a id="l-clean-audit-87"></a>
### [AGENTS-LESSONS] l-clean-audit-87 Подсчёт функций класса через grep занижен при multi-prefix
**Правило:** считать функции класса — `grep -cE "^        ((private|internal|suspend) )+fun |^        fun "` (extended pattern, `+` для повторов префиксов), или Python `re.findall(r"^        (?:private |internal |suspend )*fun ", text, re.MULTILINE)`. **Не** `grep "^\s*(private \|internal \|suspend )?fun"` — `?` матчит **один** префикс, `private suspend fun` не попадает.

**Инцидент:** 2026-11-07, M013 step 2b (`99ecd6a`). После удаления `seedSheet` в `PortfolioViewModel` — assert `functions in class == 11` упал с `got 10`. Причина двойная:
1. `grep -cE "^        (private |internal |suspend )?fun"` — `updateTradingStatuses` (`private suspend fun`, строка 210) не матчится (`?` = один префикс, тут два).
2. Мой `assert == 11` — арифметическая ошибка от устаревшего KDoc «12 функций»; фактически 10 (9 + `updateTradingStatuses`).

Файл корректен (`@Suppress("TooManyFunctions")` снят, 10 < 11 — detekt не ругается). Assert ложный, лишний round-trip.

**Проверка:** `grep -nE "^        ((private|internal|suspend) )+fun |^        fun " file.kt | wc -l`. Дополнительно — Python с `(?:...)*`, сверить. Если KDoc класса говорит «N функций» — **проверить KDoc по факту**, не доверять (KDoc устаревает). Связано: урок 61 (двусторонняя проверка), 76 (unused import по count).

## Clean-audit

---
<!-- SUBSECTION_END: l-clean-audit-87 -->










<!-- SUBSECTION_BEGIN: l-clean-audit-20 -->
<a id="l-clean-audit-20"></a>
### [AGENTS-LESSONS] l-clean-audit-20 Инвентаризация не пропускает «уже известное»
**Правило:** каждая находка (нарушение Clean, presentation-текст в нижнем слое, god-класс, `@Suppress` как маркер долга, swallowed exception, dead code, дублирование) заводится отдельной записью в `docs/clean-audit.md`. Существование задачи в ROADMAP/ISSUES не повод не заводить запись — наоборот, ссылка на задачу в поле «Ссылка на задачу:».

**Инцидент:** 2026-10-27, по ходу clean-audit я 13 раз сказал «уже в ISSUES/ROADMAP, не заводим новую запись». Пользователь: «Э! Отмена всем ранее бывшим в сессии "не заводим новые"... Нельзя на этом основании делать дыру в инвентаризации!». Backfill Н58-Н70 — 13 записей. Правило в AGENTS 2c добавлено (коммит `c9468c3`).

**Проверка:** `grep -n "не заводим\|уже известно\|не новая находка" docs/clean-audit.md` — пусто. `grep -c "^### Н" docs/clean-audit.md` — число должно совпадать с ожидаемым.

---
<!-- SUBSECTION_END: l-clean-audit-20 -->










<!-- SUBSECTION_BEGIN: l-clean-audit-66 -->
<a id="l-clean-audit-66"></a>
### [AGENTS-LESSONS] l-clean-audit-66 Перенос в «Исправленные (L<N>)» — ### ✅ и актуальная ссылка на задачу
**Правило:** при переносе записи из открытых в `### Исправленные (L<N>)` менять **три** вещи, не одну: (1) `**Статус:** открыто.` → `исправлено (дата, ветка/PR)`; (2) **заголовок** `### Н<xx>.` → `### ✅ Н<xx>.` (маркер «исправлено» в шапке, как у всех записей блока); (3) `**Ссылка на задачу:**` — на актуальное состояние (закрытая задача в ROADMAP, PR pending/merged), не «запланировано». Поля `**Серьёзность:**`, `**Описание:**`, `**Clean-альтернатива:**`, `**Что сделано:**` — сохранять дословно (5g.4).

**Инцидент:** 2026-10-31, 8c (Н63, перенос в Исправленные L6). Перенёс секцию с `**Статус:** исправлено`, но (1) **заголовок** остался `### Н63.` без ✅, (2) `**Ссылка на задачу:**` остался «запланировано (отдельная сессия)». Fix-коммитом (`c6e1baf`) догонял: `### ✅ Н63.` + ссылка на `### ✅ Пакет C ... (PR pending)`. Pre-commit verify проверял только, что запись «есть в блоке», не «все ### в блоке — с ✅».

**Проверка:** после переноса — `awk` по блоку от `### Исправленные (L<N>)` до следующего `## `: все `### ` должны начинаться с `### ✅ ` (иначе — `ERROR: <line>`). Плюс `grep -nE "^\*\*Ссылка на задачу:\*\*" | grep -v "PR pending|PR #|dev"` — пусто (нет «запланировано»).

---
<!-- SUBSECTION_END: l-clean-audit-66 -->










<!-- SECTION_END: clean-audit -->










<!-- FILE_END -->
