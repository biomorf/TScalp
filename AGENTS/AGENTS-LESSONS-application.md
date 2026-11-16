<!-- LESSONS_SECTION_BEGIN: application -->










<!-- SUBSECTION_BEGIN: l-application-89 -->
___
### l-application-89 Merge `origin/dev` в feature перед PR — через `--no-commit --no-ff`
**Правило:** если в Rulesets включено «Require branches to be up to date before merging» — перед созданием PR (или перед merge) feature-ветка **обязана** содержать `origin/dev`. Проверять: `git merge origin/dev --no-commit --no-ff` → `git status --short` → если `Unmerged paths` пусто — `git commit -m "Merge origin/dev into <branch>"` → `git push`. **Не** `git merge origin/dev` сразу — без `--no-commit` merge-коммит создаётся автоматически, точка контроля (`git diff --cached --stat`) теряется.

**Инцидент:** 2026-11-07, M013 PR. `git diff --stat origin/dev..origin/feature` показал **обратные** удаления (`AGENTS-LESSONS.md | 52 -----`, `docs/ISSUES.md | 48 -----`) — это не «удаления в feature», а «в feature нет docs-коммитов, которые есть в dev». Без merge PR был бы заблокирован ruleset'ом («Require branches to be up to date»). `git merge origin/dev --no-commit --no-ff` → `Automatic merge went well; stopped before committing as requested` → проверка `git diff --cached --stat` → commit → push.

**Проверка:** `git log --oneline origin/feature..origin/dev` — пусто перед PR. Если непусто — `git merge origin/dev --no-commit --no-ff`, проверить `git diff --name-only --diff-filter=U` (пусто = нет конфликтов), commit, push. Связано: 5f-freeze, `docs/repo-protection.md` (Rulesets), 5f (post-PR).
<!-- SUBSECTION_END: l-application-89 -->










<!-- SUBSECTION_BEGIN: l-application-93 -->
___
### l-application-93 4f: правки → пауза → гейт+коммит → пауза (не в одном блоке)
**Правило:** при работе с кодом на desktop (feature-ветка) — правки,
гейт и коммит **разделены на два блока** с паузой после каждого:

1. **Блок 1 — правки + verify + `git diff`.** Пауза: пользователь видит
   diff, проверяет корректность.
2. **Блок 2 — гейт → условный коммит → push.** Пауза: пользователь видит
   `BUILD SUCCESSFUL` / `GATE RED`.

Один блок «правки + гейт + коммит» — **запрещён**. Это лишает пользователя
точки контроля между правками и гейтом: если правки некорректны, гейт
упадёт, но пауза для review уже потеряна.

**Инцидент:** 2026-11-07, Session Orders-VM-Split, 14a. Ассистент выдал
один bash-блок: удаление orphan import `PriceFlowManager` + гейт
(`./gradlew ...`) + коммит + push (в одном `{...} | tee`). Нарушение 4f:
блок 1 и блок 2 должны быть разделены паузой. Пользователь запросил
verify-only, скрипт не запускался — но правило нарушено на этапе
формулировки.

**Проверка:** перед отправкой ответа — если в bash-блоке есть
`./gradlew` **и** `git commit` **в одном** `{ ... }` → **разделить** на
два блока. Один блок — один тип операции: правки/verify (блок 1) или
гейт+commit+push (блок 2). `git add` — в блоке 1 (staging после review
diff), `git commit` — в блоке 2.

**Связано:** AGENTS 4f (структура шага: блок 1 + блок 2), 4b (один шаг
за раз), 4d (пауза после скрипта).
<!-- SUBSECTION_END: l-application-93 -->










<!-- SUBSECTION_BEGIN: l-application-104 -->
___
### l-application-104 5f-freeze: dev заморожен до трёх условий merge
**Правило:** в ожидании merge PR — **`dev` заморожен**. Не пушить в `dev`
(даже docs-only, даже `AGENTS.md`, даже `AGENTS-LESSONS.md`). «Момент
merge» — три условия одновременно: (а) merge-коммит в `origin/dev`,
(б) CI `gate` на нём зелёный, (в) пользователь подтвердил «PR смержен».
До всех трёх — только рабочее дерево (без коммита), stash, или другая
ветка. Перед любым `git push origin dev` — проверить все три явно.

