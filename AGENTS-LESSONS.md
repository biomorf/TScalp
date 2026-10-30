# AGENTS-LESSONS.md — уроки из инцидентов

Дополняет `AGENTS.md`. Там — правила (императив), здесь — уроки из
конкретных инцидентов (почему правило появилось, как проверять).
Читается **вместе с** `AGENTS.md` в начале каждой сессии.

Если файл не передан пользователем в первом сообщении — ассистент
**настойчиво требует** его (см. `AGENTS.md` → «Обязательные документы»).

## Шаблон записи

    ### <Короткое название правила>

    **Правило:** одна строка, императив.

    **Инцидент:** что произошло (файл, ошибка, дата), какой урок.

    **Проверка:** команда или критерий.

---

## Структура файла

Уроки сгруппированы по темам: «Скрипты и правки», «CI»,
«Оценки», «Диагностика», «Clean-audit», «UI и дизайн», «Процессные».

**Правило:** новая запись идёт в **соответствующий
тематический раздел**, не плоско в конец файла и не в
произвольный раздел. Если ни один существующий раздел не
подходит — создать новый раздел в логическом месте (по
соседству с близкими по теме) и обновить список в этом
блоке.

**Проверка:** `grep -n "^## " AGENTS-LESSONS.md` перед
вставкой — увидеть все существующие разделы; выбрать
тот, к которому относится урок; если новая тема —
создать раздел.

---

## Скрипты и правки

### 1. Grep по `main` и `test`

**Правило:** при добавлении обязательного параметра в конструктор —
`grep -rn "ClassName(" app/src` (включая `test`, не только `main`).

**Инцидент:** H23, `TInvestOrchestratorFallbackTest` — 5 callsites
в тестах, CI упал `NO_VALUE_FOR_PARAMETER`. В диагностике искал только
`app/src/main`.

**Проверка:** `grep -rn "ClassName(" app/src/main app/src/test`.

### 2. `str.count` ≠ `grep -c`

**Правило:** перед `assert count == N` — прогнать `grep -n` и
пересчитать глазами, или явно различать строки и вхождения.

**Инцидент:** H23, `SettingsViewModel` — ожидал 6 `DebugConfig.`,
реально 9 (3 чтения + 3×2 в `_uiState.update`). `PriceFlowManager` —
ожидал 2, реально 3 (два обращения в строке 207).

**Проверка:** `grep -c` — строки, `grep -o | wc -l` — вхождения.
Смотреть, нет ли двух обращений в одной строке.

### 3. Supertype в Impl → import Interface

**Правило:** при добавлении `: Supertype` в data-класс — сразу
добавить `import <path>.Supertype`. KSP-ошибка «ERROR supertype» —
всегда про импорт, не про видимость.

**Инцидент:** H21b, `SettingsRepositoryImpl`, `TradingStateRepositoryImpl`
— добавил `: Interface`, забыл импорт. KSP упал, Kotlin-компилятор
молчал до KSP (KSP падает первым).

**Проверка:** `grep -n "^import.*SupertypeName" file`.

### 4. `internal` + `public @Binds` = EXPOSED_PARAMETER_TYPE

**Правило:** если реализация `internal`, то `@Binds`-функция
тоже `internal abstract fun`. Альтернатива — `@Provides` в
`DataModule` (параметр не нужен, только return type).

**Инцидент:** H23, `bindDebugFlagsRepository(impl: DebugConfig)` —
`public abstract fun` в `public abstract class` не может принимать
`internal` параметр. Kotlin-компилятор упал после KSP.

**Проверка:** на CI (локально на телефоне не видно). При
`internal class` + `@Binds` — делать `internal abstract fun` сразу.

### 5. `mockk(relaxed = true)` — импорт типа не нужен

**Правило:** при `mockk(relaxed = true)` в аргументе конструктора
тип выводится из параметра, импорт интерфейса не нужен. Импорт
нужен только при явном `mockk<Type>()`.

**Инцидент:** H23, `PriceFlowManagerTest` — вставил
`import DebugFlagsRepository`, ktlint упал `UnusedImport`.

**Проверка:** после `mockk(...)` в аргументе — не добавлять импорт;
если добавил — `ktlintCheck` поймает.

### 6. Порядок импортов — по алфавиту, всегда

**Правило:** при вставке импорта — определять позицию по
лексикографическому порядку вручную (посимвольно), даже если
`ktlintFormat` недоступен локально. Позиционный anchor
(`after import X`) — **запрещён**.

**Инцидент:** H21b (3 раза), H23 (4 файла сразу).
`import X` вставлен после `import Y`, лексикографически X < Y.
На телефоне `ktlintFormat` не запустить, CI упал на
`Imports must be ordered in lexicographic order`.

**Проверка:** перед вставкой — `grep -n "^import" file` и найти
позицию по алфавиту. `java`/`javax`/`kotlin` — в конце. Не «после
такого-то», а «между такими-то». При неуверенности — вставить,
затем `grep -n "^import"` и проверить посимвольно.

### 7. `grep -v` фильтр в verify может врать

**Правило:** после verify с `grep -v` — отдельно посмотреть
«что осталось» и глазами классифицировать (KDoc / historical /
actual). Не доверять фильтру.

**Инцидент:** H21b-финал, `grep "InvestRepository" | grep -vE "..."` —
не отфильтровал `BrokerApi.kt` (`domain/api/`, не `domain/api/repository/`)
и «удалён». Ложный FAIL.

**Проверка:** запустить без фильтра, посмотреть полный вывод,
классифицировать каждую строку.

---

### 23. `text.find("### Header")` находит inline-ссылку

**Правило:** для поиска заголовка в `.md` — `re.search(r"^### Header$", text, re.MULTILINE)`, не `text.find("### Header")`.

**Инцидент:** 2026-10-23, mark done `StreamState`. `text.find("### Унификация")` нашёл не заголовок, а inline-ссылку `` `### Унификация` `` в блоке «Связано» предыдущей задачи. `end_idx` оказался до `start_idx`, `rfind` в диапазоне вернул `-1`. Скрипт упал на `assert closing separator not found`. То же самое — при mark done `Unification` и `TInvest-specific` (ссылки `### StreamState` в тексте).

