#!/usr/bin/env python3
"""Normalize Markdown files: FILE markers, padding, separators, rstrip, fence parity.

SSoT for formatting rules: docs/docs-structure.md
"""
import argparse
import re
import sys
from pathlib import Path

from docs_canon_lib import (  # noqa: E402
    BEGIN_RE,
    END_RE,
    FILE_BEGIN_LINE,
    FILE_END_LINE,
    PAD_LINES,
    PREFIX_RE,
    ROADMAP_TOKENS,
    SECTION_BEGIN_PREFIX,
    SECTION_END_PREFIX,
    TOKEN_BY_FILE,
    enforce_transitions as _enforce_transitions,
    expected_token as _detect_expected_token,
    normalize_heading as _normalize_heading,
    strip_prefix as _strip_prefix,
)


def normalize_text(text: str, file_path: str = "") -> tuple[str, list[str]]:
    """Return (new_text, deviations). Idempotent: if already normal, deviations = [].

    Rules (SSoT: docs/docs-structure.md):
    - FILE_BEGIN / FILE_END: first / last line, exactly one each.
    - 10 blank lines between SUBSECTION blocks; 10 before SECTION_END.
    - anchor <a id="id"></a> right after BEGIN; ### heading right after anchor.
    - --- before SUBSECTION_END, exactly one blank line before it.
    - rstrip every line; one trailing newline at EOF.
    """
    deviations: list[str] = []
    expected_token = _detect_expected_token(file_path) if file_path else None
    lines = text.splitlines(keepends=True)

    def strip_nl(s: str) -> str:
        return s[:-1] if s.endswith("\n") else s

    raw_lines = [strip_nl(l) for l in lines]

    prefix_fixed = 0
    for i, l in enumerate(raw_lines):
        fixed, changed = _strip_prefix(l)
        if changed:
            raw_lines[i] = fixed
            prefix_fixed += 1
    if prefix_fixed:
        deviations.append(f"prefix markers replaced: {prefix_fixed}")

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
        anchor = f'<a id="{sid}"></a>'

        try:
            h_idx = next(j for j, l in enumerate(body) if l.startswith("### "))
        except StopIteration:
            raise AssertionError(f"{sid}: no ### heading")

        pre = [l for l in body[:h_idx] if l.strip() not in ("", "___")]
        if not pre:
            deviations.append(f"{sid}: missing anchor")
        elif pre != [anchor]:
            deviations.append(f"{sid}: unexpected lines before ###: {pre[:2]}")

        heading = body[h_idx]
        if not heading.startswith("### "):
            deviations.append(f"{sid}: heading not ### ")
        else:
            heading, head_devs = _normalize_heading(heading, sid, expected_token)
            for d in head_devs:
                deviations.append(f"{sid}: {d}")

        rest = body[h_idx + 1:]
        if any(l.rstrip() != l for l in rest):
            deviations.append(f"{sid}: trailing whitespace")
        if any(l.strip() == "___" for l in rest):
            deviations.append(f"{sid}: ___ separator (forbidden; use --- before END)")
        if rest and rest[0].strip() == "":
            deviations.append(f"{sid}: blank after ###")

        body_out = [l.rstrip() for l in rest]
        while body_out and body_out[0] == "":
            body_out.pop(0)
        while body_out and body_out[-1] == "":
            body_out.pop()
        # trailing separator is canonical, not a stray: pop it before scanning
        if body_out and body_out[-1].strip() == "---":
            body_out.pop()
            while body_out and body_out[-1].strip() == "":
                body_out.pop()

        cleaned = []
        in_fence = False
        dropped = 0
        for l in body_out:
            s = l.strip()
            if s.startswith("```") or s.startswith("~~~"):
                in_fence = not in_fence
                cleaned.append(l)
                continue
            if not in_fence and s in ("---", "___"):
                dropped += 1
                continue
            cleaned.append(l)
        if dropped:
            deviations.append(f"{sid}: removed {dropped} stray separator(s) from body")
        body_out = cleaned
        while body_out and body_out[-1] == "":
            body_out.pop()
        # only the trailing separator is regenerated; --- inside body (fences) stays
        out.append(f"<!-- SUBSECTION_BEGIN: {sid} -->\n")
        out.append(anchor + "\n")
        out.append(heading + "\n")
        for l in body_out:
            out.append(l + "\n")
        out.append("\n")
        out.append("---\n")
        out.append(f"<!-- SUBSECTION_END: {sid} -->\n")
        if bi < len(blocks) - 1:
            out.append("\n" * 10)

    tail_lines = [strip_nl(l) for l in lines[last_end+1:]] if last_end >= 0 else []
    while tail_lines and tail_lines[-1].strip() == "":
        tail_lines.pop()
    # gap before SECTION_END / FILE_END is owned by _enforce_transitions
    for l in tail_lines:
        out.append(l + "\n")

    out, trans_fixed = _enforce_transitions(out)
    if trans_fixed:
        deviations.append(f"transition padding fixed: {trans_fixed}")

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
            "  %(prog)s --file docs/roadmap/ROADMAP-plan.md --dry-run\n"
            "  %(prog)s --file docs/roadmap/ROADMAP-plan.md --apply\n"
            "  %(prog)s --all AGENTS/AGENTS-code.md AGENTS/AGENTS-style-core.md --apply\n"
            "  %(prog)s --all AGENTS/AGENTS-code.md --dry-run\n"
            "\n"
            "Rules: FILE_BEGIN first / FILE_END last; 10 blanks between SUBSECTION blocks and before SECTION_END; anchor after BEGIN; --- before END; rstrip; fence parity. Exit: 0 ok, 1 deviations, 2 abort (structure broken)."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument("--apply", action="store_true", help="write files (default: dry-run)")
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
            new_text, devs = normalize_text(text, str(f))
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