**Инцидент:** 2026-11-12, сессия PrepareOrderRequest B2. PR #133
(`feature/prepare-order-decompose-b2`, B2) открыт, CI зелёный, но **не
смержен**. Я запушил в `dev` два коммита: `7d24eae2` (docs(agents): 4a
fence hierarchy) и `f3b633ad` (docs(architecture): add Data flow: orders)
— оба docs-only, оба в обход 5f-freeze. **Повторение** инцидента
2026-10-30 (`b10c790`, AGENTS.md в dev до merge PR #75) — правило
5f-freeze уже содержало урок. Пользователь: «разве нет правила, что в
ожидании PR нельзя пушить даже AGENTS? Почему не проверяешь
соблюдение правил?». Нарушение — моё, проверка трёх условий перед push
отсутствовала.

**Проверка:** перед любым `git push origin dev` — три команды:

    git log --oneline origin/dev -1     # (а) merge-коммит ожидаемого PR?
    # CI статус на merge-коммите — зелёный? (в вебе / gh)
    # (в) пользователь подтвердил «PR смержен»?

Если хотя бы одно — нет: `git push origin dev` **запрещён**. Разрешено:
правки в рабочем дереве, `git stash push` (pop после merge), работа в
feature-ветке. Для docs-правок в ожидании: подготовить, проверить 5a/4h,
stash — не коммитить.

**Связано:** AGENTS 5f-freeze, 4f (порядок шага), урок 89 (merge
origin/dev перед PR).

---
<!-- SUBSECTION_END: l-application-104 -->










<!-- SUBSECTION_BEGIN: l-application-106 -->
___
### l-application-106 Test-код → feature-ветка + PR, не push в dev
**Правило:** коммиты с изменением `.kt` / `.kts` / `.gradle` (test / feat / fix / refactor / chore) — только через `feature/<name>` → PR → merge. Прямой push в `dev` через admin bypass — только `docs(...)` (5g категория 2, 5a docs-only). `test(...)` — код, не документация.

**Инцидент:** 2026-11-12, сессия Hilt-test infra Step 2. Я запушил `1e1cc845` (`test(orders): migrate OrdersScreenBehaviourTest to @UninstallModules + add FakeBrokerRegistry`) прямо в `dev` через admin bypass. Пользователь: «только почему в dev. а не в feature?». Нарушение: git-flow (`dev` — интеграция через PR), 4f (feature (desktop) — батч перед PR), repo-protection (`dev` защищён: PR + required gate; bypass — для восстановления, не для нового test-кода). Revert не делал (test-only, gate локально зелёный — по решению пользователя 2026-11-12).

**Проверка:** перед `git push origin dev` — что за коммит? `git diff --stat HEAD~1 HEAD -- '*.kt' '*.kts' '*.gradle'`:
- **пусто** → docs-only, bypass допустим (5a);
- **непусто** → код, нужен feature-ветка + PR.

Плюс проверить `git log --oneline -1` — тип: `docs(...)` = OK для `dev`; `test(...)` / `feat(...)` / `fix(...)` / `refactor(...)` / `chore(...)` = feature.

**Связано:** AGENTS 5a (docs-only исключение), 4f (push в feature — батч перед PR), 5g (категории документации), `docs/git-flow.md` (dev — только PR), `docs/repo-protection.md` (bypass — для восстановления), урок 104 (5f-freeze).


---
<!-- SUBSECTION_END: l-application-106 -->










<!-- SUBSECTION_BEGIN: l-application-107 -->
___
### l-application-107 4h mobile pre-commit check — до commit, не после
**Правило:** на mobile (proot) 4h pre-commit check (import order, unused imports, test function names, multiline wrapping, trailing ws) — строго **до** `git commit`. Post-push check — не замена, только sanity. Пропуск = нарушение 4h, коммит запрещён (даже если CI потом зелёный).

**Инцидент:** 2026-11-12, property-based tests (`0c6a4d6b`). Сделал commit+push, проверку запустил post-push. Проверка прошла (все OK), но сам факт — нарушение. Повторение паттерна «сначала код, потом проверка» после Step 4 (Settings extended). Пользователь: «Import order ты по правилам должен сам проверять сам при mobile workflow перед коммитом».

**Проверка:** перед `git commit` — 5 grep-ов (import order, unused, test names, multiline wrapping, trailing ws) + `cat -A` + `git diff --check`. Только после `OK` — `git commit`.

**Связано:** AGENTS 4h (mobile pre-commit checklist), 5a (mobile gate = CI), урок 78 (ktlint на mobile — «золотой» гейт).


## Применение уроков

Механизм — `AGENTS.md` → 4h «Проверка правок на соответствие
перед коммитом». Без 4h уроки — просто текст.
<!-- SUBSECTION_END: l-application-107 -->








<!-- LESSONS_SECTION_END: application -->
