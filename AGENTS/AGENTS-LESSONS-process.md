<!-- LESSONS_SECTION_BEGIN: process -->
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

<!-- SUBSECTION_BEGIN: l-process-21 -->
___
### l-process-21 reset --hard — перечислить теряемые коммиты

**Правило:** перед `git reset --hard <target>` — явно перечислить коммиты, которые будут потеряны (`git log --oneline <target>..HEAD`). Не делать вслепую.

**Инцидент:** 2026-10-27, сессия clean-audit. Предложил `git reset --hard origin/dev` для отката расхождения, но не перечислил, что при этом потеряется и `35e2a88` (clean-audit.md, тоже локальный). Пользователь спросил: «Можно сделать 'git reset --hard 35e2a88'» — пришлось объяснять, что это не решает проблему с origin. Если бы сделал reset без уточнения — потерял бы clean-audit.

**Проверка:** `git log --oneline <target>..HEAD` в сообщении с предупреждением перед командой. Явный список теряемых коммитов.

---

<!-- SUBSECTION_END: l-process-21 -->
<!-- SUBSECTION_BEGIN: l-process-22 -->
___
### l-process-22 В proot писать в $HOME, не /tmp

**Правило:** в proot-окружении (Termux) `/tmp` может быть недоступен для записи. Все backup-файлы, временные fragments — в `$HOME/tscalp-tmp/` или другой writable директории.

**Инцидент:** 2026-10-27, backup clean-audit перед reset. `git show 35e2a88:docs/clean-audit.md > /tmp/clean-audit.md.bak` → `Permission denied`. Файл не создан, `wc -l` упал. Пользователь переключил на `~/tscalp-tmp/`.

**Проверка:** `mkdir -p ~/tscalp-tmp && test -w ~/tscalp-tmp && echo writable` перед backup. Или проверить `test -w /tmp` заранее.

<!-- SUBSECTION_END: l-process-22 -->
<!-- SUBSECTION_BEGIN: l-process-34 -->
___
### l-process-34 Миграция DI — поэтапно, компиляция между этапами

**Правило:** миграция на Hilt разбивается на шаги: (1) зависимости + `@HiltAndroidApp`; компиляция; (2) модули `DataModule`/`RepositoryModule`; компиляция; (3) одна ViewModel (`OrdersViewModel`); компиляция; (4) остальные VM по одной. Не мигрировать всё разом.

**Инцидент:** 2026-10-28. Попытка мигрировать `OrdersViewModel`, `PortfolioViewModel`, `OrdersListViewModel` одновременно — лавина ошибок (AGP vs Hilt, KSP vs kapt, `@AndroidEntryPoint`, `SharedPreferences` DuplicateBindings). Каждый следующий шаг требовал предыдущего. Пришлось откатываться и делать поэтапно.

**Проверка:** `./gradlew :app:compileDebugKotlin` после каждого шага. Не переходить к следующему, пока не зелёный.

<!-- SUBSECTION_END: l-process-34 -->
<!-- SUBSECTION_BEGIN: l-process-35 -->
___
### l-process-35 Hilt: три аннотации — Application, Activity, ViewModel

**Правило:** при первом `hiltViewModel()` в Composable проверить все три: `@HiltAndroidApp` на Application, `@AndroidEntryPoint` на Activity, `@HiltViewModel` + `@Inject constructor` на ViewModel. Пропуск любой — краш `Given component holder class ... does not implement ... GeneratedComponent` или внедрение не работает.

**Инцидент:** 2026-10-28, после перевода `OrdersViewModel` на Hilt. Первый запуск — `IllegalStateException: Given component holder class com.example.tscalp.MainActivity does not implement interface dagger.hilt.internal.GeneratedComponent`. Пропустил `@AndroidEntryPoint`. Второй запуск — `API не инициализирован`: `DataModule` создавал `TInvestInvestService` без `initializeFromSettings()`, хотя токен был.

**Проверка:** `grep -rn "@HiltAndroidApp\|@AndroidEntryPoint\|@HiltViewModel" app/src/main/java/com/example/tscalp/` — все три присутствуют. Плюс проверить, что модуль-провайдер сервиса сам вызывает `initializeFromSettings()` (или `initialize()`), а не полагается на `Application.onCreate`.

<!-- SUBSECTION_END: l-process-35 -->
<!-- SUBSECTION_BEGIN: l-process-36 -->
___
### l-process-36 `SharedPreferences` — один `@Provides` на всё приложение

**Правило:** `SharedPreferences` предоставляется `@Provides` ровно в одном Hilt-модуле. Два и более — `DuplicateBindings` на этапе KSP.

**Инцидент:** 2026-10-28, `SharedPreferences` в `DataModule` и `RepositoryModule`. KSP: `android.content.SharedPreferences is bound multiple times`. Пришлось удалять из `RepositoryModule`.

**Проверка:** `grep -rn "provideSharedPreferences" app/src/main/java/com/example/tscalp/di/` — один результат.

<!-- SUBSECTION_END: l-process-36 -->
<!-- SUBSECTION_BEGIN: l-process-37 -->
___
### l-process-37 Рефакторинг ≠ изменение поведения

**Правило:** рефакторинг (переименование, извлечение, перенос) сохраняет поведение 1:1. Любое изменение поведения (даже «улучшение», «полировка», «оптимизация») — отдельный коммит с явным описанием. Не смешивать.

**Инцидент:** 2026-10-28, при рефакторинге `OrderCard` «улучшил» дизайн (иконка, скролл) — в итоге UI-баг с перекрытием оверлея. При полировке полей цены «улучшил» — сломал видимость цифр (не хватало высоты поля). Пришлось откатывать и делать отдельно.

**Проверка:** `git diff --stat` перед коммитом. UI-файлы + файлы логики в одном diff — разбить на два коммита.

<!-- SUBSECTION_END: l-process-37 -->
<!-- SUBSECTION_BEGIN: l-process-38 -->
___
### l-process-38 «Обсуждаем» / «планируем» — не код

**Правило:** при формулировках «обсудим», «планируем», «какой план», «сначала обсудим» — выдать обсуждение/план **без кода** (даже если кажется, что код очевиден). Код — только после явного «приступаем», «готов», «давай код».

**Инцидент:** 2026-10-28, сессия рефакторинга. Пользователь: «Пока обсуждаем и планируем» — я выдал код. «Давай план» — снова код. Пользователь: «Мы же начинаем новый тикет, значит пока только обсуждаем и планируем. Такой порядок работы всегда».

**Проверка:** в ответе на «обсуждаем»/«планируем» — `grep -c '^\`\`\`' ответ` = 0 (нет code fence).

<!-- SUBSECTION_END: l-process-38 -->
<!-- SUBSECTION_BEGIN: l-process-50 -->
___
### l-process-50 `git revert --no-commit` — не забыть `--continue`/`--abort`

**Правило:** после `git revert --no-commit` — либо `git revert --continue` (если конфликты разрешены), либо `git revert --abort` (отменить). Не оставлять репозиторий в состоянии reverting. `git revert --quit` — «выйти без отката» (если нужен промежуточный результат без commit).

**Инцидент:** 2026-10-28. `git revert --no-commit c4865a5` + `commit` вручную — `status` показал «You are currently reverting», git ждал `--continue`. `--quit` привёл в порядок.

**Проверка:** `git status` после revert. Если `reverting` — `--continue` / `--abort` / `--quit`.


<!-- SUBSECTION_END: l-process-50 -->
<!-- LESSONS_SECTION_END: process -->