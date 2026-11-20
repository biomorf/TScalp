#!/usr/bin/env python3
"""Rebuild canonical index tables for AGENTS / AGENTS-LESSONS / ROADMAP / ci-roadmap.

SSoT for formatting rules: docs/docs-structure.md (section 5).

Ordering: roadmap families follow status order (wip, plan, defer, done); rows inside
a file follow block order. Manual priority in the files is therefore preserved.
AGENTS families are alphabetical by file name.

Index format: | TOKEN | ID | Title | File | Anchor |
Anchor cell:  [#<id>](<File>#<id>)
"""
import argparse
import re
import sys
from pathlib import Path

HEADING_RE = re.compile(r"^### \[(WIP|PLAN|DEFER|DONE|AGENTS|AGENTS-LESSONS)\] ([A-Za-z0-9_-]+) (.+)$")
BEGIN_RE = re.compile(r"^<!-- SUBSECTION_BEGIN: ([A-Za-z0-9_-]+) -->$")
END_RE = re.compile(r"^<!-- SUBSECTION_END: ([A-Za-z0-9_-]+) -->$")
NEXT_RE = re.compile(r"^\*\*Next ID:\*\* ([TC])-(\d+)$")
ID_ANY_RE = re.compile(r"\b([TC])-(\d+)\b")

TABLE_HEADER = "| TOKEN | ID | Title | File | Anchor |"
TABLE_SEP = "|---|---|---|---|---|"

STATUS_ORDER = {
    "roadmap": ("ROADMAP-wip.md", "ROADMAP-plan.md", "ROADMAP-defer.md", "ROADMAP-done.md"),
    "ci-roadmap": ("ci-roadmap-wip.md", "ci-roadmap-plan.md",
                   "ci-roadmap-defer.md", "ci-roadmap-done.md"),
}
ARCHIVE = {
    "roadmap": "docs/roadmap/ROADMAP-ARCHIVE.md",
    "ci-roadmap": "docs/roadmap/CI-ROADMAP-ARCHIVE.md",
}

MODES = {
    "agents": {
        "index": "AGENTS/AGENTS.md",
        "section": "## Справочник (по мере необходимости)",
        "next_prefix": None,
    },
    "agents-lessons": {
        "index": "AGENTS/AGENTS-LESSONS.md",
        "section": "## Справочник (по мере необходимости)",
        "next_prefix": None,
    },
    "roadmap": {
        "index": "docs/roadmap/ROADMAP.md",
        "section": "## Задачи",
        "next_prefix": "T",
    },
    "ci-roadmap": {
        "index": "docs/roadmap/ci-roadmap.md",
        "section": "## Задачи",
        "next_prefix": "C",
    },
}


def files_for(mode: str) -> list[Path]:
    if mode == "agents":
        return sorted(p for p in Path("AGENTS").glob("AGENTS-*.md")
                      if not p.name.startswith("AGENTS-LESSONS-"))
    if mode == "agents-lessons":
        return sorted(Path("AGENTS").glob("AGENTS-LESSONS-*.md"))
    root = Path("docs/roadmap")
    return [root / name for name in STATUS_ORDER[mode] if (root / name).exists()]


def rows_for(f: Path) -> list[tuple[str, str, str, str]]:
    """Return (token, marker_id, heading_id, title) in block order."""
    rows = []
    marker_id = None
    for line in f.read_text(encoding="utf-8").splitlines():
        mb = BEGIN_RE.match(line)
        if mb:
            marker_id = mb.group(1)
            continue
        if END_RE.match(line):
            marker_id = None
            continue
        if marker_id is None:
            continue
        m = HEADING_RE.match(line)
        if m:
            rows.append((m.group(1), marker_id, m.group(2), m.group(3).strip()))
    return rows


