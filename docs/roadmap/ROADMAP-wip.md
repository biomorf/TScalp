<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: roadmap-wip -->
## В работе










<!-- SUBSECTION_BEGIN: t-44 -->
<a id="t-44"></a>
### [WIP] T-44 SSoT стиля документации (docs/docs-structure.md) + миграция всех файлов и инструментов
**Мобильная работа:** полное закрытие (proot + PR; docs + скрипты, гейт на CI).

**Статус:** в работе. Ревизия 2026-11-20: исходная задача переписана под принятые решения.

**Контекст:** стиль оформления `.md` размазан по 4 источникам: (1) `AGENTS-integrity.md` — маркеры; (2) `project-docs-section-order` — разделители; (3) код скриптов (`docs_normalize.py`, `docs_rebuild_index.py`) — форматы заголовков и индексов; (4) `r4j` — список инструментов. «Код — единственный полный источник истины для форматов» — это не SSoT, это четыре канона, расходящихся друг с другом. Результат: любая правка стиля требует синхронной правки AGENTS + кода + индексов, и риск рассинхрона растёт с каждым коммитом. Решение — **один канон-документ**, в котором прописаны инварианты, а скрипты и AGENTS ссылаются на него, а не определяют сами.

**Ревизия 2026-11-20 — принятые решения:**
1. Канон — `docs/docs-structure.md`. Скрипты — реализация.
2. Индексы — 5 колонок `| TOKEN | ID | Title | File | Anchor |`. Anchor — кликабельная ссылка `File#id`.
3. Счётчик `Next ID` в шапке индекса. Архив не читается.
4. Маркеры plain (`<!-- FILE_BEGIN -->`), префиксы `AGENTS_FILE_BEGIN` / `LESSONS_FILE_BEGIN` запрещены и заменяются (не дополняются).
5. Область применения — три уровня: (а) все `.md` — `FILE_BEGIN/END`; (б) `.md` с `##` — `SECTION_BEGIN/END`; (в) семейства AGENTS/LESSONS/roadmap/ci-roadmap — `SUBSECTION_BEGIN/END` + токены + ID + якоря + индексы.
6. Заголовки: `### [TOKEN] <ID> <title>`. TOKEN выводится из имени файла: `AGENTS-LESSONS-*` → `[AGENTS-LESSONS]` (приоритет), иначе `AGENTS-*` → `[AGENTS]`; roadmap → `WIP/PLAN/DEFER/DONE`; ci-roadmap → то же.
7. Регистр ID: UPPER для roadmap/ci-roadmap (`T-44`, `C-5`); kebab-case lower для AGENTS (`r4k`, `l-kotlin-115`). Маркер — всегда kebab-case lower. Связь `marker.id` ↔ `heading.ID`: для roadmap `marker.id == lower(heading.ID)`; для AGENTS — точное строковое равенство.
8. Якорь `<a id="<id>"></a>` — производный от маркера, первая строка тела. Проверка: наличие, позиция, совпадение с id маркера.
9. Разделители: единственный визуальный разделитель — `---` **перед** `SUBSECTION_END`, отделён от тела **ровно одной** пустой строкой. `___` убирается полностью. Между блоками и перед `SECTION_END` / `FILE_END` — ровно 10 пустых строк. Таблица переходов §4.4 упраздняется.
10. Цепочка статусов: имя файла → токен → строка `**Статус:**`. Смена статуса = перенос блока в другой файл, а не правка текста.
11. `AGENTS-integrity.md` превращается в ссылку на SSoT (прекращает определять маркеры).
12. Описание CLI (§6 старого документа) уходит в `AGENTS/scripts/README.md` (стык с T-2).
13. Корневой `AGENTS.md` — тонкий указатель, без дублирования полного индекса.

**Соответствие Clean:** — (не применимо, документация/инструменты).

