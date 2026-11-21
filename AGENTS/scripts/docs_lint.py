#!/usr/bin/env python3
"""Docs gate: check markdown against docs/docs-structure.md. Read-only.

Modes:
  --all          canonical scope: AGENTS/**, docs/roadmap/** and the root pointers
  --changed      .md files changed vs HEAD plus untracked ones
  --file PATH    a single file

Exit: 0 = all checks pass, 1 = at least one FAIL.
"""
import argparse
import re
import subprocess
import sys
from pathlib import Path

from docs_canon_lib import (
    ANCHOR_RE,
    ANY_BEGIN_RE,
    ANY_END_RE,
    FILE_BEGIN_LINE,
    FILE_END_LINE,
    HEADING_RE,
    NEXT_ID_RE,
    PAD_LINES,
    PREFIX_RE,
    ROADMAP_TOKENS,
    STATUS_RE,
    TOKEN_STATUS,
    expected_token,
    find_block,
)

MAX_ERRORS = 8
SKIP_DIRS = ("docs/issues", "docs/clean-audit")
SKIP_FILES = ("ROADMAP-ARCHIVE.md", "CI-ROADMAP-ARCHIVE.md")
SCOPE_DIRS = ("AGENTS", "docs/roadmap")
SCOPE_FILES = ("AGENTS.md", "AGENTS-LESSONS.md", "docs/ROADMAP.md", "docs/ci-roadmap.md")
ORDER = {"FILE": 1, "SECTION": 2, "SUBSECTION": 3}
ROW_RE = re.compile(r"^\| \[?([A-Za-z-]+)\]? \| ([A-Za-z0-9-]+) \|")
LINK_RE = re.compile(r"\]\(([^)#]+\.md)(#[^)]*)?\)")
FENCES = ("```", "~~~")


def collect_scope() -> list[Path]:
    out: list[Path] = []
    for base in SCOPE_DIRS:
        d = Path(base)
        if d.is_dir():
            out.extend(sorted(d.rglob("*.md")))
    for extra in SCOPE_FILES:
        p = Path(extra)
        if p.exists():
            out.append(p)
    keep = []
    for f in out:
        s = f.as_posix()
        if f.name in SKIP_FILES or any(s.startswith(d) for d in SKIP_DIRS):
            continue
        keep.append(f)
    return keep


def collect_changed() -> list[Path]:
    cmds = (
        ["git", "diff", "--name-only", "HEAD"],
        ["git", "diff", "--cached", "--name-only"],
        ["git", "ls-files", "--others", "--exclude-standard"],
    )
    names: set[str] = set()
    for cmd in cmds:
        r = subprocess.run(cmd, capture_output=True, text=True)
        names.update(x for x in r.stdout.splitlines() if x.endswith(".md"))
    return [Path(n) for n in sorted(names) if Path(n).exists()]


