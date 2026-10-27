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

## Применение уроков

Механизм — `AGENTS.md` → 4h «Проверка правок на соответствие
перед коммитом». Без 4h уроки — просто текст.