**Файлы:**
- `docs/docs-structure.md` — новый SSoT (инварианты, не миграция).
- `AGENTS/AGENTS-integrity.md` — ссылка на SSoT, убрать определения маркеров.
- `AGENTS/AGENTS-project-docs.md` → `project-docs-section-order` — ссылка на SSoT, убрать таблицу переходов.
- `AGENTS/AGENTS-style-docs.md` → `r4j` — ссылка на `AGENTS/scripts/README.md`, убрать описание CLI.
- `AGENTS/AGENTS.md` — тонкий указатель, SECTION-маркеры, без дублирования полного индекса.
- `AGENTS/scripts/README.md` — описание инструментов (новый).
- `AGENTS/scripts/docs_normalize.py` — убрать `___`; добавить `---` + одна пустая перед `SUBSECTION_END`; якорь как первая строка тела; замена префиксных маркеров.
- `AGENTS/scripts/docs_rebuild_index.py` — 5 колонок с Anchor; счётчик `Next ID`; `HEADING_RE` с TOKEN.
- `AGENTS/scripts/docs_add.py` / `docs_cut.py` — соответствие новому формату.
- `docs/scripts/check-docs.sh` — синхронизация с новым CLI (`--root` / `--check-index` больше нет); новые проверки канона (см. «Шаги»).
- Все `AGENTS/AGENTS-*.md` и `AGENTS/AGENTS-LESSONS-*.md` — массовая правка (скриптом, r4c).
- Все `docs/roadmap/ROADMAP-*.md` и `docs/roadmap/ci-roadmap-*.md` — массовая правка (скриптом, r4c).

**Шаги:**
1. **Написать новый `docs/docs-structure.md`:** инварианты (разделы 1–5 из скелета ревизии), без раздела «Скрипты» (уходит в README) и без таблицы переходов. Поле статуса для каждого правила — три значения: Правило / Инструмент / Файлы.
2. **Переписать `AGENTS-integrity.md`:** убрать определения маркеров (FILE/SECTION/SUBSECTION), оставить только роль integrity-policy (отчёт о прочтении) и ссылку на SSoT.
3. **Переписать `project-docs-section-order`:** убрать таблицу переходов, оставить ссылку на SSoT §4.
4. **Переписать `r4j`:** убрать описание CLI, оставить ссылку на `AGENTS/scripts/README.md`.
5. **Написать `AGENTS/scripts/README.md`:** таблица «скрипт / назначение / аргументы / пример» для 4 штатных скриптов.
6. **Переписать `docs_normalize.py`:** убрать `___` как обязательный; добавить генерацию `---` + одна пустая перед `SUBSECTION_END`; якорь `<a id="<id>"></a>` как первая строка тела; замена (не дополнение) префиксных маркеров; сохранение `rstrip` на каждой строке тела; 10 пустых везде между блоками.
7. **Переписать `docs_rebuild_index.py`:** 5 колонок; ячейка Anchor — кликабельная ссылка `File#id`; счётчик `Next ID` в шапке индекса; `HEADING_RE` с захватом TOKEN; вывод `--mode` в 4 режимах (agents / agents-lessons / roadmap / ci-roadmap).
8. **Обновить `docs_add.py` / `docs_cut.py`:** соответствие новому формату блока (якорь, `---` перед END).
9. **Переписать `docs/scripts/check-docs.sh`:** убрать удалённые флаги `--root` / `--check-index`; добавить новые проверки канона:
   - `FILE_BEGIN` — первая строка, `FILE_END` — последняя;
   - отсутствие префиксных маркеров (`AGENTS_*`, `LESSONS_*`);
   - `marker.id` ↔ `heading.ID` (с учётом регистра по семействам);
   - уникальность `id` в пределах индекса (для AGENTS и ROADMAP — раздельно);
   - наличие якоря `<a id="...">` первой строкой тела;
   - `---` перед каждым `SUBSECTION_END` с ровно одной пустой строкой перед ним;
   - отсутствие `___` в файлах области;
   - соответствие токена имени файла (TOKEN ↔ File);
   - соответствие `**Статус:**` токену (`WIP → в работе` и т. д.).
