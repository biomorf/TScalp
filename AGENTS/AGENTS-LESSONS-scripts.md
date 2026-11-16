<!-- LESSONS_SECTION_BEGIN: scripts -->
## Скрипты и правки

<!-- SUBSECTION_BEGIN: l-scripts-1 -->
___
### l-scripts-1 Grep по `main` и `test`

**Правило:** при добавлении обязательного параметра в конструктор —
`grep -rn "ClassName(" app/src` (включая `test`, не только `main`).

**Инцидент:** H23, `TInvestOrchestratorFallbackTest` — 5 callsites
в тестах, CI упал `NO_VALUE_FOR_PARAMETER`. В диагностике искал только
`app/src/main`.

**Проверка:** `grep -rn "ClassName(" app/src/main app/src/test`.

<!-- SUBSECTION_END: l-scripts-1 -->
<!-- SUBSECTION_BEGIN: l-scripts-2 -->
___
### l-scripts-2 `str.count` ≠ `grep -c`

**Правило:** перед `assert count == N` — прогнать `grep -n` и
пересчитать глазами, или явно различать строки и вхождения.

**Инцидент:** H23, `SettingsViewModel` — ожидал 6 `DebugConfig.`,
реально 9 (3 чтения + 3×2 в `_uiState.update`). `PriceFlowManager` —
ожидал 2, реально 3 (два обращения в строке 207).

**Проверка:** `grep -c` — строки, `grep -o | wc -l` — вхождения.
Смотреть, нет ли двух обращений в одной строке.

<!-- SUBSECTION_END: l-scripts-2 -->
<!-- SUBSECTION_BEGIN: l-scripts-3 -->
___
### l-scripts-3 Supertype в Impl → import Interface

**Правило:** при добавлении `: Supertype` в data-класс — сразу
добавить `import <path>.Supertype`. KSP-ошибка «ERROR supertype» —
всегда про импорт, не про видимость.

**Инцидент:** H21b, `SettingsRepositoryImpl`, `TradingStateRepositoryImpl`
— добавил `: Interface`, забыл импорт. KSP упал, Kotlin-компилятор
молчал до KSP (KSP падает первым).

**Проверка:** `grep -n "^import.*SupertypeName" file`.

<!-- SUBSECTION_END: l-scripts-3 -->
<!-- SUBSECTION_BEGIN: l-scripts-4 -->
___
### l-scripts-4 `internal` + `public @Binds` = EXPOSED_PARAMETER_TYPE

**Правило:** если реализация `internal`, то `@Binds`-функция
тоже `internal abstract fun`. Альтернатива — `@Provides` в
`DataModule` (параметр не нужен, только return type).

**Инцидент:** H23, `bindDebugFlagsRepository(impl: DebugConfig)` —
`public abstract fun` в `public abstract class` не может принимать
`internal` параметр. Kotlin-компилятор упал после KSP.

**Проверка:** на CI (локально на телефоне не видно). При
`internal class` + `@Binds` — делать `internal abstract fun` сразу.

<!-- SUBSECTION_END: l-scripts-4 -->
<!-- SUBSECTION_BEGIN: l-scripts-5 -->
___
### l-scripts-5 `mockk(relaxed = true)` — импорт типа не нужен

**Правило:** при `mockk(relaxed = true)` в аргументе конструктора
тип выводится из параметра, импорт интерфейса не нужен. Импорт
нужен только при явном `mockk<Type>()`.

**Инцидент:** H23, `PriceFlowManagerTest` — вставил
`import DebugFlagsRepository`, ktlint упал `UnusedImport`.

**Проверка:** после `mockk(...)` в аргументе — не добавлять импорт;
если добавил — `ktlintCheck` поймает.

<!-- SUBSECTION_END: l-scripts-5 -->
<!-- SUBSECTION_BEGIN: l-scripts-6 -->
___
### l-scripts-6 Порядок импортов — по алфавиту, всегда

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

**Расширение (2026-11-07, M013 step 4b, `17512d4`):** замена
import in-place (`api.repository.OrdersRepository` →
`usecases.CancelOrderUseCase` на той же строке) **не работает**
при смене группы: `domain.api.repository.*` идёт до `domain.log.*`,
`domain.models.*`, `domain.usecases.*` — новая позиция **ниже**.
Правильно: удалить старый import, вставить новый **в группу
`usecases.*`** (по алфавиту), не на позицию старого. Гейт упал
на `ktlintMainSourceSetCheck: Imports must be ordered in
lexicographic order` (3:1). `ktlintFormat` (урок 55) автофиксит —
но лучше сразу вставлять в правильную группу. То же — при
**переносе класса** между пакетами (`api/` → `usecases/`).

<!-- SUBSECTION_END: l-scripts-6 -->
<!-- SUBSECTION_BEGIN: l-scripts-7 -->
___
### l-scripts-7 `grep -v` фильтр в verify может врать

**Правило:** после verify с `grep -v` — отдельно посмотреть
«что осталось» и глазами классифицировать (KDoc / historical /
actual). Не доверять фильтру.

**Инцидент:** H21b-финал, `grep "InvestRepository" | grep -vE "..."` —
не отфильтровал `BrokerApi.kt` (`domain/api/`, не `domain/api/repository/`)
и «удалён». Ложный FAIL.

**Проверка:** запустить без фильтра, посмотреть полный вывод,
классифицировать каждую строку.

---

<!-- SUBSECTION_END: l-scripts-7 -->
<!-- SUBSECTION_BEGIN: l-scripts-23 -->
___
### l-scripts-23 `text.find("### Header")` находит inline-ссылку

**Правило:** для поиска заголовка в `.md` — `re.search(r"^### Header$", text, re.MULTILINE)`, не `text.find("### Header")`.

**Инцидент:** 2026-10-23, mark done `StreamState`. `text.find("### Унификация")` нашёл не заголовок, а inline-ссылку `` `### Унификация` `` в блоке «Связано» предыдущей задачи. `end_idx` оказался до `start_idx`, `rfind` в диапазоне вернул `-1`. Скрипт упал на `assert closing separator not found`. То же самое — при mark done `Unification` и `TInvest-specific` (ссылки `### StreamState` в тексте).

**Проверка:** перед `find` — `grep -n "^### Header$" file.md`. Или сразу `re.search(r"^### Header$", text, re.MULTILINE)`.

**Дополнение (2026-10-29, повтор):** то же касается `text.replace("### X", ...)` и `re.sub("### X", ...)` без `^...$`. Первое вхождение может быть **inline-ссылкой** (`` `### X` ``) в другом блоке, не заголовком.

**Инцидент (2026-10-29, повтор):** дважды за сессию. Mark done P&L: `str.replace("### Рефакторинг расчёта P&L", "### ✅ Рефакторинг расчёта P&L", 1)` — первое вхождение оказалось inline-ссылкой в H5 → заголовок P&L склеился с inline-контекстом, `git diff` показал `+6223/−6156`. То же для H17–H20. Урок 23 был, но не применён — формулировка говорила про `find`, не про `replace`/`re.sub`.

**Проверка (replace/re.sub):** для правки заголовка — `re.sub(r"^### X$", ..., flags=re.MULTILINE)` или **line-based slicing** (список строк, `startswith` + `find next header`). Позиция — `grep -n "^### X$" file.md` (ровно 1).


---

<!-- SUBSECTION_END: l-scripts-23 -->
<!-- SUBSECTION_BEGIN: l-scripts-24 -->
___
### l-scripts-24 `count("**Marker:**") == 1` ложно-отрицателен для общих маркеров

**Правило:** post-write `assert` не проверяет `count == 1` для маркеров, которые есть в каждой задаче ROADMAP (`**Что сделано:**`, `**Референсы:**`, `**Оценка:**`, `**Шаги:**`, `**Файлы:**`, `**Статус:**`, `**Контекст:**`). Использовать `>= 1` + отдельная проверка на дубль внутри *своей* секции (между `### TaskA` и `### TaskB`).

**Инцидент:** 2026-10-23/24, ~6 падений. `assert check.count("**Что сделано:**") == 1` — реально **7** (по числу закрытых задач). `**Референсы:**` — **14**. `**Оценка:** ~35–45 мин` — тот же формат был в другой задаче. `**Находки вне scope:**` — уникальный, прошло. Каждый раз файл корректен, assertion — ложный.

**Проверка:** `grep -c "^\*\*Marker:\*\*$" docs/ROADMAP.md` перед `assert count == 1`. Если > 1 — `text.count(marker, start, end)` в узком диапазоне или unique content-marker.

---

<!-- SUBSECTION_END: l-scripts-24 -->
<!-- SUBSECTION_BEGIN: l-scripts-26 -->
___
### l-scripts-26 `assert "old_name" not in text` — KDoc содержит старое имя

**Правило:** после rename — проверять `count(new_name) >= expected`, не `old_name not in text`. Старое имя легитимно остаётся в KDoc, в ссылках на задачи, в исторических цитатах.

**Инцидент:** 2026-10-23, `PriceFlowManager` после `runGrcpStream → collectStream`. `assert "broker.pricesSnapshot" not in check` упало — `broker.pricesSnapshot` есть в KDoc строк 46 и 77 («подписчик подтягивает через `broker.pricesSnapshot`»), это описание абстракции, не код. Аналогично `as? TInvestBrokerAPI` в KDoc-цитатах, `runGrcpStream bypassed`.

