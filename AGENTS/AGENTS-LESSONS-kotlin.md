<!-- LESSONS_SECTION_BEGIN: kotlin -->










<!-- SUBSECTION_BEGIN: l-kotlin-86 -->
___
### l-kotlin-86 `assert "${X}" not in text` — ловит X в других методах
**Правило:** post-write verify `assert "${result.error.message}" not in text` на **всём файле** ловит `${result.error.message}` в **pre-existing** методах, не только в изменённом. Проверять внутри извлечённого блока целевого метода: `re.search(r"fun <name>\(.*?
    \}", text, re.DOTALL)` → `assert "${X}" not in match.group(0)`. Или проверять **позитивно** внутри блока (`initResult.error.message in block`), не негативно на файле.

**Инцидент:** 2026-11-07, M013 step 1b (`a47ddae`). После apply: `git diff --check` пуст, `grep -nE "result\.(data|error)"` → `0 (clean)` (по **методу**), но мой whole-file `assert "${result.error.message}" not in t` упал на PortfolioViewModel: `${result.error.message}` есть в **pre-existing** `loadAccounts` (283, 289), `onSearchQueryChanged` (323), `openBrokerDialog` (682), `loadDialogAccounts` (769), `updateTradingStatuses` (847, 947), `refreshLastSelectedInstruments` (982). Файл корректен — assert ложный, лишний round-trip.

**Проверка:** verify-assert после правки — только по извлечённому блоку целевого метода. `grep -nE` по файлу — для **диагностики** (понять, где именно), не для assert. Родственно урокам 67 (line-anchored для `###`), 79 (post-assert scope — только новые маркеры), 80(б) (проверяй факт, не форму).

**Инцидент-счётчик (2-й, 2026-11-07):**
- M013 step 3b (`520bbcb`): `assert "when (val result =" not in t2` — whole-file, нашёл pre-existing `when (val result = ...)` в `openBrokerDialog`, `loadDialogAccounts`, `updateTradingStatuses`, `refreshLastSelectedInstruments`. В `loadAccounts` замена корректна. Ложный FAIL, лишний round-trip.
- M013 step 5b (`61a74d3`): тот же класс — whole-file `assert "accountsRepository" not in t2` вместо narrow по извлечённым блокам `openBrokerDialog` / `loadDialogAccounts`. В этот раз сработал корректно (в VM действительно 0), но **тот же антипаттерн**. Исправлено — narrow `re.search` по каждому методу.

**Связано:** уроки 26 (`assert old not in text` — KDoc), 65 (`new ⊂ old`), 67 (inline-ссылки в `###`), 79 (post-assert scope), 80(б) (проверяй факт, не форму).
<!-- SUBSECTION_END: l-kotlin-86 -->










<!-- SUBSECTION_BEGIN: l-kotlin-88 -->
___
### l-kotlin-88 Idempotency guard `if new_fragment in text: skip` неверен, когда new ⊂ old
**Правило:** idempotency-guard в скрипте правки — проверять **old-fragment**, которого в новом состоянии **не будет** (`if old_frag not in text: print("already applied"); return`). **Не** `if new_frag in text: skip` — если `new` является подстрокой `old` или наоборот, guard срабатывает ложно: `new` уже есть в файле **до** правки (как часть `old`), правка не применяется, хотя должна.

**Инцидент:** 2026-11-07, M013 step 2b (`99ecd6a`). `new_p1 = "@HiltViewModel\nclass PortfolioViewModel"` — эта строка есть **и до** (с `@Suppress("TooManyFunctions")` на предыдущей), **и после** удаления `@Suppress`. Guard `if new_p1 in t: print("P1: already applied — skip")` → **skip** → `@Suppress` не удалён. P2–P4 применились, `write_text` записал файл в несогласованном состоянии (`seedSheet` удалён, но `@Suppress` остался). Пришлось доделывать line-based fix.

**Вторая ошибка того же шага:** `assert new_p in pt, "Portfolio fix already applied"` — inverted guard (`assert` при `True` пропускает, при `False` падает — то есть падает именно когда фикса **ещё нет**). Правильная семантика: `if new in text: skip; else: assert old in text; replace`.

**Проверка:** в скрипте — guard по `old` (`if old_frag not in text: skip`), не по `new`. Если есть подозрение на пересечение — `assert old_frag in text, "old not found"` + `assert text.count(old_frag) == 1` до replace, `assert new_frag in text` после. Связано: урок 4d (идемпотентность), 65 (`new ⊂ old`), 79 (post-assert scope).
<!-- SUBSECTION_END: l-kotlin-88 -->