10. **Массовая правка AGENTS-файлов (скриптом, r4c, r4d):** `### <id> <title>` → `### [AGENTS] <id> <title>` (или `[AGENTS-LESSONS]`); добавить якоря; убрать `___`; добавить `---` + одна пустая перед `SUBSECTION_END`; нормализовать 10 пустых.
11. **Массовая правка ROADMAP-файлов (скриптом, r4c, r4d):** то же, но с токенами `WIP/PLAN/DEFER/DONE`; приведение ID заголовков к UPPER; токены в строках индекса.
12. **Корневой `AGENTS.md` — тонкий указатель:** гейт обязательного чтения, ссылка на полный индекс `AGENTS/AGENTS.md`, без дублирования таблицы.
13. **Пересобрать индексы:** `docs_rebuild_index.py --apply` — 4 индекса, в каждом `Next ID`, 5 колонок.
14. **Verify:**
    - `docs_normalize.py --check --all` → 0 deviations;
    - `docs_rebuild_index.py --dry-run` → 4 индекса consistent;
    - `check-docs.sh` → зелёный;
    - рендер якоря на GitHub на одном файле (эмпирическая проверка: `File#t-44` прокручивает к блоку).

**Тесты:**
- `docs_normalize.py --check --all` (deviations=0);
- `docs_rebuild_index.py --dry-run` (4 индекса consistent);
- `check-docs.sh` (все проверки зелёные);
- эмпирическая проверка рендера якоря на GitHub (1 файл, вручную после merge).

**Референсы:** сессия ревизии 2026-11-20; `AGENTS-integrity.md`; `project-docs-section-order`; `r4j`; `docs_add.py` / `docs_cut.py` / `docs_normalize.py` / `docs_rebuild_index.py`; `check-docs.sh`; `ROADMAP.md` (индекс); `ci-roadmap.md` (индекс).

**Ограничения:**
- `issues/` / `clean-audit/` — **вне scope** (структура не подтверждена, не табличная).
- Массовая правка AGENTS и ROADMAP — **только скриптом**, не агентом (r4c, r2d).
- Архивы (`ROADMAP-ARCHIVE.md`, `CI-ROADMAP-ARCHIVE.md`) не трогаем: FILE-маркеры расставляются (уровень 1), структурный стиль не применяется.
- Не менять логику `docs_cut.py` (кроме соответствия новому формату блока).
- Счётчик `Next ID` — в шапке индекса; при выдаче инкремент; первичная установка — разовым скриптом по максимуму индекса и архива (скрипт архив читает, контекст ассистента — нет).

**Критерий готовности:**
- `docs/docs-structure.md` — SSoT (все инварианты, без §6 и таблицы переходов).
- `AGENTS-integrity.md`, `project-docs-section-order`, `r4j` — ссылки на SSoT / `scripts/README.md`, без дублирования стиля.
- `AGENTS/scripts/README.md` — описание 4 инструментов.
- Скрипты обеспечивают описанный порядок (создание и поддержание).
- Все AGENTS-файлы — `### [AGENTS] <id> <title>` (или `[AGENTS-LESSONS]`), якоря, `---` перед END, 10 пустых.
- Все ROADMAP-файлы — `### [WIP|PLAN|DEFER|DONE] <ID> <title>` (UPPER), якоря, `---` перед END, 10 пустых.
- 4 индекса — 5 колонок, `Next ID`, consistent.
- `docs_normalize.py --check --all` → deviations=0.
- `check-docs.sh` → ALL CHECKS PASSED.
- Рендер якоря на GitHub проверен.
- Гейт зелёный.

**Оценка:** ~10–14 ч. (SSoT 1 ч, переписывание AGENTS-директив 1 ч, скрипты 3 ч, `check-docs.sh` 1.5 ч, миграция AGENTS 2 ч, миграция ROADMAP 1.5 ч, индексы 0.5 ч, verify 1 ч, фиксы по ходу 2–3 ч).

**Зависимости:** T-2 (консолидация скриптов) — пересекается в `AGENTS/scripts/README.md`, не блокер; решается в шаге 5.

**Связано:** T-2 (консолидация скриптов), `AGENTS-integrity.md`, `project-docs-section-order`, `r4j`, `AGENTS-report.md`.

**Открытые вопросы:** нет.

---
<!-- SUBSECTION_END: t-44 -->










<!-- SUBSECTION_BEGIN: t-3 -->
<a id="t-3"></a>
### [WIP] T-3 Rate-limit umbrella: cold start + search RESOURCE_EXHAUSTED
**Мобильная работа:** полное закрытие (proot + PR; гейт на CI).