def check_file(f: Path) -> tuple[list[str], list[tuple[str, int]]]:
    errs: list[str] = []
    text = f.read_text(encoding="utf-8")
    lines = text.split("\n")
    if lines and lines[-1] == "":
        lines.pop()

    if not lines or lines[0] != FILE_BEGIN_LINE:
        errs.append("FILE_BEGIN is not the first line")
    if not lines or lines[-1] != FILE_END_LINE:
        errs.append("FILE_END is not the last line")
    if lines.count(FILE_BEGIN_LINE) != 1:
        errs.append(f"FILE_BEGIN count = {lines.count(FILE_BEGIN_LINE)}")
    if lines.count(FILE_END_LINE) != 1:
        errs.append(f"FILE_END count = {lines.count(FILE_END_LINE)}")

    has_h2 = any(l.startswith("## ") for l in lines)
    has_section = any((m := ANY_BEGIN_RE.match(l)) and m.group(1) == "SECTION" for l in lines)
    if has_h2 and not has_section:
        errs.append("file has ## sections but no SECTION markers (level 2)")

    fence: list[bool] = []
    inside = False
    for l in lines:
        fence.append(inside)
        if l.strip().startswith(FENCES):
            inside = not inside
    if sum(1 for l in lines if l.strip().startswith(FENCES)) % 2:
        errs.append("fence parity: odd number of fences")

    for i, l in enumerate(lines):
        if l != l.rstrip():
            errs.append(f"L{i+1}: trailing whitespace")
        if PREFIX_RE.search(l):
            errs.append(f"L{i+1}: prefixed marker")
        if not fence[i] and l.strip() == "___":
            errs.append(f"L{i+1}: ___ separator (forbidden)")
        if l.strip() == "---" and i + 1 < len(lines) and lines[i + 1].strip() == "---":
            errs.append(f"L{i+1}: double ---")

    stack: list[tuple[str, str]] = []
    for i, l in enumerate(lines):
        mb = ANY_BEGIN_RE.match(l)
        if mb:
            kind, mid = mb.group(1), mb.group(2)
            if stack and ORDER[kind] > ORDER[stack[-1][0]] + 1:
                errs.append(f"L{i+1}: {kind}_BEGIN without parent")
            stack.append((kind, mid))
            continue
        me = ANY_END_RE.match(l)
        if me:
            kind, mid = me.group(1), me.group(2)
            if not stack:
                errs.append(f"L{i+1}: {kind}_END without BEGIN")
                continue
            tk, tm = stack.pop()
            if (tk, tm) != (kind, mid):
                errs.append(f"L{i+1}: {kind}_END {mid} != {tk}_BEGIN {tm}")
    if stack:
        errs.append(f"unclosed BEGIN: {stack[-1][0]} {stack[-1][1]}")

    def blanks_before(i: int) -> tuple[int, int]:
        n = 0
        j = i - 1
        while j >= 0 and lines[j].strip() == "":
            n += 1
            j -= 1
        return n, j

    for i, l in enumerate(lines):
        mb = ANY_BEGIN_RE.match(l)
        if mb and mb.group(1) == "SECTION":
            n, j = blanks_before(i)
            first = n == 0 and j >= 0 and lines[j] == FILE_BEGIN_LINE
            if not first and n != PAD_LINES:
                errs.append(f"L{i+1}: SECTION_BEGIN needs {PAD_LINES} blanks, has {n}")
        if mb and mb.group(1) == "SUBSECTION":
            n, _ = blanks_before(i)
            if n != PAD_LINES:
                errs.append(f"L{i+1}: SUBSECTION_BEGIN needs {PAD_LINES} blanks, has {n}")
        me = ANY_END_RE.match(l)
        if me and me.group(1) == "SECTION":
            n, _ = blanks_before(i)
            if n != PAD_LINES:
                errs.append(f"L{i+1}: SECTION_END needs {PAD_LINES} blanks, has {n}")
        if l == FILE_END_LINE:
            n, _ = blanks_before(i)
            if n != PAD_LINES:
                errs.append(f"L{i+1}: FILE_END needs {PAD_LINES} blanks, has {n}")

    ranges: list[tuple[int, int, str]] = []
    open_sub = None
    for i, l in enumerate(lines):
        mb = ANY_BEGIN_RE.match(l)
        if mb and mb.group(1) == "SUBSECTION":
            open_sub = (i, mb.group(2))
        me = ANY_END_RE.match(l)
        if me and me.group(1) == "SUBSECTION" and open_sub and open_sub[1] == me.group(2):
            ranges.append((open_sub[0], i, open_sub[1]))
            open_sub = None

    tok = expected_token(f.name)
    for b, e, sid in ranges:
        if b + 1 >= len(lines):
            continue
        ma = ANCHOR_RE.match(lines[b + 1])
        if not ma:
            errs.append(f"L{b+1}: anchor missing right after SUBSECTION_BEGIN")
        elif ma.group(1) != sid:
            errs.append(f"L{b+2}: anchor id != {sid}")
        if b + 2 >= len(lines) or not lines[b + 2].startswith("### "):
            errs.append(f"L{b+1}: heading missing right after anchor")
            continue
        heading = lines[b + 2]
        if b + 3 < len(lines) and lines[b + 3].strip() == "":
            errs.append(f"L{b+4}: blank line right after heading")
        if e >= 2 and lines[e - 1].strip() != "---":
            errs.append(f"L{e}: --- missing before SUBSECTION_END")
        elif e >= 2 and lines[e - 2].strip() != "":
            errs.append(f"L{e-1}: exactly one blank needed before ---")
        elif e >= 3 and lines[e - 3].strip() == "":
            errs.append(f"L{e-2}: more than one blank before ---")
        for i in range(b + 3, max(b + 3, e - 2)):
            if not fence[i] and lines[i].strip() == "---":
                errs.append(f"L{i+1}: --- inside block body")
        if tok:
            mh = HEADING_RE.match(heading)
            want = sid.upper() if tok in ROADMAP_TOKENS else sid
            if not mh:
                errs.append(f"L{b+3}: heading is not '### [TOKEN] ID title'")
            else:
                if mh.group(1) != tok:
                    errs.append(f"L{b+3}: TOKEN [{mh.group(1)}] != [{tok}]")
                if mh.group(2) != want:
                    errs.append(f"L{b+3}: heading ID {mh.group(2)} != marker id {want}")
        if tok in ROADMAP_TOKENS:
            status = None
            for i in range(b + 3, e):
                ms = STATUS_RE.match(lines[i])
                if ms:
                    status = ms.group(1).strip().lower()
                    break
            if status is None:
                errs.append(f"L{b+1}: status line not found")
            elif not status.startswith(TOKEN_STATUS[tok]):
                errs.append(f"L{b+1}: status {status!r} != {TOKEN_STATUS[tok]!r}")

    return errs, [(sid, b) for b, e, sid in ranges]


