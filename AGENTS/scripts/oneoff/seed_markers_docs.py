#!/usr/bin/env python3
"""ONE-OFF: seed SECTION_BEGIN/END (##) and SUBSECTION_BEGIN/END (###)
in docs/*.md. Does NOT touch archive files.

ID scheme:
  docs/ROADMAP.md          -> section: roadmap,     sub: t-<N>
  docs/ci-roadmap.md       -> section: ci-roadmap,  sub: c-<N>
  docs/ISSUES.md           -> section: issues,      sub: i-<N>
  docs/clean-audit.md      -> section: clean-audit, sub: Н<N> / M<N> (keep)
  docs/<file>.md           -> section: <file>,      sub: <file>-<slug>

Does not add [WIP]/[PLAN]/... tokens. Does not touch archives:
  ROADMAP-ARCHIVE.md, CI-ROADMAP-ARCHIVE.md.

DO NOT RE-RUN after Phase 0 apply. Kept for history.

Usage: seed_markers_docs.py --dry-run | --apply [--file <path> | --all]
"""
import argparse
import re
import sys
from pathlib import Path

ARCHIVES = {"ROADMAP-ARCHIVE.md", "CI-ROADMAP-ARCHIVE.md"}
NUM_RE = re.compile(r"^(\d+[a-z]?)\.\s+")
CYR_RE = re.compile(r"[А-Яа-яЁё]")

OVERRIDES: dict[tuple[str, str], str] = {
    ("ROADMAP.md", "Планируемые задачи"): "roadmap-plan",
    ("ROADMAP.md", "Закрытые (ссылка на ROADMAP-ARCHIVE.md)"): "roadmap-closed",
    ("ROADMAP.md", "Шаблон задачи"): "roadmap-template",
    ("ci-roadmap.md", "Планируемые задачи"): "ci-roadmap-plan",
    ("ci-roadmap.md", "Закрытые (ссылка на CI-ROADMAP-ARCHIVE.md)"): "ci-roadmap-closed",
    ("ci-roadmap.md", "Шаблон задачи"): "ci-roadmap-template",
}


def slugify(title: str) -> str:
    s = title.lower()
    s = re.sub(r"[^a-z0-9]+", "-", s).strip("-")
    return s


def section_id(fname: str) -> str:
    return fname[:-3].lower()


def sub_id(fname: str, num: str | None, title: str, seq: int):
    if fname == "clean-audit.md":
        m = re.match(r"^(✅\s+)?(Н|M)(\d+[a-z]?)\.\s+", title)
        if m:
            return f"{m.group(2)}{m.group(3)}"
        return f"clean-audit-{seq}"
    if fname == "ROADMAP.md":
        return f"t-{seq}"
    if fname == "ci-roadmap.md":
        return f"c-{seq}"
    if fname == "ISSUES.md":
        return f"i-{seq}"
    base = fname[:-3]
    return f"{base}-{seq}"


def process_file(path: Path, apply: bool) -> dict:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)

    h2: list[tuple[int, str]] = []
    h3: list[tuple[int, str, str]] = []
    seq = 0
    in_fence = False
    for i, raw in enumerate(lines):
        s = raw.rstrip("\n")
        if s.startswith("```") or s.startswith("~~~"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        if s.startswith("## "):
            h2.append((i, s[3:].strip()))
        elif s.startswith("### "):
            title = s[4:].strip()
            m = NUM_RE.match(title)
            num = m.group(1) if m else None
            seq += 1
            sid = sub_id(path.name, num, title, seq)
            h3.append((i, sid, title))

    if not h2 and not h3:
        return {"file": path.name, "h2": 0, "h3": 0, "skipped": True,
                "skip_reason": "no ## or ###"}

    if "<!-- SECTION_BEGIN:" in text or "<!-- SUBSECTION_BEGIN:" in text:
        return {"file": path.name, "h2": len(h2), "h3": len(h3),
                "skipped": True, "skip_reason": "already has markers"}

    h2_set = {i: t for i, t in h2}
    h3_set = {i: (sid, t) for i, sid, t in h3}
    out: list[str] = []
    open_h2: str | None = None
    open_h3: str | None = None
    sec_id = section_id(path.name)

    def close_h3():
        nonlocal open_h3
        if open_h3 is not None:
            out.append(f"<!-- SUBSECTION_END: {open_h3} -->\n")
            open_h3 = None

    def close_h2():
        nonlocal open_h2
        close_h3()
        if open_h2 is not None:
            out.append(f"<!-- SECTION_END: {sec_id} -->\n")
            open_h2 = None

    for i, raw in enumerate(lines):
        s = raw.rstrip("\n")
        if i in h2_set:
            close_h2()
            out.append(f"<!-- SECTION_BEGIN: {sec_id} -->\n")
            out.append(f"## {h2_set[i]}\n")
            open_h2 = sec_id
            continue
        if i in h3_set:
            close_h3()
            sid, title = h3_set[i]
            m = NUM_RE.match(title)
            title_clean = title[m.end():] if m else title
            out.append(f"<!-- SUBSECTION_BEGIN: {sid} -->\n")
            out.append("___\n")
            out.append(f"### {sid} {title_clean}\n")
            open_h3 = sid
            continue
        out.append(raw)
    close_h2()

    new_text = "".join(out)
    if not new_text.endswith("\n"):
        new_text += "\n"
    stats = {"file": path.name, "h2": len(h2), "h3": len(h3), "skipped": False,
             "old_len": len(text), "new_len": len(new_text), "section": sec_id}

    if apply:
        path.write_text(new_text, encoding="utf-8")
        check = path.read_text(encoding="utf-8")
        assert f"SECTION_BEGIN: {sec_id}" in check, "post: SECTION missing"
        for i, sid, _t in h3:
            assert f"SUBSECTION_BEGIN: {sid}" in check, f"post: {sid} missing"
            assert f"SUBSECTION_END: {sid}" in check, f"post END: {sid} missing"

    return stats


def main():
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--dry-run", action="store_true")
    g.add_argument("--apply", action="store_true")
    ap.add_argument("--file")
    ap.add_argument("--all", action="store_true")
    args = ap.parse_args()

    if args.file:
        files = [Path(args.file)]
    else:
        files = sorted(Path("docs").glob("*.md"))
        files = [f for f in files if f.name not in ARCHIVES]

    total_h2 = total_h3 = 0
    for f in files:
        if not f.exists():
            print(f"SKIP (missing): {f}")
            continue
        try:
            st = process_file(f, apply=args.apply)
        except AssertionError as e:
            print(f"ABORT {f.name}: {e}")
            sys.exit(2)
        if st["skipped"]:
            print(f"SKIP {f.name}: {st.get('skip_reason')}")
            continue
        total_h2 += st["h2"]
        total_h3 += st["h3"]
        print(f"{'APPLIED' if args.apply else 'DRY'} {f.name}: section={st['section']}, ##={st['h2']}, ###={st['h3']}")

    print("---")
    print(f"total: files={len(files)}, h2={total_h2}, h3={total_h3}, apply={args.apply}")


if __name__ == "__main__":
    main()
