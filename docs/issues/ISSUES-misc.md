<!-- SECTION_BEGIN: issues-misc -->
## Misc










<!-- SUBSECTION_BEGIN: i-3 -->
___
### [OPEN] I-3 Separator между секциями ROADMAP не везде по шаблону
**Статус:** открыт.

**Симптом:** AGENTS.md (раздел «Порядок секций в плановых
документах») требует разделитель `\n\n\n\n---\n` (три пустые
строки, `---`, одна пустая) между секциями плановых документов.
Фактически в `docs/ROADMAP.md` между некоторыми секциями —
`\n\n\n\n` без `---` (например, между концом секции
«Разбивка InvestRepository» и началом `### H21b`).

**Причина:** шаблон separator добавлен в AGENTS.md позже, чем
создавались существующие секции. При вставке новых задач через
скрипты (4c) separator воспроизводится по шаблону только для
новых; старые секции остаются как были.

**Последствия:**
- Markdown-рендер: секции визуально слипаются (нет
  горизонтальной линии-разделителя).
- Скрипты, ожидающие `---` как границу блока (например, при
  извлечении секции), не находят её.
- Неоднородность: часть секций с `---`, часть без.

**Решение:** нормализовать separator во всех секциях
`docs/ROADMAP.md` и `docs/ci-roadmap.md` — привести к шаблону
`\n\n\n\n---\n`. Механически через скрипт (4c), идемпотентно
(4d). Отдельной задачей или в рамках следующей правки этих
файлов.

**Когда вернуться:** при следующей правке ROADMAP / ci-roadmap
с вставкой нескольких секций, или при подключении markdown-
линтера (ROADMAP → «Markdown linter в CI»).

**Примечание (2026-10-27):** обнаружено при вставке задачи
«BrokerApi: ревизия fat-части…». Новая задача вставлена с
`---` (по шаблону AGENTS), соседние секции оставлены как были.

---
<!-- SUBSECTION_END: i-3 -->










<!-- SUBSECTION_BEGIN: i-4 -->
___
### [OPEN] I-4 Trailing whitespace в docs/*.md
**Статус:** открыт.

**Симптом:** в `docs/ROADMAP.md` есть строки с невидимыми
пробелами в конце (`cat -A` показывает `$` после пробелов).
Встречается в статус-строках задач, разделителях, пустых
строках между блоками.

**Причина:** скрипты вставки через heredoc, которые
ассистент применял для правок `.md`, содержали лишние
отступы в шаблонах. Также возможно — копипаст из
markdown-рендера, где trailing spaces не видны.

**Последствия:**
- `assert` в скриптах не совпадает со строкой (пример:
  `**Статус:** в работе.` не равна `**Статус:** в работе.   `).
- ktlint/markdown-линтеры могут ругаться, если мы когда-то
  их подключим для `.md`.
- git diff показывает шум в конце строк.

**Решение:**
1. Одноразовая чистка: `sed -i 's/[[:space:]]*$//' docs/*.md`
   (проверить через `git diff`, что diff только пробелы).
2. В `AGENTS.md` добавить пункт в правило 4d: «Скрипты
   вставки в `.md` не создают trailing spaces. Шаблон в
   heredoc не содержит отступов после последнего
   непробельного символа каждой строки.»
3. По возможности использовать `rstrip()` для каждой строки
   вставляемого блока перед записью.

**Когда вернуться:** при следующей правке любого
`docs/*.md` или при подключении markdown-линтера.

**Примечание (2026-10-26):** правило добавлено в AGENTS 4d
(`rstrip()` каждой строки в скриптах вставки). Одноразовая
чистка `sed -i 's/[[:space:]]*$//' docs/*.md` — pending.

---
<!-- SUBSECTION_END: i-4 -->










<!-- SUBSECTION_BEGIN: i-25 -->
___
### [DONE] I-25 AGENTS.md ссылался на удалённое `InstrumentUi.ttech_uid`
**Статус:** решён.

**Симптом:** в `AGENTS.md` → «Идентификаторы инструментов» был пункт «`InstrumentUi.ttech_uid` — исключение: специфика T-Invest». Поле `ttech_uid` удалено в C-min шаг 1 (`241b8cb`, feature/instrument-cmin) — правило ссылалось на несуществующее поле.

**Причина:** правило добавлено, когда поле ещё было (специфика T-Invest в domain-модели). После C-min `ttech_uid` слит с `tscalpInstrumentId` (оба — `uid` из T-Invest), `ttech_figi` переименован в `figi: String?` (common nullable). Правило не обновлено вместе с кодом.

**Решение:** пункт удалён из `AGENTS.md`. `tscalpInstrumentId` — единственный идентификатор инструмента в domain; `figi` — common nullable, не T-Invest-специфичен.

**Файлы:**
- `AGENTS.md` → «Идентификаторы инструментов» — пункт про `ttech_uid` удалён.
- `domain/models/Instruments.kt` — поля `ttech_uid` / `ttech_figi` заменены на `figi: String?` (C-min шаги 1-2).

**Связано:** `docs/ROADMAP.md` → `### Instrument: C-min → sealed interface (две фазы)`, коммит `241b8cb`.

**Когда вернуться:** при следующем изменении `tscalpInstrumentId` / `figi` в domain-модели, чтобы правило не рассинхронизировалось с кодом.
<!-- SUBSECTION_END: i-25 -->










<!-- SUBSECTION_BEGIN: i-30 -->
___
### [OPEN] I-30 System: A resource failed to call release (lifecycle-находка)
**Статус:** открыт (pre-existing, не блокер).

**Симптом:** при disconnect брокера (logout / смена токена / force-stop) в
logcat: `System: A resource failed to call release.` (PID 32180,
2026-11-07 02:08:48.655 — live-logout-лог `~/tscalp-live-logout.log`).
Предупреждение системы об утечке ресурса (OkHttp / CIO / gRPC channel /
WS-socket), не наш код, но триггерится нашими lifecycle-переходами.

**Причина:** не установлена. Возможные источники:
`TInvestPositionsWebSocket` (CIO client), `TInvestPricesWebSocket`,
`TInvestOrderStateWebSocket`, gRPC channel (`pricesStreamChannel` /
`ordersStateChannel`), OkHttp client. Не закрываются при `deinitialize()` —
только `stop()` отменяет scope, но клиент/канал не `close()`.

**Решение:** диагностика — `grep` в `TInvestBrokerApi.deinitialize` /
`TInvestWsService.stop` / `TInvestOrchestrator` — все `HttpClient` /
`Channel` / `OkHttpClient` закрыты? Если нет — `close()` в
`deinitialize()`. Отдельная задача (fix в 13d не входит — pre-existing,
вне scope split VM).

**Файлы:**
- `data/api/TInvestBrokerApi.kt` — `deinitialize()`.
- `transport-ktor/.../TInvestPositionsWebSocket.kt` /
  `TInvestPricesWebSocket.kt` / `TInvestOrderStateWebSocket.kt` — `close()`.
- `data/orchestrator/TInvestOrchestrator.kt` — `close()` gRPC channel.

**Связано:** `docs/ISSUES.md` → «PriceFlowManager retry loop после logout
при неактивных VM», «BrokerRegistry.anyInitialized не сбрасывается после
DisconnectBrokerUseCase» (та же область — lifecycle disconnect).

**Когда вернуться:** при следующей правке `TInvestBrokerApi.deinitialize`
/ `TInvestWsService.stop` / `TInvestOrchestrator`, или при появлении
жалоб на утечку ресурсов / battery drain после logout.
<!-- SUBSECTION_END: i-30 -->









<!-- SECTION_END: issues-misc -->
