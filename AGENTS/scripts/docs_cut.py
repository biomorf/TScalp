#!/usr/bin/env python3
"""Cut a SUBSECTION block out of an AGENTS/ file, save to /tmp (fallback $HOME/tmp), normalize.

Usage:
  cut.py --file <path> --id <id> [--force] [--apply]

Output tmp: /tmp/agents-cut-<id>.md (fallback: $HOME/tmp/agents-cut-<id>.md).
Output body: /tmp/agents-body-<id>.md (fallback: $HOME/tmp/agents-body-<id>.md).
Idempotent: if SUBSECTION_BEGIN: <id> not in file, print "already absent" and exit 0.
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from docs_canon_lib import (  # noqa: E402
    ANCHOR_RE,
    ANY_BEGIN_RE,
    ANY_END_RE,
    find_block,
    tmp_path,
)
from docs_normalize import normalize_text  # noqa: E402


def extract_body(block_lines: list[str], sid: str) -> str:
    """Return the block body: no BEGIN, no anchor, no heading, no trailing --- and END."""
    body = list(block_lines)
    while body and (ANCHOR_RE.match(body[0]) or body[0].startswith("### ")):
        body.pop(0)
    while body and body[-1].strip() in ("", "---"):
        body.pop()
    assert body, f"{sid}: body is empty"
    assert not any(ANY_BEGIN_RE.match(l) or ANY_END_RE.match(l) for l in body), f"{sid}: body holds marker lines"
    assert not any(ANCHOR_RE.match(l) for l in body), f"{sid}: body holds anchor line"
    return "\n".join(body).rstrip() + "\n"

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", required=True)
    ap.add_argument("--id", required=True)
    ap.add_argument("--force", action="store_true", help="overwrite existing tmp")
    ap.add_argument("--apply", action="store_true", help="write (default: dry-run)")
    ap.add_argument("--no-rebuild", action="store_true",
                    help="skip index rebuild after apply (indexes frozen)")
    args = ap.parse_args()

    f = Path(args.file)
    assert f.exists(), f"file not found: {f}"
    text = f.read_text(encoding="utf-8")

    begin_tag = f"<!-- SUBSECTION_BEGIN: {args.id} -->"
    end_tag = f"<!-- SUBSECTION_END: {args.id} -->"

    if begin_tag not in text:
        print(f"already absent: {args.id}")
        return 0

    lines = text.split("\n")
    b, e = find_block(lines, args.id)
    block = "\n".join(lines[b:e + 1])
    new_text = "\n".join(lines[:b] + lines[e + 1:])

    new_text, devs = normalize_text(new_text, str(f))
    if devs:
        print(f"WARN: normalize deviations after cut: {len(devs)}")

    assert begin_tag not in new_text, "block still present after cut"

    out = tmp_path(f"agents-cut-{args.id}.md")
    body_out = tmp_path(f"agents-body-{args.id}.md")
    if out.exists() and not args.force:
        print(f"ERROR: {out} exists (use --force)")
        return 2
    body_text = extract_body(lines[b + 1:e], args.id)

    if args.apply:
        f.write_text(new_text, encoding="utf-8")
        out.write_text(block, encoding="utf-8")
        body_out.write_text(body_text, encoding="utf-8")
        print(f"CUT: {args.id} from {f} -> {out}")
        print(f"BODY: {body_out} ({body_text.count(chr(10))} lines)")
        if not args.no_rebuild:
            from docs_rebuild_index import rebuild
            rebuild(apply=True)
        else:
            print("SKIP: index rebuild (--no-rebuild)")
    else:
        print(f"DRY: would cut {args.id} from {f} -> {out}")
        print(f"DRY: would write body -> {body_out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
