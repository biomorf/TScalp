#!/usr/bin/env python3
"""One-shot: seed SUBSECTION_BEGIN/END markers before every ### in AGENTS/ files.

ID scheme:
  - numeric (r1, r4a, l-scripts-1, ...) — automatic
  - English slug (code-git-workflow-origin, ...) — automatic
  - Russian title without number — ID_OVERRIDES (manual map)
  - Russian title without override — ABORT (no secNNNN)

Does not touch ##. Does not add padding \\n (normalize.py later).
"""
import argparse
import re
from pathlib import Path

LESSONS_TOPICS = {
    "scripts", "kotlin", "diagnostics", "clean-audit",
    "ui", "ci", "estimates", "process", "application",
    "docs",
}

SKIP_FILES = {
    "AGENTS.md", "AGENTS-LESSONS.md",
    "AGENTS-preamble.md", "AGENTS-LESSONS-preamble.md",
    "AGENTS-report.md",
}

# (file_name, exact_title) -> id
ID_OVERRIDES: dict[tuple[str, str], str] = {
    # AGENTS-style.md
    ("AGENTS-style.md", "Ожидание merge: заморозка feature-ветки и dev"): "r5f-freeze",
    # AGENTS-code.md
    ("AGENTS-code.md", "Проект"): "code-project",
    ("AGENTS-code.md", "Рабочие сценарии: mobile (proot) и desktop (Bluefin)"): "code-workflows",
    ("AGENTS-code.md", "Идентификаторы инструментов"): "code-instrument-ids",
    ("AGENTS-code.md", "Перед правками"): "code-before-edits",
    ("AGENTS-code.md", "После правок"): "code-after-edits",
    ("AGENTS-code.md", "Самодостаточность инфраструктурных скриптов"): "code-self-contained-scripts",
    ("AGENTS-code.md", "Unit-тесты: mockk для suspend-функций"): "code-unit-mockk-suspend",
    ("AGENTS-code.md", "Git workflow: синхронизация с origin"): "code-git-workflow-origin",
    ("AGENTS-code.md", "Git workflow: ветки и PR"): "code-git-workflow-pr",
    ("AGENTS-evolution.md", "Workflow-находки фиксируются сразу"): "evolution-workflow",
    # AGENTS-project-docs.md
    ("AGENTS-project-docs.md", "Всё планируемое — в документах"): "project-docs-all-planned",
    ("AGENTS-project-docs.md", "Диаграммы в документации"): "project-docs-diagrams",
    ("AGENTS-project-docs.md", "Пометка о мобильной работе"): "project-docs-mobile",
    ("AGENTS-project-docs.md", "Порядок секций в плановых документах"): "project-docs-section-order",
    # AGENTS-evolution.md
    ("AGENTS-evolution.md", "Правки директив приоритетнее любой деятельности"): "evolution-priority",
    # AGENTS-integrity.md
    ("AGENTS-integrity.md", "Формат отчёта"): "integrity-report",
    # AGENTS-docs.md
    ("AGENTS-docs.md", "Формат отчёта"): "docs-report",
    # AGENTS-LESSONS-docs.md
    ("AGENTS-LESSONS-docs.md", "Обязательные файлы"): "l-docs-obligatory-files",
    ("AGENTS-LESSONS-docs.md", "Формат отчёта"): "l-docs-report-format",
    ("AGENTS-LESSONS-docs.md", "Причина"): "l-docs-reason",
}

NUM_RE = re.compile(r"^(\d+[a-z]?)\.\s+")
CYR_RE = re.compile(r"[А-Яа-яЁё]")


def slugify(title: str) -> str:
    s = title.lower()
    s = re.sub(r"[^a-z0-9]+", "-", s).strip("-")
    return s


