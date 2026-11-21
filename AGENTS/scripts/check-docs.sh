#!/usr/bin/env bash
# check-docs.sh — gate for the docs-structure canon (docs/docs-structure.md, section 6).
# Scope: AGENTS/**, docs/roadmap/** and the four root pointers.
# Other docs/*.md are out of scope until the docs migration task lands.
# Read-only. Exit 0 = all checks pass, 1 = at least one FAIL.
set -uo pipefail
REPO_ROOT="${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$REPO_ROOT" || { echo "FAIL: cannot cd to $REPO_ROOT"; exit 1; }

exec python3 - <<'PY'
import re
import sys
from pathlib import Path

PAD = 10
MAX_ERRORS_PER_FILE = 8
SKIP_DIRS = ("docs/issues", "docs/clean-audit", ".git", "__pycache__")
SKIP_FILES = ("ROADMAP-ARCHIVE.md", "CI-ROADMAP-ARCHIVE.md")

FILE_BEGIN = "<!-- FILE_BEGIN -->"
FILE_END = "<!-- FILE_END -->"
PREFIX_RE = re.compile(r"<!--\s*(AGENTS_|LESSONS_)(FILE|SECTION|SUBSECTION)_")
BEGIN_RE = re.compile(r"^<!-- (FILE|SECTION|SUBSECTION)_BEGIN(?:: ([A-Za-z0-9_-]+))? -->$")
END_RE = re.compile(r"^<!-- (FILE|SECTION|SUBSECTION)_END(?:: ([A-Za-z0-9_-]+))? -->$")
ANCHOR_RE = re.compile(r'^<a id="([A-Za-z0-9_-]+)"></a>$')
HEADING_RE = re.compile(r"^### \[([A-Z-]+)\] ([A-Za-z0-9_-]+) (.*)$")
NEXT_ID_RE = re.compile(r"^\*\*Next ID:\*\* ([TC])-(\d+)$")
ROW_RE = re.compile(r"^\| \[?([A-Za-z-]+)\]? \| ([A-Za-z0-9-]+) \|")
STATUS_RE = re.compile(r"^\*\*Статус:\*\*\s*(.+)$")

TOK_STATUS = {"WIP": "в работе", "PLAN": "запланировано", "DEFER": "отложено", "DONE": "выполнено"}
ROADMAP_TOKENS = {"WIP", "PLAN", "DEFER", "DONE"}
ORDER = {"FILE": 1, "SECTION": 2, "SUBSECTION": 3}


def expected_token(name):
    if name.startswith("AGENTS-LESSONS-") and name.endswith(".md"):
        return "AGENTS-LESSONS"
    if name.startswith("AGENTS-") and name.endswith(".md"):
        return "AGENTS"
    for tok in ("wip", "plan", "defer", "done"):
        if name == f"ROADMAP-{tok}.md" or name == f"ci-roadmap-{tok}.md":
            return tok.upper()
    return None


SCOPE_DIRS = ("AGENTS", "docs/roadmap")
SCOPE_FILES = ("AGENTS.md", "AGENTS-LESSONS.md", "docs/ROADMAP.md", "docs/ci-roadmap.md")


def gather():
    out = []
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
        if f.name in SKIP_FILES:
            continue
        if any(s.startswith(d) for d in SKIP_DIRS):
            continue
        keep.append(f)
    return keep


