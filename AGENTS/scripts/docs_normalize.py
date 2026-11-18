#!/usr/bin/env python3
"""Normalize Markdown files: FILE markers, padding, separators, rstrip, fence parity.

SSoT for formatting rules: docs/docs-structure.md
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
FILE_BEGIN_LINE = "<!-- FILE_BEGIN -->"
FILE_END_LINE = "<!-- FILE_END -->"


def normalize_text(text: str) -> tuple[str, list[str]]:
    """Return (new_text, deviations). Idempotent: if already normal, deviations = [].

    Rules (SSoT: docs/docs-structure.md):
    - FILE_BEGIN / FILE_END: first / last line, exactly one each.
    - 10 blank lines between SUBSECTION blocks; 10 before SECTION_END.
    - ___ after BEGIN, ### after ___; no leading/trailing blanks in body.
    - rstrip every line; one trailing newline at EOF.
    """
    deviations: list[str] = []
    lines = text.splitlines(keepends=True)

    def strip_nl(s: str) -> str:
        return s[:-1] if s.endswith("\n") else s

    raw_lines = [strip_nl(l) for l in lines]
    has_file_begin = any(l == FILE_BEGIN_LINE for l in raw_lines)
    has_file_end = any(l == FILE_END_LINE for l in raw_lines)
    if not has_file_begin:
        deviations.append("missing FILE_BEGIN")
        raw_lines.insert(0, FILE_BEGIN_LINE)
    elif raw_lines[0] != FILE_BEGIN_LINE:
        deviations.append("FILE_BEGIN not first line")
        raw_lines = [l for l in raw_lines if l != FILE_BEGIN_LINE]
        raw_lines.insert(0, FILE_BEGIN_LINE)
    if not has_file_end:
        deviations.append("missing FILE_END")
        raw_lines.append(FILE_END_LINE)
    elif raw_lines[-1] != FILE_END_LINE:
        deviations.append("FILE_END not last line")
        raw_lines = [l for l in raw_lines if l != FILE_END_LINE]
        raw_lines.append(FILE_END_LINE)

    lines = [l + "\n" for l in raw_lines]
    n = len(lines)

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
    for h in head_lines:
        out.append(h + "\n")
    if blocks:
        out.append("\n" * 10)

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
    while tail_lines and tail_lines[0].strip() == "":
        tail_lines.pop(0)
    while tail_lines and tail_lines[-1].strip() == "":
        tail_lines.pop()
    if tail_lines:
        out.append("\n" * 10)
        for l in tail_lines:
            out.append(l + "\n")

    new_text = "".join(out)
    if not new_text.endswith("\n"):
        new_text += "\n"
        deviations.append("no trailing newline")
    return new_text, deviations


def main():
    ap = argparse.ArgumentParser(
        description="Normalize Markdown files: FILE markers, 10-blank separators, rstrip, fence parity, SUBSECTION/SECTION structure. SSoT: docs/docs-structure.md",
        epilog=(
            "Examples:\n"
            "  %(prog)s --file docs/roadmap/ROADMAP-plan.md --check\n"
            "  %(prog)s --file docs/roadmap/ROADMAP-plan.md --apply\n"
            "  %(prog)s --all AGENTS/AGENTS-code.md AGENTS/AGENTS-style-core.md --apply\n"
            "  %(prog)s --all AGENTS/AGENTS-code.md --dry-run\n"
            "\n"
            "Rules: FILE_BEGIN first / FILE_END last; 10 blanks between SUBSECTION blocks and before SECTION_END; ___ after BEGIN; ### after ___; rstrip; fence parity. Exit: 0 ok, 1 deviations, 2 abort (structure broken)."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument("--apply", action="store_true", help="write files (default: check)")
    ap.add_argument("--check", action="store_true", help="exit 1 on deviations (default if no --apply)")
    ap.add_argument("--dry-run", action="store_true", help="show what would change, do not write")
    ap.add_argument("--file", help="process one file")
    ap.add_argument("--all", nargs="*", default=None, metavar="FILE", help="process explicit files (space-separated); --all with no args is an error")
    args = ap.parse_args()

    if args.file:
        files = [Path(args.file)]
    elif args.all is not None:
        if not args.all:
            ap.error("--all requires explicit filenames (e.g. --all AGENTS/AGENTS-code.md)")
        files = [Path(f) for f in args.all]
    else:
        ap.error("specify --file <path> or --all <file1> <file2> ...")

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
    if not args.apply and total_dev > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