def make_id(fname: str, num: str | None, title: str) -> str:
    # 1. explicit override
    key = (fname, title)
    if key in ID_OVERRIDES:
        return ID_OVERRIDES[key]
    # 2. style numeric
    if fname == "AGENTS-style.md":
        if num is not None:
            return f"r{num}"
        raise AssertionError(
            f"AGENTS-style.md: ### without number and no override: {title!r}\n"
            f"  add to ID_OVERRIDES: (\"AGENTS-style.md\", {title!r}): \"r...\""
        )
    # 3. lessons
    if fname.startswith("AGENTS-LESSONS-"):
        topic = fname[len("AGENTS-LESSONS-"):-len(".md")]
        assert topic in LESSONS_TOPICS, f"unknown lessons topic: {topic}"
        if num is not None:
            return f"l-{topic}-{num}"
        if CYR_RE.search(title):
            raise AssertionError(
                f"{fname}: Russian ### without number and no override: {title!r}\n"
                f"  add to ID_OVERRIDES"
            )
        return f"l-{topic}-{slugify(title)}"
    # 4. descriptive file
    base = fname[len("AGENTS-"):-len(".md")]
    if CYR_RE.search(title):
        raise AssertionError(
            f"{fname}: Russian ### without override: {title!r}\n"
            f"  add to ID_OVERRIDES: (\"{fname}\", {title!r}): \"{base}-...\""
        )
    return f"{base}-{slugify(title)}"


def process_file(path: Path, apply: bool) -> dict:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)

    header_idx: list[tuple[int, str, str]] = []
    in_fence = False
    for i, line in enumerate(lines):
        stripped = line.rstrip("\n")
        if stripped.startswith("```") or stripped.startswith("~~~"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        if stripped.startswith("### "):
            title = stripped[len("### "):].strip()
            m = NUM_RE.match(title)
            num = m.group(1) if m else None
            sid = make_id(path.name, num, title)
            header_idx.append((i, sid, title))

    if not header_idx:
        return {"file": path.name, "headers": 0, "skipped": True, "skip_reason": "no ###"}

    if "SUBSECTION_BEGIN" in text:
        return {"file": path.name, "headers": len(header_idx), "skipped": True,
                "skip_reason": "already has SUBSECTION_BEGIN"}

    hset = {h[0]: h for h in header_idx}
    out: list[str] = []
    open_id: str | None = None
    ids: list[str] = []

    def emit_end():
        nonlocal open_id
        if open_id is not None:
            out.append(f"<!-- SUBSECTION_END: {open_id} -->\n")
            open_id = None

    for i, line in enumerate(lines):
        stripped = line.rstrip("\n")
        if i in hset:
            emit_end()
            _, sid, title = hset[i]
            out.append(f"<!-- SUBSECTION_BEGIN: {sid} -->\n")
            out.append("___\n")
            m = NUM_RE.match(title)
            title_clean = title[m.end():] if m else title
            out.append(f"### {sid} {title_clean}\n")
            open_id = sid
            ids.append(sid)
            continue
        if stripped.startswith("<!-- AGENTS_SECTION_END:") or stripped.startswith("<!-- LESSONS_SECTION_END:"):
            emit_end()
            out.append(line)
            continue
        out.append(line)
    emit_end()

    new_text = "".join(out)
    stats = {"file": path.name, "headers": len(header_idx), "ids": ids, "skipped": False,
             "old_len": len(text), "new_len": len(new_text)}

    if apply:
        path.write_text(new_text, encoding="utf-8")
        check = path.read_text(encoding="utf-8")
        for sid in ids:
            assert f"SUBSECTION_BEGIN: {sid}" in check, f"post-assert: {sid} missing"
            assert f"SUBSECTION_END: {sid}" in check, f"post-assert END: {sid} missing"

    return stats


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--file")
    ap.add_argument("--all", action="store_true")
    args = ap.parse_args()

    if not args.file and not args.all:
        ap.error("specify --file <path> or --all")

    if args.file:
        files = [Path(args.file)]
    else:
        files = sorted(Path("AGENTS").glob("AGENTS-*.md"))
        files = [f for f in files if f.name not in SKIP_FILES and f.name != "AGENTS-LESSONS.md"]

    total_headers = 0
    total_files_touched = 0
    for f in files:
        if not f.exists():
            print(f"SKIP (missing): {f}")
            continue
        try:
            stats = process_file(f, apply=args.apply)
        except AssertionError as e:
            print(f"ABORT {f.name}: {e}")
            raise
        if stats["skipped"]:
            print(f"SKIP {f.name}: {stats.get('skip_reason', 'skipped')}")
            continue
        total_headers += stats["headers"]
        total_files_touched += 1
        print(f"{'APPLIED' if args.apply else 'DRY'} {f.name}: {stats['headers']} headers")
        print(f"  ids={stats['ids']}")

    print("---")
    print(f"total: files={total_files_touched}, headers={total_headers}, apply={args.apply}")
    if not args.apply:
        print("DRY-RUN: no files written. Re-run with --apply to write.")


if __name__ == "__main__":
    main()