def check(f, ids_by_family):
    errs = []
    text = f.read_text(encoding="utf-8")
    lines = text.split("\n")
    if lines and lines[-1] == "":
        lines.pop()

    if not lines or lines[0] != FILE_BEGIN:
        errs.append("FILE_BEGIN is not the first line")
    if not lines or lines[-1] != FILE_END:
        errs.append("FILE_END is not the last line")
    if lines.count(FILE_BEGIN) != 1:
        errs.append(f"FILE_BEGIN count = {lines.count(FILE_BEGIN)}")
    if lines.count(FILE_END) != 1:
        errs.append(f"FILE_END count = {lines.count(FILE_END)}")

    has_h2 = any(l.startswith("## ") for l in lines)
    has_section = any(
        (m := BEGIN_RE.match(l)) and m.group(1) == "SECTION" for l in lines
    )
    if has_h2 and not has_section:
        errs.append("file has ## sections but no SECTION markers (level 2)")

    fence = []
    inside = False
    for l in lines:
        fence.append(inside)
        if l.strip().startswith(("```", "~~~")):
            inside = not inside

    for i, l in enumerate(lines):
        if PREFIX_RE.search(l):
            errs.append(f"L{i+1}: prefixed marker")
        if not fence[i] and l.strip() == "___":
            errs.append(f"L{i+1}: ___ separator (forbidden)")

    stack = []
    for i, l in enumerate(lines):
        mb = BEGIN_RE.match(l)
        if mb:
            kind, mid = mb.group(1), mb.group(2)
            if stack and ORDER[kind] > ORDER[stack[-1][0]] + 1:
                errs.append(f"L{i+1}: {kind}_BEGIN without parent (top is {stack[-1][0]})")
            stack.append((kind, mid))
            continue
        me = END_RE.match(l)
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

    def blanks_before(i):
        n = 0
        j = i - 1
        while j >= 0 and lines[j].strip() == "":
            n += 1
            j -= 1
        return n, j

    subs = []
    open_sub = None
    for i, l in enumerate(lines):
        mb = BEGIN_RE.match(l)
        if mb and mb.group(1) == "SUBSECTION":
            open_sub = (i, mb.group(2))
        me = END_RE.match(l)
        if me and me.group(1) == "SUBSECTION" and open_sub and open_sub[1] == me.group(2):
            subs.append((open_sub[0], i, open_sub[1]))
            open_sub = None

    for i, l in enumerate(lines):
        mb = BEGIN_RE.match(l)
        if mb and mb.group(1) == "SECTION":
            n, j = blanks_before(i)
            first_after_file = n == 0 and j >= 0 and lines[j] == FILE_BEGIN
            if not first_after_file and n != PAD:
                errs.append(f"L{i+1}: SECTION_BEGIN needs {PAD} blanks, has {n}")
        if mb and mb.group(1) == "SUBSECTION":
            n, _ = blanks_before(i)
            if n != PAD:
                errs.append(f"L{i+1}: SUBSECTION_BEGIN needs {PAD} blanks, has {n}")
        me = END_RE.match(l)
        if me and me.group(1) == "SECTION":
            n, _ = blanks_before(i)
            if n != PAD:
                errs.append(f"L{i+1}: SECTION_END needs {PAD} blanks, has {n}")
        if l == FILE_END:
            n, _ = blanks_before(i)
            if n != PAD:
                errs.append(f"L{i+1}: FILE_END needs {PAD} blanks, has {n}")

    tok = expected_token(f.name)
    for b, e, sid in subs:
        if b + 1 >= len(lines) or not ANCHOR_RE.match(lines[b + 1] or ""):
            errs.append(f"L{b+1}: anchor missing right after SUBSECTION_BEGIN")
        elif ANCHOR_RE.match(lines[b + 1]).group(1) != sid:
            errs.append(f"L{b+2}: anchor id != {sid}")
        if b + 2 >= len(lines) or not lines[b + 2].startswith("### "):
            errs.append(f"L{b+1}: heading missing right after anchor")
            continue
        heading = lines[b + 2]
        if tok:
            mh = HEADING_RE.match(heading)
            if not mh:
                errs.append(f"L{b+3}: heading is not '### [TOKEN] ID title'")
            else:
                htok, hid = mh.group(1), mh.group(2)
                if htok != tok:
                    errs.append(f"L{b+3}: TOKEN [{htok}] != [{tok}]")
                want = sid.upper() if tok in ROADMAP_TOKENS else sid
                if hid != want:
                    errs.append(f"L{b+3}: heading ID {hid} != marker id {want}")
        if b + 3 < len(lines) and lines[b + 3].strip() == "":
            errs.append(f"L{b+4}: blank line right after heading")
        if e >= 2 and lines[e - 1].strip() != "---":
            errs.append(f"L{e}: --- missing before SUBSECTION_END")
        if e >= 2 and lines[e - 2].strip() != "":
            errs.append(f"L{e-1}: exactly one blank line needed before ---")
        elif e >= 3 and lines[e - 3].strip() == "":
            errs.append(f"L{e-2}: more than one blank line before ---")
        for i in range(b + 3, max(b + 3, e - 2)):
            if not fence[i] and lines[i].strip() == "---":
                errs.append(f"L{i+1}: --- inside block body")

        if tok in ROADMAP_TOKENS:
            found = None
            for i in range(b + 3, e):
                ms = STATUS_RE.match(lines[i])
                if ms:
                    found = ms.group(1).strip().lower()
                    break
            if found is None:
                errs.append(f"L{b+1}: **Статус:** not found")
            elif not found.startswith(TOK_STATUS[tok]):
                errs.append(f"L{b+1}: status {found!r} != {TOK_STATUS[tok]!r}")

    if tok in ("AGENTS", "AGENTS-LESSONS"):
        family = "AGENTS"
    elif tok in ROADMAP_TOKENS:
        family = "ROADMAP"
    else:
        family = "other"
    for b, e, sid in subs:
        ids_by_family.setdefault(family, []).append((sid, f, b))

    return errs


