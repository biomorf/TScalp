#!/usr/bin/env python3
"""Rebuild index tables in AGENTS/AGENTS.md, AGENTS/AGENTS-LESSONS.md,
and the thin root pointers AGENTS.md, AGENTS-LESSONS.md.

Reads all AGENTS/AGENTS-*.md, extracts (file, id, title) from
"### <id> <title>" inside SUBSECTION_BEGIN/END, and regenerates
the "## Справочник" table. Idempotent: re-running produces the
same table.

Usage:
  rebuild_index.py --dry-run   # show new tables, do not write
  rebuild_index.py --apply     # write
"""
import argparse
import re
import sys
from pathlib import Path

BEGIN_RE = re.compile(r"^<!-- SUBSECTION_BEGIN: ([A-Za-z0-9_-]+) -->$")
END_RE = re.compile(r"^<!-- SUBSECTION_END: ([A-Za-z0-9_-]+) -->$")
HEADING_RE = re.compile(r"^### ([A-Za-z0-9_-]+) (.+)$")


def collect(root: str = "AGENTS") -> tuple[list[tuple[str, str, str]], list[tuple[str, str, str]]]:
    """Return (agents_rows, lessons_rows). Row = (id, title, file, anchor)."""
    agents: list[tuple[str, str, str, str]] = []
    lessons: list[tuple[str, str, str, str]] = []
    for f in sorted(Path(root).rglob("*.md")):
        if f.name in ("AGENTS.md", "AGENTS-LESSONS.md"):
            continue
        lines = f.read_text(encoding="utf-8").splitlines()
        cur_id = None
        cur_title = None
        for line in lines:
            mb = BEGIN_RE.match(line)
            if mb:
                cur_id = mb.group(1)
                cur_title = None
                continue
            me = END_RE.match(line)
            if me:
                if cur_id and cur_title:
                    row = (cur_id, cur_title, f"AGENTS/{f.name}", f"#{cur_id}")
                    if f.name.startswith("AGENTS-LESSONS-"):
                        lessons.append(row)
                    else:
                        agents.append(row)
                cur_id = None
                cur_title = None
                continue
            if cur_id:
                mh = HEADING_RE.match(line)
                if mh and mh.group(1) == cur_id:
                    cur_title = mh.group(2).strip()
    return agents, lessons


def build_table(rows: list[tuple[str, str, str, str]]) -> str:
    out = ["| ID | Заголовок | Файл | Якорь |", "|---|---|---|---|"]
    for sid, title, fpath, anchor in rows:
        # escape pipes in title
        title_esc = title.replace("|", "\\|")
        out.append(f"| {sid} | {title_esc} | {fpath} | {anchor} |")
    return "\n".join(out) + "\n"


def replace_table(text: str, new_table: str) -> str:
    """Replace the '## Справочник' table block (from header to next '---' or EOF)."""
    lines = text.splitlines(keepends=True)
    out: list[str] = []
    i = 0
    n = len(lines)
    replaced = False
    while i < n:
        if lines[i].strip() == "## Справочник (по мере необходимости)":
            out.append(lines[i])
            i += 1
            # skip blank/non-table lines until table start
            while i < n and not lines[i].startswith("|"):
                i += 1
            # skip table body (consecutive lines starting with "|")
            while i < n and lines[i].startswith("|"):
                i += 1
            out.append("\n")
            out.append(new_table)
            replaced = True
            continue
        out.append(lines[i])
        i += 1
    assert replaced, "## Справочник header not found"
    return "".join(out)


def rebuild(apply: bool, root: str = "AGENTS", index_paths: list[str] | None = None) -> int:
    """Regenerate index files from <root>/**/*.md, write to index_paths."""
    agents_rows, lessons_rows = collect(root)
    agents_table = build_table(agents_rows)
    lessons_table = build_table(lessons_rows)

    if index_paths is None:
        index_paths = ["AGENTS/AGENTS.md", "AGENTS/AGENTS-LESSONS.md",
                       "AGENTS.md", "AGENTS-LESSONS.md"]
    targets = []
    for ip in index_paths:
        if "LESSONS" in ip:
            targets.append((ip, lessons_table))
        else:
            targets.append((ip, agents_table))
    for path, table in targets:
        p = Path(path)
        assert p.exists(), f"missing: {path}"
        old = p.read_text(encoding="utf-8")
        new = replace_table(old, table)
        if apply:
            if new != old:
                p.write_text(new, encoding="utf-8")
                print(f"APPLIED {path}")
            else:
                print(f"OK (unchanged) {path}")
        else:
            if new != old:
                print(f"DRY {path}: would change ({len(old)} -> {len(new)} chars)")
            else:
                print(f"DRY {path}: unchanged")

    print(f"agents_rows={len(agents_rows)} lessons_rows={len(lessons_rows)}")
    return 0


def main():
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply", action="store_true")
    ap.add_argument("--root", default="AGENTS")
    ap.add_argument("--index-paths",
                    default="AGENTS/AGENTS.md,AGENTS/AGENTS-LESSONS.md,AGENTS.md,AGENTS-LESSONS.md")
    args = ap.parse_args()
    idx = [x.strip() for x in args.index_paths.split(",") if x.strip()]
    agents_rows, lessons_rows = collect(args.root)
    print(f"collected: agents={len(agents_rows)} lessons={len(lessons_rows)}")
    return rebuild(apply=args.apply, root=args.root, index_paths=idx)


if __name__ == "__main__":
    main()
