<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: roadmap-wip -->
## В работе










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










<!-- SUBSECTION_BEGIN: t-47 -->
<a id="t-47"></a>
### [WIP] T-47 docs_cut: тело блока и обёртка inline.sh
**Мобильная работа:** полное закрытие (proot + PR; гейт на CI).
**Статус:** в работе.
**Ветка:** feature/docs-tooling
**Контекст:** штатная операция `r5g` «перенести / start / defer / закрыть» парой `docs_cut.py` → `docs_add.py` не работает: `docs_cut.py` пишет в tmp целый блок (`SUBSECTION_BEGIN` + якорь + `### …` + тело + `---` + `SUBSECTION_END`), а `docs_add.py` сам добавляет `BEGIN`, якорь и `### <title>`, а `--body-file` подставляет как есть. На входе получается вложенный `SUBSECTION_BEGIN`, `normalize_text` падает `AssertionError: nested BEGIN` — перенос задачи невозможен. Обнаружено при старте T-45.
**Второй дефект:** каркас inline-скриптов (`r4i`, шаблон 1) копируется в каждый блок руками — `tee`, `RC=${PIPESTATUS[0]}`, `echo&&echo "---"`, `wc -l`, `exit $RC`. Забытый элемент искажает exit-код и вывод.
**Соответствие Clean:** — (не применимо, инструменты документации).
**Нарушенное правило Clean:** —
**Clean-альтернатива:** —
**Выгоды (для «отклонение по 2c»):** —
**MVVM:** — (не применимо).
**KMP:** — (не применимо).
**Предусловия:** T-44 закрыт (скрипты `docs_add` / `docs_cut` / `docs_normalize` / `docs_lint` существуют).
**Файлы:**
- `AGENTS/scripts/docs_cut.py` — второй выход: тело блока.
- `AGENTS/scripts/inline.sh` — новый.
- `AGENTS/scripts/README.md` — строки в таблице инструментов.
- `AGENTS/AGENTS-style-docs.md` → `r4i` — альтернативный запуск через `inline.sh`.
**Шаги:**
1. `docs_cut.py`: кроме `agents-cut-<id>.md` (блок, поведение не меняется) писать `agents-body-<id>.md` — только тело: без `SUBSECTION_BEGIN`, без якоря, без строки `### …`, без завершающих `---` и `SUBSECTION_END`. Pre-assert: блок найден ровно один раз. Post-assert: в теле нет `SUBSECTION_`, `FILE_`, `<a id=`; размер > 0.
2. `inline.sh`: читает stdin во временный файл (`4k`: `/tmp`, fallback `$HOME/tmp`), исполняет **отдельным** `bash` (тело самодостаточно — без обёртки работает так же), затем `tee` в `$HOME/deepseek-chat.txt`, `RC`, `echo&&echo "---"`, `wc -l`, `exit $RC`. Опции: `--log <file>`, `--keep`. `WARN`, если в теле нет `set -e` / `set -uo pipefail` — без падения.
3. `AGENTS/scripts/README.md`: строка `inline.sh` в таблицу; у `docs_cut.py` — упоминание второго выхода.
4. `r4i`: добавить альтернативный запуск (`bash AGENTS/scripts/inline.sh <<'BASH_END'`) и два раздела: «что гарантирует скрипт» (tee / RC / wc / exit) и «что остаётся на авторе» (`set -euo pipefail`, явный `cd` и пути, pre/post-assert с сообщением, verify в том же блоке, line-anchored проверки, `echo` без backticks, `printf` для commit message, внешний fence 5 тильд).
5. `docs_rebuild_index.py --apply`, затем `docs_lint.py --all` — зелёный.
**Тесты:** `inline.sh` на теле `set -euo pipefail` + `false` → exit 1, лог непустой; тело без шапки → `WARN`, exit 0; `docs_cut.py --apply` на копии `ROADMAP-plan.md` → оба файла, тело без маркеров; `docs_add.py --body-file agents-body-<id>.md` → вставка без `nested BEGIN`; `docs_lint.py --all` → `ALL CHECKS PASSED`.
**Референсы:** `AGENTS/AGENTS-style-docs.md` → `r4i`, `r4c`, `r4d`; `AGENTS/AGENTS-style-git.md` → `r5g`; `AGENTS/scripts/README.md`; `docs/docs-structure.md` §4.
**Ограничения:** `docs_add.py` не меняется; вывод `docs_cut.py` для блока не меняется; `r5g` не меняется (пара `cut → add` остаётся штатной, `--body-file` указывает на `agents-body-…`); автодетекта блока в `docs_add` не вводится.
**Критерий готовности:** старт и перенос задачи проходят парой `docs_cut → docs_add` без падения; `inline.sh` исполняет самодостаточное тело и печатает `RC` и `wc -l`; README содержит строки обоих скриптов; `docs_lint.py --all` зелёный.
**Оценка:** 30–45 мин.
**Зависимости:** T-44.
**Связано:** T-44, T-45 (потребитель — старт и закрытие задачи).
**Открытые вопросы:** старт T-47 выполняется разово inline-переносом (bootstrap: инструмент, делающий перенос штатным, появляется этой же задачей; решение пользователя в этой сессии). Возможное расширение — `docs_move.py` (одна атомарная операция): рассмотрено и отклонено в пользу минимальной правки `docs_cut.py`.

---
<!-- SUBSECTION_END: t-47 -->










<!-- SECTION_END: roadmap-wip -->










<!-- FILE_END -->