**Проверка:** перед `find` — `grep -n "^### Header$" file.md`. Или сразу `re.search(r"^### Header$", text, re.MULTILINE)`.

**Дополнение (2026-10-29, повтор):** то же касается `text.replace("### X", ...)` и `re.sub("### X", ...)` без `^...$`. Первое вхождение может быть **inline-ссылкой** (`` `### X` ``) в другом блоке, не заголовком.

**Инцидент (2026-10-29, повтор):** дважды за сессию. Mark done P&L: `str.replace("### Рефакторинг расчёта P&L", "### ✅ Рефакторинг расчёта P&L", 1)` — первое вхождение оказалось inline-ссылкой в H5 → заголовок P&L склеился с inline-контекстом, `git diff` показал `+6223/−6156`. То же для H17–H20. Урок 23 был, но не применён — формулировка говорила про `find`, не про `replace`/`re.sub`.

**Проверка (replace/re.sub):** для правки заголовка — `re.sub(r"^### X$", ..., flags=re.MULTILINE)` или **line-based slicing** (список строк, `startswith` + `find next header`). Позиция — `grep -n "^### X$" file.md` (ровно 1).


---

### 24. `count("**Marker:**") == 1` ложно-отрицателен для общих маркеров

**Правило:** post-write `assert` не проверяет `count == 1` для маркеров, которые есть в каждой задаче ROADMAP (`**Что сделано:**`, `**Референсы:**`, `**Оценка:**`, `**Шаги:**`, `**Файлы:**`, `**Статус:**`, `**Контекст:**`). Использовать `>= 1` + отдельная проверка на дубль внутри *своей* секции (между `### TaskA` и `### TaskB`).

**Инцидент:** 2026-10-23/24, ~6 падений. `assert check.count("**Что сделано:**") == 1` — реально **7** (по числу закрытых задач). `**Референсы:**` — **14**. `**Оценка:** ~35–45 мин` — тот же формат был в другой задаче. `**Находки вне scope:**` — уникальный, прошло. Каждый раз файл корректен, assertion — ложный.

**Проверка:** `grep -c "^\*\*Marker:\*\*$" docs/ROADMAP.md` перед `assert count == 1`. Если > 1 — `text.count(marker, start, end)` в узком диапазоне или unique content-marker.

---

### 26. `assert "old_name" not in text` — KDoc содержит старое имя

**Правило:** после rename — проверять `count(new_name) >= expected`, не `old_name not in text`. Старое имя легитимно остаётся в KDoc, в ссылках на задачи, в исторических цитатах.

**Инцидент:** 2026-10-23, `PriceFlowManager` после `runGrcpStream → collectStream`. `assert "broker.pricesSnapshot" not in check` упало — `broker.pricesSnapshot` есть в KDoc строк 46 и 77 («подписчик подтягивает через `broker.pricesSnapshot`»), это описание абстракции, не код. Аналогично `as? TInvestBrokerAPI` в KDoc-цитатах, `runGrcpStream bypassed`.

**Проверка:** `grep -n "old_name" file | grep -v "^\s*\*"` — отфильтровать KDoc. Или проверять `text.count(": " + new_name)` / `text.count("?." + new_name)` — в коде, не в комментариях.

---

### 27. Голый `assert` в скрипте — нет контекста падения

**Правило:** каждый `assert` — с сообщением `assert X, "what was expected"`. Голый даёт `AssertionError` без указания, что не прошло.

**Инцидент:** 2026-10-23/24, ~6 раз. Скрипт с 15+ assert'ами, падение на 115-й или 233-й строке — какой именно? Traceback `AssertionError` без условия. Приходилось перезапускать диагностику через `grep`. Пользователь: «без сообщения не знаю, какой assert упал».

**Проверка:** `grep -c "^    assert " script.py` vs `grep -c "^    assert .*, \"" script.py` — должны совпадать. Или `grep -n "assert [^,]*$" script.py` — пусто.

---

### 28. `write_text` до post-write assert — файл уже изменён

**Правило:** `write_text` — точка невозврата до `git checkout`. При падении post-write assert файл **уже изменён**. Диагностика — `git diff` + `git status`, не «откатить и перезапустить». Остановка перед коммитом — правильно; остановка перед диагностикой — нет.