<!-- SUBSECTION_BEGIN: l-kotlin-91 -->
___
### l-kotlin-91 Verify = call-form / declaration-form, никогда `not in text` для symbols
**Правило:** при verify правки / pre-commit — проверять **фактическое
состояние кода**, не подстроку в файле. Для символов (`fun X`, `val X`,
`@Annotation`, `suspend fun X`, `LaunchedEffect(x)`) — call-form /
declaration-form (`^\s*(suspend\s+)?fun\s+X\(`, `^\s*val\s+X\b`,
`^\s*@Annotation\b`, `^\s*LaunchedEffect\(x\)`) или **code-only**
grep (`grep -vE '^\s*(\*|//|/\*|\*/|import)'`). `assert "X" not in
text` / `grep "X"` ловит KDoc, комментарии, KDoc-цитаты — ложные
срабатывания.

**Инцидент-счётчик (2026-11-07, Session Orders-VM-Split, 12+ нарушений
класса 80(б) за сессию):**
1. `assert "startPositionUpdates" not in text` — KDoc «**не вызывает**
   `startPositionUpdates` (12b, P4)».
2. `assert "Правило Clean: M2" in t` — фактически `**Правило Clean:** M2`
   (bold между `Clean:` и `M2`).
3. `assert "suspend" not in t` — KDoc «нет suspend, deterministic».
4. `grep -nE "@Inject constructor"` — KDoc «**`@Inject constructor()` без
   зависимостей** — для Hilt-инъекции в VM».
5. `assert "LaunchedEffect(uiState.statusMessage)" not in t` — KDoc
   «Pre-existing N3-duplicate (`2 × LaunchedEffect(uiState.statusMessage)`)».
6. `state anchor count=0` — anchor `val state:`, фактически `val uiState:`.
7. `assert "updateTradingStatuses" not in vm_text` — мой assert искал
   `private fun updateTradingStatuses(`, в файле
   `private suspend fun updateTradingStatuses(`.
8. `assert "restoreState()" not in t2` — KDoc «13d-F-A: restoreState()
   переехал в OrderFormViewModel».
9. `assert "currentPrice =" not in t2` — `SelectedInstrumentInfo(
   currentPrice = null, …)` (domain-конструктор, не state).
10. `assert "coordState\." not in t2` — re.sub без raw-строки (см. урок 92).
11. `assert "N3 duplicate still present"` — KDoc «Two status channels …
    Pre-existing N3-duplicate (`2 × LaunchedEffect(uiState.statusMessage)`)».
12. `assert "PriceConsumer" import` — импорт был после `PriceFlowManager`
    (урок 48).

**Правило (окончательно):** `assert "X" not in text` для **symbol** —
**запрещён**. Только:
- **call-form**: `re.search(r'^\s*(private\s+|suspend\s+|override\s+)?fun\s+X\(', text, re.MULTILINE)`;
- **declaration-form**: `re.search(r'^\s*val\s+X\b', text, re.MULTILINE)`;
- **code-only**: `grep -vE '^\s*(\*|//|/\*|\*/|import)' "$file" | grep -nE "X"`;
- **markdown-aware**: `assert "**Правило Clean:** M2" in t` (с `**`);
- **call-form для LaunchedEffect**: `re.search(r'^\s*LaunchedEffect\([^)]*\)', text, re.MULTILINE)`.

**Проверка:** перед отправкой скрипта — для каждого `assert "X" not in
text` — `grep -n "X" file | grep -vE '^\s*\*'` + call-form regex.
`grep "X" file` без фильтра — запрещён как verify.
<!-- SUBSECTION_END: l-kotlin-91 -->










<!-- SUBSECTION_BEGIN: l-kotlin-92 -->
___
### l-kotlin-92 `re.sub` replacement — raw-string или без escaped-symbol; `SyntaxWarning` = сигнал
**Правило:** `re.sub(pattern, replacement, text)` — replacement-строка
**не должна содержать** escaped-symbol (`\.`, `\(`, `\)`, …) вне
raw-строки (`r'\.'`). Python 3.12+ выдаёт `SyntaxWarning: "\." is an
invalid escape sequence` — это **сигнал**, что `re.sub` вставит литеральный
`\.` вместо `.`. Использовать `re.sub(pattern, 'coordState.', text)` (без
escape) или `re.sub(pattern, r'coordState\.', text)` (raw-string, если
escape **нужен** в regex-replacement — редко).

