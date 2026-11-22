<!-- FILE_BEGIN -->
<!-- SECTION_BEGIN: scripts-readme -->
## Скрипты AGENTS — README

Описание инструментов для работы с `AGENTS/` и `docs/`. SSoT по стилю —
`docs/docs-structure.md`; здесь — **как** применять инструменты.










<!-- SUBSECTION_BEGIN: tools -->
<a id="tools"></a>
### Инструменты
| Скрипт | Назначение | Аргументы | Пример |
|---|---|---|---|
| `docs_add.py` | Вставка подраздела | `--file`, `--id`, `--title`, `--body-file`, `--after` / `--at-end`, `--apply` | `python3 AGENTS/scripts/docs_add.py --file ... --id ... --title ... --body-file ... --after ... --apply` |
| `docs_cut.py` | Удаление подраздела; два выхода: блок (`agents-cut-<id>.md`) и тело (`agents-body-<id>.md`), оба в `/tmp`, fallback `$HOME/tmp` | `--file`, `--id`, `--force`, `--apply`, `--no-rebuild` | `python3 AGENTS/scripts/docs_cut.py --file ... --id ... --apply` |
| `docs_normalize.py` | Нормализация (10 пустых, `rstrip`, fence parity, `FILE`/`SECTION`/`SUBSECTION`, `---` перед `SUBSECTION_END`); между блоками допустимы только пустые строки, `---`, `___` — иначе падение без записи | `--file`, `--all` (явные имена), `--apply`, `--dry-run` | `python3 AGENTS/scripts/docs_normalize.py --dry-run --file ...` |
| `docs_rebuild_index.py` | Пересборка индексов (5 колонок, `Anchor`, `Next ID`) | `--mode` (`all` / `agents` / `agents-lessons` / `roadmap` / `ci-roadmap`), `--index-path-override`, `--dry-run`, `--apply` | `python3 AGENTS/scripts/docs_rebuild_index.py --apply --mode all` |
| `live-test-build.sh` | installDebug + force-stop | — | `bash AGENTS/scripts/live-test-build.sh` |
| `live-test-capture.sh` | Режим A (файл) | `CAPTURE_SECONDS` | `bash AGENTS/scripts/live-test-capture.sh` |
| `live-test-capture-interactive.sh` | Режим B (live) | — | `bash AGENTS/scripts/live-test-capture-interactive.sh` |
| `post-pr.sh` | 5f чек-лист (+ ROADMAP verify) | `<branch> [--task T-N]` | `bash AGENTS/scripts/post-pr.sh feature/... --task T-44` |
| `run-local-gate.sh` | Локальный гейт desktop: ktlint, lint, detekt, unit-тесты, Roborazzi verify | `--no-build-cache`, `--keep`, `--log` | `bash AGENTS/scripts/run-local-gate.sh` |
| `gate-commit.sh` | Гейт + коммит: вызывает `run-local-gate.sh`, при зелёном — `git add` явными путями, сверка индекса, `git commit -F`; push только по `--push` | `--msg-file`, `--files`, `--no-build-cache`, `--keep`, `--log`, `--branch`, `--push` | `bash AGENTS/scripts/gate-commit.sh --msg-file ~/.msg.txt --files a.md b.md --push` |
| `docs_lint.py` | Гейт канона: 17 проверок §6 + ссылки, trailing ws, fence parity | `--all` (область), `--changed`, `--file <path>`, `--task-state`, `--require-done` | `python3 AGENTS/scripts/docs_lint.py --all` |
| `docs_canon_lib.py` | Общая библиотека правил — не запускается, только импорт | — | `from docs_canon_lib import expected_token` |
| `inline.sh` | Каркас inline-блоков (`r4i`): тело со stdin исполняется отдельным `bash`, вывод в лог, `RC`, `wc -l` | `--log`, `--keep` | `bash AGENTS/scripts/inline.sh <<'BASH_END' … BASH_END` |

Одноразовые скрипты лежат в `AGENTS/scripts/oneoff/`; в таблицу выше не входят, повторно не запускаются (уже применены): `seed_markers.py`, `seed_markers_docs.py`.
`WARN: normalize deviations in target` у `docs_add.py` — ожидаемо при вставке в конец файла: нормализатор правит отступы на стыке с `SECTION_END`, а записывается уже нормализованный текст.

---
<!-- SUBSECTION_END: tools -->










<!-- SUBSECTION_BEGIN: usage -->
<a id="usage"></a>
### Вызов
**Запуск — всегда явно через `bash`**, без `./` (не зависит от exec-бита,
shebang, cwd; консистентно с 4g — без умолчаний):

```
bash AGENTS/scripts/<script>.sh [args]
python3 AGENTS/scripts/<script>.py [args]
```

**Вызов скрипта — тоже bash-блок** по `4i` шаблон 1 (heredoc, `tee`, `RC`,
`wc`, `exit`), не bare-строкой. Inline в правилах допустим только как
«что делает скрипт» (справка для чтения), не для запуска. Если скрипт
недоступен (нет файла / не та ОС) — зафиксировать причину в ответе;
иначе inline запрещён.

Причина: `evolution-workflow` (защита от повторения ошибок),
`5b` / `r5b` (не переизобретать текст для копирования).

---
<!-- SUBSECTION_END: usage -->










<!-- SUBSECTION_BEGIN: see-also -->
<a id="see-also"></a>
### См. также
- `docs/docs-structure.md` — SSoT по стилю.
- `docs/live-test.md` — live-test сценарии.
- `AGENTS/AGENTS-style-docs.md` → `r4j` — директивы по применению.

---
<!-- SUBSECTION_END: see-also -->










<!-- SECTION_END: scripts-readme -->










<!-- FILE_END -->