**Статус:** в работе. Блокирует live-test C2 (scenarios 2–7).

**Ветка:** — (umbrella; ветки подзадач — отдельные)

**Умбрелла.** Группировка задач по rate-limit и сокращению unary-запросов. Цель: устранить `RESOURCE_EXHAUSTED` на cold start и search; заложить broker-agnostic architecture для BCS/Finam.

**Контекст (live-log C2, 2026-11-15, emulator, sandbox T-Invest):**
- **Search burst:** 6 × `TInvestInstruments: RESOURCE_EXHAUSTED` за 300ms (17:50:40.024–.314) на pair-search.
- **Причина:** `findInstruments` = N+1 unary (1 + N `getInstrumentBy`); C2 добавил `SearchInstrumentsUseCase:49` `updateStatuses(ids)` = +N `getTradingStatuses` → **2N+1 unary** на поиск. Sandbox 6/сек → burst.
- **Cold start:** 5s×N accounts polling + 10s position polling + 5 min periodic statuses + `restore` (broker init + instrument ×2 + load) → суммарная нагрузка поверх search.
- **НЕ rate-limit:** `PortfolioScreen` `LaunchedEffect(isLoading)` infinite retry (UX-баг, блокирует live-test 6).

**Подзадачи (все — отдельные задачи 5e, ниже в этом разделе):**

| # | Подзадача | Тип | Блокирует |
|---|---|---|---|
| 1a | `Rate-limiter для unary-запросов T-Invest` (existing) | защита (P0) | live-test 2–7 |
| 1b | `Stream connection limiter: WS + gRPC-streaming` (new) | защита (P0) | WS-reconnect шторм, BCS/Finam streaming |
| 2 | `Search: сократить N+1 unary в findInstruments` (new) | оптимизация | UX search |
| 3 | `updateStatuses: skip on cache hit / lazy on select` (new) | оптимизация | rate-budget |
| 4 | `getTradingStatuses: батчинг / concurrency` (new; поглощает `clean-audit` Н66) | оптимизация | rate-budget |
| 5 | `PortfolioScreen: retry с backoff` (new; **не rate-limit**, UX) | UX | live-test 6 |

**Порядок:** **1a → 1b → (2, 3, 4 параллельно) → 5.**
**Обоснование:** (1a) — фундамент unary: без защиты любая новая фича / фоновый запрос / periodic может снова выбить unary-лимит. (1b) — фундамент stream: WS-реконнект-шторм может выбить stream-лимит (32/16/11) молча; сокращение без защиты — «бег на месте». После (1) каждая оптимизация — безопасный refactor: «стало 2N+1 → N+1 → 1» на фоне гарантии ≤ 50/sec. (5) независима от rate-limit, но блокирует live-test 6 (restore).

**Multi-broker (2a, 2e):** rate-limits у брокеров разные:
- T-Invest: `docs/rate-limits.md` (50/sec global, 200/min Instruments gRPC, 600/min MarketData, `postOrder` 15/sec, `GetUserTariff` для динамических лимитов).
- BCS: `429 Too Many Requests`; конкретные лимиты — TBD (проверить при `### Реализация BCS Broker API`).
- Finam: REST 200 req/min (`docs/brokers.md`); gRPC — TBD.

**Архитектурное решение (2e → data-слой, ближе к транспорту):** `RateLimiter` — интерфейс broker-agnostic (в `data/api/common/`); конфиг лимитов — per-broker (`data/api/<broker>/<Broker>RateLimits.kt`). Обёртка вокруг **каждого unary-вызова** в `data/api/tinvest/TInvest*Service` (`getAccounts`, `findInstruments`, `fetchProtoInstrument`, `getTradingStatuses`, `postOrder`, …). Перед вызовом — `rateLimiter.withPermission(transport, service, method) { ... }`. Domain не знает про лимиты — они свойство транспорта. Подзадача 1 реализует для T-Invest; BCS/Finam — при интеграции.