def collect(mode: str) -> list[tuple[str, str, str, str, str]]:
    out = []
    for f in files_for(mode):
        for token, marker_id, heading_id, title in rows_for(f):
            out.append((token, marker_id, heading_id, title, f.as_posix()))
    return out


def build_table(rows) -> str:
    lines = [TABLE_HEADER, TABLE_SEP]
    for token, marker_id, heading_id, title, path in rows:
        safe = title.replace("|", "\\|")
        anchor = f"[#{marker_id}]({path}#{marker_id})"
        lines.append(f"| {token} | {heading_id} | {safe} | `{path}` | {anchor} |")
    return "\n".join(lines) + "\n"


def max_id(mode: str) -> int:
    best = 0
    for f in files_for(mode):
        for _, marker_id, _, _ in rows_for(f):
            m = ID_ANY_RE.search(marker_id)
            if m:
                best = max(best, int(m.group(2)))
    archive = Path(ARCHIVE[mode])
    if archive.exists():
        for m in ID_ANY_RE.finditer(archive.read_text(encoding="utf-8")):
            best = max(best, int(m.group(2)))
    return best


def replace_table(text: str, new_table: str, section: str, next_line: str | None) -> str:
    lines = text.splitlines(keepends=True)
    out = []
    i = 0
    replaced = False
    while i < len(lines):
        if lines[i].strip() == section:
            out.append(lines[i])
            i += 1
            while i < len(lines) and not lines[i].startswith("|"):
                if lines[i].strip().startswith("**Next ID:**"):
                    i += 1
                    continue
                out.append(lines[i])
                i += 1
            while i < len(lines) and lines[i].startswith("|"):
                i += 1
            while out and out[-1].strip() == "":
                out.pop()
            out.append("\n")
            if next_line:
                out.append(next_line + "\n")
            out.append("\n")
            out.append(new_table)
            replaced = True
            continue
        out.append(lines[i])
        i += 1
    if not replaced:
        raise AssertionError(f"section header not found: {section!r}")
    return "".join(out)


def rebuild(mode: str, apply: bool) -> int:
    cfg = MODES[mode]
    rows = collect(mode)
    table = build_table(rows)
    path = Path(cfg["index"])
    if not path.exists():
        print(f"SKIP missing index: {path}")
        return 0
    old = path.read_text(encoding="utf-8")

    next_line = None
    if cfg["next_prefix"]:
        want = max_id(mode) + 1
        existing = None
        for line in old.splitlines():
            m = NEXT_RE.match(line)
            if m:
                existing = int(m.group(2))
                break
        nxt = want if existing is None or existing < want else existing
        next_line = f"**Next ID:** {cfg['next_prefix']}-{nxt}"

    new = replace_table(old, table, cfg["section"], next_line)
    tok = sum(1 for r in rows)
    if apply:
        if new != old:
            path.write_text(new, encoding="utf-8")
            print(f"APPLIED {path} rows={tok}")
        else:
            print(f"OK unchanged {path} rows={tok}")
    else:
        print(f"DRY {path} rows={tok} changed={new != old}")
    return 0


def main():
    ap = argparse.ArgumentParser(
        description="Rebuild canonical index tables. SSoT: docs/docs-structure.md section 5.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  %(prog)s --dry-run\n"
            "  %(prog)s --apply --mode roadmap\n"
            "  %(prog)s --apply\n"
            "\n"
            "Order: wip, plan, defer, done; rows follow block order (priority preserved).\n"
            "Anchor: [#<id>](<File>#<id>). Next ID: max + 1, archive read once.\n"
        ),
    )
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply", action="store_true")
    ap.add_argument("--mode", default="all",
                    choices=["all", "agents", "agents-lessons", "roadmap", "ci-roadmap"])
    args = ap.parse_args()

    modes = list(MODES) if args.mode == "all" else [args.mode]
    rc = 0
    for m in modes:
        rc |= rebuild(m, apply=args.apply)
    return rc


if __name__ == "__main__":
    sys.exit(main())