**Проверка:** `grep -n "old_name" file | grep -v "^\s*\*"` — отфильтровать KDoc. Или проверять `text.count(": " + new_name)` / `text.count("?." + new_name)` — в коде, не в комментариях.


**Расширение (2026-11-06, M006 steps 4, 5, 6, 13):** ещё 3 инцидента с подстрокой в идентификаторе:
- `assert "SettingsViewModel" not in block` ловит `TInvestSettingsViewModel` (step 4) — panel-VM содержит имя базового класса как суффикс.
- `assert "SettingsViewModel" not in info_block` ловит `InfoSettingsViewModel` (step 13) — тот же паттерн, забыл после step 4.
- `assert "Dispatchers.IO" not in text` ловит KDoc `(нет Dispatchers.IO)` (step 8, MainDispatcherRule) — мой KDoc-комментарий содержит то, что я же проверяю.
- `assert count("reapplyDebugFlags()") == 3` ловит упоминание в KDoc + 3 в коде = 4 (step 7.5, ToggleDebugFlagUseCase).

**Правило (расширено):** в `assert X not in text` для новых имён классов, которые содержат в себе старый класс как суффикс/префикс (`XSettingsViewModel` ⊃ `SettingsViewModel`), проверять уникальный контекст (`" $X"`, `"\n$X("`, `" $X("`). Либо — `code-only` check (`grep -v '^\s*\*'`). Особенно — когда новое имя класса само является расширением старого (`TInvestSettingsViewModel` от `SettingsViewModel`).

---

<!-- SUBSECTION_END: l-scripts-26 -->
<!-- SUBSECTION_BEGIN: l-scripts-27 -->
___
### l-scripts-27 Голый `assert` в скрипте — нет контекста падения

**Правило:** каждый `assert` — с сообщением `assert X, "what was expected"`. Голый даёт `AssertionError` без указания, что не прошло.

**Инцидент:** 2026-10-23/24, ~6 раз. Скрипт с 15+ assert'ами, падение на 115-й или 233-й строке — какой именно? Traceback `AssertionError` без условия. Приходилось перезапускать диагностику через `grep`. Пользователь: «без сообщения не знаю, какой assert упал».

**Проверка:** `grep -c "^    assert " script.py` vs `grep -c "^    assert .*, \"" script.py` — должны совпадать. Или `grep -n "assert [^,]*$" script.py` — пусто.

---

<!-- SUBSECTION_END: l-scripts-27 -->
<!-- SUBSECTION_BEGIN: l-scripts-28 -->
___
### l-scripts-28 `write_text` до post-write assert — файл уже изменён

**Правило:** `write_text` — точка невозврата до `git checkout`. При падении post-write assert файл **уже изменён**. Диагностика — `git diff` + `git status`, не «откатить и перезапустить». Остановка перед коммитом — правильно; остановка перед диагностикой — нет.

