#!/usr/bin/env python3
"""Normalize AGENTS/ section files: padding, separators, rstrip, fence parity.

Canonical layout (per AGENTS/AGENTS-style.md 4j):
  <!-- SUBSECTION_BEGIN: <id> -->
  ___
  ### <id> <title>
  <body, every line rstrip'ed>
  <!-- SUBSECTION_END: <id> -->
  10x\\n
  <!-- SUBSECTION_BEGIN: <next_id> -->
  ...
  <!-- SUBSECTION_END: <last_id> -->
  4x\\n
  <!-- SECTION_END: <file_id> -->

Section boundaries:
  ## <title>  (4x\\n if preamble, 10x\\n if no preamble)  first SUBSECTION_BEGIN
  last SUBSECTION_END  (4x\\n)  SECTION_END

Exit codes:
  0 — ok (or --apply succeeded)
  1 — check/dry-run found deviations, or fence parity broken
  2 — usage / pre-assert error
"""
import argparse
import re
import sys
from pathlib import Path

BEGIN_RE = re.compile(r"^<!-- SUBSECTION_BEGIN: ([A-Za-z0-9_-]+) -->$")
END_RE = re.compile(r"^<!-- SUBSECTION_END: ([A-Za-z0-9_-]+) -->$")
SECTION_END_RE = re.compile(r"^<!-- (?:AGENTS_|LESSONS_)?SECTION_END: ([A-Za-z0-9_-]+) -->$")
SECTION_BEGIN_RE = re.compile(r"^<!-- (?:AGENTS_|LESSONS_)?SECTION_BEGIN: ([A-Za-z0-9_-]+) -->$")
H2_RE = re.compile(r"^## ")


def normalize_text(text: str) -> tuple[str, list[str]]:
    """Return (new_text, deviations). Idempotent: if already normal, deviations = [].

    Works on splitlines(keepends=True): every line already ends with \\n
    (except possibly the last one; we ensure the output ends with \\n).
    """
    deviations: list[str] = []
    lines = text.splitlines(keepends=True)
    n = len(lines)

    def strip_nl(s: str) -> str:
        return s[:-1] if s.endswith("\n") else s

    blocks = []
    cur_begin = None
    cur_id = None
    for i, raw in enumerate(lines):
        line = strip_nl(raw)
        mb = BEGIN_RE.match(line)
        if mb:
            if cur_begin is not None:
                raise AssertionError(f"nested BEGIN at line {i+1}")
            cur_begin = i
            cur_id = mb.group(1)
            continue
        me = END_RE.match(line)
        if me:
            if cur_begin is None:
                raise AssertionError(f"END without BEGIN at line {i+1}")
            if me.group(1) != cur_id:
                raise AssertionError(f"END id mismatch at line {i+1}")
            blocks.append((cur_begin, i, cur_id))
            cur_begin = None
            cur_id = None
    if cur_begin is not None:
        raise AssertionError(f"unclosed BEGIN: {cur_id}")

    fence_count = 0
    for raw in lines:
        s = strip_nl(raw).strip()
        if s.startswith("```") or s.startswith("~~~"):
            fence_count += 1
    if fence_count % 2 != 0:
        raise AssertionError(f"fence parity: {fence_count} (odd)")

    out: list[str] = []
    first_begin = blocks[0][0] if blocks else n
    last_end = blocks[-1][1] if blocks else -1

    head_lines = [strip_nl(l) for l in lines[:first_begin]]
    while head_lines and head_lines[-1].strip() == "":
        head_lines.pop()

    has_preamble = False
    for h in head_lines:
        s = h.strip()
        if s and not s.startswith("<!--") and not s.startswith("## "):
            has_preamble = True
            break

    for h in head_lines:
        out.append(h + "\n")
    if blocks:
        sep_n = 4 if has_preamble else 10
        out.append("\n" * sep_n)

    for bi, (b, e, sid) in enumerate(blocks):
        body = [strip_nl(l) for l in lines[b+1:e]]
        try:
            sep_idx = next(j for j, l in enumerate(body) if l.strip() == "___")
        except StopIteration:
            raise AssertionError(f"{sid}: no ___ separator")
        try:
            h_idx = next(j for j, l in enumerate(body) if l.startswith("### "))
        except StopIteration:
            raise AssertionError(f"{sid}: no ### heading")
        if h_idx != sep_idx + 1:
            deviations.append(f"{sid}: ___-to-### gap")
        if sep_idx != 0:
            deviations.append(f"{sid}: BEGIN-to-___ gap")
        heading = body[h_idx]
        # heading check: "### <sid> " for AGENTS/, "### [TOKEN] <ID> " for docs/.
        # We only require that a ### heading exists; exact prefix varies.
        if not heading.startswith("### "):
            deviations.append(f"{sid}: heading not ### ")
        rest = body[h_idx+1:]
        rest_stripped = [l.rstrip() for l in rest]
        if rest_stripped != rest:
            deviations.append(f"{sid}: trailing whitespace")
        if rest_stripped and rest_stripped[0] == "":
            deviations.append(f"{sid}: blank after ###")
        if rest_stripped and rest_stripped[-1] == "":
            deviations.append(f"{sid}: blank before END")

        out.append(f"<!-- SUBSECTION_BEGIN: {sid} -->\n")
        out.append("___\n")
        out.append(heading + "\n")
        body_out = list(rest_stripped)
        while body_out and body_out[0] == "":
            body_out.pop(0)
        while body_out and body_out[-1] == "":
            body_out.pop()
        for l in body_out:
            out.append(l + "\n")
        out.append(f"<!-- SUBSECTION_END: {sid} -->\n")
        if bi < len(blocks) - 1:
            out.append("\n" * 10)

    tail_lines = [strip_nl(l) for l in lines[last_end+1:]] if last_end >= 0 else []
    while tail_lines and tail_lines[-1].strip() == "":
        tail_lines.pop()
    if tail_lines:
        out.append("\n" * 4)
        for l in tail_lines:
            out.append(l + "\n")

    new_text = "".join(out)
    if not new_text.endswith("\n"):
        new_text += "\n"
        deviations.append("no trailing newline")
    return new_text, deviations