**Разные протоколы и методы — разные лимиты (D1 = 4 уровня):**
1. **global 50/sec** — по IP, все запросы.
2. **per-(transport, service)** — gRPC и REST **разные бюджеты** (Instruments gRPC 200/min vs REST 100/min; MarketData 600/600; Orders 100/100).
3. **per-(transport, method)** — если у метода свой лимит (`getOrders` 200, `postOrder` 15/sec, `getHistory` 30 REST, `getStopOrders` 60, `cancelOrder`/`replaceOrder` 300, `postOrderAsync` 600).
4. **per-protocol fallback** — при gRPC → REST fallback (orchestrator) второй запрос идёт в **другой** budget (gRPC Instruments 200 vs REST Instruments 100), не в общий «Instruments».

**Соответствие Clean:** соответствует (organizational umbrella; подзадачи — по 2e отдельно).

**MVVM:** — (не применимо, umbrella).

**KMP:** — (не применимо, umbrella).

**Предусловия:** нет.

**Файлы (umbrella):** `docs/ROADMAP.md` — эта umbrella; `docs/ISSUES.md` — umbrella-запись.

**Шаги:** см. подзадачи 1–5. Каждая — отдельный PR.

**Тесты:** см. подзадачи. Umbrella-критерий: live-test C2 2–7 PASS без `RESOURCE_EXHAUSTED`.

