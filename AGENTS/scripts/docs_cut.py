#!/usr/bin/env python3
"""Cut a SUBSECTION block out of an AGENTS/ file, save to /tmp (fallback $HOME/tmp), normalize.

Usage:
  cut.py --file <path> --id <id> [--force] [--apply]

Output tmp: /tmp/agents-cut-<id>.md (fallback: $HOME/tmp/agents-cut-<id>.md).
Idempotent: if SUBSECTION_BEGIN: <id> not in file, print "already absent" and exit 0.
"""
import argparse
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from docs_normalize import normalize_text  # noqa: E402


def tmp_path(id_: str) -> Path:
    """tmp policy (AGENTS-style-docs.md 4k): primary /tmp, fallback $HOME/tmp."""
    primary = Path("/tmp")
    if primary.is_dir() and os.access(primary, os.W_OK):
        return primary / f"agents-cut-{id_}.md"
    home_tmp = Path.home() / "tmp"
    home_tmp.mkdir(parents=True, exist_ok=True)
    return home_tmp / f"agents-cut-{id_}.md"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", required=True)
    ap.add_argument("--id", required=True)
    ap.add_argument("--force", action="store_true", help="overwrite existing tmp")
    ap.add_argument("--apply", action="store_true", help="write (default: dry-run)")
    args = ap.parse_args()

    f = Path(args.file)
    assert f.exists(), f"file not found: {f}"
    text = f.read_text(encoding="utf-8")

    begin_tag = f"<!-- SUBSECTION_BEGIN: {args.id} -->"
    end_tag = f"<!-- SUBSECTION_END: {args.id} -->"

    if begin_tag not in text:
        print(f"already absent: {args.id}")
        return 0

    bi = text.index(begin_tag)
    ei = text.index(end_tag) + len(end_tag)
    block = text[bi:ei]

    # Remove block + surrounding padding
    before = text[:bi].rstrip()
    after = text[ei:].lstrip()
    new_text = before + "\n" + after

    new_text, devs = normalize_text(new_text)
    if devs:
        print(f"WARN: normalize deviations after cut: {len(devs)}")

    assert begin_tag not in new_text, "block still present after cut"

    out = tmp_path(args.id)
    if out.exists() and not args.force:
        print(f"ERROR: {out} exists (use --force)")
        return 2

    if args.apply:
        f.write_text(new_text, encoding="utf-8")
        out.write_text(block, encoding="utf-8")
        print(f"CUT: {args.id} from {f} -> {out}")
        from docs_rebuild_index import rebuild
        rebuild(apply=True)
    else:
        print(f"DRY: would cut {args.id} from {f} -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