**Инцидент:** 2026-10-23, ~5 раз. Скрипт: `write_text` → `assert` → `print("applied")`. Assert падал (#27). Файл изменён, не закоммичен, `print` не выполнен. Терминал показывает «applied не пришло» — приходится проверять `git diff --stat` и решать, применять файл или откатывать. Правильный паттерн: `assert` до `write_text` (пре-проверка), `write_text`, `assert` после (пост-проверка), **каждый** с сообщением.

**Проверка:** структура `pre-assert → write_text → post-assert`. Все с сообщениями. При падении post-assert — сразу `git diff file`, не паника.

---

<!-- SUBSECTION_END: l-scripts-28 -->
<!-- SUBSECTION_BEGIN: l-scripts-13 -->
___
### l-scripts-13 Anchor в скрипте — grep перед написанием

**Правило:** перед написанием anchor в скрипте (для замены/вставки) — взять точный текст из файла через `grep -n -A5` на смежный маркер, не по памяти и не по KDoc.

**Инцидент:** 2026-10-27, clean-audit L7. Скрипт вставки `**Анализ L7-part1:**` в Н22 упал на `assert anchor_ref22 in text`. Я использовал anchor `Ссылка на задачу: — (не заведена). Связано: ### Broker lifecycle API`, а фактический текст в Н22 был без `Связано:`. Причина: составлял anchor по воспоминанию о KDoc, а не по текущему файлу. Скрипт упал до `write_text` — файл не изменён, но потрачен round-trip.

**Проверка:** `grep -n -A5 "**Ссылка на задачу:**" docs/clean-audit.md | head -30` перед написанием скрипта. Или `awk '/^### Н22\./,/^### Н23\./' file`.

---

<!-- SUBSECTION_END: l-scripts-13 -->
<!-- SUBSECTION_BEGIN: l-scripts-14 -->
___
### l-scripts-14 Fence вложенного блока — 4 backticks или `~~~`

**Правило:** если правка (или fragment для вставки) содержит markdown-разметку с тройными backticks — обернуть внешним блоком из 4+ backticks (или `~~~`). Три backticks внутри трёх — ломают fence.

**Инцидент:** 2026-10-27, подготовка fragment `L6-part2-p1.md`. Внешний блок в чате был из 3 backticks, внутри — markdown с ```python. Fence рассыпался, рендер сломался. Пользователь: «Внешний fence у блока рассыпался».

**Проверка:** перед отправкой code-блока — механически посмотреть на содержимое. Есть 3+ backticks подряд? Если да — обёртка `~~~` или 4+ backticks.

**Инцидент-счётчик (2026-11-08, Session Orders-VM-Split follow-up, 1 нарушение):**
D4 (architecture.md 4-VM), первый apply-скрипт: внешняя обёртка `~~~bash`, внутри
`~~~mermaid` — тильды **одного уровня** рассыпались, пользователь: «fence блока
рассыпался». Фикс — внешняя обёртка **4 backticks** (````bash … ````). Это 2-й
инцидент после `L6-part2-p1.md` (2026-10-27). Правило не соблюдается на практике:
для скриптов, содержащих `~~~` **внутри**, внешняя обёртка — 4 backticks, не `~~~`.


---

<!-- SUBSECTION_END: l-scripts-14 -->
<!-- SUBSECTION_BEGIN: l-scripts-15 -->
___
### l-scripts-15 Серия скриптов на один файл — по одному, `git diff` после каждого

**Правило:** если правка файла разбита на серию последовательных python-скриптов, запускать по одному, после каждого — `git diff --stat` и верификация. Не выдавать все скрипты сразу.

**Инцидент:** 2026-10-27, серия скриптов для ROADMAP (umbrella, задачи, BCS, Codecov, ISSUES). Скрипт 3 (BCS) упал на `assert detekt != -1`, но до этого скрипты 1-2 выполнились в памяти. Пользователь запустил всё как один блок, при падении 3 — 1-2 уже записали файл, но не факт что правильно. Пришлось диагностировать через grep отдельно.

**Проверка:** при серии из N>1 скриптов на один файл — один shell-блок на скрипт, `git diff --stat` после каждого.

---

<!-- SUBSECTION_END: l-scripts-15 -->
<!-- SUBSECTION_BEGIN: l-scripts-16 -->
___
### l-scripts-16 Скрипт + верификация в одном блоке

**Правило:** скрипт правки и verification-команды — один shell-блок. Пользователь запускает один раз, видит результат применения и результат проверки. Не два раунда «пришли вывод → получи verify».

**Инцидент:** 2026-10-27, ~6+ раз за сессию скрипт и верификация выдавались раздельно. Каждый раз — два round-trip. Двойной лаг в proot.

**Проверка:** `grep -c "^```" ответ` — чётное, скрипт и verify внутри одного fence. Визуально: один markdown-блок `bash` содержит и `python3 <<` heredoc, и `grep`, и `git diff --stat`.

<!-- SUBSECTION_END: l-scripts-16 -->
<!-- SUBSECTION_BEGIN: l-scripts-29 -->
___
### l-scripts-29 Не изобретать — grep перед использованием

**Правило:** перед использованием переменной/поля/функции в предложении и перед правкой файла — `grep -n` фактическое объявление в актуальном исходнике. Не полагаться на память о прошлой сессии, на свой предыдущий ответ, на «логично же».

**Инцидент:** 2026-10-28, миграция `app/build.gradle.kts` на Kotlin DSL. Предложил `versionName = "1." + buildTimestamp` — переменной нет, есть `buildTime()`. Пользователь: «А почему ты думаешь, что у меня есть переменная buildTimestamp?». Затем — `buildConfigField("String", "BUILD_TIME", ...)` — не нужно, есть стандартный `BuildConfig.VERSION_NAME`. Аналогично при рефакторинге `OrdersScreen` несколько раз «читал» файл и всё равно предлагал правки по прежней версии.

**Проверка:** `grep -n "<symbol>" path/to/file` перед предложением. Если грепа нет — символ не существует. Перед правкой — `git log --oneline -1 -- path/to/file`: если коммит новее, чем «знание» — запросить свежий raw.

<!-- SUBSECTION_END: l-scripts-29 -->
<!-- SUBSECTION_BEGIN: l-scripts-30 -->
___
### l-scripts-30 `return@label` требует наличия label в области видимости

**Правило:** перед `return@let` / `return@run` / `return@launch` — проверить, что вокруг действительно есть соответствующая обёртка. Ошибка `Unresolved label` — не про скобки, а про отсутствующий label.

**Инцидент:** 2026-10-28, `OrdersViewModel.postOrder`. Внутри `try { }` написал `return@let` — обёртки `let` не было. Kotlin: `Unresolved label` на двух строках. Правильный exit — `if (stopPrice == null || stopOrderType == null) { ... } else { ... }`, без метки.

**Проверка:** `grep -n "X\s*{" file` в диапазоне 20-30 строк выше — найти `let {` / `run {` / `launch {`. Если нет — `return` без метки или добавить обёртку.

---

---

<!-- SUBSECTION_END: l-scripts-30 -->
<!-- SUBSECTION_BEGIN: l-scripts-40 -->
___
### l-scripts-40 `git add -A` — источник инцидента

**Правило:** только `git add <files>` с явным списком (правило 4g, без исключений). Перед `git commit` — `git diff --cached --name-only`, сверить с ожидаемым. Лишний файл → `git restore --staged <file>`.

**Инцидент:** 2026-10-28, fat-part. 4 коммита с `git add -A` (`a1c119b`, `a1fc32c`, `c4865a5`, `f76ba50`). В `72c28f3` захватил untracked `.tmp` (mv упал молча), `.kt` не изменились → CI упал на тех же ktlint. Revert + fix.

**Проверка:** `git diff --cached --name-only` перед коммитом.

<!-- SUBSECTION_END: l-scripts-40 -->
<!-- SUBSECTION_BEGIN: l-scripts-41 -->
___
### l-scripts-41 `mv` без `-f` на телефоне — зависание на `overwrite?`

**Правило:** `mv -f` (или `mv -n`). `mv` без флага интерактивен, если файл существует.

**Инцидент:** 2026-10-28, `.tmp` → `.kt`. `mv` спросил `overwrite?` на два файла, Ctrl+C — оба `mv` не выполнились. Команда зависла в ожидании ввода.

**Проверка:** `mv -f` всегда. Защита от перезаписи — `mv -n` + `echo "skipped"`.

<!-- SUBSECTION_END: l-scripts-41 -->
<!-- SUBSECTION_BEGIN: l-scripts-42 -->
___
### l-scripts-42 `.tmp + mv` — коммит может захватить `.tmp`

**Правило:** не использовать `.tmp`-файлы для правок. `write_text` сразу в целевой файл, `assert` до записи и после.

**Инцидент:** 2026-10-28, fat-part. `cp → sed .tmp → mv` — `mv` упал (overwrite?), `git add -A` захватил `.tmp`, `.kt` не изменились. `72c28f3` = 445 строк `.tmp`, CI красный.

**Проверка:** `git diff --cached --name-only` не содержит `*.tmp`. Правка через `write_text` + `assert` — атомарная.

<!-- SUBSECTION_END: l-scripts-42 -->
<!-- SUBSECTION_BEGIN: l-scripts-43 -->
___
### l-scripts-43 Heredoc на телефоне — риск потери `MSGEOF`

**Правило:** commit message через `printf '%s\n' ... > "$HOME/.msg.txt" && git commit -F "$HOME/.msg.txt"`. Heredoc (`git commit -F - <<'MSGEOF'`) — только на десктопе.

**Инцидент:** 2026-10-28. При вставке heredoc на телефоне потерялась закрывающая `MSGEOF` → `git commit` ждал ввода бесконечно.

**Проверка:** в блоке коммита — `printf`, не `<<`. Если heredoc неизбежен — `set -e` + `echo "heredoc OK"` после закрывающей метки.

<!-- SUBSECTION_END: l-scripts-43 -->
<!-- SUBSECTION_BEGIN: l-scripts-44 -->
___
### l-scripts-44 `set -e` в блоках с несколькими командами

**Правило:** каждый блок, где несколько команд пишут в файл / меняют состояние, начинается с `set -euo pipefail` (или `&&`-цепочка).

**Исключение — захват exit code.** Если блок должен перехватить код возврата (`GATE=$?` после `./gradlew`), начать с `set -uo pipefail` **без** `-e` (иначе падение команды завершает `bash` до `GATE=$?`, вывод `exit=` теряется). Сразу после `GATE=$?` — `set -e`: остальная часть блока (коммит, push) под защитой, как обычно.

**Инцидент:** 2026-10-28, fat-part. Блок `mv .tmp .kt` + `git add -A` + `commit` — `mv` упал, `set -e` не было, скрипт продолжился. Коммит создан, `.tmp` в индексе.

**Проверка:** `set -e` в шапке. `echo "exit: $?"` после критичной команды.

<!-- SUBSECTION_END: l-scripts-44 -->
<!-- SUBSECTION_BEGIN: l-scripts-45 -->
___
### l-scripts-45 Парсер multi-line overrides — brace-balance, не regex

**Правило:** удаление методов в Kotlin — через brace-balance по строкам (inline ` = value` → 1 строка; block `{...}` → баланс), не через regex `[^\n]*\n`. Перед удалением — `grep -n "override.*fun <name>"` + `sed -n 'N,Mp'` для границ.

**Инцидент:** 2026-10-28, fat-part. Regex `override fun (\w+)\([^\n]*\n` матчил только первую строку multi-line сигнатуры → orphan bodies (BCS/Finam, ~200 syntax errors). Обратный случай: ` = emptyFlow()` inline, но с `{` в generic — parser искал следующий `{` = тело следующего метода → удалил живую роль.

**Проверка:** `grep -n` + `sed -n 'N,Mp'` для границ. Brace-balance + inline-детект.

<!-- SUBSECTION_END: l-scripts-45 -->
<!-- SUBSECTION_BEGIN: l-scripts-49 -->
___
### l-scripts-49 Не писать в `.tmp` до полной проверки всех `.tmp`

**Правило:** при обработке нескольких файлов — сначала все `write_text` в `.tmp` (без mv), проверка (brace, counts), только потом `mv -f` каждого. Никаких промежуточных `mv` до полной проверки.

**Инцидент:** 2026-10-28. Один блок: `sed BCS.tmp`, `mv BCS.tmp BCS`, `sed FIN.tmp` (упал) — BCS уже перезаписан, откатывать сложнее.

**Проверка:** структура `copy → mutate .tmp → check → mv -f → check`.

<!-- SUBSECTION_END: l-scripts-49 -->
<!-- SUBSECTION_BEGIN: l-scripts-51 -->
___
### l-scripts-51 `bash -c '...'` ломается от одинарных кавычек в содержимом

**Правило:** не оборачивать блок в `bash -c '...'`, если внутри есть хоть одна одинарная кавычка. Она закроет обёртку, остаток станет командами bash. Использовать `bash << 'EOF' ... EOF` или `python3 << 'PYEOF'` напрямую, без `bash -c`.

**Инцидент:** 2026-10-29. Попытка вставить большой блок (правки `ROADMAP.md` + `architecture.md`) через `bash -c '... python3 << "PYEOF" ... PYEOF ...'`. Внутри были одинарные кавычки — одна закрыла `bash -c '`, весь markdown-контент стал командами bash (`### ...`, `**Тесты:**`, `OrderFlowManager: ...`). Сотни «command not found», `syntax error near unexpected token`. Файлы не пострадали (bash не пишет через `###`), но это прямое нарушение AGENTS 4e (правило 1: команда и пример — раздельно).

**Проверка:** перед отправкой блока — есть ли `bash -c '...'`? Если да — посчитать `'` внутри. Любая — опасно. Заменить на `bash << 'EOF'` или убрать `bash -c`.

---

<!-- SUBSECTION_END: l-scripts-51 -->
<!-- SUBSECTION_BEGIN: l-scripts-52 -->
___
### l-scripts-52 `write_text` в Python ломает line endings

**Правило:** перед `Path.write_text(..., encoding="utf-8")` для `.md` / `.kt` — проверить line endings. Если файл с CRLF — `newline=""` в `read_text`/`write_text` (сохраняет как есть). Иначе весь файл «изменится» (universal newlines → LF), `git diff` покажет `+N/−N` вместо локальной правки.

**Инцидент:** 2026-10-29. `write_text` над `ROADMAP.md` — `git show --stat` показал `+6223/−6156`. Первая гипотеза — line endings. `od -c` показал LF (в этом случае CRLF не было; настоящая причина — inline-replace, урок 23). Риск реальный: если файл CRLF, `write_text` его перепишет.

**Проверка:** `python3 -c "import sys; sys.exit(0 if b'\r\n' in open('file','rb').read() else 1)"`. Если CRLF — `newline=""`.

---

<!-- SUBSECTION_END: l-scripts-52 -->
<!-- SUBSECTION_BEGIN: l-scripts-53 -->
___
### l-scripts-53 `git diff --cached --stat` перед коммитом

**Правило:** после `git add <file>` и **до** `git commit` — `git diff --cached --stat <file>`. Сверить числа с ожидаемыми. Аномалия (`+6000/−6000` на файл в 7000 строк, когда ожидается `+50/−40`) = что-то сломалось. Откат `git checkout -- <file>`, разбор, заново.

**Инцидент:** 2026-10-29. Коммиты mark done H20/H17–H20/P&L — `+6223/−6156`. Обнаружено **после** коммита (`git reset --mixed`, переделка). Должно было быть **до**.

**Проверка:** `git diff --cached --stat` — часть каждого блока коммита.

---

<!-- SUBSECTION_END: l-scripts-53 -->
<!-- SUBSECTION_BEGIN: l-scripts-54 -->
___
### l-scripts-54 Шаблон 4f блока 2 — не урезать grep-фильтр

**Правило:** при использовании шаблона 4f (блок 2, гейт+коммит+push) копировать grep-фильтр **целиком**, включая паттерн `\.kt:[0-9]+:[0-9]+`. Урезание скрывает ktlint-ошибки, которые пишутся как `path/to/file.kt:line:col: Rule`.

**Инцидент:** 2026-10-30, PR #71. Блок 1 (гейт) — `grep -E '^w:|^e:|\[warn\]|\[error\]|FAILED|BUILD'` без `\.kt:` паттерна. `:app:ktlintMainSourceSetCheck FAILED` на `TInvestBrokerApi.kt:41:1 an EOL comment may not be preceded by a KDoc` — эта строка в фильтр не попала, ошибка пропущена. Спасла `tail -3` и повторный grep.

**Проверка:** сверить `grep -E` паттерн с шаблоном 4f посимвольно — все альтернативы (`\.kt:`, `\[warn\]`, `^w:`, …) на месте.

---

<!-- SUBSECTION_END: l-scripts-54 -->
<!-- SUBSECTION_BEGIN: l-scripts-55 -->
___
### l-scripts-55 На desktop — `./gradlew :app:ktlintFormat`, не ручной перенос импортов

**Правило:** при ktlint-ошибке про порядок импортов (или других автофиксируемых нарушений) — на desktop запускать `./gradlew :app:ktlintFormat`, а не править импорты вручную. Форматтер использует `.editorconfig` проекта и расставляет импорты по ktlint-правилам (`java`, `javax`, `kotlin` в конце). Ручная правка — двойная работа и риск повторить ошибку.

**Инцидент:** 2026-10-30, KMP phase 1 (#3 Clock). Ручная правка импортов дважды: (1) `kotlin.time.Clock` вставлен после `kotlinx.coroutines.*` вместо после `java.util.*`; (2) `kotlin.time.*` в `StreamStateTrackerTest` вставлены не в конец. Два падения `ktlintTestSourceSetCheck` — два `fix`-цикла. Пользователь: «Неужели нет автоматической сортировки импортов?» — да, `ktlintFormat`, 2 секунды.

**Проверка:** `./gradlew :app:ktlintFormat` → `git diff` (убедиться, что форматтер не сделал лишнего) → повторный гейт.

---

<!-- SUBSECTION_END: l-scripts-55 -->
<!-- SUBSECTION_BEGIN: l-scripts-56 -->
___
### l-scripts-56 `assert count == 1` для имени задачи — только line-anchored; структурные проверки — awk, не grep-контекст

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

<!-- SUBSECTION_END: l-scripts-56 -->
<!-- SUBSECTION_BEGIN: l-scripts-57 -->
___
### l-scripts-57 JVM/JUnit API-ограничения: проверять до первого запуска

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


**Расширение (2026-11-06, M006 step 3.5):** ещё один инцидент с (б):
- `DisconnectBrokerUseCaseTest`: имена `TInvest full: clear, tradingState, deinit, stop managers, refresh`, `BCS: clear, deinit, refresh; no tradingState, no stop`, `Finam: clear, deinit, refresh; no tradingState, no stop` — компилятор: `e: Name contains illegal characters: :.` и `;:`. Список запрещённых символов в (б) был неполным (только `.`), теперь — полный JVM-spec.

**Правило (расширено):** в именах backtick-функций JUnit — **не использовать** `:`, `;`, `[`, `]`, `/`, `<`, `>`, `.`. Замены: ` -` (dash-space) и `,` (запятая) — безопасны.

---



<!-- SUBSECTION_END: l-scripts-57 -->
<!-- SUBSECTION_BEGIN: l-scripts-58 -->
___
### l-scripts-58 post-assert count — считать по факту диагностики, не по «логической» сумме

**Правило:** перед `assert total == N` в скрипте правки — пересчитать `N` по фактическому выводу диагностики (`grep -n` / `grep -c`), а не по «ожиданию» из плана. Диагностика уже была перед глазами — из неё число берётся однозначно.

**Инцидент:** 2026-10-31, пакет B (Н68). Дважды упал на post-assert `total`:
- `SettingsViewModel`: `assert total == 19`, реально **21** (12 credentials + 3 account + 3 sandbox + 3 trading). Число 19 — из «плана», не из `grep`.
- `BrokerLifecycleManagerTest`: `assert total == 9`, реально **12** (6 `loadBrokerCredentials` + 5 `loadDefaultAccountId` + 1 `saveDefaultAccountId`).

Оба раза `write_text` не выполнялся (assert до записи), файлы не пострадали. Но каждый раз — лишний round-trip: пользователь присылал вывод диагностики, я игнорировал фактические числа и брал «ожидаемое».

**Проверка:** в скрипте правки рядом с `assert total == N` — `counts` печатается в лог, и `N` пересчитывается перед отправкой по выводу диагностики. `grep -c "settingsRepository\." file` — контрольная сумма до правки.

<!-- SUBSECTION_END: l-scripts-58 -->
<!-- SUBSECTION_BEGIN: l-scripts-59 -->
___
### l-scripts-59 Откат блока в `.kt`/`.md` — anchor на следующий член, не на `}`

**Правило:** при удалении/откате многострочного блока кода — правый якорь — **имя следующего члена** (`private val`, `internal abstract fun`, `### Header`), а не символ `}` класса/файла. `}` после блока может быть не закрывающей скобкой класса, а частью следующего члена.

**Инцидент:** 2026-10-31, пакет B (Н68), откат `RepositoryBindingModule` после detekt-падения. Скрипт ждал `text[idx_end:idx_end+2] == "}
"` после последнего нового `@Binds` (`bindTradingPreferencesRepository`). Фактически после него шёл ещё один член класса — `internal abstract fun bindDebugFlagsRepository` (не закрывающая `}`). Assert упал, файл не изменён, лишний round-trip.

Правильный якорь — `"\n    @Binds\n    @Singleton\n    internal abstract fun bindDebugFlagsRepository"`, не `}`.

**Проверка:** перед откатом блока — `grep -n "abstract fun bind\|^    fun \|^    private val " file` — найти реальный следующий член. Якорь в скрипте — по его сигнатуре, не по `}`.

<!-- SUBSECTION_END: l-scripts-59 -->
<!-- SUBSECTION_BEGIN: l-scripts-60 -->
___
### l-scripts-60 detekt `TooManyFunctions` на DI-модуле — split на доменные модули, не `@Suppress`

**Правило:** при добавлении `@Binds`/`@Provides` в существующий модуль — считать **итоговое** число функций после **всех** шагов задачи (не только текущего коммита). Если итог > 11 и `@Suppress` не снимается никогда — разделить модуль на доменные (`RepositoryBindingModule` core vs `PreferencesRepositoryBindingModule`), а не ставить `@Suppress`.

**Инцидент:** 2026-10-31, пакет B (Н68). В `RepositoryBindingModule` добавил 4 `@Binds` для новых preferences-репозиториев → 13 функций, detekt `TooManyFunctions` (порог 11). Альтернатива `@Suppress("TooManyFunctions")` отклонена: после коммита 3 (`bindSettingsRepository` удаляется) остаётся 12 — всё равно > 11, `@Suppress` был бы **постоянным** костылём. Разбил на 2 модуля: `RepositoryBindingModule` (8) + `PreferencesRepositoryBindingModule` (4) — оба ≤ 11, без `@Suppress`.

**Проверка:** перед добавлением `@Binds` — `grep -cE "abstract fun bind" <module>.kt`, прибавить новые + учесть предстоящие удаления. Если итог > 11 и не снимается — split заранее, в том же PR.

<!-- SUBSECTION_END: l-scripts-60 -->
<!-- SUBSECTION_BEGIN: l-scripts-61 -->
___
### l-scripts-61 Двусторонняя проверка импортов после удаления кода

**Правило:** после удаления функций/секций — проверять импорты в **две стороны**: (а) каждый импорт используется в оставшемся теле; (б) каждый не-builtin символ в теле имеет импорт. Односторонняя проверка («все импорты используются») пропускает случаи, когда **нужный** импорт случайно удалён вместе с «ненужным».

**Инцидент:** 2026-10-31, шаг 6/8 split `TInvestMarketDataService` (`bc6757c`). Удалил импорт `CancellationException` как «unused» — он был нужен только в `subscribePositionsGrpcWithReconnect`, которая осталась в файле. `compileDebugKotlin` упал: `Unresolved reference 'CancellationException'` (MD:154). Pre-commit self-check проверял только «все импорты используются», но не «все символы в теле имеют импорт».

**Проверка:** в pre-commit по 4h для каждого изменённого `.kt`: (1) каждый импорт используется (>= 2 вхождений — import + тело); (2) после удаления любого импорта — `grep -n '<symbol>' <file>` без `^import` — если непусто, импорт **нельзя** удалять. Быстрый вариант: `./gradlew :app:compileDebugKotlin` до коммита.

**Расширение (2026-11-07, Session Orders-VM-Split, 13a):** обратная сторона — **использование extension в теле без импорта**. Создал `RestoreOrderFormUseCaseTest.kt` с `sber.moneyPerPointOfPrice` в трёх местах; импорт `import com.gitlab.biomorf.tscalp.domain.models.moneyPerPointOfPrice` не добавил (проверял использование в usecase-файле, не в тесте). Гейт упал: `Unresolved reference 'moneyPerPointOfPrice' on receiver of type 'Share'` в 3 строках (93, 123, 124) + `Cannot infer type for type parameter 'T'` как следствие.

**Правило (расширено):** при генерации тестового файла — проверять **использование extension в теле** vs **импорт в шапке** для каждого extension-символа. Для usecase проверка прошла (импорт есть), для теста — нет.

**Проверка:** для каждого extension-символа в тесте — `grep -cE "\.<extName>" <file>` vs `grep -cE "^import .*\.<extName>$" <file>` (равны или 0/0). Прецедент: 13a `sber.moneyPerPointOfPrice` в тесте без импорта — 3 compile-errors.

**Расширение (2026-11-07, Session Orders-VM-Split, 13d-F, 2-й инцидент):**
обратная сторона снова, но для **inject** и **типа из wildcard**. В Coord
13d-F добавлены `private val tradingStatusRepository: TradingStatusRepository`
и `val tradingStatuses: StateFlow<Map<String, TradingAvailability>>` — импорты
не добавлены. KSP упал: `InjectProcessingStep was unable to process
'OrdersViewModel(...)' because 'TradingStatusRepository' could not be resolved`.
`TradingAvailability` — из wildcard-импорта в `OrdersPairTrading.kt`
(`domain.models.*`), но `OrdersSearch.kt` использует explicit import.
Координатная state — `OrdersCoordState` (same-package), импорт не нужен.

**Правило (расширено):** при добавлении **нового inject-поля** или
**нового типа в public property** — проверить: (а) импорт типа добавлен;
(б) если тип из wildcard-пакета — либо wildcard, либо explicit import
(ktlint `no-wildcard-imports` не форсит, но и не запрещает); (в) типы
из того же package — без импорта.

**Проверка:** `grep -nE "^import .*<Type>" file` для каждого нового
inject/свойства. Плюс `./gradlew :app:kspDebugKotlin` (или full gate) до
коммита — KSP первым ловит missing imports.

<!-- SUBSECTION_END: l-scripts-61 -->
<!-- SUBSECTION_BEGIN: l-scripts-62 -->
___
### l-scripts-62 Import order после rename — лексикографически, не только «нет старого имени»

**Правило:** при переименовании класса (rename) — пересортировать блок `import` **заново**, не просто заменить строку `<Old> -> <New>` на месте. Лексикографический порядок может измениться: `TInvestPositionsService` (`Pos`) идёт **после** `TInvestPortfolioService` (`Por`), но `TInvestOrdersService` (`O`) — раньше обоих.

**Инцидент:** 2026-10-31, шаг 7/8 split (`9fb105c`). Заменил `import ...TInvestMarketDataService` -> `import ...TInvestPositionsService` на той же позиции — между `TInvestAccountsService` и `TInvestOrdersService`. `ktlintTestSourceSetCheck` упал: `Imports must be ordered in lexicographic order...` в `TInvestOrchestrator.kt` и `TInvestOrchestratorFallbackTest.kt`. Grep «нет старого имени» прошёл — старое имя действительно удалено, порядок сломан.

**Проверка:** после rename — `awk '/^import / { if (prev != "" && $0 < prev) print "OOD: " prev " >> " $0; prev = $0 }' <file>` — пусто => порядок верный. Дополнительно — `./gradlew :app:ktlintCheck` (уже в гейте).

<!-- SUBSECTION_END: l-scripts-62 -->
<!-- SUBSECTION_BEGIN: l-scripts-63 -->
___
### l-scripts-63 Rename класса — все формы

**Правило:** при rename класса — искать **все формы** использования, не только callsites методов: `Class.method()` (callsites), `param = <ClassName>` (named-args в конструкторах), `[<ClassName>]` (KDoc-ссылки), `import ...<ClassName>` (импорты), `<ClassName>(` (конструкторы). Grep по `<OldName>` **без** `\.` ловит все формы. Дополнительно — `git grep` по main + test (не только main, урок 1).

**Инцидент:** 2026-10-31, шаг 7/8 split (`9fb105c`). При rename `TInvestMarketDataService` -> `TInvestPositionsService` переименовал import, ctor, mockk, callsites методов, но **пропустил** named-arg `marketData = marketDataService` в конструкторе `TInvestOrchestrator` (внутри `TInvestBrokerApi`). `compileDebugKotlin` упал: `No value passed for parameter 'positions'` + `No parameter with name 'marketData' found` + `Unresolved reference 'marketDataService'` (BA:82–83). Pre-commit grep смотрел `marketDataService\.` (callsites методов) — named-arg без точки не поймал.

**Проверка:** `grep -rn '<OldName>' app/src/main app/src/test` (без `\.`) — до нуля перед коммитом. Если результат не пустой — не только callsites, но и named-args, KDoc, импорты.

<!-- SUBSECTION_END: l-scripts-63 -->
<!-- SUBSECTION_BEGIN: l-scripts-64 -->
___
### l-scripts-64 echo с backticks внутри двойных кавычек — command substitution

**Правило:** в bash-скриптах, которые ассистент выдаёт пользователю, **backticks** внутри `"..."` — это command substitution, даже рядом с одинарными кавычками. Пример: `echo` с тремя backtick'ами внутри двойных кавычек — bash видит `$(...)`, парсер валится с `syntax error near unexpected token`. Писать: одинарные кавычки вокруг всего аргумента (`echo '...'`), экранирование, или без backticks вообще.

**Инцидент:** 2026-10-31, docs-commit 8b (`mark Пакет C done`). Pre-check block содержал `echo` с тремя backtick'ами внутри двойных кавычек (проверка баланса fence'ов markdown). Bash упал на строке 15 — до `git add` / `git commit`. Pre-checks 1 и 2 прошли, 3 — нет. Коммит не создан (не страшно), но лишний round-trip.

**Проверка:** перед отправкой скрипта — визуально просмотреть все `echo "..."`, где могут быть backticks (типично — markdown fence-проверки, примеры кода); заменить на `echo '...'` (одинарные) или убрать backticks.

<!-- SUBSECTION_END: l-scripts-64 -->
<!-- SUBSECTION_BEGIN: l-scripts-65 -->
___
### l-scripts-65 assert new not in text, когда new — подстрока old

**Правило:** если `new_fragment` содержит `old_fragment` как подстроку (`new = old + suffix` или `new` с префиксом вокруг `old`), `assert new not in text` **всегда** падает, когда `old` присутствует — это не «already applied», а «old found». Запрещено писать `assert new not in text` для проверки «уже применено», если `new` может содержать `old` как подстроку. Правильно: `assert text.count(old) == 1` (pre-check через count старой формы), потом `text.replace(old, new, 1)`, потом `assert old not in text` (пост — старой формы больше нет).

**Инцидент:** 2026-10-31, 8b-b (`mark Пакет C done`). `old_cls = "internal class TInvestAccountsService("`, `new_cls = "class TInvestAccountsService("`. `assert new_cls not in text` ложно-сработал как «already public» — потому что `new_cls` ⊂ `old_cls` (префикс `internal ` + `class TInvestAccountsService(`). Скрипт упал **до** `write_text` (файл не изменён, pre-assert сработал правильно), но с ложным сообщением. Урок 26/2d был, но применён не полностью.

**Проверка:** в скрипте правки: если `old_fragment in new_fragment` — pre-check через `assert text.count(old) == 1`, **не** `assert new not in text`; пост-проверка — `assert old not in check` (старой формы нет) или `assert check.count(new) == 1`.


**Расширение (2026-11-06, M006 steps 3/6, 5/6):** ещё 2 инцидента:
- `assert '\nfun TradeSettingsContent' not in text` при `new = '\n@Composable\nfun TradeSettingsContent'` — `new` ⊃ `old`, assert не может пройти (новый содержит старый как подстроку). Правильно: `assert text.count('\n@Composable\nfun TradeSettingsContent') == 1` и не проверять отсутствие `\nfun TradeSettingsContent`.
- `assert 'internal data class LoadAccountsResult' not in text` (new) при `old = 'internal data class LoadAccountsResult('` (со скобкой) — `old` ⊃ `new` (new без скобки), `new in old` — assert падает (new уже есть в тексте как подстрока old).

**Правило (расширено):** в pre-check `assert new not in text` — проверять `old in new` и `new in old` (пересечения в обе стороны); если есть — не писать этот assert, использовать `assert text.count(old) == 1` (pre) и `assert text.count(new) == expected_count` (post).

<!-- SUBSECTION_END: l-scripts-65 -->
<!-- SUBSECTION_BEGIN: l-scripts-67 -->
___
### l-scripts-67 `assert "### X" not in text` — line-anchored regex, не подстрока

**Правило:** проверку «заголовка `### X` больше нет в файле» писать как `re.findall(r"^### X$", text, re.MULTILINE)` (длина 0) или `grep -cE "^### X$"` в shell. Не `assert "### X" not in text` — тот падает на inline-ссылках в `**Связано:**` / `**Предусловия:**` / `**Зависимости:**` / цитатах.

**Инцидент:** 2026-11-04, три раза подряд за одну сессию:
- clean-audit (`### Н5` — 4 вхождения `### Н5` как inline-ссылка);
- ROADMAP (`### H5.` — 6 вхождений в `### Ревизия`, `### ✅ Portfolio`, `### Обогащение name` и т.д.);
- ROADMAP (`### Currency format` — 2 вхождения в `**Связано:**` + `**Когда вернуться:**` моего же блока).

Каждый раз `assert not in text` ложно-падал, файл не изменялся (assert до `write_text`). Родственно урокам 23 и 65, но обратное направление (не «находит лишнее», а «не находит лишнее»).

**Проверка:** перед `assert not in text` для `### ...` — `re.findall(r"^### X$", text, re.MULTILINE)`; `assert len(...) == 0`.

**Обобщение (2026-11-06, 5 ложных assert за сессию).** Правило действует не только для `### X`, но для **любого** `assert X not in text` на `.md`-файле, где X может встретиться inline. Заголовки (`###`, `##`), ID задач (`Н5`, `M004`), имена секций (`## Закрытые задачи`), имена задач (`OrdersViewModelTest`), счётчики (`109`, `112`) — всё это встречается и как заголовок, и как inline-ссылка в `**Связано:**` / `**Предусловия:**` / `**Зависимости:**` / `**Ссылка на задачу:**` / цитатах / телах других задач.

**Правило:** `if X not in text` в проверках `.md` — запрещено. Только:
- `re.findall(r"^X$", text, re.MULTILINE)` (длина 0) — line-anchored;
- `grep -cE "^X$" file` в shell (== 0) — line-anchored;
- или `X not in <extracted_block>` — проверка в узком извлечённом блоке (line-anchored extract задачи / секции).

Подстрочный `in` / `not in` для проверки «этого нет в файле» — запрещён.

**Инцидент-счётчик (2026-11-06, 5 ложных assert за сессию):**
1. `assert "### ✅" not in new_roadmap` — 3 inline `### ✅` в `**Связано:**` / `**Референсы:**` других задач.
2. `assert "109" not in new_roadmap` — `109 строк` в планируемой задаче (`AssetCardContent`), не счётчик.
3. `assert "## Закрытые задачи" not in text` — 3 inline упоминания в текстах задач архива (7787, 7800).
4. `assert 'OrdersViewModelTest' not in text` — упоминание в другой (закрытой) задаче ROADMAP (9247).
5. `assert '### H5.' not in text` — inline-ссылки на H5 в `**Связано:**` / `**Ревизия**`.

**Продолжение (2026-11-09, 6-й случай):**

6. `assert "### CardIdentity: persist broker" not in at` — header
   `### CardIdentity: persist broker` встречается как подстрока в тексте
   другой секции ARCHIVE (transform markdown-archiving); line-anchored
   `re.findall(r"^### CardIdentity: persist broker", at, re.MULTILINE)` = 0,
   подстрочный `in` = True.

Формулировка 67 «line-anchored regex» была правильной, но узкой: говорила про `### X`, не про общий принцип. Расширение — после 5-го промаха.


**Продолжение (2026-11-09, 7-й случай + namespace):**

7. `assert "### ✅ M013." in at2` — ложно-упал после того, как файлы уже
   были записаны (`write_text` до post-assert). `M013` — **clean-audit ID**;
   в `ROADMAP-ARCHIVE.md` эта задача закрыта как
   `### ✅ A2: orchestration out of Orders/Portfolio/OrdersList (M013)` —
   header отличается. Проверка `"### ✅ M013." in at2` не могла пройти
   никогда. **Правило:** post-assert на `### X` — (а) line-anchored
   (`re.findall(r"^### X$", ...)`), (б) **namespace-aware**: брать точный
   текст заголовка из того файла, куда пишешь, а не ID из другого
   документа (clean-audit ID ≠ ROADMAP header). Диагностика после падения
   post-assert при `write_text`-до-assert: `git diff`, не откат (урок 28).

**Продолжение (2026-11-09, 8-й случай + wrong-suffix):**

8. `assert "### ✅ M001." in at2` — ложно-упал после `write_text` (файлы уже корректны). В `ROADMAP-ARCHIVE.md` M001 (formatting) назван `### ✅ M001: formatting в presentation` (двоеточие), а не `### ✅ M001.` (точка). Line-anchored — да, но **не тот суффикс**: я «дописал» `.` к ID. **Правило:** post-assert на `### X` — брать **точную** строку заголовка из целевого файла (`grep -nF` / `re.findall(r"^### ✅ M001[:. ]", ...)`), а не конструировать «`M001.` + дописать». Если нужен «любой заголовок с этим ID» — явный regex по разделителям (`[:. ]`), а не «дописанный суффикс».

**Проверка (общая):** перед `assert X not in text` в скрипте правки `.md` — `grep -nE "^X$" file`. Если >0 — line-anchored проверка; если inline-вхождения — `assert X not in text` невозможен, использовать `re.findall(r"^X$", ...)`. Связано с уроком 79 (проверяй факт, не форму).

<!-- SUBSECTION_END: l-scripts-67 -->
<!-- SUBSECTION_BEGIN: l-scripts-68 -->
___
### l-scripts-68 `Path.read_text()` возвращает trailing `\n`

**Правило:** `Path.read_text()` сохраняет финальный `\n`. При сравнении полного файла (`actual == old_fragment`) — `old_fragment` должен содержать `\n` в конце. Иначе `assert` падает на разнице в один символ.

**Инцидент:** 2026-11-04, A1.2a (`PortfolioUiState.kt`). `old_ui` заканчивался на `)`, файл — на `)\n`. `assert actual_ui == old_ui` упал с `PortfolioUiState mismatch` — без указания, что расхождение в trailing `\n`.

**Проверка:** `assert text.endswith("\n")` перед сравнением полного файла; `old_fragment` — `... + "\n"`. Либо сравнение через `.rstrip()` с обеих сторон.

<!-- SUBSECTION_END: l-scripts-68 -->
<!-- SUBSECTION_BEGIN: l-scripts-69 -->
___
### l-scripts-69 Вставка в конец файла — `rstrip()` + `\n`

**Правило:** вставка `text = text + new_entry` в конец файла даёт blank line at EOF, если `new_entry` оканчивается `\n\n`. Финал: `text = (text + new_entry).rstrip() + "\n"` (или `new_entry.rstrip("\n") + "\n"`).

**Инцидент:** 2026-11-04, шаг 4 (`docs/ISSUES.md`). `git diff --check` дал `docs/ISSUES.md:1229: new blank line at EOF`. Фикс — `text.rstrip() + "\n"`. Потеряно `1` байт.

**Проверка:** `git diff --check` в том же shell-блоке, что и правка; `assert not text.endswith("\n\n")` после `write_text`.

<!-- SUBSECTION_END: l-scripts-69 -->
<!-- SUBSECTION_BEGIN: l-scripts-76 -->
___
### l-scripts-76 `unused import` после удаления кода

**Правило:** после удаления функции — `grep -c "\bSymbol\b" file` vs `grep -c "^import.*\.Symbol$" file`. Равенство (1 == 1) = символ используется только в импорте → unused. После удаления кода — прогонять этот pre-check на все импорты удалённого файла.

**Инцидент:** 2026-11-04, CI `fast-check`: `PortfolioViewModel.kt:23:1 Unused import` — `import com.gitlab.biomorf.tscalp.domain.models.moneyPerPointOfPrice`, символ использовался только в удалённом `toUiPosition` (A1.2b-3).

**Проверка:** `grep -c "\bSymbol\b" file` == `grep -c "^import.*\.Symbol$" file` → unused; удалить. Или `./gradlew :app:ktlintFormat` (desktop, урок 55).

**Расширение (2026-11-05, InstrumentColors):** второй случай — `InstrumentType` в `AssetPositionCard.kt` после вырезания локального `when`-блока (PR #95). Pre-check в python (`re.findall(r'\bInstrumentType\b', ...)`) ошибочно посчитал `import ...InstrumentType` за использование → `inst_c = 1`, скрипт не удалил импорт, CI `ktlintMainSourceSetCheck`: `Unused import` (fix `30b82ad`). **Правило:** при подсчёте использований — **вычитать import-строку**: `len(re.findall(r'\bSymbol\b', text.replace(imp_line, '', 1))) == 0` до удаления импорта (или `grep -c "^import.*\.Symbol$"` vs `grep -c "\bSymbol\b"` — равенство = unused).

<!-- SUBSECTION_END: l-scripts-76 -->
<!-- SUBSECTION_BEGIN: l-scripts-77 -->
___
### l-scripts-77 Commit message / PR body / tag — English only, strict pre-check

**Правило:** строго перед `git commit -F` (и `git tag -a -F`) — обязательный pre-check на кириллицу:

    if grep -P '[Cyrillic-range]' "$HOME/.msg.txt"; then
      echo "ERROR: Cyrillic in commit message — abort, rewrite"
      exit 1
    fi

Match (`grep` exit 0) → abort, rewrite message in the script.
No match (`grep` exit 1) → continue. Не «после коммита», не
«глазами» — строго механический pre-check **перед** коммитом.
PR body / tag message — та же проверка, но вручную (в скрипте
нет автоматического pre-check, потому что PR/tag — отдельный шаг).

**Инцидент:** сессия 2026-11-04, H5 + UI extract. Русский в
commit message прошёл 5 раз: `c855ebe`, `7294c9b`, `aa7909f`,
`55ca55d` (в `dev`), `1bfc359` (feature). Каждый — нарушение
AGENTS 5 «Язык коммитов — только английский». Правило есть с
самого начала, но **механического** pre-check в шаблоне не было.
Исправлено в AGENTS 5b + 4i.

**Проверка:** в шаблоне коммита — блок `if grep -P ...; then
exit 1; fi` до `git commit -F`. Идентификаторы clean-audit в
commit/PR — **латиницей** (`H5`, `H94`), в `.md` файлах — как
в clean-audit (кириллическая «Н»). Иначе `grep -P` на
кириллицу ложно-срабатывает на кириллическую «Н» в ID.
В commit message нельзя цитировать буквальный regex
с кириллицей — placeholder `[Cyrillic-range]` (иначе
pre-check абортит сам себя).

**Placeholder `[Cyrillic-range]` — только в тексте документа.**
В живом скрипте — только реальный диапазон `[А-Яа-я]` (как в
4i / 5b) или `[\x{0400}-\x{04FF}]`. Литерал `[Cyrillic-range]`
в скрипте — это 14 латинских букв в character class
(`C y r i l l a n g e r a n g e`), матчит `a`/`e`/`i` в любом
английском тексте → ложное срабатывание на чистом коммите.

**Инцидент (2026-11-06, M003):** в скрипте коммита M003
написал `if grep -P '[Cyrillic-range]' ...; then echo "ERROR:
..."`. На английском commit message сработало ложно
(«ERROR: Cyrillic detected»), но прошло к `git commit` — потому
что вторая проверка (правильный unicode-range) была корректной
и не нашла кириллицу. Коммит `b120cd3` ушёл чистым. Урок:
placeholder ≠ рабочий regex.

**Инцидент-счётчик (1-й сработавший корректно, 2026-11-07, M013 step 4a):** commit message с телом `- Empty accounts now returns AppResult.Failure("Нет доступных счетов")` — pre-check `grep -P '[\x{0400}-\x{04FF}]'` поймал, `exit 1` до `git commit`. Файлы не закоммичены, `git status` чистый. Переписал на `"No available accounts"` — коммит `4e0264f` прошёл. **Pre-check работает как задумано.** Урок: русский текст в **коде** (UI-сообщения) — норма; в **commit message** — запрещён. Цитата русского текста в commit — перевод/перефраз (5: цитаты тоже на английском).

**Инцидент-счётчик (2026-11-08, Session Orders-VM-Split follow-up, 5 нарушений):**
за одну сессию pre-check сработал 5 раз подряд — каждый раз русские цитаты в
`git commit -F` message (docs-коммиты в `dev` / feature). Литералы, попавшие в
message:

1. D2 ROADMAP: заголовок секции `## Планируемые задачи` в теле коммита.
2. D1 ROADMAP: название задачи `### OrdersViewModel: разбить на несколько VM`.
3. A (mark done, ROADMAP-ARCHIVE): `Дата закрытия`, `Статус: в работе`,
   `Ветка: feature/orders-vm-split`.
4. A (fix): всё ещё `Дата закрытия`, `Статус: в работе`, `Ветка`.
5. A (mark done OpenCardDialogUseCase): `Разбивка` / `Подтверждение` в цитате.

Правило (расширено): при формировании message — **сразу** перефраз на
английский, без попытки «вставить и посмотреть, сработает ли pre-check».
Русский в commit/PR/tag — запрещён, включая цитаты названий задач и
заголовков секций; либо английский эквивалент, либо описание без литерала
(«the split task», «closing-date line», «the closing section»).



---

<!-- SUBSECTION_END: l-scripts-77 -->
<!-- SUBSECTION_BEGIN: l-scripts-79 -->
___
### l-scripts-79 post-assert: только новое + `old_frag not in text`

**Правило:** post-assert проверяет **только** новые маркеры
(line-anchored через `re.findall(r"^...$", text, re.MULTILINE)`)
и `old_frag not in text`. Не проверять «старое не тронуто»
через склейку двух строк пробелом или сквозь markdown-bold —
ложные падения. `old_frag` заменён через `replace(..., 1)` —
не тронут по определению; визуально — `git diff` в verify.

**Инцидент:** 2026-11-05, AGENTS.md 4h mobile-ветка (97a61e1).
Два падения post-assert (файл не изменён, оба до `write_text`):
1. `assert "`./gradlew :app:ktlintFormat`, **не** ручной перенос"
   in text` — в файле эти строки **на разных строках** (перенос
   после `./gradlew`), подстрока не находится.
2. `assert "не ручной перенос" in text` — в файле
   **`**не** ручной перенос`** (bold), подстрока без `**` не
   находится.

Исправлено: post-assert только на новые маркеры +
`old_frag not in text`. Desktop-ветка не тронута —
доказательство: `old_frag` не содержит desktop-строк;
replace по `old_frag` не мог её задеть; визуально — `git diff`
в verify.

**Проверка:** в скрипте post-assert — `assert old_frag not in
text` + `assert new_frag in text` + `assert len(re.findall(
r"^...", text, re.MULTILINE)) == N`. Не проверять
не-тронутость через склейку/сквозь bold.

**Связано:** урок 26 (`assert old not in text` — KDoc), 56а
(`count == 1` для имени задачи — line-anchored), 65 (`assert
new not in text` — new ⊂ old), 67 (`assert "### X" not in
text` — inline-ссылки).

**Расширение (2026-11-05, M008):** ещё 4 ложных падения за одну
сессию, новые корни:
3. **Подстрока в другом блоке файла.** `assert 'OrdersViewModelTest'
   not in text` — ловит упоминание в **другой** (закрытой) задаче
   файла (ROADMAP: 9247, `### ✅ OrdersViewModel: миграция на
   PortfolioSheet`). Правильно: `old_frag not in <extracted_block>`,
   block — line-anchored extract M008-блока
   (`re.search(r"^### M008:...", text, re.MULTILINE)` → срез до
   `\n### `).
4. **Регистр.** `assert 'Стоимость mobile-гейта' in text` — в тексте
   `— стоимость` (строчная, после `—`). Правильно: `assert
   text.count(fragment) == 1`, где `fragment` — **точная копия** из
   `new_frag`, а не перепечатка.
5. **Новый текст намеренно содержит старый идентификатор**
   (инверсия урока 65). `assert 'OrdersViewModelTest' not in block` —
   новый R3-текст намеренно упоминает `OrdersViewModelTest`
   («VM-тест на триггеры не заводится»). Правильно: проверять
   **точный старый фрагмент** (`'OrdersViewModelTest` (новый,
   если нет)`), не голый идентификатор.


<!-- SUBSECTION_END: l-scripts-79 -->
<!-- SUBSECTION_BEGIN: l-scripts-80 -->
___
### l-scripts-80 `echo` с backtick — 3-й раз; проверяй факт, не форму

**Правило:** (а) `echo` с backtick внутри двойных кавычек —
command substitution (повтор урока 64, 2026-11-05, M008-диагностика:
`echo "=== ... 'OrdersViewModelTest` ..."` → `syntax error near
unexpected token '('`). Писать `echo '...'` (одинарные) или без
backticks вообще.

**Третий случай (2026-11-06, 5a-check):** `echo "``` count:
$(grep -c '^```' file)"` — backticks внутри двойных кавычек,
bash → `command substitution: syntax error near unexpected token
')'`. Тот же класс, 3-й раз (64 → 80-первый → этот).

(б) **Общий корень** всех ложных post-assert за
M008-сессию (6 штук): **проверял предполагаемую форму, а не
фактическое состояние**. Правило: post-assert проверяет **факт**
(`old_frag not in <block>`), не «как я думаю выглядит файл».

**Инцидент:** 2026-11-05, M008-сессия. Шесть ложных assert'ов
подряд:
1. AGENTS.md: `assert "`./gradlew :app:ktlintFormat`, **не** ручной
   перенос" in text` — склейка двух строк.
2. AGENTS.md: `assert "не ручной перенос" in text` — `**не**`
   (bold).
3. ROADMAP: `assert 'Стоимость mobile-гейта' in text` — регистр
   (`— стоимость`).
4. ROADMAP: `assert 'OrdersViewModelTest' not in text` — подстрока
   в другом блоке (9247).
5. ROADMAP: `assert 'OrdersViewModelTest' not in m008_block` —
   новый текст намеренно упоминает идентификатор (инверсия 65).
6. Bash-диагностика: `echo "=== ... 'OrdersViewModelTest` ..."` —
   backtick в двойных кавычках (повтор 64).

**Проверка:** перед отправкой скрипта — (а) все `echo` без
backticks внутри `"..."`; (б) каждый post-assert — `old_frag not in
<block>` с `block` = line-anchored extract; (в) `fragment` —
точная копия из `new_frag`, не перепечатка; (г) `git diff --check`
+ `cat -A` затронутого региона.

**Связано:** урок 64 (`echo` с backtick), 65 (`new` ⊂ `old`),
67 (line-anchored), 79 (post-assert scope).

**Инцидент-счётчик (2026-11-07, Session Orders-VM-Split, 4 нарушения):**
один и тот же класс за сессию — post-assert / verify / pre-commit проверяет подстроку в файле, а ловит **KDoc** (текст комментария), не код. Счётчик «6-й раз за сессию».

1. 12a, verify `OrderDialogsViewModel.kt`: `assert "startPositionUpdates" not in text` упал — строка есть в KDoc («**не вызывает** `startPositionUpdates` (UI-оркестрация, 12b, P4)»). В коде вызова нет. Нужен был call-form: `grep -nE "[^`]startPositionUpdates\("` (без backticks) — пусто.

2. 12a-fix, verify KDoc `OrderDialogsViewModel`: `assert "Правило Clean: M2" in t` упал — фактически в файле `**Правило Clean:** M2` (bold между `Clean:` и `M2`). Подстрока без `**` не совпадает.

3. 13b-1, verify `RefreshSelectedInstrumentsUseCase.kt`: `assert "suspend" not in t` упал — `suspend` есть в KDoc («нет suspend, deterministic»). Файл корректен (pure). Нужен был call-form: `grep -nE "^\s*suspend\s+fun\s"` — пусто.

4. 13b-2, pre-commit `RefreshSelectedInstrumentsUseCase.kt`: `grep -nE "@Inject constructor"` сматчил строку KDoc «**`@Inject constructor()` без зависимостей** — для Hilt-инъекции в VM». Файл корректен (`@Inject` на отдельной строке, `constructor()` на следующей — урок 78). Нужно было: `grep -nE "@Inject[[:space:]]+constructor" "$f" | grep -vE "^[0-9]+:[[:space:]]*\*"` (исключить KDoc).

**Правило (расширено, для всех verify/pre-commit):** (а) для **символов** — verify по **call-form / declaration-form** (`^\s*(suspend\s+)?fun\s+X\(`), не по подстроке в файле; (б) для **markdown-текста** — учитывать `**bold**` в паттерне или проверять **вне** backticks; (в) для **pre-commit аннотаций** — `grep … | grep -vE "^[0-9]+:[[:space:]]*\*"` (исключить KDoc-строки), иначе KDoc-цитата даёт ложный PREFAIL.

**Проверка:** перед отправкой скрипта — для каждого `assert "X" not in text` / `grep "X"` — `grep -n "X" file | grep -v "^\s*\*"` + `grep -nE "^\s*(suspend\s+)?fun\s+X\("`.

<!-- SUBSECTION_END: l-scripts-80 -->
<!-- SUBSECTION_BEGIN: l-scripts-81 -->
___
### l-scripts-81 Multiline pre-check в скрипте — `\($` / `\([^)]*$`, не `\(`

**Правило:** при проверке multiline wrapping в скрипте — `grep -nE "val \w+ = \w+\.\w+\($"` (с `$` — `(` в конце строки) или `\([^)]*$` (открывающая `(` без `)` на той же строке). **Не** `val \w+ = \w+\.\w+\(` (без `$`) — ложно-падает на однострочных `val x = obj.method(arg)`.

**Инцидент:** 2026-11-05, H64 (`TInvestPortfolioService.kt`). Verify-grep `'val \w+ = \w+\.\w+\('` (без `$`) поймал `val protoInstrument = instrumentsService.fetchProtoInstrument(uid)` — однострочное, `)` на той же строке. Ложная тревога `multiline: 1`, диагностика.

**Проверка:** `grep -nE "val \w+ = \w+\.\w+\($"` или `\([^)]*$` — только реальные multiline. `val x = obj.method(arg)` — не матчит.

**Связано:** AGENTS.md 4h mobile-ветка, правило 4 (Multiline wrapping) — формулировка **корректная** (`\($`), ошибка была в скрипте.

<!-- SUBSECTION_END: l-scripts-81 -->
<!-- SUBSECTION_BEGIN: l-scripts-82 -->
___
### l-scripts-82 5g.4 при extract — перенос комментариев

**Правило:** при вынесении кода в helper / extract (2c-рефакторинг) — переносить **все комментарии внутри** старого блока. Потерянный комментарий — нарушение 5g.4 («содержимое не сокращается»).

**Инцидент:** 2026-11-05, H64. В `fetchPortfolioPositions` был комментарий про `money/blocked` (`GetPortfolio` не отдаёт; WS или GetPositions). При extract в 3 функции — комментарий **потерян**. Восстановлен перед `AccountPortfolio(...)`.

**Проверка:** при extract — `git diff` старого блока; каждый `//` / `/** */` внутри должен быть **где-то** в новом коде. `grep -n '// ' <old>` vs `grep -n '// ' <new>`.

**Связано:** урок 66 (5g.4, перенос в Исправленные — три вещи).

<!-- SUBSECTION_END: l-scripts-82 -->
<!-- SUBSECTION_BEGIN: l-scripts-83 -->
___
### l-scripts-83 Clean-audit refs — проверять ID

**Правило:** при закрытии задачи — проверять, что `→ docs/clean-audit.md → Нxx` ведёт на **правильную** запись. Перепутанный ID — источник ложных mark done.

**Инцидент:** 2026-11-05, TInvestBrokerApi (PR #96). В ROADMAP `**Референсы:** docs/clean-audit.md → Н70` — но Н70 = `BrokerApi — @Suppress("TooManyFunctions")` (PR #61), **не** TInvestBrokerApi. Запись Н70 корректна, к TInvestBrokerApi не относится; правку Н70 **не** делали (решение пользователя). Урок: сверять ID.

**Проверка:** `grep -n '### Н<xx>\.' docs/clean-audit.md` — заголовок соответствует задаче. И `grep -n '### ✅ Н<xx>\.'` для закрытых.

**Связано:** урок 13 (anchor в скрипте — grep перед написанием).


<!-- SUBSECTION_END: l-scripts-83 -->
<!-- SUBSECTION_BEGIN: l-scripts-100 -->
___
### l-scripts-100 `git stash push` no-op → `git stash pop` в том же блоке запрещён

**Правило:** если `git stash push -u` вернул `No local changes to save` (или exit 0 без вывода) — `git stash pop` в том же блоке **запрещён**: применит чужой stash (например, WIP с другой ветки), и на неверной ветке. Правильно: `git stash push` + флаг `STASH_CREATED=1`; `git stash pop` — только если флаг установлен.

**Инцидент:** 2026-10-27 (`SettingsViewModel` → `dev`); повтор 2026-11-10 (`feature/compose-ui-settings` → `dev`, post-PR чек-лист U3 Pass-3).

**Проверка:** `if [ -n "$(git status --porcelain)" ]; then git stash push -u -m "..."; STASH_CREATED=1; fi` → `if [ "$STASH_CREATED" = "1" ]; then git stash pop; fi`. Перед pop: `git stash list | head -1` — проверить, что наш stash.
---

<!-- SUBSECTION_END: l-scripts-100 -->
<!-- SUBSECTION_BEGIN: l-scripts-101 -->
___
### l-scripts-101 `grep -cE` с неэкранированной `(` — Unmatched; для unused-import — `grep -cF` + skip wildcards

**Правило:** `grep -cE "Card("` → `Unmatched ( or \(`. Для проверки символов — `grep -cF` (literal, без regex). Для unused-import в pre-commit: `grep -cF "$sym"` + skip wildcard (`import x.*`) — символы из wildcard не ловятся ktlint как unused, но и literal-проверка их не находит.

**Инцидент:** U3 Pass-1b (2026-11-11), pre-commit `CardDefaults` показал `count=0` → abort, хотя символ из wildcard `androidx.compose.material3.*`. Первая причина: `grep -cE "Card("` — unmatched `(`.

**Проверка:** `grep -cF "$sym"`; wildcard-импорты скипать: `if echo "$sym" | grep -qE '^\*$'; then continue; fi`.

---

<!-- SUBSECTION_END: l-scripts-101 -->
<!-- SUBSECTION_BEGIN: l-scripts-115 -->
___
### l-scripts-115 adb в heredoc: `< /dev/null`, `timeout --foreground`, `--pid`

**Правило:** (а) `adb shell` в `$()` внутри heredoc — `< /dev/null`;
(б) `timeout` без `--foreground` не пробрасывает Ctrl+C —
`timeout --foreground`; (в) live-test лог — `adb logcat --pid=<PID>`
(10–50× меньше).

**Инцидент:** 2026-11-16, live-test 1a (rate-limiter). Скрипт
захвата с `adb logcat` падал за 0.1с или висел до таймаута
(2507 строк, 324KB — стресс не успел). Причины: (1) `adb shell
pidof` в `$()` — hung, читал stdin heredoc'а; (2) `timeout 300
adb logcat` без `--foreground` — Ctrl+C не работал, ждали полные
5 мин; (3) без `--pid` — весь logcat (700KB). Итераций: 6.

**Проверка:** шаблон:

    PID=$(timeout 5 adb shell pidof <pkg> < /dev/null | tr -d '\r\n')
    timeout --foreground 300 adb logcat --pid="$PID" -v threadtime > "$LOG" 2>&1

<!-- SUBSECTION_END: l-scripts-115 -->
<!-- LESSONS_SECTION_END: scripts -->