**Референсы:** `docs/rate-limits.md` (обновлён 2026-11-15, [официальная страница](https://developer.tbank.ru/invest/intro/intro/limits)); `docs/ISSUES.md` → `## T-Invest rate-limit umbrella...`, `## PortfolioScreen: infinite retry...`, `## T-Invest REST findInstruments fallback (N+1 unary)`; `docs/clean-audit.md` → Н66; live-log C2 (2026-11-15); `docs/brokers.md`.

**Ограничения:**
- Не смешивать с `### Rate-limiter...` — это подзадача, umbrella только группирует.
- Multi-broker: не реализовывать BCS/Finam-лимиты сейчас (нет интеграции), только архитектурная готовность (2a).
- Не блокировать PR #160 — он уже merged; umbrella — новая работа.

**Критерий готовности:** live-test C2 2–7 PASS; `RESOURCE_EXHAUSTED` в search — 0; rate-limiter в продакшене; search latency приемлемая (< 2 сек на 10 инструментов); Portfolio retry — bounded (3 попытки).

**Оценка:** umbrella ~6–9 ч суммарно. Подзадачи: (1a) 3–5 ч; (1b) 1–2 ч; (2) 1–1.5 ч; (3) 0.5–1 ч; (4) 1 ч; (5) 0.5 ч.

**Зависимости:** нет. Подзадачи независимы (кроме 2–4 → после 1).

**Связано:** PR #160 (observability + 3 fix; merged `4af3b682`); PR #162 (burst-capacity fix; merged `1a3635fb`); `docs/ISSUES.md` → `## T-Invest rate-limit umbrella...`, `## PortfolioScreen: infinite retry...`; `docs/clean-audit.md` → Н66; `docs/rate-limits.md`; `docs/brokers.md`.

**Открытые вопросы (решить в подзадаче 1):**
- **D1 (решено).** A+ split: 1a — unary (gRPC-unary + REST-unary, token bucket, 4 уровня); 1b — stream (gRPC-streaming + WS, semaphore, флаг `WS_SHARES_STREAM_BUDGET = true`). «gRPC» в конфиге заменён на «streaming» (WS = прокси для gRPC streaming, делят stream-лимит).
- **D2.** `updateStatuses` в search: skip on cache hit / lazy на select / drop?
- **D3.** `findInstruments` N+1: partial (InstrumentShort) vs full контракт? Если presentation требует full — N+1 неизбежен без API-батчинга.
- **D4.** Concurrency: `Semaphore` поверх limiter (N parallel) vs sequential?
- **D5 (решено).** Статика из `docs/rate-limits.md` (A); `GetUserTariff` (динамика, B) — отдельная follow-up задача.
- **D6 (решено).** Per-broker конфиг: `data/api/<broker>/<Broker>RateLimits.kt` (A) + флаг `WS_SHARES_STREAM_BUDGET`. Role-pattern не применим — см. «Не role» в `### Rate-limiter для unary-запросов T-Invest`.

---
<!-- SUBSECTION_END: t-3 -->










<!-- SUBSECTION_BEGIN: t-33 -->
<a id="t-33"></a>
### [WIP] T-33 Установка GrpcExceptionGuard
**Мобильная работа:** частичное (live-тест на устройстве).

**Статус:** в работе.
**Подтверждение:** ожидает (PR #33 merged, ветка удалена;
AppMetrica, 2–3 дня после релиза; критерий: нет новых fatal
`io.grpc.StatusException`, появились non-fatal
`"Unhandled gRPC"`; детали — «Критерий закрытия» ниже).
**Триггер сработал (2026-10-21):** AppMetrica зафиксировала
краш `io.grpc.StatusException`.

**Контекст:** `io.grpc.kotlin.ClientCalls` при ошибке канала
доставляет `StatusException` через поздний callback в уже
завершённую suspend-корутину. Доставить некому → исключение
уходит в `Thread.getDefaultUncaughtExceptionHandler()` →
kill process. `CoroutineExceptionHandler` и `.catch { }` не
помогают.

**Файлы:**
- `app/src/main/kotlin/.../TScalpApplication.kt` — установка
  глобального `UncaughtExceptionHandler`.

**Шаги:**
1. В `TScalpApplication.onCreate` обернуть оригинальный
   `Thread.getDefaultUncaughtExceptionHandler()`.
2. Если `throwable is StatusException || throwable is StatusRuntimeException`:
    - логировать через `AppLogger.e` (с расшифровкой из
      `TInvestErrorMessages`);
    - отправить в AppMetrica как **нефатальную** ошибку
      (`AppMetrica.reportError("Unhandled gRPC", throwable)`);
    - **не пробрасывать** дальше в оригинальный handler
      (приложение выживает).
3. Иначе — пробросить в оригинальный handler (пусть падает
   как обычно).
4. Проверка: включить авиарежим на эмуляторе, дождаться
   gRPC-ошибки, убедиться, что приложение не падает, а в
   логах есть запись об ошибке.

**Ограничения:**
- Не глотать ошибку молча — обязательно логировать и
  отправлять в AppMetrica. Иначе скроем реальные сетевые
  проблемы (см. `docs/ISSUES.md`).
- Не перехватывать `CancellationException` — это штатная
  отмена, она не должна попадать в handler.

**Критерий готовности:**
- Приложение не падает при gRPC-ошибках в фоне.
- Ошибки видны в AppMetrica (как non-fatal) и в логах.
- Остальные краши (не gRPC) по-прежнему валят приложение и
  видны в AppMetrica.

**Live-смоук (2026-10-22, эмулятор Pixel 8a, Android 14):**
- Guard установлен, старт приложения не сломан
  (`initAppMetrica` → `installGrpcExceptionGuard`, порядок
  сохраняет fatal-reпортинг AppMetrica).
- Авиарежим → `UNAVAILABLE` во всех стримах; guard молчит
  (штатные ошибки ловятся в своих корутинах — `try/catch`,
  `.catch` — до глобального handler не доходят). Ожидаемое
  поведение.
- Возврат сети → gRPC-reconnect и WS-reconnect, данные
  пошли, приложение живо.
- `AndroidRuntime: FATAL EXCEPTION` — нет.
- Исходный race (`ClientCalls` доставляет `StatusException`
  в уже завершённую корутину) по команде не воспроизводится —
  этим смоуком не проверялся. Регрессий не внёс, но и
  доказательством работы guard не является.

**Критерий закрытия:** наблюдение AppMetrica 2–3 дня после
релиза. Задача подтверждена, если одновременно:
- не появляются новые **fatal** `io.grpc.StatusException`;
- появляются **non-fatal** `"Unhandled gRPC"` (из
  `AppMetrica.reportError` в guard).

Если оба условия выполнены — `docs(roadmap): mark
grpc-exception-guard done`. Если появились новые fatal —
guard не срабатывает, разбираться отдельно.

**Оценка:** ~1 час.

---
<!-- SUBSECTION_END: t-33 -->










<!-- SECTION_END: roadmap-wip -->










<!-- FILE_END -->
