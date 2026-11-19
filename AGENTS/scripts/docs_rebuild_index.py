#!/usr/bin/env python3
"""Rebuild index tables for AGENTS / AGENTS-LESSONS / ROADMAP / ci-roadmap.

SSoT for formatting rules: docs/docs-structure.md

Modes:
  agents         — AGENTS/AGENTS-*.md (excl. AGENTS-LESSONS-*) -> AGENTS/AGENTS.md
  agents-lessons — AGENTS/AGENTS-LESSONS-*.md -> AGENTS/AGENTS-LESSONS.md
  roadmap        — docs/roadmap/ROADMAP-*.md -> docs/roadmap/ROADMAP.md
  ci-roadmap     — docs/roadmap/ci-roadmap-*.md -> docs/roadmap/ci-roadmap.md
  all            — all four (default)

Index format: | TOKEN | ID | Title | File | Anchor |
Heading format: ### [TOKEN] <ID> <title>
Anchor: #<id> (short, stable, derived from SUBSECTION id; for cutting/pasting
        use SUBSECTION_BEGIN/END: <id>, not the anchor).
"""
import argparse
import re
import sys
from pathlib import Path

HEADING_RE = re.compile(
    r"^### \[(WIP|PLAN|DEFER|DONE|AGENTS|AGENTS-LESSONS)\] ([A-Za-z0-9_-]+) (.+)$"
)
BEGIN_RE = re.compile(r"^<!-- SUBSECTION_BEGIN: ([A-Za-z0-9_-]+) -->$")
END_RE = re.compile(r"^<!-- SUBSECTION_END: ([A-Za-z0-9_-]+) -->$")

TABLE_HEADER = "| TOKEN | ID | Title | File | Anchor |"
TABLE_SEP = "|---|---|---|---|---|"

MODE_CONFIG = {
    "agents": {
        "root": "AGENTS",
        "prefix": "AGENTS-",
        "exclude": ("AGENTS.md", "AGENTS-LESSONS.md", "AGENTS-LESSONS-"),
        "table_section": "## Справочник (по мере необходимости)",
        "index_paths": ["AGENTS/AGENTS.md"],
    },
    "agents-lessons": {
        "root": "AGENTS",
        "prefix": "AGENTS-LESSONS-",
        "exclude": (),
        "table_section": "## Справочник (по мере необходимости)",
        "index_paths": ["AGENTS/AGENTS-LESSONS.md"],
    },
    "roadmap": {
        "root": "docs/roadmap",
        "prefix": "ROADMAP-",
        "exclude": ("ROADMAP.md", "ROADMAP-ARCHIVE.md", "ROADMAP-task-template.md"),
        "table_section": "## Задачи",
        "index_paths": ["docs/roadmap/ROADMAP.md"],
    },
    "ci-roadmap": {
        "root": "docs/roadmap",
        "prefix": "ci-roadmap-",
        "exclude": ("ci-roadmap.md", "CI-ROADMAP-ARCHIVE.md", "ROADMAP-task-template.md"),
        "table_section": "## Задачи",
        "index_paths": ["docs/roadmap/ci-roadmap.md"],
    },
}


def collect(mode: str) -> list[tuple[str, str, str, str, str]]:
    """Return rows: (token, id, title, file, anchor). Anchor = #<id> (from SUBSECTION id)."""
    cfg = MODE_CONFIG[mode]
    root = Path(cfg["root"])
    rows: list[tuple[str, str, str, str, str]] = []
    if not root.exists():
        return rows
    for f in sorted(root.rglob("*.md")):
        if not f.name.startswith(cfg["prefix"]):
            continue
        if any(f.name == e or f.name.startswith(e) for e in cfg["exclude"]):
            continue
        rel = f.as_posix()
        lines = f.read_text(encoding="utf-8").splitlines()
        cur_begin = False
        for line in lines:
            if BEGIN_RE.match(line):
                cur_begin = True
                continue
            if END_RE.match(line):
                cur_begin = False
                continue
            if not cur_begin:
                continue
            m = HEADING_RE.match(line)
            if m:
                token, sid, title = m.group(1), m.group(2), m.group(3).strip()
                rows.append((token, sid, title, rel, "#" + sid))
    return rows


def build_table(rows: list[tuple[str, str, str, str, str]]) -> str:
    out = [TABLE_HEADER, TABLE_SEP]
    for token, sid, title, fpath, anchor in rows:
        title_esc = title.replace("|", "\\|")
        out.append(f"| {token} | {sid} | {title_esc} | `{fpath}` | {anchor} |")
    return "\n".join(out) + "\n"


def replace_table(text: str, new_table: str, section_header: str) -> str:
    """Replace the table under section_header (from header to next '---' or EOF)."""
    lines = text.splitlines(keepends=True)
    out: list[str] = []
    i = 0
    n = len(lines)
    replaced = False
    while i < n:
        if lines[i].strip() == section_header:
            out.append(lines[i])
            i += 1
            while i < n and not lines[i].startswith("|"):
                i += 1
            while i < n and lines[i].startswith("|"):
                i += 1
            out.append("\n")
            out.append(new_table)
            replaced = True
            continue
        out.append(lines[i])
        i += 1
    assert replaced, f"section header not found: {section_header!r}"
    return "".join(out)


def rebuild(mode: str, index_path_override: list[str] | None = None, apply: bool = False) -> int:
    cfg = MODE_CONFIG[mode]
    rows = collect(mode)
    table = build_table(rows)
    targets = index_path_override if index_path_override else cfg["index_paths"]
    for path in targets:
        p = Path(path)
        assert p.exists(), f"missing: {path}"
        old = p.read_text(encoding="utf-8")
        new = replace_table(old, table, cfg["table_section"])
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
    print(f"mode={mode} rows={len(rows)}")
    return 0


def main():
    ap = argparse.ArgumentParser(
        description="Rebuild index tables for AGENTS / AGENTS-LESSONS / ROADMAP / ci-roadmap. SSoT: docs/docs-structure.md",
        epilog=(
            "Examples:\n"
            "  %(prog)s --dry-run\n"
            "  %(prog)s --apply --mode roadmap\n"
            "  %(prog)s --apply --mode all\n"
            "  %(prog)s --apply --index-path-override docs/roadmap/ROADMAP.md\n"
            "\n"
            "Modes: all (default) | agents | agents-lessons | roadmap | ci-roadmap.\n"
            "Index format: | TOKEN | ID | Title | File | Anchor |.\n"
            "Heading: ### [TOKEN] <ID> <title>. Anchor: #<id> (from SUBSECTION id).\n"
            "For cutting/pasting use SUBSECTION_BEGIN/END: <id>, not the anchor.\n"
            "Note: AGENTS index is empty until headings get [AGENTS]/[AGENTS-LESSONS]\n"
            "(step 5 of T-44 migration). --apply is safe: it writes the empty table."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true", help="show what would change")
    g.add_argument("--apply", action="store_true", help="write index files")
    ap.add_argument("--mode", default="all",
                    choices=["all", "agents", "agents-lessons", "roadmap", "ci-roadmap"],
                    help="which index to rebuild (default: all)")
    ap.add_argument("--index-path-override", nargs="*", default=None, metavar="PATH",
                    help="override default index paths (space-separated)")
    args = ap.parse_args()

    modes = ["agents", "agents-lessons", "roadmap", "ci-roadmap"] if args.mode == "all" else [args.mode]
    rc = 0
    for m in modes:
        rc |= rebuild(m, index_path_override=args.index_path_override, apply=args.apply)
    return rc


if __name__ == "__main__":
    sys.exit(main())