def check_links(f: Path) -> list[str]:
    errs = []
    for m in LINK_RE.finditer(f.read_text(encoding="utf-8")):
        target = m.group(1)
        if target.startswith(("http", "/")):
            continue
        if not (f.parent / target).exists():
            errs.append(f"broken link -> {target}")
    return errs


SCRIPTS_DIR = Path("AGENTS/scripts")
SCRIPTS_README = Path("AGENTS/scripts/README.md")


def check_scripts_readme() -> list[str]:
    """Every top-level script must have a row in the scripts README."""
    if not SCRIPTS_README.exists():
        return [f"{SCRIPTS_README}: missing"]
    text = SCRIPTS_README.read_text(encoding="utf-8")
    errs = []
    for f in sorted(SCRIPTS_DIR.glob("*")):
        if not f.is_file() or f.suffix not in (".py", ".sh"):
            continue
        if f.name not in text:
            errs.append(f"{f.name}: no row in scripts README")
    return errs


def family_of(f: Path) -> str | None:
    tok = expected_token(f.name)
    if tok in ("AGENTS", "AGENTS-LESSONS"):
        return "AGENTS"
    if tok in ROADMAP_TOKENS:
        return "ROADMAP"
    return None


def check_next_id(path: str) -> list[str]:
    p = Path(path)
    if not p.exists():
        return []
    lines = p.read_text(encoding="utf-8").split("\n")
    m = next((m for m in (NEXT_ID_RE.match(l) for l in lines) if m), None)
    if not m:
        return [f"{path}: Next ID line missing"]
    nxt = int(m.group(2))
    best = 0
    for l in lines:
        mr = ROW_RE.match(l)
        if mr:
            mm = re.match(r"^[TC]-(\d+)$", mr.group(2))
            if mm:
                best = max(best, int(mm.group(1)))
    if nxt <= best:
        return [f"{path}: Next ID {m.group(1)}-{nxt} <= max {m.group(1)}-{best}"]
    return []