**Инцидент:** 2026-10-23, ~5 раз. Скрипт: `write_text` → `assert` → `print("applied")`. Assert падал (#27). Файл изменён, не закоммичен, `print` не выполнен. Терминал показывает «applied не пришло» — приходится проверять `git diff --stat` и решать, применять файл или откатывать. Правильный паттерн: `assert` до `write_text` (пре-проверка), `write_text`, `assert` после (пост-проверка), **каждый** с сообщением.

**Проверка:** структура `pre-assert → write_text → post-assert`. Все с сообщениями. При падении post-assert — сразу `git diff file`, не паника.

---

### 13. Anchor в скрипте — grep перед написанием

**Правило:** перед написанием anchor в скрипте (для замены/вставки) — взять точный текст из файла через `grep -n -A5` на смежный маркер, не по памяти и не по KDoc.

**Инцидент:** 2026-10-27, clean-audit L7. Скрипт вставки `**Анализ L7-part1:**` в Н22 упал на `assert anchor_ref22 in text`. Я использовал anchor `Ссылка на задачу: — (не заведена). Связано: ### Broker lifecycle API`, а фактический текст в Н22 был без `Связано:`. Причина: составлял anchor по воспоминанию о KDoc, а не по текущему файлу. Скрипт упал до `write_text` — файл не изменён, но потрачен round-trip.

**Проверка:** `grep -n -A5 "**Ссылка на задачу:**" docs/clean-audit.md | head -30` перед написанием скрипта. Или `awk '/^### Н22\./,/^### Н23\./' file`.

---

### 14. Fence вложенного блока — 4 backticks или `~~~`

**Правило:** если правка (или fragment для вставки) содержит markdown-разметку с тройными backticks — обернуть внешним блоком из 4+ backticks (или `~~~`). Три backticks внутри трёх — ломают fence.

**Инцидент:** 2026-10-27, подготовка fragment `L6-part2-p1.md`. Внешний блок в чате был из 3 backticks, внутри — markdown с ```python. Fence рассыпался, рендер сломался. Пользователь: «Внешний fence у блока рассыпался».

**Проверка:** перед отправкой code-блока — механически посмотреть на содержимое. Есть 3+ backticks подряд? Если да — обёртка `~~~` или 4+ backticks.

---

### 15. Серия скриптов на один файл — по одному, `git diff` после каждого

**Правило:** если правка файла разбита на серию последовательных python-скриптов, запускать по одному, после каждого — `git diff --stat` и верификация. Не выдавать все скрипты сразу.

**Инцидент:** 2026-10-27, серия скриптов для ROADMAP (umbrella, задачи, BCS, Codecov, ISSUES). Скрипт 3 (BCS) упал на `assert detekt != -1`, но до этого скрипты 1-2 выполнились в памяти. Пользователь запустил всё как один блок, при падении 3 — 1-2 уже записали файл, но не факт что правильно. Пришлось диагностировать через grep отдельно.

**Проверка:** при серии из N>1 скриптов на один файл — один shell-блок на скрипт, `git diff --stat` после каждого.

---

### 16. Скрипт + верификация в одном блоке

**Правило:** скрипт правки и verification-команды — один shell-блок. Пользователь запускает один раз, видит результат применения и результат проверки. Не два раунда «пришли вывод → получи verify».

**Инцидент:** 2026-10-27, ~6+ раз за сессию скрипт и верификация выдавались раздельно. Каждый раз — два round-trip. Двойной лаг в proot.

**Проверка:** `grep -c "^```" ответ` — чётное, скрипт и verify внутри одного fence. Визуально: один markdown-блок `bash` содержит и `python3 <<` heredoc, и `grep`, и `git diff --stat`.

### 29. Не изобретать — grep перед использованием

**Правило:** перед использованием переменной/поля/функции в предложении и перед правкой файла — `grep -n` фактическое объявление в актуальном исходнике. Не полагаться на память о прошлой сессии, на свой предыдущий ответ, на «логично же».

**Инцидент:** 2026-10-28, миграция `app/build.gradle.kts` на Kotlin DSL. Предложил `versionName = "1." + buildTimestamp` — переменной нет, есть `buildTime()`. Пользователь: «А почему ты думаешь, что у меня есть переменная buildTimestamp?». Затем — `buildConfigField("String", "BUILD_TIME", ...)` — не нужно, есть стандартный `BuildConfig.VERSION_NAME`. Аналогично при рефакторинге `OrdersScreen` несколько раз «читал» файл и всё равно предлагал правки по прежней версии.

**Проверка:** `grep -n "<symbol>" path/to/file` перед предложением. Если грепа нет — символ не существует. Перед правкой — `git log --oneline -1 -- path/to/file`: если коммит новее, чем «знание» — запросить свежий raw.

### 30. `return@label` требует наличия label в области видимости

**Правило:** перед `return@let` / `return@run` / `return@launch` — проверить, что вокруг действительно есть соответствующая обёртка. Ошибка `Unresolved label` — не про скобки, а про отсутствующий label.

**Инцидент:** 2026-10-28, `OrdersViewModel.postOrder`. Внутри `try { }` написал `return@let` — обёртки `let` не было. Kotlin: `Unresolved label` на двух строках. Правильный exit — `if (stopPrice == null || stopOrderType == null) { ... } else { ... }`, без метки.

**Проверка:** `grep -n "X\s*{" file` в диапазоне 20-30 строк выше — найти `let {` / `run {` / `launch {`. Если нет — `return` без метки или добавить обёртку.

---

---

### 40. `git add -A` — источник инцидента

**Правило:** только `git add <files>` с явным списком (правило 4g, без исключений). Перед `git commit` — `git diff --cached --name-only`, сверить с ожидаемым. Лишний файл → `git restore --staged <file>`.

**Инцидент:** 2026-10-28, fat-part. 4 коммита с `git add -A` (`a1c119b`, `a1fc32c`, `c4865a5`, `f76ba50`). В `72c28f3` захватил untracked `.tmp` (mv упал молча), `.kt` не изменились → CI упал на тех же ktlint. Revert + fix.

**Проверка:** `git diff --cached --name-only` перед коммитом.

### 41. `mv` без `-f` на телефоне — зависание на `overwrite?`

**Правило:** `mv -f` (или `mv -n`). `mv` без флага интерактивен, если файл существует.

**Инцидент:** 2026-10-28, `.tmp` → `.kt`. `mv` спросил `overwrite?` на два файла, Ctrl+C — оба `mv` не выполнились. Команда зависла в ожидании ввода.

**Проверка:** `mv -f` всегда. Защита от перезаписи — `mv -n` + `echo "skipped"`.

### 42. `.tmp + mv` — коммит может захватить `.tmp`

**Правило:** не использовать `.tmp`-файлы для правок. `write_text` сразу в целевой файл, `assert` до записи и после.

**Инцидент:** 2026-10-28, fat-part. `cp → sed .tmp → mv` — `mv` упал (overwrite?), `git add -A` захватил `.tmp`, `.kt` не изменились. `72c28f3` = 445 строк `.tmp`, CI красный.

**Проверка:** `git diff --cached --name-only` не содержит `*.tmp`. Правка через `write_text` + `assert` — атомарная.

### 43. Heredoc на телефоне — риск потери `MSGEOF`

**Правило:** commit message через `printf '%s\n' ... > "$HOME/.msg.txt" && git commit -F "$HOME/.msg.txt"`. Heredoc (`git commit -F - <<'MSGEOF'`) — только на десктопе.

**Инцидент:** 2026-10-28. При вставке heredoc на телефоне потерялась закрывающая `MSGEOF` → `git commit` ждал ввода бесконечно.

**Проверка:** в блоке коммита — `printf`, не `<<`. Если heredoc неизбежен — `set -e` + `echo "heredoc OK"` после закрывающей метки.

### 44. `set -e` в блоках с несколькими командами

**Правило:** каждый блок, где несколько команд пишут в файл / меняют состояние, начинается с `set -euo pipefail` (или `&&`-цепочка).

**Исключение — захват exit code.** Если блок должен перехватить код возврата (`GATE=$?` после `./gradlew`), начать с `set -uo pipefail` **без** `-e` (иначе падение команды завершает `bash` до `GATE=$?`, вывод `exit=` теряется). Сразу после `GATE=$?` — `set -e`: остальная часть блока (коммит, push) под защитой, как обычно.

**Инцидент:** 2026-10-28, fat-part. Блок `mv .tmp .kt` + `git add -A` + `commit` — `mv` упал, `set -e` не было, скрипт продолжился. Коммит создан, `.tmp` в индексе.

**Проверка:** `set -e` в шапке. `echo "exit: $?"` после критичной команды.

### 45. Парсер multi-line overrides — brace-balance, не regex

**Правило:** удаление методов в Kotlin — через brace-balance по строкам (inline ` = value` → 1 строка; block `{...}` → баланс), не через regex `[^\n]*\n`. Перед удалением — `grep -n "override.*fun <name>"` + `sed -n 'N,Mp'` для границ.

**Инцидент:** 2026-10-28, fat-part. Regex `override fun (\w+)\([^\n]*\n` матчил только первую строку multi-line сигнатуры → orphan bodies (BCS/Finam, ~200 syntax errors). Обратный случай: ` = emptyFlow()` inline, но с `{` в generic — parser искал следующий `{` = тело следующего метода → удалил живую роль.

**Проверка:** `grep -n` + `sed -n 'N,Mp'` для границ. Brace-balance + inline-детект.

### 49. Не писать в `.tmp` до полной проверки всех `.tmp`

**Правило:** при обработке нескольких файлов — сначала все `write_text` в `.tmp` (без mv), проверка (brace, counts), только потом `mv -f` каждого. Никаких промежуточных `mv` до полной проверки.

**Инцидент:** 2026-10-28. Один блок: `sed BCS.tmp`, `mv BCS.tmp BCS`, `sed FIN.tmp` (упал) — BCS уже перезаписан, откатывать сложнее.

**Проверка:** структура `copy → mutate .tmp → check → mv -f → check`.

### 51. `bash -c '...'` ломается от одинарных кавычек в содержимом

**Правило:** не оборачивать блок в `bash -c '...'`, если внутри есть хоть одна одинарная кавычка. Она закроет обёртку, остаток станет командами bash. Использовать `bash << 'EOF' ... EOF` или `python3 << 'PYEOF'` напрямую, без `bash -c`.

**Инцидент:** 2026-10-29. Попытка вставить большой блок (правки `ROADMAP.md` + `architecture.md`) через `bash -c '... python3 << "PYEOF" ... PYEOF ...'`. Внутри были одинарные кавычки — одна закрыла `bash -c '`, весь markdown-контент стал командами bash (`### ...`, `**Тесты:**`, `OrderFlowManager: ...`). Сотни «command not found», `syntax error near unexpected token`. Файлы не пострадали (bash не пишет через `###`), но это прямое нарушение AGENTS 4e (правило 1: команда и пример — раздельно).

**Проверка:** перед отправкой блока — есть ли `bash -c '...'`? Если да — посчитать `'` внутри. Любая — опасно. Заменить на `bash << 'EOF'` или убрать `bash -c`.

---

### 52. `write_text` в Python ломает line endings

**Правило:** перед `Path.write_text(..., encoding="utf-8")` для `.md` / `.kt` — проверить line endings. Если файл с CRLF — `newline=""` в `read_text`/`write_text` (сохраняет как есть). Иначе весь файл «изменится» (universal newlines → LF), `git diff` покажет `+N/−N` вместо локальной правки.

**Инцидент:** 2026-10-29. `write_text` над `ROADMAP.md` — `git show --stat` показал `+6223/−6156`. Первая гипотеза — line endings. `od -c` показал LF (в этом случае CRLF не было; настоящая причина — inline-replace, урок 23). Риск реальный: если файл CRLF, `write_text` его перепишет.

**Проверка:** `python3 -c "import sys; sys.exit(0 if b'\r\n' in open('file','rb').read() else 1)"`. Если CRLF — `newline=""`.

---

### 53. `git diff --cached --stat` перед коммитом

**Правило:** после `git add <file>` и **до** `git commit` — `git diff --cached --stat <file>`. Сверить числа с ожидаемыми. Аномалия (`+6000/−6000` на файл в 7000 строк, когда ожидается `+50/−40`) = что-то сломалось. Откат `git checkout -- <file>`, разбор, заново.

**Инцидент:** 2026-10-29. Коммиты mark done H20/H17–H20/P&L — `+6223/−6156`. Обнаружено **после** коммита (`git reset --mixed`, переделка). Должно было быть **до**.

**Проверка:** `git diff --cached --stat` — часть каждого блока коммита.

---

### 54. Шаблон 4f блока 2 — не урезать grep-фильтр

**Правило:** при использовании шаблона 4f (блок 2, гейт+коммит+push) копировать grep-фильтр **целиком**, включая паттерн `\.kt:[0-9]+:[0-9]+`. Урезание скрывает ktlint-ошибки, которые пишутся как `path/to/file.kt:line:col: Rule`.

**Инцидент:** 2026-10-30, PR #71. Блок 1 (гейт) — `grep -E '^w:|^e:|\[warn\]|\[error\]|FAILED|BUILD'` без `\.kt:` паттерна. `:app:ktlintMainSourceSetCheck FAILED` на `TInvestBrokerApi.kt:41:1 an EOL comment may not be preceded by a KDoc` — эта строка в фильтр не попала, ошибка пропущена. Спасла `tail -3` и повторный grep.

**Проверка:** сверить `grep -E` паттерн с шаблоном 4f посимвольно — все альтернативы (`\.kt:`, `\[warn\]`, `^w:`, …) на месте.

---

### 55. На desktop — `./gradlew :app:ktlintFormat`, не ручной перенос импортов

**Правило:** при ktlint-ошибке про порядок импортов (или других автофиксируемых нарушений) — на desktop запускать `./gradlew :app:ktlintFormat`, а не править импорты вручную. Форматтер использует `.editorconfig` проекта и расставляет импорты по ktlint-правилам (`java`, `javax`, `kotlin` в конце). Ручная правка — двойная работа и риск повторить ошибку.

**Инцидент:** 2026-10-30, KMP phase 1 (#3 Clock). Ручная правка импортов дважды: (1) `kotlin.time.Clock` вставлен после `kotlinx.coroutines.*` вместо после `java.util.*`; (2) `kotlin.time.*` в `StreamStateTrackerTest` вставлены не в конец. Два падения `ktlintTestSourceSetCheck` — два `fix`-цикла. Пользователь: «Неужели нет автоматической сортировки импортов?» — да, `ktlintFormat`, 2 секунды.

**Проверка:** `./gradlew :app:ktlintFormat` → `git diff` (убедиться, что форматтер не сделал лишнего) → повторный гейт.

---

### 56. `assert count == 1` для имени задачи — только line-anchored; структурные проверки — awk, не grep-контекст

**Правило (а) — имена задач/секций в `.md`.** `assert text.count("### <name>") == 1` ложно-падает, если имя встречается в deps / preconditions / KDoc / ссылках «Связано». Считать только заголовки: `len(re.findall(r"^### <name>$", text, re.MULTILINE)) == 1`. Отсутствие в открытых — `not re.search(r"^### <name>$", text, re.MULTILINE)`. Наличие ссылки в deps — `f"`### {name}`" in text` (с backticks, но без `^`).

**Правило (б) — структурные проверки файла.** «Двойные `---` подряд», «пустые строки», «секции» — проверять `awk` построчно (`prev=="---" && $0=="---"`), **не** `grep -B1 "^---$" | grep -A1 "^--$"`. Grep с `-B`/`-A`/`-C` при нескольких группах вставляет собственные разделители `--` между ними — второй grep находит эти разделители и даёт ложный FAIL.

**Инцидент (а):** 2026-10-30, сессия KMP phase 1 → Dispatchers.IO. Три падения подряд на `count == 1` для `### KMP: заменить java.time.Instant в Instruments` и `### KMP: System.currentTimeMillis → Clock-интерфейс`:
1. `pre ROADMAP: #1 not found` — regex на `\n\n---\n` не матчил из-за реального разделителя `\n\n\n\n---\n` (это ISSUES, отдельно).
2. `post ROADMAP: #1 open still present` — `not in text` упал на ссылке в строке «Зависимости» umbrella (`### KMP: заменить …`).
3. `post ROADMAP: new task count` — `count == 1` упал: имя новой задачи `### KMP: инжект CoroutineDispatcher` встречается в заголовке + deps + (не)preconditions.

Каждый раз файл корректен, assertion — ложный. Уроки 23/26 это предупреждали, но формулировка «line-anchored для заголовков» не была явной.

**Инцидент (б):** 2026-10-30, тот же сеанс. Мой `grep -B1 "^---$" | grep -A1 "^--$"` → «DOUBLE --- FOUND» на ~70 парах. Реально двойных `---` в ROADMAP — **ноль** (awk `prev=="---" && $0=="---"` → пусто). Grep-разделители `--` между группами второго grep'а — не строки файла.

**Проверка:**
- (а) `re.findall(r"^### <name>$", text, re.MULTILINE)` — длина 1. Для deps-ссылки — `f"`### {name}`" in text`.
- (б) `awk 'prev=="---" && $0=="---" {print NR}' file` — пусто. `grep -c "^---$"` — не равно 0, но не проверка «двойных».

**Связано:** урок 7 (`grep -v` фильтр врёт), урок 23 (`text.find("### Header")` — inline-ссылка), урок 26 (`assert old not in text` — KDoc), `docs/ISSUES.md` → «Separator между секциями ROADMAP не везде по шаблону» (отдельная pre-existing проблема, не мой scope).

---

### 57. JVM/JUnit API-ограничения: проверять до первого запуска

**Правило:** перед использованием API в коде/тесте — проверить его ограничения в документации, не полагаться на «выглядит логично». Три категории, на которых уже падали:

**(а) `limitedParallelism` и `Dispatchers.Unconfined`.** `Dispatchers.Unconfined.limitedParallelism(n)` бросает `UnsupportedOperationException` в **конструкторе** (`Unconfined.kt:12`). Для тестов менеджеров с `limitedParallelism(1)` — `Dispatchers.Default` (поддерживает). `Unconfined` не подходит.

**(б) Символы в backtick-именах JUnit-методов.** JVM method name spec запрещает `.`, `;`, `[`, `/`, `<`, `>`. Kotlin не санитизирует — компилятор: `Name contains illegal characters: ..`. Имя теста не должно содержать точек: `System.currentTimeMillis` в имени → ошибка компиляции; в KDoc-комментарии — ок.

**(в) JUnit4: тест-метод должен быть `void`.** Expression body `fun foo() = runBlocking { ... }` выводит тип из последнего выражения. Если последнее возвращает не-`Unit` (например, `manager.stop()` → `Unit?` из `synchronized`), JUnit валит `InvalidTestClassError: Method ... should be void`. Block body `{ runBlocking { ... } }` — `void`.

**Инцидент:** 2026-10-30, `feature/kmp-dispatchers` (#4 + fake-clock тест).
- (а) 18 тестов менеджеров упали `UnsupportedOperationException: limitedParallelism is not supported for Dispatchers.Unconfined` — в конструкторе каждого менеджера. Замена на `Dispatchers.Default` — 17 замен, гейт зелёный.
- (б) `OrderFlowManagerTest` — `e: Name contains illegal characters: ..` на имени `evictStale uses injected clock, not System.currentTimeMillis`.
- (в) После (б) — `InvalidTestClassError: Method evictStale uses injected clock, not system time (fake-time regression)() should be void` из-за expression body с `= runBlocking`.

Каждый раз — цикл `гейт → красный → правка → гейт`. Три цикла на одну задачу.

**Проверка:** перед тестом с API — `grep` в документации/исходниках (KMP-таблица ограничений, JUnit-контракт); имя теста — без `.` `;` `[` `/` `<` `>`; JUnit4-метод — block body, не expression body.

---



## Диагностика

### 17. KDoc vs код — grep фактическое использование

**Правило:** перед находкой «KDoc не соответствует коду» — `grep` фактическое использование в репозитории. Не доверять тексту KDoc как источнику правды.

**Инцидент:** 2026-10-27, clean-audit L9. Нашёл «KDoc говорит Engine: Ktor CIO, actual OkHttp» (Н82). После grep: `provideTInvestWsHttpClient` реально CIO, `createTransportHttpClient` (OkHttp) — dead code. KDoc был прав. Н82 отозвана.

**Проверка:** `grep -rn "createTransportHttpClient\|provideTInvestWsHttpClient" app/src` — что вызывается на самом деле.

---

### 18. Не угадывать архитектурный intent

**Правило:** перед рекомендацией «X должен использоваться в Y» — `grep` потребителей X и понять, почему Y не использует X. Не предполагать «не используется = неполнота», возможно — осознанное решение.

**Инцидент:** 2026-10-27, clean-audit L4. Запись Н32: «`StreamCombinators.streamWithFallback` в domain, но менеджеры должны использовать его вместо ручного merge». Пользователь: «Он нужен только для организации переподключения... И да, мне экстремально важно знать откуда пришли данные: из стрима или из polling. В этом весь смысл существования трекера состояний». Оказалось: менеджеры **сознательно** не используют `streamWithFallback`. Н32 переформулирована.

**Проверка:** `grep -rn "streamWithFallback" app/src` перед рекомендацией; посмотреть, какие менеджеры его (не) используют и какие альтернативы у них в коде.

---

### 19. Структурные находки в документах — grep, не память

**Правило:** перед отчётом о положении заголовков/секций в `.md` — `grep -n "^## \|^### "` и сверка с фактическим файлом. Не по памяти прошлых сессий.

**Инцидент:** 2026-10-27, аудит ROADMAP. В отчёте написал «`### Расшифровка кодов ошибок брокеров` в Закрытых задачах» — фактически она в Планируемых (строка 2207, до `## Закрытые` на 2231). Пользователь поймал. Также «двойной `---` перед detekt» — фактически дубль был у хвоста umbrella и уехал вместе с ним.

**Проверка:** `grep -n "^### \|^## Планируемые\|^## Закрытые" docs/ROADMAP.md` перед каждым отчётом.

---

### 25. `awk /^### X/,/^### /` закрывается на самом X

**Правило:** для вывода секции `.md` по границам — `grep -n "^### "` + `sed -n 'N,Mp'` по номерам. Не `awk '/^### X/,/^### /'`.

**Инцидент:** 2026-10-23, 3 раза (`StreamState`, `DebugConfig`, `TInvest-specific`). Обе границы `awk` матчат первую строку — вывод 1 строка или пусто. Приходилось перезапрашивать через `grep -n` + `sed`. Для `## ` та же проблема.

**Проверка:** `grep -n "^### X$\|^### Y$" file` → два номера → `sed -n 'N,Mp'`. Или `awk '/^### X$/{f=1} f && /^### Y$/{exit} f'`.

### 31. «UI не обновляется» — по слоям, не по симптомам

**Правило:** при баге «данные не отображаются / не обновляются» — диагностировать снизу вверх: (1) источник эмитит? лог в `collect`; (2) ViewModel получает? лог в обработчике; (3) `_uiState` обновляется? лог в `_uiState.update`; (4) Composable читает? лог в `LaunchedEffect`. Не начинать с UI-правок.

**Инцидент:** 2026-10-28, «карточки в портфеле не обновляются». Начал с UI (переписал дизайн кнопок, скролл, оверлеи) — не помогло. Только после `Log.d` в `SharedPositionStreamManager` и `updatePositionPnl` увидел: `portfolioPositions` пуст, потому что `replay = 0`. Правильный порядок сэкономил бы 3-4 итерации.

**Проверка:** `Log.d` в 4 точках (`emit`, `collect`, `_uiState.update`, `LaunchedEffect`) — смотреть, где обрывается.

### 32. `MutableSharedFlow(replay = 0)` не отдаёт текущее состояние

**Правило:** если на Flow подписываются **после** первой эмиссии (восстановление состояния, вход на экран, переподключение) — `replay = 1`. `replay = 0` даёт только новые эмиссии; «пустое» состояние до следующего тика.

**Инцидент:** 2026-10-28, `SharedPositionStreamManager`. После перезапуска приложения выбранный инструмент восстанавливался, но позиции не заполнялись, пока не придёт следующий poll (10 с). `replay = 0` — первый снапшот терялся для новых подписчиков. После `replay = 1` — данные приходят сразу.

**Проверка:** `grep -n "MutableSharedFlow.*replay" app/src/main/java/com/example/tscalp/data/api/SharedPositionStreamManager.kt` — `replay = 1`.

---

### 46. Удаление fat-метода — проверять supertype реализаций

**Правило:** перед удалением метода из интерфейса — `grep -rn "override.*fun <method>" app/src/main/kotlin/**/data` — какие реализации есть. Если брокер не имеет supertype для этого метода (роль `= null`) — override станет orphan (`NOTHING_TO_OVERRIDE`).

**Инцидент:** 2026-10-28, fat-part. Удалил fat из `BrokerApi`, оставил `override fun openSandboxAccount` в BCS/Finam (supertype `SandboxApi` нет, `sandbox = null`). CI: 14+15 `overrides nothing`.

**Проверка:** `grep -E "^class \w+.*: .*Api" <broker>.kt` — supertype list. Override допустим только для supertype-ролей.

### 47. `grep -c` на структурных маркерах — ложные тревоги

**Правило:** `grep -c` — для точного сравнения с ожидаемым; для «есть/нет» — `grep -q`. Для классов/интерфейсов — `grep -nE '^class \w+'` + `head`. `@Suppress` в KDoc ≠ `@Suppress` на классе — считать с `^@Suppress`.

**Инцидент:** 2026-10-28. `grep -c "@Suppress"` вернул 1 — был в KDoc `@Suppress снят`. `grep -cE "^    (suspend )?fun "` вернул 3 — были role overrides. `ls | grep -cE '^PortfolioApi'` вернул 0 — глюк в Termux-контексте.

**Проверка:** `^`-anchor, `grep -n`, смотреть на контекст.

### 48. Import-order чекер — `java`/`javax`/`kotlin` в конце

**Правило:** для проверки порядка — ktlint-правило (`java`, `javax`, `kotlin` в конце), не чистый алфавит. `grep -E "^import " file | awk 'NR>1 && $0<prev'` — неверно, `javax` лексикографически раньше `kotlinx`, но по правилу — позже.

**Инцидент:** 2026-10-28. Self-check показал `FAIL: kotlinx.coroutines.withContext > javax.inject.Inject` — ложная тревога, ktlint-стандарт `javax` в конце.

**Проверка:** для быстрой сверки — `ktlintFormat` локально или полагаться на CI.


## Clean-audit

### 20. Инвентаризация не пропускает «уже известное»

**Правило:** каждая находка (нарушение Clean, presentation-текст в нижнем слое, god-класс, `@Suppress` как маркер долга, swallowed exception, dead code, дублирование) заводится отдельной записью в `docs/clean-audit.md`. Существование задачи в ROADMAP/ISSUES не повод не заводить запись — наоборот, ссылка на задачу в поле «Ссылка на задачу:».

**Инцидент:** 2026-10-27, по ходу clean-audit я 13 раз сказал «уже в ISSUES/ROADMAP, не заводим новую запись». Пользователь: «Э! Отмена всем ранее бывшим в сессии "не заводим новые"... Нельзя на этом основании делать дыру в инвентаризации!». Backfill Н58-Н70 — 13 записей. Правило в AGENTS 2c добавлено (коммит `c9468c3`).

**Проверка:** `grep -n "не заводим\|уже известно\|не новая находка" docs/clean-audit.md` — пусто. `grep -c "^### Н" docs/clean-audit.md` — число должно совпадать с ожидаемым.

---

## UI и дизайн

### 39. Дизайн/стили/иконки — только по явному указанию

**Правило:** любое изменение внешнего вида (цвета, шрифт, отступы, иконки, размеры, порядок элементов) — только по прямому запросу. Не «улучшать» дизайн при рефакторинге, не «приводить к единому стилю» без запроса. Всё, что уже есть, — сохранять.

**Инцидент:** 2026-10-28, при унификации `OrderCard` самовольно: (1) заменил иконку «корзина» (`Icons.Default.Delete`) на крестик (`Icons.Default.Close`); (2) убрал `verticalScroll` в `OrdersListDialog`. Пользователь: «старый значок с мурорной корзиной верни. Опять меняешь дизайн сам по-тихому????» и «Вертикальную прокрутку забыл? Все что уде есть - надо оставлять».

**Проверка:** `git diff` перед коммитом UI-файлов. Если в diff есть изменения, которых не было в запросе — откатить до коммита.

---

## CI

### 8. `feature/**` push → CI запускается сразу

**Правило:** после push в feature — смотреть CI на коммите. Не
нужен PR, чтобы увидеть `gate`.

**Проверка:** `.github/workflows/ci.yml` → `on.push.branches` содержит
`'feature/**'`.

### 9. Порядок падений CI: compile → ktlint → detekt

**Правило:** если несколько ошибок — чинить в порядке
compile → ktlint → detekt, не смешивать. Сначала «чтобы
скомпилировалось», потом «стиль».

**Инцидент:** H23 — 4 волны CI-падений, в каждой одна группа.

**Проверка:** вывод CI, `Task :app:... FAILED`.

### 10. `paths-ignore` — docs-only не запускает CI

**Правило:** docs-only коммиты (`.md`, `docs/**`, `LICENSE`) не
тратят Actions-минуты и не показывают красный. Не паниковать,
что «CI не запустился».

**Проверка:** `.github/workflows/ci.yml` → `on.push.paths-ignore`.

### 11. HTML-репорт ktlint на CI — недоступен с телефона

**Правило:** вывод в консоли CI содержит `file:line:col` — читать
консоль, не html.

**Проверка:** `Task :app:ktlintTestSourceSetCheck FAILED` и строки
с `file.kt:line:col: Rule`.

### 33. Матрица AGP ↔ Gradle ↔ Hilt — точные версии

**Правило:** AGP 9.0.0 → Gradle **9.1.0** (`bin.zip` существует на services.gradle.org; 9.1.1 — нет). Hilt 2.59.2+ требует AGP ≥ 9.0.0. Hilt 2.51.1 — совместим с AGP 8.x. При обновлении любого звена — сверять матрицу.

**Инцидент:** 2026-10-28. AGP 9.0.0 + Gradle 9.1.1 — `Not Found` по URL `gradle-9.1.1-bin.zip`. AGP 8.13.2 + Hilt 2.59.2 — `Hilt plugin requires AGP 9.0.0`. Пришлось перебирать версии. Дополнительно: с AGP 9.0 `kapt` несовместим со встроенным Kotlin (`android.builtInKotlin`), миграция на KSP обязательна.

**Проверка:** `grep -n "distributionUrl" gradle/wrapper/gradle-wrapper.properties`; `grep -n "hilt-android" build.gradle.kts app/build.gradle.kts`. URL дистрибутива — сверять с https://services.gradle.org/distributions/.

---

## Оценки

### 12. Scriptable-задачи — реальное время в 5–10× меньше оценки

**Правило:** `**Оценка:**` в ROADMAP — для человека-разработчика.
Для AI+скриптов реальное время обычно в 5–10 раз меньше. В
отчётах о работе — указывать реальное, не плановое.

**Инцидент:** H23 — оценка 35 → 70 → 90–100 мин, факт ~10–15 мин
wall time (без учёта round-trips в чате).

**Проверка:** при mark done — фиксировать план/факт.

---

## Процессные (для другой сессии)

- `docs(roadmap): start` идёт в `dev` до feature (AGENTS 5g.2).
- `git checkout dev` с незакоммиченными правками — только через
  stash (AGENTS 4f, инцидент `SettingsViewModel`).
- Правка AGENTS висит, PR merge — stash → checkout → merge → pop →
  commit (инцидент H21b post-PR).
- Post-PR чек-лист (AGENTS 5f) перед следующей задачей.
- `git fetch` перед `git rev-list --count dev...origin/dev`.

---


---

### 21. reset --hard — перечислить теряемые коммиты

**Правило:** перед `git reset --hard <target>` — явно перечислить коммиты, которые будут потеряны (`git log --oneline <target>..HEAD`). Не делать вслепую.

**Инцидент:** 2026-10-27, сессия clean-audit. Предложил `git reset --hard origin/dev` для отката расхождения, но не перечислил, что при этом потеряется и `35e2a88` (clean-audit.md, тоже локальный). Пользователь спросил: «Можно сделать 'git reset --hard 35e2a88'» — пришлось объяснять, что это не решает проблему с origin. Если бы сделал reset без уточнения — потерял бы clean-audit.

**Проверка:** `git log --oneline <target>..HEAD` в сообщении с предупреждением перед командой. Явный список теряемых коммитов.

---

### 22. В proot писать в $HOME, не /tmp

**Правило:** в proot-окружении (Termux) `/tmp` может быть недоступен для записи. Все backup-файлы, временные fragments — в `$HOME/tscalp-tmp/` или другой writable директории.

**Инцидент:** 2026-10-27, backup clean-audit перед reset. `git show 35e2a88:docs/clean-audit.md > /tmp/clean-audit.md.bak` → `Permission denied`. Файл не создан, `wc -l` упал. Пользователь переключил на `~/tscalp-tmp/`.

**Проверка:** `mkdir -p ~/tscalp-tmp && test -w ~/tscalp-tmp && echo writable` перед backup. Или проверить `test -w /tmp` заранее.

### 34. Миграция DI — поэтапно, компиляция между этапами

**Правило:** миграция на Hilt разбивается на шаги: (1) зависимости + `@HiltAndroidApp`; компиляция; (2) модули `DataModule`/`RepositoryModule`; компиляция; (3) одна ViewModel (`OrdersViewModel`); компиляция; (4) остальные VM по одной. Не мигрировать всё разом.

**Инцидент:** 2026-10-28. Попытка мигрировать `OrdersViewModel`, `PortfolioViewModel`, `OrdersListViewModel` одновременно — лавина ошибок (AGP vs Hilt, KSP vs kapt, `@AndroidEntryPoint`, `SharedPreferences` DuplicateBindings). Каждый следующий шаг требовал предыдущего. Пришлось откатываться и делать поэтапно.

**Проверка:** `./gradlew :app:compileDebugKotlin` после каждого шага. Не переходить к следующему, пока не зелёный.

### 35. Hilt: три аннотации — Application, Activity, ViewModel

**Правило:** при первом `hiltViewModel()` в Composable проверить все три: `@HiltAndroidApp` на Application, `@AndroidEntryPoint` на Activity, `@HiltViewModel` + `@Inject constructor` на ViewModel. Пропуск любой — краш `Given component holder class ... does not implement ... GeneratedComponent` или внедрение не работает.

**Инцидент:** 2026-10-28, после перевода `OrdersViewModel` на Hilt. Первый запуск — `IllegalStateException: Given component holder class com.example.tscalp.MainActivity does not implement interface dagger.hilt.internal.GeneratedComponent`. Пропустил `@AndroidEntryPoint`. Второй запуск — `API не инициализирован`: `DataModule` создавал `TInvestInvestService` без `initializeFromSettings()`, хотя токен был.

**Проверка:** `grep -rn "@HiltAndroidApp\|@AndroidEntryPoint\|@HiltViewModel" app/src/main/java/com/example/tscalp/` — все три присутствуют. Плюс проверить, что модуль-провайдер сервиса сам вызывает `initializeFromSettings()` (или `initialize()`), а не полагается на `Application.onCreate`.

### 36. `SharedPreferences` — один `@Provides` на всё приложение

**Правило:** `SharedPreferences` предоставляется `@Provides` ровно в одном Hilt-модуле. Два и более — `DuplicateBindings` на этапе KSP.

**Инцидент:** 2026-10-28, `SharedPreferences` в `DataModule` и `RepositoryModule`. KSP: `android.content.SharedPreferences is bound multiple times`. Пришлось удалять из `RepositoryModule`.

**Проверка:** `grep -rn "provideSharedPreferences" app/src/main/java/com/example/tscalp/di/` — один результат.

### 37. Рефакторинг ≠ изменение поведения

**Правило:** рефакторинг (переименование, извлечение, перенос) сохраняет поведение 1:1. Любое изменение поведения (даже «улучшение», «полировка», «оптимизация») — отдельный коммит с явным описанием. Не смешивать.

**Инцидент:** 2026-10-28, при рефакторинге `OrderCard` «улучшил» дизайн (иконка, скролл) — в итоге UI-баг с перекрытием оверлея. При полировке полей цены «улучшил» — сломал видимость цифр (не хватало высоты поля). Пришлось откатывать и делать отдельно.

**Проверка:** `git diff --stat` перед коммитом. UI-файлы + файлы логики в одном diff — разбить на два коммита.

### 38. «Обсуждаем» / «планируем» — не код

**Правило:** при формулировках «обсудим», «планируем», «какой план», «сначала обсудим» — выдать обсуждение/план **без кода** (даже если кажется, что код очевиден). Код — только после явного «приступаем», «готов», «давай код».

**Инцидент:** 2026-10-28, сессия рефакторинга. Пользователь: «Пока обсуждаем и планируем» — я выдал код. «Давай план» — снова код. Пользователь: «Мы же начинаем новый тикет, значит пока только обсуждаем и планируем. Такой порядок работы всегда».

**Проверка:** в ответе на «обсуждаем»/«планируем» — `grep -c '^\`\`\`' ответ` = 0 (нет code fence).

### 50. `git revert --no-commit` — не забыть `--continue`/`--abort`

**Правило:** после `git revert --no-commit` — либо `git revert --continue` (если конфликты разрешены), либо `git revert --abort` (отменить). Не оставлять репозиторий в состоянии reverting. `git revert --quit` — «выйти без отката» (если нужен промежуточный результат без commit).

**Инцидент:** 2026-10-28. `git revert --no-commit c4865a5` + `commit` вручную — `status` показал «You are currently reverting», git ждал `--continue`. `--quit` привёл в порядок.

**Проверка:** `git status` после revert. Если `reverting` — `--continue` / `--abort` / `--quit`.


## Применение уроков

Механизм — `AGENTS.md` → 4h «Проверка правок на соответствие
перед коммитом». Без 4h уроки — просто текст.