**Инцидент:** 2026-11-07, Session Orders-VM-Split, 13d-F. В скрипте:
`sc = re.sub(r'\buiState\.', 'coordState\.', sc)` (replacement —
`'coordState\.'`, не raw). Python выдал `SyntaxWarning: "\." is an invalid
escape sequence` — я его проигнорировал. В 6 местах файла записалось
`coordState\.` (литеральный backslash) — `viewModel.coordState\.collectAsState()`.
Kotlin-компилятор упал бы на `Unresolved reference 'coordState\'`, но
повезло — гейт остановился раньше (ktlint). Fix: `t.replace("coordState\.",
"coordState.")`.

**Правило (расширено):** `SyntaxWarning` в Python-скрипте — **не
косметика**, а реальный баг в replacement. `re.sub` — только с
raw-string в **pattern** (`r'...'`) и без escaped-symbol в **replacement**
(или тоже raw, если действительно нужен `\`).

**Проверка:** перед отправкой скрипта — `grep -n "re.sub" script.py` и
`grep -nE "\\([.()\\[\\]{}])" script.py` (escaped-symbol в
replacement). Если есть — заменить на raw-string или убрать escape.
Дополнительно — Python при запуске печатает `SyntaxWarning` — **читать
stderr**, не подавлять.

---
<!-- SUBSECTION_END: l-kotlin-92 -->










<!-- SUBSECTION_BEGIN: l-kotlin-97 -->
___
### l-kotlin-97 uniqueness pre-check до replace: `count == 1`, а не `new in text`
**Правило:** в скрипте правки `.md`/`.kt` — **до** `text.replace(old, new, 1)` проверить `assert text.count(old) == 1, f"count={text.count(old)}"` (или `if old not in text: skip`). **Запрещено** `if new in text: print("already applied"); skip` для широких `old`/`new` (например, `.isEmpty()`, `StatusEffect`): они могут встретиться в **другом** контексте и дать ложное «уже применено», а целевое место — не тронуто.

**Инцидент:** 2026-11-09, M016 (PR #112). `if NEW in t2: print("already fixed")` для `.isEmpty()` — сработало ложно: многострочная форма уже была в **другом** тесте (`line 54`), а целевой (`line 91`) — не тронут. CI упал снова, третий push на тот же ktlint (`1269cc28`). В том же скрипте для `StatusEvent` правило «SKIP, если new уже есть» — работало корректно (new был уникальным).

**Проверка:** в скрипте правки перед `replace` — `assert text.count(old) == 1, f"count={text.count(old)}"`. Если `old` законно встречается N раз — `count == N`. «Already applied» — только при `if text.count(new) == 1 and text.count(old) == 0`, и только если `new` уникально.
<!-- SUBSECTION_END: l-kotlin-97 -->










<!-- SUBSECTION_BEGIN: l-kotlin-108 -->
___
### l-kotlin-108 Idempotency guard — в extract'е блока, не глобальный `grep` по файлу
**Правило:** идемпотентность-guard для правки `.md` (по 88) проверять **в extract'е целевого блока** (`start_marker` … `end_marker`), не глобально по файлу. Глобальный `grep -qF "marker"` / `if NEW in text: skip` даёт **false positive**, когда `NEW` уже есть в **другой** секции файла — guard абортит правку, файл остаётся нетронутым, лишний round-trip. Правильно:

```python
START = "**<уникальный якорь начала блока>**"
END = "**<уникальный якорь конца блока>**"
assert t.count(START) == 1 and t.count(END) == 1
si, ei = t.index(START), t.index(END)
block = t[si:ei]
if NEW in block: print("already applied — skip"); return
assert block.count(OLD) == 1, f"count={block.count(OLD)}"
t = t[:si] + block.replace(OLD, NEW, 1) + t[ei:]
```

Bash-guard `if grep -qF "$NEW" "$F"; then ...` — **запрещён**, если `NEW` может встретиться в другой секции (пример: `✅ merged (PR #141)` уже есть в Предусловиях и во втором prereq-блоке).

**Инцидент:** 2026-11-14, prereq 2 status в ROADMAP. Bash-guard `grep -qF "✅ merged (PR #141"` (глобально по `docs/ROADMAP.md`) сработал ложно: маркер уже стоял в **Предусловиях** (строка 313) — guard абортил правку в **другой** строке (361, prereq 2 `**Статус:**`). Файл не тронут (аборт до `python3`), но потрачен round-trip.

**Проверка:** перед bash-guard `grep -qF "$MARKER" "$F"` — `grep -cF "$MARKER" "$F"`. Если > 1 — guard должен быть scoped: extract блока (python) или `awk`-по-номерам-строк (start…end). Для `**Статус:** запланировано.` в ROADMAP — 24 вхождения, глобальный replace запрещён (см. 56а/67).

---
<!-- SUBSECTION_END: l-kotlin-108 -->










<!-- SUBSECTION_BEGIN: l-kotlin-109 -->
___
### l-kotlin-109 `grep -c` при 0 совпадениях возвращает exit 1 — убивает `set -e`
**Правило:** при `N=$(grep -cE '<pattern>' file)` под `set -e` — **всегда** дописывать `|| N=0`. GNU grep `-c` печатает `0` в stdout, но возвращает **exit 1**, когда совпадений нет (POSIX: «exit > 0 means no lines selected»). Assignment наследует exit 1 → `set -e` убивает группу **до** следующей команды. Правильно:

```bash
N=$(grep -cE '^(```|~~~)' "$F") || N=0
test $((N % 2)) -eq 0 || { echo "FAIL: fence parity ($N)"; exit 1; }
echo "OK: fence parity ($N)"
```

`|| N=0` перехватывает exit 1 от `grep`; `N` остаётся `0` из stdout — перезапись no-op. Не `|| true` — маскирует **любой** exit (включая 2 = битый regex).

**Инцидент:** 2026-11-14, mini-gate на `docs/ci-roadmap.md` (0 fences). `N=$(grep -cE ...)` → exit 1 → `set -e` убил группу на assignment. `docs/ci-roadmap.md` остался unstaged, коммит не создан. Тот же mini-gate на `AGENTS.md` (18 fences) и `AGENTS-LESSONS.md` (2) проходил — баг проявлялся только на 0-fence файлах, что составляет **большинство** docs.

**Проверка:** перед `N=$(grep -cE ...)` под `set -e` — `|| N=0`. Или `set +e` вокруг, или `grep -cE ... >/dev/null || true; N=$(grep -cE ...)` (двойной вызов, не нужно). Родственно 94/101 (grep в скриптах).

---
<!-- SUBSECTION_END: l-kotlin-109 -->










<!-- SUBSECTION_BEGIN: l-kotlin-110 -->
___
### l-kotlin-110 `cmd && echo OK` под `set -e` не abort'ит — только левая часть как statement
**Правило:** `test … && echo "OK"` **не** abort'ит группу под `set -e`, даже когда `test` вернул 1 — POSIX `set -e` **не применяется к левой части** `a && b`. Аборт произойдёт только если `a && b` — последняя команда в списке (проверка всей конструкцией). Внутри `{ …; }` — `a && b` не последняя (дальше `;`), `set -e` не срабатывает. Симптом: `OK:` не напечатано, но группа **продолжилась** к следующей команде (commit, push). Правильно — все четыре пункта mini-gate через `|| { …; exit 1; }`:

```bash
git diff --check || { echo "FAIL: trailing ws"; exit 1; }
echo "OK: trailing ws"
test $((N % 2)) -eq 0 || { echo "FAIL: fence parity ($N)"; exit 1; }
echo "OK: fence parity ($N)"
```

**Инцидент:** 2026-11-14, mini-gate на `docs/ci-roadmap.md`. Broken link (`docs/ci-cd.md`) — `BROKEN:` напечатан, `test -f "$d" || echo "BROKEN: $d"` exit 1 — но **группа продолжилась** → commit `57908178` + push. Mini-gate прошёл как «информационный», а не как abort-гейт. Найдено вручную при разборе обрезанного вывода.

**Проверка:** grep по mini-gate-скриптам — `grep -nE ' && (echo|test)' script.sh` (левая часть `&&`). Если это guard — заменить на ` || { echo "FAIL: …"; exit 1; }`. Дополнительно — при добавлении нового пункта в mini-gate: **red test** (намеренно сломать и убедиться, что abort'ит).

**Связано:** 109 (частный случай, `grep -c` exit 1); POSIX `set -e` — `a && b` exemption.

---
<!-- SUBSECTION_END: l-kotlin-110 -->










<!-- SUBSECTION_BEGIN: l-kotlin-111 -->
___
### l-kotlin-111 Commit message — английский, включая цитаты названий и полей
**Правило:** commit message формулируется **сразу на английском**, включая цитаты названий задач, заголовков секций и служебных полей (`**Дата закрытия:**`, `**Статус:**`). Не «вставлю и посмотрю, поймает ли pre-check». Механический pre-check — последняя линия защиты, а не первая.

**Инцидент:** сессия 2026-11-15 (C2 + v2 + build-warnings), **10/10** попыток пронести русскую цитату в `git commit -F` поймал pre-check `grep -P '[А-Яа-я]'` — до коммита. Каждый раз — лишний round-trip: `cat → pre-check → abort → переформулировать`. Примеры: `«### Compose UI tests: миграция на v2»` (C4), `«### OrderFormViewModel: split на sub-VM»` (ROADMAP mark done), `«**Дата закрытия:**»`, `«**Статус:** в работе — Phase 6 done»`, `«(C2, снятие @Suppress) + 2 test infra prereqs»`. Правило 5 «цитаты — тоже на английском» есть с самого начала, но я его обходил мысленно на этапе `cat > $HOME/.msg.txt`.

**Проверка:** превентивно — message-черновик пишется на английском; если в голове всплывает русская цитата — **перефраз** или перевод. Постфактум — pre-check перед `git commit` (как в 5b/4i). Расширение урока 77 и правила 5: «10/10 поймано pre-check» — сигнал, что без pre-check проходило бы 10/10.

---
<!-- SUBSECTION_END: l-kotlin-111 -->










<!-- SUBSECTION_BEGIN: l-kotlin-112 -->
___
### l-kotlin-112 Idempotency guard — по `old`, не по `new`; `new ⊂ old` — ложно-«уже применено»
**Правило:** idempotency-guard в скрипте правки `.md`/`.kt` — проверять **`old`** (`if old not in text: print("already applied"); return`), а не `new`. `if new in text: skip` ложно-срабатывает, когда `new` содержит `old` как подстроку **или** встречается в файле в другом контексте. `assert old not in text` для «старой формы больше нет» — только если `new` её не содержит; иначе — `assert count(old) == expected_old_count` (обычно 0) или `assert count(new) == expected` без проверки отсутствия `old`.

**Инцидент:** сессия 2026-11-15 (PR-4b, C2), **2 случая**:
1. **Phase 5d, Edit 6 (`OrderExecutionViewModel` KDoc):** `if new in text: skip` — `new = " * 2026-11-14: execution-результат, а не trade-details)."` уже присутствовал в файле **внутри `old`** (на строке до anchor). Guard ложно-пропустил; строка `«работает параллельно до Phase 5»` осталась. 7 файлов изменены, 8-й — нет. Обнаружено verify-grep; фикс-скрипт с `if OLD not in t: skip`.
2. **`OrderPlaceRequestMapper.kt` (fix ранее):** `assert "toLegacyFormState" not in t` — ловил **KDoc-цитату** `«`OrdersScrollableState.toLegacyFormState` … удалён в Phase 5d»` (legitimate исторический комментарий) — assert падал с ложным «функция ещё здесь», хотя `internal fun` был удалён.

Расширение урока 88 (idempotency guard) и 65 (`new ⊂ old`).

**Проверка:** в скрипте — `if old_frag not in text: skip` + `assert text.count(old_frag) == 1` до replace. Если `new` может быть в файле по другому поводу (KDoc, исторический комментарий) — `assert count(old_frag) == 1`, replace, потом **line-anchored** `re.findall(r'^new_frag$', text, re.MULTILINE)` (не подстрочный). Это тот же принцип, что урок 67 («line-anchored regex для `###`») — распространить на любой маркер.

---
<!-- SUBSECTION_END: l-kotlin-112 -->










<!-- SUBSECTION_BEGIN: l-kotlin-113 -->
___
### l-kotlin-113 JVM/Gradle-флаги — синтаксис проверить до применения
**Правило:** JVM-флаги (`-Xshare:off`, `--sun-misc-unsafe-memory-access=allow`, `-Xmx`, `-Dfile.encoding`) — **синтаксис варьируется**: некоторые с `:` (`-Xshare:off`), некоторые с `=` (`--sun-misc-unsafe-memory-access=allow`), некоторые с пробелом (`-Dfile.encoding=UTF-8`). Перед добавлением в `gradle.properties` / `tasks.withType<Test>` — сверить с **документацией JDK** (не по памяти, не по образцу «в интернете видел»). Неверный синтаксис = `Unrecognized option: -Xshare=off` → JVM не стартует → **gate RED**, но файл уже записан.

**Инцидент:** 2026-11-15 (C2, build-warnings). В `app/build.gradle.kts` добавил `jvmArgs("-Xshare=off")` (со знаком `=`). Gate (`testDebugUnitTest`) — `BUILD FAILED in 45s`, `Unrecognized option: -Xshare=off` → `Error: A fatal exception has occurred. Program will exit` в `Gradle Test Executor 123/124/125`. Правка: `-Xshare:off` (**двоеточие**). Правил в 2 итерации.

**Проверка:** перед `jvmArgs` / `org.gradle.jvmargs` / `JAVA_OPTS` — `java -Xshare:help` (для `-Xshare`), или `man java` на нужной JDK, или официальная документация Oracle. Плюс — **минимальный локальный прогон** (`./gradlew :app:testDebugUnitTest`) до `git commit`, а не после.

---
<!-- SUBSECTION_END: l-kotlin-113 -->










<!-- SUBSECTION_BEGIN: l-kotlin-114 -->
___
### l-kotlin-114 YAML block scalar (`run: |`) — многострочный контент одинаково сдвинут
**Правило:** в GitHub Actions `run: |` (block scalar) все строки контента должны иметь одинаковый отступ — минимум **+2 пробела** от `run:`. Строка с меньшим отступом (особенно 0) разрывает block scalar; YAML-парсер видит конец блока и падает на следующей строке. В контексте `run:` это даёт `Invalid workflow file: path#L<N>`.

**Инцидент:** 2026-11-15, `ktlint-gradle-944.yml` (PR #158, merge `45052d05`). В `run: |` было:
```yaml
          BODY="ktlint-gradle issue #944 ... CLOSED.

Next steps:
1. Check ...
```
— `Next steps:` и `1.` на нулевом отступе. GitHub Actions: `Invalid workflow file: .github/workflows/ktlint-gradle-944.yml#L64` (строка 64 = `Next steps:`). Хотфикс `5ce9dbf4` (PR #159): `BODY="..."` → `cat > body.txt <<EOF … EOF` + `gh issue create --body-file body.txt`. После merge — smoke-test через `workflow_dispatch`.

**Проверка:**
1. Превентивно — `ruby -ryaml -e 'YAML.parse_file(ARGV[0])' file.yml` (или `python3 -c 'import yaml,sys; yaml.safe_load(open(sys.argv[1]))' file.yml`). Локально у нас fallback (ruby / python3+yaml нет) — тогда визуально: все строки под `run: |` имеют отступ ≥ +2 от `run:`, нет нулевого отступа внутри контента.
2. Долгосрочно — `actionlint` в CI (отдельной задачей, не блокер; связать с `### Markdown linter в CI`).

Расширение урока 77 (`strict pre-check`) и 5a (CI/CD — порядок «коммит → push → CI»).

---


## Kotlin / Compose
<!-- SUBSECTION_END: l-kotlin-114 -->










<!-- SUBSECTION_BEGIN: l-kotlin-72 -->
___
### l-kotlin-72 Smart cast невозможен для computed getter
**Правило:** если `val prop: T` объявлен как `get() = ...` (computed getter) — `if (obj.prop != null && obj.prop.someMethod())` даёт `Smart cast to 'T' is impossible`. Сохранять в локальную: `val local = obj.prop; if (local != null && local.someMethod())`.

**Инцидент:** 2026-11-04, CI `compileDebugKotlin`: `AssetPositionCard.kt:379:83 Smart cast to 'Double' is impossible, because 'profitPercent' is a property that has an open or custom getter`. В `Position` (sealed interface) `profitPercent` — computed getter (`get() { ... }`), не поле.

**Проверка:** `grep -n "get() =\|get() {" file.kt` — найти computed getters; в местах `obj.prop != null && obj.prop...` — заменить на локальную `val`.
<!-- SUBSECTION_END: l-kotlin-72 -->










<!-- SUBSECTION_BEGIN: l-kotlin-73 -->
___
### l-kotlin-73 `data class` non-null common param без default — обязателен
**Правило:** при создании экземпляра `sealed interface` / `data class` в Preview или тесте — все non-null поля без default обязательны. Перед правкой — `grep` всех полей интерфейса / data class; `String` без `= ""` / `?` — обязателен.

**Инцидент:** 2026-11-04, CI `compileDebugKotlin`: `AssetPositionCard.kt:462:25 No value passed for parameter 'isin'`. В Preview «Futures» — `Future(tscalpInstrumentId = "uid-3", ticker = "SiU4", name = "Фьючерс Si", classCode = "SPBFUT")` без `isin`. `isin: String` в `Instrument` — non-null `String`, не `String?` и без default.

**Проверка:** перед `data class(...)` в Preview / тесте — `grep -nE "^\s+val \w+: \w+" Instrument.kt` (тип без `?`, без `=`) — все обязательные поля.
<!-- SUBSECTION_END: l-kotlin-73 -->










<!-- SUBSECTION_BEGIN: l-kotlin-74 -->
___
### l-kotlin-74 Locale-dependent `format` — `Locale.US` в продакшене
**Правило:** `"%.1f".format(x)` в продакшене зависит от `Locale.getDefault()` — на CI (ru-RU) даёт `"10,5"`, тест ждёт `"10.5"`. Формат — часть контракта → `String.format(Locale.US, "%.1f", x)`.

**Инцидент:** 2026-11-04, CI `testDebugUnitTest`: `PositionFormatterTest > formatQuantity keeps one decimal FAILED` (`ComparisonFailure`, `expected "10.5"`). `formatQuantity` использовала `"%.1f".format(quantity)`, на CI (ru-RU locale) → `"10,5"`. Локально (desktop, ru-RU) тоже бы упало — но там тест не запускался.

**Проверка:** `grep -n '"%[0-9.]*[df]".format' file.kt` — все такие места → `String.format(Locale.US, ...)`. Или `Locale.setDefault(Locale.US)` в тесте (`@BeforeClass`).
<!-- SUBSECTION_END: l-kotlin-74 -->










<!-- SUBSECTION_BEGIN: l-kotlin-75 -->
___
### l-kotlin-75 `Currency.getInstance` — ISO 4217 uppercase
**Правило:** `java.util.Currency.getInstance(code)` требует ISO 4217 uppercase (`"RUB"`, `"USD"`). Lowercase / mixed case — `IllegalArgumentException`. Нормализовать: `code.uppercase(Locale.ROOT)` перед `getInstance`.

**Инцидент:** 2026-11-04, CI `testDebugUnitTest`: `PositionFormatterTest > formatAverage for share FAILED` (`IllegalArgumentException`). Тест передавал `currency = "rub"`, `Currency.getInstance("rub")` бросал. Латентный баг в продакшене: `Instrument.currency` от брокеров может приходить lowercase. Pre-existing (до PR #90 тоже передавалось как есть).

**Проверка:** `grep -n "Currency.getInstance" file.kt` — перед `getInstance` стоит `uppercase(Locale.ROOT)`; тест с lowercase не падает. В ROADMAP — задача `### Currency format: normalize ISO 4217 case`.


---
<!-- SUBSECTION_END: l-kotlin-75 -->










<!-- SUBSECTION_BEGIN: l-kotlin-78 -->
___
### l-kotlin-78 Mobile/proot: ktlint-ошибки на `.kt` — только CI
**Правило:** на mobile (proot) `ktlintFormat` недоступен. Каждая
ktlint-ошибка = 4–5 минут CI-цикла (Actions-минуты, ожидание).
Гейт здесь «золотой». Перед `git push` — self-check по 5
правилам (AGENTS.md → 4h, mobile-ветка): import order,
`@Inject` на отдельной строке, indent после split, multiline
wrapping, имена тест-функций. Плюс `cat -A` затронутого
региона — `git diff --check` ловит trailing ws, но **не**
неверную индентацию.

**Инцидент:** 2026-11-05, M008 `CalculatePairExecUseCase` + тест.
8 CI-циклов, все — на форматировании:
1. `fun disabledPairTrading returnsZeroAndNull()` — пробел в
   имени теста → `NON_ABSTRACT_FUNCTION_WITH_NO_BODY`.
2. `val result = useCase.execute(` (multiline, а `val result =`
   на той же строке) → `multiline-expression-wrapping`.
3. `class X @Inject constructor()` inline → `Expected newline
   before annotation`.
4. Тело класса на 4 после split `@Inject constructor()` →
   `Unexpected indentation (4) (should be 8)`.
5. `}` класса на 0 (а ktlint ожидает 4, т.к. `class X` +
   `@Inject` на отдельной строке — как `PostOrderUseCase`).

Ни одна из 5 не ловится `git diff --check`. Ловятся — `cat -A`
или `ktlintCheck` (недоступен на mobile).

**Проверка:** AGENTS.md → 4h, mobile-ветка. Перед `git push` —
5 grep + `cat -A` затронутого региона.
<!-- SUBSECTION_END: l-kotlin-78 -->










<!-- SUBSECTION_BEGIN: l-kotlin-84 -->
___
### l-kotlin-84 `TooManyFunctions` на классе-менеджере — extraction в top-level extension
**Правило:** если класс > 11 функций и раздутие идёт за счёт **pure-функции** без зависимости от полей класса — вынести её как top-level `private fun Type.method(...)` в тот же файл. Detekt `TooManyFunctions` считает только члены класса — top-level extension не считается. `@Suppress` не нужен. Если раздутие — из-за **разнородных** методов с зависимостью от состояния класса — split на доменные модули (урок 60).

**Инцидент:** 2026-11-05, `PositionFlowManager` (задача «Portfolio: баланс и пополнение в песочнице»). Добавил `applySeed` (12-я функция) → detekt `TooManyFunctions` (порог 11). `mergePartial` — pure (`when` по subtypes `Position`, без полей класса). Вынес в `private fun Position.mergeStreamItem(...)` top-level → 11 функций в классе, гейт зелёный, `@Suppress` не понадобился.

**Проверка:** `grep -nE "^        (private )?(suspend )?fun [a-zA-Z]+\(" <file>` — count. Если > 11 — какая из функций pure (не читает поля класса)? → extension. Иначе — split (урок 60).
<!-- SUBSECTION_END: l-kotlin-84 -->










<!-- SUBSECTION_BEGIN: l-kotlin-103 -->
___
### l-kotlin-103 Hilt-test infra pitfalls (Robolectric + Compose)
**Правило:** 3 грабли на первом PR:
1. `@HiltAndroidApp` в `test/` source set конфликтует с `@HiltAndroidTest` в том же compilation unit (`Cannot process test roots and app roots in the same compilation unit`). Fix: `robolectric.properties` → `application=dagger.hilt.android.testing.HiltTestApplication` (встроенный в `hilt-android-testing`).
2. `HiltTestActivity` (`@AndroidEntryPoint`) — в `app/src/debug/kotlin/.../testutil/`, не `test/`; + `<activity>` в `app/src/debug/AndroidManifest.xml`. `test/AndroidManifest.xml` **не** мёржится в `debugUnitTest`; `test/` classes не участвуют в `lintDebug`; Activity нужна в variant (`debug`).
3. Полный набор на smoke-тест: `@HiltAndroidTest` + `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [34])` + `@GraphicsMode(NATIVE)` + `@ConscryptMode(OFF)` + `@get:Rule(order = 0) HiltAndroidRule(this)` + `@get:Rule(order = 1) createAndroidComposeRule<HiltTestActivity>()`.

**Инцидент:** U4 step-1 (2026-11-11), 3 CI-цикла: `Cannot process test roots` → `RuntimeException at RoboMonitoringInstrumentation` (Activity не в manifest) → зелёный.

**Проверка:** перед первым Hilt-smoke — все 3 условия; при `RuntimeException at RoboMonitoringInstrumentation` — `debug/AndroidManifest.xml`; при `Cannot process test roots` — `robolectric.properties` без custom `@HiltAndroidApp`.

---
<!-- SUBSECTION_END: l-kotlin-103 -->










<!-- SUBSECTION_BEGIN: l-kotlin-115 -->
___
### l-kotlin-115 Rate-limiter per-minute bucket capacity = 1s burst, not full minute
**Правило:** per-minute token-bucket инициализировать `capacity = limit / 60.0` (1 секунда токенов), не `capacity = limit` (полная минута). `refillPerNano` — по-прежнему `limit / ONE_MINUTE_NANOS`. Per-second bucket (`postOrder` 15/sec) — `capacity = limit` (уже 1s), не трогать.

**Инцидент:** 2026-11-16, live-test sandbox (fix/rate-limiter-burst-capacity). `TInvestRateLimiter` инициализировал `perMethodMinBuckets` и `perServiceBuckets` с `capacity = limit`. Для `instruments` (200/min) бакет стартовал с 200 токенами → клиент разрешал burst до 200 запросов мгновенно. Реальный burst: `findInstruments` = 1 `findInstrument` + N `getInstrumentBy` + N `getTradingStatus` (2N+1 unary на поиск). Сервер отбивал `RESOURCE_EXHAUSTED`: 152 за прогон, первые 10 за ~1.2 сек. После `capacity = limit / 60.0 = 3.33` — 0 `RESOURCE_EXHAUSTED`, waits 98–300ms (`getTradingStatus` ~100ms = 600/min, `getInstrumentBy` ~300ms = 200/min).

**Проверка:** в `TokenBucket(capacity = ...)` для per-minute bucket — `capacity = limit / 60.0`. Для per-second — `capacity = limit`. Тест: burst N > 60 × limit за < 1 сек, ожидаем 0 `RESOURCE_EXHAUSTED` на сервере, waits ≈ 60000/limit ms. Дефолт `capacity = limit` в новом rate-limiter — красный флаг.
<!-- SUBSECTION_END: l-kotlin-115 -->





<!-- LESSONS_SECTION_END: kotlin -->