def check_task_state(task: str) -> list[str]:
    """Closed task lives in the archive; deferred one carries a Подтверждение field."""
    tid = task.lower()
    for name in ("ROADMAP-done.md", "ci-roadmap-done.md"):
        p = Path("docs/roadmap") / name
        if not p.exists():
            continue
        lines = p.read_text(encoding="utf-8").split("\n")
        try:
            b, e = find_block(lines, tid)
        except ValueError:
            continue
        heading = next((l for l in lines[b:e] if l.startswith("### ")), "")
        if heading.startswith("### [DONE]"):
            return []
    for name in ("ROADMAP-wip.md", "ROADMAP-plan.md", "ROADMAP-defer.md",
                 "ci-roadmap-wip.md", "ci-roadmap-plan.md", "ci-roadmap-defer.md"):
        p = Path("docs/roadmap") / name
        if not p.exists():
            continue
        lines = p.read_text(encoding="utf-8").split("\n")
        try:
            b, e = find_block(lines, tid)
        except ValueError:
            continue
        if any(l.startswith("**Подтверждение:**") for l in lines[b:e]):
            return []
    return [f"task {task}: not in *-done.md and no Подтверждение field"]


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Docs gate: markdown canon checks. SSoT: docs/docs-structure.md",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  %(prog)s --all\n"
            "  %(prog)s --changed\n"
            "  %(prog)s --file AGENTS/AGENTS-code.md\n"
        ),
    )
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--all", action="store_true", help="canonical scope (default)")
    g.add_argument("--changed", action="store_true", help="changed and untracked .md files")
    g.add_argument("--file", help="single file")
    g.add_argument("--task", help="verify ROADMAP state for a task id, e.g. T-45")
    args = ap.parse_args()

    if args.task:
        errs = check_task_state(args.task)
        for e in errs:
            print(f"FAIL {e}")
        if errs:
            return 1
        print(f"OK {args.task}: state accepted")
        return 0

    if args.file:
        files = [Path(args.file)]
    elif args.changed:
        files = collect_changed()
    else:
        files = collect_scope()

    per_file: dict[Path, list[str]] = {}
    subs: dict[Path, list[tuple[str, int]]] = {}
    for f in files:
        if not f.exists():
            per_file[f] = ["file not found"]
            continue
        errs, subs_f = check_file(f)
        errs.extend(check_links(f))
        per_file[f] = errs
        subs[f] = subs_f

    seen: dict[tuple[str, str], Path] = {}
    for f, items in subs.items():
        fam = family_of(f)
        if not fam:
            continue
        for sid, line_no in items:
            key = (fam, sid)
            if key in seen:
                per_file[seen[key]].append(f"duplicate id {sid!r} also in {f}:{line_no+1}")
                per_file[f].append(f"duplicate id {sid!r} also in {seen[key]}")
            else:
                seen[key] = f

    for msg in check_scripts_readme():
        per_file.setdefault(SCRIPTS_README, []).append(msg)

    for idx in ("docs/roadmap/ROADMAP.md", "docs/roadmap/ci-roadmap.md"):
        for msg in check_next_id(idx):
            p = Path(idx)
            per_file.setdefault(p, []).append(msg)

    failed_files = []
    total = 0
    for f, errs in per_file.items():
        if not errs:
            continue
        failed_files.append(f)
        total += len(errs)
        print(f"FAIL {f} ({len(errs)})")
        for e in errs[:MAX_ERRORS]:
            print(f"  - {e}")
        if len(errs) > MAX_ERRORS:
            print(f"  ... +{len(errs) - MAX_ERRORS} more")

    print("---")
    buckets: dict[str, int] = {}
    for f in failed_files:
        parts = f.parts
        key = "/".join(parts[:2]) if len(parts) > 1 else parts[0]
        buckets[key] = buckets.get(key, 0) + 1
    print("failed by dir:")
    for k in sorted(buckets):
        print(f"  {k}: {buckets[k]}")
    print("failed files:")
    for f in failed_files:
        print(f"  {f}")
    print("---")
    print(f"files={len(files)} failed={len(failed_files)} errors={total}")
    if failed_files:
        print("SOME CHECKS FAILED")
        return 1
    print("ALL CHECKS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
