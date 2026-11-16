#!/usr/bin/env python3
"""Insert a SUBSECTION block into an AGENTS/ file, then normalize.

Usage:
  add.py --file <path> --id <id> --title <title> --body-file <body.md>
         [--after <existing_id> | --at-end]

Body file is read as-is (rstrip at normalize time).
Idempotent: if SUBSECTION_BEGIN: <id> already in file, print "already present" and exit 0.
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from normalize import normalize_text  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", required=True)
    ap.add_argument("--id", required=True)
    ap.add_argument("--title", required=True)
    ap.add_argument("--body-file", required=True)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--after", help="insert after this existing id")
    g.add_argument("--at-end", action="store_true", help="append at end (before SECTION_END)")
    ap.add_argument("--apply", action="store_true", help="write (default: dry-run)")
    args = ap.parse_args()

    f = Path(args.file)
    assert f.exists(), f"file not found: {f}"
    text = f.read_text(encoding="utf-8")

    begin_tag = f"<!-- SUBSECTION_BEGIN: {args.id} -->"
    if begin_tag in text:
        print(f"already present: {args.id}")
        return 0

    body = Path(args.body_file).read_text(encoding="utf-8").rstrip() + "\n"

    block_lines = [
        begin_tag,
        "___",
        f"### {args.id} {args.title}",
        body.rstrip("\n"),
        f"<!-- SUBSECTION_END: {args.id} -->",
    ]
    block = "\n".join(block_lines) + "\n"

    if args.at_end:
        # insert before SECTION_END of this file
        import re
        m = re.search(r"^<!-- (AGENTS|LESSONS)_SECTION_END: [A-Za-z0-9_-]+ -->$",
                      text, re.MULTILINE)
        assert m, "SECTION_END not found"
        idx = m.start()
        new_text = text[:idx] + block + "\n" + text[idx:]
    else:
        end_tag = f"<!-- SUBSECTION_END: {args.after} -->"
        assert end_tag in text, f"--after id not found: {args.after}"
        idx = text.index(end_tag) + len(end_tag)
        new_text = text[:idx] + "\n" + block + text[idx:]

    # normalize the whole file
    new_text, devs = normalize_text(new_text)
    if devs:
        print(f"WARN: normalize deviations in target: {len(devs)} (should be 0 after normalize)")

    # post-check: block present
    assert begin_tag in new_text
    assert f"<!-- SUBSECTION_END: {args.id} -->" in new_text

    if args.apply:
        f.write_text(new_text, encoding="utf-8")
        print(f"APPLIED: {args.id} into {f}")
    else:
        print(f"DRY: would insert {args.id} into {f} (pass --apply)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