def check_index(idx_paths: list[str], root: str = "AGENTS") -> list[str]:
    """Cross-check AGENTS/AGENTS.md index vs actual SUBSECTION_BEGIN in files.

    Returns list of errors (empty = ok)."""
    errors = []
    idx_paths = [Path(x) for x in idx_paths]
    # scan only table rows (lines starting with "|"), extract IDs from all cells
    id_pat = re.compile(r"\b((?:t|c|i)-\d+|[HM]\d+[a-z]?|clean-audit-\d+|r[0-9][0-9a-z-]*|l-[a-z-]+-\d+|code-[a-z0-9-]+|project-docs-[a-z0-9-]+|evolution-[a-z0-9-]+|integrity-[a-z0-9-]+|docs-report|l-docs-[a-z0-9-]+)\b", re.IGNORECASE)
    index_ids = set()
    any_index = False
    for idx_path in idx_paths:
        if not idx_path.exists():
            continue
        any_index = True
        for line in idx_path.read_text(encoding="utf-8").splitlines():
            if not line.startswith("|"):
                continue
            for m in id_pat.finditer(line):
                index_ids.add(m.group(1).lower())
    if not any_index:
        return []
    # Actual
    actual = {}
    for f in Path(root).rglob("*.md"):
        if f.name in ("AGENTS.md", "AGENTS-LESSONS.md"):
            continue
        for m in re.finditer(r"^<!-- SUBSECTION_BEGIN: ([A-Za-z0-9_-]+) -->$",
                     f.read_text(encoding="utf-8"), re.MULTILINE):
            actual[m.group(1).lower()] = f.name
    # In index, not in files
    for sid in sorted(index_ids - set(actual)):
        errors.append(f"index has {sid}, but no SUBSECTION_BEGIN in files")
    # In files, not in index
    for sid in sorted(set(actual) - index_ids):
        errors.append(f"file has SUBSECTION_BEGIN {sid}, but not in index")
    # File path mismatch: skipped (universal ID pattern does not capture file column)
    return errors


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="write files (default: check)")
    ap.add_argument("--check", action="store_true", help="exit 1 on deviations (default if no --apply)")
    ap.add_argument("--dry-run", action="store_true", help="show diff, do not write")
    ap.add_argument("--file", help="process only this file")
    ap.add_argument("--all", action="store_true", help="process all AGENTS/AGENTS-*.md")
    ap.add_argument("--check-index", action="store_true", help="also cross-check index files")
    ap.add_argument("--root", default="AGENTS", help="root dir (default: AGENTS)")
    ap.add_argument("--index-paths", default="AGENTS/AGENTS.md,AGENTS/AGENTS-LESSONS.md",
                    help="comma-separated index files for --check-index")
    args = ap.parse_args()

    if not args.file and not args.all:
        ap.error("specify --file <path> or --all")

    if args.file:
        files = [Path(args.file)]
    else:
        files = sorted(Path(args.root).rglob("*.md"))
        files = [f for f in files if f.name not in ("AGENTS.md", "AGENTS-LESSONS.md")]

    total_dev = 0
    total_written = 0
    all_deviations: list[str] = []
    for f in files:
        if not f.exists():
            print(f"SKIP (missing): {f}")
            continue
        text = f.read_text(encoding="utf-8")
        try:
            new_text, devs = normalize_text(text)
        except AssertionError as e:
            print(f"ABORT {f.name}: {e}")
            sys.exit(2)
        if devs:
            total_dev += len(devs)
            all_deviations.extend(f"{f.name}: {d}" for d in devs)
            if not args.apply and not args.dry_run:
                for d in devs:
                    print(f"DEVIATION {f.name}: {d}")
        if args.apply:
            if new_text != text:
                f.write_text(new_text, encoding="utf-8")
                total_written += 1
                print(f"APPLIED {f.name}")
            else:
                print(f"OK (already normal) {f.name}")
        elif args.dry_run:
            if new_text != text:
                print(f"DRY {f.name}: would change ({len(devs)} deviations)")

    print("---")
    print(f"files_checked={len(files)} files_written={total_written} deviations={total_dev}")
    if args.check_index:
        errors = check_index([x.strip() for x in args.index_paths.split(",") if x.strip()], args.root)
        if errors:
            print(f"--- check-index: {len(errors)} error(s) ---")
            for e in errors:
                print(f"  {e}")
            sys.exit(1)
        else:
            print("check-index: OK (index and SUBSECTION_BEGIN consistent)")

    if not args.apply and total_dev > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