def check_index(path, family_ids):
    errs = []
    p = Path(path)
    if not p.exists():
        return [f"{path}: missing"]
    lines = p.read_text(encoding="utf-8").split("\n")
    m = None
    for l in lines:
        m = NEXT_ID_RE.match(l)
        if m:
            break
    if not m:
        return [f"{path}: **Next ID:** line missing"]
    prefix, next_num = m.group(1), int(m.group(2))
    max_id = 0
    for l in lines:
        mr = ROW_RE.match(l)
        if mr:
            mid = mr.group(2)
            mm = re.match(r"^[TC]-(\d+)$", mid)
            if mm:
                max_id = max(max_id, int(mm.group(1)))
    if next_num <= max_id:
        errs.append(f"{path}: Next ID {prefix}-{next_num} <= max {prefix}-{max_id}")
    return errs


def main():
    files = gather()
    ids_by_family = {}
    failed = 0
    total_errs = 0
    failed_files = []
    for f in files:
        try:
            errs = check(f, ids_by_family)
        except Exception as exc:  # noqa: BLE001
            errs = [f"checker crashed: {exc}"]
        if errs:
            failed += 1
            failed_files.append(f)
            total_errs += len(errs)
            print(f"FAIL {f} ({len(errs)})")
            for e in errs[:MAX_ERRORS_PER_FILE]:
                print(f"  - {e}")
            if len(errs) > MAX_ERRORS_PER_FILE:
                print(f"  ... +{len(errs) - MAX_ERRORS_PER_FILE} more")

    for family, items in ids_by_family.items():
        seen = {}
        for sid, f, b in items:
            if sid in seen:
                prev_f, prev_b = seen[sid]
                print(f"FAIL duplicate id {sid!r} in {family}: {prev_f} L{prev_b+1} and {f} L{b+1}")
                failed += 1
            else:
                seen[sid] = (f, b)

    for path in ("docs/roadmap/ROADMAP.md", "docs/roadmap/ci-roadmap.md"):
        for e in check_index(path, ids_by_family):
            print(f"FAIL {e}")
            failed += 1

    buckets = {}
    for f in failed_files:
        parts = f.parts
        key = "/".join(parts[:2]) if len(parts) > 1 else str(parts[0])
        buckets[key] = buckets.get(key, 0) + 1
    print("---")
    print("failed by dir:")
    for k in sorted(buckets):
        print(f"  {k}: {buckets[k]}")
    print("failed files:")
    for f in failed_files:
        print(f"  {f}")
    print("---")
    print(f"files={len(files)} failed={failed} errors={total_errs}")
    if failed:
        print("SOME CHECKS FAILED")
        sys.exit(1)
    print("ALL CHECKS PASSED")
    sys.exit(0)


main()
PY
