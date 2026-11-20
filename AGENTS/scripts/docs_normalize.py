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

PREFIX_RE = re.compile(
    r"^<!-- (?:AGENTS_|LESSONS_)(FILE|SECTION|SUBSECTION)_(BEGIN|END)(?:: ([A-Za-z0-9_-]+))? -->$"
)


def _strip_prefix(line: str) -> tuple[str, bool]:
    """Replace prefixed markers (AGENTS_FILE_BEGIN, LESSONS_SECTION_END) with plain ones."""
    m = PREFIX_RE.match(line)
    if not m:
        return line, False
    kind, bound, mid = m.group(1), m.group(2), m.group(3)
    plain = f"<!-- {kind}_{bound}: {mid} -->" if mid else f"<!-- {kind}_{bound} -->"
    return plain, True


TOKEN_BY_FILE = {
    "ROADMAP-plan.md": "PLAN",
    "ROADMAP-defer.md": "DEFER",
    "ROADMAP-wip.md": "WIP",
    "ROADMAP-done.md": "DONE",
    "ci-roadmap-plan.md": "PLAN",
    "ci-roadmap-defer.md": "DEFER",
    "ci-roadmap-wip.md": "WIP",
    "ci-roadmap-done.md": "DONE",
}


def _detect_expected_token(file_path: str) -> str | None:
    """Return expected TOKEN for file_path, or None if not applicable."""
    name = Path(file_path).name
    if name in TOKEN_BY_FILE:
        return TOKEN_BY_FILE[name]
    if name.startswith("AGENTS-LESSONS-") and name.endswith(".md"):
        return "AGENTS-LESSONS"
    if name.startswith("AGENTS-") and name.endswith(".md") and not name.startswith("AGENTS-LESSONS-"):
        return "AGENTS"
    return None


ROADMAP_TOKENS = {"WIP", "PLAN", "DEFER", "DONE"}

ID_WORD_RE = re.compile(r"[A-Za-z][A-Za-z0-9-]*\Z")


def _looks_like_id(word: str) -> bool:
    """True for marker-id-like words (code-project, r4k, T-44); titles do not match."""
    if not ID_WORD_RE.fullmatch(word):
        return False
    return any(c.isdigit() or c == "-" for c in word)


def _normalize_heading(heading: str, sid: str, expected_token: str | None) -> tuple[str, list[str]]:
    """Return '### [TOKEN] <ID> <title>' for level-3 families, plus deviations.

    expected_token is None for files outside AGENTS / AGENTS-LESSONS / ROADMAP /
    ci-roadmap families: their headings are left untouched.
    """
    devs: list[str] = []
    if expected_token is None:
        return heading, devs

    body = heading[4:]
    cur_tok = None
    m = re.match(r"^\[([A-Za-z-]+)\] (.*)$", body)
    if m:
        cur_tok = m.group(1)
        body = m.group(2)

    want_id = sid.upper() if expected_token in ROADMAP_TOKENS else sid

    parts = body.split(" ", 1)
    first = parts[0] if parts else ""
    if first.lower() == sid.lower() or _looks_like_id(first):
        got_id, title = first, (parts[1] if len(parts) == 2 else "")
    else:
        got_id, title = None, body

    if cur_tok is None:
        devs.append(f"missing TOKEN (expected [{expected_token}])")
    elif cur_tok != expected_token:
        devs.append(f"TOKEN mismatch: [{cur_tok}] (expected [{expected_token}])")

    if got_id is None:
        devs.append(f"missing heading ID (expected {want_id!r})")
    elif got_id != want_id:
        devs.append(f"heading ID {got_id!r} != {want_id!r}")

    out = f"### [{expected_token}] {want_id}"
    if title:
        out += f" {title}"
    return out, devs


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
        body_out = [l for l in body_out if l.strip() not in ("___", "---")]
        while body_out and body_out[0] == "":
            body_out.pop(0)
        while body_out and body_out[-1] == "":
            body_out.pop()

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
            "Rules: FILE_BEGIN first / FILE_END last; 10 blanks between SUBSECTION blocks and before SECTION_END; anchor after BEGIN; --- before END; rstrip; fence parity. Exit: 0 ok, 1 deviations, 2 abort (structure broken)."
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
